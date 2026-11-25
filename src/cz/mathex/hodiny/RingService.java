package cz.mathex.hodiny;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.lang.ref.WeakReference;

/**
 * Zvonění budíku jako služba na popředí: zvuk hraje, i kdyby se obrazovka budíku neotevřela.
 * Obrazovka (RingActivity), oznámení i fyzická tlačítka jen posílají „odložit“ / „vypnout“.
 */
public class RingService extends Service {
    static final String START = "cz.mathex.hodiny.ring.START";
    static final String SNOOZE = "cz.mathex.hodiny.ring.SNOOZE";
    static final String DISMISS = "cz.mathex.hodiny.ring.DISMISS";

    static final long AUTO_SNOOZE = 10 * 60 * 1000L;
    static final int MAX_AUTO_SNOOZES = 2;

    static volatile boolean ringing;
    static volatile int alarmId;
    static WeakReference<Activity> ui;

    private final Handler h = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private ToneGenerator tone;
    private AudioManager audio;
    private PowerManager.WakeLock wake;

    private final AudioManager.OnAudioFocusChangeListener focus = new AudioManager.OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int change) {
        }
    };

    private final Runnable auto = new Runnable() {
        public void run() {
            autoSnooze();
        }
    };

    static void start(Context c, int id) {
        c.startService(new Intent(c, RingService.class).setAction(START).putExtra("alarm_id", id));
    }

    static void send(Context c, String action) {
        c.startService(new Intent(c, RingService.class).setAction(action));
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        String a = i != null ? i.getAction() : null;
        if (START.equals(a)) {
            alarmId = i.getIntExtra("alarm_id", 0);
            if (!ringing) {
                ringing = true;
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hodiny:ring");
                wake.acquire(AUTO_SNOOZE + 60000);
                foreground();
                startSound();
                h.postDelayed(auto, AUTO_SNOOZE);
            }
        } else if (SNOOZE.equals(a)) {
            if (ringing) {
                Prefs.get(this).edit().putInt("auto_snoozes", 0).apply(); // odložil člověk → je u budíku
                Alarms.snooze(this, alarmId);
                stopAll();
            }
        } else if (DISMISS.equals(a)) {
            if (ringing) dismiss();
        } else if (!ringing) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @SuppressWarnings("deprecation")
    private void foreground() {
        Intent open = new Intent(this, RingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent openPi = PendingIntent.getActivity(this, 20, open, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent snoozePi = PendingIntent.getService(this, 21,
                new Intent(this, RingService.class).setAction(SNOOZE), PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stopPi = PendingIntent.getService(this, 22,
                new Intent(this, RingService.class).setAction(DISMISS), PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏰ Budík")
                .setContentText("Ťukni pro otevření")
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setOngoing(true)
                .setContentIntent(openPi)
                .setFullScreenIntent(openPi, true) // druhá cesta, jak otevřít obrazovku budíku
                .addAction(android.R.drawable.ic_media_pause, "Odložit", snoozePi)
                .addAction(android.R.drawable.ic_lock_idle_alarm, "Vypnout", stopPi)
                .build();
        startForeground(43, n);
    }

    private void startSound() {
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audio.requestAudioFocus(focus, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM);
        if (audio.getStreamVolume(AudioManager.STREAM_ALARM) < max / 2) {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, Math.max(1, (int) (max * 0.7)), 0);
        }
        Uri[] candidates = {
                RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        };
        for (Uri u : candidates) {
            if (u == null) continue;
            try {
                MediaPlayer p = new MediaPlayer();
                p.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                p.setDataSource(this, u);
                p.setLooping(true);
                p.prepare();
                p.start();
                player = p;
                return;
            } catch (Exception ignored) {
            }
        }
        // žádný zvuk nenalezen – aspoň tónem
        try {
            tone = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, (int) AUTO_SNOOZE);
        } catch (Exception ignored) {
        }
    }

    /** Nikdo nereaguje: 2× se budík sám odloží, potřetí ztichne (ať nezvoní celý víkend). */
    private void autoSnooze() {
        if (!ringing) return;
        int n = Prefs.get(this).getInt("auto_snoozes", 0);
        if (n >= MAX_AUTO_SNOOZES) {
            Prefs.get(this).edit().putInt("auto_snoozes", 0).apply();
            Alarms.clearSnooze(this);
            Alarms.schedule(this);
        } else {
            Prefs.get(this).edit().putInt("auto_snoozes", n + 1).apply();
            Alarms.snooze(this, alarmId);
        }
        stopAll();
    }

    private void dismiss() {
        Alarms.clearSnooze(this);
        Alarms.schedule(this);
        Alarms.Alarm a = Alarms.find(Alarms.load(this), alarmId);
        if (a != null && a.print) Prefs.printMorning(this, true); // výsledek ukáže toast
        if (Yeelight.configured(this) && Prefs.get(this).getBoolean("bulb_full_on_dismiss", true)) Yeelight.full(this);
        stopAll();
        startRadio(getApplicationContext());
    }

    /** Po vypnutí budíku pustit zvolenou stanici (Nastavení → Budík). */
    static void startRadio(final android.content.Context c) {
        android.content.SharedPreferences sp = Prefs.get(c);
        if (!sp.getBoolean("alarm_radio", false)) return;
        final String url = sp.getString("alarm_radio_url", "");
        if (url.length() == 0) return;
        final String name = sp.getString("alarm_radio_name", "Rádio");
        final String fav = sp.getString("alarm_radio_favicon", "");
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            public void run() {
                RadioService.play(c, name, url, fav);
            }
        }, 1500); // až zvonění uvolní zvuk
    }

    private void stopAll() {
        ringing = false;
        h.removeCallbacks(auto);
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        if (tone != null) {
            tone.release();
            tone = null;
        }
        if (audio != null) audio.abandonAudioFocus(focus);
        if (wake != null && wake.isHeld()) wake.release();
        Activity act = ui != null ? ui.get() : null;
        if (act != null) act.finish();
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (ringing) stopAll();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }
}
