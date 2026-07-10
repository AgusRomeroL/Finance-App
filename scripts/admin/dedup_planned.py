#!/usr/bin/env python3
"""Deduplica los gastos PLANNED materializados por recurrencia (2026-07-10).

Contexto: RecurrenceMaterializer usaba UUID aleatorio por dispositivo → con
varios dispositivos sincronizando el mismo hogar, cada uno materializó su
copia del mismo (plantilla, día) y el sync las propagó (×4 observado). El fix
de ids deterministas evita NUEVOS duplicados; este script limpia los ya
sembrados en Firestore.

Estrategia: agrupa `expenses` con status=PLANNED y recurrence_template_id
no-nulo por (template, día de occurred_at); conserva el doc de created_at más
antiguo y TOMBSTONEA el resto (deleted_at + updatedAt, borrando su
subcolección attributions) — la lápida propaga el borrado a todos los
dispositivos sin resurrección (mecanismo verificado E2E).

ORDEN CRÍTICO: correr DESPUÉS de instalar el APK con ids deterministas en los
dispositivos activos (y con los emuladores QA apagados/desinstalados); si no,
el siguiente arranque regenera copias aleatorias.

Uso:
  python scripts/admin/dedup_planned.py --service-account <ruta> [--household default_household] [--dry-run]
"""
import argparse
import time
from collections import defaultdict

import firebase_admin
from firebase_admin import credentials, firestore

DAY_MS = 86_400_000


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--service-account", required=True)
    ap.add_argument("--household", default="default_household")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    cred = credentials.Certificate(args.service_account)
    firebase_admin.initialize_app(cred)
    db = firestore.client()
    h = db.collection("households").document(args.household)

    def field(d: dict, camel: str, snake: str):
        return d.get(camel) if d.get(camel) is not None else d.get(snake)

    groups: dict[tuple, list] = defaultdict(list)
    scanned = 0
    for doc in h.collection("expenses").stream():
        d = doc.to_dict() or {}
        scanned += 1
        if field(d, "deletedAt", "deleted_at"):
            continue
        if field(d, "status", "status") != "PLANNED":
            continue
        tpl = field(d, "recurrenceTemplateId", "recurrence_template_id")
        if not tpl:
            continue
        occurred = field(d, "occurredAt", "occurred_at") or 0
        groups[(tpl, int(occurred) // DAY_MS)].append(
            (int(field(d, "createdAt", "created_at") or 0), doc.reference, d.get("concept"))
        )

    now = int(time.time() * 1000)
    kept = removed = 0
    for (tpl, day), rows in sorted(groups.items()):
        if len(rows) < 2:
            kept += len(rows)
            continue
        rows.sort(key=lambda r: r[0])  # conservar el created_at más antiguo
        keep = rows[0]
        kept += 1
        for created, ref, concept in rows[1:]:
            removed += 1
            print(f"  lápida: {concept!r} tpl={tpl[:8]} día={day} doc={ref.id}")
            if args.dry_run:
                continue
            batch = db.batch()
            for a in ref.collection("attributions").stream():
                batch.delete(a.reference)
            batch.set(ref, {
                "id": ref.id,
                "householdId": args.household,
                "deletedAt": now,
                "updatedAt": now,
            })
            batch.commit()

    print(f"\nEscaneados {scanned} expenses. PLANNED de plantilla conservados: {kept}; "
          f"duplicados {'que se tombstonearían' if args.dry_run else 'tombstoneados'}: {removed}.")


if __name__ == "__main__":
    main()
