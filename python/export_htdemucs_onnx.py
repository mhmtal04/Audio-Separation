#!/usr/bin/env python3
"""
htdemucs (Demucs v4, MIT) -> Android için ONNX çekirdeği.

STFT/iSTFT ONNX'e aktarılmaz (torch.istft dışa aktarılamıyor); Kotlin tarafında yapılır.
ONNX çekirdeği:
  girdi  mix (1,2,343980), cac (1,4,2048,336)
  çıktı  freq (1,4,4,2048,336), time (1,4,2,343980)

Bu script, dışa aktarmadan ÖNCE sarmalayıcının orijinal htdemucs ile eşdeğer olduğunu
kontrol eder; eşleşmezse model üretmez.

Kullanım:  python export_htdemucs_onnx.py --out htdemucs.onnx
"""
import argparse
import sys

import numpy as np
import torch
import torch.nn as nn
from demucs.pretrained import get_model

SEG = 343980
EXPECTED_SOURCES = ["drums", "bass", "other", "vocals"]


class Core(nn.Module):
    """HTDemucs.forward'un _spec ve (_mask, _ispec) dışındaki kısmı (demucs 4.0.1)."""

    def __init__(self, m):
        super().__init__()
        self.m = m

    def forward(self, mix, cac):
        m = self.m
        B, C, Fq, T = cac.shape
        x = cac
        mean = x.mean(dim=(1, 2, 3), keepdim=True)
        std = x.std(dim=(1, 2, 3), keepdim=True)
        x = (x - mean) / (1e-5 + std)
        xt = mix
        meant = xt.mean(dim=(1, 2), keepdim=True)
        stdt = xt.std(dim=(1, 2), keepdim=True)
        xt = (xt - meant) / (1e-5 + stdt)

        saved, saved_t, lengths, lengths_t = [], [], [], []
        for idx, encode in enumerate(m.encoder):
            lengths.append(x.shape[-1])
            inject = None
            if idx < len(m.tencoder):
                lengths_t.append(xt.shape[-1])
                tenc = m.tencoder[idx]
                xt = tenc(xt)
                if not tenc.empty:
                    saved_t.append(xt)
                else:
                    inject = xt
            x = encode(x, inject)
            if idx == 0 and m.freq_emb is not None:
                frs = torch.arange(x.shape[-2], device=x.device)
                emb = m.freq_emb(frs).t()[None, :, :, None].expand_as(x)
                x = x + m.freq_emb_scale * emb
            saved.append(x)

        if m.crosstransformer:
            if m.bottom_channels:
                b, c, f, t = x.shape
                x = x.reshape(b, c, f * t)
                x = m.channel_upsampler(x)
                x = x.reshape(b, -1, f, t)
                xt = m.channel_upsampler_t(xt)
            x, xt = m.crosstransformer(x, xt)
            if m.bottom_channels:
                x = x.reshape(b, -1, f * t)
                x = m.channel_downsampler(x)
                x = x.reshape(b, -1, f, t)
                xt = m.channel_downsampler_t(xt)

        for idx, decode in enumerate(m.decoder):
            skip = saved.pop(-1)
            x, pre = decode(x, skip, lengths.pop(-1))
            offset = m.depth - len(m.tdecoder)
            if idx >= offset:
                tdec = m.tdecoder[idx - offset]
                length_t = lengths_t.pop(-1)
                if tdec.empty:
                    pre = pre[:, :, 0]
                    xt, _ = tdec(pre, None, length_t)
                else:
                    skip = saved_t.pop(-1)
                    xt, _ = tdec(xt, skip, length_t)
        assert len(saved) == 0 and len(lengths_t) == 0 and len(saved_t) == 0

        S = len(m.sources)
        x = x.view(B, S, -1, Fq, T)
        x = x * std[:, None] + mean[:, None]
        L = mix.shape[-1]
        xt = xt.view(B, S, -1, L)
        xt = xt * stdt[:, None] + meant[:, None]
        return x, xt


def full_from_core(m, core, mix):
    z = m._spec(mix)
    cac = m._magnitude(z)  # cac=True -> (B, 2*C, Fq, T) gerçek
    x, xt = core(mix, cac)
    zout = m._mask(z, x)
    return m._ispec(zout, mix.shape[-1]) + xt, cac, x, xt


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="htdemucs.onnx")
    ap.add_argument("--opset", type=int, default=17)
    args = ap.parse_args()

    bag = get_model("htdemucs")
    m = bag.models[0] if hasattr(bag, "models") else bag
    m.eval()
    assert list(m.sources) == EXPECTED_SOURCES, m.sources
    assert m.cac, "Bu script cac=True (htdemucs varsayılanı) varsayar"
    assert m.use_train_segment and int(m.segment * m.samplerate) == SEG, (m.segment, m.samplerate)

    core = Core(m).eval()
    torch.manual_seed(0)
    mix = torch.randn(1, 2, SEG) * 0.1
    with torch.no_grad():
        ref = m(mix)
        mine, cac, x, xt = full_from_core(m, core, mix)
    err = (ref - mine).abs().max().item()
    print(f"[1/3] Sarmalayıcı vs orijinal htdemucs: max hata = {err:.3e}")
    if err > 1e-4:
        sys.exit("HATA: sarmalayıcı orijinalle eşleşmiyor (demucs sürümü 4.0.1 mi?). Model üretilmedi.")
    print("      cac:", tuple(cac.shape), " freq:", tuple(x.shape), " time:", tuple(xt.shape))

    print("[2/3] ONNX dışa aktarma...")
    kw = dict(input_names=["mix", "cac"], output_names=["freq", "time"],
              opset_version=args.opset, do_constant_folding=True)
    try:
        torch.onnx.export(core, (mix, cac), args.out, dynamo=False, **kw)
    except TypeError:
        torch.onnx.export(core, (mix, cac), args.out, **kw)

    print("[3/3] ONNX Runtime ile doğrulama...")
    import onnxruntime as ort
    sess = ort.InferenceSession(args.out, providers=["CPUExecutionProvider"])
    ox, oxt = sess.run(None, {"mix": mix.numpy(), "cac": cac.numpy()})
    e1 = np.abs(ox - x.numpy()).max()
    e2 = np.abs(oxt - xt.numpy()).max()
    print(f"      freq max hata = {e1:.3e}, time max hata = {e2:.3e}")
    if max(e1, e2) > 1e-3:
        sys.exit("HATA: ONNX çıktısı PyTorch ile eşleşmiyor.")
    print(f"TAMAM -> {args.out}")


if __name__ == "__main__":
    main()
