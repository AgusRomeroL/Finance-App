#!/usr/bin/env python3
"""Repara roles/espejos dañados por el bug de auto-canje (2026-07-10).

El bug: `joinByCode` hacía `set` plano sobre `roles/{uid}` sin guard — el
dueño que canjeaba su propio código se degradaba OWNER→COLLABORATOR y el
espejo `users/{uid}/households/{hid}` quedaba con displayName = hid.

Este script restaura un rol concreto y normaliza espejos. Dry-run por
default; `--apply` para escribir.

Uso:
  python scripts/admin/repair_roles.py --service-account <ruta> \
      --household hh_1ae9cf5c --uid <uid> --role OWNER [--apply]
"""
import argparse

import firebase_admin
from firebase_admin import credentials, firestore


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--service-account", required=True)
    ap.add_argument("--household", required=True)
    ap.add_argument("--uid", required=True)
    ap.add_argument("--role", required=True, choices=["OWNER", "PAYER", "MEMBER", "COLLABORATOR"])
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()

    cred = credentials.Certificate(args.service_account)
    firebase_admin.initialize_app(cred)
    db = firestore.client()

    h = db.collection("households").document(args.household)
    name = (h.get().to_dict() or {}).get("name") or args.household
    role_ref = h.collection("roles").document(args.uid)
    mirror_ref = (
        db.collection("users").document(args.uid)
        .collection("households").document(args.household)
    )

    before_role = role_ref.get().to_dict()
    before_mirror = mirror_ref.get().to_dict()
    print(f"hogar {args.household} (name={name!r})")
    print(f"  rol ANTES:    {before_role}")
    print(f"  espejo ANTES: {before_mirror}")
    print(f"  -> rol={args.role}, espejo.displayName={name!r}")

    if not args.apply:
        print("(dry-run; usa --apply para escribir)")
        return

    role_ref.set({"role": args.role}, merge=True)
    mirror_ref.set({"role": args.role, "displayName": name}, merge=True)
    print("APLICADO.")


if __name__ == "__main__":
    main()
