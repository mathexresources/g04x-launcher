package cz.mathex.hodiny;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Design „Ember“: teplý tmavý podklad, jantar pro budík a přehrávání, tyrkys pro počasí a rádio,
 * čísla Space Grotesk, texty systémové Roboto (ostřejší na 480p). Všechny rozměry jsou poměrem k výšce displeje (480 px = 1.0).
 */
final class Ui {
    // barvy se mění podle zvolené palety (applyPalette) – proto nejsou final
    static int BG = 0xFF0F1216;
    static int BG_STANDBY = 0xFF07080A;
    static int TILE = 0xFF171B21;
    static int TILE_PRESSED = 0xFF232A33;
    static int PILL = 0xFF111419;
    static int LINE = 0xFF262C35;
    static final int INK = 0xFFEEF1F4;
    static final int SOFT = 0xFFC9D0D8;
    static final int DIM = 0xFF9AA3AE;
    static final int FAINT = 0xFF7C8590;
    static int AMBER = 0xFFF5B54A;
    static int AMBER_SOFT = 0xFFC8A15E;
    static int AMBER_BG = 0x1FF5B54A;
    static int ON_AMBER = 0xFF1A1408;
    static int TEAL = 0xFF6CC6C0;
    static int TEAL_BG = 0xFF1F3A39;
    static int ACCENT = TEAL;
    static final int NIGHT = 0xFFC8503F;
    static final int BAD = 0xFFFF7A7A;

    static final String[] PALETTES = {"Ember", "Les", "Oceán", "Neon", "Mono"};
    /** bg, standby, dlaždice, stisknutá, pilulka, linka, hlavní, hlavní tlumená, na hlavní, druhá, druhá pozadí */
    private static final int[][] PAL = {
            {0xFF0F1216, 0xFF07080A, 0xFF171B21, 0xFF232A33, 0xFF111419, 0xFF262C35,
                    0xFFF5B54A, 0xFFC8A15E, 0xFF1A1408, 0xFF6CC6C0, 0xFF1F3A39},
            {0xFF0E1310, 0xFF060907, 0xFF161D18, 0xFF212B24, 0xFF101511, 0xFF243029,
                    0xFFB8DC6E, 0xFF97AE68, 0xFF141A08, 0xFF6FC49A, 0xFF1E3A2C},
            {0xFF0C1118, 0xFF05080C, 0xFF141B25, 0xFF1E2835, 0xFF0F151D, 0xFF222D3B,
                    0xFF5CB8F5, 0xFF6E9EC2, 0xFF06121C, 0xFFF59E7A, 0xFF3A2620},
            {0xFF120E18, 0xFF08060B, 0xFF1C1623, 0xFF292032, 0xFF15101B, 0xFF2E2538,
                    0xFFFF5FB2, 0xFFC27099, 0xFF1E0814, 0xFF8E7CFF, 0xFF27214A},
            {0xFF101010, 0xFF060606, 0xFF1A1A1A, 0xFF262626, 0xFF131313, 0xFF2A2A2A,
                    0xFFF2F2F2, 0xFFB0B0B0, 0xFF111111, 0xFFB8B8B8, 0xFF2A2A2A},
    };
    private static int paletteId = -1;
    /** Zvyšuje se při změně palety – obrazovky se podle něj znovu sestaví. */
    static int paletteGen;

    static void applyPalette(Context c) {
        int id = Prefs.get(c).getInt("palette", 0);
        if (id < 0 || id >= PAL.length) id = 0;
        if (id == paletteId) return;
        int[] q = PAL[id];
        BG = q[0]; BG_STANDBY = q[1]; TILE = q[2]; TILE_PRESSED = q[3]; PILL = q[4]; LINE = q[5];
        AMBER = q[6]; AMBER_SOFT = q[7]; ON_AMBER = q[8]; TEAL = q[9]; TEAL_BG = q[10];
        AMBER_BG = 0x1F000000 | (AMBER & 0xFFFFFF);
        ACCENT = TEAL;
        if (paletteId != -1) paletteGen++;
        paletteId = id;
    }

