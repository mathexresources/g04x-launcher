package cz.mathex.hodiny;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

/** Ovládání žárovky Yeelight / Mi LED: vypínač, jas, teplota bílé, barvy, svítání před budíkem. */
public class LightActivity extends BaseActivity {
    /** Výchozí předvolby: název, teplota bílé (K), jas (%). Podržením se přepíšou aktuálním stavem. */
    private static final String[] PRESET_NAMES = {"Bílá max", "Halogen", "Noční"};
    private static final int[][] PRESET_DEFAULTS = {{5500, 100}, {2700, 60}, {1800, 5}};
    private LinearLayout presets;

    private static final int[] COLORS = {0xFFB040, 0xFF6A2A, 0xFF2A2A, 0xFF3D9A, 0xA040FF, 0x3A6BFF, 0x2AC8C8, 0x40D060};

    private LinearLayout power, body, missing;
    private TextView status;
    private BigSlider bright, ct;
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

        // předvolby
        presets = Ui.box(this, false);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, px(.2f));
        pl.topMargin = px(.04f);
        body.addView(presets, pl);
        renderPresets();

        // jas – celá dlaždice je posuvník
        bright = new BigSlider(this, "Jas", 1, 100, Ui.AMBER, null);
        bright.setListener(new BigSlider.Listener() {
            public void onChange(int v, boolean end) {
                bright.setValueText(v + " %");
                if (end || throttle()) Yeelight.bright(LightActivity.this, v, null);
            }
        });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, px(.16f));
        bl.topMargin = px(.04f);
        body.addView(bright, bl);

        // teplota bílé 1700–6500 K – přechod přes celou dlaždici
        ct = new BigSlider(this, "Bílá", 1700, 6500, 0, new int[]{0xFFFF9A3C, 0xFFFFD9A6, 0xFFFFF4E6, 0xFFDDEBFF});
        ct.setListener(new BigSlider.Listener() {
            public void onChange(int v, boolean end) {
                int k = Math.round(v / 100f) * 100;
                ct.setValueText(k + " K");
                if (end || throttle()) Yeelight.ct(LightActivity.this, k, null);
            }
        });
        LinearLayout.LayoutParams cl2 = new LinearLayout.LayoutParams(-1, px(.16f));
        cl2.topMargin = px(.03f);
        body.addView(ct, cl2);

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

    /** Max ~4 příkazy za sekundu při tažení (žárovka má limit). */
    private boolean throttle() {
        long now = System.currentTimeMillis();
        if (now - lastSend < 250) return false;
        lastSend = now;
        return true;
    }

    private int[] preset(int i) {
        android.content.SharedPreferences p = Prefs.get(this);
        return new int[]{p.getInt("preset" + i + "_k", PRESET_DEFAULTS[i][0]), p.getInt("preset" + i + "_b", PRESET_DEFAULTS[i][1])};
    }

    /** Barva náhledu předvolby podle teploty bílé. */
    private static int ctColor(int k) {
        if (k <= 2200) return 0xFFFF9A3C;
        if (k <= 3200) return 0xFFFFC46B;
        if (k <= 4500) return 0xFFFFE6C0;
        return 0xFFF2F6FF;
    }

    private void renderPresets() {
        presets.removeAllViews();
        for (int i = 0; i < PRESET_NAMES.length; i++) {
            final int idx = i;
            final int[] v = preset(i);
            LinearLayout t = Ui.box(this, true);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setBackground(Ui.tile(this));
            t.setPadding(px(.04f), 0, px(.03f), 0);
            LinearLayout head = Ui.box(this, false);
            head.setGravity(Gravity.CENTER_VERTICAL);
            View dot = new View(this);
            dot.setBackground(Ui.circle(ctColor(v[0])));
            head.addView(dot, new LinearLayout.LayoutParams(px(.05f), px(.05f)));
            TextView n = Ui.text(this, PRESET_NAMES[i], .046f, Ui.INK);
            n.setTypeface(Ui.medium());
            head.addView(n, Ui.left(this, .02f));
            t.addView(head);
            t.addView(Ui.text(this, v[0] + " K · " + v[1] + " %", .035f, Ui.DIM), Ui.top(this, .012f));
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View x) {
                    int[] p = preset(idx);
                    Yeelight.scene(LightActivity.this, p[0], p[1], new Yeelight.Done() {
                        public void done(boolean ok, JSONArray r) {
                            if (!ok) fail();
                            else refresh();
                        }
                    });
                }
            });
            t.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View x) {
                    int k = Math.round(ct.getValue() / 100f) * 100, b = bright.getValue();
                    Prefs.get(LightActivity.this).edit().putInt("preset" + idx + "_k", k).putInt("preset" + idx + "_b", b).apply();
                    Toast.makeText(LightActivity.this, "„" + PRESET_NAMES[idx] + "“ uloženo: " + k + " K · " + b + " %", Toast.LENGTH_SHORT).show();
                    renderPresets();
                    return true;
                }
            });
            LinearLayout.LayoutParams lp = Ui.lp(0, -1, 1f);
            if (i > 0) lp.leftMargin = px(.03f);
            presets.addView(t, lp);
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
                bright.setValue(s.bright, s.bright + " %");
                ct.setValue(s.ct, s.ct + " K");
            }
        });
    }
}
