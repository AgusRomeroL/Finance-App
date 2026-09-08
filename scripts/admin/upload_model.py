#!/usr/bin/env python3
"""Publica el modelo Gemma `.litertlm` en el Firebase Storage del proyecto (Fase 4).

Es la pieza que faltaba para que la app deje de depender de `adb push`: baja el
archivo de Hugging Face (repo *gated*: hay que aceptar la licencia Gemma con la
cuenta y usar un token de lectura), calcula su SHA-256, lo sube al bucket bajo
`models/` y regenera `models/manifest.json`, que es lo que la app consulta para
saber cuanto pesa y que huella debe tener.

El token NUNCA se commitea: se lee de `hf_token.local` en la raiz (gitignored) o de
la variable de entorno HF_TOKEN, con el mismo criterio que `service-account.json`.

Uso tipico:
  python scripts/admin/upload_model.py --list --repo <repo de HF>
  python scripts/admin/upload_model.py --variant E2B --repo <repo> --file <archivo>
  python scripts/admin/upload_model.py --manifest-only

El nombre con el que se guarda en el bucket lo fija el catalogo de la app
(`ModelCatalog`), no el nombre del archivo en Hugging Face.
"""
import argparse
import hashlib
import json
import os
import sys
import tempfile
from pathlib import Path

import requests

ROOT = Path(__file__).resolve().parents[2]
BUCKET = "finance-app-abdf9.firebasestorage.app"
PREFIX = "models/"
MANIFEST = PREFIX + "manifest.json"

# Espejo del ModelCatalog de la app: id -> nombre del archivo en el bucket.
VARIANTS = {
    "E2B": "gemma-4-e2b-it.litertlm",
    "E4B": "gemma-4-e4b-it.litertlm",
}

CHUNK = 8 * 1024 * 1024


def hf_token(explicit: str | None) -> str | None:
    if explicit:
        return explicit.strip()
    env = os.environ.get("HF_TOKEN")
    if env:
        return env.strip()
    local = ROOT / "hf_token.local"
    if local.exists():
        return local.read_text(encoding="utf-8").strip()
    return None


def list_repo(repo: str, token: str | None) -> int:
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    r = requests.get(f"https://huggingface.co/api/models/{repo}", headers=headers, timeout=60)
    if r.status_code == 401 or r.status_code == 403:
        print("Hugging Face rechazo el token o falta aceptar la licencia del repo.", file=sys.stderr)
        return 2
    if r.status_code == 404:
        print(f"No existe el repo {repo}.", file=sys.stderr)
        return 2
    r.raise_for_status()
    files = [s["rfilename"] for s in r.json().get("siblings", [])]
    interesting = [f for f in files if f.endswith(".litertlm") or f.endswith(".task")]
    print(f"{repo}: {len(files)} archivos, {len(interesting)} de modelo")
    for f in interesting or files:
        print("  ", f)
    return 0


def download(repo: str, filename: str, token: str | None, dest: Path) -> Path:
    """Baja con reanudacion: son gigabytes y la conexion se cae."""
    url = f"https://huggingface.co/{repo}/resolve/main/{filename}"
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    part = dest.with_suffix(dest.suffix + ".part")
    done = part.stat().st_size if part.exists() else 0
    if done:
        headers["Range"] = f"bytes={done}-"
    with requests.get(url, headers=headers, stream=True, timeout=300, allow_redirects=True) as r:
        if r.status_code in (401, 403):
            raise SystemExit(
                "Hugging Face devolvio %s: acepta la licencia Gemma con tu cuenta y usa un token de lectura."
                % r.status_code
            )
        if r.status_code == 416:
            done = 0
            part.unlink(missing_ok=True)
            raise SystemExit("La descarga previa quedo inconsistente; vuelve a correr el comando.")
        r.raise_for_status()
        mode = "ab" if r.status_code == 206 and done else "wb"
        if mode == "wb":
            done = 0
        total = int(r.headers.get("Content-Length", 0)) + done
        with open(part, mode) as fh:
            for chunk in r.iter_content(CHUNK):
                fh.write(chunk)
                done += len(chunk)
                if total:
                    print(f"\r  {done / 1048576:.0f} de {total / 1048576:.0f} MB", end="", flush=True)
    print()
    part.replace(dest)
    return dest


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        while True:
            block = fh.read(CHUNK)
            if not block:
                break
            h.update(block)
    return h.hexdigest()


