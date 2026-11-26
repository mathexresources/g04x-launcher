#!/usr/bin/env bash
# Sestaví Spotify Connect přijímač (go-librespot) jako statickou binárku pro Android armv7
# → connect/out/libgolibrespot.so (build.sh ho přibalí do APK jako nativní knihovnu).
# Potřebuje Go ≥ 1.25. Úpravy pro Hodiny jsou v overlay/:
#  - Ogg Vorbis dekodér v čistém Go (jfreymuth/oggvorbis) místo libvorbis → bez cgo
#  - FLAC/MP3 bez cgo vypnuté (Spotify posílá Ogg Vorbis)
#  - DNS z proměnné HODINY_DNS (Android nemá /etc/resolv.conf)
set -euo pipefail
cd "$(dirname "$0")"
COMMIT=d13ec6576b9071db5157d641333da3d0477bdf97
SRC=build/go-librespot
mkdir -p out build
if [ ! -d "$SRC/.git" ]; then
  git clone -q https://github.com/devgianlu/go-librespot.git "$SRC"
fi
git -C "$SRC" fetch -q --depth 1 origin "$COMMIT" 2>/dev/null || true
git -C "$SRC" checkout -q -f "$COMMIT"
git -C "$SRC" clean -qfd
cp -r overlay/. "$SRC/"
# ALSA (cgo) jen s cgo, jinak stub
sed -i '1s#.*#//go:build !android \&\& !darwin \&\& !js \&\& !windows \&\& !nintendosdk \&\& cgo#' \
  "$SRC/output/driver-alsa.go" "$SRC/output/mixer-alsa.go"
sed -i '1s#.*#//go:build android || darwin || js || windows || nintendosdk || !cgo#' "$SRC/output/driver-alsa-stub.go"
cd "$SRC"
go get github.com/jfreymuth/oggvorbis@v1.0.6
CGO_ENABLED=0 GOOS=linux GOARCH=arm GOARM=7 go build -trimpath -ldflags "-s -w" -o ../../out/libgolibrespot.so ./cmd/daemon
echo "OK: $(cd ../../out && pwd)/libgolibrespot.so"
