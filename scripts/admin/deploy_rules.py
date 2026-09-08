#!/usr/bin/env python3
"""Despliega las reglas de seguridad por la Firebase Rules API (Fases 2 y 4).

Por que no la CLI: `firebase deploy --only hosting` NO despliega reglas, y
`--only firestore:rules` exige un permiso de serviceusage que el service account
local no tiene. Este script habla directo con la API, reusando el patron de
autenticacion de firebase_setup.py (AuthorizedSession + scopes cloud-platform y
firebase).

Hace dos llamadas por objetivo:
  1. POST /v1/projects/{p}/rulesets      crea un ruleset con el contenido del archivo
  2. PATCH /v1/projects/{p}/releases/... apunta la release a ese ruleset

Objetivos (--target):
  firestore  release cloud.firestore, archivo firestore.rules
  storage    release firebase.storage/<bucket>, archivo storage.rules (Fase 4: el
             modelo de IA vive en el bucket y solo lo lee una sesion iniciada)
  all        los dos

Imprime el ruleset activo ANTES y DESPUES, para dejar rastro de que cambio.

Uso:
  python scripts/admin/deploy_rules.py --service-account <ruta service-account.json>
  python scripts/admin/deploy_rules.py --target storage
  python scripts/admin/deploy_rules.py --dry-run    # solo muestra el ruleset activo
"""
import argparse
import os
import sys
from pathlib import Path

import google.auth.transport.requests
from google.oauth2 import service_account

PROJECT = "finance-app-abdf9"
BUCKET = "finance-app-abdf9.firebasestorage.app"
RELEASES = {
    "firestore": f"projects/{PROJECT}/releases/cloud.firestore",
    "storage": f"projects/{PROJECT}/releases/firebase.storage/{BUCKET}",
}
RULE_FILES = {"firestore": "firestore.rules", "storage": "storage.rules"}
BASE = "https://firebaserules.googleapis.com/v1"
SCOPES = [
    "https://www.googleapis.com/auth/cloud-platform",
    "https://www.googleapis.com/auth/firebase",
]


def session(sa_path: str):
    creds = service_account.Credentials.from_service_account_file(sa_path, scopes=SCOPES)
    return google.auth.transport.requests.AuthorizedSession(creds)


def current_release(s, release: str) -> dict | None:
    r = s.get(f"{BASE}/{release}")
    if r.status_code == 404:
        return None
    r.raise_for_status()
    return r.json()


def create_ruleset(s, source: str, file_name: str) -> str:
    body = {"source": {"files": [{"name": file_name, "content": source}]}}
    r = s.post(f"{BASE}/projects/{PROJECT}/rulesets", json=body)
    if r.status_code >= 400:
        # El error de compilacion viene aqui, con linea y columna: hay que verlo.
        print(r.text, file=sys.stderr)
        r.raise_for_status()
    return r.json()["name"]


def point_release(s, release: str, ruleset_name: str) -> dict:
    body = {"name": release, "rulesetName": ruleset_name}
    r = s.patch(f"{BASE}/{release}", json={"release": body})
    if r.status_code == 404:
        # Primer despliegue del proyecto: no hay release que actualizar.
        r = s.post(f"{BASE}/projects/{PROJECT}/releases", json=body)
    if r.status_code >= 400:
        print(r.text, file=sys.stderr)
        r.raise_for_status()
    return r.json()


def deploy(s, target: str, rules_path: Path, dry_run: bool) -> int:
    release = RELEASES[target]
    before = current_release(s, release)
    print(f"[{target}] ruleset activo ANTES:", (before or {}).get("rulesetName", "(ninguno)"))
    if dry_run:
        return 0
    if not rules_path.exists():
        print(f"[{target}] no existe {rules_path}", file=sys.stderr)
        return 1

    source = rules_path.read_text(encoding="utf-8")
    print(f"[{target}] subiendo {rules_path} ({len(source)} bytes)")
    ruleset = create_ruleset(s, source, RULE_FILES[target])
    print(f"[{target}] ruleset creado:", ruleset)

    after = point_release(s, release, ruleset)
    print(f"[{target}] ruleset activo DESPUES:", after.get("rulesetName"))
    return 0


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    ap = argparse.ArgumentParser()
    ap.add_argument("--service-account", default=os.environ.get("GOOGLE_APPLICATION_CREDENTIALS"))
    ap.add_argument("--target", choices=["firestore", "storage", "all"], default="firestore")
    ap.add_argument("--rules", default=None, help="ruta del archivo de reglas (solo con un target)")
    ap.add_argument("--dry-run", action="store_true", help="solo muestra el ruleset activo")
    args = ap.parse_args()

    if not args.service_account or not Path(args.service_account).exists():
        print("Falta el service account. Pasa --service-account o exporta GOOGLE_APPLICATION_CREDENTIALS.", file=sys.stderr)
        return 2
    if args.rules and args.target == "all":
        print("--rules solo tiene sentido con un target concreto.", file=sys.stderr)
        return 2

    s = session(args.service_account)
    targets = ["firestore", "storage"] if args.target == "all" else [args.target]
    status = 0
    for target in targets:
        path = Path(args.rules) if args.rules else root / RULE_FILES[target]
        status |= deploy(s, target, path, args.dry_run)
    return status


if __name__ == "__main__":
    raise SystemExit(main())
