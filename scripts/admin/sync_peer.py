#!/usr/bin/env python3
"""Par de sincronizacion: escribe en Firestore lo que escribiria un SEGUNDO
dispositivo, para verificar la convergencia del pull sin necesidad de un segundo
telefono con sesion de Google (Fase 2 del cierre).

Un dispositivo que edita el hogar no hace mas que escribir estos documentos: el
resto (LWW por updatedAt, lapida por deletedAt, aplicacion por DAO directo) lo
resuelve el dispositivo que los recibe. Por eso este par cubre exactamente el
contrato del pull, entidad por entidad.

Subcomandos:
  create  <entidad>   alta con id determinista de prueba
  edit    <entidad>   edicion con updatedAt nuevo (debe ganar el LWW)
  stale   <entidad>   edicion con updatedAt VIEJO (NO debe aplicarse)
  delete  <entidad>   lapida (deletedAt), que es lo que sobrevive a un offline largo
  show    <entidad>   imprime el documento tal como esta en la nube
  purge               borra de verdad todos los documentos de prueba

Uso:
  python scripts/admin/sync_peer.py --service-account <ruta> --household <hid> create expense
"""
import argparse
import sys
import time

import firebase_admin
from firebase_admin import credentials, firestore

PREFIX = "peer-test-"

# entidad -> (coleccion, campos obligatorios del mapper de Android)
ENTITIES = {
    "expense": ("expenses", lambda h, i, n: {
        "id": i, "householdId": h, "occurredAt": n, "quincenaId": PREFIX + "quincena",
        "categoryId": PREFIX + "category", "concept": "Prueba de convergencia",
        "amountMxn": 123.45, "paymentMethodId": PREFIX + "wallet", "status": "POSTED",
    }),
    "category": ("categories", lambda h, i, n: {
        "id": i, "householdId": h, "code": "PEER.TEST", "displayName": "Prueba peer",
        "kind": "EXPENSE_VARIABLE", "sortOrder": 999,
    }),
    "member": ("members", lambda h, i, n: {
        "id": i, "householdId": h, "displayName": "Peer de prueba",
        "role": "BENEFICIARY_DEPENDENT", "isActive": True,
    }),
    "wallet": ("wallets", lambda h, i, n: {
        "id": i, "householdId": h, "displayName": "Cuenta peer", "kind": "CASH",
        "currentBalanceMxn": 100.0, "openingBalanceMxn": 100.0, "isActive": True,
    }),
    "quincena": ("quincenas", lambda h, i, n: {
        "id": i, "householdId": h, "year": 2026, "month": 9, "half": "FIRST",
        "startDate": "2026-09-01", "endDate": "2026-09-15", "label": "Peer Q1",
        "status": "CLOSED",
    }),
    "income": ("income_source", lambda h, i, n: {
        "id": i, "householdId": h, "quincenaId": PREFIX + "quincena",
        "memberId": PREFIX + "member", "label": "Ingreso peer", "amountMxn": 500.0,
        "expectedDate": "2026-09-10", "status": "PLANNED", "cadence": "BIWEEKLY",
    }),
    "transfer": ("wallet_transfer", lambda h, i, n: {
        "id": i, "householdId": h, "fromPaymentMethodId": PREFIX + "wallet",
        "toPaymentMethodId": PREFIX + "wallet", "amountMxn": 50.0, "occurredAt": n,
        "note": "Transferencia peer",
    }),
    "savings": ("savings_goal", lambda h, i, n: {
        "id": i, "householdId": h, "name": "Meta peer", "targetMxn": 1000.0,
        "currentMxn": 0.0,
    }),
    "loan": ("loan", lambda h, i, n: {
        "id": i, "householdId": h, "debtorMemberId": PREFIX + "member",
        "principalMxn": 800.0, "remainingBalanceMxn": 800.0, "issuedAt": "2026-09-01",
    }),
    "installment": ("installment_plan", lambda h, i, n: {
        "id": i, "householdId": h, "displayName": "Plan peer", "principalMxn": 1200.0,
        "totalInstallments": 12, "installmentAmountMxn": 100.0,
        "startDate": "2026-09-01", "status": "ACTIVE",
    }),
    "recurrence": ("recurrence_template", lambda h, i, n: {
        "id": i, "householdId": h, "concept": "Plantilla peer",
        "categoryId": PREFIX + "category", "defaultAmountMxn": 300.0, "cadence": "MONTHLY",
        "active": False,
    }),
    "statement": ("statement_import", lambda h, i, n: {
        "id": i, "householdId": h, "walletId": PREFIX + "wallet", "emisor": "Peer",
        "periodoFin": "2026-09-01", "fechaCorte": "2026-09-01", "appliedAt": n,
    }),
    "household": (None, lambda h, i, n: {
        "name": "Test", "currency": "MXN", "timezone": "America/Mexico_City",
    }),
}


def ref(db, hid, entity):
    col, _ = ENTITIES[entity]
    if col is None:
        return db.collection("households").document(hid)
    return db.collection("households").document(hid).collection(col).document(PREFIX + entity)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--service-account", required=True)
    ap.add_argument("--household", required=True)
    ap.add_argument("action", choices=["create", "edit", "stale", "delete", "show", "purge"])
    ap.add_argument("entity", nargs="?", choices=sorted(ENTITIES))
    args = ap.parse_args()

    if not firebase_admin._apps:
        firebase_admin.initialize_app(credentials.Certificate(args.service_account))
    db = firestore.client()
    now = int(time.time() * 1000)

    if args.action == "purge":
        for entity, (col, _) in ENTITIES.items():
            if col is None:
                continue
            ref(db, args.household, entity).delete()
            print("purgado", entity)
        return 0

    if not args.entity:
        print("falta la entidad", file=sys.stderr)
        return 2

    r = ref(db, args.household, args.entity)
    doc_id = PREFIX + args.entity

    if args.action == "show":
        snap = r.get()
        print(args.entity, snap.to_dict() if snap.exists else "(no existe)")
        return 0

    if args.action == "delete":
        # Lapida, no borrado duro: es lo unico que sobrevive a un offline largo,
        # porque el evento REMOVED solo llega a quien esta conectado.
        r.set({"id": doc_id, "householdId": args.household, "deletedAt": now, "updatedAt": now})
        print(f"lapida escrita en {args.entity} (deletedAt={now})")
        return 0

    data = ENTITIES[args.entity][1](args.household, doc_id, now)
    if args.action == "create":
        data["updatedAt"] = now
    elif args.action == "edit":
        data["updatedAt"] = now
        for k in ("concept", "displayName", "label", "name", "emisor", "note"):
            if k in data:
                data[k] = data[k] + " EDITADO"
    elif args.action == "stale":
        # updatedAt del pasado: el gate LWW del pull debe RECHAZARLO.
        data["updatedAt"] = 1
        for k in ("concept", "displayName", "label", "name", "emisor", "note"):
            if k in data:
                data[k] = "NO DEBE APLICARSE"
    r.set(data, merge=(args.entity == "household"))
    print(f"{args.action} {args.entity} updatedAt={data['updatedAt']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
