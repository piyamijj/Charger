#!/usr/bin/env python3
"""Generate original, periodic, mono ambient loops using NumPy and the standard library."""
from pathlib import Path
import wave
import numpy as np

RATE = 22050
DURATION = 16
COUNT = RATE * DURATION
TIME = np.arange(COUNT, dtype=np.float64) / RATE
OUTPUT = Path(__file__).resolve().parents[1] / "app/src/main/res/raw"
OUTPUT.mkdir(parents=True, exist_ok=True)

for track in range(6):
    base = [110.0, 123.5, 98.0, 82.4, 146.8, 164.8][track]
    drone = np.zeros(COUNT)
    for ratio, strength in [(1, .13), (1.5, .045), (2, .035)]:
        frequency = round(base * ratio * DURATION) / DURATION
        drone += strength * np.sin(2 * np.pi * frequency * TIME)
    drone *= .7 + .3 * np.cos(2 * np.pi * TIME / DURATION)
    tones = np.zeros(COUNT)
    rng = np.random.default_rng(420 + track)
    scale = [1, 1.067, 1.185, 1.333, 1.5, 1.6, 1.78]
    for note in range(8):
        start = note * RATE * 2
        length = RATE * 3
        t = np.arange(length) / RATE
        frequency = base * 2 * scale[(note * (track + 2)) % len(scale)]
        envelope = np.exp(-2.1 * t) * (1 - np.exp(-18 * t))
        tone = .065 * envelope * (np.sin(2 * np.pi * frequency * t) + .28 * np.sin(4 * np.pi * frequency * t))
        tone += .003 * envelope * rng.normal(size=length)
        indices = (start + np.arange(length)) % COUNT
        np.add.at(tones, indices, tone)
    wet = tones.copy()
    for delay, gain in [(0.19, .27), (.43, .19), (.79, .12), (1.31, .08), (2.17, .05)]:
        wet += gain * np.roll(tones, round(delay * RATE))
    signal = np.clip(drone + wet, -.95, .95)
    with wave.open(str(OUTPUT / f"ambient{track + 1}.wav"), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(RATE)
        output.writeframes((signal * 32767).astype("<i2").tobytes())
