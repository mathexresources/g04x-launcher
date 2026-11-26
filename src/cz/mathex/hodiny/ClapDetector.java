package cz.mathex.hodiny;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

/**
 * Detekce série tlesknutí (2× / 3×) z mikrofonu. Zvuk se nikam neukládá ani neposílá – počítá se jen hlasitost
 * po 32ms blocích: tlesknutí = krátká ostrá špička nad hlukem pozadí, dvojí = dvě špičky 0,2–0,8 s po sobě.
 */
final class ClapDetector {
    interface Listener {
        /** Série tlesknutí skončila: count = 2 nebo 3 (víc se počítá jako 3). */
        void onClaps(int count);
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
            int loudBlocks = 0, count = 0;
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
                // série skončila: od posledního tlesknutí uběhlo 0,9 s bez dalšího
                if (count > 0 && now - lastClap > 900) {
                    final int c = Math.min(3, count);
                    count = 0;
                    if (c >= 2) {
                        cooldownUntil = now + 1200;
                        main.post(new Runnable() {
                            public void run() {
                                listener.onClaps(c);
                            }
                        });
                    }
                }
                // citlivější: nižší absolutní práh i poměr k pozadí (v2.9: 9000 a ×5)
                boolean loud = peak > 5500 && rms > bg * 3.5;
                if (loud) {
                    loudBlocks++;
                } else {
                    // tlesknutí je krátké: nejvýš 3 hlasité bloky (~96 ms, i s ozvěnou); delší zvuk (hudba, řeč) se ignoruje
                    if (loudBlocks >= 1 && loudBlocks <= 3 && now > cooldownUntil) {
                        long gap = now - lastClap;
                        if (count > 0 && gap > 150 && gap <= 900) {
                            count++; // další tlesknutí v sérii
                        } else if (count == 0 || gap > 900) {
                            count = 1; // začátek nové série
                        }
                        if (gap > 150) lastClap = now;
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
