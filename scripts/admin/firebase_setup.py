#!/usr/bin/env python3
"""Configura el proyecto Firebase para la web/PWA (Fase 3 integración 2026-07).

Idempotente. Hace, vía APIs de administración con el service account local
(NUNCA commitear la clave; se lee de --service-account o de la var
GOOGLE_APPLICATION_CREDENTIALS):

1. Registra (o reutiliza) la app WEB "Presupuesto Web" en el proyecto y
   escribe su sdkconfig en web/.env (VITE_FIREBASE_*).
2. Habilita el proveedor anónimo de Authentication (desbloquea también el
   sync de la app Android, que hace signInAnonymously al arrancar).
3. Habilita el proveedor Google con el web client id existente del proyecto.
4. Añade los dominios autorizados de hosting (web.app / firebaseapp.com).

Uso:
  python scripts/admin/firebase_setup.py --service-account <ruta service-account.json>
"""
import argparse
import json
import sys
import time
from pathlib import Path

import google.auth.transport.requests
from google.oauth2 import service_account

PROJECT = "finance-app-abdf9"
WEB_APP_DISPLAY_NAME = "Presupuesto Web"
# Web client OAuth (client_type=3) ya provisionado en el proyecto
# (mismo que usa el Credential Manager de Android para linkWithCredential).
GOOGLE_WEB_CLIENT_ID = "318390629591-rciqtqbdg4588vafmb25q1176iuic2od.apps.googleusercontent.com"
AUTHORIZED_DOMAINS = [
    "localhost",
    f"{PROJECT}.web.app",
    f"{PROJECT}.firebaseapp.com",
]
SCOPES = [
    "https://www.googleapis.com/auth/cloud-platform",
    "https://www.googleapis.com/auth/firebase",
]


def session(sa_path: str):
    creds = service_account.Credentials.from_service_account_file(sa_path, scopes=SCOPES)
    authed = google.auth.transport.requests.AuthorizedSession(creds)
    return authed


def ensure_web_app(s) -> dict:
    """Devuelve el sdkconfig de la app web, creándola si no existe."""
    base = f"https://firebase.googleapis.com/v1beta1/projects/{PROJECT}"
    r = s.get(f"{base}/webApps")
    r.raise_for_status()
    apps = r.json().get("apps", [])
    app = next((a for a in apps if a.get("displayName") == WEB_APP_DISPLAY_NAME), None)
    if app is None and apps:
        app = apps[0]
        print(f"Reutilizando app web existente: {app['displayName']} ({app['appId']})")
    if app is None:
        print("Creando app web…")
        r = s.post(f"{base}/webApps", json={"displayName": WEB_APP_DISPLAY_NAME})
        r.raise_for_status()
        op = r.json()
        # Operación long-running: sondear hasta done.
        for _ in range(30):
            ro = s.get(f"https://firebase.googleapis.com/v1beta1/{op['name']}")
            ro.raise_for_status()
            od = ro.json()
            if od.get("done"):
                app = od["response"]
                break
            time.sleep(2)
        else:
            sys.exit("Timeout esperando la creación de la app web")
        print(f"App web creada: {app['appId']}")
    r = s.get(f"https://firebase.googleapis.com/v1beta1/projects/{PROJECT}/webApps/{app['appId']}/config")
    r.raise_for_status()
    return r.json()


def write_env(cfg: dict, web_dir: Path) -> None:
    env = web_dir / ".env"
    lines = [
        f"VITE_FIREBASE_API_KEY={cfg['apiKey']}",
        f"VITE_FIREBASE_AUTH_DOMAIN={cfg['authDomain']}",
        f"VITE_FIREBASE_PROJECT_ID={cfg['projectId']}",
        f"VITE_FIREBASE_STORAGE_BUCKET={cfg.get('storageBucket', '')}",
        f"VITE_FIREBASE_MESSAGING_SENDER_ID={cfg.get('messagingSenderId', '')}",
        f"VITE_FIREBASE_APP_ID={cfg['appId']}",
    ]
    env.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Escrito {env} (appId {cfg['appId']})")


def enable_auth(s) -> None:
    admin = f"https://identitytoolkit.googleapis.com/admin/v2/projects/{PROJECT}"
    # 1) Config general: anónimo + email opcionalmente no; dominios autorizados.
    r = s.get(f"{admin}/config")
    if r.status_code == 404 or (r.status_code == 400 and "CONFIGURATION_NOT_FOUND" in r.text):
        sys.exit(
            "Identity Platform no está inicializado en el proyecto. Paso manual: "
            "consola Firebase → Authentication → Get started, luego re-corre este script."
        )
    r.raise_for_status()
    cfg = r.json()
    domains = list(dict.fromkeys((cfg.get("authorizedDomains") or []) + AUTHORIZED_DOMAINS))
    patch = {
        "signIn": {"anonymous": {"enabled": True}},
        "authorizedDomains": domains,
    }
    r = s.patch(
        f"{admin}/config",
        params={"updateMask": "signIn.anonymous.enabled,authorizedDomains"},
        json=patch,
    )
    r.raise_for_status()
    print(f"Auth anónimo habilitado; dominios autorizados: {domains}")

    # 2) Proveedor Google.
    idp = f"{admin}/defaultSupportedIdpConfigs/google.com"
    r = s.get(idp)
    body = {"enabled": True, "clientId": GOOGLE_WEB_CLIENT_ID}
    if r.status_code == 404:
        r = s.post(
            f"{admin}/defaultSupportedIdpConfigs",
            params={"idpId": "google.com"},
            json=body,
        )
    else:
        r.raise_for_status()
        existing = r.json()
        if existing.get("clientSecret"):
            body["clientSecret"] = existing["clientSecret"]
        r = s.patch(idp, params={"updateMask": "enabled,clientId" + (",clientSecret" if "clientSecret" in body else "")}, json=body)
    r.raise_for_status()
    print("Proveedor Google habilitado (web client id del proyecto).")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--service-account", required=True)
    ap.add_argument("--web-dir", default=str(Path(__file__).resolve().parents[2] / "web"))
    args = ap.parse_args()
    s = session(args.service_account)
    cfg = ensure_web_app(s)
    write_env(cfg, Path(args.web_dir))
    enable_auth(s)
    print("Fase 3 completa.")


if __name__ == "__main__":
    main()
