#!/usr/bin/env python3
"""
Compara la extracción NVIDIA NIM contra el gold hecho a mano.

Uso:
    PYTHONUTF8=1 python scripts/statements/compare_gold_nvidia.py \
        --gold "Estados de Cuenta/_gold/raw" \
        --nvidia "Estados de Cuenta/_nvidia/baseline" \
        [--out metrics.md]

Empareja por nombre de archivo <Banco>-<AAAA-MM>.json. Para cada estado calcula:
  - Exactitud de campos de cabecera (periodo, corte, límite, saldo, pagoMinimo,
    pagoNoIntereses, tasaAnual, last4).
  - Movimientos: precision/recall emparejando por (fecha, |monto|) con tolerancia
    de ±0.01 en monto y ±3 días en fecha; reporta faltantes/espurios.
  - MSI: de los movimientos emparejados, acierto de esMsi / msiPlazo / msiNumero.
  - (si el gold/NVIDIA traen beneficiariosSugeridos/categoriaSugerida) acierto de
    esos campos sobre los movimientos emparejados.

El gold puede traer 'pagosAbonos' aparte de 'movimientos'; NVIDIA mete todo en
'movimientos' (a veces con monto negativo para abonos). Para comparar movimientos
de CARGO, se excluyen del lado NVIDIA los montos <= 0 y los conceptos de pago
(regex PAGO/ABONO/SU PAGO). El gold ya separa cargos en 'movimientos'.
"""
from __future__ import annotations
import argparse, json, re, sys
from datetime import date
from pathlib import Path

PAY_RE = re.compile(r"\b(SU\s+)?PAGO|ABONO|GRACIAS|BONIFICACION|CASHBACK|GANANCIA|INGRESO DE DINERO", re.I)


def _d(s):
    if not s:
        return None
    try:
        y, m, dd = str(s)[:10].split("-")
        return date(int(y), int(m), int(dd))
    except Exception:
        return None


def _f(x):
    if x is None:
        return None
    try:
        return round(float(x), 2)
    except Exception:
        return None


def load(p: Path):
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except Exception as e:
        print(f"  ! no pude leer {p.name}: {e}", file=sys.stderr)
        return None


def charges(obj, from_nvidia: bool):
    """Lista normalizada de movimientos de CARGO (compras/cargos, monto positivo)."""
    out = []
    for m in obj.get("movimientos", []) or []:
        amt = _f(m.get("monto"))
        if amt is None:
            continue
        tipo = str(m.get("tipo", "") or "").strip().upper()
        if tipo in ("PAGO", "ABONO"):
            continue
        if from_nvidia:
            # NVIDIA a veces mete abonos como negativos o pagos como movimiento
            if amt <= 0:
                continue
            if not tipo and PAY_RE.search(str(m.get("concepto", ""))):
                continue
        out.append({
            "fecha": _d(m.get("fecha")),
            "monto": abs(amt),
            "concepto": str(m.get("concepto", "")),
            "esMsi": bool(m.get("esMsi", False)),
            "msiPlazo": m.get("msiPlazo"),
            "msiNumero": m.get("msiNumero"),
            "cat": m.get("categoriaSugerida"),
            "ben": m.get("beneficiariosSugeridos"),
        })
    return out


def match_movements(gold, nvd):
    """Greedy match por |monto| (±0.01) y fecha (±3 días). Devuelve pares y sobrantes."""
    used = [False] * len(nvd)
    pairs = []
    for g in gold:
        best = -1
        best_score = None
        for i, n in enumerate(nvd):
            if used[i]:
                continue
            if abs(g["monto"] - n["monto"]) > 0.01:
                continue
            dd = 999
            if g["fecha"] and n["fecha"]:
                dd = abs((g["fecha"] - n["fecha"]).days)
            score = dd
            if best_score is None or score < best_score:
                best_score, best = score, i
        if best >= 0 and (best_score is None or best_score <= 3):
            used[best] = True
            pairs.append((g, nvd[best]))
    missing = [g for g in gold if g not in [p[0] for p in pairs]]
    spurious = [nvd[i] for i in range(len(nvd)) if not used[i]]
    return pairs, missing, spurious


