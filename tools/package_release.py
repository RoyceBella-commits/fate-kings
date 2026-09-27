#!/usr/bin/env python3
"""Builds the release zip (after ./gradlew build):

    fate-kings-<version>/
      README.md
      安装与验收说明.md
      fate-kings-<version>.jar
      fabric-api-0.161.0+26.3.jar
      source/            (project sources: no build/, run/, .gradle/, logs/)

Usage: python3 tools/package_release.py [destination directory]
"""
import pathlib
import re
import shutil
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
FABRIC_API = "0.161.0+26.3"
EXCLUDE_DIRS = {"build", "run", ".gradle", "logs", ".idea", ".voice-cache", "__pycache__", ".git"}
EXCLUDE_FILES = {".DS_Store"}


def version() -> str:
    m = re.search(r"^mod_version=(.+)$", (ROOT / "gradle.properties").read_text(encoding="utf-8"), re.M)
    return m.group(1).strip()


def fabric_api_jar() -> pathlib.Path:
    base = pathlib.Path.home() / ".gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api" / FABRIC_API
    for jar in base.rglob(f"fabric-api-{FABRIC_API}.jar"):
        return jar
    raise SystemExit(f"fabric-api {FABRIC_API} not found in the Gradle cache")


def source_files():
    for p in sorted(ROOT.rglob("*")):
        rel = p.relative_to(ROOT)
        if any(part in EXCLUDE_DIRS for part in rel.parts) or p.name in EXCLUDE_FILES or not p.is_file():
            continue
        yield p, rel


def main() -> int:
    ver = version()
    name = f"fate-kings-{ver}"
    jar = ROOT / "build" / "libs" / f"{name}.jar"
    if not jar.exists():
        raise SystemExit(f"{jar} missing: run ./gradlew build first")
    with zipfile.ZipFile(jar) as z:
        bad = [n for n in z.namelist() if "/gametest/" in n or "/checks/" in n]
        if bad:
            raise SystemExit(f"test classes inside the release jar: {bad[:5]}")
    out_dir = ROOT / "build" / "release"
    out_dir.mkdir(parents=True, exist_ok=True)
    zip_path = out_dir / f"{name}.zip"
    api = fabric_api_jar()
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
        z.write(ROOT / "README.md", f"{name}/README.md")
        z.write(ROOT / "安装与验收说明.md", f"{name}/安装与验收说明.md")
        z.write(jar, f"{name}/{jar.name}")
        z.write(api, f"{name}/{api.name}")
        count = 0
        for p, rel in source_files():
            z.write(p, f"{name}/source/{rel.as_posix()}")
            count += 1
    print(f"{zip_path} ({zip_path.stat().st_size / 1e6:.1f} MB, {count} source files)")
    if len(sys.argv) > 1:
        dest = pathlib.Path(sys.argv[1])
        dest.mkdir(parents=True, exist_ok=True)
        shutil.copy2(zip_path, dest / zip_path.name)
        print(f"copied to {dest / zip_path.name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
