#!/usr/bin/env python3
"""Despliega firestore.rules por la Firebase Rules API (Fase 2 del cierre).

Por que no la CLI: `firebase deploy --only hosting` NO despliega reglas, y
`--only firestore:rules` exige un permiso de serviceusage que el service account
local no tiene. Este script habla directo con la API, reusando el patron de
autenticacion de firebase_setup.py (AuthorizedSession + scopes cloud-platform y
firebase).

Hace dos llamadas:
  1. POST /v1/projects/{p}/rulesets      crea un ruleset con el contenido del archivo
  2. PATCH /v1/projects/{p}/releases/... apunta cloud.firestore a ese ruleset

Imprime el ruleset activo ANTES y DESPUES, para dejar rastro de que cambio.

Uso:
  python scripts/admin/deploy_rules.py --service-account <ruta service-account.json>
  python scripts/admin/deploy_rules.py --dry-run    # solo muestra el ruleset activo
"""
import argparse
import os
import sys
from pathlib import Path

import google.auth.transport.requests
from google.oauth2 import service_account

PROJECT = "finance-app-abdf9"
RELEASE = f"projects/{PROJECT}/releases/cloud.firestore"
BASE = "https://firebaserules.googleapis.com/v1"
SCOPES = [
    "https://www.googleapis.com/auth/cloud-platform",
    "https://www.googleapis.com/auth/firebase",
]


def session(sa_path: str):
    creds = service_account.Credentials.from_service_account_file(sa_path, scopes=SCOPES)
    return google.auth.transport.requests.AuthorizedSession(creds)


def current_release(s) -> dict | None:
    r = s.get(f"{BASE}/{RELEASE}")
    if r.status_code == 404:
        return None
    r.raise_for_status()
    return r.json()


def create_ruleset(s, source: str) -> str:
    body = {"source": {"files": [{"name": "firestore.rules", "content": source}]}}
    r = s.post(f"{BASE}/projects/{PROJECT}/rulesets", json=body)
    if r.status_code >= 400:
        # El error de compilacion viene aqui, con linea y columna: hay que verlo.
        print(r.text, file=sys.stderr)
        r.raise_for_status()
    return r.json()["name"]


def point_release(s, ruleset_name: str) -> dict:
    body = {"name": RELEASE, "rulesetName": ruleset_name}
    r = s.patch(f"{BASE}/{RELEASE}", json={"release": body})
    if r.status_code == 404:
        # Primer despliegue del proyecto: no hay release que actualizar.
        r = s.post(f"{BASE}/projects/{PROJECT}/releases", json=body)
    if r.status_code >= 400:
        print(r.text, file=sys.stderr)
        r.raise_for_status()
    return r.json()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--service-account", default=os.environ.get("GOOGLE_APPLICATION_CREDENTIALS"))
    ap.add_argument("--rules", default=str(Path(__file__).resolve().parents[2] / "firestore.rules"))
    ap.add_argument("--dry-run", action="store_true", help="solo muestra el ruleset activo")
    args = ap.parse_args()

    if not args.service_account or not Path(args.service_account).exists():
        print("Falta el service account. Pasa --service-account o exporta GOOGLE_APPLICATION_CREDENTIALS.", file=sys.stderr)
        return 2

    s = session(args.service_account)

    before = current_release(s)
    print("ruleset activo ANTES:", (before or {}).get("rulesetName", "(ninguno)"))
    if args.dry_run:
        return 0

    source = Path(args.rules).read_text(encoding="utf-8")
    print(f"subiendo {args.rules} ({len(source)} bytes)")
    ruleset = create_ruleset(s, source)
    print("ruleset creado:", ruleset)

    after = point_release(s, ruleset)
    print("ruleset activo DESPUES:", after.get("rulesetName"))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
