package cz.mathex.hodiny;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;

/** Zazvonění budíku + přeplánování po restartu / změně času / aktualizaci aplikace. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (!"cz.mathex.hodiny.FIRE".equals(i.getAction())) {
            Alarms.schedule(c);
            return;
        }
        int id = i.getIntExtra("id", 0);
        if (id == Alarms.SNOOZE_ID) {
            Alarms.clearSnooze(c);
        } else {
            List<Alarms.Alarm> list = Alarms.load(c);
            Alarms.Alarm a = Alarms.find(list, id);
            if (a != null && a.days == 0) a.on = false; // jednorázový se po zazvonění vypne
            Alarms.save(c, list); // zároveň naplánuje další
        }
        Alarms.schedule(c);
        c.startActivity(new Intent(c, RingActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_USER_ACTION));
    }
}
