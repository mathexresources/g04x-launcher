package cz.mathex.hodiny;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Vzhled standby, když nic nehraje. Kreslí se celé ručně a všechny rozměry se odvozují od velikosti
 * pohledu – stejná třída tak slouží i jako malý náhled v nastavení.
 */
public class StandbyFace extends View {
    static final int CLASSIC = 0, ANALOG = 1, FLIP = 2, WORDS = 3, SUN = 4, DOTS = 5;
    static final String[] NAMES = {"Klasický", "Ručičky", "Překlápěcí", "Slovy", "Slunce", "Tečky"};
    static final int COUNT = NAMES.length;

    private static final Locale CS = new Locale("cs", "CZ");
    private static final double LAT = 50.08, LON = 14.42;

    private int style;
    private String alarmTime, alarmLeft;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private Drawable alarmIcon;

    // překlápění
    private String flipShown = "", flipOld = "";
    private long flipStart;
    private static final long FLIP_MS = 520;

    // slovy – cache, ať se layout nepočítá každou sekundu
    private String wordsKey = "";
    private StaticLayout wordsLayout;

    public StandbyFace(Context c) {
        super(c);
        Ui.loadFonts(c);
        alarmIcon = c.getResources().getDrawable(R.drawable.ic_alarm).mutate();
        alarmIcon.setTint(Ui.AMBER);
    }

    /** Uložený styl; s volbou „každý den jiný“ se vybere podle dne v roce. */
    static int resolve(Context c) {
        if (Prefs.get(c).getBoolean("standby_random", false)) {
            int doy = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
            return doy % COUNT;
        }
        int s = Prefs.get(c).getInt("standby_style", CLASSIC);
        return s >= 0 && s < COUNT ? s : CLASSIC;
    }

    void setStyle(int s) {
        if (s == style) return;
        style = s;
        wordsKey = "";
        invalidate();
    }

    int getStyle() {
        return style;
    }

    void setAlarm(String time, String left) {
        alarmTime = time;
        alarmLeft = left;
        invalidate();
    }

