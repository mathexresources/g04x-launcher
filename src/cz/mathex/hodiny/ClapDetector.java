package cz.mathex.hodiny;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

/**
 * Detekce dvojího tlesknutí z mikrofonu. Zvuk se nikam neukládá ani neposílá – počítá se jen hlasitost
 * po 32ms blocích: tlesknutí = krátká ostrá špička nad hlukem pozadí, dvojí = dvě špičky 0,2–0,8 s po sobě.
 */
final class ClapDetector {
    interface Listener {
        void onDoubleClap();
    }

    private static final int RATE = 16000;
    private static final int BLOCK = 512; // 32 ms

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private volatile boolean running;
    private Thread thread;

    /** Poslední hladina pozadí a špička (pro ladění v nastavení). */
    static volatile int lastBg, lastPeak;

    ClapDetector(Listener l) {
        listener = l;
    }

    void start() {
        if (running) return;
        running = true;
        thread = new Thread(new Runnable() {
            public void run() {
                loop();
            }
        }, "clap");
        thread.start();
    }

    void stop() {
        running = false;
        if (thread != null) thread.interrupt();
        thread = null;
    }

    private void loop() {
        AudioRecord rec = null;
        try {
            int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            rec = new AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min, BLOCK * 8));
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) return;
            rec.startRecording();
            short[] buf = new short[BLOCK];
            double bg = 300; // hladina hluku pozadí (RMS), pomalu se přizpůsobuje
            long lastClap = 0, cooldownUntil = 0;
            int loudBlocks = 0;
            while (running) {
                int n = rec.read(buf, 0, BLOCK);
                if (n <= 0) continue;
                int peak = 0;
                double sum = 0;
                for (int i = 0; i < n; i++) {
                    int v = Math.abs(buf[i]);
                    if (v > peak) peak = v;
                    sum += (double) buf[i] * buf[i];
                }
                double rms = Math.sqrt(sum / n);
                lastBg = (int) bg;
                lastPeak = peak;
                long now = System.currentTimeMillis();
                boolean loud = peak > 9000 && rms > bg * 5;
                if (loud) {
                    loudBlocks++;
                } else {
                    // tlesknutí je krátké: nejvýš 2 hlasité bloky (~64 ms); delší zvuk (hudba, řeč) se ignoruje
                    if (loudBlocks >= 1 && loudBlocks <= 2 && now > cooldownUntil) {
                        long gap = now - lastClap;
                        if (gap > 200 && gap < 800) {
                            lastClap = 0;
                            cooldownUntil = now + 1500;
                            main.post(new Runnable() {
                                public void run() {
                                    listener.onDoubleClap();
                                }
                            });
                        } else {
                            lastClap = now;
                        }
                    }
                    loudBlocks = 0;
                    bg = bg + (rms - bg) * 0.02; // pozadí se učí jen z tichých bloků
                    if (bg < 50) bg = 50;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (rec != null) {
                try {
                    rec.stop();
                } catch (Exception ignored) {
                }
                rec.release();
            }
        }
    }
}
