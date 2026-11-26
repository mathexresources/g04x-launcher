package cz.mathex.hodiny;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
import java.util.Random;

/**
 * Retro LCD jako na displeji starého autorádia: delfíni skáčou přes vlny (na údery hudby),
 * dole ekvalizér, nahoře běžící název písničky. Kreslí se do malé bitmapy 200×120 a zvětší se
 * bez vyhlazení, přes to jemná mřížka pixelů. Celá scéna má jednu barvu (odstín hue) – stejnou jako žárovka.
 */
final class PartyView extends View {
    private static final int LW = 200, LH = 120, SEA = 84;

    private final Bitmap lcd = Bitmap.createBitmap(LW, LH, Bitmap.Config.ARGB_8888);
    private final Canvas lc = new Canvas(lcd);
    private final Paint p = new Paint(); // bez vyhlazení = ostré pixely
    private final Paint blit = new Paint();
    private final Paint grid = new Paint();
    private final Path path = new Path();
    private final Rect src = new Rect(0, 0, LW, LH), dst = new Rect();
    private final Random rnd = new Random();

    float hue = 200;              // odstín scény
    boolean strobe, strobeOn;     // stroboskop
    PartyAudio audio;
    String title = "";
    private final float[] peaks = new float[PartyAudio.BANDS];
    private int seenBeats;
    private long beatFlash, lastSpawn;
    private float marquee;
    private long lastFrame;
    private final int[][] stars = new int[24][2];

    private static final class Dolphin {
        float x0, dir, height, travel;
        long start, dur;
        boolean splashed;
    }

    private final ArrayList<Dolphin> dolphins = new ArrayList<Dolphin>();
    private final ArrayList<float[]> drops = new ArrayList<float[]>(); // x, y, vx, vy

    PartyView(Context c) {
        super(c);
        blit.setFilterBitmap(false);
        blit.setAntiAlias(false);
        p.setAntiAlias(false);
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        for (int[] s : stars) {
            s[0] = rnd.nextInt(LW);
            s[1] = 14 + rnd.nextInt(SEA - 40);
        }
    }

