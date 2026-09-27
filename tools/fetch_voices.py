#!/usr/bin/env python3
"""Download the FGO voice lines listed in tools/voices.json into tools/.voice-cache/.

The lines come from the Mooncell (fgo.wiki) servant voice pages. Only the handful of
lines used by the mod are listed; convert_audio.py turns them into mono OGG Vorbis.
"""
import json
import pathlib
import sys
import time
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent
CACHE = ROOT / ".voice-cache"
UA = "Mozilla/5.0 (fate-kings mod build script)"


def main() -> int:
    voices = json.loads((ROOT / "voices.json").read_text(encoding="utf-8"))
    CACHE.mkdir(exist_ok=True)
    failed = []
    for v in voices:
        dest = CACHE / f"{v['id']}.mp3"
        if dest.exists() and dest.stat().st_size > 0:
            continue
        for attempt in range(2):
            try:
                req = urllib.request.Request(v["url"], headers={"User-Agent": UA})
                with urllib.request.urlopen(req, timeout=30) as r:
                    data = r.read()
                dest.write_bytes(data)
                print(f"ok   {v['id']:<22} {len(data):>7} B")
                break
            except Exception as e:  # noqa: BLE001 - report and retry once
                print(f"fail {v['id']} ({e})")
                time.sleep(1)
        else:
            failed.append(v["id"])
    if failed:
        print("FAILED:", ", ".join(failed))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
