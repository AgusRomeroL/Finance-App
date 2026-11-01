#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
diff_excel.py
─────────────
Compara dos versiones del Excel quincenal de Norma y emite un reporte
Markdown con:

  1. Hojas nuevas y retiradas.
  2. Por cada hoja común con cambios: cada celda distinta (coordenada, valor
     viejo y nuevo), etiquetada con la sección y el concepto de su renglón.
  3. Por cada hoja común con cambios: el diff a nivel de línea presupuestal
     (lo que de verdad lee el ETL): altas, bajas y cambios de monto o de
     reparto Norma/Benjamín.
  4. Las celdas de ingreso (E4 Benjamín, E5 Norma, E9 Ahorro empresa) de
     todas las hojas del año más reciente, vieja contra nueva.
  5. Para cada hoja nueva: sus líneas y la comparación contra la hoja anterior
     de la misma mitad del mes (detector de plantilla copiada sin actualizar).

Reutiliza el escáner de secciones del ETL (no lo replica), así que "línea"
significa exactamente lo mismo que en ``excel_to_room_etl.py``.

Uso
---
    $ PYTHONUTF8=1 python scripts/etl/diff_excel.py \
        --old "Copy of presupuesto 2.5 (1).xlsx" \
        --new "Copy of presupuesto 2.5 (2).xlsx" \
        --output diff.md

