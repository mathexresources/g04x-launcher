package cz.mathex.hodiny;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

/**
 * Poslouchá mikrofon a počítá spektrum (FFT 512), hlasitost a údery (beat) pro párty mód.
 * Výsledky čte UI vlákno z volatile polí; údery počítá čítač beats.
 */
final class PartyAudio {
    static final int BANDS = 24;
    private static final int RATE = 22050, N = 512;

    final float[] bands = new float[BANDS]; // 0..1, vyhlazené
    volatile float level;                   // 0..1 celková hlasitost (s automatickým ziskem)
    volatile int beats;                     // zvyšuje se při každém úderu
    volatile boolean running, failed;

    private Thread thread;
    private final int[] binLo = new int[BANDS], binHi = new int[BANDS];

    PartyAudio() {
        // pásma logaritmicky 60 Hz – 9 kHz
        double lo = 60, hi = 9000;
        for (int b = 0; b < BANDS; b++) {
            double f0 = lo * Math.pow(hi / lo, b / (double) BANDS);
            double f1 = lo * Math.pow(hi / lo, (b + 1) / (double) BANDS);
            binLo[b] = Math.max(1, (int) Math.floor(f0 * N / RATE));
            binHi[b] = Math.max(binLo[b] + 1, (int) Math.ceil(f1 * N / RATE));
        }
    }

    void start() {
        if (running) return;
        running = true;
        failed = false;
        thread = new Thread(new Runnable() {
            public void run() {
                loop();
            }
        }, "party-audio");
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
            // mikrofon mohl ještě držet detektor tlesknutí na ploše → pár pokusů
            for (int i = 0; i < 6 && running; i++) {
                rec = new AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, Math.max(min, N * 8));
                if (rec.getState() == AudioRecord.STATE_INITIALIZED) break;
                rec.release();
                rec = null;
                Thread.sleep(400);
            }
            if (rec == null) {
                failed = true;
                return;
            }
            rec.startRecording();
            short[] buf = new short[N];
            float[] re = new float[N], im = new float[N], win = new float[N];
            for (int i = 0; i < N; i++) win[i] = (float) (.5 - .5 * Math.cos(2 * Math.PI * i / (N - 1)));
            float[] hist = new float[43];
            int hi = 0;
            float gain = 1e-3f, bandMax = 1e-3f;
            long lastBeat = 0;
            while (running) {
                int got = 0;
                while (got < N && running) {
                    int n = rec.read(buf, got, N - got);
                    if (n <= 0) break;
                    got += n;
                }
                if (got < N) continue;
                double sum = 0;
                for (int i = 0; i < N; i++) {
                    float v = buf[i] / 32768f;
                    sum += v * v;
                    re[i] = v * win[i];
                    im[i] = 0;
                }
                fft(re, im);
                float rms = (float) Math.sqrt(sum / N);
                gain = Math.max(rms, gain * .9985f); // automatický zisk: pomalu zapomíná maximum
                level = Math.min(1f, rms / Math.max(.004f, gain));

                float frameMax = 0;
                float[] raw = new float[BANDS];
                for (int b = 0; b < BANDS; b++) {
                    float m = 0;
                    for (int k = binLo[b]; k < binHi[b] && k < N / 2; k++) {
                        m = Math.max(m, (float) Math.sqrt(re[k] * re[k] + im[k] * im[k]));
                    }
                    raw[b] = (float) Math.log10(1 + 40 * m);
                    frameMax = Math.max(frameMax, raw[b]);
                }
                bandMax = Math.max(frameMax, bandMax * .997f);
                for (int b = 0; b < BANDS; b++) {
                    float v = Math.min(1f, raw[b] / Math.max(.05f, bandMax));
                    bands[b] = v > bands[b] ? v : bands[b] * .82f + v * .18f; // rychlý náběh, pomalý pád
                }

                // úder: energie basů výrazně nad průměrem poslední sekundy
                float bass = raw[0] + raw[1] + raw[2] + raw[3] + raw[4];
                float avg = 0;
                for (float x : hist) avg += x;
                avg /= hist.length;
                hist[hi] = bass;
                hi = (hi + 1) % hist.length;
                long now = System.currentTimeMillis();
                if (bass > avg * 1.35f && bass > .25f && level > .25f && now - lastBeat > 260) {
                    lastBeat = now;
                    beats++;
                }
            }
        } catch (Exception e) {
            failed = true;
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

    /** Radix-2 FFT na místě. */
    private static void fft(float[] re, float[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                float t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            float wr = (float) Math.cos(ang), wi = (float) Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                float cr = 1, ci = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = a + len / 2;
                    float xr = re[b] * cr - im[b] * ci, xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr; im[b] = im[a] - xi;
                    re[a] += xr; im[a] += xi;
                    float nr = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = nr;
                }
            }
        }
    }
}
