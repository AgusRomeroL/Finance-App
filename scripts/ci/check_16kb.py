#!/usr/bin/env python3
"""Revisa que las bibliotecas nativas de un APK sean compatibles con paginas de
16 KB (Fase 8).

Android 15+ en un kernel de paginas de 16 KB solo carga un .so cuyos segmentos
LOAD esten alineados a 16 KB (p_align >= 0x4000). `zipalign -c -P 16` revisa
otra cosa: que el .so este alineado DENTRO del zip, para cargarlo sin
descomprimir. Hacen falta las dos; este script cubre la del ELF, que zipalign no
mira.

Solo cuentan las ABI de 64 bits (arm64-v8a y x86_64): las de 32 bits nunca corren
en un kernel de 16 KB.

Uso:
  python scripts/ci/check_16kb.py app/build/outputs/apk/release/app-release.apk [otro.apk ...]

Sale con 1 si alguna biblioteca de 64 bits incumple.
"""
import struct
import sys
import zipfile

PAGE_16K = 0x4000
PT_LOAD = 1
ABIS_64 = ("arm64-v8a", "x86_64")


def load_alignments(elf: bytes) -> list[int]:
    if elf[:4] != b"\x7fELF":
        raise ValueError("no es un ELF")
    is64 = elf[4] == 2
    endian = "<" if elf[5] == 1 else ">"
    if is64:
        phoff, = struct.unpack_from(endian + "Q", elf, 0x20)
        phentsize, phnum = struct.unpack_from(endian + "HH", elf, 0x36)
    else:
        phoff, = struct.unpack_from(endian + "I", elf, 0x1C)
        phentsize, phnum = struct.unpack_from(endian + "HH", elf, 0x2A)
    aligns = []
    for i in range(phnum):
        off = phoff + i * phentsize
        p_type, = struct.unpack_from(endian + "I", elf, off)
        if p_type != PT_LOAD:
            continue
        if is64:
            p_align, = struct.unpack_from(endian + "Q", elf, off + 0x30)
        else:
            p_align, = struct.unpack_from(endian + "I", elf, off + 0x1C)
        aligns.append(p_align)
    return aligns


def check(apk: str) -> bool:
    ok = True
    print(apk)
    with zipfile.ZipFile(apk) as z:
        for info in sorted(z.infolist(), key=lambda i: i.filename):
            parts = info.filename.split("/")
            if len(parts) != 3 or parts[0] != "lib" or not parts[2].endswith(".so"):
                continue
            abi = parts[1]
            if abi not in ABIS_64:
                continue
            aligns = load_alignments(z.read(info))
            worst = min(aligns) if aligns else 0
            good = worst >= PAGE_16K
            ok &= good
            print(f"  {'OK   ' if good else 'FALLA'} {abi}/{parts[2]}  p_align minimo 0x{worst:x}")
    return ok


def main() -> None:
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    results = [check(apk) for apk in sys.argv[1:]]
    if not all(results):
        print("Hay bibliotecas de 64 bits sin alinear a 16 KB.")
        sys.exit(1)
    print("Todas las bibliotecas de 64 bits estan alineadas a 16 KB.")


if __name__ == "__main__":
    main()