def hdr_cmp(g, n):
    fields = ["fechaCorte", "fechaLimitePago", "saldoTotal", "pagoMinimo",
              "pagoNoIntereses", "tasaAnual", "last4"]
    res = {}
    gp, np_ = g.get("periodo", {}) or {}, n.get("periodo", {}) or {}
    res["periodo.inicio"] = (_d(gp.get("inicio")) == _d(np_.get("inicio")))
    res["periodo.fin"] = (_d(gp.get("fin")) == _d(np_.get("fin")))
    for f in fields:
        gv, nv = g.get(f), n.get(f)
        if f in ("saldoTotal", "pagoMinimo", "pagoNoIntereses", "tasaAnual"):
            res[f] = (_f(gv) == _f(nv))
        elif f in ("fechaCorte", "fechaLimitePago"):
            res[f] = (_d(gv) == _d(nv))
        else:
            res[f] = (str(gv or "").strip()[-4:] == str(nv or "").strip()[-4:])
    return res


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--gold", default="Estados de Cuenta/_gold/raw")
    ap.add_argument("--nvidia", default="Estados de Cuenta/_nvidia/baseline")
    ap.add_argument("--out", default=None)
    args = ap.parse_args()

    gold_dir, nvd_dir = Path(args.gold), Path(args.nvidia)
    rows = []
    tot = {"g": 0, "n": 0, "tp": 0, "miss": 0, "spur": 0,
           "msi_ok": 0, "msi_n": 0, "hdr_ok": 0, "hdr_n": 0}

    for gp in sorted(gold_dir.glob("*.json")):
        name = gp.stem
        npth = nvd_dir / f"{name}.json"
        if not npth.exists():
            rows.append((name, "— sin NVIDIA —"))
            continue
        g, n = load(gp), load(npth)
        if not g or not n:
            rows.append((name, "— error de lectura —"))
            continue
        gc, nc = charges(g, False), charges(n, True)
        pairs, missing, spurious = match_movements(gc, nc)
        hdr = hdr_cmp(g, n)
        hdr_ok = sum(1 for v in hdr.values() if v)
        hdr_n = len(hdr)
        msi_ok = sum(1 for gg, nn in pairs
                     if gg["esMsi"] == nn["esMsi"]
                     and (gg["msiPlazo"] or None) == (nn["msiPlazo"] or None)
                     and (gg["msiNumero"] or None) == (nn["msiNumero"] or None))
        prec = len(pairs) / len(nc) if nc else 1.0
        rec = len(pairs) / len(gc) if gc else 1.0
        f1 = 2 * prec * rec / (prec + rec) if (prec + rec) else 0.0
        rows.append((name, {
            "g": len(gc), "n": len(nc), "tp": len(pairs),
            "miss": len(missing), "spur": len(spurious),
            "prec": prec, "rec": rec, "f1": f1,
            "msi_ok": msi_ok, "hdr": f"{hdr_ok}/{hdr_n}",
            "hdr_fail": [k for k, v in hdr.items() if not v],
            "missing": [(str(m["fecha"]), m["monto"], m["concepto"][:30]) for m in missing],
            "spurious": [(str(s["fecha"]), s["monto"], s["concepto"][:30]) for s in spurious],
        }))
        tot["g"] += len(gc); tot["n"] += len(nc); tot["tp"] += len(pairs)
        tot["miss"] += len(missing); tot["spur"] += len(spurious)
        tot["msi_ok"] += msi_ok; tot["msi_n"] += len(pairs)
        tot["hdr_ok"] += hdr_ok; tot["hdr_n"] += hdr_n

    lines = ["# Métricas gold vs NVIDIA\n"]
    lines.append("| Estado | Mov gold | Mov NVIDIA | TP | Prec | Rec | F1 | MSI ok | Cabecera | Campos fallidos |")
    lines.append("|---|--:|--:|--:|--:|--:|--:|--:|--:|---|")
    for name, r in rows:
        if isinstance(r, str):
            lines.append(f"| {name} | {r} | | | | | | | | |")
            continue
        lines.append(f"| {name} | {r['g']} | {r['n']} | {r['tp']} | "
                     f"{r['prec']:.2f} | {r['rec']:.2f} | {r['f1']:.2f} | "
                     f"{r['msi_ok']}/{r['tp']} | {r['hdr']} | {', '.join(r['hdr_fail']) or '—'} |")
    P = tot["tp"] / tot["n"] if tot["n"] else 0
    R = tot["tp"] / tot["g"] if tot["g"] else 0
    F = 2 * P * R / (P + R) if (P + R) else 0
    lines.append(f"\n**TOTAL** — movimientos gold {tot['g']}, NVIDIA {tot['n']}, "
                 f"emparejados {tot['tp']} · **Precision {P:.3f} · Recall {R:.3f} · F1 {F:.3f}** · "
                 f"MSI {tot['msi_ok']}/{tot['msi_n']} · "
                 f"cabecera {tot['hdr_ok']}/{tot['hdr_n']} ({tot['hdr_ok']/tot['hdr_n']*100:.0f}%) · "
                 f"faltantes {tot['miss']} · espurios {tot['spur']}")

    lines.append("\n## Detalle de discrepancias por estado\n")
    for name, r in rows:
        if isinstance(r, str):
            continue
        if not r["missing"] and not r["spurious"]:
            continue
        lines.append(f"### {name}")
        if r["missing"]:
            lines.append("- **Faltantes (gold no visto por NVIDIA):** " +
                         "; ".join(f"{f} ${m} {c}" for f, m, c in r["missing"]))
        if r["spurious"]:
            lines.append("- **Espurios (NVIDIA inventó/no-cargo):** " +
                         "; ".join(f"{f} ${m} {c}" for f, m, c in r["spurious"]))
        lines.append("")

    out = "\n".join(lines)
    if args.out:
        Path(args.out).write_text(out, encoding="utf-8")
        print(f"escrito: {args.out}")
    print(out)


if __name__ == "__main__":
    main()
