package cz.mathex.hodiny;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Random;

/**
 * Tajný párty mód (podržení karty s tím, co hraje): žárovka do rytmu / v cyklech / stroboskop
 * a na displeji retro LCD s delfíny, který mění barvu spolu se žárovkou.
 */
public class PartyActivity extends Activity {
    private static final int RHYTHM = 0, CYCLE = 1;

    private PartyView view;
    private final PartyAudio audio = new PartyAudio();
    private PartyLight light;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private int mode = RHYTHM;
    private boolean strobe, flowStarted;
    private int seenBeats;
    private long lastColor, lastBright, lastStrobe, lastTitle;
    private float hue = 200;
    private LinearLayout controls;
    private TextView bRhythm, bCycle;
    private MediaController mc;
    private boolean closed;

    private int px(float f) {
        return Ui.px(this, f);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.loadFonts(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 1f;
        getWindow().setAttributes(lp);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        view = new PartyView(this);
        view.audio = audio;
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));

        controls = Ui.box(this, false);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(px(.03f), px(.02f), px(.03f), px(.02f));
        controls.setBackground(Ui.round(0xCC000000, px(.05f)));
        bRhythm = chip("Rytmus", new Runnable() {
            public void run() {
                setMode(RHYTHM);
            }
        });
        bCycle = chip("Cykly", new Runnable() {
            public void run() {
                setMode(CYCLE);
            }
        });
        TextView bStrobe = chip("Strobo (drž)", null);
        bStrobe.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) setStrobe(true);
                else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) setStrobe(false);
                showControls();
                return true;
            }
        });
        TextView bSkip = chip("⏭", new Runnable() {
            public void run() {
                if (mc != null) mc.getTransportControls().skipToNext();
            }
        });
        TextView bEnd = chip("✕  Konec", new Runnable() {
            public void run() {
                close();
            }
        });
        bEnd.setBackground(Ui.round(Ui.BAD, px(.04f)));
        bEnd.setTextColor(0xFF1A0A0A);
        for (TextView t : new TextView[]{bRhythm, bCycle, bStrobe, bSkip, bEnd}) {
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, px(.11f));
            l.leftMargin = l.rightMargin = px(.008f);
            controls.addView(t, l);
        }
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        cl.bottomMargin = px(.03f);
        root.addView(controls, cl);
        root.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showControls();
            }
        });
        setContentView(root);
        setMode(RHYTHM);
        showControls();

        light = new PartyLight(this);
        light.start();
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            h.postDelayed(new Runnable() { // ať plocha stihne pustit mikrofon (tlesknutí)
                public void run() {
                    if (!closed) audio.start();
                }
            }, 700);
        } else {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 7);
        }
        h.post(loop);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code == 7 && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED && !closed) audio.start();
    }

    private TextView chip(String label, final Runnable r) {
        TextView t = Ui.text(this, label, .036f, Ui.INK);
        t.setTypeface(Ui.medium());
        t.setGravity(Gravity.CENTER);
        t.setPadding(px(.035f), 0, px(.035f), 0);
        t.setBackground(Ui.round(0x33FFFFFF, px(.04f)));
        if (r != null) t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                r.run();
                showControls();
            }
        });
        return t;
    }

    private void setMode(int m) {
        mode = m;
        flowStarted = false;
        for (int i = 0; i < 2; i++) {
            TextView t = i == 0 ? bRhythm : bCycle;
            boolean on = i == m;
            t.setBackground(Ui.round(on ? Ui.AMBER : 0x33FFFFFF, px(.04f)));
            t.setTextColor(on ? Ui.ON_AMBER : Ui.INK);
        }
        if (light != null && light.ready) light.color(rgb(), 100, 300);
    }

    private void setStrobe(boolean on) {
        strobe = on;
        view.strobe = on;
        if (!on && light != null) light.color(rgb(), 100, 0);
    }

    private final Runnable hideControls = new Runnable() {
        public void run() {
            controls.animate().alpha(0f).setDuration(400).start();
        }
    };

    private void showControls() {
        controls.animate().cancel();
        controls.setAlpha(1f);
        h.removeCallbacks(hideControls);
        h.postDelayed(hideControls, 4000);
    }

    private int rgb() {
        return Color.HSVToColor(new float[]{hue, 1f, 1f});
    }

    // ------------------------------------------------------------------ smyčka světla a barev
    private long lastLoop;
    private final Runnable loop = new Runnable() {
        public void run() {
            if (closed) return;
            long now = System.currentTimeMillis();
            float dt = lastLoop == 0 ? .05f : Math.min(.2f, (now - lastLoop) / 1000f);
            lastLoop = now;
            float level = audio.level;

            if (strobe) {
                if (now - lastStrobe >= 90) {
                    lastStrobe = now;
                    view.strobeOn = !view.strobeOn;
                    light.color(0xFFFFFF, view.strobeOn ? 100 : 1, 0);
                }
            } else if (mode == RHYTHM) {
                if (audio.beats != seenBeats) {
                    seenBeats = audio.beats;
                    hue = (hue + 45 + rnd.nextInt(70)) % 360;
                    light.color(rgb(), 100, 0);
                    lastColor = lastBright = now;
                } else if (now - lastBright > 140) {
                    lastBright = now;
                    light.bright(Math.round(18 + 82 * level), 140); // jas dýchá s hlasitostí
                }
                if (audio.failed || !audio.running) { // bez mikrofonu aspoň pomalé střídání
                    hue = (hue + dt * 25) % 360;
                    if (now - lastColor > 1500) {
                        lastColor = now;
                        light.color(rgb(), 80, 1000);
                    }
                }
            } else { // cykly
                hue = (hue + dt * (30 + 60 * level)) % 360;
                if (light.ready) {
                    if (now - lastColor > 200) {
                        lastColor = now;
                        light.color(rgb(), Math.round(55 + 45 * level), 200);
                    }
                } else if (light.fallback && !flowStarted) {
                    flowStarted = true;
                    int[] cols = new int[6];
                    for (int i = 0; i < 6; i++) cols[i] = Color.HSVToColor(new float[]{(hue + i * 60) % 360, 1, 1});
                    light.flowOnBulb(cols, 1500);
                }
            }
            view.hue = hue;
            if (now - lastTitle > 2000) {
                lastTitle = now;
                updateTitle();
            }
            h.postDelayed(this, 50);
        }
    };

    private void updateTitle() {
        try {
            MediaSessionManager msm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> list = msm.getActiveSessions(new ComponentName(this, MediaListener.class));
            MediaController best = null;
            for (MediaController m : list) {
                if (best == null) best = m;
                PlaybackState s = m.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                    best = m;
                    break;
                }
            }
            mc = best;
            String t = "";
            if (best != null && best.getMetadata() != null) {
                MediaMetadata md = best.getMetadata();
                String title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
                String artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
                t = (artist != null && artist.length() > 0 ? artist + " – " : "") + (title != null ? title : "");
            }
            view.title = t.trim();
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ konec
    private void close() {
        if (closed) return;
        closed = true;
        h.removeCallbacksAndMessages(null);
        audio.stop();
        if (light != null) light.stop();
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
    }

    @Override
    public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) Ui.immersive(this);
    }

    @Override
    public void onBackPressed() {
        close();
    }

    @Override
    protected void onStop() {
        super.onStop();
        close(); // odchod jinam (tlačítko Domů, budík…) = konec párty a vrácení světla
    }
}
