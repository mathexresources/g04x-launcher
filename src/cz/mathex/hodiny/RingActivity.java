package cz.mathex.hodiny;

import android.app.Activity;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;

/** Zvonící budík: velký čas, „Odložit“ a „Vypnout“. Libovolné tlačítko na hodinách = odložit. */
public class RingActivity extends Activity {
    private static final long AUTO_SNOOZE = 10 * 60 * 1000L;

    private final Handler h = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private AudioManager audio;
    private boolean done;
    private int alarmId;

    private final AudioManager.OnAudioFocusChangeListener focus = new AudioManager.OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int change) {
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        alarmId = getIntent().getIntExtra("alarm_id", 0);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 1f;
        getWindow().setAttributes(lp);

        LinearLayout root = Ui.box(this, true);
        root.setBackgroundColor(Ui.BG);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Ui.px(this, .06f);
        root.setPadding(pad, pad, pad, pad);

        Calendar c = Calendar.getInstance();
        TextView time = Ui.text(this, c.get(Calendar.HOUR_OF_DAY) + ":"
                + (c.get(Calendar.MINUTE) < 10 ? "0" : "") + c.get(Calendar.MINUTE), .34f, Ui.INK);
        time.setTypeface(Ui.light());
        root.addView(time);
        Alarms.Alarm me = Alarms.find(Alarms.load(this), alarmId);
        TextView label = Ui.text(this, "⏰  Budík" + (me != null && me.print ? "   ·   🖨 po vypnutí ranní zpráva" : ""),
                .055f, Ui.ACCENT);
        root.addView(label, Ui.top(this, .02f));

        LinearLayout buttons = Ui.box(this, false);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, 0, 1f);
        bl.topMargin = Ui.px(this, .06f);
        root.addView(buttons, bl);
        buttons.addView(button("Odložit " + Alarms.SNOOZE_MIN + " min", Ui.INK, new Runnable() {
            public void run() {
                snooze();
            }
        }), Ui.lp(0, -1, 1f));
        View gap = new View(this);
        buttons.addView(gap, new LinearLayout.LayoutParams(Ui.px(this, .04f), 1));
        buttons.addView(button("Vypnout", Ui.ACCENT, new Runnable() {
            public void run() {
                dismiss();
            }
        }), Ui.lp(0, -1, 1f));

        setContentView(root);
        startSound();
        h.postDelayed(new Runnable() {
            public void run() {
                snooze(); // nikdo nereaguje → odložit, ať to nezvoní donekonečna
            }
        }, AUTO_SNOOZE);
    }

    private TextView button(String text, int color, final Runnable r) {
        TextView t = Ui.text(this, text, .07f, color);
        t.setTypeface(Ui.medium());
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.tile(this));
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                r.run();
            }
        });
        return t;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
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
            android.media.ToneGenerator tg = new android.media.ToneGenerator(AudioManager.STREAM_ALARM, 100);
            tg.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, (int) AUTO_SNOOZE);
        } catch (Exception ignored) {
        }
    }

    private void stopSound() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        if (audio != null) audio.abandonAudioFocus(focus);
    }

    private void snooze() {
        if (done) return;
        done = true;
        Alarms.snooze(this, alarmId);
        finishRing();
    }

    private void dismiss() {
        if (done) return;
        done = true;
        Alarms.clearSnooze(this);
        Alarms.schedule(this);
        Alarms.Alarm a = Alarms.find(Alarms.load(this), alarmId);
        if (a != null && a.print) Prefs.printMorning(this, true); // výsledek ukáže toast
        finishRing();
    }

    private void finishRing() {
        h.removeCallbacksAndMessages(null);
        stopSound();
        finish();
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        snooze(); // hlasitost / mute na hodinách = odložit
        return true;
    }

    @Override
    public void onBackPressed() {
        snooze();
    }

    @Override
    protected void onDestroy() {
        stopSound();
        super.onDestroy();
    }
}
