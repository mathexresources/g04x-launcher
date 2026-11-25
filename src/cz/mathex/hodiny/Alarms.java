package cz.mathex.hodiny;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Vlastní budíky (nezávislé na systémové aplikaci Hodiny).
 * Ukládají se jako JSON v SharedPreferences, naplánovaný je vždy jen ten nejbližší přes setAlarmClock.
 */
final class Alarms {
    static final String[] DAY_NAMES = {"Po", "Út", "St", "Čt", "Pá", "So", "Ne"};
    static final int SNOOZE_MIN = 9;
    static final int SNOOZE_ID = -1;
    static final int SUNRISE_MIN = 10;

    static final class Alarm {
        int id, hour, minute, days; // days: bit 0 = pondělí … bit 6 = neděle, 0 = jednorázový
        boolean on = true;
        boolean print; // po vypnutí vytisknout ranní zprávu

        String daysText() {
            if (days == 0) return "jednou";
            if (days == 0x7F) return "každý den";
            if (days == 0x1F) return "Po – Pá";
            if (days == 0x60) return "víkend";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 7; i++) {
                if ((days & (1 << i)) != 0) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(DAY_NAMES[i]);
                }
            }
            return sb.toString();
        }

        String timeText() {
            return hour + ":" + (minute < 10 ? "0" : "") + minute;
        }
    }

    private Alarms() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("alarms", Context.MODE_PRIVATE);
    }

    static List<Alarm> load(Context c) {
        List<Alarm> out = new ArrayList<Alarm>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString("list", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Alarm a = new Alarm();
                a.id = o.getInt("id");
                a.hour = o.getInt("h");
                a.minute = o.getInt("m");
                a.days = o.optInt("d", 0);
                a.on = o.optBoolean("on", true);
                a.print = o.optBoolean("p", false);
                out.add(a);
            }
        } catch (Exception ignored) {
        }
        java.util.Collections.sort(out, new java.util.Comparator<Alarm>() {
            public int compare(Alarm x, Alarm y) {
                return (x.hour * 60 + x.minute) - (y.hour * 60 + y.minute);
            }
        });
        return out;
    }

    static void save(Context c, List<Alarm> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Alarm a : list) {
                arr.put(new JSONObject().put("id", a.id).put("h", a.hour).put("m", a.minute).put("d", a.days).put("on", a.on).put("p", a.print));
            }
        } catch (Exception ignored) {
        }
        sp(c).edit().putString("list", arr.toString()).apply();
        schedule(c);
    }

    static int newId(Context c) {
        int id = sp(c).getInt("seq", 0) + 1;
        sp(c).edit().putInt("seq", id).apply();
        return id;
    }

    static Alarm find(List<Alarm> list, int id) {
        for (Alarm a : list) if (a.id == id) return a;
        return null;
    }

    /** Nejbližší čas zazvonění pro budík (ms), nebo -1. */
    static long nextTrigger(Alarm a, long now) {
        if (!a.on) return -1;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, a.hour);
        c.set(Calendar.MINUTE, a.minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        for (int d = 0; d <= 7; d++) {
            if (c.getTimeInMillis() > now) {
                int idx = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7; // pondělí = 0
                if (a.days == 0 || (a.days & (1 << idx)) != 0) return c.getTimeInMillis();
            }
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return -1;
    }

    static long snoozeAt(Context c) {
        return sp(c).getLong("snooze", 0);
    }

    /** Id budíku, který je právě odložený (aby se po odložení vědělo, jestli tisknout). */
    static int snoozeOf(Context c) {
        return sp(c).getInt("snooze_of", 0);
    }

    static void snooze(Context c, int alarmId) {
        sp(c).edit().putLong("snooze", System.currentTimeMillis() + SNOOZE_MIN * 60000L)
                .putInt("snooze_of", alarmId).apply();
        schedule(c);
    }

    static void clearSnooze(Context c) {
        sp(c).edit().remove("snooze").apply();
    }

    /** Čas, na který by měl být naplánovaný nejbližší budík (nebo odložení); -1 = žádný. */
    static long expectedNext(Context c) {
        long now = System.currentTimeMillis(), best = -1;
        for (Alarm a : load(c)) {
            long t = nextTrigger(a, now);
            if (t > 0 && (best < 0 || t < best)) best = t;
        }
        long sn = snoozeAt(c);
        if (sn > now && (best < 0 || sn < best)) best = sn;
        return best;
    }

    /** Zkušební budík: projde celou cestou (plánování, služba, zvuk, obrazovka) za `seconds` s. */
    static long test(Context c, int seconds) {
        long at = System.currentTimeMillis() + seconds * 1000L;
        sp(c).edit().putLong("snooze", at).putInt("snooze_of", 0).apply();
        schedule(c);
        return at;
    }

    /** Naplánuje nejbližší budík (nebo odložení). Volat po každé změně, po startu a po změně času. */
    static void schedule(Context c) {
        long now = System.currentTimeMillis();
        long best = -1;
        int bestId = 0;
        for (Alarm a : load(c)) {
            long t = nextTrigger(a, now);
            if (t > 0 && (best < 0 || t < best)) {
                best = t;
                bestId = a.id;
            }
        }
        long sn = snoozeAt(c);
        if (sn > now && (best < 0 || sn < best)) {
            best = sn;
            bestId = SNOOZE_ID;
        }
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        Intent fire = new Intent(c, AlarmReceiver.class).setAction("cz.mathex.hodiny.FIRE");
        // svítání: 10 min před skutečným budíkem (ne před odložením) pomalu rozsvítit žárovku
        Intent sun = new Intent(c, AlarmReceiver.class).setAction("cz.mathex.hodiny.SUNRISE");
        PendingIntent sunPi = PendingIntent.getBroadcast(c, 2, sun, PendingIntent.FLAG_UPDATE_CURRENT);
        long sunAt = best - SUNRISE_MIN * 60000L;
        if (best > 0 && bestId != SNOOZE_ID && sunAt > now && Yeelight.configured(c)
                && Prefs.get(c).getBoolean("bulb_sunrise", true)) {
            am.setExact(AlarmManager.RTC_WAKEUP, sunAt, sunPi);
        } else {
            am.cancel(sunPi);
        }
        if (best < 0) {
            am.cancel(PendingIntent.getBroadcast(c, 0, fire, PendingIntent.FLAG_UPDATE_CURRENT));
            return;
        }
        fire.putExtra("id", bestId);
        PendingIntent op = PendingIntent.getBroadcast(c, 0, fire, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent show = PendingIntent.getActivity(c, 1, new Intent(c, AlarmsActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(best, show), op);
    }
}
