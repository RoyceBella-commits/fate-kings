#!/usr/bin/env python3
"""Convert voice sources into mono OGG Vorbis files under assets/fatekings/sounds/voice/.

Usage: python3 tools/convert_audio.py /path/to/ffmpeg [id-prefix]

With an id prefix (e.g. "emiya") only those voices are converted. When the ffmpeg build has no
libvorbis encoder (current Homebrew), the audio is normalised to a mono WAV by ffmpeg and encoded
by oggenc (vorbis-tools) at the same quality.

Sources:
  * tools/.voice-cache/<id>.mp3   (downloaded by fetch_voices.py)
  * 素材/saber-契约胜利之剑.mp3    (user-supplied Excalibur line)
  * 素材/乖离剑.ogg                (user-supplied Enuma Elish line)

Mono output keeps the sounds positional in game. The two signature lines are split at
their natural pauses (found with ffmpeg silencedetect) into a chant part that plays when
charging starts and a true-name part that plays on release.
"""
import pathlib
import shutil
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
CACHE = ROOT / "tools" / ".voice-cache"
ASSETS = ROOT / "素材"
OUT = ROOT / "src" / "main" / "resources" / "assets" / "fatekings" / "sounds" / "voice"

# id -> (source, start seconds, end seconds or None)
TRIMS = {
    # Keep only "ふむ……手に入らぬからこそ、美しいものもある" and drop the joke that follows.
    "gil_defeat_saber": (None, 0.0, 6.2),
}
# Downloaded lines cut in two: id -> (cache id, start, end or None). The source itself is not converted.
SPLITS = {
    # "I am the bone of my sword." | pause | "So as I pray, unlimited blade works."
    "emiya_ubw_chant": ("emiya_np", 0.0, 2.85),
    "emiya_ubw_release": ("emiya_np", 3.45, None),
}
SPLIT_SOURCES = {src for src, _, _ in SPLITS.values()}
SIGNATURE = {
    # 束ねるは星の息吹、輝ける命の奔流 | 受けるが良い | 約束された勝利の剣
    "saber_excalibur_chant": ("saber-契约胜利之剑.mp3", 0.0, 4.45),
    "saber_excalibur_release": ("saber-契约胜利之剑.mp3", 7.25, None),
    # chant (0-2.2 s) | pause | true name (4.3-6.8 s)
    "gil_ea_chant": ("乖离剑.ogg", 0.0, 2.45),
    "gil_ea_release": ("乖离剑.ogg", 4.1, None),
}


_LIBVORBIS = None


def has_libvorbis(ffmpeg: str) -> bool:
    global _LIBVORBIS
    if _LIBVORBIS is None:
        out = subprocess.run([ffmpeg, "-hide_banner", "-encoders"], capture_output=True, text=True).stdout
        _LIBVORBIS = "libvorbis" in out
    return _LIBVORBIS


def convert(ffmpeg: str, src: pathlib.Path, dest: pathlib.Path, start: float, end) -> None:
    cmd = [ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-i", str(src), "-ss", f"{start:.3f}"]
    if end is not None:
        cmd += ["-to", f"{end:.3f}"]
    cmd += ["-af", "loudnorm=I=-15:TP=-1.0:LRA=11,afade=t=in:d=0.02", "-ac", "1", "-ar", "44100"]
    if has_libvorbis(ffmpeg):
        subprocess.run(cmd + ["-c:a", "libvorbis", "-q:a", "5", str(dest)], check=True)
        return
    oggenc = shutil.which("oggenc") or "/opt/homebrew/bin/oggenc"
    with tempfile.TemporaryDirectory() as tmp:
        wav = pathlib.Path(tmp) / "voice.wav"
        subprocess.run(cmd + [str(wav)], check=True)
        subprocess.run([oggenc, "-Q", "-q", "5", "-o", str(dest), str(wav)], check=True)


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    ffmpeg = sys.argv[1]
    prefix = sys.argv[2] if len(sys.argv) > 2 else ""
    OUT.mkdir(parents=True, exist_ok=True)
    count = 0
    for mp3 in sorted(CACHE.glob("*.mp3")):
        vid = mp3.stem
        if vid in SPLIT_SOURCES or not vid.startswith(prefix):
            continue
        _, start, end = TRIMS.get(vid, (None, 0.0, None))
        convert(ffmpeg, mp3, OUT / f"{vid}.ogg", start, end)
        count += 1
    for vid, (src, start, end) in SPLITS.items():
        if not vid.startswith(prefix):
            continue
        convert(ffmpeg, CACHE / f"{src}.mp3", OUT / f"{vid}.ogg", start, end)
        count += 1
    for vid, (name, start, end) in SIGNATURE.items():
        if not vid.startswith(prefix):
            continue
        convert(ffmpeg, ASSETS / name, OUT / f"{vid}.ogg", start, end)
        count += 1
    print(f"converted {count} files into {OUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
