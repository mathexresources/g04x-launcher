#!/usr/bin/env bash
# Sestaví Hodiny.apk bez Gradle (Debian/Ubuntu):
#   sudo apt install android-sdk-platform-23 aapt dalvik-exchange apksigner zipalign default-jdk
set -euo pipefail
cd "$(dirname "$0")"
JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
rm -rf build && mkdir -p build/gen build/classes

aapt package -f -M AndroidManifest.xml -S res -A assets -I "$JAR" -J build/gen -F build/unsigned.apk
javac -source 8 -target 8 -encoding UTF-8 -Xlint:-options -bootclasspath "$JAR" -classpath "$JAR" \
      -d build/classes $(find src build/gen -name '*.java')
dalvik-exchange --dex --output=build/classes.dex build/classes
(cd build && aapt add unsigned.apk classes.dex >/dev/null)
zipalign -f 4 build/unsigned.apk build/aligned.apk

# stejný klíč = aktualizace přes `adb install -r` bez odinstalace
[ -f hodiny.keystore ] || keytool -genkeypair -keystore hodiny.keystore -storepass hodiny -keypass hodiny \
    -alias hodiny -keyalg RSA -keysize 2048 -validity 20000 -dname "CN=Hodiny" >/dev/null 2>&1
apksigner sign --ks hodiny.keystore --ks-pass pass:hodiny --key-pass pass:hodiny --out Hodiny.apk build/aligned.apk
echo "OK: $(pwd)/Hodiny.apk"
