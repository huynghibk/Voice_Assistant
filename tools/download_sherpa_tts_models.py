#!/usr/bin/env python3
"""Prefetch TTS models into local-tts-models/ for adb push (optional dev helper)."""
import shutil
import tarfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "local-tts-models"
RELEASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
# Vietnamese MMS (vie) is not published on Symmetris-labs/tts-mms yet; use Piper 25h below.


def extract_strip_top(tar_path: Path, dest: Path) -> None:
    dest.mkdir(parents=True, exist_ok=True)
    with tarfile.open(tar_path, "r:bz2") as tar:
        for member in tar.getmembers():
            name = member.name.replace("\\", "/").lstrip("/")
            slash = name.find("/")
            rel = name[slash + 1 :] if slash >= 0 else name
            if not rel or rel.endswith("/"):
                continue
            out = dest / rel
            out.parent.mkdir(parents=True, exist_ok=True)
            src = tar.extractfile(member)
            if src is None:
                continue
            out.write_bytes(src.read())


def download(url: str, dest: Path) -> None:
    print(f"Downloading {url} …")
    dest.parent.mkdir(parents=True, exist_ok=True)
    urllib.request.urlretrieve(url, dest)


def main() -> None:
    melo_tar = ROOT / "vits-melo-tts-zh_en.tar.bz2"
    melo_dest = ROOT / "sherpa" / "vits-melo-tts-zh_en"
    download(f"{RELEASE}/vits-melo-tts-zh_en.tar.bz2", melo_tar)
    if melo_dest.exists():
        shutil.rmtree(melo_dest)
    print(f"Extracting to {melo_dest} …")
    extract_strip_top(melo_tar, melo_dest)

    piper_tar = ROOT / "vits-piper-vi_VN-25hours_single-low.tar.bz2"
    piper_dest = ROOT / "piper"
    download(f"{RELEASE}/vits-piper-vi_VN-25hours_single-low.tar.bz2", piper_tar)
    piper_dest.mkdir(parents=True, exist_ok=True)
    print(f"Extracting to {piper_dest} …")
    extract_strip_top(piper_tar, piper_dest)

    print("Done.")


if __name__ == "__main__":
    main()
