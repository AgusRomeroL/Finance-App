#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
seed_report.py
──────────────
Reporte Markdown de una base sembrada por el ETL: conteos, invariantes del
contrato de la semilla y, opcionalmente, la comparación contra otra base (la
golden vigente) fila por fila.

  * Conteos: quincenas por estado, gastos por estado, ingresos, atribuciones,
    plantillas, planes MSI, préstamos, miembros, categorías, carteras, total.
  * Invariantes (salida 1 si alguno falla): bps = 10000 por gasto y rol, todo
    gasto con los dos roles, ``foreign_key_check`` limpio, ``user_version = 1``,
    una sola quincena ACTIVE, cero gastos PLANNED, cero ``occurred_at`` posterior
    a ``--today``, pagador Norma al 100 % desde 2025-10-01 salvo Spotify, y los
    huecos de quincenas iguales a los deliberados.
  * Con ``--golden``: tabla por quincena y diff por id de cada tabla, ignorando
    las marcas de generación (``created_at``, ``closed_at``, ``updated_at``),
    que dependen del momento en que se corrió el ETL.

Solo lee: abre las bases en modo lectura.

Uso
---
    $ python scripts/etl/seed_report.py --db /tmp/seed.db \
        --golden seed/budget_database.golden.db --today 2026-09-15 --output r.md
