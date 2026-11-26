package cz.mathex.hodiny;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

/**
 * Spotify Connect přijímač: hodiny se v aplikaci Spotify (mobil, PC) objeví jako reproduktor „Hodiny“.
 * Uvnitř běží go-librespot (statická binárka libgolibrespot.so), který posílá zvuk (PCM) na stdout –
 * tady se přehrává přes AudioTrack. Stav a ovládání přes jeho lokální API (127.0.0.1:3678) a z toho
 * vlastní MediaSession, takže karta přehrávání, standby, barvy obalu i párty mód fungují jako u jiných přehrávačů.
 */
public class SpotifyService extends Service {
    static final int PORT = 3678;
    private static final int RATE = 44100;

    static volatile SpotifyService instance;
    static volatile String status = "vypnuto";   // pro nastavení
    static volatile String user = "";
    private static final ArrayList<String> log = new ArrayList<String>();

    private final Handler h = new Handler(Looper.getMainLooper());
    private volatile Process proc;
    private volatile boolean stopping;
    private AudioTrack track;
    private MediaSession session;
    private android.net.wifi.WifiManager.MulticastLock mlock;
    private android.net.wifi.WifiManager.WifiLock wlock;
    private String artUrl;
    private Bitmap art;
    private boolean hasFocus;
    private boolean dismissed; // vypnuto z hodin (⏻) – neukazovat, dokud znovu nezačne hrát
    private long restartDelay = 3000;

    static boolean enabled(Context c) {
        return Prefs.get(c).getBoolean("spotify_connect", false);
    }

    static boolean available(Context c) {
        return new File(c.getApplicationInfo().nativeLibraryDir, "libgolibrespot.so").exists();
    }

    /** Spustí / zastaví službu podle nastavení. */
    static void sync(Context c) {
        Intent i = new Intent(c, SpotifyService.class);
        if (enabled(c) && available(c)) c.startService(i);
        else c.stopService(i);
    }