Los Excel contienen datos financieros personales: se leen por ruta, nunca se
copian al repositorio. La salida sí puede ir a ``seed/`` como parte de un
reporte (mismo criterio que los reportes de semilla existentes).
"""

from __future__ import annotations

import argparse
import hashlib
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from openpyxl import load_workbook
from openpyxl.utils import get_column_letter
from openpyxl.worksheet.worksheet import Worksheet

sys.path.insert(0, str(Path(__file__).resolve().parent))
from excel_to_room_etl import (  # noqa: E402
    BudgetLine,
    SectionHeader,
    _normalize,
    build_sheet_periods,
    extract_budget_lines,
    find_section_headers,
)

# Filas a partir de las cuales la hoja deja de tener bloques presupuestales y
# empiezan las notas sueltas (mismo corte que usa find_section_headers).
NOTES_FROM_ROW = 61

INCOME_CELLS = (("E4", "Sueldo Benjamín"), ("E5", "Sueldo Norma"), ("E9", "Ahorro empresa"))


def _sha256_short(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()[:12]


def _fmt(value) -> str:
    """Valor de celda legible y seguro dentro de una tabla Markdown."""
    if value is None:
        return "(vacía)"
    if isinstance(value, float):
        text = f"{value:,.2f}"
    elif isinstance(value, int):
        text = f"{value:,}"
    else:
        text = str(value).strip() or "(espacios)"
    return text.replace("|", "\\|").replace("\n", " ")


def _money(amount: Optional[float]) -> str:
    if amount is None:
        return "(vacía)"
    return f"{'-' if amount < 0 else ''}${abs(amount):,.2f}"


# ── Etiquetado de celdas ─────────────────────────────────────────────────────

_ROLE_BY_OFFSET = {0: "concepto", 1: "proyectado", 2: "Norma", 3: "Benjamín"}


def _label_cell(ws: Worksheet, headers: list[SectionHeader], row: int, col: int) -> str:
    """Dice qué es la celda: cabecera de ingresos, renglón de una sección o nota."""
    if row <= 10:
        label = ws.cell(row=row, column=4).value or ws.cell(row=row, column=3).value
        return f"cabecera ({_fmt(label)})" if label else "cabecera"
    if row >= NOTES_FROM_ROW:
        return "notas"
    best: Optional[SectionHeader] = None
    for hdr in headers:
        if hdr.name_col <= col <= hdr.benjamin_col and hdr.row < row < hdr.row + 25:
            if best is None or hdr.row > best.row:
                best = hdr
    if best is None:
        return "fuera de bloque"
    if row == best.row:
        return f"{best.section_code}, encabezado"
    concept = ws.cell(row=row, column=best.name_col).value
    role = _ROLE_BY_OFFSET.get(col - best.name_col, "otra columna")
    return f"{best.section_code} · {_fmt(concept)} · {role}"


@dataclass
class CellDiff:
    coord: str
    label: str
    old: object
    new: object


def diff_cells(old_ws: Worksheet, new_ws: Worksheet) -> list[CellDiff]:
    headers_new = find_section_headers(new_ws)
    headers_old = find_section_headers(old_ws)
    max_row = max(old_ws.max_row, new_ws.max_row)
    max_col = max(old_ws.max_column, new_ws.max_column)
    diffs: list[CellDiff] = []
    for r in range(1, max_row + 1):
        for c in range(1, max_col + 1):
            a = old_ws.cell(row=r, column=c).value
            b = new_ws.cell(row=r, column=c).value
            if a == b:
                continue
            # La etiqueta sale de la hoja nueva; si ahí la celda quedó vacía,
            # de la vieja (un renglón borrado conserva su concepto viejo).
            label = _label_cell(new_ws, headers_new, r, c)
            if "(vacía)" in label or label == "fuera de bloque":
                label = _label_cell(old_ws, headers_old, r, c)
            diffs.append(CellDiff(f"{get_column_letter(c)}{r}", label, a, b))
    return diffs


# ── Diff a nivel de línea presupuestal ───────────────────────────────────────

def _line_key(line: BudgetLine) -> tuple[str, str]:
    return (line.section_code, _normalize(line.concept))


def _index_lines(lines: list[BudgetLine]) -> dict[tuple[str, str], list[BudgetLine]]:
    index: dict[tuple[str, str], list[BudgetLine]] = {}
    for line in lines:
        index.setdefault(_line_key(line), []).append(line)
    return index


def diff_lines(old_lines: list[BudgetLine], new_lines: list[BudgetLine]) -> list[tuple[str, str, str, str]]:
    """Filas (tipo, sección, concepto, detalle) del diff de líneas."""
    old_idx, new_idx = _index_lines(old_lines), _index_lines(new_lines)
    rows: list[tuple[str, str, str, str]] = []
    for key in sorted(set(old_idx) | set(new_idx)):
        olds, news = old_idx.get(key, []), new_idx.get(key, [])
        for i in range(max(len(olds), len(news))):
            o = olds[i] if i < len(olds) else None
            n = news[i] if i < len(news) else None
            if o is None:
                rows.append(("alta", n.section_code, n.concept.strip(),
                             f"proyectado {_money(n.projected)} (Norma {_money(n.paid_by_norma)}, "
                             f"Benjamín {_money(n.paid_by_benjamin)})"))
            elif n is None:
                rows.append(("baja", o.section_code, o.concept.strip(),
                             f"era {_money(o.projected)}"))
            else:
                changes = []
                if o.projected != n.projected:
                    changes.append(f"proyectado {_money(o.projected)} → {_money(n.projected)}")
                if o.paid_by_norma != n.paid_by_norma:
                    changes.append(f"Norma {_money(o.paid_by_norma)} → {_money(n.paid_by_norma)}")
                if o.paid_by_benjamin != n.paid_by_benjamin:
                    changes.append(f"Benjamín {_money(o.paid_by_benjamin)} → {_money(n.paid_by_benjamin)}")
                if o.concept.strip() != n.concept.strip():
                    changes.append(f"texto '{o.concept.strip()}' → '{n.concept.strip()}'")
                if changes:
                    rows.append(("cambio", n.section_code, n.concept.strip(), "; ".join(changes)))
    return rows


# ── Reporte ──────────────────────────────────────────────────────────────────

def build_report(old_path: Path, new_path: Path) -> str:
    wb_old = load_workbook(old_path, data_only=True)
    wb_new = load_workbook(new_path, data_only=True)
    old_names, new_names = wb_old.sheetnames, wb_new.sheetnames
    periods_new = build_sheet_periods(new_names)
    periods_old = build_sheet_periods(old_names)

    out: list[str] = []
    out.append("| Archivo | Bytes | sha256 (12) | Hojas |")
    out.append("|---|---:|---|---:|")
    for label, path, names in (("viejo", old_path, old_names), ("nuevo", new_path, new_names)):
        out.append(f"| {label}: `{path.name}` | {path.stat().st_size:,} | `{_sha256_short(path)}` | {len(names)} |")
    out.append("")

    added = [n for n in new_names if n not in old_names]
    removed = [n for n in old_names if n not in new_names]
    common = [n for n in new_names if n in old_names]
    out.append(f"Hojas nuevas: {', '.join(repr(n) for n in added) or 'ninguna'}.  ")
    out.append(f"Hojas retiradas: {', '.join(repr(n) for n in removed) or 'ninguna'}.")
    out.append("")

    # 1. Resumen por hoja común.
    changed: list[tuple[str, list[CellDiff]]] = []
    unchanged: list[str] = []
    for name in common:
        cells = diff_cells(wb_old[name], wb_new[name])
        (changed.append((name, cells)) if cells else unchanged.append(name))
    out.append(f"Hojas comunes sin ningún cambio de valor: {len(unchanged)} de {len(common)}.")
    out.append("")
    out.append("| Hoja con cambios | Celdas | Renglones | Rango de filas |")
    out.append("|---|---:|---:|---|")
    for name, cells in changed:
        rows = sorted({int("".join(ch for ch in d.coord if ch.isdigit())) for d in cells})
        out.append(f"| {name.strip()} | {len(cells)} | {len(rows)} | {rows[0]}–{rows[-1]} |")
    out.append("")

    # 2. Celdas de ingreso del año más reciente.
    years = [p.year for p in periods_new.values()]
    last_year = max(years) if years else None
    out.append(f"### Celdas de ingreso, hojas de {last_year}")
    out.append("")
    out.append("| Hoja | " + " | ".join(f"{label} ({cell}) viejo → nuevo" for cell, label in INCOME_CELLS) + " |")
    out.append("|---|" + "---|" * len(INCOME_CELLS))
    for name in new_names:
        period = periods_new.get(name)
        if period is None or period.year != last_year:
            continue
        cells_txt = []
        for cell, _ in INCOME_CELLS:
            new_v = wb_new[name][cell].value
            old_v = wb_old[name][cell].value if name in old_names else "(no existía)"
            mark = "" if old_v == new_v else " **(cambió)**"
            cells_txt.append(f"{_fmt(old_v)} → {_fmt(new_v)}{mark}")
        out.append(f"| {name.strip()} | " + " | ".join(cells_txt) + " |")
    out.append("")

    # 3. Detalle por hoja con cambios.
    for name, cells in changed:
        out.append(f"### {name.strip()}")
        out.append("")
        line_rows = diff_lines(extract_budget_lines(wb_old[name]), extract_budget_lines(wb_new[name]))
        old_total = sum(l.projected for l in extract_budget_lines(wb_old[name]))
        new_total = sum(l.projected for l in extract_budget_lines(wb_new[name]))
        out.append(f"Total proyectado que lee el ETL: {_money(old_total)} → {_money(new_total)} "
                   f"({_money(new_total - old_total)}).")
        out.append("")
        out.append("**Líneas presupuestales (lo que lee el ETL)**")
        out.append("")
        if line_rows:
            out.append("| Tipo | Sección | Concepto | Detalle |")
            out.append("|---|---|---|---|")
            for kind, section, concept, detail in line_rows:
                out.append(f"| {kind} | {section} | {_fmt(concept)} | {detail} |")
        else:
            out.append("Sin cambios en las líneas presupuestales: todo el cambio está en cabecera o notas.")
        out.append("")
        out.append("**Celda por celda**")
        out.append("")
        out.append("| Celda | Qué es | Viejo | Nuevo |")
        out.append("|---|---|---|---|")
        for d in cells:
            out.append(f"| {d.coord} | {d.label} | {_fmt(d.old)} | {_fmt(d.new)} |")
        out.append("")

    # 4. Hojas nuevas contra la anterior de la misma mitad.
    for name in added:
        period = periods_new.get(name)
        out.append(f"### Hoja nueva: {name.strip()}")
        out.append("")
        new_lines = extract_budget_lines(wb_new[name])
        out.append(f"{len(new_lines)} líneas, total proyectado {_money(sum(l.projected for l in new_lines))}.")
        out.append("")
        ref_name = None
        if period is not None:
            for other in reversed(new_names[:new_names.index(name)]):
                op = periods_new.get(other)
                if op is not None and op.half == period.half:
                    ref_name = other
                    break
        if ref_name:
            ref_lines = extract_budget_lines(wb_new[ref_name])
            same = {(_line_key(l), l.projected) for l in ref_lines} & {(_line_key(l), l.projected) for l in new_lines}
            out.append(f"Contra «{ref_name.strip()}» (misma mitad, {len(ref_lines)} líneas, total "
                       f"{_money(sum(l.projected for l in ref_lines))}): {len(same)} líneas idénticas en "
                       f"concepto, sección y monto.")
            out.append("")
            rows = diff_lines(ref_lines, new_lines)
            if rows:
                out.append("| Tipo | Sección | Concepto | Detalle |")
                out.append("|---|---|---|---|")
                for kind, section, concept, detail in rows:
                    out.append(f"| {kind} | {section} | {_fmt(concept)} | {detail} |")
                out.append("")
        out.append("| Sección | Concepto | Proyectado | Norma | Benjamín |")
        out.append("|---|---|---:|---:|---:|")
        for l in new_lines:
            out.append(f"| {l.section_code} | {_fmt(l.concept)} | {_money(l.projected)} | "
                       f"{_money(l.paid_by_norma)} | {_money(l.paid_by_benjamin)} |")
        out.append("")
    return "\n".join(out) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description="Diff celda a celda entre dos versiones del Excel quincenal.")
    parser.add_argument("--old", type=Path, required=True, help="Excel anterior.")
    parser.add_argument("--new", type=Path, required=True, help="Excel nuevo.")
    parser.add_argument("--output", type=Path, help="Markdown de salida (por defecto, stdout).")
    args = parser.parse_args()
    for p in (args.old, args.new):
        if not p.exists():
            print(f"ERROR: no existe {p}", file=sys.stderr)
            return 2
    report = build_report(args.old, args.new)
    if args.output:
        args.output.write_text(report, encoding="utf-8")
        print(f"[OK] Diff escrito en {args.output}")
    else:
        sys.stdout.write(report)
    return 0


if __name__ == "__main__":
    sys.exit(main())
