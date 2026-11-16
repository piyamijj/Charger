#!/usr/bin/env python3
"""Check that each packaged ambient loop has audible PCM samples."""
from pathlib import Path
import array
import math
import sys
import wave

root = Path(__file__).resolve().parents[1] / "app/src/main/res/raw"
for number in range(1, 7):
    path = root / f"ambient{number}.wav"
    with wave.open(str(path), "rb") as track:
        assert track.getsampwidth() == 2 and track.getnchannels() == 1
        samples = track.readframes(track.getnframes())
    values = array.array("h", samples)
    if sys.byteorder != "little":
        values.byteswap()
    peak = max(abs(value) for value in values)
    rms = math.sqrt(sum(value * value for value in values) / len(values))
    assert path.stat().st_size < 3_000_000 and peak > 1000 and rms > 100
    print(f"{path.name}: {path.stat().st_size} bytes, peak={peak}, RMS={rms:.0f}")
