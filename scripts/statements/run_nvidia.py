#!/usr/bin/env python3
"""
Corre el pipeline NVIDIA NIM sobre el TEXTO extraído de los estados de cuenta,
replicando EXACTAMENTE el request de `NvidiaNimClient.kt` (endpoint, modelo,
temperatura, max_tokens, system prompt y contexto del hogar). Sirve para iterar el
prompt fuera de la app y medir contra el gold.

La API key se lee de la variable de entorno NVIDIA_API_KEY (NUNCA se hardcodea ni
se commitea). Uso:

    NVIDIA_API_KEY=nvapi-... PYTHONUTF8=1 python scripts/statements/run_nvidia.py \
        --text "Estados de Cuenta/_text" \
        --out  "Estados de Cuenta/_nvidia/improved" \
        [--prompt baseline|improved]

--prompt improved (default) usa el prompt extendido (tipo/decimales/OCR/beneficiario);
--prompt baseline usa el prompt original C1 (para reproducir el baseline).
"""
from __future__ import annotations
import argparse, json, os, sys, time, urllib.request, urllib.error
from pathlib import Path

ENDPOINT = "https://integrate.api.nvidia.com/v1/chat/completions"
MODEL = "google/diffusiongemma-26b-a4b-it"

MIEMBROS = ["Benjamín", "Norma", "Normita", "David", "Agustín", "Santiago"]

# Subconjunto de categorías hoja del catálogo del ETL, relevantes a compras.
CATEGORIAS = [
    "FOOD.COMIDA — Comida", "FOOD.DESPENSA — Despensa", "FOOD.LIMPIEZA — Limpieza",
    "TRANSPORTATION.GASOLINA — Gasolina", "TRANSPORTATION.MAINTENANCE — Mantenimiento",
    "ENTERTAINMENT.DIVERSION — Diversión", "ENTERTAINMENT.NETFLIX — Netflix",
    "ENTERTAINMENT.SPOTIFY — Spotify", "ENTERTAINMENT.DISNEY — Disney+",
    "ENTERTAINMENT.PRIME — Amazon Prime", "ENTERTAINMENT.HBO — HBO",
    "ENTERTAINMENT.YOUTUBE_PREMIUM — YouTube Premium", "ENTERTAINMENT.MELI_PLUS — Meli+",
    "HOUSING.INTERNET — Internet", "HOUSING.TELEFONO — Teléfono", "HOUSING.MUEBLES — Muebles",
    "PETS.COMIDA — Comida gatas", "PETS.VETERINARIO — Veterinario",
    "PERSONAL_CARE — Cuidado personal", "ESCUELA — Escuela / Colegiaturas",
    "GIFTS — Regalos y donaciones", "SERVICIOS_EXTERNOS — Servicios externos",
    "OTHER — Otros",
]