    // ------------------------------------------------------------------ obnova
    private final Runnable tick = new Runnable() {
        public void run() {
            invalidate();
            long ms = System.currentTimeMillis() % 1000;
            postDelayed(this, 1000 - ms + 5);
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(tick);
        post(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    // ------------------------------------------------------------------ kreslení
    @Override
    protected void onDraw(Canvas c) {
        if (getWidth() == 0 || getHeight() == 0) return;
        if (!isShown()) return;
        Calendar now = Calendar.getInstance();
        switch (style) {
            case ANALOG: analog(c, now); break;
            case FLIP: flip(c, now); break;
            case WORDS: words(c, now); break;
            case SUN: sun(c, now); break;
            case DOTS: dots(c, now); break;
            default: classic(c, now); break;
        }
    }

    private static String two(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    private static String time(Calendar c) {
        return c.get(Calendar.HOUR_OF_DAY) + ":" + two(c.get(Calendar.MINUTE));
    }

    private static String dateLine(Calendar c) {
        return new SimpleDateFormat("EEEE d. MMMM", CS).format(c.getTime());
    }

    private void text(Canvas c, String s, float x, float cy, float size, int color,
                      android.graphics.Typeface tf, Paint.Align align) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setColor(color);
        p.setTextAlign(align);
        p.setLetterSpacing(0);
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(s, x, cy - (fm.ascent + fm.descent) / 2, p);
    }

    /** Velikost písma, aby text nebyl širší než maxW. */
    private float fit(String s, float size, float maxW, android.graphics.Typeface tf, float spacing) {
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setLetterSpacing(spacing);
        float w = p.measureText(s);
        p.setLetterSpacing(0);
        return w > maxW ? size * maxW / w : size;
    }

    /** Řádek budíku (ikona + čas + odpočet) se středem v cx; align LEFT/RIGHT/CENTER. */
    private void alarm(Canvas c, float x, float cy, float size, Paint.Align align) {
        if (alarmTime == null) return;
        p.setTypeface(Ui.medium());
        p.setTextSize(size);
        float wT = p.measureText(alarmTime);
        p.setTypeface(Ui.regular());
        float wL = alarmLeft == null ? 0 : p.measureText(" " + alarmLeft);
        float icon = size * 1.1f, gap = size * .4f;
        float total = icon + gap + wT + wL;
        float x0 = align == Paint.Align.CENTER ? x - total / 2 : align == Paint.Align.RIGHT ? x - total : x;
        alarmIcon.setBounds((int) x0, (int) (cy - icon / 2), (int) (x0 + icon), (int) (cy + icon / 2));
        alarmIcon.draw(c);
        text(c, alarmTime, x0 + icon + gap, cy, size, Ui.AMBER, Ui.medium(), Paint.Align.LEFT);
        if (alarmLeft != null) {
            text(c, " " + alarmLeft, x0 + icon + gap + wT, cy, size, Ui.AMBER_SOFT, Ui.regular(), Paint.Align.LEFT);
        }
    }

    // ------------------------------------------------------------------ 0 klasický
    private void classic(Canvas c, Calendar now) {
        float W = getWidth(), H = getHeight();
        String t = time(now);
        float size = fit(t, H * .58f, W * .94f, Ui.display(), -0.045f);
        text(c, dateLine(now), W / 2, H * .15f, H * .046f, Ui.DIM, Ui.regular(), Paint.Align.CENTER);
        p.setTypeface(Ui.display());
        p.setTextSize(size);
        p.setColor(Ui.INK);
        p.setTextAlign(Paint.Align.CENTER);
        p.setLetterSpacing(-0.045f);
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(t, W / 2, H * .5f - (fm.ascent + fm.descent) / 2, p);
        p.setLetterSpacing(0);
        alarm(c, W / 2, H * .86f, H * .05f, Paint.Align.CENTER);
    }

    // ------------------------------------------------------------------ 1 ručičky
    private void analog(Canvas c, Calendar now) {
        float W = getWidth(), H = getHeight();
        float R = Math.min(H * .42f, W * .26f);
        float cx = W * .06f + R, cy = H / 2f;

        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Ui.TILE);
        c.drawCircle(cx, cy, R, p);
        // značky hodin
        for (int i = 0; i < 60; i++) {
            double a = Math.PI * 2 * i / 60;
            float sx = (float) Math.sin(a), sy = (float) -Math.cos(a);
            if (i % 5 == 0) {
                boolean q = i % 15 == 0;
                p.setColor(q ? Ui.AMBER : Ui.SOFT);
                c.drawCircle(cx + sx * R * .84f, cy + sy * R * .84f, R * (q ? .045f : .028f), p);
            } else {
                p.setColor(Ui.LINE);
                c.drawCircle(cx + sx * R * .84f, cy + sy * R * .84f, R * .011f, p);
            }
        }
        float sec = now.get(Calendar.SECOND);
        float min = now.get(Calendar.MINUTE) + sec / 60f;
        float hr = now.get(Calendar.HOUR) + min / 60f;
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStyle(Paint.Style.STROKE);
        hand(c, cx, cy, hr / 12f, R * .5f, R * .075f, Ui.INK);
        hand(c, cx, cy, min / 60f, R * .72f, R * .05f, Ui.TEAL);
        hand(c, cx, cy, sec / 60f, R * .78f, R * .018f, Ui.AMBER);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Ui.AMBER);
        c.drawCircle(cx, cy, R * .06f, p);
        p.setColor(Ui.TILE);
        c.drawCircle(cx, cy, R * .025f, p);

        // pravý sloupec
        float x = cx + R + W * .07f;
        float colW = W - x - W * .04f;
        String day = new SimpleDateFormat("EEEE", CS).format(now.getTime());
        String date = new SimpleDateFormat("d. MMMM", CS).format(now.getTime());
        text(c, day, x, H * .30f, fit(day, H * .1f, colW, Ui.medium(), 0), Ui.INK, Ui.medium(), Paint.Align.LEFT);
        text(c, date, x, H * .42f, fit(date, H * .06f, colW, Ui.regular(), 0), Ui.DIM, Ui.regular(), Paint.Align.LEFT);
        String t = time(now);
        text(c, t, x, H * .60f, fit(t, H * .17f, colW, Ui.display(), 0), Ui.SOFT, Ui.display(), Paint.Align.LEFT);
        alarm(c, x, H * .76f, Math.min(H * .05f, colW / 9f), Paint.Align.LEFT);
    }

    private void hand(Canvas c, float cx, float cy, float frac, float len, float width, int color) {
        double a = Math.PI * 2 * frac;
        p.setStrokeWidth(width);
        p.setColor(color);
        float tx = (float) Math.sin(a), ty = (float) -Math.cos(a);
        c.drawLine(cx - tx * len * .14f, cy - ty * len * .14f, cx + tx * len, cy + ty * len, p);
    }

    // ------------------------------------------------------------------ 2 překlápěcí
    private void flip(Canvas c, Calendar now) {
        float W = getWidth(), H = getHeight();
        int hh = now.get(Calendar.HOUR_OF_DAY), mm = now.get(Calendar.MINUTE);
        String digits = two(hh) + two(mm);
        if (!digits.equals(flipShown)) {
            flipOld = flipShown.length() == 4 ? flipShown : digits;
            flipShown = digits;
            flipStart = SystemClock.uptimeMillis();
        }
        float prog = Math.min(1f, (SystemClock.uptimeMillis() - flipStart) / (float) FLIP_MS);

        float ch = H * .48f, cw = ch * .68f, gap = H * .035f, colon = H * .15f;
        float total = 4 * cw + 2 * gap + colon;
        if (total > W * .92f) {
            float k = W * .92f / total;
            ch *= k; cw *= k; gap *= k; colon *= k; total *= k;
        }
        float x = (W - total) / 2, top = H * .53f - ch / 2;
        for (int i = 0; i < 4; i++) {
            char nd = flipShown.charAt(i), od = flipOld.charAt(i);
            card(c, x, top, cw, ch, String.valueOf(od), String.valueOf(nd), nd == od ? 1f : prog);
            x += cw + (i == 1 ? colon : i == 3 ? 0 : gap);
            if (i == 1) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Ui.AMBER);
                float dx = x - colon / 2;
                c.drawCircle(dx, top + ch * .36f, ch * .035f, p);
                c.drawCircle(dx, top + ch * .64f, ch * .035f, p);
            }
        }
        text(c, dateLine(now), W / 2, H * .14f, H * .05f, Ui.DIM, Ui.regular(), Paint.Align.CENTER);
        alarm(c, W / 2, H * .9f, H * .05f, Paint.Align.CENTER);
        if (prog < 1f) postInvalidateOnAnimation();
    }

    /** Jedna kartička: horní půlka nová číslice, dolní stará, mezi nimi se překlápí list. */
    private void card(Canvas c, float x, float y, float w, float h, String oldD, String newD, float prog) {
        float mid = y + h / 2, r = w * .1f;
        drawHalf(c, x, y, w, h, newD, true, 1f, r);
        drawHalf(c, x, y, w, h, prog < 1f ? oldD : newD, false, 1f, r);
        if (prog < 1f) {
            if (prog < .5f) drawHalf(c, x, y, w, h, oldD, true, (float) Math.cos(prog * Math.PI), r);
            else drawHalf(c, x, y, w, h, newD, false, (float) -Math.cos(prog * Math.PI), r);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(Ui.BG_STANDBY);
        c.drawRect(x, mid - h * .008f, x + w, mid + h * .008f, p);
    }

    private void drawHalf(Canvas c, float x, float y, float w, float h, String d, boolean upper, float scale, float r) {
        float mid = y + h / 2;
        c.save();
        if (upper) c.clipRect(x, y, x + w, mid);
        else c.clipRect(x, mid, x + w, y + h);
        c.scale(1f, Math.max(0.001f, scale), x + w / 2, mid);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(scale < 1f ? 0xFF1E232B : Ui.TILE);
        rf.set(x, y, x + w, y + h);
        c.drawRoundRect(rf, r, r, p);
        p.setTypeface(Ui.display());
        p.setTextSize(h * .78f);
        p.setColor(Ui.INK);
        p.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(d, x + w / 2, mid - (fm.ascent + fm.descent) / 2, p);
        c.restore();
    }

    // ------------------------------------------------------------------ 3 slovy
    private static final String[] NOM = {"dvanáct", "jedna", "dvě", "tři", "čtyři", "pět", "šest", "sedm",
            "osm", "devět", "deset", "jedenáct", "dvanáct"};
    private static final String[] ACC = {"dvanáct", "jednu", "dvě", "tři", "čtyři", "pět", "šest", "sedm",
            "osm", "devět", "deset", "jedenáct", "dvanáct"};
    private static final String[] GEN = {"dvanácté", "jedné", "druhé", "třetí", "čtvrté", "páté", "šesté",
            "sedmé", "osmé", "deváté", "desáté", "jedenácté", "dvanácté"};

    /** Např. „skoro čtvrt na jedenáct“, „půl desáté a kousek“; [0] = věta, [1] = slovo hodiny. */
    static String[] phrase(int hour24, int minute) {
        int q = (minute + 7) / 15; // 0..4 – nejbližší čtvrthodina
        int d = minute - q * 15;
        int h12 = hour24 % 12 == 0 ? 12 : hour24 % 12;
        int next = h12 % 12 + 1;
        String core, word;
        switch (q) {
            case 1: word = ACC[next]; core = "čtvrt na " + word; break;
            case 2: word = GEN[next]; core = "půl " + word; break;
            case 3: word = ACC[next]; core = "tři čtvrtě na " + word; break;
            default: {
                int n = q == 4 ? next : h12;
                word = NOM[n];
                core = word + (n == 1 ? " hodina" : n <= 4 ? " hodiny" : " hodin");
            }
        }
        if (d < 0) core = "skoro " + core;
        else if (d > 0) core = core + " a kousek";
        return new String[]{core, word};
    }

    private static String daypart(int h) {
        if (h < 5) return "v noci";
        if (h < 9) return "ráno";
        if (h < 12) return "dopoledne";
        if (h < 13) return "v poledne";
        if (h < 18) return "odpoledne";
        if (h < 22) return "večer";
        return "v noci";
    }

    @SuppressWarnings("deprecation")
    private void words(Canvas c, Calendar now) {
        float W = getWidth(), H = getHeight();
        int hh = now.get(Calendar.HOUR_OF_DAY), mm = now.get(Calendar.MINUTE);
        String key = hh + ":" + mm + "/" + getWidth() + "x" + getHeight();
        if (!key.equals(wordsKey) || wordsLayout == null) {
            String[] ph = phrase(hh, mm);
            String s = "Je " + ph[0] + ".";
            SpannableString sp = new SpannableString(s);
            int i = s.lastIndexOf(ph[1]);
            if (i >= 0) sp.setSpan(new ForegroundColorSpan(Ui.AMBER), i, i + ph[1].length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            tp.setTypeface(Ui.medium());
            tp.setColor(Ui.INK);
            int width = (int) (W * .86f);
            float size = H * .19f;
            StaticLayout l;
            while (true) {
                tp.setTextSize(size);
                l = new StaticLayout(sp, tp, width, Layout.Alignment.ALIGN_CENTER, .95f, 0, false);
                if ((l.getHeight() <= H * .56f && l.getLineCount() <= 3) || size < H * .06f) break;
                size *= .93f;
            }
            wordsLayout = l;
            wordsKey = key;
        }
        String top = time(now) + "  ·  " + daypart(hh) + "  ·  " + dateLine(now);
        text(c, top, W / 2, H * .12f, fit(top, H * .042f, W * .9f, Ui.regular(), 0), Ui.DIM, Ui.regular(),
                Paint.Align.CENTER);
        c.save();
        c.translate((W - wordsLayout.getWidth()) / 2f, H * .52f - wordsLayout.getHeight() / 2f);
        wordsLayout.draw(c);
        c.restore();
        alarm(c, W / 2, H * .9f, H * .048f, Paint.Align.CENTER);
    }

    // ------------------------------------------------------------------ 4 slunce
    /** Východ a západ slunce (hodiny jako desetinné číslo, místní čas) – zjednodušený výpočet NOAA. */
    static double[] sunTimes(Calendar day) {
        int doy = day.get(Calendar.DAY_OF_YEAR);
        double decl = Math.toRadians(23.44) * Math.sin(2 * Math.PI * (284 + doy) / 365.0);
        double lat = Math.toRadians(LAT);
        double cosW = (Math.cos(Math.toRadians(90.833)) - Math.sin(lat) * Math.sin(decl))
                / (Math.cos(lat) * Math.cos(decl));
        cosW = Math.max(-1, Math.min(1, cosW));
        double w0 = Math.toDegrees(Math.acos(cosW)) / 15.0; // hodin od poledne
        double b = 2 * Math.PI * (doy - 81) / 364.0;
        double eot = 9.87 * Math.sin(2 * b) - 7.53 * Math.cos(b) - 1.5 * Math.sin(b); // minuty
        double tz = TimeZone.getDefault().getOffset(day.getTimeInMillis()) / 3600000.0;
        double noon = 12 - LON / 15.0 + tz - eot / 60.0;
        return new double[]{noon - w0, noon + w0};
    }

    private static String hm(double h) {
        int m = (int) Math.round(h * 60);
        return (m / 60) + ":" + two(m % 60);
    }

    private void sun(Canvas c, Calendar now) {
        float W = getWidth(), H = getHeight();
        float hy = H * .58f, cx = W / 2;
        float rA = Math.min(W * .4f, H * .44f);
        double[] st = sunTimes(now);
        double t = now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60.0 + now.get(Calendar.SECOND) / 3600.0;
        boolean day = t >= st[0] && t <= st[1];
        double f;
        if (day) f = (t - st[0]) / (st[1] - st[0]);
        else {
            double nightLen = 24 - (st[1] - st[0]);
            double since = t > st[1] ? t - st[1] : t + 24 - st[1];
            f = since / nightLen;
        }

        // dráha
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, H * .006f));
        p.setColor(Ui.LINE);
        float dash = H * .02f;
        p.setPathEffect(new DashPathEffect(new float[]{dash, dash}, 0));
        rf.set(cx - rA, hy - rA, cx + rA, hy + rA);
        c.drawArc(rf, 180, 180, false, p);
        p.setPathEffect(null);
        // uražená část dráhy
        p.setColor(day ? 0x66F5B54A : 0x55C9D0D8);
        p.setStrokeWidth(Math.max(1.5f, H * .01f));
        p.setStrokeCap(Paint.Cap.ROUND);
        c.drawArc(rf, 180, (float) (180 * f), false, p);
        // obzor
        p.setColor(Ui.LINE);
        p.setStrokeWidth(Math.max(1f, H * .006f));
        c.drawLine(W * .05f, hy, W * .95f, hy, p);

        double a = Math.PI * f;
        float sx = cx - (float) (rA * Math.cos(a)), sy = hy - (float) (rA * Math.sin(a));
        float sr = H * .06f;
        p.setStyle(Paint.Style.FILL);
        if (day) {
            for (int i = 3; i >= 1; i--) {
                p.setColor((0x10 * (4 - i)) << 24 | 0xF5B54A);
                c.drawCircle(sx, sy, sr * (1 + i * .45f), p);
            }
            p.setColor(Ui.AMBER);
            c.drawCircle(sx, sy, sr, p);
        } else {
            p.setColor(0x14C9D0D8);
            c.drawCircle(sx, sy, sr * 1.6f, p);
            p.setColor(Ui.SOFT);
            c.drawCircle(sx, sy, sr, p);
            p.setColor(Ui.BG_STANDBY);
            c.drawCircle(sx + sr * .45f, sy - sr * .3f, sr * .85f, p);
            // pár hvězd
            p.setColor(Ui.FAINT);
            float[][] stars = {{.12f, .14f}, {.22f, .32f}, {.8f, .12f}, {.9f, .36f}, {.68f, .22f}, {.34f, .1f}};
            for (float[] s : stars) c.drawCircle(W * s[0], H * s[1], Math.max(1f, H * .006f), p);
        }

        String tm = time(now);
        text(c, tm, cx, H * .77f, fit(tm, H * .27f, W * .6f, Ui.display(), 0), Ui.INK, Ui.display(), Paint.Align.CENTER);
        text(c, (day ? "západ " + hm(st[1]) : "východ " + hm(st[0] < t ? sunTimes(tomorrow(now))[0] : st[0])),
                W * .06f, H * .94f, H * .042f, day ? Ui.AMBER_SOFT : Ui.DIM, Ui.regular(), Paint.Align.LEFT);
        alarm(c, W * .94f, H * .94f, H * .042f, Paint.Align.RIGHT);
        text(c, dateLine(now), W * .06f, H * .08f, H * .042f, Ui.DIM, Ui.regular(), Paint.Align.LEFT);
    }

    private static Calendar tomorrow(Calendar c) {
        Calendar t = (Calendar) c.clone();
        t.add(Calendar.DAY_OF_YEAR, 1);
        return t;
    }

    // ------------------------------------------------------------------ 5 tečky (LED matice)
    private static final String[] FONT = {
            "01110100011001110101110011000101110", // 0
            "00100011000010000100001000010001110", // 1
            "01110100010000100010001000100011111", // 2
            "11111000100010000010000011000101110", // 3
            "00010001100101010010111110001000010", // 4
            "11111100001111000001000011000101110", // 5
            "00110010001000011110100011000101110", // 6
            "11111000010001000100010000100001000", // 7
            "01110100011000101110100011000101110", // 8
            "01110100011000101111000010001001100", // 9
    };

    private void dots(Canvas c, Calendar now) {
        float W = getWidth(), H = getHeight();
        int hh = now.get(Calendar.HOUR_OF_DAY), mm = now.get(Calendar.MINUTE), ss = now.get(Calendar.SECOND);
        int[] d = {hh / 10, hh % 10, mm / 10, mm % 10};
        int cols = 25;
        float cell = Math.min(W * .88f / cols, H * .5f / 7);
        float x0 = (W - cell * cols) / 2, y0 = H * .49f - cell * 3.5f;
        float r = cell * .38f;
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        int col = 0;
        for (int k = 0; k < 4; k++) {
            boolean blank = k == 0 && d[0] == 0;
            String g = FONT[d[k]];
            for (int cx = 0; cx < 5; cx++) {
                for (int ry = 0; ry < 7; ry++) {
                    boolean on = !blank && g.charAt(ry * 5 + cx) == '1';
                    p.setColor(on ? Ui.AMBER : 0x12FFFFFF);
                    c.drawCircle(x0 + (col + cx + .5f) * cell, y0 + (ry + .5f) * cell, r, p);
                }
            }
            col += 6;
            if (k == 1) {
                boolean blink = ss % 2 == 0;
                for (int ry = 0; ry < 7; ry++) {
                    boolean on = blink && (ry == 2 || ry == 4);
                    p.setColor(on ? Ui.TEAL : 0x12FFFFFF);
                    c.drawCircle(x0 + (col + .5f) * cell, y0 + (ry + .5f) * cell, r, p);
                }
                col += 2;
            }
        }
        // sekundy: 60 teček
        float sw = W * .7f, sStep = sw / 60, sx0 = (W - sw) / 2 + sStep / 2, sy = y0 + 7 * cell + H * .08f;
        for (int i = 0; i < 60; i++) {
            p.setColor(i < ss ? Ui.TEAL : i == ss ? Ui.INK : 0x14FFFFFF);
            c.drawCircle(sx0 + i * sStep, sy, Math.max(1f, sStep * .26f), p);
        }
        text(c, dateLine(now), W / 2, H * .1f, H * .046f, Ui.DIM, Ui.regular(), Paint.Align.CENTER);
        alarm(c, W / 2, H * .92f, H * .046f, Paint.Align.CENTER);
    }
}
