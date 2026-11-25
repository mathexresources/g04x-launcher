package cz.mathex.hodiny;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;

/**
 * Obrazovka zvonícího budíku – jen ovládání. Zvuk hraje RingService (i kdyby se tahle obrazovka
 * neotevřela). Libovolné tlačítko na hodinách = odložit.
 */
public class RingActivity extends Activity {
    private int alarmId;
    private boolean sent;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        alarmId = getIntent().getIntExtra("alarm_id", RingService.alarmId);
        RingService.ui = new java.lang.ref.WeakReference<Activity>(this);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 1f;
        getWindow().setAttributes(lp);

        Ui.loadFonts(this);
        LinearLayout root = Ui.box(this, true);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.px(this, .067f), Ui.px(this, .0625f), Ui.px(this, .067f), Ui.px(this, .058f));

        LinearLayout center = Ui.box(this, true);
        center.setGravity(Gravity.CENTER);
        root.addView(center, Ui.lp(-1, 0, 1f));

        LinearLayout chip = Ui.box(this, false);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackground(Ui.round(Ui.AMBER_BG, Ui.px(this, .5f)));
        chip.setPadding(Ui.px(this, .033f), Ui.px(this, .015f), Ui.px(this, .033f), Ui.px(this, .015f));
        chip.addView(Ui.icon(this, R.drawable.ic_alarm, Ui.AMBER, .042f));
        TextView cl = Ui.text(this, "Budík", .035f, Ui.AMBER);
        cl.setTypeface(Ui.medium());
        chip.addView(cl, Ui.left(this, .02f));
        center.addView(chip, new LinearLayout.LayoutParams(-2, -2));

        Calendar c = Calendar.getInstance();
        TextView time = Ui.text(this, c.get(Calendar.HOUR_OF_DAY) + ":"
                + (c.get(Calendar.MINUTE) < 10 ? "0" : "") + c.get(Calendar.MINUTE), .41f, Ui.INK);
        time.setTypeface(Ui.display());
        time.setLetterSpacing(-0.04f);
        center.addView(time, Ui.top(this, .025f));

        Alarms.Alarm me = Alarms.find(Alarms.load(this), alarmId);
        if (me != null && me.print) {
            LinearLayout pr = Ui.box(this, false);
            pr.setGravity(Gravity.CENTER_VERTICAL);
            pr.addView(Ui.icon(this, R.drawable.ic_printer, Ui.DIM, .0375f));
            pr.addView(Ui.text(this, "po vypnutí se vytiskne ranní zpráva", .037f, Ui.DIM), Ui.left(this, .017f));
            center.addView(pr, Ui.top(this, .025f));
        }

        LinearLayout buttons = Ui.box(this, false);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, Ui.px(this, .246f));
        bl.topMargin = Ui.px(this, .046f);
        root.addView(buttons, bl);

        LinearLayout snoozeB = Ui.box(this, true);
        snoozeB.setGravity(Gravity.CENTER);
        snoozeB.setBackground(Ui.tile(this, Ui.TILE, .058f));
        TextView st = Ui.text(this, "Odložit", .054f, Ui.INK);
        st.setTypeface(Ui.medium());
        st.setGravity(Gravity.CENTER);
        snoozeB.addView(st, new LinearLayout.LayoutParams(-2, -2));
        snoozeB.addView(Ui.text(this, "o " + Alarms.SNOOZE_MIN + " minut", .031f, Ui.DIM), Ui.top(this, .01f));
        snoozeB.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                snooze();
            }
        });
        buttons.addView(snoozeB, Ui.lp(0, -1, 1f));

        TextView stop = Ui.text(this, "Vypnout", .0625f, Ui.ON_AMBER);
        stop.setTypeface(Ui.medium());
        stop.setGravity(Gravity.CENTER);
        stop.setBackground(Ui.tile(this, Ui.AMBER, .058f));
        stop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dismiss();
            }
        });
        LinearLayout.LayoutParams sl = Ui.lp(0, -1, 1.4f);
        sl.leftMargin = Ui.px(this, .033f);
        buttons.addView(stop, sl);

        setContentView(root);
    }

    private final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
        // služba se spouští asynchronně → chvíli počkat, než ověříme, že budík pořád zvoní
        h.postDelayed(new Runnable() {
            public void run() {
                if (!RingService.ringing && !isFinishing()) finish();
            }
        }, 3000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacksAndMessages(null);
    }

    private void snooze() {
        if (sent) return;
        sent = true;
        RingService.send(this, RingService.SNOOZE);
        finish();
    }

    private void dismiss() {
        if (sent) return;
        sent = true;
        RingService.send(this, RingService.DISMISS);
        finish();
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        snooze(); // hlasitost / mute na hodinách = odložit
        return true;
    }

    @Override
    public void onBackPressed() {
        snooze();
    }
}