SYSTEM_PROMPT_IMPROVED = """
Eres un extractor de datos de estados de cuenta bancarios mexicanos (MXN).
Recibes el TEXTO PLANO de un estado de cuenta (puede venir de OCR, con
ruido). Devuelve EXCLUSIVAMENTE un objeto JSON válido, sin texto adicional,
sin markdown, sin explicaciones.

Esquema EXACTO (usa null si un dato no aparece; NO inventes):
{
  "emisor": string|null,
  "last4": string|null,
  "periodo": { "inicio": "YYYY-MM-DD"|null, "fin": "YYYY-MM-DD"|null },
  "fechaCorte": "YYYY-MM-DD"|null,
  "fechaLimitePago": "YYYY-MM-DD"|null,
  "saldoTotal": number|null,
  "pagoMinimo": number|null,
  "pagoNoIntereses": number|null,
  "tasaAnual": number|null,
  "movimientos": [
    {
      "fecha": "YYYY-MM-DD"|null,
      "concepto": string,
      "monto": number,
      "tipo": "COMPRA"|"CARGO"|"INTERES"|"COMISION"|"PAGO"|"ABONO",
      "esMsi": boolean,
      "msiPlazo": number|null,
      "msiNumero": number|null,
      "categoriaSugerida": string|null,
      "beneficiariosSugeridos": [string]|null
    }
  ]
}

REGLAS CRÍTICAS:
1. MONTOS: cópialos TAL CUAL, con sus centavos. Quita solo "$" y las
   comas de millares. "159.73" es 159.73, NUNCA 1597.3. "1,234.56" es
   1234.56. Jamás muevas el punto decimal ni multipliques.
2. EXTRAE TODOS los movimientos del detalle, de principio a fin, sin omitir
   ni resumir. Si el estado tiene 20 movimientos, devuelve los 20.
3. TIPO de cada movimiento:
   - COMPRA/CARGO/INTERES/COMISION = dinero que Norma gasta o debe.
   - PAGO/ABONO = dinero a favor (pagos a la tarjeta, abonos, devoluciones,
     depósitos, nómina, ingresos, ganancias, cashback). Inclúyelos igual.
   Señales de abono: "Su Pago", "Gracias", "Abono", "Devolución",
   "Depósito", "Ingreso", "Ganancia"; signo "-" antepuesto o guion
   pospuesto ("244.00-"); columna de abonos.
4. SIGNO: en "monto" pon SIEMPRE el valor absoluto (positivo). La dirección
   la da "tipo", no el signo.
5. FECHAS: ISO YYYY-MM-DD, infiriendo el año del periodo. Si una fecha está
   corrupta o ilegible (ruido de OCR tipo "Nnf-60"), usa null; nunca copies
   texto que no sea una fecha.
6. MSI: detecta "3/12", "3 de 12", "N DE M", "MSI", "Meses sin intereses",
   "N MENS", "PP#####", "en N Cuotas (n/N)". esMsi=true y rellena msiPlazo
   (total) y msiNumero (actual) cuando el texto los dé; si no, null.
7. CUENTAS DE DÉBITO / WALLETS (BBVA Libretón, Banamex MiCuenta, BanCoppel,
   Mercado Pago): retiros/compras/SPEI enviados = CARGO/COMPRA; depósitos/
   nómina/ingresos = ABONO. No hay MSI ni pago mínimo (usa null en esos).
8. CATEGORÍA y BENEFICIARIOS: solo si abajo se te da la lista del hogar.
   Para cada COMPRA sugiere "categoriaSugerida" (un CÓDIGO EXACTO de la
   lista) y "beneficiariosSugeridos" (nombres EXACTOS de la lista) según el
   comercio. Si dudas, usa null y []. No lo apliques a INTERES/COMISION/
   PAGO/ABONO.
9. Si el texto no es un estado de cuenta, devuelve todos los campos null y
   "movimientos": [].
""".strip()

SYSTEM_PROMPT_BASELINE = """
Eres un extractor de datos de estados de cuenta bancarios mexicanos (MXN).
Recibes el TEXTO PLANO de un estado de cuenta. Devuelve EXCLUSIVAMENTE un
objeto JSON válido, sin texto adicional, sin markdown, sin explicaciones.

Esquema EXACTO (usa null si un dato no aparece; no inventes):
{
  "emisor": string|null,
  "last4": string|null,
  "periodo": { "inicio": "YYYY-MM-DD"|null, "fin": "YYYY-MM-DD"|null },
  "fechaCorte": "YYYY-MM-DD"|null,
  "fechaLimitePago": "YYYY-MM-DD"|null,
  "saldoTotal": number|null,
  "pagoMinimo": number|null,
  "pagoNoIntereses": number|null,
  "tasaAnual": number|null,
  "movimientos": [
    { "fecha": "YYYY-MM-DD"|null, "concepto": string, "monto": number,
      "esMsi": boolean, "msiPlazo": number|null, "msiNumero": number|null }
  ]
}

Reglas:
- Todas las fechas en formato ISO YYYY-MM-DD. Infiere el año del periodo.
- Montos como número decimal sin símbolo ni comas de miles (1234.56).
- Detecta MSI/mensualidades: "3/12", "3 de 12", "MSI", "Meses sin intereses",
  "a X meses". Rellena esMsi=true, msiPlazo y msiNumero cuando puedas.
- No incluyas pagos/abonos ni intereses como movimientos de cargo.
- Si el texto no es un estado de cuenta, devuelve el objeto con todos los
  campos en null y "movimientos": [].
""".strip()


def user_prompt(text: str, with_context: bool) -> str:
    household = ""
    if with_context:
        household = (
            "\nContexto del hogar (para categoriaSugerida y beneficiariosSugeridos):\n"
            f"MIEMBROS (usa estos nombres exactos): {', '.join(MIEMBROS)}\n"
            f"CATEGORÍAS (código — nombre; usa el CÓDIGO exacto): {'; '.join(CATEGORIAS)}\n"
        )
    return (
        "Analiza el siguiente estado de cuenta y devuelve SOLO el JSON del esquema.\n"
        + household
        + f"\n===== ESTADO DE CUENTA =====\n{text}\n===== FIN ====="
    )


