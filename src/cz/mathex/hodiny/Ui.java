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
    static final int BG = 0xFF0F1216;
    static final int BG_STANDBY = 0xFF07080A;
    static final int TILE = 0xFF171B21;
    static final int TILE_PRESSED = 0xFF232A33;
    static final int PILL = 0xFF111419;
    static final int LINE = 0xFF262C35;
    static final int INK = 0xFFEEF1F4;
    static final int SOFT = 0xFFC9D0D8;
    static final int DIM = 0xFF9AA3AE;
    static final int FAINT = 0xFF7C8590;
    static final int AMBER = 0xFFF5B54A;
    static final int AMBER_SOFT = 0xFFC8A15E;
    static final int AMBER_BG = 0x1FF5B54A;
    static final int ON_AMBER = 0xFF1A1408;
    static final int TEAL = 0xFF6CC6C0;
    static final int TEAL_BG = 0xFF1F3A39;
    static final int ACCENT = TEAL;
    static final int NIGHT = 0xFFC8503F;
    static final int BAD = 0xFFFF7A7A;

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
