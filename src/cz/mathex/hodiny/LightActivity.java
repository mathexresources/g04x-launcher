package cz.mathex.hodiny;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

/** Ovládání žárovky Yeelight / Mi LED: vypínač, jas, teplota bílé, barvy, svítání před budíkem. */
public class LightActivity extends Activity {
    private static final int[] COLORS = {0xFFB040, 0xFF6A2A, 0xFF2A2A, 0xFF3D9A, 0xA040FF, 0x3A6BFF, 0x2AC8C8, 0x40D060};

    private LinearLayout power, body, missing;
    private TextView status, brightVal, ctVal;
    private SeekBar bright, ct;
    private boolean on;
    private long lastSend;

    private int px(float f) {
        return Ui.px(this, f);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.loadFonts(this);
        LinearLayout root = Ui.box(this, true);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.bar(this, "Světlo", "Najít žárovku", new View.OnClickListener() {
            public void onClick(View v) {
                find();
            }
        }));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout content = Ui.box(this, true);
        content.setPadding(px(.0625f), px(.02f), px(.0625f), px(.05f));
        sv.addView(content);
        root.addView(sv, Ui.lp(-1, 0, 1f));

        // žárovka nenalezena
        missing = Ui.box(this, true);
        missing.setBackground(Ui.round(Ui.TILE, px(.046f)));
        missing.setPadding(px(.05f), px(.04f), px(.05f), px(.04f));
        TextView mt = Ui.text(this, "Žárovka zatím není nastavená", .046f, Ui.INK);
        mt.setTypeface(Ui.medium());
        missing.addView(mt);
        missing.addView(Ui.text(this, "1. V aplikaci Yeelight (stejný Xiaomi účet a region) zapni u žárovky „LAN Control“.\n"
                + "2. Ťukni vpravo nahoře na „Najít žárovku“.\n"
                + "Nebo zadej IP žárovky v ⚙ Nastavení.", .035f, Ui.DIM), Ui.top(this, .02f));
        content.addView(missing, new LinearLayout.LayoutParams(-1, -2));

        body = Ui.box(this, true);
        content.addView(body, new LinearLayout.LayoutParams(-1, -2));

