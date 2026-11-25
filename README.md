# g04x-launcher – "Hodiny"

A custom home-screen app that turns the **Xiaomi Mi Smart Clock (X04G)** into a Google-free bedside / desk clock:
big clock, alarms, weather, music controls, a standby screen and a one-tap control panel for a home-made thermal printer.

> ⚠️ **This is a vibe-coded app.** It was written in a single evening together with an AI assistant (Claude),
> straight from a chat conversation, tested on exactly one device. It works for me – it may or may not work for you.
> The UI is in Czech. Read the code before you trust it with your morning alarm. 🙂

This README is also the full log of how we got there: from a stock Google clock to LineageOS + this launcher,
in the order we actually did it, including the mistakes.

---

## Contents

1. [What you get](#what-you-get)
2. [The hardware](#the-hardware)
3. [Part 1 – Flashing LineageOS](#part-1--flashing-lineageos)
4. [Part 2 – Making Android usable on 1 GB of RAM](#part-2--making-android-usable-on-1-gb-of-ram)
5. [Part 3 – Apps (Spotify on a 32-bit device)](#part-3--apps-spotify-on-a-32-bit-device)
6. [Part 4 – Installing the launcher](#part-4--installing-the-launcher)
7. [Part 5 – Remote updates](#part-5--remote-updates)
8. [Thermal printer integration](#thermal-printer-integration)
9. [Building from source](#building-from-source)
10. [Lessons learned / gotchas](#lessons-learned--gotchas)
11. [Credits](#credits)

---

## What you get

Design **"Ember"** (since 2.1): warm dark background, amber for alarms & playback, teal for weather & radio,
Space Grotesk numerals, system Roboto text (sharper than Inter on the 480p panel), vector icons instead of emoji.

- **Home screen** (landscape 800×480): large clock, date, name day / birthdays, next alarm with countdown, weather,
  now-playing card, five icon tiles (alarms · Spotify · radio · printer · all apps).
- **Own alarm clock** – the system clock app on the GSI just flashed a black screen, so the launcher has its own.
  Editing in two steps: time on swipeable hour/minute wheels (like Google's clock), then once / repeat days and
  morning print. Ringing runs in a foreground service, so the sound plays even if the alarm screen fails to open
  (stop/snooze also from the notification or any hardware button). The alarm line shows ✓ when Android confirms
  the alarm is scheduled for that exact time, ⚠ (and an immediate fix) when it isn't. "Test alarm in 1 minute"
  in the alarm list walks the whole path.
  Features:
  one-off by default ("ring at the next 6:30"), optional repeat days, snooze 9 min, any hardware button = snooze,
  auto-snooze after 10 min, survives reboots. Ducks Spotify while ringing.
- **Standby** after 15 s without touch. Nothing playing: date, huge time and the alarm (with countdown).
  Six looks to pick from in Settings → Standby (live previews): *Classic*, *Hands* (analog with a seconds hand),
  *Flip* (animated flip cards), *Words* (Czech "skoro čtvrt na jedenáct"), *Sun* (sun / moon travelling over the
  horizon, sunrise & sunset times), *Dots* (LED matrix with a seconds row), and the abstract ones: *Eyes* (two eyes
  that look around, blink and get sleepy at night), *Lava* (drifting colour blobs), *Water* (the screen fills up
  over the hour, the time is cut out of the water), *Planets* (hour planet, minute moon, seconds comet) and
  *Bauhaus* (each digit on its own geometric tile), plus more graphic-design ones: *Mondrian* (layout reshuffles
  every hour, the yellow block fills up over the hour), *Memphis* (80s squiggles and shapes, reshuffled every
  minute), *Swiss* (giant hour, red minutes, grid), *Pop art* (halftone, starburst, comic bubbles) and
  *Konstrukt* (Russian constructivism: red wedge, dial, slanted date bar) – or a different one every day.
  Something playing: the music becomes the main element – big cover art (Spotify album art, radio station logo),
  title, artist and large ⏮ ⏯ ⏭ that work without waking the screen; time, date and alarm move to a small line on top.
  Any touch outside the controls wakes it (the first touch never launches anything).
- **Auto brightness** from the light sensor (log curve, smoothed, ~12 % in the dark up to 100 % in sunlight);
  standby turns red when the *room* is dark (light sensor), not just by the clock.
  **Wave your hand** over the clock to wake it from standby – detected by the proximity sensor *and* by a quick
  dip of the light sensor (the GSI's proximity driver may not work). Live sensor values are shown in ⚙ Settings.
- **Double clap** (optional, ⚙ Settings, needs the microphone permission) = pause / play music, or wake from standby.
  Audio is never stored or sent anywhere – only the loudness of 32 ms blocks is analysed on the device.
  **Burn-in protection**: standby content shifts by a few pixels every minute.
- **Name day & birthdays** under the date ("svátek má Albert · 🎂 zítra Máma (56)") from thermoprint's `GET /api/today`;
  without the server it still shows the name day from svatky.adresa.info.
- **Internet radio** (📻 tile): most-played Czech stations from [radio-browser.info](https://www.radio-browser.info),
  search, ★ favourites; plays in a foreground service with its own MediaSession, so the music card, standby controls
  and ⏮/⏭ (switch between favourites) work like with Spotify. Pauses for the alarm and resumes after. Much lighter than Spotify.
- **Smart bulb** (Yeelight / Mi LED Smart Bulb White and Color) over the LAN, no cloud: 💡 in the dock = toggle,
  long-press = light screen (power, brightness, white temperature 1700–6500 K, colours). **Sunrise**: 10 min before
  an alarm the bulb slowly fades in from dim warm to bright daylight; after stopping the alarm it goes full on.
  Requires "LAN Control" enabled for the bulb in the Yeelight app (same Xiaomi account and region); the bulb is found
  automatically (SSDP) or its IP can be entered in ⚙ Settings.
- **Weather** from [wttr.in](https://wttr.in) (no API key): current temp, condition, min/max, rain chance,
  next hours. Default location is Prague (by coordinates); tap = refresh, long-press = settings.
- **Music card** for whatever is playing (Spotify, SoundCloud, …) via Android MediaSession.
- **Printer tile** – native control panel for [thermoprint-server](https://github.com/mathexresources/thermoprint-server)
  via its API: printer / queue / website status and 8 big print buttons.
- **Morning report on alarm dismiss** – each alarm has a *🖨 print morning report* checkbox; tapping *Vypnout* (stop)
  on such an alarm calls the thermoprint API and prints the morning receipt, instead of printing it at a fixed time.
- **Remote self-update** from GitHub Releases (⚙ Settings → ⟳ Aktualizovat).
- **Watchdog**: after a crash the launcher logs the error and restarts itself within 2 s; every 30 min (and at
  start) it re-checks that the next alarm is scheduled. An alarm nobody reacts to auto-snoozes at most twice,
  then goes quiet – no ringing all weekend when you are away. Status in ⚙ Settings.
- **Settings** on a full screen: sections on the left (weather, printer, light, microphone, system), saved instantly.
- Secondary screens (alarms, radio, light, printer, settings…) return to the home screen in standby after 30 s idle.
- Keeps the screen on, hides system bars, ignores Back.

## The hardware

| | |
|---|---|
| Device | Xiaomi Mi Smart Clock, global variant **X04G** (the Chinese **LX04** has the same hardware) |
| SoC | MediaTek MT8167, 4× Cortex-A35 @ 1.5 GHz |
| RAM / storage | 1 GB / ~3.7 GB eMMC |
| Display | 4″ 800×480 touchscreen |
| Stock OS | Android Go 10 with Google Assistant |
| Buttons | Vol+, Vol−, Mute (top) |
| Port | micro-USB (power + data, no battery) |

---

## Part 1 – Flashing LineageOS

Based on [octathorp/x04g_tools](https://github.com/octathorp/x04g_tools), the XDA thread
*"Xiaomi Mi Smart Clock – Development guide, GSI"* and the
[View Assist guide](https://dinki.github.io/View-Assist/docs/supported-devices/android-devices/xiaomi-smart-clock).
Done on **Debian** – Linux is strongly recommended, people report Windows being a pain.

> **Everything below can brick your device. Make the backup. Keep the backup somewhere other than the laptop.**

### 1.1 Install tools

```bash
sudo apt install python3 python3-pip python3-venv git adb fastboot libusb-1.0-0 libfuse2

# ModemManager likes to grab MediaTek devices – disable it
sudo systemctl stop ModemManager
sudo systemctl disable ModemManager

git clone https://github.com/bkerler/mtkclient ~/mtkclient
cd ~/mtkclient
python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt && pip install .

# udev rules so it works without sudo
sudo usermod -aG plugdev,dialout $USER
sudo cp mtkclient/Setup/Linux/*.rules /etc/udev/rules.d/
sudo udevadm control -R
```

Log out and back in, then check: `python mtk.py --help` prints help and `groups` contains `plugdev` and `dialout`.

> 💡 We also ran `sudo udevadm trigger` – it re-enumerates **every** device, and the desktop reset the sound output
> and the touchpad's natural scrolling. Harmless, but you can skip it.

### 1.2 Enter BROM mode and test the connection

Every mtkclient command works the same way: **unplug the clock → run the command → hold Vol+ and plug in the USB cable**
(release when the terminal reacts). Use a real data cable – many micro-USB cables are charge-only.

```bash
cd ~/mtkclient && source venv/bin/activate
mkdir -p ~/x04g_backup
python mtk.py printgpt
```

You should see `CPU: MT8167/MT8516/MT8362`, the Kamakiri exploit running ("Bypassing security") and the partition table
(proinfo, nvram, persist, seccfg, lk, boot, recovery, …, vbmeta, super, cache, userdata).

### 1.3 Full backup (do not skip)

`nvram`, `nvdata`, `proinfo` and `persist` hold the device-unique MAC addresses and calibration – you can't download
them anywhere.

```bash
python mtk.py rf ~/x04g_backup/flash_full.bin                    # whole eMMC, ~3.7 GB
python mtk.py rf ~/x04g_backup/boot1.bin --parttype boot1        # preloader areas (not in the full dump)
python mtk.py rf ~/x04g_backup/boot2.bin --parttype boot2
python mtk.py rl ~/x04g_backup/partitions --skip userdata        # each partition as a separate file
```

Re-plug with Vol+ for each command. (Pasting all four at once is fine – they only read, bash runs them one after
another, you just re-plug when it waits.) Expected sizes: `flash_full.bin` 3.7 G, `boot1/boot2` 2 M each,
`super.bin` 1.5 G.

```bash
cd ~/x04g_backup && sha256sum boot1.bin boot2.bin flash_full.bin partitions/* > SHA256SUMS
# copy it off the laptop, e.g. scp -r ~/x04g_backup pi@192.168.0.101:~/
```

### 1.4 Download LineageOS

The X04G does **not** need the LX04 conversion – go straight to LineageOS. The image from the XDA thread
(androidfilehost, `fid=10620683726822080854`) is **LineageOS 20 (Android 13)** and contains three files:

```
boot.img      8 MB
super.img     1.5 GB   (must be ≤ 1 610 612 736 bytes = size of the super partition)
vbmeta.img    4 KB
```

```bash
mkdir -p ~/x04g_lineage && cd ~/x04g_lineage && unzip ~/Downloads/<file>.zip
```

### 1.5 Unlock the bootloader

The first command that writes to the device (only the small `seccfg` partition, which you have backed up):

```bash
cd ~/mtkclient && source venv/bin/activate
python mtk.py da seccfg unlock        # → "Successfully wrote seccfg."
```

### 1.6 Get into fastboot

1. Unplug. Hold **Vol+ and Vol−** while plugging in → "No Command" screen.
2. Hold **Mute**, tap **Vol+**, release Mute → recovery menu.
3. Vol− to **Enter fastboot**, confirm with Mute (then *Reboot to bootloader* if asked).

This took a few tries – go slowly and let "No Command" sit for a couple of seconds between attempts.
`fastboot devices` should print a serial number.

### 1.7 Flash

One command at a time, wait for `OKAY`:

```bash
cd ~/x04g_lineage
fastboot flash super super.img
fastboot --disable-verity --disable-verification flash vbmeta vbmeta.img
fastboot flash boot boot.img
fastboot -w
fastboot reboot
```

`fastboot -w` on Debian fails with `mkfs.f2fs: Sparse mode is only supported for android` – **that's fine**,
the important part (`Erasing 'userdata' OKAY`) happened and Android formats the empty partition on first boot.

First boot takes 5–10 minutes; a white rectangle on a black screen for a while is normal.

### 1.8 First setup

- Settings → About → tap *Build number* 7× → Developer options → enable **USB debugging** (toggle off/on if needed).
- `adb devices` should show the clock.
- Check the CPU ABI – this matters later:
  ```bash
  adb shell getprop ro.product.cpu.abilist     # → armeabi-v7a,armeabi  (32-bit only!)
  ```

---

## Part 2 – Making Android usable on 1 GB of RAM

Stock was Android Go 10, tuned for this hardware. LineageOS 20 is full Android 13 – after boot we had **36 MB free**
and 320 MB already swapped to zram. Everything here is reversible.

### Screen, rotation, power

```bash
# landscape and ignore apps that force portrait
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb shell wm fixed-to-user-rotation enabled

# no animations
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

# screen never turns off while plugged in, no screensaver
adb shell settings put global stay_on_while_plugged_in 7
adb shell settings put system screen_off_timeout 2147483647
adb shell settings put secure screensaver_enabled 0
adb shell settings put secure screensaver_activate_on_sleep 0
adb shell settings put secure screensaver_activate_on_dock 0

# no battery saver / app standby
adb shell settings put global low_power 0
adb shell settings put global adaptive_battery_management_enabled 0
adb shell dumpsys deviceidle disable
```

Optional: `adb shell wm density 160` (180 if too small). The launcher sizes everything relative to the screen height,
so density only affects other apps and system dialogs.

Settings → Security → Screen lock → **None**.

### Debloat

```bash
for p in org.lineageos.recorder com.android.gallery3d com.android.egg com.android.dreams.phototable \
  com.android.dreams.basic com.stevesoltys.seedvault org.calyxos.backup.contacts com.android.bips \
  com.android.printservice.recommendation com.android.printspooler com.android.nfc com.android.apps.tag \
  com.android.wallpaper.livepicker org.lineageos.backgrounds org.lineageos.jelly com.android.deskclock \
  com.android.calllogbackup com.android.emergency com.android.cameraextensions com.android.dynsystem \
  com.android.stk com.android.cellbroadcastreceiver com.android.cellbroadcastreceiver.module \
  com.android.calculator2 org.lineageos.audiofx; do
  adb shell pm disable-user --user 0 $p
done
# undo any of them: adb shell pm enable <package>
```

> ⚠️ **Don't disable `com.android.launcher3` while using gesture navigation.** Even when another launcher is the
> default home, the stock launcher provides the swipe-home / recents gestures. We did it and the swipe stopped working.
> Either keep it, or switch to 3-button navigation first and then disable it (saves RAM):
> ```bash
> adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton
> adb shell pm disable-user --user 0 com.android.launcher3
> ```

Developer options → **Background process limit → At most 2 processes** helps too (resets on reboot).

Next level, not done yet: root with Magisk and set `ro.config.low_ram=true` (Android Go mode).

---

## Part 3 – Apps (Spotify on a 32-bit device)

```bash
wget https://f-droid.org/F-Droid.apk && adb install F-Droid.apk
```

New apps don't appear on the home screen automatically – they're in the app drawer.

From F-Droid install **Aurora Store** (anonymous Google Play client).

**Spotify:** Aurora says *"not supported"* because the system is 32-bit only (`armeabi-v7a`) and Play doesn't serve
the phone build for it. The fix is the **Spotify for Amazon Fire Tablet** build – Fire tablets have no Google services
and the build is still current and 32-bit. Download the latest `armeabi-v7a` **APK** (not bundle) from APKMirror:

```bash
adb install ~/Downloads/com.spotify.music*.apk
```

Log in with e-mail (not "Continue with Google"). It's still slow on 1 GB of RAM – turn off **Canvas** and set
streaming quality to Normal. Using the clock as a Spotify Connect speaker controlled from your phone is the most
pleasant way.

We tried Olauncher (portrait-only), AGAMA Car Launcher and others – all "meh" – which is why this launcher exists.

---

## Part 4 – Installing the launcher

Grab `Hodiny-<version>.apk` from [Releases](../../releases) (or build it, see below), then:

```bash
adb install -r Hodiny.apk
adb shell cmd package set-home-activity cz.mathex.hodiny/.MainActivity               # default home
adb shell cmd notification allow_listener cz.mathex.hodiny/cz.mathex.hodiny.MediaListener   # music card
adb shell appops set cz.mathex.hodiny SYSTEM_ALERT_WINDOW allow        # alarm can pop over other apps
adb shell appops set cz.mathex.hodiny REQUEST_INSTALL_PACKAGES allow   # self-update
adb shell pm grant cz.mathex.hodiny android.permission.RECORD_AUDIO     # optional: double clap
adb shell dumpsys deviceidle whitelist +cz.mathex.hodiny
adb shell input keyevent KEYCODE_HOME
```

### Using it

| Where | Tap | Long-press |
|---|---|---|
| Clock | go to standby now | – |
| Alarm line / ⏰ tile | alarm list | – |
| Weather | refresh | settings |
| Music card | open the player | – |
| 🖨 tile | printer control page | – |
| ⋯ tile | all apps (⚙ settings in the top bar) | app info |

**Settings** (long-press weather): weather location (village name or `lat,lon`), printer URL, thermoprint API token,
"🖨 test morning print", **⟳ Aktualizovat** (update). Tip for typing the token:
tap the field and run `adb shell input text "TOKEN"`.

**Alarms:** *+ Nový* → pick time on the wheels → by default it rings once at the next occurrence of that time
(the dialog shows "zazvoní zítra v 6:30"); *↻ Opakovat ve dny…* reveals the weekday picker.
A new alarm is pre-filled with the last time you used.

**Debugging:** `adb logcat -d | grep -iE "hodiny|AndroidRuntime" | tail -50`

---

## Part 5 – Remote updates

Once 1.5+ is installed you never need the cable again:

1. Bump `versionCode` and `versionName` in `AndroidManifest.xml`, push to `main`.
2. The GitHub Action [`.github/workflows/release.yml`](.github/workflows/release.yml) builds a signed APK and creates
   Release `v<versionName>` (only if it doesn't exist yet).
3. On the clock: long-press weather → **⟳ Aktualizovat** → confirm the system install dialog.

The Action needs the repo secret **`KEYSTORE_B64`** = `base64 -w0 hodiny.keystore`
(Settings → Secrets and variables → Actions → *Repository secret*). The APK must always be signed with the same key,
otherwise Android refuses to install it over the old version. The keystore is **not** in git – keep it safe.

Without Actions: `./release.sh "release notes"` builds and publishes locally (needs `gh`).

### Wireless ADB (if you still need it)

Developer options → Wireless debugging → *Pair device with pairing code*. Debian's `adb` is too old for `adb pair`
(needs platform-tools ≥ 30), so use Google's:

```bash
wget https://dl.google.com/android/repository/platform-tools-latest-linux.zip && unzip platform-tools-latest-linux.zip
~/platform-tools/adb pair  <ip>:<pairing-port>      # code from the pairing dialog
~/platform-tools/adb connect <ip>:<port>            # a DIFFERENT port, from the main Wireless debugging screen
```

The connect port changes after every reboot – give the clock a DHCP reservation.

---

## Thermal printer integration

The 🖨 tile opens a native screen that talks to [thermoprint-server](https://github.com/mathexresources/thermoprint-server)
over its API (`GET /api/status`, `POST /api/print/<what>`, Bearer token from the launcher settings): printer / queue /
website status and big buttons for morning report, todo, shopping list, site status, Moodle, planes, xkcd and a test
print. (Version ≤ 1.8 used the `/clock` web page in a WebView instead – the native screen is lighter and needs no login.)

Morning report on alarm dismiss: in thermoprint settings untick *"Tisknout každé ráno"* and generate an **API token**,
put it into the launcher settings and tick *🖨 Po vypnutí vytisknout ranní zprávu* on the alarm. On *Vypnout* the
launcher calls `POST /api/print/morning` (Bearer token), retried 5× every 10 s in case Wi-Fi is still waking up,
and shows the result as a toast. Snooze doesn't print (the snoozed alarm remembers the setting).

---

## Building from source

No Gradle, no Android Studio – plain `aapt` + `javac` + `dx` against API 23, because that's what was available.

```bash
sudo apt install android-sdk-platform-23 aapt dalvik-exchange apksigner zipalign default-jdk
./build.sh                # → Hodiny.apk (creates hodiny.keystore on first run if missing)
```

- Java 8, **no lambdas** (the old `dx` can't desugar them) – hence all the anonymous classes.
- `minSdk`/`targetSdk` 23: no runtime-permission or package-visibility hassle, cleartext HTTP to the LAN allowed.
- All UI is built in code (`Ui.java`); every size is a fraction of the screen height, so it's density-independent.

```
src/cz/mathex/hodiny/
  MainActivity.java     home screen, standby, weather, music, alarm line
  Alarms.java           alarm storage (JSON in SharedPreferences) + scheduling via setAlarmClock
  AlarmsActivity.java   alarm list + edit dialog
  AlarmReceiver.java    fires the alarm, reschedules after boot / time change / update
  RingService.java      ringing (foreground service): sound, auto-snooze, stop → morning print + light
  RingActivity.java     ringing screen (snooze / stop buttons only)
  AlarmEditActivity.java  two-step alarm editor; WheelView.java = swipeable number wheel
  StandbyFace.java      the standby looks (all drawn on a Canvas, also used as previews)
  SettingsActivity.java full-screen settings; BaseActivity.java = idle → standby
  PrinterActivity.java  native printer control panel (thermoprint API)
  RadioActivity.java    station list (radio-browser.info), search, favourites
  RadioService.java     radio playback + MediaSession (foreground service)
  AppsActivity.java     app drawer
  Prefs.java            settings dialog, HTTP helper, thermoprint API call
  Updater.java          GitHub Releases check + PackageInstaller session
  UpdateReceiver.java   install result / confirmation dialog
  MediaListener.java    empty NotificationListenerService (needed for MediaSession access)
  ClapDetector.java     double-clap detection from the microphone (loudness only)
  Yeelight.java         Yeelight LAN protocol (SSDP discovery, JSON over TCP 55443)
  LightActivity.java    bulb control screen
  App.java              watchdog (crash restart, periodic alarm check)
  BigSlider.java        full-tile slider
  Ui.java               "Ember" colours, fonts, icons, sizes, helpers
assets/fonts/           Space Grotesk (static Latin-Ext subsets, SIL OFL – licence included)
res/drawable/           vector icons
```

---

## Lessons learned / gotchas

- **Backup first**, and copy it off the laptop. `nvram`/`persist` are irreplaceable.
- Pasting several read-only mtkclient commands at once is harmless; just re-plug with Vol+ for each.
- `fastboot -w` "failing" on Debian is fine.
- The device is **32-bit userspace** – most modern Play builds won't install. Look for `armeabi-v7a` APKs
  (Amazon Fire builds are a good source).
- The GSI's own clock app didn't work (black flash) → write your own alarm.
- Gesture navigation depends on the stock launcher3 – don't disable it unless you switch to 3-button nav.
- `udevadm trigger` resets desktop input/audio settings.
- Old cached data after an update can break parsing – version your cache keys.
- 1 GB of RAM is the real limit. Keep the app count low.

## Credits

- [bkerler/mtkclient](https://github.com/bkerler/mtkclient) – the BROM exploit that makes all of this possible
- [octathorp/x04g_tools](https://github.com/octathorp/x04g_tools) and the XDA development thread
- [View Assist](https://dinki.github.io/View-Assist/) guide for the Xiaomi Smart Clock
- [wttr.in](https://wttr.in) for weather, [radio-browser.info](https://www.radio-browser.info) for radio stations
- [Space Grotesk](https://github.com/floriankarsten/space-grotesk) font (SIL OFL 1.1)
- Written with Claude (Anthropic) in one long chat. Vibe coded, human tested.