    /** Výrazná barva obrázku (obal alba) – nejsytější z jasných pixelů zmenšeniny; 0 = nic výrazného. */
    static int dominant(android.graphics.Bitmap b) {
        if (b == null) return 0;
        try {
            android.graphics.Bitmap s = android.graphics.Bitmap.createScaledBitmap(b, 24, 24, true);
            float best = 0;
            int bestC = 0;
            float[] hsv = new float[3];
            for (int y = 0; y < 24; y++) {
                for (int x = 0; x < 24; x++) {
                    int px = s.getPixel(x, y);
                    android.graphics.Color.colorToHSV(px, hsv);
                    if (hsv[2] < .25f) continue;
                    float score = hsv[1] * (.4f + hsv[2]);
                    if (score > best) { best = score; bestC = px; }
                }
            }
            if (best < .15f) return 0;
            android.graphics.Color.colorToHSV(bestC, hsv);
            hsv[1] = Math.min(1f, Math.max(.45f, hsv[1]));
            hsv[2] = Math.max(.75f, hsv[2]);
            return android.graphics.Color.HSVToColor(hsv);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Směs dvou barev (t = 0 → a, 1 → b). */
    static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    /** Posun odstínu o deg stupňů. */
    static int hue(int c, float deg) {
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(c, hsv);
        hsv[0] = (hsv[0] + deg + 360) % 360;
        return android.graphics.Color.HSVToColor(hsv);
    }

    static boolean light(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return r * .299 + g * .587 + b * .114 > 150;
    }

    private static Typeface spaceLight, spaceRegular, interRegular, interSemi;

    private Ui() {}

    static int base(Context c) {
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        return Math.min(dm.widthPixels, dm.heightPixels);
    }

    static int px(Context c, float f) {
        return Math.round(base(c) * f);
    }

    private static Typeface font(Context c, String file, Typeface fallback) {
        try {
            return Typeface.createFromAsset(c.getAssets(), "fonts/" + file);
        } catch (Exception e) {
            return fallback;
        }
    }

    static void loadFonts(Context c) {
        applyPalette(c);
        if (interRegular != null) return;
        spaceLight = font(c, "space_light.ttf", Typeface.create("sans-serif-light", Typeface.NORMAL));
        spaceRegular = font(c, "space_regular.ttf", Typeface.create("sans-serif", Typeface.NORMAL));
        // texty: systémové Roboto – je hintované a na displeji 480p ostřejší než Inter
        interRegular = Typeface.create("sans-serif", Typeface.NORMAL);
        interSemi = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    /** Čísla – Space Grotesk Light / Regular. */
    static Typeface light() {
        return spaceLight;
    }

    static Typeface display() {
        return spaceRegular;
    }

    /** Texty – systémové Roboto. */
    static Typeface regular() {
        return interRegular;
    }

    static Typeface medium() {
        return interSemi;
    }

    static TextView text(Context c, String s, float size, int color) {
        loadFonts(c);
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(color);
        t.setTypeface(interRegular);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, base(c) * size);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Čtvercová ikona (vektor z res/drawable) obarvená na color, strana = size × výška displeje. */
    static ImageView icon(Context c, int res, int color, float size) {
        ImageView v = new ImageView(c);
        v.setImageResource(res);
        v.setImageTintList(ColorStateList.valueOf(color));
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int s = px(c, size);
        v.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        return v;
    }

    static void tint(ImageView v, int color) {
        v.setImageTintList(ColorStateList.valueOf(color));
    }

    static GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    static GradientDrawable circle(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    /** Pozadí karty / dlaždice se stavem „stisknuto“. */
    static StateListDrawable tile(Context c) {
        return tile(c, TILE, .046f);
    }

    static StateListDrawable tile(Context c, int color, float radius) {
        float r = px(c, radius);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, round(color == TILE ? TILE_PRESSED : blend(color), r));
        s.addState(new int[]{}, round(color, r));
        return s;
    }

    private static int blend(int c) {
        int a = c >>> 24, r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return (a << 24) | (Math.min(255, r + 24) << 16) | (Math.min(255, g + 24) << 8) | Math.min(255, b + 24);
    }

    /** Průhledné pozadí, které při stisku zesvětlá. */
    static StateListDrawable press(Context c) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, round(TILE_PRESSED, px(c, .03f)));
        s.addState(new int[]{}, new ColorDrawable(0));
        return s;
    }

    static LinearLayout box(Context c, boolean vertical) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        return l;
    }

