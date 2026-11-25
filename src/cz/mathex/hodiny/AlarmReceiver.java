package cz.mathex.hodiny;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;

/** Zazvonění budíku + přeplánování po restartu / změně času / aktualizaci aplikace. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (App.CHECK.equals(i.getAction())) {
            Alarms.schedule(c);
            Prefs.get(c).edit().putString("last_check",
                    new java.text.SimpleDateFormat("d. M. HH:mm", java.util.Locale.US).format(new java.util.Date())).apply();
            return;
        }
        if ("cz.mathex.hodiny.SUNRISE".equals(i.getAction())) {
            Yeelight.sunrise(c, Alarms.SUNRISE_MIN * 60);
            return;
        }
        if (!"cz.mathex.hodiny.FIRE".equals(i.getAction())) {
            Alarms.schedule(c);
            return;
        }
        int id = i.getIntExtra("id", 0);
        if (id == Alarms.SNOOZE_ID) {
            id = Alarms.snoozeOf(c);
            Alarms.clearSnooze(c);
        } else {
            Prefs.get(c).edit().putInt("auto_snoozes", 0).apply(); // nový budík, ne odložení
            List<Alarms.Alarm> list = Alarms.load(c);
            Alarms.Alarm a = Alarms.find(list, id);
            if (a != null && a.days == 0) a.on = false; // jednorázový se po zazvonění vypne
            Alarms.save(c, list); // zároveň naplánuje další
        }
        Alarms.schedule(c);
        c.startActivity(new Intent(c, RingActivity.class).putExtra("alarm_id", id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_USER_ACTION));
    }
}
