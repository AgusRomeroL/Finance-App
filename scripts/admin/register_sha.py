#!/usr/bin/env python3
"""Registra las huellas del certificado de release en Firebase y regenera
app/google-services.json (Fase 8).

Google Sign-In (Credential Manager) solo funciona en un APK cuyo certificado
este registrado en la app Android de Firebase: cada huella SHA-1 crea su cliente
OAuth de Android. Sin la de release, un build firmado falla al iniciar sesion
aunque el de debug funcione.

Las huellas salen del APK YA firmado (apksigner verify --print-certs), asi que
el script nunca toca el keystore ni sus contrasenas. Es idempotente: lista lo
registrado y solo crea lo que falta. Despues descarga la config de la app
Android y reescribe app/google-services.json (config publica de cliente,
versionada a proposito).

Uso:
  python scripts/admin/register_sha.py --dry-run
  python scripts/admin/register_sha.py
  python scripts/admin/register_sha.py --apk ruta/al/app-release.apk
  python scripts/admin/register_sha.py --sha1 AA:BB:... --sha256 CC:DD:...

Lee service-account.json de la raiz (nunca se commitea), como los demas
scripts de admin.
"""
import argparse
import base64
import json
import re
import subprocess
import sys
from pathlib import Path

import google.auth.transport.requests
from google.oauth2 import service_account

ROOT = Path(__file__).resolve().parents[2]
PROJECT = "finance-app-abdf9"
ANDROID_APP_ID = "1:318390629591:android:0c48524ea7923757c0ec92"
BASE = f"https://firebase.googleapis.com/v1beta1/projects/{PROJECT}/androidApps/{ANDROID_APP_ID}"
DEFAULT_APK = ROOT / "app/build/outputs/apk/release/app-release.apk"
SCOPES = ["https://www.googleapis.com/auth/cloud-platform", "https://www.googleapis.com/auth/firebase"]


def normalize(sha: str) -> str:
    return sha.replace(":", "").strip().lower()


def apksigner() -> Path:
    sdk = Path.home() / "AppData/Local/Android/Sdk/build-tools"
    versions = sorted((p for p in sdk.glob("*") if (p / "apksigner.bat").exists()), reverse=True)
    if not versions:
        sys.exit("No encontre apksigner en el SDK (build-tools).")
    return versions[0] / "apksigner.bat"


def fingerprints_from_apk(apk: Path) -> dict:
    if not apk.exists():
        sys.exit(f"No existe {apk}. Compila primero el release firmado.")
    out = subprocess.run(
        [str(apksigner()), "verify", "--print-certs", str(apk)],
        capture_output=True, text=True, check=False,
    )
    if out.returncode != 0:
        sys.exit(f"apksigner no verifico el APK (sin firma?):\n{out.stderr or out.stdout}")
    found = {}
    for kind, label in (("SHA_1", "SHA-1"), ("SHA_256", "SHA-256")):
        m = re.search(rf"Signer #1 certificate {label} digest: ([0-9a-f]+)", out.stdout)
        if m:
            found[kind] = m.group(1)
    signers = len(set(re.findall(r"Signer #(\d+) certificate", out.stdout)))
    if signers != 1 or len(found) != 2:
        sys.exit(f"Esperaba un solo firmante con SHA-1 y SHA-256:\n{out.stdout}")
    return found


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--apk", type=Path, default=DEFAULT_APK, help="APK firmado del que se leen las huellas")
    ap.add_argument("--sha1", help="huella SHA-1 explicita (en vez del APK)")
    ap.add_argument("--sha256", help="huella SHA-256 explicita (en vez del APK)")
    ap.add_argument("--service-account", type=Path, default=ROOT / "service-account.json")
    ap.add_argument("--dry-run", action="store_true", help="solo muestra lo que haria")
    args = ap.parse_args()

    if args.sha1 or args.sha256:
        wanted = {k: normalize(v) for k, v in (("SHA_1", args.sha1), ("SHA_256", args.sha256)) if v}
    else:
        wanted = fingerprints_from_apk(args.apk)
    for kind, sha in wanted.items():
        print(f"Huella de release {kind}: {sha}")

    creds = service_account.Credentials.from_service_account_file(str(args.service_account), scopes=SCOPES)
    s = google.auth.transport.requests.AuthorizedSession(creds)

    r = s.get(f"{BASE}/sha")
    r.raise_for_status()
    registered = {normalize(c["shaHash"]) for c in r.json().get("certificates", [])}
    print(f"Ya registradas: {len(registered)}")

    for kind, sha in wanted.items():
        if sha in registered:
            print(f"  {kind} ya estaba registrada")
            continue
        if args.dry_run:
            print(f"  (dry-run) registraria {kind}")
            continue
        r = s.post(f"{BASE}/sha", json={"shaHash": sha, "certType": kind})
        if r.status_code == 403:
            sys.exit("El service account no puede registrar huellas: hazlo en la consola de Firebase "
                     "(Configuracion del proyecto > Tus apps > mx.budget) y baja el google-services.json.")
        r.raise_for_status()
        print(f"  {kind} registrada")

    if args.dry_run:
        print("(dry-run) no se reescribe app/google-services.json")
        return

    r = s.get(f"{BASE}/config")
    r.raise_for_status()
    contents = base64.b64decode(r.json()["configFileContents"]).decode("utf-8")
    config = json.loads(contents)
    hashes = [
        o.get("android_info", {}).get("certificate_hash")
        for c in config.get("client", [])
        for o in c.get("oauth_client", [])
        if o.get("client_type") == 1
    ]
    print(f"Clientes OAuth de Android en la config: {len(hashes)}")
    if wanted.get("SHA_1") and wanted["SHA_1"] not in hashes:
        print("AVISO: la config todavia no trae el cliente OAuth de la huella nueva. Google tarda unos "
              "minutos en crearlo; vuelve a correr el script.", file=sys.stderr)
    target = ROOT / "app/google-services.json"
    target.write_text(json.dumps(config, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Escrito {target.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