"""

from __future__ import annotations

import argparse
import sqlite3
import sys
from datetime import date, datetime, timezone
from pathlib import Path
from typing import Optional

# Quincenas que no existen en el Excel y se dejan como huecos a propósito
# (seed/REPORTE_SEMILLA_2026-07.md §1).
DELIBERATE_GAPS = {(2025, 1, "FIRST"), (2025, 2, "SECOND"), (2025, 11, "FIRST")}

PAYER_RULE_CUTOVER = date(2025, 10, 1)

# Marcas de generación: cambian en cada corrida aunque el contenido no cambie.
IGNORED_COLUMNS = {"created_at", "closed_at", "updated_at"}

COMPARED_TABLES = (
    "household", "member", "category", "payment_method", "quincena",
    "income_source", "expense", "expense_attribution", "recurrence_template",
    "installment_plan", "loan", "savings_goal",
)

DETAIL_LIMIT = 120


def _open_ro(path: Path) -> sqlite3.Connection:
    return sqlite3.connect(f"file:{path.as_posix()}?mode=ro", uri=True)


def _money(amount: Optional[float]) -> str:
    if amount is None:
        return "n/a"
    sign = "-" if amount < 0 else ""
    return f"{sign}${abs(amount):,.2f}"


def _cell(value) -> str:
    text = "NULL" if value is None else str(value)
    text = text.replace("|", "\\|").replace("\n", " ")
    return text if len(text) <= 80 else text[:77] + "..."


def _date_of(epoch_ms: int) -> date:
    return datetime.fromtimestamp(epoch_ms / 1000, tz=timezone.utc).date()


# ── Conteos ──────────────────────────────────────────────────────────────────

def counts(conn: sqlite3.Connection) -> dict[str, object]:
    q = lambda sql: conn.execute(sql).fetchone()[0]  # noqa: E731
    by_status = dict(conn.execute("SELECT status, COUNT(*) FROM quincena GROUP BY status").fetchall())
    exp_status = dict(conn.execute("SELECT status, COUNT(*) FROM expense GROUP BY status").fetchall())
    return {
        "Quincenas": q("SELECT COUNT(*) FROM quincena"),
        "Quincenas CLOSED / ACTIVE / PROVISIONED": " / ".join(
            str(by_status.get(s, 0)) for s in ("CLOSED", "ACTIVE", "PROVISIONED")),
        "Gastos": q("SELECT COUNT(*) FROM expense"),
        "Gastos POSTED / PLANNED": f"{exp_status.get('POSTED', 0)} / {exp_status.get('PLANNED', 0)}",
        "Total de gastos": _money(q("SELECT COALESCE(SUM(amount_mxn), 0) FROM expense")),
        "Atribuciones": q("SELECT COUNT(*) FROM expense_attribution"),
        "Ingresos": q("SELECT COUNT(*) FROM income_source"),
        "Total de ingresos": _money(q("SELECT COALESCE(SUM(amount_mxn), 0) FROM income_source")),
        "Plantillas de recurrencia": q("SELECT COUNT(*) FROM recurrence_template"),
        "Planes MSI": q("SELECT COUNT(*) FROM installment_plan"),
        "Préstamos": q("SELECT COUNT(*) FROM loan"),
        "Miembros": q("SELECT COUNT(*) FROM member"),
        "Categorías": q("SELECT COUNT(*) FROM category"),
        "Carteras": q("SELECT COUNT(*) FROM payment_method"),
        "Meta Ahorro Empresa (current)": _money(q("SELECT COALESCE(SUM(current_mxn), 0) FROM savings_goal")),
    }


def quincena_keys(conn: sqlite3.Connection) -> set[tuple[int, int, str]]:
    return {(y, m, h) for y, m, h in conn.execute("SELECT year, month, half FROM quincena")}


def gaps(keys: set[tuple[int, int, str]]) -> set[tuple[int, int, str]]:
    """Quincenas ausentes entre la primera y la última presentes."""
    if not keys:
        return set()
    ordered = sorted(keys, key=lambda k: (k[0], k[1], k[2] == "SECOND"))
    first, last = ordered[0], ordered[-1]
    missing: set[tuple[int, int, str]] = set()
    y, m, h = first
    while (y, m, h) != last:
        if (y, m, h) not in keys:
            missing.add((y, m, h))
        if h == "FIRST":
            h = "SECOND"
        else:
            h = "FIRST"
            m += 1
            if m == 13:
                y, m = y + 1, 1
    return missing


# ── Invariantes ──────────────────────────────────────────────────────────────

def invariants(conn: sqlite3.Connection, today: Optional[date]) -> list[tuple[bool, str]]:
    checks: list[tuple[bool, str]] = []
    bad_bps = conn.execute(
        """SELECT COUNT(*) FROM (SELECT expense_id, role, SUM(share_bps) s
           FROM expense_attribution GROUP BY expense_id, role HAVING s != 10000)"""
    ).fetchone()[0]
    checks.append((bad_bps == 0, f"bps = 10000 por gasto y rol ({bad_bps} pares fuera)"))

    missing_roles = conn.execute(
        """SELECT COUNT(*) FROM expense e WHERE
             (SELECT COUNT(DISTINCT role) FROM expense_attribution a WHERE a.expense_id = e.id) != 2"""
    ).fetchone()[0]
    checks.append((missing_roles == 0, f"todo gasto con BENEFICIARY y PAYER ({missing_roles} sin alguno)"))

    fk = conn.execute("PRAGMA foreign_key_check").fetchall()
    checks.append((not fk, f"foreign_key_check limpio ({len(fk)} violaciones)"))

    uv = conn.execute("PRAGMA user_version").fetchone()[0]
    checks.append((uv == 1, f"user_version = 1 (es {uv})"))

    active = conn.execute("SELECT COUNT(*) FROM quincena WHERE status = 'ACTIVE'").fetchone()[0]
    checks.append((active == 1, f"una sola quincena ACTIVE (hay {active})"))

    planned = conn.execute("SELECT COUNT(*) FROM expense WHERE status != 'POSTED'").fetchone()[0]
    checks.append((planned == 0, f"cero gastos no POSTED ({planned})"))

    if today is not None:
        future = sum(1 for (oa,) in conn.execute("SELECT occurred_at FROM expense") if _date_of(oa) > today)
        checks.append((future == 0, f"cero gastos posteriores a {today.isoformat()} ({future})"))
        fut_q = conn.execute("SELECT COUNT(*) FROM quincena WHERE start_date > ?", (today.isoformat(),)).fetchone()[0]
        checks.append((fut_q == 0, f"cero quincenas que empiezan después de {today.isoformat()} ({fut_q})"))

    norma = conn.execute("SELECT id FROM member WHERE display_name = 'Norma'").fetchone()
    benja = conn.execute("SELECT id FROM member WHERE display_name LIKE 'Benjam%' AND role = 'PAYER_ADULT'").fetchone()
    if norma and benja:
        wrong = 0
        for eid, oa, concept in conn.execute("SELECT id, occurred_at, concept FROM expense"):
            if _date_of(oa) < PAYER_RULE_CUTOVER:
                continue
            payers = conn.execute(
                "SELECT member_id, share_bps FROM expense_attribution WHERE expense_id = ? AND role = 'PAYER'",
                (eid,),
            ).fetchall()
            expected = benja[0] if "SPOTIFY" in concept.upper() else norma[0]
            if payers != [(expected, 10000)]:
                wrong += 1
        checks.append((wrong == 0, f"pagador Norma 100 % desde 2025-10-01 salvo Spotify ({wrong} fuera)"))

    keys = quincena_keys(conn)
    found = gaps(keys)
    # 2025-01-FIRST queda antes de la primera hoja: no es un hueco interno.
    order = lambda k: (k[0], k[1], k[2] == "SECOND")  # noqa: E731
    lo, hi = (min(map(order, keys)), max(map(order, keys))) if keys else ((0, 0, False),) * 2
    expected = {g for g in DELIBERATE_GAPS if lo < order(g) < hi}
    checks.append((found == expected,
                   "huecos internos = los deliberados (" +
                   (", ".join(f"{y}-{m:02d}-{h}" for y, m, h in sorted(found)) or "ninguno") + ")"))
    return checks


# ── Comparación contra otra base ─────────────────────────────────────────────

def per_quincena(conn: sqlite3.Connection) -> dict[tuple[int, int, str], tuple]:
    rows = conn.execute(
        """SELECT q.year, q.month, q.half, q.label, q.status, q.projected_income_mxn,
                  q.projected_expenses_mxn,
                  (SELECT COUNT(*) FROM expense e WHERE e.quincena_id = q.id),
                  (SELECT COALESCE(SUM(amount_mxn), 0) FROM expense e WHERE e.quincena_id = q.id),
                  (SELECT COALESCE(SUM(amount_mxn), 0) FROM income_source i WHERE i.quincena_id = q.id)
           FROM quincena q"""
    ).fetchall()
    return {(r[0], r[1], r[2]): r[3:] for r in rows}


def _table_rows(conn: sqlite3.Connection, table: str) -> tuple[list[str], dict[str, tuple]]:
    cols = [r[1] for r in conn.execute(f"PRAGMA table_info({table})") if r[1] not in IGNORED_COLUMNS]
    rows = conn.execute(f"SELECT {', '.join(cols)} FROM {table}").fetchall()
    id_idx = cols.index("id")
    return cols, {r[id_idx]: r for r in rows}


def diff_tables(new: sqlite3.Connection, old: sqlite3.Connection) -> list[str]:
    out: list[str] = []
    out.append("| Tabla | Filas golden | Filas nueva | Altas | Bajas | Cambiadas |")
    out.append("|---|---:|---:|---:|---:|---:|")
    details: list[str] = []
    for table in COMPARED_TABLES:
        cols, new_rows = _table_rows(new, table)
        _, old_rows = _table_rows(old, table)
        added = sorted(set(new_rows) - set(old_rows))
        removed = sorted(set(old_rows) - set(new_rows))
        changed = sorted(k for k in set(new_rows) & set(old_rows) if new_rows[k] != old_rows[k])
        out.append(f"| {table} | {len(old_rows)} | {len(new_rows)} | {len(added)} | {len(removed)} | {len(changed)} |")
        if table == "expense_attribution" or not (added or removed or changed):
            continue
        label_col = next((c for c in ("concept", "label", "display_name", "name", "code") if c in cols), "id")
        li = cols.index(label_col)
        details.append(f"#### {table}")
        details.append("")
        details.append("| Cambio | Fila | Detalle |")
        details.append("|---|---|---|")
        shown = 0
        for k in added:
            if shown >= DETAIL_LIMIT:
                break
            details.append(f"| alta | {_cell(new_rows[k][li])} | id `{k[:8]}` |")
            shown += 1
        for k in removed:
            if shown >= DETAIL_LIMIT:
                break
            details.append(f"| baja | {_cell(old_rows[k][li])} | id `{k[:8]}` |")
            shown += 1
        for k in changed:
            if shown >= DETAIL_LIMIT:
                break
            diffs = [f"{c}: {_cell(o)} → {_cell(n)}"
                     for c, o, n in zip(cols, old_rows[k], new_rows[k]) if o != n]
            details.append(f"| cambio | {_cell(new_rows[k][li])} | {'; '.join(diffs)} |")
            shown += 1
        total = len(added) + len(removed) + len(changed)
        if total > shown:
            details.append(f"| ... | {total - shown} más | |")
        details.append("")
    return out + [""] + details


def build_report(db: Path, golden: Optional[Path], today: Optional[date]) -> tuple[str, bool]:
    conn = _open_ro(db)
    out: list[str] = []
    out.append("| Dato | Valor |")
    out.append("|---|---|")
    for k, v in counts(conn).items():
        out.append(f"| {k} | {v} |")
    out.append("")

    checks = invariants(conn, today)
    out.append("**Invariantes**")
    out.append("")
    for ok, text in checks:
        out.append(f"- {'OK' if ok else 'FALLA'}: {text}")
    out.append("")

    out.append("**Plantillas de recurrencia**")
    out.append("")
    out.append("| Concepto | Cadencia | Monto | Próxima | Confianza |")
    out.append("|---|---|---:|---|---:|")
    for concept, cadence, amount, nxt, conf in conn.execute(
            """SELECT concept, cadence, default_amount_mxn, next_expected_date, confidence_score
               FROM recurrence_template ORDER BY concept"""):
        out.append(f"| {_cell(concept)} | {cadence} | {_money(amount)} | {nxt} | {conf:.2f} |")
    out.append("")
    out.append("**Planes MSI**")
    out.append("")
    out.append("| Plan | Cuota | Pagos | Estado | Inicio |")
    out.append("|---|---:|---|---|---|")
    for name, amount, cur, total, status, start in conn.execute(
            """SELECT display_name, installment_amount_mxn, current_installment,
                      total_installments, status, start_date FROM installment_plan ORDER BY display_name"""):
        out.append(f"| {_cell(name)} | {_money(amount)} | {cur} de {total} | {status} | {start} |")
    out.append("")

    if golden is not None:
        old = _open_ro(golden)
        new_q, old_q = per_quincena(conn), per_quincena(old)
        out.append("**Por quincena, contra la golden**")
        out.append("")
        out.append("| Quincena | Estado | Gastos (golden → nueva) | Monto (golden → nueva) | Ingresos (golden → nueva) |")
        out.append("|---|---|---|---|---|")
        for key in sorted(set(new_q) | set(old_q), key=lambda k: (k[0], k[1], k[2] == "SECOND")):
            n, o = new_q.get(key), old_q.get(key)
            label = (n or o)[0]
            status = f"{o[1] if o else 'no existía'} → {n[1] if n else 'no existe'}"
            if o and n and o[1] == n[1]:
                status = n[1]
            cnt = f"{o[4] if o else 0} → {n[4] if n else 0}"
            amt = f"{_money(o[5] if o else 0)} → {_money(n[5] if n else 0)}"
            inc = f"{_money(o[6] if o else 0)} → {_money(n[6] if n else 0)}"
            same = o is not None and n is not None and o[1:] == n[1:]
            if same:
                continue
            out.append(f"| {label} | {status} | {cnt} | {amt} | {inc} |")
        unchanged = sum(1 for k in set(new_q) & set(old_q) if new_q[k][1:] == old_q[k][1:])
        out.append("")
        out.append(f"Quincenas idénticas en estado, presupuesto, gastos e ingresos: {unchanged} (no se listan).")
        out.append("")
        out.append("**Diff por tabla** (sin `created_at`, `closed_at` ni `updated_at`)")
        out.append("")
        out.extend(diff_tables(conn, old))
    return "\n".join(out) + "\n", all(ok for ok, _ in checks)


def main() -> int:
    parser = argparse.ArgumentParser(description="Conteos, invariantes y diff de una base sembrada.")
    parser.add_argument("--db", type=Path, required=True, help="Base a revisar (solo lectura).")
    parser.add_argument("--golden", type=Path, help="Base contra la que comparar (solo lectura).")
    parser.add_argument("--today", type=date.fromisoformat, help="BUDGET_TODAY con el que se generó.")
    parser.add_argument("--output", type=Path, help="Markdown de salida (por defecto, stdout).")
    args = parser.parse_args()
    report, ok = build_report(args.db, args.golden, args.today)
    if args.output:
        args.output.write_text(report, encoding="utf-8")
        print(f"[{'OK' if ok else 'FALLA'}] Reporte escrito en {args.output}")
    else:
        sys.stdout.write(report)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
