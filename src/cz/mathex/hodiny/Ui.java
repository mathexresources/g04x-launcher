package cz.mathex.hodiny;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Barvy, velikosti a pomocné funkce pro UI. Všechny rozměry jsou poměrem k výšce displeje. */
final class Ui {
    static final int BG = 0xFF0B0E0D;
    static final int TILE = 0xFF171C1A;
    static final int TILE_PRESSED = 0xFF26302B;
    static final int INK = 0xFFEDF2EF;
    static final int DIM = 0xFF7E8A85;
    static final int ACCENT = 0xFF8FE3B0;
    static final int NIGHT = 0xFF8A2A2A;

    private Ui() {}

    /** Kratší strana displeje v px (na šířku = výška). */
    static int base(Context c) {
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        return Math.min(dm.widthPixels, dm.heightPixels);
    }

    static int px(Context c, float f) {
        return Math.round(base(c) * f);
    }

    static TextView text(Context c, String s, float size, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, base(c) * size);
        t.setIncludeFontPadding(false);
        return t;
    }

    static Typeface light() {
        return Typeface.create("sans-serif-light", Typeface.NORMAL);
    }

    static Typeface medium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    static GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    /** Pozadí dlaždice se stavem „stisknuto“. */
    static StateListDrawable tile(Context c) {
        float r = px(c, .035f);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, round(TILE_PRESSED, r));
        s.addState(new int[]{}, round(TILE, r));
        return s;
    }

    /** Průhledné pozadí, které při stisku zesvětlá (tlačítka v kartě hudby, lišta). */
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

    /** Horní lišta „‹ Zpět · titulek · akce“ pro vedlejší obrazovky. */
    static LinearLayout bar(final Activity a, String title, String actionLabel, View.OnClickListener action) {
        LinearLayout bar = box(a, false);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = px(a, .03f);
        TextView back = text(a, "‹  Zpět", .05f, ACCENT);
        back.setTypeface(medium());
        back.setPadding(pad, pad, pad * 2, pad);
        back.setBackground(press(a));
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                a.finish();
            }
        });
        bar.addView(back);
        TextView t = text(a, title, .05f, DIM);
        t.setGravity(Gravity.CENTER);
        bar.addView(t, lp(0, -2, 1f));
        TextView act = text(a, actionLabel == null ? "" : actionLabel, .05f, INK);
        act.setPadding(pad * 2, pad, pad, pad);
        if (action != null) {
            act.setBackground(press(a));
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
