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
    static final int CLASSIC = 0, ANALOG = 1, FLIP = 2, WORDS = 3, SUN = 4, DOTS = 5,
            EYES = 6, LAVA = 7, WATER = 8, ORBIT = 9, BAUHAUS = 10;
    static final String[] NAMES = {"Klasický", "Ručičky", "Překlápěcí", "Slovy", "Slunce", "Tečky",
            "Očička", "Láva", "Hladina", "Planety", "Bauhaus"};
    private static final int CORAL = 0xFFE8735E, VIOLET = 0xFF9A88F2;
    static final int COUNT = NAMES.length;

    private static final Locale CS = new Locale("cs", "CZ");
    private static final double LAT = 50.08, LON = 14.42;

    private int style;
    private String alarmTime, alarmLeft;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private Drawable alarmIcon;
    private int frameMs = 50; // animované vzhledy ~20 fps (náhledy pomaleji)
    private final android.graphics.Path path = new android.graphics.Path();
    private final java.util.Random rnd = new java.util.Random();

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

    /** Malý náhled v nastavení – animace stačí pomaleji. */
    void setPreview(boolean on) {
        frameMs = on ? 120 : 50;
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
            case EYES: eyes(c, now); break;
            case LAVA: lava(c, now); break;
            case WATER: water(c, now); break;
            case ORBIT: orbit(c, now); break;
            case BAUHAUS: bauhaus(c, now); break;
            default: classic(c, now); break;
        }
        if (style == EYES || style == LAVA || style == WATER || style == ORBIT) postInvalidateDelayed(frameMs);
    }

    private static float secs() {
        return (SystemClock.uptimeMillis() % 3600000L) / 1000f;
    }

    private void bigTime(Canvas c, String t, float cx, float cy, float size, int color) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Ui.display());
        p.setTextSize(size);
        p.setColor(color);
        p.setTextAlign(Paint.Align.CENTER);
        p.setLetterSpacing(-0.04f);
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(t, cx, cy - (fm.ascent + fm.descent) / 2, p);
        p.setLetterSpacing(0);
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

    // ------------------------------------------------------------------ 6 očička
    private float lookX, lookY, lookTX, lookTY;
    private long nextLook, blinkAt;

    private void eyes(Canvas c, Calendar now) {
        float W = getWidth(), H = getHeight();
        long ms = SystemClock.uptimeMillis();
        if (ms > nextLook) {
            // občas se podívá na čas dole, jinak kamkoli
            if (rnd.nextInt(4) == 0) { lookTX = 0; lookTY = .9f; }
            else { lookTX = rnd.nextFloat() * 2 - 1; lookTY = rnd.nextFloat() * 1.6f - .8f; }
            nextLook = ms + 1500 + rnd.nextInt(3500);
        }
        lookX += (lookTX - lookX) * .18f;
        lookY += (lookTY - lookY) * .18f;
        if (blinkAt == 0 || ms > blinkAt + 2000) blinkAt = ms + 2500 + rnd.nextInt(4500);
        float blink = 0;
        long bd = ms - blinkAt;
        if (bd >= 0 && bd < 220) blink = (float) Math.sin(Math.PI * bd / 220.0);
        int hr = now.get(Calendar.HOUR_OF_DAY);
        float sleepy = hr >= 22 || hr < 6 ? .42f : 0f; // v noci ospalá víčka
        float lid = Math.max(blink, sleepy);

        float rx = Math.min(H * .2f, W * .15f), ry = rx * 1.18f, ey = H * .4f;
        for (int i = 0; i < 2; i++) {
            float ex = W / 2 + (i == 0 ? -1 : 1) * rx * 1.35f;
            rf.set(ex - rx, ey - ry, ex + rx, ey + ry);
            c.save();
            path.reset();
            path.addOval(rf, android.graphics.Path.Direction.CW);
            c.clipPath(path);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.INK);
            c.drawOval(rf, p);
            float px0 = ex + lookX * rx * .42f, py0 = ey + lookY * ry * .4f;
            p.setColor(Ui.TEAL);
            c.drawCircle(px0, py0, rx * .52f, p);
            p.setColor(Ui.BG_STANDBY);
            c.drawCircle(px0, py0, rx * .3f, p);
            p.setColor(Ui.INK);
            c.drawCircle(px0 - rx * .14f, py0 - rx * .16f, rx * .1f, p);
            if (lid > 0) {
                p.setColor(Ui.TILE);
                float ly = ey - ry + 2 * ry * lid;
                c.drawRect(ex - rx, ey - ry, ex + rx, ly, p);
                p.setColor(Ui.AMBER);
                c.drawRect(ex - rx, ly - H * .008f, ex + rx, ly, p);
            }
            c.restore();
        }
        bigTime(c, time(now), W / 2, H * .8f, H * .16f, Ui.SOFT);
        alarm(c, W / 2, H * .93f, H * .042f, Paint.Align.CENTER);
        if (sleepy > 0) {
            float t = secs() % 3f / 3f;
            text(c, "z", W / 2 + rx * 2.6f + t * H * .06f, ey - ry - t * H * .12f, H * (.04f + t * .03f),
                    (int) (0xFF * (1 - t)) << 24 | 0x9AA3AE, Ui.medium(), Paint.Align.CENTER);
        }
    }

    // ------------------------------------------------------------------ 7 láva
    private static final float[][] BLOBS = { // fáze, rychlost, poloměr
            {0f, .11f, .55f}, {1.7f, .08f, .45f}, {3.1f, .13f, .38f}, {4.4f, .07f, .6f}, {5.6f, .1f, .34f},
            {2.4f, .09f, .42f}};
    private static final int[] LAVA_C = {Ui.AMBER, Ui.TEAL, CORAL, VIOLET, Ui.AMBER, CORAL};

    private void lava(Canvas c, Calendar now) {
        float W = getWidth(), H = getHeight(), t = secs();
        int shift = now.get(Calendar.HOUR_OF_DAY); // barvy se každou hodinu prohodí
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < BLOBS.length; i++) {
            float[] b = BLOBS[i];
            float x = W * (.5f + .42f * (float) Math.sin(t * b[1] + b[0]));
            float y = H * (.5f + .45f * (float) Math.sin(t * b[1] * .73f + b[0] * 1.3f));
            float r = H * b[2] * (1 + .12f * (float) Math.sin(t * .5f + i));
            int col = LAVA_C[(i + shift) % LAVA_C.length];
            p.setShader(new android.graphics.RadialGradient(x, y, r,
                    new int[]{(0x70 << 24) | (col & 0xFFFFFF), (0x30 << 24) | (col & 0xFFFFFF), col & 0xFFFFFF},
                    new float[]{0f, .55f, 1f}, android.graphics.Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r, p);
        }
        p.setShader(null);
        String tm = time(now);
        float size = fit(tm, H * .5f, W * .9f, Ui.display(), -0.04f);
        bigTime(c, tm, W / 2 + H * .006f, H * .5f + H * .01f, size, 0x88000000);
        bigTime(c, tm, W / 2, H * .5f, size, Ui.INK);
        text(c, dateLine(now), W / 2, H * .12f, H * .046f, Ui.SOFT, Ui.regular(), Paint.Align.CENTER);
        alarm(c, W / 2, H * .88f, H * .048f, Paint.Align.CENTER);
    }

    // ------------------------------------------------------------------ 8 hladina
    private void water(Canvas c, Calendar now) {
        float W = getWidth(), H = getHeight(), t = secs();
        float frac = (now.get(Calendar.MINUTE) + now.get(Calendar.SECOND) / 60f) / 60f; // za hodinu se nádoba naplní
        float top = H * (.94f - .8f * frac);
        float amp = H * .018f;
        // zadní vlna
        wave(top - amp * .6f, amp, .016f / (H / 480f), t * 1.3f + 2f, W, H);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF173B3A);
        c.drawPath(path, p);
        // přední vlna
        wave(top, amp, .012f / (H / 480f), -t * 1.8f, W, H);
        p.setColor(0xFF2A7570);
        c.drawPath(path, p);

        String tm = time(now);
        float size = fit(tm, H * .46f, W * .86f, Ui.display(), -0.04f);
        bigTime(c, tm, W / 2, H * .5f, size, Ui.INK);
        c.save();
        c.clipPath(path);
        bigTime(c, tm, W / 2, H * .5f, size, Ui.BG_STANDBY);
        // bublinky
        p.setColor(0x55EEF1F4);
        for (int i = 0; i < 7; i++) {
            float bx = W * ((i * .137f + .07f) % 1f);
            float speed = H * (.05f + (i % 3) * .025f);
            float by = H - ((t * speed + i * H * .29f) % (H - top + H * .05f));
            c.drawCircle(bx + (float) Math.sin(t * 2 + i) * H * .01f, by, H * (.006f + (i % 3) * .003f), p);
        }
        c.restore();
        text(c, dateLine(now), W * .05f, H * .08f, H * .042f, Ui.DIM, Ui.regular(), Paint.Align.LEFT);
        alarm(c, W * .95f, H * .08f, H * .042f, Paint.Align.RIGHT);
    }

    private void wave(float top, float amp, float k, float ph, float W, float H) {
        path.reset();
        path.moveTo(0, H);
        float step = Math.max(4f, W / 100f);
        for (float x = 0; x <= W + step; x += step) {
            float y = top + amp * (float) Math.sin(x * k + ph) + amp * .4f * (float) Math.sin(x * k * 2.3f - ph * .7f);
            path.lineTo(x, y);
        }
        path.lineTo(W, H);
        path.close();
    }

    // ------------------------------------------------------------------ 9 planety
    private void orbit(Canvas c, Calendar now) {
        float W = getWidth(), H = getHeight();
        float ms = now.get(Calendar.MILLISECOND) / 1000f;
        float sec = now.get(Calendar.SECOND) + ms;
        float min = now.get(Calendar.MINUTE) + sec / 60f;
        float hr = now.get(Calendar.HOUR) + min / 60f;
        float R3 = H * .46f, R1 = H * .33f, R2 = H * .1f;
        float cx = W - W * .04f - R3, cy = H / 2;

        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, H * .004f));
        p.setColor(Ui.LINE);
        c.drawCircle(cx, cy, R1, p);
        c.drawCircle(cx, cy, R3, p);
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            p.setColor(i % 3 == 0 ? Ui.FAINT : Ui.LINE);
            c.drawCircle(cx + (float) Math.sin(a) * R1, cy - (float) Math.cos(a) * R1, H * (i % 3 == 0 ? .009f : .006f), p);
        }
        // slunce
        for (int i = 3; i >= 1; i--) {
            p.setColor((0x12 * (4 - i)) << 24 | 0xF5B54A);
            c.drawCircle(cx, cy, H * .06f * (1 + i * .5f), p);
        }
        p.setColor(Ui.AMBER);
        c.drawCircle(cx, cy, H * .06f, p);
        // hodinová planeta + minutový měsíc
        double ah = Math.PI * 2 * hr / 12;
        float hx = cx + (float) Math.sin(ah) * R1, hy = cy - (float) Math.cos(ah) * R1;
        p.setStyle(Paint.Style.STROKE);
        p.setColor(0x336CC6C0);
        c.drawCircle(hx, hy, R2, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Ui.TEAL);
        c.drawCircle(hx, hy, H * .045f, p);
        double am = Math.PI * 2 * min / 60;
        p.setColor(Ui.INK);
        c.drawCircle(hx + (float) Math.sin(am) * R2, hy - (float) Math.cos(am) * R2, H * .02f, p);
        // vteřinová kometa s ohonem
        for (int i = 14; i >= 0; i--) {
            double as = Math.PI * 2 * (sec - i * .25f) / 60;
            int alpha = (int) (0xFF * (1 - i / 15f) * (i == 0 ? 1 : .5f));
            p.setColor(alpha << 24 | 0xF5B54A);
            c.drawCircle(cx + (float) Math.sin(as) * R3, cy - (float) Math.cos(as) * R3,
                    H * (.016f - i * .0008f), p);
        }
        // text vlevo
        float x = W * .06f, colW = cx - R3 - x - W * .03f;
        String tm = time(now);
        text(c, tm, x, H * .42f, fit(tm, H * .2f, colW, Ui.display(), 0), Ui.INK, Ui.display(), Paint.Align.LEFT);
        String day = new SimpleDateFormat("EEEE", CS).format(now.getTime());
        String date = new SimpleDateFormat("d. MMMM", CS).format(now.getTime());
        text(c, day, x, H * .58f, fit(day, H * .055f, colW, Ui.medium(), 0), Ui.SOFT, Ui.medium(), Paint.Align.LEFT);
        text(c, date, x, H * .66f, fit(date, H * .045f, colW, Ui.regular(), 0), Ui.DIM, Ui.regular(), Paint.Align.LEFT);
        alarm(c, x, H * .8f, Math.min(H * .042f, colW / 9f), Paint.Align.LEFT);
    }

    // ------------------------------------------------------------------ 10 bauhaus
    private static final int[] BAU_C = {Ui.AMBER, Ui.TEAL, CORAL, VIOLET};

    private void bauhaus(Canvas c, Calendar now) {
        float W = getWidth(), H = getHeight();
        int hh = now.get(Calendar.HOUR_OF_DAY), mm = now.get(Calendar.MINUTE);
        String d = two(hh) + two(mm);
        float ch = H * .56f, cw = ch * .72f, gap = H * .03f, colon = H * .12f;
        float total = 4 * cw + 2 * gap + colon;
        if (total > W * .94f) {
            float k = W * .94f / total;
            ch *= k; cw *= k; gap *= k; colon *= k; total *= k;
        }
        float x = (W - total) / 2, y = H * .5f - ch / 2;
        for (int i = 0; i < 4; i++) {
            int v = d.charAt(i) - '0';
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.TILE);
            c.drawRect(x, y, x + cw, y + ch, p);
            shape(v, i, x, y, cw, ch);
            int col = BAU_C[(v + i + hh) % BAU_C.length];
            p.setColor(col);
            c.drawPath(path, p);
            // číslice: světlá mimo tvar, tmavá uvnitř tvaru
            p.setTypeface(Ui.medium());
            p.setTextSize(ch * .78f);
            p.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = p.getFontMetrics();
            float by = y + ch / 2 - (fm.ascent + fm.descent) / 2;
            p.setColor(Ui.INK);
            c.drawText(String.valueOf(v), x + cw / 2, by, p);
            c.save();
            c.clipPath(path);
            p.setColor(Ui.BG_STANDBY);
            c.drawText(String.valueOf(v), x + cw / 2, by, p);
            c.restore();
            x += cw + (i == 1 ? colon : gap);
            if (i == 1) {
                float s = colon * .32f, mx = x - colon / 2;
                p.setColor(Ui.INK);
                c.drawRect(mx - s / 2, y + ch * .3f - s / 2, mx + s / 2, y + ch * .3f + s / 2, p);
                p.setColor(Ui.AMBER);
                c.drawCircle(mx, y + ch * .7f, s / 2, p);
            }
        }
        // vteřiny jako pruh barevných dílků
        int ss = now.get(Calendar.SECOND);
        float bw = total / 60f, by = y + ch + H * .05f;
        for (int i = 0; i < 60; i++) {
            p.setColor(i <= ss ? BAU_C[(i / 15) % 4] : 0x14FFFFFF);
            c.drawRect((W - total) / 2 + i * bw, by, (W - total) / 2 + (i + 1) * bw - Math.max(1f, bw * .25f),
                    by + H * .018f, p);
        }
        text(c, dateLine(now).toUpperCase(CS), W / 2, H * .1f, H * .04f, Ui.DIM, Ui.medium(), Paint.Align.CENTER);
        alarm(c, W / 2, H * .92f, H * .044f, Paint.Align.CENTER);
    }

    /** Geometrický tvar dlaždice podle číslice (kruh, půlkruh, čtvrtkruh, trojúhelník, kosočtverec…). */
    private void shape(int v, int pos, float x, float y, float w, float h) {
        path.reset();
        float m = Math.min(w, h);
        switch ((v + pos * 3) % 6) {
            case 0: path.addCircle(x + w / 2, y + h / 2, m * .46f, android.graphics.Path.Direction.CW); break;
            case 1:
                rf.set(x, y + h - w / 2, x + w, y + h + w / 2);
                path.addArc(rf, 180, 180);
                path.close();
                break;
            case 2:
                rf.set(x - w, y - w, x + w, y + w);
                path.moveTo(x, y);
                path.arcTo(rf, 0, 90);
                path.close();
                break;
            case 3:
                path.moveTo(x, y + h);
                path.lineTo(x + w, y + h);
                path.lineTo(x + w / 2, y + h * .3f);
                path.close();
                break;
            case 4:
                path.moveTo(x + w / 2, y + h * .12f);
                path.lineTo(x + w, y + h / 2);
                path.lineTo(x + w / 2, y + h * .88f);
                path.lineTo(x, y + h / 2);
                path.close();
                break;
            default:
                path.addRect(x, y + h * .5f, x + w, y + h, android.graphics.Path.Direction.CW);
                break;
        }
    }
}