    /** Barva LCD v jasu 0..1 (0 = „zhasnutý“ segment, pořád trochu vidět). */
    private int lit(float k) {
        float v = .07f + .93f * Math.max(0, Math.min(1, k));
        return Color.HSVToColor(new float[]{hue, strobe ? 0 : .78f - .35f * Math.max(0, k - .8f) * 2, v});
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? .033f : Math.min(.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float level = audio != null ? audio.level : 0;

        if (audio != null && audio.beats != seenBeats) {
            seenBeats = audio.beats;
            beatFlash = now;
            if (dolphins.size() < 3 && now - lastSpawn > 450) spawn(now);
        }
        if (now - lastSpawn > 3200 && dolphins.isEmpty()) spawn(now); // i v tichu občas skočí

        float flash = Math.max(0, 1 - (now - beatFlash) / 220f);
        lc.drawColor(lit(.0f + .06f * flash));

        if (strobe) {
            lc.drawColor(strobeOn ? 0xFFFFFFFF : 0xFF000000);
        } else {
            drawSky(now, flash);
            drawSea(now, level, dt);
            drawDolphins(now);
            drawEq(dt);
            drawTitle(dt);
        }

        int W = getWidth(), H = getHeight();
        int scale = Math.max(1, Math.min(W / LW, H / LH));
        int w = LW * scale, h = LH * scale;
        int x0 = (W - w) / 2, y0 = (H - h) / 2;
        c.drawColor(0xFF000000);
        dst.set(x0, y0, x0 + w, y0 + h);
        c.drawBitmap(lcd, src, dst, blit);
        if (scale >= 3 && !strobe) { // mřížka mezi pixely
            grid.setColor(0x66000000);
            for (int i = 0; i <= LW; i++) c.drawRect(x0 + i * scale, y0, x0 + i * scale + 1, y0 + h, grid);
            for (int j = 0; j <= LH; j++) c.drawRect(x0, y0 + j * scale, x0 + w, y0 + j * scale + 1, grid);
        }
        postInvalidateOnAnimation();
    }

    private void spawn(long now) {
        lastSpawn = now;
        Dolphin d = new Dolphin();
        d.dir = rnd.nextBoolean() ? 1 : -1;
        d.travel = 50 + rnd.nextInt(30);
        d.x0 = d.dir > 0 ? 10 + rnd.nextInt(LW - 30 - (int) d.travel) : d.travel + 20 + rnd.nextInt(LW - 30 - (int) d.travel);
        d.height = 26 + rnd.nextInt(22);
        d.dur = 1100 + rnd.nextInt(500);
        d.start = now;
        dolphins.add(d);
    }

    private void drawSky(long now, float flash) {
        // hvězdy blikají
        for (int i = 0; i < stars.length; i++) {
            boolean on = ((now / 400 + i * 7) % 11) != 0;
            p.setColor(lit(on ? .55f + .45f * flash : .15f));
            lc.drawRect(stars[i][0], stars[i][1], stars[i][0] + 1, stars[i][1] + 1, p);
        }
        // měsíc
        p.setColor(lit(.85f));
        lc.drawCircle(LW - 30, 30, 8, p);
        p.setColor(lit(.06f));
        lc.drawCircle(LW - 26, 27, 7, p);
    }

    private float wavePhase;

    private void drawSea(long now, float level, float dt) {
        wavePhase += dt * (1.6f + level * 3f);
        float amp = 1.5f + level * 2.5f;
        for (int x = 0; x < LW; x++) {
            float y1 = SEA + amp * (float) Math.sin(x * .12f + wavePhase);
            float y2 = SEA + 5 + amp * .7f * (float) Math.sin(x * .09f - wavePhase * 1.3f + 1);
            p.setColor(lit(.9f));
            lc.drawRect(x, (int) y1, x + 1, (int) y1 + 1, p);
            p.setColor(lit(.4f));
            lc.drawRect(x, (int) y2, x + 1, (int) y2 + 1, p);
            if ((x + (int) (wavePhase * 8)) % 9 == 0) lc.drawRect(x, SEA + 9, x + 1, SEA + 10, p);
        }
        // kapky ze šplouchnutí
        p.setColor(lit(.8f));
        for (int i = drops.size() - 1; i >= 0; i--) {
            float[] d = drops.get(i);
            d[0] += d[2] * dt;
            d[1] += d[3] * dt;
            d[3] += 160 * dt;
            if (d[1] > SEA + 2) {
                drops.remove(i);
                continue;
            }
            lc.drawRect((int) d[0], (int) d[1], (int) d[0] + 1, (int) d[1] + 1, p);
        }
    }

    private void drawDolphins(long now) {
        for (int i = dolphins.size() - 1; i >= 0; i--) {
            Dolphin d = dolphins.get(i);
            float t = (now - d.start) / (float) d.dur;
            if (t > 1.15f) {
                dolphins.remove(i);
                continue;
            }
            float tt = Math.min(1, t);
            float x = d.x0 + d.dir * d.travel * tt;
            float y = SEA + 6 - d.height * 4 * tt * (1 - tt);
            float vx = d.dir * d.travel, vy = -d.height * 4 * (1 - 2 * tt);
            float ang = (float) Math.toDegrees(Math.atan2(vy, vx));
            if (t > .9f && !d.splashed) {
                d.splashed = true;
                for (int k = 0; k < 7; k++)
                    drops.add(new float[]{x, SEA, (rnd.nextFloat() - .5f) * 50, -40 - rnd.nextFloat() * 50});
            }
            if (t < .04f) {
                for (int k = 0; k < 4; k++)
                    drops.add(new float[]{x, SEA, (rnd.nextFloat() - .5f) * 30, -30 - rnd.nextFloat() * 30});
            }
            lc.save();
            lc.clipRect(0, 0, LW, SEA + 1); // pod hladinou není vidět
            lc.translate(x, y);
            lc.rotate(ang);
            if (d.dir < 0) lc.scale(1, -1); // otočený doleva, ale pořád hřbetem nahoru
            dolphin();
            lc.restore();
        }
    }

    /** Vlastní pixelový delfín, zobák doprava (délka ~43 px). */
    private void dolphin() {
        lc.scale(1.4f, 1.4f);
        path.reset();
        path.moveTo(15, 1);                  // špička zobáku
        path.lineTo(10, .3f);
        path.quadTo(8, -4.2f, 3, -4.3f);     // čelo (meloun)
        path.quadTo(-6, -4.2f, -12, -.8f);   // hřbet k ocasu
        path.lineTo(-15.5f, -4);             // ocasní ploutev
        path.lineTo(-14.2f, 0);
        path.lineTo(-15.5f, 4);
        path.lineTo(-12, .9f);
        path.quadTo(-3, 4.2f, 9, 2.3f);      // břicho
        path.lineTo(15, 1.8f);
        path.close();
        p.setColor(lit(1f));
        lc.drawPath(path, p);
        path.reset();                        // hřbetní ploutev zahnutá dozadu
        path.moveTo(1.5f, -4.2f);
        path.quadTo(-2, -6, -6.5f, -9);
        path.lineTo(-5, -6);
        path.lineTo(-5.5f, -3.8f);
        path.close();
        lc.drawPath(path, p);
        p.setColor(lit(.1f));                // oko
        lc.drawRect(7.5f, -2, 8.5f, -1, p);
    }

    private void drawEq(float dt) {
        int n = PartyAudio.BANDS, bw = 6, gap = 2;
        int total = n * (bw + gap) - gap, x0 = (LW - total) / 2, base = LH - 2, maxH = 20;
        for (int b = 0; b < n; b++) {
            float v = audio != null ? audio.bands[b] : 0;
            peaks[b] = Math.max(v, peaks[b] - dt * .6f);
            int segs = Math.round(v * maxH / 3f);
            int x = x0 + b * (bw + gap);
            for (int sgm = 0; sgm < maxH / 3; sgm++) {
                p.setColor(lit(sgm < segs ? .55f + .45f * sgm / (maxH / 3f) : .12f));
                int y = base - sgm * 3;
                lc.drawRect(x, y - 2, x + bw, y, p);
            }
            int py = base - Math.round(peaks[b] * maxH / 3f) * 3 - 3;
            p.setColor(lit(1f));
            lc.drawRect(x, py, x + bw, py + 1, p);
        }
    }

    private void drawTitle(float dt) {
        p.setTextSize(10);
        p.setColor(lit(1f));
        String t = title.length() > 0 ? "♪ " + title + "     " : "PARTY  •  PARTY  •  ";
        float w = p.measureText(t);
        marquee -= dt * 28;
        if (marquee < -w) marquee += w;
        float x = marquee;
        while (x < LW) {
            lc.drawText(t, x, 10, p);
            x += w;
        }
        p.setColor(lit(.35f));
        lc.drawRect(0, 13, LW, 14, p);
    }
}