def bucket(service_account: str):
    import firebase_admin
    from firebase_admin import credentials, storage

    if not firebase_admin._apps:
        firebase_admin.initialize_app(
            credentials.Certificate(service_account), {"storageBucket": BUCKET}
        )
    return storage.bucket()


def rebuild_manifest(bkt) -> dict:
    """El manifiesto se arma de lo que HAY en el bucket, no de lo que se creia subir."""
    models = []
    for variant_id, file_name in VARIANTS.items():
        blob = bkt.get_blob(PREFIX + file_name)
        if blob is None:
            continue
        blob.reload()
        sha = (blob.metadata or {}).get("sha256")
        if not sha:
            print(f"  aviso: {file_name} no trae sha256 en su metadata, se omite del manifiesto")
            continue
        models.append(
            {"id": variant_id, "file_name": file_name, "size_bytes": blob.size, "sha256": sha}
        )
    manifest = {"models": models}
    payload = json.dumps(manifest, indent=2, ensure_ascii=False)
    bkt.blob(MANIFEST).upload_from_string(payload, content_type="application/json")
    return manifest


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--variant", choices=sorted(VARIANTS) + ["all"], help="variante a publicar")
    ap.add_argument("--repo", help="repo de Hugging Face, por ejemplo litert-community/...")
    ap.add_argument("--file", help="archivo .litertlm dentro del repo")
    ap.add_argument("--local", help="usa un archivo ya descargado en vez de bajarlo")
    ap.add_argument("--work-dir", default=tempfile.gettempdir(), help="donde se descarga")
    ap.add_argument("--token", help="token de Hugging Face (por defecto hf_token.local o HF_TOKEN)")
    ap.add_argument("--service-account", default=str(ROOT / "service-account.json"))
    ap.add_argument("--list", action="store_true", help="lista los archivos del repo y sale")
    ap.add_argument("--manifest-only", action="store_true", help="solo regenera el manifiesto")
    args = ap.parse_args()

    token = hf_token(args.token)

    if args.list:
        if not args.repo:
            print("--list necesita --repo", file=sys.stderr)
            return 2
        return list_repo(args.repo, token)

    if not Path(args.service_account).exists():
        print(f"Falta {args.service_account}.", file=sys.stderr)
        return 2
    bkt = bucket(args.service_account)

    if args.manifest_only:
        print(json.dumps(rebuild_manifest(bkt), indent=2, ensure_ascii=False))
        return 0

    if not args.variant:
        print("Falta --variant (o usa --manifest-only / --list).", file=sys.stderr)
        return 2

    targets = sorted(VARIANTS) if args.variant == "all" else [args.variant]
    if len(targets) > 1 and (args.repo or args.file or args.local):
        print("Con --variant all no se pueden fijar --repo/--file/--local.", file=sys.stderr)
        return 2

    for variant_id in targets:
        file_name = VARIANTS[variant_id]
        print(f"== {variant_id} -> {PREFIX}{file_name}")
        if args.local:
            local = Path(args.local)
            if not local.exists():
                print(f"No existe {local}", file=sys.stderr)
                return 2
        else:
            if not args.repo or not args.file:
                print("Sin --local hay que pasar --repo y --file.", file=sys.stderr)
                return 2
            if not token:
                print("Falta el token de Hugging Face (hf_token.local o HF_TOKEN).", file=sys.stderr)
                return 2
            local = Path(args.work_dir) / file_name
            if local.exists():
                print(f"  ya estaba en {local}, no se vuelve a bajar")
            else:
                print(f"  bajando {args.repo}/{args.file}")
                download(args.repo, args.file, token, local)

        print("  calculando SHA-256")
        digest = sha256(local)
        size = local.stat().st_size
        print(f"  {size} bytes, sha256 {digest}")

        blob = bkt.blob(PREFIX + file_name)
        blob.metadata = {"sha256": digest, "variant": variant_id}
        print("  subiendo al bucket (tarda)")
        blob.upload_from_filename(str(local))
        blob.patch()

    print("== manifiesto")
    print(json.dumps(rebuild_manifest(bkt), indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
