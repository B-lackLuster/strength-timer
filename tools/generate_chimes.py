"""Generate original soft bell cues, with smooth attack/release and no clipping.

Run with Python's standard library; no external audio assets or dependencies.
"""
import math
from pathlib import Path
import struct
import wave

RATE = 44100
OUT = Path(__file__).resolve().parents[1] / "app/src/main/res/raw"
OUT.mkdir(parents=True, exist_ok=True)

def render(name, notes):
    length = max(start + duration for start, freq, duration in notes) + 0.06
    samples = []
    for i in range(int(length * RATE)):
        t = i / RATE
        value = 0.0
        for start, freq, duration in notes:
            age = t - start
            if 0 <= age < duration:
                attack = min(1.0, age / 0.025)
                release = min(1.0, (duration - age) / 0.12)
                envelope = attack * release * math.exp(-4.5 * age)
                value += envelope * (math.sin(2 * math.pi * freq * age)
                         + 0.16 * math.sin(2 * math.pi * freq * 2 * age)
                         + 0.04 * math.sin(2 * math.pi * freq * 3 * age))
        samples.append(value)
    peak = max(abs(s) for s in samples)
    pcm = b"".join(struct.pack("<h", round(s / peak * 0.48 * 32767)) for s in samples)
    with wave.open(str(OUT / (name + ".wav")), "wb") as out:
        out.setnchannels(1); out.setsampwidth(2); out.setframerate(RATE); out.writeframes(pcm)

render("chime_training", [(0, 523.25, 0.62), (0.16, 659.25, 0.64)])
render("chime_rest", [(0, 440.0, 0.72), (0.19, 349.23, 0.74)])
render("chime_complete", [(0, 523.25, 0.68), (0.18, 659.25, 0.7), (0.36, 783.99, 0.84)])
