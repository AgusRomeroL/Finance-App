#!/usr/bin/env python3
"""Escribe una lápida sobre uno o más documentos de un hogar (válvula de admin).

Por qué existe: cuenta, ingreso, meta y plan MSI **no tienen borrado en ninguna
superficie** (ni la app ni la web). Solo LEEN lápidas, así que si una fila de
esas hay que quitarla, la única vía es escribir la lápida en Firestore desde
fuera y dejar que el pull la propague a cada dispositivo. Es el mismo agujero
que la Fase 2 dejó anotado a propósito, en vez de añadir código inalcanzable.

Cómo funciona: reemplaza el documento por la lápida mínima que produce
`data/remote/Tombstones.kt`, es decir `{id, householdId, deletedAt, updatedAt}`,
con `set` SIN merge. Limpiar el resto de campos es deliberado: así el documento
no es mapeable a una entidad y ningún pull puede aplicarlo como si fuera un
alta. `RemotePullSync` comprueba `deleted_at` ANTES de mapear y borra la fila
local si `max(deletedAt, updatedAt)` remoto es mayor o igual que el
`updated_at` local, de modo que gana el borrado.

Borrar el documento de verdad NO sirve: el evento REMOVED del listener solo
llega a los dispositivos conectados en ese momento, y uno que estuvo offline lo
bastante resucita la fila al re-empujar su copia local.

Para que un dispositivo converja tiene que abrir la app (los listeners del pull
viven en el proceso).

Uso, en dos pasos, porque por defecto NO escribe nada:
  python scripts/admin/tombstone_doc.py --service-account service-account.json \\
      --collection income_source --id <uuid>
  # revisa lo que imprime y, si es lo que esperabas, repite con --yes
"""
import argparse
import json
import time

import firebase_admin
from firebase_admin import credentials, firestore

# Colecciones planas del hogar que el pull sincroniza. Se valida contra esta
# lista para que un dedazo en el nombre no cree una colección nueva y vacía en
# vez de tocar la que se quería.
COLECCIONES = [
    "categories",
    "expenses",
    "income_source",
    "installment_plan",
    "loan",
    "members",
    "quincenas",
    "recurrence_template",
    "savings_goal",
    "statement_import",
    "wallet_transfer",
    "wallets",
]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--service-account", required=True)
    ap.add_argument("--household", default="default_household")
    ap.add_argument("--collection", required=True, choices=COLECCIONES)
    ap.add_argument("--id", action="append", required=True,
                    help="Id del documento. Repetible.")
    ap.add_argument("--yes", action="store_true",
                    help="Escribe de verdad. Sin esto solo imprime lo que haría.")
    args = ap.parse_args()

    cred = credentials.Certificate(args.service_account)
    firebase_admin.initialize_app(cred)
    db = firestore.client()
    col = (
        db.collection("households")
        .document(args.household)
        .collection(args.collection)
    )

    now = int(time.time() * 1000)
    escritos = 0
    for doc_id in args.id:
        ref = col.document(doc_id)
        snap = ref.get()
        if not snap.exists:
            print(f"[{doc_id}] NO EXISTE en {args.household}/{args.collection}")
            continue

        datos = snap.to_dict() or {}
        if datos.get("deletedAt", datos.get("deleted_at", 0)) > 0:
            print(f"[{doc_id}] ya era una lápida; se omite")
            continue

        # Se imprime el contenido antes de tocarlo: la lápida borra el resto de
        # campos y esta es la última oportunidad de ver qué se está quitando.
        print(f"[{doc_id}] contenido actual:")
        print(json.dumps(datos, indent=2, ensure_ascii=False, default=str))

        if not args.yes:
            print(f"[{doc_id}] simulacro: no se escribió nada (usa --yes)\n")
            continue

        ref.set({
            "id": doc_id,
            "householdId": args.household,
            "deletedAt": now,
            "updatedAt": now,
        })
        escritos += 1
        print(f"[{doc_id}] lápida escrita\n")

    if args.yes:
        print(f"Lápidas escritas: {escritos}")
        print("Abre la app en cada dispositivo para que el pull las aplique.")
    else:
        print("Simulacro. Repite con --yes para escribir.")


if __name__ == "__main__":
    main()