    static LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    static LinearLayout.LayoutParams top(Context c, float margin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.topMargin = px(c, margin);
        return p;
    }

    static LinearLayout.LayoutParams left(Context c, float margin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.leftMargin = px(c, margin);
        return p;
    }

    /** Štítek s ikonou (svátek, narozeniny, budík…). */
    static LinearLayout chip(Context c, int iconRes, int iconColor, String text, int textColor, int bg, float size) {
        LinearLayout l = box(c, false);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setBackground(round(bg, px(c, .5f)));
        int ph = px(c, .025f), pv = px(c, .0125f);
        l.setPadding(ph, pv, ph, pv);
        l.addView(icon(c, iconRes, iconColor, size * 1.05f));
        TextView t = text(c, text, size, textColor);
        t.setSingleLine(true);
        l.addView(t, left(c, .014f));
        return l;
    }

    /** Kulaté tlačítko s ikonou (přehrávání). */
    static LinearLayout roundButton(Context c, int iconRes, int iconColor, int bg, float size, float iconSize) {
        LinearLayout b = box(c, false);
        b.setGravity(Gravity.CENTER);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, circle(bg == 0 ? TILE_PRESSED : blend(bg)));
        s.addState(new int[]{}, bg == 0 ? new ColorDrawable(0) : circle(bg));
        b.setBackground(s);
        b.addView(icon(c, iconRes, iconColor, iconSize));
        int sz = px(c, size);
        b.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
        return b;
    }

    static void setIcon(LinearLayout roundButton, int res) {
        ((ImageView) roundButton.getChildAt(0)).setImageResource(res);
    }

    /** Celá obrazovka bez lišt, displej nezhasíná. */
    static void immersive(Activity a) {
        a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        a.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    /** Tlačítko „‹ Zpět“. */
    static LinearLayout back(final Activity a) {
        LinearLayout back = box(a, false);
        back.setGravity(Gravity.CENTER_VERTICAL);
        int p = px(a, .025f);
        back.setPadding(p / 2, p, p * 3 / 2, p);
        back.setBackground(press(a));
        back.addView(icon(a, R.drawable.ic_back, TEAL, .058f));
        TextView t = text(a, "Zpět", .046f, TEAL);
        t.setTypeface(medium());
        back.addView(t, left(a, .008f));
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                a.finish();
            }
        });
        return back;
    }

    /** Horní lišta „‹ Zpět · titulek · akce“ pro vedlejší obrazovky. */
    static LinearLayout bar(final Activity a, String title, String actionLabel, View.OnClickListener action) {
        LinearLayout bar = box(a, false);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = px(a, .03f);
        bar.setPadding(pad, pad / 2, pad, 0);
        bar.addView(back(a));
        TextView t = text(a, title, .042f, INK);
        t.setTypeface(medium());
        t.setGravity(Gravity.CENTER);
        bar.addView(t, lp(0, -2, 1f));
        TextView act = text(a, actionLabel == null ? "" : actionLabel, .038f, INK);
        int p = px(a, .025f);
        act.setPadding(p * 3 / 2, p, p * 3 / 2, p);
        if (action != null) {
            act.setBackground(tile(a, TILE, .035f));
            act.setOnClickListener(action);
        }
        bar.addView(act);
        return bar;
    }

    static boolean launch(Context c, String pkg, String name) {
        Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) {
            Toast.makeText(c, name + " není nainstalovaný", Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            c.startActivity(i);
            return true;
        } catch (ActivityNotFoundException e) {
            Toast.makeText(c, name + " nejde spustit", Toast.LENGTH_SHORT).show();
            return false;
        }
    }
}
