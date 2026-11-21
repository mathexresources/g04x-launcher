# g04x-launcher – „Hodiny“

Vlastní launcher pro **Xiaomi Mi Smart Clock (X04G)** s LineageOS 20 (displej 800×480 na šířku, 32bit ARM).

- velké hodiny, datum a další budík
- počasí z [Open-Meteo](https://open-meteo.com) (bez API klíče), město se nastavuje podržením počasí
- ovládání přehrávané hudby (Spotify i cokoliv jiného) přes MediaSession
- dlaždice: Budík, Spotify, Tiskárna (stránka `/clock` z [thermoprint-server](https://github.com/mathexresources/thermoprint-server)), Počasí, Aplikace
- noční režim 22:00–7:00 po minutě nečinnosti (tlumené červené hodiny), displej jinak nezhasíná

## Sestavení (bez Gradle)

```bash
sudo apt install android-sdk-platform-23 aapt dalvik-exchange apksigner zipalign default-jdk
./build.sh            # → Hodiny.apk
```

Kód je čistá Java 8 bez lambd (kvůli `dx`) proti `android-23`.
Při prvním sestavení se vytvoří `hodiny.keystore` – **schovej si ho**, jinak nepůjde aktualizovat přes `adb install -r` (v gitu není).

## Vydání nové verze (vzdálená aktualizace)

1. Zvyš `versionCode` a `versionName` v `AndroidManifest.xml` a pushni do `main`.
2. GitHub Action (`.github/workflows/release.yml`) sestaví podepsané APK a vytvoří Release `v<verze>`
   (jen pokud ještě neexistuje). Potřebuje secret `KEYSTORE_B64` (base64 z `hodiny.keystore`).
   Alternativa bez Actions: `./release.sh "co je nového"` (lokálně, potřebuje `gh`).
3. Na hodinách: podrž počasí (nebo ⋯ → ⚙ Nastavení) → **⟳ Aktualizovat** → potvrdit instalaci.

APK musí být podepsané stejným `hodiny.keystore`, jinak ho Android odmítne nainstalovat přes starou verzi.

## Instalace na hodiny

```bash
adb install -r Hodiny.apk
adb shell cmd package set-home-activity cz.mathex.hodiny/.MainActivity
adb shell cmd notification allow_listener cz.mathex.hodiny/cz.mathex.hodiny.MediaListener
adb shell appops set cz.mathex.hodiny SYSTEM_ALERT_WINDOW allow        # budík vyskočí i nad jinou aplikací
adb shell appops set cz.mathex.hodiny REQUEST_INSTALL_PACKAGES allow   # aktualizace z GitHubu
```

Ladění: `adb logcat -d | grep -i hodiny | tail -50`
