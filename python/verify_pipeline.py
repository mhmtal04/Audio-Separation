#!/usr/bin/env python3
"""
Kotlin hattının (STFT -> ONNX -> iSTFT -> toplama) numpy aynasını gerçek htdemucs ile karşılaştırır.
  1) spec_reference.stft  ==  m._spec  (Kotlin DemucsSpec.stft bunun aktarımı)
  2) tam hat (ONNX + numpy iSTFT)  ==  m(mix)

Kullanım: python verify_pipeline.py --onnx htdemucs.onnx [--wav sarki.wav]
"""
import argparse

import numpy as np
import onnxruntime as ort
import torch
from demucs.pretrained import get_model

import spec_reference as sr

SEG = 343980


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--onnx", required=True)
    ap.add_argument("--wav")
    a = ap.parse_args()

    m = get_model("htdemucs").models[0].eval()
    if a.wav:
        import soundfile as sf
        wav, rate = sf.read(a.wav, dtype="float32", always_2d=True)
        assert rate == 44100, "WAV 44.1 kHz olmalı"
        mix = torch.from_numpy(wav.T[:, :SEG].copy())[None]
        assert mix.shape[-1] == SEG, "WAV en az 7.8 sn olmalı"
    else:
        torch.manual_seed(1)
        mix = torch.randn(1, 2, SEG) * 0.1

    with torch.no_grad():
        z_ref = m._spec(mix)[0].numpy()               # (2, 2048, 336) complex
        ref = m(mix)[0].numpy()                       # (4, 2, SEG)

    z_np = np.stack([sr.stft(mix[0, c].numpy().astype(np.float64)) for c in range(2)])
    print("1) STFT max hata:", np.abs(z_np - z_ref).max())

    cac = np.zeros((1, 4, sr.FREQ, z_np.shape[-1]), dtype=np.float32)
    for c in range(2):
        cac[0, 2 * c] = z_np[c].real
        cac[0, 2 * c + 1] = z_np[c].imag
    sess = ort.InferenceSession(a.onnx, providers=["CPUExecutionProvider"])
    freq, time = sess.run(None, {"mix": mix.numpy(), "cac": cac})
    out = np.zeros((4, 2, SEG))
    for s in range(4):
        for c in range(2):
            zc = freq[0, s, 2 * c] + 1j * freq[0, s, 2 * c + 1]
            out[s, c] = sr.istft(zc, SEG) + time[0, s, c]
    d = np.abs(out - ref)
    print("2) Tam hat max hata (tüm):", d.max())
    print("   Tam hat max hata (kenarlar hariç):", d[..., 4096:-4096].max())
    print("   Referans RMS:", np.sqrt((ref ** 2).mean()))


if __name__ == "__main__":
    main()
