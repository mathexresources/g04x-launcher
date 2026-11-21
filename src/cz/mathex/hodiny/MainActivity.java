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
import android.provider.AlarmClock;
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
    private static final long NIGHT_IDLE = 60 * 1000L;
    private static final String SPOTIFY = "com.spotify.music";
    private static final String WEATHER_APP = "org.breezyweather";

    private final Handler h = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE d. MMMM", CS);

    private TextView clock, date, alarm, wIcon, wTemp, wDesc, wRange, wHours;
    private TextView mTitle, mArtist, bPrev, bPlay, bNext;
    private FrameLayout night;
    private TextView nightClock;

    private MediaSessionManager msm;
    private MediaController mc;
    private ComponentName listener;
    private boolean needMediaPermission;

    private long lastTouch = System.currentTimeMillis();
    private long lastAlarm;
    private boolean swallowTouch;

    // ------------------------------------------------------------------ layout
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setBackgroundColor(Ui.BG);
        FrameLayout root = new FrameLayout(this);

        LinearLayout main = Ui.box(this, true);
        main.setPadding(px(.06f), px(.05f), px(.06f), px(.05f));
        root.addView(main, new FrameLayout.LayoutParams(-1, -1));

        // horní část: hodiny vlevo, počasí vpravo
        LinearLayout topRow = Ui.box(this, false);
        main.addView(topRow, Ui.lp(-1, 0, 1f));

        LinearLayout left = Ui.box(this, true);
        left.setGravity(Gravity.CENTER_VERTICAL);
        topRow.addView(left, Ui.lp(0, -1, 1.45f));

        clock = Ui.text(this, "--:--", .30f, Ui.INK);
        clock.setTypeface(Ui.light());
        clock.setLetterSpacing(-0.02f);
        clock.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setNight(true);
            }
        });
        left.addView(clock);

        date = Ui.text(this, "", .062f, Ui.INK);
        left.addView(date, Ui.top(this, .025f));

        alarm = Ui.text(this, "", .055f, Ui.ACCENT);
        alarm.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showAlarms();
            }
        });
        left.addView(alarm, Ui.top(this, .03f));

        LinearLayout right = Ui.box(this, true);
        right.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        right.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Ui.launch(MainActivity.this, WEATHER_APP, "Breezy Weather");
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
        wIcon = Ui.text(this, "", .10f, Ui.INK);
        wTemp = Ui.text(this, "--°", .15f, Ui.INK);
        wTemp.setTypeface(Ui.light());
        wRow.addView(wIcon);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.leftMargin = px(.025f);
        wRow.addView(wTemp, tp);
        right.addView(wRow);

        wDesc = rightText("načítám počasí…", .05f, Ui.INK);
        right.addView(wDesc, Ui.top(this, .02f));
        wRange = rightText("", .044f, Ui.DIM);
        right.addView(wRange, Ui.top(this, .015f));
        wHours = rightText("", .04f, Ui.DIM);
        right.addView(wHours, Ui.top(this, .025f));

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
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, px(.17f));
        mp.topMargin = px(.035f);
        main.addView(media, mp);

        LinearLayout mt = Ui.box(this, true);
        mTitle = Ui.text(this, "Nic nehraje", .05f, Ui.INK);
        mTitle.setTypeface(Ui.medium());
        mTitle.setSingleLine(true);
        mTitle.setEllipsize(TextUtils.TruncateAt.END);
        mArtist = Ui.text(this, "ťukni pro Spotify", .04f, Ui.DIM);
        mArtist.setSingleLine(true);
        mArtist.setEllipsize(TextUtils.TruncateAt.END);
        mt.addView(mTitle);
        mt.addView(mArtist, Ui.top(this, .012f));
        media.addView(mt, Ui.lp(0, -2, 1f));

        bPrev = control("⏮︎");
        bPlay = control("▶︎");
        bNext = control("⏭︎");
        media.addView(bPrev, new LinearLayout.LayoutParams(px(.17f), -1));
        media.addView(bPlay, new LinearLayout.LayoutParams(px(.17f), -1));
        media.addView(bNext, new LinearLayout.LayoutParams(px(.17f), -1));
        bPrev.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mc != null) mc.getTransportControls().skipToPrevious();
            }
        });
        bPlay.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mc == null) return;
                if (isPlaying(mc)) mc.getTransportControls().pause();
                else mc.getTransportControls().play();
            }
        });
        bNext.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mc != null) mc.getTransportControls().skipToNext();
            }
        });

        // dlaždice
        LinearLayout tiles = Ui.box(this, false);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, px(.22f));
        tl.topMargin = px(.035f);
        main.addView(tiles, tl);
        addTile(tiles, "⏰", "Budík", new Runnable() {
            public void run() {
                showAlarms();
            }
        });
        addTile(tiles, "🎵", "Spotify", new Runnable() {
            public void run() {
                Ui.launch(MainActivity.this, SPOTIFY, "Spotify");
            }
        });
        addTile(tiles, "🖨", "Tiskárna", new Runnable() {
            public void run() {
                startActivity(new Intent(MainActivity.this, WebActivity.class));
            }
        });
        addTile(tiles, "🌤", "Počasí", new Runnable() {
            public void run() {
                Ui.launch(MainActivity.this, WEATHER_APP, "Breezy Weather");
            }
        });
        addTile(tiles, "⋯", "Aplikace", new Runnable() {
            public void run() {
                startActivity(new Intent(MainActivity.this, AppsActivity.class));
            }
        });

        // noční hodiny přes celou obrazovku
        night = new FrameLayout(this);
        night.setBackgroundColor(0xFF000000);
        nightClock = Ui.text(this, "", .42f, Ui.NIGHT);
        nightClock.setTypeface(Ui.light());
        night.addView(nightClock, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        night.setVisibility(View.GONE);
        root.addView(night, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);

        msm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        listener = new ComponentName(this, MediaListener.class);
        showCachedWeather();
    }

    private int px(float f) {
        return Ui.px(this, f);
    }

    private TextView rightText(String s, float size, int color) {
        TextView t = Ui.text(this, s, size, color);
        t.setGravity(Gravity.END);
        return t;
    }

    private TextView control(String symbol) {
        TextView t = Ui.text(this, symbol, .075f, Ui.INK);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.press(this));
        return t;
    }

    private void addTile(LinearLayout row, String icon, String label, final Runnable action) {
        LinearLayout t = Ui.box(this, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.tile(this));
        t.addView(Ui.text(this, icon, .075f, Ui.INK));
        TextView l = Ui.text(this, label, .042f, Ui.INK);
        t.addView(l, Ui.top(this, .02f));
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
        if (focus) Ui.immersive(this);
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
        setNight(false); // tlačítko Domů
    }

    @Override
    public void onBackPressed() {
        setNight(false); // launcher se zpět nezavírá
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        lastTouch = System.currentTimeMillis();
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN && night.getVisibility() == View.VISIBLE) {
            setNight(false);
            swallowTouch = true; // první dotek jen probudí, nic nespustí
        }
        if (swallowTouch) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                swallowTouch = false;
            }
            return true;
        }
        return super.dispatchTouchEvent(e);
    }

    // ------------------------------------------------------------------ hodiny a noc
    private final Runnable tick = new Runnable() {
        public void run() {
            long now = System.currentTimeMillis();
            Calendar c = Calendar.getInstance();
            String t = c.get(Calendar.HOUR_OF_DAY) + ":" + two(c.get(Calendar.MINUTE));
            if (!t.contentEquals(clock.getText())) {
                clock.setText(t);
                nightClock.setText(t);
                date.setText(dateFmt.format(c.getTime()));
            }
            if (now - lastAlarm > 60000) updateAlarm();
            if (now - Prefs.get(MainActivity.this).getLong("weather_ts", 0) > WEATHER_EVERY) loadWeather();
            if (isNightHours(c) && now - lastTouch > NIGHT_IDLE && night.getVisibility() != View.VISIBLE) {
                setNight(true);
            }
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

    private void setNight(boolean on) {
        boolean dark = isNightHours(Calendar.getInstance());
        night.setVisibility(on ? View.VISIBLE : View.GONE);
        nightClock.setTextColor(dark ? Ui.NIGHT : Ui.DIM);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = on ? (dark ? 0.01f : 0.25f) : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(lp);
        if (!on) lastTouch = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ budík
    private final BroadcastReceiver alarmChanged = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            updateAlarm();
        }
    };

    private void updateAlarm() {
        lastAlarm = System.currentTimeMillis();
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        AlarmManager.AlarmClockInfo ai = am.getNextAlarmClock();
        if (ai == null) {
            alarm.setText("⏰  žádný budík");
            alarm.setTextColor(Ui.DIM);
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
        alarm.setText("⏰  " + day + " " + a.get(Calendar.HOUR_OF_DAY) + ":" + two(a.get(Calendar.MINUTE)));
        alarm.setTextColor(Ui.ACCENT);
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
        try {
            startActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS));
        } catch (ActivityNotFoundException e) {
            Ui.launch(this, "com.android.deskclock", "Budík");
        }
    }

    // ------------------------------------------------------------------ počasí (Open-Meteo)
    private boolean weatherLoading;

    private void loadWeather() {
        if (weatherLoading) return;
        weatherLoading = true;
        final double lat = Prefs.lat(this), lon = Prefs.lon(this);
        new Thread(new Runnable() {
            public void run() {
                String js = null;
                try {
                    js = Prefs.http("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                            + "&current=temperature_2m,weather_code,is_day"
                            + "&hourly=temperature_2m,weather_code"
                            + "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                            + "&timezone=auto&forecast_days=2");
                    new JSONObject(js); // validace
                } catch (Exception e) {
                    js = null;
                }
                final String result = js;
                runOnUiThread(new Runnable() {
                    public void run() {
                        weatherLoading = false;
                        long now = System.currentTimeMillis();
                        if (result != null) {
                            Prefs.get(MainActivity.this).edit().putString("weather", result).putLong("weather_ts", now).apply();
                            showWeather(result);
                        } else {
                            // zkusit znovu za 2 minuty
                            Prefs.get(MainActivity.this).edit().putLong("weather_ts", now - WEATHER_EVERY + 120000).apply();
                            if (Prefs.get(MainActivity.this).getString("weather", null) == null) {
                                wDesc.setText("počasí nedostupné");
                            }
                        }
                    }
                });
            }
        }).start();
    }

    private void showCachedWeather() {
        String js = Prefs.get(this).getString("weather", null);
        if (js != null) showWeather(js);
    }

    private void showWeather(String js) {
        try {
            JSONObject o = new JSONObject(js);
            JSONObject cur = o.getJSONObject("current");
            int code = cur.getInt("weather_code");
            boolean day = cur.optInt("is_day", 1) == 1;
            wIcon.setText(icon(code, day));
            wTemp.setText(Math.round(cur.getDouble("temperature_2m")) + "°");
            wDesc.setText(Prefs.city(this) + " · " + describe(code));

            JSONObject d = o.getJSONObject("daily");
            long max = Math.round(d.getJSONArray("temperature_2m_max").getDouble(0));
            long min = Math.round(d.getJSONArray("temperature_2m_min").getDouble(0));
            int rain = d.getJSONArray("precipitation_probability_max").optInt(0, 0);
            wRange.setText("↑" + max + "°  ↓" + min + "°" + (rain >= 20 ? "   ☂ " + rain + " %" : ""));

            // další hodiny po dvou
            JSONObject hr = o.getJSONObject("hourly");
            JSONArray times = hr.getJSONArray("time");
            JSONArray temps = hr.getJSONArray("temperature_2m");
            String nowHour = cur.getString("time").substring(0, 13);
            int idx = -1;
            for (int i = 0; i < times.length(); i++) {
                if (times.getString(i).startsWith(nowHour)) {
                    idx = i;
                    break;
                }
            }
            StringBuilder sb = new StringBuilder();
            if (idx >= 0) {
                for (int k = 2; k <= 8 && idx + k < times.length(); k += 2) {
                    if (sb.length() > 0) sb.append("   ");
                    String hh = times.getString(idx + k).substring(11, 13);
                    sb.append(Integer.parseInt(hh)).append("h ").append(Math.round(temps.getDouble(idx + k))).append("°");
                }
            }
            wHours.setText(sb.toString());
        } catch (Exception e) {
            wDesc.setText("počasí: chyba dat");
        }
    }

    private static String icon(int c, boolean day) {
        if (c == 0) return day ? "☀️" : "🌙";
        if (c == 1) return day ? "🌤" : "🌙";
        if (c == 2) return "⛅";
        if (c == 3) return "☁️";
        if (c == 45 || c == 48) return "🌫";
        if (c >= 51 && c <= 57) return "🌦";
        if (c >= 61 && c <= 67) return "🌧";
        if (c >= 71 && c <= 77) return "🌨";
        if (c >= 80 && c <= 82) return "🌦";
        if (c == 85 || c == 86) return "🌨";
        if (c >= 95) return "⛈";
        return "🌡";
    }

    private static String describe(int c) {
        switch (c) {
            case 0: return "jasno";
            case 1: return "skoro jasno";
            case 2: return "polojasno";
            case 3: return "zataženo";
            case 45: case 48: return "mlha";
            case 51: case 53: case 55: return "mrholení";
            case 56: case 57: return "mrznoucí mrholení";
            case 61: return "slabý déšť";
            case 63: return "déšť";
            case 65: return "silný déšť";
            case 66: case 67: return "mrznoucí déšť";
            case 71: return "slabé sněžení";
            case 73: return "sněžení";
            case 75: return "silné sněžení";
            case 77: return "sněhová zrna";
            case 80: case 81: case 82: return "přeháňky";
            case 85: case 86: return "sněhové přeháňky";
            case 95: return "bouřka";
            case 96: case 99: return "bouřka s kroupami";
            default: return "";
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
