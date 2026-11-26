# GMM – ARM64 kaynak projesi

Unity IL2CPP Android modu. Çıktı `libmultiplayermod.so`; yalnızca `arm64-v8a` hedeflenir. Bu depo kaynak kodu, testleri, GitHub Actions yapılandırmasını, bağımlılıkları ve SEDA model dosyalarını içerir.

## Derleme ve paketleme

GitHub Actions (`.github/workflows/android.yml`) Android NDK r27c (`27.2.12479018`), CMake/Ninja ve sherpa-onnx `v2.0.3` kullanır. Önce yerel regresyon testlerini, ardından Android ARM64 derlemesini yürütür.

- Çıktı: `dist/arm64-v8a/` altındaki mod ve tüm sherpa/ONNX `.so` bağımlılıkları.
- Model: `dist/assets/gmm_seda/` ve manuel testler için `dist/seda-v0.2-tiny-c32.zip`.
- Android derlemesi için `MM_ENABLE_SEDA=ON` ve `SHERPA_ONNX_ROOT` (sherpa kurulum dizini) gerekir.
- APK paketinde tüm ARM64 `.so` dosyalarını `lib/arm64-v8a/` altına, `dist/assets/gmm_seda/` klasörünü ise `assets/gmm_seda/` altına yerleştir. `assets/bin/Data/datapack.unity3d` ile model klasörünü karıştırma.

Model ilk kullanımda APK assets içinden uygulamanın özel `files/gmm_seda` dizinine çıkarılır. `asset-version.txt`, `tokens.txt` ve `c32/encoder.int8.onnx`, `c32/decoder.onnx`, `c32/joiner.int8.onnx` birlikte bulunmalıdır. Model lisansı ve atıfları `assets/gmm_seda/MODEL-CREDITS.txt` ve `MODEL-INFO.txt` içindedir. Manuel model kurulumu için `tools/install_seda_android.ps1` korunmuştur.

## AI ve Myinstants

- AI menüsünden API anahtarını, oyuncu seçimini, kişiliği ve filtreleri ayarla. Metin AI ve Sesli AI aynı anda etkin tutulmaz.
- Sesli AI: SEDA yerel tanıma, Gemini metin üretimi ve Android TTS seslendirme ayrı işleyiciler kullanır. Sesli cevap metni oynatma başlarken sohbet kuyruğuna alınır; sohbet beklemedeyse normal gönderim izni beklenir. Ses üretimi/oynatımı başarısız olursa metin yine sıraya alınır. Ses girdisi için en az üç anlamlı kelime kontrolü vardır. Gemini'ye ham ses değil, işlenen metin gönderilir.
- Otomatik konuşmacı seçimi erişilebilir uzak ses akışlarını ayrı işler. Manuel oyuncu seçimi, ses türü, okuma hızı ve ses tonu ayarları mevcuttur. Türkçe TTS ses paketinin cihazda yüklü olması gerekir.
- Beş modelin sırası `src/chat_bot_engine.h` içindedir. Zaman aşımı veya hatada sonraki modele geçilir; model dinlendirme süresi yoktur.
- Myinstants, AI kapalıyken de kullanılabilir. Favoriler yerel dosyaya kaydedilir. Myinstants ses durdurma düğmesi AI seslendirme kuyruğuna müdahale etmez.
- Oda ses yayını, oyunun ses kanalının açık ve bağlı olmasını gerektirir. Sesli özellikleri odadakilerin bilgisiyle kullan. Çevrim içi servislerin model erişimi, kotası ve yanıt süreleri garanti edilmez.
- Cool Mod yalnızca dekoratif durum satırlarını yönetir. Her olay tek kısa, tamamlanmış satırla, gerçek gönderen adı ve oyunun normal sohbet sınırlarıyla bildirilir. Büyük ekranı kaplayan biçimlendirme kaldırıldı. Sesli AI metinleri dekoratif satırlardan önceliklidir. Metin ve sesli AI durumları ve model geçişleri ortak kuyrukta ayrı bildirilir. Model sırası değişmedi. Gönderim çağrısı ile sohbette görülen yankı ayrı loglanır; belirsiz gönderimler tekrar edilmez. Yalnız daha önce gönderilip geri konmuş taslak geçici olarak ayrılır ve aynen geri yüklenir. Oda değişince ve Cool Mod kapatılınca eski durum kuyruğu temizlenir.

## Yerel testler

```bash
ASAN_OPTIONS=detect_leaks=0 bash tests/run.sh
```

İsteğe bağlı PowerShell tanılama testi:

```bash
python3 tests/diagnostics_test.py --pwsh /path/to/pwsh
```

Testler masaüstü C++/ImGui ve sahte IL2CPP kullanır. Gerçek Android cihazındaki ağ, oyun sürümü, ses ve oda davranışını doğrulamaz.

## Tanılama

```powershell
adb logcat -v threadtime -s multiplayermod:I |
    Select-String "GMM_DIAG cool|GMM_DIAG voice|GMM_DIAG tts"
```

Kapsamlı kayıt için `tools/Collect-GmmDiagnostics.ps1`, çökme için `tools/Collect-GmmCrash.ps1` kullanılabilir. Logları paylaşmadan önce kişisel içerik ve erişim anahtarları açısından kontrol et.

## Kaynak düzeni

- `src/`, `include/`: üretim kaynakları ve başlıklar.
- `tests/`: yerel regresyon testleri ve sahte çalışma zamanı.
- `assets/`: SEDA model dosyaları ve lisans bilgileri.
- `manifest/`: Android manifest yardımcıları.
- `third_party/`: ImGui ve Dobby kaynakları, kendi lisans ve belgeleri.
- `tools/`: tanılama, model kurulum ve mevcut ABI referansları.

Askıya alınan 255 oyuncu limiti ve artık kullanılmayan robotik DSP filtresi bu sürümde derlemeye dahil değildir. Önceki sürümlerin kaynakları kendi ZIP arşivlerinde saklanır.

Sürekli konuşma yaklaşık 4 saniyelik bağımsız ses parçalarına ayrılır; 3 anlamlı kelime şartı ve sessizlikle doğal sonlandırma korunur. ASR ve Gemini işleyicileri bağımsızdır; yoğunlukta kuyruk sınırlıdır.
