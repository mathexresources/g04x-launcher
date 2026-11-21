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
public class AlarmsActivity extends Activity {
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
            TextView t = Ui.text(this, "😴  odloženo do " + fmt(sn) + "  · ťukni pro zrušení", .045f, Ui.ACCENT);
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
            txt.addView(Ui.text(this, a.daysText(), .045f, Ui.DIM), Ui.top(this, .01f));
            row.addView(txt, Ui.lp(0, -2, 1f));

            TextView sw = Ui.text(this, a.on ? "ZAP" : "VYP", .05f, a.on ? Ui.BG : Ui.DIM);
            sw.setTypeface(Ui.medium());
            sw.setGravity(Gravity.CENTER);
            sw.setBackground(Ui.round(a.on ? Ui.ACCENT : Ui.TILE_PRESSED, Ui.px(this, .06f)));
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

    private void toastNext(Alarms.Alarm a) {
        long t = Alarms.nextTrigger(a, System.currentTimeMillis());
        if (t < 0) return;
        long min = (t - System.currentTimeMillis()) / 60000 + 1;
        String s = min >= 60 ? (min / 60) + " h " + (min % 60) + " min" : min + " min";
        Toast.makeText(this, "Budík zazvoní za " + s, Toast.LENGTH_SHORT).show();
    }

    /** Dialog pro nový (a == null) nebo existující budík. */
    private void edit(final Alarms.Alarm existing) {
        int pad = Ui.px(this, .04f);
        LinearLayout box = Ui.box(this, true);
        box.setPadding(pad, pad / 2, pad, 0);
        box.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout pick = Ui.box(this, false);
        pick.setGravity(Gravity.CENTER);
        final NumberPicker hp = picker(23);
        final NumberPicker mp = picker(59);
        java.util.Calendar now = java.util.Calendar.getInstance();
        hp.setValue(existing != null ? existing.hour : 7);
        mp.setValue(existing != null ? existing.minute : 0);
        pick.addView(hp);
        TextView colon = Ui.text(this, ":", .09f, Ui.INK);
        colon.setPadding(pad / 2, 0, pad / 2, 0);
        pick.addView(colon);
        pick.addView(mp);
        box.addView(pick);

        final int[] days = {existing != null ? existing.days : 0x1F};
        LinearLayout dayRow = Ui.box(this, false);
        dayRow.setGravity(Gravity.CENTER);
        final TextView[] dayViews = new TextView[7];
        for (int i = 0; i < 7; i++) {
            final int bit = 1 << i;
            final TextView d = Ui.text(this, Alarms.DAY_NAMES[i], .045f, Ui.INK);
            d.setGravity(Gravity.CENTER);
            d.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    days[0] ^= bit;
                    styleDays(dayViews, days[0]);
                }
            });
            dayViews[i] = d;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(this, .11f), Ui.px(this, .09f));
            lp.leftMargin = lp.rightMargin = Ui.px(this, .008f);
            dayRow.addView(d, lp);
        }
        styleDays(dayViews, days[0]);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, -2);
        dl.topMargin = pad / 2;
        box.addView(dayRow, dl);
        TextView hint = Ui.text(this, "bez vybraných dnů = zazvoní jednou", .032f, Ui.DIM);
        box.addView(hint, Ui.top(this, .015f));

        ScrollView sv = new ScrollView(this);
        sv.addView(box);

        AlertDialog.Builder bd = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setView(sv)
                .setNegativeButton("Zrušit", null)
                .setPositiveButton("Uložit", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface di, int w) {
                        List<Alarms.Alarm> all = Alarms.load(AlarmsActivity.this);
                        Alarms.Alarm a = existing != null ? Alarms.find(all, existing.id) : null;
                        if (a == null) {
                            a = new Alarms.Alarm();
                            a.id = Alarms.newId(AlarmsActivity.this);
                            all.add(a);
                        }
                        a.hour = hp.getValue();
                        a.minute = mp.getValue();
                        a.days = days[0];
                        a.on = true;
                        Alarms.save(AlarmsActivity.this, all);
                        toastNext(a);
                        render();
                    }
                });
        if (existing != null) {
            bd.setNeutralButton("Smazat", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface di, int w) {
                    List<Alarms.Alarm> all = Alarms.load(AlarmsActivity.this);
                    Alarms.Alarm a = Alarms.find(all, existing.id);
                    if (a != null) all.remove(a);
                    Alarms.save(AlarmsActivity.this, all);
                    render();
                }
            });
        }
        AlertDialog dlg = bd.create();
        dlg.show();
        Ui.immersive(this);
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
            views[i].setTextColor(on ? Ui.BG : Ui.INK);
            views[i].setBackground(Ui.round(on ? Ui.ACCENT : Ui.TILE_PRESSED, Ui.px(this, .03f)));
        }
    }
}
