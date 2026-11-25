package cz.mathex.hodiny;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Nový / upravovaný budík ve dvou krocích:
 * 1) čas na otočných kolečkách (hodiny, minuty), 2) jednou / opakovat ve dny + ranní tisk.
 */
public class AlarmEditActivity extends BaseActivity {
    private int id = -1;
    private int days;
    private boolean print;
    private WheelView hours, minutes;
    private TextView when1, when2;
    private LinearLayout step1, step2, daysBox;
    private TextView onceBtn, repeatBtn, printPill;
    private final TextView[] dayViews = new TextView[7];

    private int px(float f) {
        return Ui.px(this, f);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.loadFonts(this);
        id = getIntent().getIntExtra("id", -1);
        Alarms.Alarm a = id >= 0 ? Alarms.find(Alarms.load(this), id) : null;
        if (a == null) id = -1;
        int h0 = a != null ? a.hour : Prefs.get(this).getInt("last_h", 7);
        int m0 = a != null ? a.minute : Prefs.get(this).getInt("last_m", 0);
        days = a != null ? a.days : 0;
        print = a != null ? a.print : Prefs.get(this).getBoolean("last_print", true);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        // ---------- krok 1: čas
        step1 = Ui.box(this, true);
        step1.setPadding(px(.0625f), px(.04f), px(.0625f), px(.04f));
        when1 = Ui.text(this, "", .042f, Ui.AMBER);
        when1.setGravity(Gravity.CENTER_HORIZONTAL);
        step1.addView(when1, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout wheels = Ui.box(this, false);
        wheels.setGravity(Gravity.CENTER);
        hours = new WheelView(this, 24, h0);
        minutes = new WheelView(this, 60, m0);
        WheelView.Listener l = new WheelView.Listener() {
            public void onValue(int v) {
                updateWhen();
            }
        };
        hours.setListener(l);
        minutes.setListener(l);
        wheels.addView(hours, new LinearLayout.LayoutParams(px(.42f), -1));
        TextView colon = Ui.text(this, ":", .15f, Ui.INK);
        colon.setTypeface(Ui.display());
        colon.setPadding(px(.04f), 0, px(.04f), px(.03f));
        wheels.addView(colon);
        wheels.addView(minutes, new LinearLayout.LayoutParams(px(.42f), -1));
        LinearLayout.LayoutParams wl = Ui.lp(-1, 0, 1f);
        wl.topMargin = px(.015f);
        wl.bottomMargin = px(.02f);
        step1.addView(wheels, wl);

        LinearLayout b1 = Ui.box(this, false);
        b1.addView(button("Zrušit", Ui.TILE, Ui.INK, new Runnable() {
            public void run() {
                finish();
            }
        }), Ui.lp(0, -1, 1f));
        LinearLayout.LayoutParams nl = Ui.lp(0, -1, 1.6f);
        nl.leftMargin = px(.03f);
        b1.addView(button("Další  ›", Ui.AMBER, Ui.ON_AMBER, new Runnable() {
            public void run() {
                showStep(2);
            }
        }), nl);
        step1.addView(b1, new LinearLayout.LayoutParams(-1, px(.15f)));
        root.addView(step1, new FrameLayout.LayoutParams(-1, -1));

        // ---------- krok 2: jednou / dny + ranní tisk
        step2 = Ui.box(this, true);
        step2.setPadding(px(.0625f), px(.04f), px(.0625f), px(.04f));
        when2 = Ui.text(this, "", .042f, Ui.AMBER);
        when2.setGravity(Gravity.CENTER_HORIZONTAL);
        step2.addView(when2, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout seg = Ui.box(this, false);
        onceBtn = segment("Jednou", new Runnable() {
            public void run() {
                days = 0;
                render2();
            }
        });
        repeatBtn = segment("Opakovat", new Runnable() {
            public void run() {
                if (days == 0) days = 0x1F; // výchozí Po–Pá
                render2();
            }
        });
        seg.addView(onceBtn, Ui.lp(0, -1, 1f));
        LinearLayout.LayoutParams rl = Ui.lp(0, -1, 1f);
        rl.leftMargin = px(.025f);
        seg.addView(repeatBtn, rl);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, px(.13f));
        sl.topMargin = px(.03f);
        step2.addView(seg, sl);

        daysBox = Ui.box(this, true);
        LinearLayout dayRow = Ui.box(this, false);
        for (int i = 0; i < 7; i++) {
            final int bit = 1 << i;
            TextView d = Ui.text(this, Alarms.DAY_NAMES[i], .042f, Ui.INK);
            d.setTypeface(Ui.medium());
            d.setGravity(Gravity.CENTER);
            d.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    days ^= bit;
                    render2();
                }
            });
            dayViews[i] = d;
            LinearLayout.LayoutParams dl = Ui.lp(0, -1, 1f);
            if (i > 0) dl.leftMargin = px(.017f);
            dayRow.addView(d, dl);
        }
        daysBox.addView(dayRow, new LinearLayout.LayoutParams(-1, px(.115f)));
        LinearLayout quick = Ui.box(this, false);
        quick.addView(chip("Po – Pá", 0x1F), Ui.lp(0, -1, 1f));
        quick.addView(chip("Víkend", 0x60), marginLeft(Ui.lp(0, -1, 1f)));
        quick.addView(chip("Každý den", 0x7F), marginLeft(Ui.lp(0, -1, 1f)));
        LinearLayout.LayoutParams ql = new LinearLayout.LayoutParams(-1, px(.09f));
        ql.topMargin = px(.017f);
        daysBox.addView(quick, ql);
        LinearLayout.LayoutParams dbl = new LinearLayout.LayoutParams(-1, -2);
        dbl.topMargin = px(.025f);
        step2.addView(daysBox, dbl);

        // ranní tisk
        LinearLayout pr = Ui.box(this, false);
        pr.setGravity(Gravity.CENTER_VERTICAL);
        pr.setBackground(Ui.tile(this, Ui.TILE, .04f));
        pr.setPadding(px(.04f), 0, px(.025f), 0);
        pr.addView(Ui.icon(this, R.drawable.ic_printer, Ui.INK, .05f));
        TextView pt = Ui.text(this, "Po vypnutí vytisknout ranní zprávu", .04f, Ui.INK);
        LinearLayout.LayoutParams ptl = Ui.lp(0, -2, 1f);
        ptl.leftMargin = px(.025f);
        pr.addView(pt, ptl);
        printPill = Ui.text(this, "", .036f, Ui.INK);
        printPill.setTypeface(Ui.medium());
        printPill.setGravity(Gravity.CENTER);
        pr.addView(printPill, new LinearLayout.LayoutParams(px(.17f), px(.085f)));
        pr.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                print = !print;
                render2();
            }
        });
        LinearLayout.LayoutParams prl = new LinearLayout.LayoutParams(-1, px(.13f));
        prl.topMargin = px(.025f);
        step2.addView(pr, prl);

        step2.addView(new View(this), Ui.lp(-1, 0, 1f));

        LinearLayout b2 = Ui.box(this, false);
        b2.addView(button("‹  Čas", Ui.TILE, Ui.INK, new Runnable() {
            public void run() {
                showStep(1);
            }
        }), Ui.lp(0, -1, 1f));
        if (id >= 0) {
            LinearLayout.LayoutParams dl = Ui.lp(0, -1, 1f);
            dl.leftMargin = px(.025f);
            b2.addView(button("Smazat", Ui.TILE, Ui.BAD, new Runnable() {
                public void run() {
                    delete();
                }
            }), dl);
        }
        LinearLayout.LayoutParams svl = Ui.lp(0, -1, 1.5f);
        svl.leftMargin = px(.025f);
        b2.addView(button("Uložit", Ui.AMBER, Ui.ON_AMBER, new Runnable() {
            public void run() {
                save();
            }
        }), svl);
        step2.addView(b2, new LinearLayout.LayoutParams(-1, px(.15f)));
        root.addView(step2, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);
        showStep(1);
        updateWhen();
    }

    private LinearLayout.LayoutParams marginLeft(LinearLayout.LayoutParams p) {
        p.leftMargin = px(.017f);
        return p;
    }

    private TextView button(String text, int bg, int fg, final Runnable r) {
        TextView t = Ui.text(this, text, .048f, fg);
        t.setTypeface(Ui.medium());
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.tile(this, bg, .046f));
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                r.run();
            }
        });
        return t;
    }

    private TextView segment(String text, final Runnable r) {
        TextView t = Ui.text(this, text, .046f, Ui.INK);
        t.setTypeface(Ui.medium());
        t.setGravity(Gravity.CENTER);
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                r.run();
            }
        });
        return t;
    }

    private TextView chip(String text, final int mask) {
        TextView t = Ui.text(this, text, .036f, Ui.SOFT);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.tile(this, Ui.TILE, .04f));
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                days = mask;
                render2();
            }
        });
        return t;
    }

    private void showStep(int n) {
        step1.setVisibility(n == 1 ? View.VISIBLE : View.GONE);
        step2.setVisibility(n == 2 ? View.VISIBLE : View.GONE);
        if (n == 2) render2();
    }

    private Alarms.Alarm current() {
        Alarms.Alarm a = new Alarms.Alarm();
        a.hour = hours.getValue();
        a.minute = minutes.getValue();
        a.days = days;
        a.on = true;
        a.print = print;
        return a;
    }

    private void updateWhen() {
        Alarms.Alarm a = current();
        String w = "zazvoní " + whenText(Alarms.nextTrigger(a, System.currentTimeMillis()));
        when1.setText(w);
        when2.setText(a.timeText() + "  ·  " + w.replace("zazvoní ", ""));
    }

    private void render2() {
        boolean repeat = days != 0;
        onceBtn.setBackground(Ui.round(repeat ? Ui.TILE : Ui.AMBER, px(.046f)));
        onceBtn.setTextColor(repeat ? Ui.INK : Ui.ON_AMBER);
        repeatBtn.setBackground(Ui.round(repeat ? Ui.AMBER : Ui.TILE, px(.046f)));
        repeatBtn.setTextColor(repeat ? Ui.ON_AMBER : Ui.INK);
        daysBox.setVisibility(repeat ? View.VISIBLE : View.INVISIBLE);
        for (int i = 0; i < 7; i++) {
            boolean on = (days & (1 << i)) != 0;
            dayViews[i].setBackground(Ui.round(on ? Ui.AMBER : Ui.TILE, px(.035f)));
            dayViews[i].setTextColor(on ? Ui.ON_AMBER : Ui.INK);
        }
        printPill.setText(print ? "ZAP" : "VYP");
        printPill.setTextColor(print ? Ui.ON_AMBER : Ui.DIM);
        printPill.setBackground(Ui.round(print ? Ui.AMBER : Ui.TILE_PRESSED, px(.05f)));
        updateWhen();
    }

    private static String whenText(long t) {
        if (t < 0) return "–";
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        Calendar today = Calendar.getInstance();
        int diff = c.get(Calendar.DAY_OF_YEAR) - today.get(Calendar.DAY_OF_YEAR);
        if (c.get(Calendar.YEAR) != today.get(Calendar.YEAR)) diff = 1;
        String day = diff == 0 ? "dnes" : diff == 1 ? "zítra"
                : new java.text.SimpleDateFormat("EEEE", new Locale("cs", "CZ")).format(c.getTime());
        int m = c.get(Calendar.MINUTE);
        return day + " v " + c.get(Calendar.HOUR_OF_DAY) + ":" + (m < 10 ? "0" : "") + m;
    }

    private void save() {
        List<Alarms.Alarm> all = Alarms.load(this);
        Alarms.Alarm a = id >= 0 ? Alarms.find(all, id) : null;
        if (a == null) {
            a = new Alarms.Alarm();
            a.id = Alarms.newId(this);
            all.add(a);
        }
        Alarms.Alarm c = current();
        a.hour = c.hour;
        a.minute = c.minute;
        a.days = c.days;
        a.print = c.print;
        a.on = true;
        Prefs.get(this).edit().putInt("last_h", a.hour).putInt("last_m", a.minute).putBoolean("last_print", a.print).apply();
        Alarms.save(this, all);
        long t = Alarms.nextTrigger(a, System.currentTimeMillis());
        if (t > 0) {
            long min = (t - System.currentTimeMillis()) / 60000 + 1;
            String s = min >= 60 ? (min / 60) + " h " + (min % 60) + " min" : min + " min";
            Toast.makeText(this, "Budík zazvoní za " + s, Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    private void delete() {
        List<Alarms.Alarm> all = Alarms.load(this);
        Alarms.Alarm a = Alarms.find(all, id);
        if (a != null) all.remove(a);
        Alarms.save(this, all);
        finish();
    }
}
