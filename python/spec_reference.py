"""htdemucs _spec/_ispec'in numpy karşılığı. Kotlin DemucsSpec.kt bunun birebir aktarımıdır."""
import numpy as np

NFFT, HOP, FREQ = 4096, 1024, 2048
PAD = HOP // 2 * 3
WIN = (0.5 - 0.5 * np.cos(2 * np.pi * np.arange(NFFT) / NFFT)).astype(np.float64)


def reflect_pad(x, l, r):
    n = len(x)
    idx = np.abs(np.arange(-l, n + r))
    idx = np.where(idx >= n, 2 * (n - 1) - idx, idx)
    return x[idx]


def stft(x):
    """x: (L,) -> (FREQ, T) complex"""
    L = len(x)
    T = -(-L // HOP)
    x1 = reflect_pad(x, PAD, PAD + T * HOP - L)
    x2 = reflect_pad(x1, NFFT // 2, NFFT // 2)
    z = np.empty((FREQ, T), dtype=np.complex128)
    for t in range(T):
        s = (t + 2) * HOP
        z[:, t] = np.fft.rfft(x2[s:s + NFFT] * WIN)[:FREQ] / np.sqrt(NFFT)
    return z


def istft(z, L):
    T = z.shape[1]
    frames = T + 4
    raw = np.zeros((frames - 1) * HOP + NFFT)
    env = np.zeros_like(raw)
    for j in range(frames):
        s = j * HOP
        if 2 <= j < 2 + T:
            spec = np.zeros(NFFT // 2 + 1, dtype=np.complex128)
            spec[:FREQ] = z[:, j - 2]
            raw[s:s + NFFT] += np.fft.irfft(spec * np.sqrt(NFFT), NFFT) * WIN
        env[s:s + NFFT] += WIN ** 2
    a = NFFT // 2 + PAD
    return raw[a:a + L] / env[a:a + L]


if __name__ == "__main__":
    L = 343980
    i = np.arange(L)
    x = 0.5 * np.sin(i * 0.01) + 0.3 * np.sin(i * 0.173)
    z = stft(x)
    print("shape", z.shape)
    y = istft(z, L)
    err = np.abs(x - y)
    print("interior max err", err[4096:-4096].max())
    print("edge max err (beklenen: demucs'ta da kenar kaybı var)", err[:4096].max(), err[-4096:].max())
