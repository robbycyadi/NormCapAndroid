# NormCap Android (screen ke teks, ala normcap Linux)

Draft without direction, dial ENERGY 1 / RHYTHM 1 / MOTION 1. Mode antislop: DURING.

## Fitur v1
- Tombol Capture layar (MediaProjection) + seret pilih area + OCR.
- Bubble mengambang: tap untuk buka auto-capture (struktur siap untuk seleksi lintas aplikasi penuh).
- OCR offline on-device via ML Kit: Latin (Indonesia/Inggris), Chinese, Japanese, Korean.
- Hasil: Salin, Bagikan, Magic (buka link, kirim email, hubungi nomor otomatis).
- State lengkap: loading, error, empty (belum ada hasil), tombol >=48dp, kontras Material.

## Instalasi ringan (tanpa Android Studio)
1. Install JDK 17: `winget install EclipseAdoptium.Temurin.17.JDK`
2. Install Gradle: `winget install Gradle.Gradle`
3. Install Android SDK command-line saja:
   - Download `commandlinetools-win` dari developer.android.com, extract ke `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest`
   - `sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"`
   - `adb --version` untuk cek.
4. Colok HP via USB, aktifkan USB Debugging, cek `adb devices`.
5. Build: klik dua kali `build.bat` atau `gradle assembleDebug`.
6. Install: `adb install -r app\build\outputs\apk\debug\app-debug.apk`.

APK ada di `app\build\outputs\apk\debug\app-debug.apk`.

## Catatan jujur
- Mesin ini belum ada JDK/SDK jadi belum bisa verifikasi build di sini (R-35: belum run, wajib `gradle assembleDebug` di PC kamu).
- Crop pakai skala kasar Canvas ke bitmap, cukup untuk teks normal. Kalau hasil crop meleset di HP resolusi aneh, kabari, saya perbaiki mapping koordinatnya.
- Bubble v1 membuka app auto-capture (1 tap extra). Seleksi langsung di atas aplikasi lain butuh token MediaProjection di service, itu upgrade berikutnya kalau kamu butuh.

## Skipped, add when
- Riwayat scan: skipped, add kalau kamu scan tiap hari dan butuh arsip.
- Winapp/UI test otomatis: skipped, add kalau sudah stabil di HP kamu.
