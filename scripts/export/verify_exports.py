#!/usr/bin/env python3
"""Revisa desde el escritorio los archivos que exporta la app (Fase 5).

No sustituye abrir el archivo: openpyxl y pypdf son mucho mas permisivos que
Excel y que un visor de PDF. Sirve para lo que un vistazo no cubre, que es
comprobar que las cifras cuadran y que la estructura es la esperada.

Uso:
  python scripts/export/verify_exports.py --pdf reporte.pdf --xlsx libro.xlsx \
      --csv movimientos.csv --backup respaldo.db

Cada argumento es opcional: se revisa lo que se pase.
Dependencias: pypdf y openpyxl (pip install pypdf openpyxl).
"""
from __future__ import annotations

import argparse
import csv
import sqlite3
import sys
import zipfile

ESPERADO_CSV = [
    "tipo", "fecha", "quincena", "concepto", "categoria", "grupo", "monto_mxn",
    "estado", "cuenta", "metodo", "beneficiarios", "pagadores", "liquidacion",
    "notas", "id",
]

fallos: list[str] = []


def error(mensaje: str) -> None:
    fallos.append(mensaje)
    print(f"  FALLA: {mensaje}")


def revisar_pdf(ruta: str) -> None:
    from pypdf import PdfReader

    print(f"PDF {ruta}")
    lector = PdfReader(ruta)
    print(f"  paginas: {len(lector.pages)}")
    if not lector.pages:
        error("el PDF no tiene paginas")
        return
    primera = lector.pages[0]
    ancho = round(float(primera.mediabox.width))
    alto = round(float(primera.mediabox.height))
    print(f"  tamano: {ancho} x {alto} pt")
    if (ancho, alto) != (595, 842):
        error(f"se esperaba A4 (595 x 842 pt) y es {ancho} x {alto}")
    fuentes = (primera.get("/Resources", {}) or {}).get("/Font", {}) or {}
    print(f"  fuentes embebidas: {len(fuentes)}")
    if not fuentes:
        error("ninguna fuente embebida: el PDF dependeria de las del lector")
    # Los encabezados se dibujan con espaciado entre letras, asi que al extraer
    # el texto salen como "RE S U M E N": se compara sin espacios.
    texto = "".join(p.extract_text() or "" for p in lector.pages)
    plano = "".join(texto.split()).lower()
    for esperado in ("RESUMEN", "Disponible"):
        if esperado.lower().replace(" ", "") not in plano:
            error(f"no aparece «{esperado}» en el texto del reporte")


def revisar_xlsx(ruta: str) -> None:
    import openpyxl

    print(f"XLSX {ruta}")
    with zipfile.ZipFile(ruta) as zf:
        if zf.testzip() is not None:
            error("el paquete zip esta danado")
        entradas = zf.namelist()
    if entradas[0] != "[Content_Types].xml":
        error("[Content_Types].xml tiene que ser la primera entrada del zip")

    libro = openpyxl.load_workbook(ruta)
    print(f"  hojas: {len(libro.sheetnames)}")
    for nombre in libro.sheetnames:
        if len(nombre) > 31:
            error(f"el nombre de hoja «{nombre}» pasa de 31 caracteres")
        if any(c in nombre for c in "\\/?*[]:"):
            error(f"el nombre de hoja «{nombre}» usa un caracter prohibido")

    for hoja in libro.worksheets:
        if not hoja.title.lower().startswith(("quincena", "quin.")):
            continue
        subtotales = 0.0
        total = None
        for fila in hoja.iter_rows(min_col=2, max_col=3):
            etiqueta, valor = fila[0].value, fila[1].value
            if etiqueta == "Subtotal" and isinstance(valor, (int, float)):
                subtotales += float(valor)
            if etiqueta == "Total de gasto quincenal" and isinstance(valor, (int, float)):
                total = float(valor)
        if total is not None and abs(subtotales - total) > 0.01:
            error(
                f"en «{hoja.title}» los subtotales suman {subtotales:.2f} "
                f"y el total dice {total:.2f}"
            )
        elif total is not None:
            print(f"  {hoja.title}: subtotales cuadran ({total:,.2f})")

    if "Movimientos" not in libro.sheetnames:
        error("falta la hoja Movimientos")


def revisar_csv(ruta: str) -> None:
    print(f"CSV {ruta}")
    with open(ruta, "rb") as f:
        if f.read(3) != b"\xef\xbb\xbf":
            error("falta la marca de orden de bytes: Excel destroza los acentos")
    with open(ruta, encoding="utf-8-sig", newline="") as f:
        filas = list(csv.reader(f))
    if not filas:
        error("el archivo esta vacio")
        return
    if filas[0] != ESPERADO_CSV:
        error(f"la cabecera no es la esperada: {filas[0]}")
    conteo: dict[str, int] = {}
    for fila in filas[1:]:
        conteo[fila[0]] = conteo.get(fila[0], 0) + 1
    print(f"  filas: {len(filas) - 1} {conteo}")
    for indice, fila in enumerate(filas[1:], start=2):
        if len(fila) != len(ESPERADO_CSV):
            error(f"la fila {indice} tiene {len(fila)} columnas")
            break
        try:
            float(fila[6])
        except ValueError:
            error(f"la fila {indice} trae un monto que no es numero: {fila[6]!r}")
            break


def revisar_respaldo(ruta: str) -> None:
    print(f"Respaldo {ruta}")
    con = sqlite3.connect(ruta)
    try:
        integridad = con.execute("PRAGMA integrity_check").fetchone()[0]
        version = con.execute("PRAGMA user_version").fetchone()[0]
        gastos = con.execute("SELECT COUNT(*) FROM expense").fetchone()[0]
        quincenas = con.execute("SELECT COUNT(*) FROM quincena").fetchone()[0]
        outbox = con.execute("SELECT COUNT(*) FROM sync_queue").fetchone()[0]
        print(f"  integridad: {integridad}")
        print(f"  version de esquema: {version}")
        print(f"  gastos: {gastos} · quincenas: {quincenas} · outbox: {outbox}")
        if integridad != "ok":
            error("la base del respaldo esta danada")
        if not 1 <= version <= 21:
            error(f"version de esquema fuera de rango: {version}")
        if gastos == 0:
            error("el respaldo no tiene ningun movimiento")
    finally:
        con.close()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--pdf")
    ap.add_argument("--xlsx")
    ap.add_argument("--csv")
    ap.add_argument("--backup")
    args = ap.parse_args()

    if not any([args.pdf, args.xlsx, args.csv, args.backup]):
        ap.print_help()
        return 2

    if args.pdf:
        revisar_pdf(args.pdf)
    if args.xlsx:
        revisar_xlsx(args.xlsx)
    if args.csv:
        revisar_csv(args.csv)
    if args.backup:
        revisar_respaldo(args.backup)

    print()
    if fallos:
        print(f"{len(fallos)} problema(s).")
        return 1
    print("Todo en orden.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
