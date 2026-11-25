package cz.mathex.hodiny;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Seznam budíků: ťuknutí = upravit, přepínač vpravo = zapnout / vypnout, „+ Nový“ nahoře. */
public class AlarmsActivity extends BaseActivity {
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = Ui.box(this, true);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.bar(this, "Budíky", "+  Nový", new View.OnClickListener() {
            public void onClick(View v) {
                edit(null);
            }
        }));
        ScrollView sv = new ScrollView(this);
        list = Ui.box(this, true);
        int pad = Ui.px(this, .04f);
        list.setPadding(pad, 0, pad, pad);
        sv.addView(list);
        root.addView(sv, Ui.lp(-1, 0, 1f));
        setContentView(root);
        Alarms.schedule(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
        render();
    }

    private void render() {
        list.removeAllViews();
        renderAlarms();
        addTestTile();
    }

    /** „Zkusit za 1 min“: projde celou cestou budíku včetně zvuku – pro klid duše. */
    private void addTestTile() {
        LinearLayout t = Ui.box(this, false);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setBackground(Ui.tile(this, Ui.TILE, .04f));
        int p = Ui.px(this, .04f);
        t.setPadding(p, p * 2 / 3, p, p * 2 / 3);
        t.addView(Ui.icon(this, R.drawable.ic_alarm, Ui.TEAL, .05f));
        LinearLayout tx = Ui.box(this, true);
        TextView n = Ui.text(this, "Zkušební budík za 1 minutu", .042f, Ui.INK);
        n.setTypeface(Ui.medium());
        tx.addView(n);
        tx.addView(Ui.text(this, "ověří plánování, zvuk i obrazovku budíku", .032f, Ui.DIM), Ui.top(this, .008f));
        LinearLayout.LayoutParams tl = Ui.lp(0, -2, 1f);
        tl.leftMargin = Ui.px(this, .03f);
        t.addView(tx, tl);
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                long at = Alarms.test(AlarmsActivity.this, 60);
                Toast.makeText(AlarmsActivity.this, "Zkušební budík zazvoní v " + fmt(at) + " – můžeš jít na plochu",
                        Toast.LENGTH_LONG).show();
                render();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.px(this, .045f);
        list.addView(t, lp);
    }

    private void renderAlarms() {
        List<Alarms.Alarm> alarms = Alarms.load(this);
        if (alarms.isEmpty()) {
            TextView t = Ui.text(this, "Zatím žádný budík.\nPřidej ho tlačítkem „+ Nový“ vpravo nahoře.", .05f, Ui.DIM);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.px(this, .15f), 0, 0);
            list.addView(t, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        long sn = Alarms.snoozeAt(this);
        if (sn > System.currentTimeMillis()) {
            TextView t = Ui.text(this, "😴  odloženo do " + fmt(sn) + "  · ťukni pro zrušení", .045f, Ui.AMBER);
            t.setPadding(0, Ui.px(this, .02f), 0, Ui.px(this, .02f));
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Alarms.clearSnooze(AlarmsActivity.this);
                    Alarms.schedule(AlarmsActivity.this);
                    render();
                }
            });
            list.addView(t);
        }
        for (final Alarms.Alarm a : alarms) {
            LinearLayout row = Ui.box(this, false);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(Ui.tile(this));
            int p = Ui.px(this, .04f);
            row.setPadding(p, p / 2, p / 2, p / 2);
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    edit(a);
                }
            });

            LinearLayout txt = Ui.box(this, true);
            TextView time = Ui.text(this, a.timeText(), .13f, a.on ? Ui.INK : Ui.DIM);
            time.setTypeface(Ui.light());
            txt.addView(time);
            txt.addView(Ui.text(this, a.daysText() + (a.print ? "   ·   🖨 ranní tisk" : ""), .045f, Ui.DIM),
                    Ui.top(this, .01f));
            row.addView(txt, Ui.lp(0, -2, 1f));

            TextView sw = Ui.text(this, a.on ? "ZAP" : "VYP", .05f, a.on ? Ui.ON_AMBER : Ui.DIM);
            sw.setTypeface(Ui.medium());
            sw.setGravity(Gravity.CENTER);
            sw.setBackground(Ui.round(a.on ? Ui.AMBER : Ui.TILE_PRESSED, Ui.px(this, .06f)));
            sw.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    List<Alarms.Alarm> all = Alarms.load(AlarmsActivity.this);
                    Alarms.Alarm x = Alarms.find(all, a.id);
                    if (x != null) x.on = !x.on;
                    Alarms.save(AlarmsActivity.this, all);
                    if (x != null && x.on) toastNext(x);
                    render();
                }
            });
            row.addView(sw, new LinearLayout.LayoutParams(Ui.px(this, .22f), Ui.px(this, .12f)));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Ui.px(this, .03f);
            list.addView(row, lp);
        }
    }

    private static String fmt(long t) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(t);
        int m = c.get(java.util.Calendar.MINUTE);
        return c.get(java.util.Calendar.HOUR_OF_DAY) + ":" + (m < 10 ? "0" : "") + m;
    }

    private static String whenText(long t) {
        if (t < 0) return "–";
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(t);
        java.util.Calendar today = java.util.Calendar.getInstance();
        int diff = c.get(java.util.Calendar.DAY_OF_YEAR) - today.get(java.util.Calendar.DAY_OF_YEAR);
        if (c.get(java.util.Calendar.YEAR) != today.get(java.util.Calendar.YEAR)) diff = 1;
        String day = diff == 0 ? "dnes" : diff == 1 ? "zítra"
                : new java.text.SimpleDateFormat("EEEE", new java.util.Locale("cs", "CZ")).format(c.getTime());
        return day + " v " + fmt(t);
    }

    private void toastNext(Alarms.Alarm a) {
        long t = Alarms.nextTrigger(a, System.currentTimeMillis());
        if (t < 0) return;
        long min = (t - System.currentTimeMillis()) / 60000 + 1;
        String s = min >= 60 ? (min / 60) + " h " + (min % 60) + " min" : min + " min";
        Toast.makeText(this, "Budík zazvoní za " + s, Toast.LENGTH_SHORT).show();
    }

    /** Nový (a == null) nebo existující budík – vlastní obrazovka s kolečky. */
    private void edit(final Alarms.Alarm existing) {
        startActivity(new android.content.Intent(this, AlarmEditActivity.class)
                .putExtra("id", existing != null ? existing.id : -1));
    }

    private NumberPicker picker(int max) {
        NumberPicker p = new NumberPicker(this);
        p.setMinValue(0);
        p.setMaxValue(max);
        p.setWrapSelectorWheel(true);
        p.setFormatter(new NumberPicker.Formatter() {
            public String format(int v) {
                return v < 10 ? "0" + v : String.valueOf(v);
            }
        });
        p.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS); // bez klávesnice
        return p;
    }

    private void styleDays(TextView[] views, int days) {
        for (int i = 0; i < 7; i++) {
            boolean on = (days & (1 << i)) != 0;
            views[i].setTextColor(on ? Ui.ON_AMBER : Ui.INK);
            views[i].setBackground(Ui.round(on ? Ui.AMBER : Ui.TILE_PRESSED, Ui.px(this, .03f)));
        }
    }
}
