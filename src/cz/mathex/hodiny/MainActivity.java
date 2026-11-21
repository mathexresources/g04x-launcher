package cz.mathex.hodiny;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Domovská obrazovka: hodiny, budík, počasí, hudba, dlaždice. V noci se po minutě nečinnosti ztlumí. */
public class MainActivity extends Activity {
    private static final Locale CS = new Locale("cs", "CZ");
    private static final long WEATHER_EVERY = 15 * 60 * 1000L;
    private static final long STANDBY_IDLE = 15 * 1000L;
    private static final String PREV = "\u23EE\uFE0E", PLAY = "\u25B6\uFE0E", PAUSE = "\u23F8\uFE0E", NEXT = "\u23ED\uFE0E";
    private static final String SPOTIFY = "com.spotify.music";

    private final Handler h = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE d. MMMM", CS);

    private TextView clock, date, alarm, wIcon, wTemp, wDesc, wRange, wHours;
    private TextView mTitle, mArtist, bPrev, bPlay, bNext;
    private FrameLayout standby;
    private LinearLayout sbMedia;
    private TextView sbClock, sbAlarm, sbTitle, sbPrev, sbPlay, sbNext;
    private boolean standbyOn;

    private MediaSessionManager msm;
    private MediaController mc;
    private ComponentName listener;
    private boolean needMediaPermission;

    private long lastTouch = System.currentTimeMillis();
    private boolean swallowTouch;

    // ------------------------------------------------------------------ layout
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setBackgroundColor(Ui.BG);
        FrameLayout root = new FrameLayout(this);

        LinearLayout main = Ui.box(this, true);
        main.setPadding(px(.05f), px(.04f), px(.05f), px(.04f));
        root.addView(main, new FrameLayout.LayoutParams(-1, -1));

        // horní část: hodiny vlevo, počasí vpravo
        LinearLayout topRow = Ui.box(this, false);
        main.addView(topRow, Ui.lp(-1, 0, 1f));

        LinearLayout left = Ui.box(this, true);
        left.setGravity(Gravity.CENTER_VERTICAL);
        topRow.addView(left, Ui.lp(0, -1, 1.45f));

