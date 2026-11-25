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

## Lokasi Virtual (v1.2.4)
Fitur ini diadaptasi dari [lilstiffy/MockGps](https://github.com/lilstiffy/MockGps), [FakeTraveler](https://github.com/mcastillof/FakeTraveler), dan [Android-Mock-Location](https://github.com/warren-bank/Android-Mock-Location):
`LocationManager.addTestProvider()` + `setTestProviderLocation()` dari foreground service untuk provider **GPS dan Network**,

tanpa Google Maps/API key. Pemilih peta memakai osmdroid + tile OpenStreetMap, pencarian nama tempat via Nominatim. Cara pakai:
1. Fitur ini tersembunyi untuk pengguna biasa. Aktifkan **mode pengembang** dengan menekan lama teks status versi di home selama ±1 detik, lalu menu **Atur Lokasi Virtual** akan muncul.
2. Isi latitude & longitude manual, atau tekan **Pilih di Peta** untuk memilih titik/ketik nama tempat (pencarian Nominatim, OpenStreetMap tanpa API key).
   - Tekan **Simpan** untuk menyimpan titik sebagai lokasi favorit (disimpan lokal).
   - Chip favorit sekali klik langsung dipakai; long-press chip untuk menghapus.
   - Koordinat bawaan: `-8.633967, 116.440739` (juga ditambahkan sebagai favorit "Lokasi Default" saat pertama dibuka).
   - Quick Settings: tambahkan tile **Lokasi Virtual** lewat tombol pensil → Add a control untuk menyalakan/memativikan tanpa membuka aplikasi.
   - Notifikasi saat aktif memiliki tombol **Matikan**.
3. Jika diminta, di *Developer options → Mock location app / Select mock location app*, pilih aplikasi ini.
4. Tekan **Hentikan Mock** untuk kembali ke GPS asli.

Catatan: lokasi yang dikirim adalah lokasi yang Anda pilih di peta, bukan koordinat GPS fisik perangkat. Android tetap menandai lokasi dari test provider sebagai mock (`isFromMockProvider`).

## OTA Update (GitHub Release)
Aplikasi otomatis mengecek `https://api.github.com/repos/alphacasss/absensi-jplb/releases/latest` saat dibuka.
- Jika ada tag lebih baru, muncul status **Update tersedia** (bisa juga lewat menu ⋮ → Cek Update).
- Tekan status/dialog untuk mengunduh aset `.apk` dari release, lalu pasang lewat FileProvider.
- Android 8+ akan meminta izin *Install unknown apps* jika belum diberikan.
- Debug dan release ditandatangani dengan keystore yang sama agar OTA bisa menggantikan versi terpasang.
