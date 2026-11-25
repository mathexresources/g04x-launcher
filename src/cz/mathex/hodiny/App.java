package cz.mathex.hodiny;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Watchdog: při pádu aplikace zapíše chybu a za 2 s launcher znovu spustí;
 * každých 30 min (a po startu) zkontroluje, že je naplánovaný nejbližší budík.
 */
public class App extends Application {
    static final String CHECK = "cz.mathex.hodiny.CHECK";

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    String trace = sw.toString();
                    if (trace.length() > 1500) trace = trace.substring(0, 1500);
                    String when = new SimpleDateFormat("d. M. HH:mm", Locale.US).format(new Date());
                    Prefs.get(App.this).edit()
                            .putString("last_crash", when + " – " + trace)
                            .putInt("crash_count", Prefs.get(App.this).getInt("crash_count", 0) + 1)
                            .commit();
                    // za 2 s znovu otevřít plochu
                    Intent i = new Intent(App.this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    PendingIntent pi = PendingIntent.getActivity(App.this, 9, i, PendingIntent.FLAG_CANCEL_CURRENT);
                    ((AlarmManager) getSystemService(Context.ALARM_SERVICE)).set(AlarmManager.RTC, System.currentTimeMillis() + 2000, pi);
                } catch (Throwable ignored) {
                }
                if (previous != null) previous.uncaughtException(t, e);
                else System.exit(2);
            }
        });
        scheduleChecks(this);
        Alarms.schedule(this);
    }

    /** Pravidelná kontrola budíků (přežije i to, že by se plán někde ztratil). */
    static void scheduleChecks(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(c, 8, new Intent(c, AlarmReceiver.class).setAction(CHECK),
                PendingIntent.FLAG_UPDATE_CURRENT);
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 60000,
                AlarmManager.INTERVAL_HALF_HOUR, pi);
    }

    /** Text pro nastavení: stav watchdogu. */
    static String status(Context c) {
        int n = Prefs.get(c).getInt("crash_count", 0);
        String last = Prefs.get(c).getString("last_crash", null);
        String check = Prefs.get(c).getString("last_check", "zatím ne");
        if (n == 0) return "watchdog: bez pádů · poslední kontrola budíku " + check;
        String first = last != null ? last.split("\n")[0] : "";
        return "watchdog: pádů " + n + " · poslední " + first + "\nposlední kontrola budíku " + check;
    }
}