def call(api_key: str, system: str, user: str, max_tokens: int):
    body = json.dumps({
        "model": MODEL,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
        "temperature": 0.1,
        "max_tokens": max_tokens,
    }).encode("utf-8")
    req = urllib.request.Request(ENDPOINT, data=body, headers={
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
        "Accept": "application/json",
    })
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=120) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    dt = time.time() - t0
    content = payload["choices"][0]["message"]["content"]
    usage = payload.get("usage", {})
    return content, dt, usage


def extract_json(content: str):
    """Reparación tolerante: primer objeto {..} balanceado."""
    import re
    s = content.strip()
    if s.startswith("```"):
        s = re.sub(r"^```(json)?", "", s).strip().rstrip("`").strip()
    i = s.find("{")
    if i < 0:
        raise ValueError("sin objeto JSON")
    dec = json.JSONDecoder()
    obj, _ = dec.raw_decode(s[i:])
    return obj


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--text", default="Estados de Cuenta/_text")
    ap.add_argument("--out", default="Estados de Cuenta/_nvidia/improved")
    ap.add_argument("--prompt", choices=["baseline", "improved"], default="improved")
    ap.add_argument("--max-tokens", type=int, default=8192)
    ap.add_argument("--only", default=None, help="substring para filtrar archivos")
    args = ap.parse_args()

    key = os.environ.get("NVIDIA_API_KEY", "").strip()
    if not key:
        print("ERROR: exporta NVIDIA_API_KEY", file=sys.stderr)
        sys.exit(2)

    system = SYSTEM_PROMPT_IMPROVED if args.prompt == "improved" else SYSTEM_PROMPT_BASELINE
    with_ctx = args.prompt == "improved"
    out_dir = Path(args.out); out_dir.mkdir(parents=True, exist_ok=True)
    log_path = out_dir / "_log.jsonl"
    log = log_path.open("w", encoding="utf-8")

    files = sorted(Path(args.text).glob("*.txt"))
    if args.only:
        files = [f for f in files if args.only.lower() in f.stem.lower()]
    ok = fail = 0
    for f in files:
        text = f.read_text(encoding="utf-8")
        up = user_prompt(text, with_ctx)
        attempts = 0
        last_err = None
        for attempt in range(1, 5):
            attempts = attempt
            try:
                content, dt, usage = call(key, system, up, args.max_tokens)
                try:
                    obj = extract_json(content)
                    parse = "ok"
                except Exception as pe:
                    obj = None
                    parse = f"parse_fail: {pe}"
                    (out_dir / f"{f.stem}.raw.txt").write_text(content, encoding="utf-8")
                if obj is not None:
                    json.dump(obj, (out_dir / f"{f.stem}.json").open("w", encoding="utf-8"),
                              ensure_ascii=False, indent=2)
                    ok += 1
                else:
                    fail += 1
                nmov = len(obj.get("movimientos", [])) if obj else 0
                rec = {"file": f.stem, "ok": obj is not None, "latency_s": round(dt, 1),
                       "attempts": attempt, "movimientos": nmov, "parse": parse,
                       "usage": usage}
                log.write(json.dumps(rec, ensure_ascii=False) + "\n"); log.flush()
                print(f"  {f.stem}: {parse}, {nmov} mov, {dt:.1f}s")
                break
            except urllib.error.HTTPError as he:
                last_err = f"HTTP {he.code}"
                if he.code == 429:
                    wait = 3 * attempt
                    print(f"  {f.stem}: 429, backoff {wait}s (intento {attempt})")
                    time.sleep(wait)
                    continue
                else:
                    break
            except Exception as e:
                last_err = str(e)
                time.sleep(2 * attempt)
        else:
            fail += 1
            log.write(json.dumps({"file": f.stem, "ok": False, "attempts": attempts,
                                  "error": last_err}, ensure_ascii=False) + "\n")
            print(f"  {f.stem}: FALLO {last_err}")
    log.close()
    print(f"\nOK {ok} · FALLO {fail} · log {log_path}")


if __name__ == "__main__":
    main()
