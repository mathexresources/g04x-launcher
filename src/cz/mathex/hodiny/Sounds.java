package cz.mathex.hodiny;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Calendar;

/**
 * Jemné zvuky rozhraní (klepnutí, probuzení, menu). Tóny se vygenerují do WAV souborů při prvním použití,
 * žádné soubory v APK. V noci (22–7) mlčí, pokud to není v nastavení povoleno.
 */
final class Sounds {
    static final int TAP = 0, WAKE = 1, MENU = 2, BACK = 3;
    private static SoundPool pool;
    private static final int[] ids = new int[4];
    private static long lastTap;

    private Sounds() {}

    static boolean enabled(Context c) {
        if (!Prefs.get(c).getBoolean("ui_sounds", true)) return false;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean night = h >= 22 || h < 7;
        return !night || Prefs.get(c).getBoolean("ui_sounds_night", false);
    }

    static void play(Context c, int which) {
        if (!enabled(c)) return;
        long now = System.currentTimeMillis();
        if (which == TAP && now - lastTap < 60) return;
        if (which == TAP) lastTap = now;
        init(c.getApplicationContext());
        if (pool != null && ids[which] != 0) {
            float v = which == TAP ? .22f : .3f;
            pool.play(ids[which], v, v, 1, 0, 1f);
        }
    }

    private static synchronized void init(Context c) {
        if (pool != null) return;
        try {
            pool = new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            ids[TAP] = load(c, "tap", new float[][]{{1500, 28}}, .5f);
            ids[WAKE] = load(c, "wake", new float[][]{{660, 70}, {990, 110}}, .45f);
            ids[MENU] = load(c, "menu", new float[][]{{784, 60}, {988, 60}, {1175, 110}}, .4f);
            ids[BACK] = load(c, "back", new float[][]{{880, 60}, {660, 100}}, .4f);
        } catch (Exception e) {
            pool = null;
        }
    }

    /** Vygeneruje WAV: posloupnost tónů {frekvence Hz, délka ms} se zvonivou obálkou. */
    private static int load(Context c, String name, float[][] notes, float gain) throws Exception {
        File f = new File(c.getCacheDir(), "ui_" + name + "_v1.wav");
        if (!f.exists()) {
            int rate = 22050;
            int total = 0;
            for (float[] n : notes) total += (int) (rate * n[1] / 1000f);
            short[] pcm = new short[total];
            int o = 0;
            for (float[] n : notes) {
                int len = (int) (rate * n[1] / 1000f);
                for (int i = 0; i < len; i++) {
                    float t = i / (float) rate;
                    float attack = Math.min(1f, i / (rate * .004f));
                    float env = attack * (float) Math.exp(-t * 28f / (n[1] / 100f));
                    double sm = Math.sin(2 * Math.PI * n[0] * t) + .25 * Math.sin(4 * Math.PI * n[0] * t);
                    pcm[o + i] = (short) (sm * env * gain * 32767 * .8);
                }
                o += len;
            }
            FileOutputStream out = new FileOutputStream(f);
            int dataLen = total * 2;
            byte[] h = new byte[44];
            wr(h, 0, "RIFF"); le32(h, 4, 36 + dataLen); wr(h, 8, "WAVE"); wr(h, 12, "fmt ");
            le32(h, 16, 16); le16(h, 20, 1); le16(h, 22, 1); le32(h, 24, rate); le32(h, 28, rate * 2);
            le16(h, 32, 2); le16(h, 34, 16); wr(h, 36, "data"); le32(h, 40, dataLen);
            out.write(h);
            byte[] b = new byte[dataLen];
            for (int i = 0; i < total; i++) {
                b[2 * i] = (byte) pcm[i];
                b[2 * i + 1] = (byte) (pcm[i] >> 8);
            }
            out.write(b);
            out.close();
        }
        return pool.load(f.getAbsolutePath(), 1);
    }

    private static void wr(byte[] b, int o, String s) {
        for (int i = 0; i < 4; i++) b[o + i] = (byte) s.charAt(i);
    }

    private static void le32(byte[] b, int o, int v) {
        b[o] = (byte) v; b[o + 1] = (byte) (v >> 8); b[o + 2] = (byte) (v >> 16); b[o + 3] = (byte) (v >> 24);
    }

    private static void le16(byte[] b, int o, int v) {
        b[o] = (byte) v; b[o + 1] = (byte) (v >> 8);
    }

    // ------------------------------------------------------------------ detekce klepnutí
    private static float downX, downY;
    private static long downT;

    /** Volá se z dispatchTouchEvent: zvuk jen při krátkém klepnutí, ne při posouvání. */
    static void touch(Context c, android.view.MotionEvent e) {
        if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
            downX = e.getRawX();
            downY = e.getRawY();
            downT = System.currentTimeMillis();
        } else if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP) {
            float slop = Ui.base(c) * .03f;
            if (Math.abs(e.getRawX() - downX) < slop && Math.abs(e.getRawY() - downY) < slop
                    && System.currentTimeMillis() - downT < 600) play(c, TAP);
        }
    }
}
