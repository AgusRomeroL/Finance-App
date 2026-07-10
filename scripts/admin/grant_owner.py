#!/usr/bin/env python3
"""Concede rol OWNER de un household a uno o más uids (script de contingencia).

Las reglas solo permiten auto-reclamar OWNER al uid que coincide con
`household.createdBy`. Cuando ese uid ya no existe (reinstalación con auth
anónima nueva, o el titular cambió de cuenta), ningún dispositivo puede
escribir el ledger (PERMISSION_DENIED en todo el push). Este script escribe
`households/{hid}/roles/{uid} = OWNER` con el Admin SDK (bypassa reglas) y
opcionalmente re-estampa `createdBy`.

Uso:
  python scripts/admin/grant_owner.py --service-account <ruta> --uid <uid1> [--uid <uid2> ...] \
      [--household default_household] [--stamp-created-by <uid>]
"""
import argparse
import time

import firebase_admin
from firebase_admin import credentials, firestore


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--service-account", required=True)
    ap.add_argument("--uid", action="append", required=True)
    ap.add_argument("--household", default="default_household")
    ap.add_argument("--stamp-created-by", default=None)
    args = ap.parse_args()

    cred = credentials.Certificate(args.service_account)
    firebase_admin.initialize_app(cred)
    db = firestore.client()
    h = db.collection("households").document(args.household)

    now = int(time.time() * 1000)
    for uid in args.uid:
        h.collection("roles").document(uid).set(
            {"role": "OWNER", "grantedBy": "admin-script", "updatedAt": now}, merge=True
        )
        # Espejo para que la web liste el hogar del usuario.
        db.collection("users").document(uid).collection("households").document(
            args.household
        ).set({"role": "OWNER", "updatedAt": now}, merge=True)
        print(f"OWNER concedido a {uid} en {args.household}")

    if args.stamp_created_by:
        h.set({"createdBy": args.stamp_created_by, "updatedAt": now}, merge=True)
        print(f"createdBy re-estampado a {args.stamp_created_by}")


if __name__ == "__main__":
    main()
