#!/usr/bin/env python3
"""Purga los DATOS del household en Firestore y re-siembra desde la golden DB.

Contexto (auditoría runtime 2026-07-09): Firestore acumuló basura de
generaciones anteriores de semilla (imports de estados viejos, quincenas UUID
huérfanas, snapshots de saldo absurdos). Como Auth estuvo deshabilitado hasta
el 2026-07-09, NINGÚN dispositivo real llegó a subir datos propios — todo lo
remoto proviene de seeds admin viejos y de QA, y es reproducible. Este script:

1. Borra los docs de las colecciones de DATOS del household (expenses con su
   subcolección attributions, categories, members, wallets, quincenas,
   income_source, wallet_transfer, savings_goal, loan, installment_plan).
   NO toca `roles`, `invites`, `proposals` (circuito de colaboradores) ni los
   campos del doc household (preserva `createdBy`, ancla de las reglas).
2. Re-siembra desde el asset (== golden, snake_case, sin updated_at → los
   docs leen updatedAt=0 y NUNCA pisan una edición local por LWW).

Uso:
  python scripts/admin/purge_and_reseed.py --service-account <ruta> [--household default_household]
"""
import argparse
import sqlite3
from pathlib import Path

import firebase_admin
from firebase_admin import credentials, firestore

DATA_COLLECTIONS = [
    "expenses", "categories", "members", "wallets", "quincenas",
    "income_source", "wallet_transfer", "savings_goal", "loan",
    "installment_plan",
]

ASSET_DB = Path(__file__).resolve().parents[2] / "app/src/main/assets/budget_database.db"


def purge(db, hid: str) -> None:
    h = db.collection("households").document(hid)
    for name in DATA_COLLECTIONS:
        total = 0
        while True:
            docs = list(h.collection(name).limit(200).stream())
            if not docs:
                break
            batch = db.batch()
            for d in docs:
                if name == "expenses":
                    for a in d.reference.collection("attributions").stream():
                        batch.delete(a.reference)
                batch.delete(d.reference)
            batch.commit()
            total += len(docs)
        print(f"  purgado {name}: {total} docs")


def seed(db, hid: str) -> None:
    conn = sqlite3.connect(str(ASSET_DB))
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()
    h = db.collection("households").document(hid)

    # household: MERGE para preservar createdBy/updatedAt de las reglas.
    row = cur.execute("SELECT * FROM household LIMIT 1").fetchone()
    hd = dict(row)
    hd["id"] = hid
    h.set(hd, merge=True)

    def push(table: str, coll: str, where_household: bool = True) -> None:
        q = f"SELECT * FROM {table}" + (" WHERE household_id = ?" if where_household else "")
        rows = cur.execute(q, (hid,) if where_household else ()).fetchall()
        batch = db.batch()
        n = 0
        for r in rows:
            d = dict(r)
            batch.set(h.collection(coll).document(d["id"]), d)
            n += 1
            if n % 400 == 0:
                batch.commit()
                batch = db.batch()
        batch.commit()
        print(f"  sembrado {coll}: {len(rows)}")

    push("member", "members")
    push("category", "categories", where_household=False)
    push("payment_method", "wallets", where_household=False)
    push("quincena", "quincenas")
    push("income_source", "income_source", where_household=False)
    push("savings_goal", "savings_goal", where_household=False)
    push("loan", "loan", where_household=False)
    push("installment_plan", "installment_plan", where_household=False)
    # recurrence_template NO se siembra: es local-only por decisión documentada
    # (ver RecurrenceTemplateEntity).

    # expenses + attributions (subcolección)
    expenses = cur.execute("SELECT * FROM expense WHERE household_id = ?", (hid,)).fetchall()
    n = 0
    batch = db.batch()
    for e in expenses:
        ed = dict(e)
        ref = h.collection("expenses").document(ed["id"])
        batch.set(ref, ed)
        for a in cur.execute("SELECT * FROM expense_attribution WHERE expense_id = ?", (ed["id"],)).fetchall():
            ad = dict(a)
            batch.set(ref.collection("attributions").document(ad["id"]), ad)
            n += 1
        n += 1
        if n >= 300:
            batch.commit()
            batch = db.batch()
            n = 0
    batch.commit()
    print(f"  sembrado expenses: {len(expenses)} (+ atribuciones)")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--service-account", required=True)
    ap.add_argument("--household", default="default_household")
    args = ap.parse_args()
    cred = credentials.Certificate(args.service_account)
    firebase_admin.initialize_app(cred)
    db = firestore.client()
    print(f"Purgando datos de households/{args.household} …")
    purge(db, args.household)
    print("Re-sembrando desde el asset golden …")
    seed(db, args.household)
    print("Listo.")


if __name__ == "__main__":
    main()