        // vypínač + stav
        LinearLayout top = Ui.box(this, false);
        top.setGravity(Gravity.CENTER_VERTICAL);
        power = Ui.roundButton(this, R.drawable.ic_power, Ui.INK, Ui.TILE, .25f, .1f);
        power.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Yeelight.power(LightActivity.this, !on, new Yeelight.Done() {
                    public void done(boolean ok, JSONArray r) {
                        if (!ok) fail();
                        else refresh();
                    }
                });
            }
        });
        top.addView(power);
        LinearLayout tt = Ui.box(this, true);
        TextView name = Ui.text(this, "Mi LED Smart Bulb", .05f, Ui.INK);
        name.setTypeface(Ui.medium());
        tt.addView(name);
        status = Ui.text(this, "načítám…", .038f, Ui.DIM);
        tt.addView(status, Ui.top(this, .01f));
        LinearLayout.LayoutParams tl = Ui.lp(0, -2, 1f);
        tl.leftMargin = px(.05f);
        top.addView(tt, tl);
        body.addView(top);

        // jas
        brightVal = Ui.text(this, "", .038f, Ui.SOFT);
        bright = slider(100, solid(Ui.AMBER), false);
        body.addView(sliderRow("Jas", brightVal, bright), Ui.top(this, .04f));
        bright.setOnSeekBarChangeListener(new Seek() {
            void changed(int p, boolean end) {
                brightVal.setText(Math.max(1, p) + " %");
                if (end || throttle()) Yeelight.bright(LightActivity.this, Math.max(1, p), null);
            }
        });

        // teplota bílé 1700–6500 K
        ctVal = Ui.text(this, "", .038f, Ui.SOFT);
        GradientDrawable warmCold = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFFFF9A3C, 0xFFFFE2B8, 0xFFDDEBFF});
        ct = slider(48, warmCold, true);
        body.addView(sliderRow("Bílá", ctVal, ct), Ui.top(this, .03f));
        ct.setOnSeekBarChangeListener(new Seek() {
            void changed(int p, boolean end) {
                int k = 1700 + p * 100;
                ctVal.setText(k + " K");
                if (end || throttle()) Yeelight.ct(LightActivity.this, k, null);
            }
        });

        // barvy
        TextView cl = Ui.text(this, "BARVY", .03f, Ui.DIM);
        cl.setLetterSpacing(0.03f);
        body.addView(cl, Ui.top(this, .04f));
        LinearLayout colors = Ui.box(this, false);
        for (final int c : COLORS) {
            View dot = new View(this);
            dot.setBackground(Ui.circle(0xFF000000 | c));
            dot.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Yeelight.rgb(LightActivity.this, c, new Yeelight.Done() {
                        public void done(boolean ok, JSONArray r) {
                            if (!ok) fail();
                            else refresh();
                        }
                    });
                }
            });
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(px(.12f), px(.12f));
            if (colors.getChildCount() > 0) dl.leftMargin = px(.03f);
            colors.addView(dot, dl);
        }
        body.addView(colors, Ui.top(this, .02f));

        // budík
        TextView al = Ui.text(this, "BUDÍK", .03f, Ui.DIM);
        al.setLetterSpacing(0.03f);
        body.addView(al, Ui.top(this, .045f));
        body.addView(check("Svítání: 10 min před budíkem se pomalu rozsvítí", "bulb_sunrise", true), Ui.top(this, .01f));
        body.addView(check("Po vypnutí budíku rozsvítit naplno", "bulb_full_on_dismiss", true));

        setContentView(root);
        show(Yeelight.configured(this));
        if (Yeelight.configured(this)) refresh();
        else find();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
    }

    private CheckBox check(String text, final String key, boolean def) {
        CheckBox c = new CheckBox(this);
        c.setText(text);
        c.setTextColor(Ui.INK);
        c.setTypeface(Ui.regular());
        c.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.base(this) * .04f);
        c.setButtonTintList(ColorStateList.valueOf(Ui.AMBER));
        c.setChecked(Prefs.get(this).getBoolean(key, def));
        c.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean v) {
                Prefs.get(LightActivity.this).edit().putBoolean(key, v).apply();
                Alarms.schedule(LightActivity.this); // přeplánovat svítání
            }
        });
        c.setPadding(px(.02f), px(.02f), 0, px(.02f));
        return c;
    }

    private static GradientDrawable solid(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        return g;
    }

    private SeekBar slider(int max, GradientDrawable fill, boolean gradient) {
        SeekBar s = new SeekBar(this);
        s.setMax(max);
        float r = px(.03f);
        GradientDrawable track = new GradientDrawable();
        track.setColor(Ui.LINE);
        track.setCornerRadius(r);
        fill.setCornerRadius(r);
        // pro teplotu je barevná celá dráha, pro jas jen vyplněná část
        LayerDrawable ld = gradient
                ? new LayerDrawable(new android.graphics.drawable.Drawable[]{fill, new ClipDrawable(solid(0), Gravity.START, ClipDrawable.HORIZONTAL)})
                : new LayerDrawable(new android.graphics.drawable.Drawable[]{track, new ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL)});
        ld.setId(0, android.R.id.background);
        ld.setId(1, android.R.id.progress);
        s.setProgressDrawable(ld);
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(Ui.INK);
        thumb.setSize(px(.075f), px(.075f));
        s.setThumb(thumb);
        s.setSplitTrack(false);
        s.setPadding(px(.04f), 0, px(.04f), 0);
        s.setMinimumHeight(px(.05f));
        return s;
    }

    private LinearLayout sliderRow(String label, TextView value, SeekBar s) {
        LinearLayout row = Ui.box(this, false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.round(Ui.TILE, px(.04f)));
        row.setPadding(px(.04f), 0, px(.04f), 0);
        TextView l = Ui.text(this, label, .04f, Ui.INK);
        row.addView(l, new LinearLayout.LayoutParams(px(.14f), -2));
        LinearLayout.LayoutParams sl = Ui.lp(0, px(.09f), 1f);
        row.addView(s, sl);
        value.setGravity(Gravity.END);
        row.addView(value, new LinearLayout.LayoutParams(px(.17f), -2));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, px(.15f));
        row.setLayoutParams(rl);
        return row;
    }

    /** Max ~4 příkazy za sekundu při tažení (žárovka má limit). */
    private boolean throttle() {
        long now = System.currentTimeMillis();
        if (now - lastSend < 250) return false;
        lastSend = now;
        return true;
    }

    private abstract static class Seek implements SeekBar.OnSeekBarChangeListener {
        abstract void changed(int p, boolean end);

        public void onProgressChanged(SeekBar s, int p, boolean user) {
            if (user) changed(p, false);
        }

        public void onStartTrackingTouch(SeekBar s) {
        }

        public void onStopTrackingTouch(SeekBar s) {
            changed(s.getProgress(), true);
        }
    }

    private void show(boolean configured) {
        missing.setVisibility(configured ? View.GONE : View.VISIBLE);
        body.setVisibility(configured ? View.VISIBLE : View.GONE);
    }

    private void find() {
        Toast.makeText(this, "Hledám žárovku v síti…", Toast.LENGTH_SHORT).show();
        Yeelight.discover(this, new Yeelight.Done() {
            public void done(boolean ok, JSONArray r) {
                if (ok) {
                    Toast.makeText(LightActivity.this, "Žárovka nalezena: " + Yeelight.ip(LightActivity.this), Toast.LENGTH_SHORT).show();
                    show(true);
                    refresh();
                    Alarms.schedule(LightActivity.this);
                } else if (!Yeelight.configured(LightActivity.this)) {
                    Toast.makeText(LightActivity.this, "Nenalezena – je zapnuté LAN Control?", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void fail() {
        status.setText("žárovka neodpovídá (" + Yeelight.ip(this) + ")");
        status.setTextColor(Ui.BAD);
    }

    private void refresh() {
        Yeelight.state(this, new Yeelight.StateDone() {
            public void done(Yeelight.State s) {
                if (s == null) {
                    fail();
                    return;
                }
                on = s.on;
                status.setTextColor(Ui.DIM);
                status.setText(s.on ? (s.mode == 1 ? "svítí barevně" : "svítí · " + s.ct + " K") : "vypnuto");
                power.setBackground(Ui.circle(s.on ? Ui.AMBER : Ui.TILE));
                Ui.tint((android.widget.ImageView) power.getChildAt(0), s.on ? Ui.ON_AMBER : Ui.INK);
                bright.setProgress(s.bright);
                brightVal.setText(s.bright + " %");
                ct.setProgress(Math.max(0, Math.min(48, (s.ct - 1700) / 100)));
                ctVal.setText(s.ct + " K");
            }
        });
    }
}
