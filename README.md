# HUB-3A Offline generator — GitHub build projekt

Ovaj projekt koristi GitHub Actions za **Windows portable EXE** i **Android debug APK**. Ne uključuje provjerenu bankovnu certifikaciju.

## Pokretanje
1. Kreirajte **novi** GitHub repository, npr. `HUB3A-Offline-Generator` (nemojte koristiti bankovni projekt).
2. Prenesite sadržaj mape u root repozitorija; workflow mora biti `.github/workflows/build.yml`.
3. Otvorite **Actions → Build Windows EXE and Android APK → Run workflow**.
4. Nakon uspješnih jobova preuzmite oba **Artifacts** paketa s te stranice.

Android debug APK potpisuje razvojni ključ koji GitHub runner generira za svaki build — za redovite nadogradnje istog APK-a potreban je stalan potpisni ključ, za što treba zasebna release konfiguracija. Nije Google Play release.

Windows EXE koristi PyInstaller i u sebe uključuje Ghostscript runtime te BWIPP PDF417. Windows build nije još potvrđen u GitHub runneru. Android koristi ZXing 3.5.3 i izvozi PNG putem Androidovog sistemskog dijaloga, bez mrežnih dozvola.

**Obavezna provjera:** skenirati primjer u mobilnoj banci i usporediti sva polja; bez toga nije potvrđena bankovna kompatibilnost. 

GitHub Actions na javnom repozitoriju i standardnim runnerima nema naplate, a privatni repo koristi mjesečnu kvotu. Izvorni kod ne sadrži stvarne osobne podatke za plaćanje.
