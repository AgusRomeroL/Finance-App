#!/usr/bin/env python3
"""Imprime las fijaciones del modelo Gemma para `ModelCatalog` (Fase 8, decision 4).

La app descarga el `.litertlm` directamente de Hugging Face, de los repos publicos
de `litert-community` (no piden token ni licencia), con la URL fijada a un commit y
el tamano y el SHA-256 escritos en `app/.../ai/service/ModelCatalog.kt`. Este
script consulta la API de Hugging Face y devuelve esos tres valores listos para
pegar, de modo que cambiar de modelo sea un cambio revisable de codigo y no una
subida a un bucket (el proyecto no tiene bucket de Storage: ver storage.rules).

Antes de la Fase 8 se llamaba `upload_model.py` y subia el archivo al Storage del
proyecto; esa ruta nunca llego a funcionar porque el bucket no existe.

Uso:
  python scripts/admin/pin_model.py                 # las dos variantes del catalogo
  python scripts/admin/pin_model.py --variant E2B   # solo una
  python scripts/admin/pin_model.py --revision <commit>  # una revision concreta
  python scripts/admin/pin_model.py --list          # archivos .litertlm de cada repo

No descarga nada ni escribe archivos: solo lee metadatos publicos.
"""
import argparse
import sys

import requests

API = "https://huggingface.co/api/models"

# Espejo del ModelCatalog de la app: id -> (repo, archivo en el repo). El nombre
# con el que se guarda en el telefono lo fija ModelCatalog.fileName, no este.
VARIANTS = {
    "E2B": ("litert-community/gemma-4-E2B-it-litert-lm", "gemma-4-E2B-it.litertlm"),
    "E4B": ("litert-community/gemma-4-E4B-it-litert-lm", "gemma-4-E4B-it.litertlm"),
}


def repo_info(repo: str, revision: str) -> dict:
    r = requests.get(f"{API}/{repo}/revision/{revision}", params={"blobs": "true"}, timeout=60)
    if r.status_code == 404:
        sys.exit(f"No existe {repo}@{revision}.")
    if r.status_code in (401, 403):
        sys.exit(f"{repo} esta cerrado: la app no puede bajarlo sin token y esta ruta deja de servir.")
    r.raise_for_status()
    info = r.json()
    if info.get("gated"):
        sys.exit(f"{repo} pide aceptar una licencia: la app no puede bajarlo sin token.")
    return info


def pin(variant: str, revision: str) -> None:
    repo, filename = VARIANTS[variant]
    info = repo_info(repo, revision)
    commit = info["sha"]
    blob = next((s for s in info.get("siblings", []) if s["rfilename"] == filename), None)
    if blob is None:
        sys.exit(f"{filename} no esta en {repo}@{commit}.")
    lfs = blob.get("lfs") or {}
    size = lfs.get("size") or blob.get("size")
    sha256 = lfs.get("sha256")
    if not size or not sha256:
        sys.exit(f"Hugging Face no publico tamano o SHA-256 de {filename}.")
    print(f"// {variant}: {repo}@{commit}")
    print(f'downloadUrl = "https://huggingface.co/{repo}/" +')
    print(f'    "resolve/{commit}/{filename}",')
    print(f"sizeBytes = {size:_}L,")
    print(f'sha256 = "{sha256}",')
    print()


def list_files(revision: str) -> None:
    for variant, (repo, _) in VARIANTS.items():
        info = repo_info(repo, revision)
        print(f"{variant}: {repo}@{info['sha']}")
        for s in info.get("siblings", []):
            if s["rfilename"].endswith(".litertlm"):
                size = (s.get("lfs") or {}).get("size") or s.get("size") or 0
                print(f"  {s['rfilename']}  {size / 1e9:.2f} GB")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--variant", choices=sorted(VARIANTS), help="solo esta variante")
    ap.add_argument("--revision", default="main", help="commit o rama de Hugging Face (por defecto main)")
    ap.add_argument("--list", action="store_true", help="lista los .litertlm de cada repo y sale")
    args = ap.parse_args()
    if args.list:
        list_files(args.revision)
        return
    for variant in [args.variant] if args.variant else sorted(VARIANTS):
        pin(variant, args.revision)


if __name__ == "__main__":
    main()
