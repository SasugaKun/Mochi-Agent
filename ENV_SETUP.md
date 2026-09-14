# Environment Variables & Keystore Setup Guide (Mochi Agent)

Panduan ini menjelaskan cara mengonfigurasi variabel lingkungan, JDK, dan Keystore untuk **Mochi Agent** setelah di-fork.

---

## 1. Persyaratan Pengembang (Local Setup)

- **JDK:** JDK 17 atau JDK 21 (Rekomendasi: Eclipse Temurin JDK 17/21).
- **Android SDK:** Compile SDK 36, Target SDK 36, Min SDK 26.
- **Android NDK:** Version `28.2.13676358`.

---

## 2. Konfigurasi `local.properties` (Pengembangan Lokal & Signing)

Buat file `local.properties` di direktori akar (root) proyek jika belum ada:

```properties
# Path ke Android SDK lokal
sdk.dir=/path/to/android/sdk

# Konfigurasi Keystore Signing (Rilis APK)
storeFile=/path/to/your/keystore.jks
storePassword=password_keystore_anda
keyAlias=alias_key_anda
keyPassword=password_key_anda
```

*Catatan:* Jika `storeFile` tidak diisi atau bernilai `.`, Gradle secara otomatis akan menggunakan signing key `debug`.

---

## 3. Konfigurasi GitHub Actions CI/CD (Repository Fork)

Agar GitHub Actions pada repository fork Anda dapat membangun (build) dan mendatatangani (sign) APK Rilis secara otomatis, tambahkan **Repository Secrets** pada GitHub:

1. Buka repository fork Anda di GitHub -> **Settings** -> **Secrets and variables** -> **Actions**.
2. Klik **New repository secret** untuk setiap item berikut:

| Secret Name | Deskripsi |
| :--- | :--- |
| `KEYSTORE_BASE64` | String encoded Base64 dari file `.jks` / `.keystore` Anda (`base64 -w 0 release.jks`) |
| `KEYSTORE_PASSWORD` | Kata sandi Keystore |
| `KEY_ALIAS` | Alias kunci dalam Keystore |
| `KEY_PASSWORD` | Kata sandi kunci |

---

## 4. Perintah Build

- **Build Debug APK:**
  ```bash
  ./gradlew assembleFdroidDebug
  ```

- **Build Release APK:**
  ```bash
  ./gradlew assembleFdroidRelease
  ```
