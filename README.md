# AUDIO SEPARATOR STUDIO (Aşama 2 – çalışan temel hat)

Tamamen cihaz üzerinde çalışan, API anahtarı gerektirmeyen AI ses ayrıştırıcı.
Model: **htdemucs** (Demucs v4, MIT) → ONNX Runtime (Android, arm64).
Kanallar: vocals · drums · bass · other.

## Durum (dürüst özet)

| Parça | Durum |
|---|---|
| STFT/iSTFT matematiği (numpy referansı) | Test edildi: iç bölgede 1e-12 hata (`python/spec_reference.py`) |
| FFT + Hermitian tamamlama algoritması | Python'da aynı algoritmayla doğrulandı |
| Kotlin kodu (derleme) | **Henüz derlenmedi** — GitHub Actions ilk derlemesi bunu gösterecek |
| Kotlin birim testleri (FFT, STFT→iSTFT) | CI'da çalışır (`gradle :app:testDebugUnitTest`) |
| ONNX dışa aktarma (`export_htdemucs_onnx.py`) | **Henüz çalıştırılmadı** (torch yok). Script, aktarmadan önce orijinal htdemucs ile eşdeğerliği kendisi doğrular |
| Gerçek şarkıyla cihaz testi, hız, bellek | **Yapılmadı** — sizin cihazınızda ölçülecek |

## 1) Modeli üret

**GitHub'da (bilgisayar gerekmez):** Actions → **Export htdemucs ONNX** → Run workflow. Bitince artifact `htdemucs-onnx` içinde `htdemucs.onnx` ve `verify.txt` olur; ikisini indirin.

**Ya da yerelde:**

```bash
cd python
pip install -r requirements.txt
python export_htdemucs_onnx.py --out htdemucs.onnx      # ~170 MB
python verify_pipeline.py --onnx htdemucs.onnx          # Kotlin hattının aynası ≈ gerçek htdemucs
```
`verify_pipeline.py` çıktılarını (özellikle "STFT max hata" ve "Tam hat max hata") bana iletin.

## 2) APK derle

GitHub'a yükleyin → Actions → **Android APK** → artifact `debug-apk`.
Release için repo secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`,
sonra workflow'u elle çalıştırın.
Yerelde: Android Studio ile açın ya da `gradle :app:assembleDebug` (Gradle 8.9, JDK 17).

## 3) Telefonda

1. `htdemucs.onnx` dosyasını telefona kopyalayın.
2. Uygulamada **Model dosyasını seç** → dosyayı seçin (uygulama deposuna kopyalanır).
3. **Ses dosyası seç** → **AYRIŞTIR**.
4. Sonuçta MIX / sessiz / solo / ses seviyesi, WAV ve ZIP dışa aktarma.

## Bilinen sınırlar (Aşama 2)

- Hız ve bellek ölçülmedi. Öneri: arm64, ≥ 6 GB RAM. Uzun şarkılar (> ~15 dk) bellekte tutulur.
- İptal, segment sınırlarında etkili olur (bir segment ~ onlarca saniye sürebilir).
- Ekran açık tutulur; arka plan servisi Aşama 5'te.
- Model indirme (URL) henüz yok, dosyadan içe aktarma var.
- YouTube, MP3/FLAC dışa aktarma, premium arayüz, Projeler/Kütüphane: sonraki aşamalar.

## Lisanslar

Demucs: MIT. htdemucs ağırlıkları Meta tarafından Demucs deposunda MIT lisansıyla yayımlanır
(yine de dağıtmadan önce depodaki güncel LICENSE'ı kontrol edin). ONNX Runtime: MIT.