        clock = Ui.text(this, "--:--", .26f, Ui.INK);
        clock.setTypeface(Ui.light());
        clock.setLetterSpacing(-0.02f);
        clock.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setStandby(true);
            }
        });
        left.addView(clock);

        date = Ui.text(this, "", .058f, Ui.INK);
        left.addView(date, Ui.top(this, .012f));

        alarm = Ui.text(this, "", .046f, Ui.ACCENT);
        alarm.setSingleLine(true);
        alarm.setEllipsize(TextUtils.TruncateAt.END);
        alarm.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showAlarms();
            }
        });
        left.addView(alarm, Ui.top(this, .02f));

        LinearLayout right = Ui.box(this, true);
        right.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        right.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                wDesc.setText("aktualizuji…");
                loadWeather();
            }
        });
        right.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                openSettings();
                return true;
            }
        });
        topRow.addView(right, Ui.lp(0, -1, 1f));

        LinearLayout wRow = Ui.box(this, false);
        wRow.setGravity(Gravity.CENTER_VERTICAL);
        wIcon = Ui.text(this, "", .09f, Ui.INK);
        wTemp = Ui.text(this, "--°", .13f, Ui.INK);
        wTemp.setTypeface(Ui.light());
        wRow.addView(wIcon);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.leftMargin = px(.025f);
        wRow.addView(wTemp, tp);
        right.addView(wRow);

        wDesc = rightText("načítám počasí…", .045f, Ui.INK);
        right.addView(wDesc, Ui.top(this, .015f));
        wRange = rightText("", .04f, Ui.DIM);
        right.addView(wRange, Ui.top(this, .012f));
        wHours = rightText("", .036f, Ui.DIM);
        right.addView(wHours, Ui.top(this, .02f));

        // karta hudby
        LinearLayout media = Ui.box(this, false);
        media.setGravity(Gravity.CENTER_VERTICAL);
        media.setBackground(Ui.tile(this));
        media.setPadding(px(.045f), 0, px(.02f), 0);
        media.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openPlayer();
            }
        });
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, px(.14f));
        mp.topMargin = px(.025f);
        main.addView(media, mp);

        LinearLayout mt = Ui.box(this, true);
        mTitle = Ui.text(this, "Nic nehraje", .045f, Ui.INK);
        mTitle.setTypeface(Ui.medium());
        mTitle.setSingleLine(true);
        mTitle.setEllipsize(TextUtils.TruncateAt.END);
        mArtist = Ui.text(this, "ťukni pro Spotify", .036f, Ui.DIM);
        mArtist.setSingleLine(true);
        mArtist.setEllipsize(TextUtils.TruncateAt.END);
        mt.addView(mTitle);
        mt.addView(mArtist, Ui.top(this, .01f));
        media.addView(mt, Ui.lp(0, -2, 1f));

        bPrev = control(PREV, .065f);
        bPlay = control(PLAY, .065f);
        bNext = control(NEXT, .065f);
        media.addView(bPrev, new LinearLayout.LayoutParams(px(.15f), -1));
        media.addView(bPlay, new LinearLayout.LayoutParams(px(.15f), -1));
        media.addView(bNext, new LinearLayout.LayoutParams(px(.15f), -1));

        // dlaždice – jen ikony
        LinearLayout tiles = Ui.box(this, false);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, px(.15f));
        tl.topMargin = px(.025f);
        main.addView(tiles, tl);
        addTile(tiles, "⏰", new Runnable() {
            public void run() {
                showAlarms();
            }
        });
        addTile(tiles, "🎵", new Runnable() {
            public void run() {
                Ui.launch(MainActivity.this, SPOTIFY, "Spotify");
            }
        });
        addTile(tiles, "🖨", new Runnable() {
            public void run() {
                startActivity(new Intent(MainActivity.this, PrinterActivity.class));
            }
        });
        addTile(tiles, "⋯", new Runnable() {
            public void run() {
                startActivity(new Intent(MainActivity.this, AppsActivity.class));
            }
        });

        // standby přes celou obrazovku: čas, budík, dole ovládání hudby
        standby = new FrameLayout(this);
        standby.setBackgroundColor(0xFF000000);
        LinearLayout sbCenter = Ui.box(this, true);
        sbCenter.setGravity(Gravity.CENTER);
        sbClock = Ui.text(this, "", .40f, Ui.INK);
        sbClock.setTypeface(Ui.light());
        sbClock.setLetterSpacing(-0.02f);
        sbCenter.addView(sbClock);
        sbAlarm = Ui.text(this, "", .055f, Ui.ACCENT);
        sbCenter.addView(sbAlarm, Ui.top(this, .02f));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1, -1);
        cp.bottomMargin = px(.14f);
        standby.addView(sbCenter, cp);

        sbMedia = Ui.box(this, false);
        sbMedia.setGravity(Gravity.CENTER_VERTICAL);
        sbMedia.setPadding(px(.06f), 0, px(.04f), 0);
        sbTitle = Ui.text(this, "", .042f, Ui.DIM);
        sbTitle.setSingleLine(true);
        sbTitle.setEllipsize(TextUtils.TruncateAt.END);
        sbMedia.addView(sbTitle, Ui.lp(0, -2, 1f));
        sbPrev = control(PREV, .06f);
        sbPlay = control(PLAY, .06f);
        sbNext = control(NEXT, .06f);
        sbMedia.addView(sbPrev, new LinearLayout.LayoutParams(px(.15f), -1));
        sbMedia.addView(sbPlay, new LinearLayout.LayoutParams(px(.15f), -1));
        sbMedia.addView(sbNext, new LinearLayout.LayoutParams(px(.15f), -1));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, px(.16f), Gravity.BOTTOM);
        bp.bottomMargin = px(.03f);
        standby.addView(sbMedia, bp);
        sbMedia.setVisibility(View.GONE);
        standby.setVisibility(View.GONE);
        root.addView(standby, new FrameLayout.LayoutParams(-1, -1));

        View.OnClickListener prev = new View.OnClickListener() {
            public void onClick(View v) {
                if (mc != null) mc.getTransportControls().skipToPrevious();
            }
        };
        View.OnClickListener play = new View.OnClickListener() {
            public void onClick(View v) {
                if (mc == null) return;
                if (isPlaying(mc)) mc.getTransportControls().pause();
                else mc.getTransportControls().play();
            }
        };
        View.OnClickListener next = new View.OnClickListener() {
            public void onClick(View v) {
                if (mc != null) mc.getTransportControls().skipToNext();
            }
        };
        bPrev.setOnClickListener(prev);
        sbPrev.setOnClickListener(prev);
        bPlay.setOnClickListener(play);
        sbPlay.setOnClickListener(play);
        bNext.setOnClickListener(next);
        sbNext.setOnClickListener(next);

        setContentView(root);

        msm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        listener = new ComponentName(this, MediaListener.class);
        showCachedWeather();
        Alarms.schedule(this);
    }

    private int px(float f) {
        return Ui.px(this, f);
    }

    private TextView rightText(String s, float size, int color) {
        TextView t = Ui.text(this, s, size, color);
        t.setGravity(Gravity.END);
        return t;
    }

    private TextView control(String symbol, float size) {
        TextView t = Ui.text(this, symbol, size, Ui.INK);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.press(this));
        return t;
    }

    private void addTile(LinearLayout row, String icon, final Runnable action) {
        LinearLayout t = Ui.box(this, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.tile(this));
        t.addView(Ui.text(this, icon, .085f, Ui.INK));
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                action.run();
            }
        });
        LinearLayout.LayoutParams p = Ui.lp(0, -1, 1f);
        if (row.getChildCount() > 0) p.leftMargin = px(.03f);
        row.addView(t, p);
    }

    // ------------------------------------------------------------------ životní cyklus
    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(alarmChanged, new IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED));
        try {
            msm.addOnActiveSessionsChangedListener(sessions, listener, h);
        } catch (SecurityException ignored) {
            // oprávnění zatím není – bindMedia ukáže výzvu
        }
        bindMedia();
        updateAlarm();
        h.post(tick);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
        lastTouch = System.currentTimeMillis();
        if (System.currentTimeMillis() - Prefs.get(this).getLong("weather_ts", 0) > WEATHER_EVERY) loadWeather();
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) {
            Ui.immersive(this);
            lastTouch = System.currentTimeMillis();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        h.removeCallbacks(tick);
        try {
            unregisterReceiver(alarmChanged);
        } catch (IllegalArgumentException ignored) {
        }
        msm.removeOnActiveSessionsChangedListener(sessions);
        setController(null);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setStandby(false); // tlačítko Domů
    }

    @Override
    public void onBackPressed() {
        setStandby(false); // launcher se zpět nezavírá
    }

    private static boolean inside(View v, MotionEvent e) {
        if (v.getVisibility() != View.VISIBLE) return false;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        float x = e.getRawX(), y = e.getRawY();
        return x >= loc[0] && x < loc[0] + v.getWidth() && y >= loc[1] && y < loc[1] + v.getHeight();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        lastTouch = System.currentTimeMillis();
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN && standbyOn) {
            if (!inside(sbMedia, e)) {
                setStandby(false);
                swallowTouch = true; // první dotek jen probudí, nic nespustí
            }
            // dotek na ovládání hudby projde a standby zůstane
        }
        if (swallowTouch) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                swallowTouch = false;
            }
            return true;
        }
        return super.dispatchTouchEvent(e);
    }

    // ------------------------------------------------------------------ hodiny a standby
    private final Runnable tick = new Runnable() {
        public void run() {
            long now = System.currentTimeMillis();
            Calendar c = Calendar.getInstance();
            String t = c.get(Calendar.HOUR_OF_DAY) + ":" + two(c.get(Calendar.MINUTE));
            if (!t.contentEquals(clock.getText())) {
                clock.setText(t);
                sbClock.setText(t);
                date.setText(dateFmt.format(c.getTime()));
                updateAlarm(); // odpočet do budíku
                if (standbyOn) styleStandby();
            }
            if (now - Prefs.get(MainActivity.this).getLong("weather_ts", 0) > WEATHER_EVERY) loadWeather();
            if (!standbyOn && hasWindowFocus() && now - lastTouch > STANDBY_IDLE) setStandby(true);
            h.postDelayed(this, 1000);
        }
    };

    private static String two(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    private static boolean isNightHours(Calendar c) {
        int hr = c.get(Calendar.HOUR_OF_DAY);
        return hr >= 22 || hr < 7;
    }

    private void setStandby(boolean on) {
        standbyOn = on;
        standby.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) {
            styleStandby();
        } else {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            getWindow().setAttributes(lp);
            lastTouch = System.currentTimeMillis();
        }
    }

    /** V noci (22–7) červeně a skoro zhasnuto, přes den normálně. */
    private void styleStandby() {
        boolean dark = isNightHours(Calendar.getInstance());
        int main = dark ? Ui.NIGHT : Ui.INK;
        int sub = dark ? Ui.NIGHT : Ui.ACCENT;
        int dim = dark ? Ui.NIGHT : Ui.DIM;
        sbClock.setTextColor(main);
        sbAlarm.setTextColor(sub);
        sbTitle.setTextColor(dim);
        sbPrev.setTextColor(dark ? Ui.NIGHT : Ui.INK);
        sbPlay.setTextColor(dark ? Ui.NIGHT : Ui.INK);
        sbNext.setTextColor(dark ? Ui.NIGHT : Ui.INK);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = dark ? 0.01f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(lp);
    }

    // ------------------------------------------------------------------ budík
    private final BroadcastReceiver alarmChanged = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            updateAlarm();
        }
    };

    private static String countdown(long ms) {
        long min = (ms + 59999) / 60000;
        if (min >= 48 * 60) return "za " + (min / (24 * 60)) + " dny";
        if (min >= 60) return "za " + (min / 60) + " h" + (min % 60 > 0 ? " " + (min % 60) + " min" : "");
        return "za " + min + " min";
    }

    private void updateAlarm() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        AlarmManager.AlarmClockInfo ai = am.getNextAlarmClock();
        if (ai == null) {
            alarm.setText("⏰  žádný budík");
            alarm.setTextColor(Ui.DIM);
            sbAlarm.setVisibility(View.GONE);
            return;
        }
        Calendar a = Calendar.getInstance();
        a.setTimeInMillis(ai.getTriggerTime());
        int diff = dayDiff(Calendar.getInstance(), a);
        String day;
        if (diff == 0) day = "dnes";
        else if (diff == 1) day = "zítra";
        else if (diff < 7) day = new SimpleDateFormat("EEEE", CS).format(a.getTime());
        else day = new SimpleDateFormat("d. M.", CS).format(a.getTime());
        String time = a.get(Calendar.HOUR_OF_DAY) + ":" + two(a.get(Calendar.MINUTE));
        String left = countdown(ai.getTriggerTime() - System.currentTimeMillis());
        alarm.setText("⏰ " + day + " " + time + " · " + left);
        alarm.setTextColor(Ui.ACCENT);
        sbAlarm.setText("⏰  " + time + "  ·  " + left);
        sbAlarm.setVisibility(View.VISIBLE);
    }

    private static int dayDiff(Calendar from, Calendar to) {
        Calendar f = (Calendar) from.clone();
        Calendar t = (Calendar) to.clone();
        for (Calendar c : new Calendar[]{f, t}) {
            c.set(Calendar.HOUR_OF_DAY, 12);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
        }
        return (int) Math.round((t.getTimeInMillis() - f.getTimeInMillis()) / 86400000.0);
    }

    private void showAlarms() {
        startActivity(new Intent(this, AlarmsActivity.class));
    }

    // ------------------------------------------------------------------ počasí (wttr.in)
    private boolean weatherLoading;

    private void loadWeather() {
        if (weatherLoading) return;
        weatherLoading = true;
        final String q = Prefs.weatherQuery(this);
        new Thread(new Runnable() {
            public void run() {
                String js = null;
                try {
                    js = Prefs.http("https://wttr.in/" + java.net.URLEncoder.encode(q, "UTF-8").replace("+", "%20")
                            + "?format=j1&lang=cs");
                    new JSONObject(js).getJSONArray("current_condition"); // validace
                } catch (Exception e) {
                    js = null;
                }
                final String result = js;
                runOnUiThread(new Runnable() {
                    public void run() {
                        weatherLoading = false;
                        long now = System.currentTimeMillis();
                        if (result != null) {
                            Prefs.get(MainActivity.this).edit().putString("weather_wttr", result).putLong("weather_ts", now).apply();
                            showWeather(result);
                        } else {
                            // zkusit znovu za 2 minuty
                            Prefs.get(MainActivity.this).edit().putLong("weather_ts", now - WEATHER_EVERY + 120000).apply();
                            String cached = Prefs.get(MainActivity.this).getString("weather_wttr", null);
                            if (cached == null) wDesc.setText("počasí nedostupné");
                            else showWeather(cached);
                        }
                    }
                });
            }
        }).start();
    }

    private void showCachedWeather() {
        Prefs.get(this).edit().remove("weather").apply(); // stará data z Open-Meteo (verze 1.0)
        String js = Prefs.get(this).getString("weather_wttr", null);
        if (js != null) showWeather(js);
        else Prefs.get(this).edit().putLong("weather_ts", 0).apply();
    }

    private static String val(JSONObject o, String key) {
        JSONArray a = o.optJSONArray(key);
        if (a == null || a.length() == 0) return null;
        JSONObject v = a.optJSONObject(0);
        return v != null ? v.optString("value", null) : null;
    }

    private static long num(JSONObject o, String key) {
        try {
            return Math.round(Double.parseDouble(o.getString(key)));
        } catch (Exception e) {
            return 0;
        }
    }

    private void showWeather(String js) {
        try {
            JSONObject o = new JSONObject(js);
            JSONObject cur = o.getJSONArray("current_condition").getJSONObject(0);
            int code = (int) num(cur, "weatherCode");
            Calendar now = Calendar.getInstance();
            int hr = now.get(Calendar.HOUR_OF_DAY);
            boolean day = hr >= 7 && hr < 19;
            wIcon.setText(icon(code, day));
            wTemp.setText(num(cur, "temp_C") + "°");
            String desc = val(cur, "lang_cs");
            if (desc == null || desc.trim().length() == 0) desc = describe(code);
            wDesc.setText(Prefs.weatherName(this) + " · " + desc.trim().toLowerCase(new Locale("cs", "CZ")));

            JSONArray days = o.getJSONArray("weather");
            JSONObject today = days.getJSONObject(0);
            JSONArray hourly = today.getJSONArray("hourly");
            int rain = 0;
            for (int i = 0; i < hourly.length(); i++) {
                JSONObject h = hourly.getJSONObject(i);
                if (Integer.parseInt(h.getString("time")) / 100 + 3 > hr) rain = Math.max(rain, (int) num(h, "chanceofrain"));
            }
            wRange.setText("↑" + num(today, "maxtempC") + "°  ↓" + num(today, "mintempC") + "°"
                    + (rain >= 20 ? "   ☂ " + rain + " %" : ""));

            // další 4 tříhodinové úseky (wttr má hodnoty po 3 h), i přes půlnoc
            StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (int d = 0; d < days.length() && shown < 4; d++) {
                JSONArray hs = days.getJSONObject(d).getJSONArray("hourly");
                for (int i = 0; i < hs.length() && shown < 4; i++) {
                    JSONObject h = hs.getJSONObject(i);
                    int at = Integer.parseInt(h.getString("time")) / 100;
                    if (d == 0 && at <= hr) continue;
                    if (sb.length() > 0) sb.append("   ");
                    sb.append(at).append("h ").append(num(h, "tempC")).append("°");
                    shown++;
                }
            }
            wHours.setText(sb.toString());
        } catch (Exception e) {
            wDesc.setText("počasí: chyba dat, ťukni pro obnovení");
            Prefs.get(this).edit().remove("weather_wttr").putLong("weather_ts", 0).apply();
        }
    }

    /** Kódy počasí World Weather Online (používá je wttr.in). */
    private static String icon(int c, boolean day) {
        switch (c) {
            case 113: return day ? "☀️" : "🌙";
            case 116: return day ? "⛅" : "☁️";
            case 119: case 122: return "☁️";
            case 143: case 248: case 260: return "🌫";
            case 200: case 386: case 389: case 392: case 395: return "⛈";
            case 227: case 230: case 323: case 326: case 329: case 332: case 335: case 338: case 368: case 371:
                return "🌨";
            case 176: case 263: case 266: case 293: case 296: case 353: return "🌦";
            default: return "🌧";
        }
    }

    private static String describe(int c) {
        switch (c) {
            case 113: return "jasno";
            case 116: return "polojasno";
            case 119: return "oblačno";
            case 122: return "zataženo";
            case 143: case 248: case 260: return "mlha";
            case 176: case 263: case 266: case 293: case 296: case 353: return "slabý déšť";
            case 299: case 302: case 356: return "déšť";
            case 305: case 308: case 359: return "silný déšť";
            case 323: case 326: case 368: return "slabé sněžení";
            case 329: case 332: case 335: case 338: case 371: case 227: case 230: return "sněžení";
            case 200: case 386: case 389: case 392: case 395: return "bouřka";
            default: return "srážky";
        }
    }

    private void openSettings() {
        Prefs.showSettings(this, new Runnable() {
            public void run() {
                loadWeather();
            }
        });
    }

    // ------------------------------------------------------------------ hudba
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions =
            new MediaSessionManager.OnActiveSessionsChangedListener() {
                public void onActiveSessionsChanged(List<MediaController> list) {
                    bindMedia();
                }
            };

    private final MediaController.Callback mediaCb = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState s) {
            updateMedia();
        }

        @Override
        public void onMetadataChanged(MediaMetadata m) {
            updateMedia();
        }

        @Override
        public void onSessionDestroyed() {
            bindMedia();
        }
    };

    private void bindMedia() {
        List<MediaController> list;
        try {
            list = msm.getActiveSessions(listener);
            needMediaPermission = false;
        } catch (SecurityException e) {
            needMediaPermission = true;
            setController(null);
            return;
        }
        MediaController best = null;
        for (MediaController m : list) {
            if (best == null) best = m;
            if (isPlaying(m)) {
                best = m;
                break;
            }
        }
        setController(best);
    }

    private void setController(MediaController m) {
        boolean same = mc != null && m != null && mc.getSessionToken().equals(m.getSessionToken());
        if (!same) {
            if (mc != null) mc.unregisterCallback(mediaCb);
            if (m != null) m.registerCallback(mediaCb, h);
            mc = m;
        }
        updateMedia();
    }

    private static boolean isPlaying(MediaController m) {
        PlaybackState s = m.getPlaybackState();
        return s != null && (s.getState() == PlaybackState.STATE_PLAYING || s.getState() == PlaybackState.STATE_BUFFERING);
    }

    private void updateMedia() {
        int vis = mc != null ? View.VISIBLE : View.GONE;
        bPrev.setVisibility(vis);
        bPlay.setVisibility(vis);
        bNext.setVisibility(vis);
        sbMedia.setVisibility(vis);
        if (mc == null) {
            if (needMediaPermission) {
                mTitle.setText("Povol ovládání hudby");
                mArtist.setText("ťukni sem → Hodiny → Povolit");
            } else {
                mTitle.setText("Nic nehraje");
                mArtist.setText("ťukni pro Spotify");
            }
            return;
        }
        MediaMetadata md = mc.getMetadata();
        String title = md != null ? md.getString(MediaMetadata.METADATA_KEY_TITLE) : null;
        String artist = md != null ? md.getString(MediaMetadata.METADATA_KEY_ARTIST) : null;
        String app = appLabel(mc.getPackageName());
        mTitle.setText(title != null && title.length() > 0 ? title : app);
        mArtist.setText(artist != null && artist.length() > 0 ? artist : app);
        bPlay.setText(isPlaying(mc) ? "⏸︎" : "▶︎");
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return String.valueOf(pm.getApplicationLabel(ai));
        } catch (Exception e) {
            return pkg;
        }
    }

    private void openPlayer() {
        if (needMediaPermission) {
            try {
                startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            } catch (ActivityNotFoundException ignored) {
            }
            return;
        }
        if (mc != null) {
            PendingIntent pi = mc.getSessionActivity();
            if (pi != null) {
                try {
                    pi.send();
                    return;
                } catch (PendingIntent.CanceledException ignored) {
                }
            }
            Ui.launch(this, mc.getPackageName(), appLabel(mc.getPackageName()));
            return;
        }
        Ui.launch(this, SPOTIFY, "Spotify");
    }
}
