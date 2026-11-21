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

## Instalace na hodiny

```bash
adb install -r Hodiny.apk
adb shell cmd package set-home-activity cz.mathex.hodiny/.MainActivity
adb shell cmd notification allow_listener cz.mathex.hodiny/cz.mathex.hodiny.MediaListener
```

Ladění: `adb logcat -d | grep -i hodiny | tail -50`