    static synchronized String logTail(int lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, log.size() - lines); i < log.size(); i++) sb.append(log.get(i)).append('\n');
        return sb.toString().trim();
    }

    private static synchronized void addLog(String s) {
        log.add(s.length() > 200 ? s.substring(0, 200) : s);
        while (log.size() > 300) log.remove(0);
    }

    /** Patří tenhle ovladač Spotify Connect (a ne rádiu)? */
    static boolean owns(android.media.session.MediaController mc) {
        SpotifyService s = instance;
        return s != null && s.session != null && mc != null && s.session.getSessionToken().equals(mc.getSessionToken());
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Notification n = new Notification.Builder(this).setSmallIcon(R.drawable.ic_music)
                .setContentTitle("Spotify Connect").setContentText("Hodiny jsou připravené jako reproduktor")
                .setPriority(Notification.PRIORITY_MIN).build();
        startForeground(44, n);
        android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        mlock = wm.createMulticastLock("hodiny-spotify");
        mlock.setReferenceCounted(false);
        mlock.acquire(); // zeroconf (mDNS), jinak hodiny nejsou vidět v seznamu zařízení
        wlock = wm.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "hodiny-spotify");
        wlock.setReferenceCounted(false);
        wlock.acquire();

        session = new MediaSession(this, "spotify");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            public void onPlay() { api("/player/resume"); }
            public void onPause() { api("/player/pause"); }
            public void onSkipToNext() { api("/player/next"); }
            public void onSkipToPrevious() { api("/player/prev"); }
            public void onStop() {
                dismissed = true;
                api("/player/pause");
                session.setActive(false);
                abandonFocus();
            }
        });
        startDaemon();
        h.post(poll);
    }

    @Override
    public int onStartCommand(Intent i, int f, int id) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopping = true;
        instance = null;
        h.removeCallbacksAndMessages(null);
        Process p = proc;
        if (p != null) p.destroy();
        if (session != null) session.release();
        if (mlock != null) mlock.release();
        if (wlock != null) wlock.release();
        abandonFocus();
        status = "vypnuto";
        super.onDestroy();
    }

    /** Restart (např. po změně názvu / kvality). */
    static void restart(Context c) {
        SpotifyService s = instance;
        if (s != null && s.proc != null) {
            s.restartDelay = 500;
            s.proc.destroy();
        } else {
            sync(c);
        }
    }

    /** Odhlásit: smazat uložené přihlášení a spustit znovu. */
    static void logout(Context c) {
        File dir = new File(c.getFilesDir(), "spotify");
        new File(dir, "state.json").delete();
        new File(dir, "credentials.json").delete();
        user = "";
        restart(c);
    }

    // ------------------------------------------------------------------ proces go-librespot
    private void writeConfig(File dir) throws Exception {
        android.content.SharedPreferences sp = Prefs.get(this);
        String name = sp.getString("spotify_name", "Hodiny").replace("\"", "");
        int bitrate = sp.getInt("spotify_bitrate", 160);
        String cfg = "device_name: \"" + name + "\"\n"
                + "device_type: speaker\n"
                + "log_level: info\n"
                + "log_disable_timestamp: true\n"
                + "audio_backend: pipe\n"
                + "audio_output_pipe: /proc/self/fd/1\n"
                + "audio_output_pipe_format: s16le\n"
                + "bitrate: " + bitrate + "\n"
                + "initial_volume: 70\n"
                + "zeroconf_enabled: true\n"
                + "credentials:\n  type: zeroconf\n  zeroconf:\n    persist_credentials: true\n"
                + "server:\n  enabled: true\n  address: 127.0.0.1\n  port: " + PORT + "\n";
        OutputStream o = new FileOutputStream(new File(dir, "config.yml"));
        o.write(cfg.getBytes("UTF-8"));
        o.close();
    }

    private String dns() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            android.net.LinkProperties lp = cm.getLinkProperties(cm.getActiveNetwork());
            StringBuilder sb = new StringBuilder();
            if (lp != null) for (java.net.InetAddress a : lp.getDnsServers()) {
                if (a instanceof java.net.Inet4Address) {
                    if (sb.length() > 0) sb.append(',');
                    sb.append(a.getHostAddress());
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void startDaemon() {
        new Thread(new Runnable() {
            public void run() {
                while (!stopping) {
                    runOnce();
                    if (stopping) break;
                    status = "restartuji…";
                    try {
                        Thread.sleep(restartDelay);
                    } catch (InterruptedException e) {
                        break;
                    }
                    restartDelay = Math.min(60000, restartDelay * 2);
                }
            }
        }, "spotify-daemon").start();
    }

    private void runOnce() {
        try {
            File dir = new File(getFilesDir(), "spotify");
            dir.mkdirs();
            killStale();
            new File(dir, "lockfile").delete(); // zbytek po spadlém procesu
            writeConfig(dir);
            String bin = new File(getApplicationInfo().nativeLibraryDir, "libgolibrespot.so").getAbsolutePath();
            ProcessBuilder pb = new ProcessBuilder(bin, "--config_dir", dir.getAbsolutePath());
            pb.environment().put("HOME", getFilesDir().getAbsolutePath());
            pb.environment().put("HODINY_DNS", dns());
            pb.directory(dir);
            status = "spouštím…";
            addLog("--- start " + new java.util.Date());
            final Process p = pb.start();
            proc = p;
            // logy (stderr)
            new Thread(new Runnable() {
                public void run() {
                    try {
                        BufferedReader r = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                        String line;
                        while ((line = r.readLine()) != null) {
                            addLog(line);
                            if (line.contains("authenticated") || line.contains("logged in")) restartDelay = 3000;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }, "spotify-log").start();
            if (status.equals("spouštím…")) status = "běží";
            pump(p.getInputStream());
            int code = p.waitFor();
            addLog("--- konec (kód " + code + ")");
        } catch (Exception e) {
            addLog("chyba: " + e);
            status = "chyba: " + e.getMessage();
        } finally {
            proc = null;
        }
    }

    /** Ukončit go-librespot, který přežil pád aplikace (jinak by držel port a zeroconf). */
    private void killStale() {
        File[] ps = new File("/proc").listFiles();
        if (ps == null) return;
        int me = android.os.Process.myPid();
        for (File f : ps) {
            try {
                int pid = Integer.parseInt(f.getName());
                if (pid == me) continue;
                byte[] b = Prefs.readAll(new java.io.FileInputStream(new File(f, "cmdline")));
                if (new String(b, "UTF-8").contains("libgolibrespot.so")) {
                    android.os.Process.killProcess(pid);
                    addLog("ukončen starý proces " + pid);
                }
            } catch (Exception ignored) {
            }
        }
    }

    /** Zvuk z go-librespot (s16le, 44,1 kHz, stereo) → AudioTrack. */
    private void pump(InputStream in) {
        int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        AudioTrack t = new AudioTrack(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                new AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build(),
                Math.max(min * 3, RATE), AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
        track = t;
        t.play();
        byte[] buf = new byte[8192];
        try {
            int n;
            while ((n = in.read(buf)) > 0) {
                int off = 0;
                while (off < n) {
                    int w = t.write(buf, off, n - off);
                    if (w <= 0) break;
                    off += w;
                }
            }
        } catch (Exception ignored) {
        } finally {
            track = null;
            try {
                t.stop();
            } catch (Exception ignored) {
            }
            t.release();
        }
    }

    // ------------------------------------------------------------------ API a MediaSession
    private void api(final String path) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + PORT + path).openConnection();
                    c.setRequestMethod("POST");
                    c.setConnectTimeout(1500);
                    c.setReadTimeout(3000);
                    c.setDoOutput(true);
                    c.getOutputStream().close();
                    c.getResponseCode();
                    c.disconnect();
                } catch (Exception ignored) {
                }
                h.postDelayed(new Runnable() {
                    public void run() {
                        h.removeCallbacks(poll);
                        h.post(poll);
                    }
                }, 250);
            }
        }).start();
    }

    private final Runnable poll = new Runnable() {
        public void run() {
            new Thread(new Runnable() {
                public void run() {
                    JSONObject st = null;
                    try {
                        HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + PORT + "/status").openConnection();
                        c.setConnectTimeout(1000);
                        c.setReadTimeout(2000);
                        if (c.getResponseCode() == 200) {
                            st = new JSONObject(new String(Prefs.readAll(c.getInputStream()), "UTF-8"));
                        }
                        c.disconnect();
                    } catch (Exception ignored) {
                    }
                    final JSONObject fs = st;
                    h.post(new Runnable() {
                        public void run() {
                            apply(fs);
                        }
                    });
                }
            }).start();
            h.postDelayed(this, 1000);
        }
    };

    private void apply(JSONObject st) {
        if (session == null) return;
        if (st == null) {
            if (proc != null && status.equals("běží")) status = "běží (API zatím neodpovídá)";
            return;
        }
        user = st.optString("username", "");
        status = user.length() > 0 ? "připojeno · " + user : "čeká na připojení ze Spotify";
        JSONObject tr = st.optJSONObject("track");
        boolean stopped = st.optBoolean("stopped", true) || tr == null;
        boolean paused = st.optBoolean("paused", false);
        if (!paused) dismissed = false;
        if (stopped || dismissed) {
            if (session.isActive()) {
                session.setActive(false);
                abandonFocus();
            }
            return;
        }
        String title = tr.optString("name");
        JSONArray artists = tr.optJSONArray("artist_names");
        StringBuilder ar = new StringBuilder();
        if (artists != null) for (int i = 0; i < artists.length(); i++) {
            if (i > 0) ar.append(", ");
            ar.append(artists.optString(i));
        }
        String cover = tr.optString("album_cover_url", "");
        if (cover.length() > 0 && !cover.equals(artUrl)) loadArt(cover);
        MediaMetadata.Builder mb = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, ar.toString())
                .putString(MediaMetadata.METADATA_KEY_ALBUM, tr.optString("album_name"))
                .putLong(MediaMetadata.METADATA_KEY_DURATION, tr.optLong("duration"));
        if (art != null && cover.equals(artUrl)) mb.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art);
        session.setMetadata(mb.build());
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_STOP)
                .setState(paused ? PlaybackState.STATE_PAUSED : PlaybackState.STATE_PLAYING,
                        tr.optLong("position"), paused ? 0 : 1).build());
        if (!session.isActive()) session.setActive(true);
        if (!paused) requestFocus();
        AudioTrack t = track;
        if (t != null && paused && t.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            t.pause();
            t.flush(); // pauza hned, ne až dohraje buffer
        } else if (t != null && !paused && t.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
            t.play();
        }
    }

    private void loadArt(final String url) {
        artUrl = url;
        new Thread(new Runnable() {
            public void run() {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(4000);
                    c.setReadTimeout(6000);
                    final Bitmap b = BitmapFactory.decodeStream(c.getInputStream());
                    c.disconnect();
                    h.post(new Runnable() {
                        public void run() {
                            if (url.equals(artUrl)) art = b;
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    // ------------------------------------------------------------------ audio focus (budík, rádio…)
    private final AudioManager.OnAudioFocusChangeListener focus = new AudioManager.OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int f) {
            if (f == AudioManager.AUDIOFOCUS_LOSS || f == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                hasFocus = false;
                api("/player/pause");
            }
        }
    };

    private void requestFocus() {
        if (hasFocus) return;
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        hasFocus = am.requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonFocus() {
        if (!hasFocus) return;
        ((AudioManager) getSystemService(AUDIO_SERVICE)).abandonAudioFocus(focus);
        hasFocus = false;
    }
}
