# Absensi Gudang JPLB (Android)

Aplikasi Android dengan **home screen native** (Material 3) yang membuka web app Google Apps Script melalui **Chrome Custom Tabs** — otorisasi perangkat dari profil Chrome tetap terpakai.
`https://script.google.com/macros/s/AKfycbwRz_d-BelbTHUAe_195XoNvUr6YoN4TWMt5f9YJp9I8BHM0Z0frb0ydmSsbvDSeKWF/exec`

## Fitur
- **Otorisasi tersambung ke Chrome** — ID perangkat (`localStorage.jplb_device_id`) dan status terdaftar yang sudah ada di Chrome dipakai ulang; tidak perlu input nomor/otorisasi ulang
- Home native: hero card branding, kartu status otorisasi, tombol aksi Material 3, menu URL/Tentang
- Chrome Custom Tabs: toolbar warna aplikasi, ikon close khusus, animasi transisi slide, paket `com.android.chrome` dipaksa
- Tombol "Mode WebView" membuka `WebActivity` (UI WebView penuh) jika ingin tampilan dalam aplikasi; otorisasi harus didaftarkan sekali di mode ini
- Pull-to-refresh, back/forward/home/reload (mode fallback WebView)
- Menu: muat ulang, buka di browser, ganti URL, bagikan, tentang
- Halaman error + tombol coba lagi saat koneksi bermasalah

## Build
Butuh JDK 17 dan Android SDK (platform 36, build-tools 36.0.0).

```bash
cd "C:/Users/zoldy/Documents/Default Project/absensi-jplb"
./gradlew.bat assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Install
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Ganti URL
Menu ⋮ → **Ganti URL** → masukkan URL deployment baru → Simpan. Tombol **Reset** kembali ke URL bawaan.

Package: `id.jplb.absensi`

## Mode Uji — Mock Location (v1.1.0)
Fitur ini diadaptasi dari pendekatan [lilstiffy/MockGps](https://github.com/lilstiffy/MockGps):
`LocationManager.addTestProvider()` + `setTestProviderLocation()` dari foreground service,

tanpa Google Maps/API key. Cara pakai:
1. Buka menu **Mode Uji (Mock Location)** di home.
2. Isi latitude & longitude, tekan **Mulai Mock**.
3. Jika diminta, di *Developer options → Mock location app / Select mock location app*, pilih aplikasi ini.
4. Tekan **Hentikan Mock** untuk kembali ke GPS asli.

Catatan: ini hanya untuk pengujian fitur berbasis lokasi. Jangan dipakai untuk memalsukan data absensi.
