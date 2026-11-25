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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Domovská obrazovka (design „Ember“): hodiny, svátek a narozeniny, budík, počasí, přehrávání, dok s ikonami.
 * Po 15 s bez dotyku standby; ve tmě tlumeně červený. Jas ze senzoru světla, mávnutí rukou probudí.
 */
public class MainActivity extends Activity {
    private static final Locale CS = new Locale("cs", "CZ");
    private static final long WEATHER_EVERY = 15 * 60 * 1000L;
    private static final long STANDBY_IDLE = 15 * 1000L;
    private static final String SPOTIFY = "com.spotify.music";

    private final Handler h = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE · d. MMMM", CS);

    // plocha
    private TextView clock, date, alarmTime, alarmLeft, wTemp, wDesc, wRange;
    private ImageView wIcon;
    private LinearLayout alarmPill, chips, hours;
    private TextView mTitle, mSub;
    private ImageView mArtIcon;
    private LinearLayout bPrev, bPlay, bNext;
    private android.widget.ImageView bulbIcon;

    // standby
    private FrameLayout standby;
    private LinearLayout sbCenter, sbMedia, sbAlarm, sbPrev, sbPlay, sbNext;
    private TextView sbDate, sbClock, sbAlarmTime, sbAlarmLeft, sbTitle, sbSub, sbHint;
    private ImageView sbAlarmIcon, sbMediaIcon, sbArtImg, mArtImg;
    private LinearLayout sbControls, sbArtBox;
    private TextView sbSmallClock, sbSmallDate, sbSmallAlarm;
    // obrázek toho, co hraje (obal alba / logo stanice)
    private String artUri;
    private android.graphics.Bitmap artBitmap;
    private boolean standbyOn;

    // senzory
    private SensorManager sensors;
    private Sensor lightSensor, proxSensor;
    private float lux = -1, bright = -1;
    private boolean darkRoom;
    private long proxNearAt;
    private float luxBase = -1;
    private long luxDipAt;
    /** Živé hodnoty senzorů pro nastavení (ladění). */
    static volatile String sensorInfo = "senzory: zatím nic";
    private static volatile float lastProx = -1;
    static volatile float luxNow = -1;
    private ClapDetector clap;

    // hudba
    private MediaSessionManager msm;
    private MediaController mc;
    private ComponentName listener;
    private boolean needMediaPermission;

    private VolumeOverlay volume;
    /** Poslední stisknutá klávesa (pro zjištění kódu tlačítka Mute v nastavení). */
    static volatile String lastKey = "–";
    private long lastTouch = System.currentTimeMillis();
    private boolean swallowTouch;

    private int px(float f) {
        return Ui.px(this, f);
    }

    // ================================================================== layout
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.loadFonts(this);
        getWindow().getDecorView().setBackgroundColor(Ui.BG);
        FrameLayout root = new FrameLayout(this);

        LinearLayout main = Ui.box(this, true);
        main.setBackgroundColor(Ui.BG);
        main.setPadding(px(.0625f), px(.054f), px(.0625f), px(.05f));
        root.addView(main, new FrameLayout.LayoutParams(-1, -1));

        // --- horní řádek: hodiny vlevo, počasí vpravo
        LinearLayout topRow = Ui.box(this, false);
        main.addView(topRow, Ui.lp(-1, 0, 1f));

        LinearLayout left = Ui.box(this, true);
        left.setGravity(Gravity.CENTER_VERTICAL);
        topRow.addView(left, Ui.lp(0, -1, 1f));

        date = Ui.text(this, "", .044f, Ui.DIM);
        left.addView(date);

        clock = Ui.text(this, "--:--", .325f, Ui.INK);
        clock.setTypeface(Ui.display());
        clock.setLetterSpacing(-0.04f);
        clock.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setStandby(true);
            }
        });
        left.addView(clock, Ui.top(this, .02f));

        chips = Ui.box(this, false);
        left.addView(chips, Ui.top(this, .03f));

        alarmPill = Ui.box(this, false);
        alarmPill.setGravity(Gravity.CENTER_VERTICAL);
        alarmPill.setBackground(Ui.tile(this, Ui.AMBER_BG, .033f));
        alarmPill.setPadding(px(.025f), px(.021f), px(.033f), px(.021f));
        alarmPill.addView(Ui.icon(this, R.drawable.ic_alarm, Ui.AMBER, .052f));
        alarmTime = Ui.text(this, "", .04f, Ui.AMBER);
        alarmTime.setTypeface(Ui.medium());
        alarmPill.addView(alarmTime, Ui.left(this, .02f));
        alarmLeft = Ui.text(this, "", .04f, Ui.AMBER_SOFT);
        alarmLeft.setSingleLine(true);
        alarmLeft.setEllipsize(TextUtils.TruncateAt.END);
        alarmPill.addView(alarmLeft, Ui.left(this, .012f));
        alarmPill.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showAlarms();
            }
        });
        left.addView(alarmPill, Ui.top(this, .025f));

        // karta počasí
        LinearLayout wCard = Ui.box(this, true);
        wCard.setBackground(Ui.round(Ui.TILE, px(.054f)));
        wCard.setPadding(px(.046f), px(.042f), px(.046f), px(.038f));
        wCard.setGravity(Gravity.CENTER_VERTICAL);
        wCard.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                wDesc.setText("aktualizuji…");
                loadWeather();
            }
        });
        wCard.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                openSettings();
                return true;
            }
        });
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(px(.546f), -1);
        wl.leftMargin = px(.046f);
        topRow.addView(wCard, wl);

        LinearLayout wRow = Ui.box(this, false);
        wRow.setGravity(Gravity.CENTER_VERTICAL);
        wIcon = Ui.icon(this, R.drawable.w_cloud, Ui.TEAL, .112f);
        wRow.addView(wIcon);
        wTemp = Ui.text(this, "--°", .13f, Ui.INK);
        wTemp.setTypeface(Ui.display());
        wTemp.setLetterSpacing(-0.03f);
        wRow.addView(wTemp, Ui.left(this, .025f));
        wCard.addView(wRow);
        wDesc = Ui.text(this, "načítám počasí…", .037f, Ui.INK);
        wDesc.setSingleLine(true);
        wDesc.setEllipsize(TextUtils.TruncateAt.END);
        wCard.addView(wDesc, Ui.top(this, .02f));
        wRange = Ui.text(this, "", .033f, Ui.DIM);
        wCard.addView(wRange, Ui.top(this, .012f));
        View line = new View(this);
        line.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, Math.max(1, px(.002f)));
        ll.topMargin = px(.025f);
        ll.bottomMargin = px(.02f);
        wCard.addView(line, ll);
        hours = Ui.box(this, false);
        wCard.addView(hours, new LinearLayout.LayoutParams(-1, -2));

        // --- spodní řádek: přehrávání + dok
        LinearLayout bottom = Ui.box(this, false);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, px(.183f));
        bl.topMargin = px(.0375f);
        main.addView(bottom, bl);

        LinearLayout media = Ui.box(this, false);
        media.setGravity(Gravity.CENTER_VERTICAL);
        media.setBackground(Ui.tile(this));
        media.setPadding(px(.025f), 0, px(.02f), 0);
        media.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openPlayer();
            }
        });
        bottom.addView(media, Ui.lp(0, -1, 1f));

        LinearLayout art = Ui.box(this, false);
        art.setGravity(Gravity.CENTER);
        art.setBackground(Ui.round(Ui.TEAL_BG, px(.031f)));
        mArtIcon = Ui.icon(this, R.drawable.ic_radio, Ui.TEAL, .066f);
        art.addView(mArtIcon);
        art.setClipToOutline(true);
        mArtImg = new ImageView(this);
        mArtImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mArtImg.setVisibility(View.GONE);
        art.addView(mArtImg, new LinearLayout.LayoutParams(-1, -1));
        media.addView(art, new LinearLayout.LayoutParams(px(.125f), px(.125f)));

        LinearLayout mt = Ui.box(this, true);
        mTitle = Ui.text(this, "Nic nehraje", .038f, Ui.INK);
        mTitle.setTypeface(Ui.medium());
        mTitle.setSingleLine(true);
        mTitle.setEllipsize(TextUtils.TruncateAt.END);
        mSub = Ui.text(this, "ťukni pro rádio", .031f, Ui.DIM);
        mSub.setSingleLine(true);
        mSub.setEllipsize(TextUtils.TruncateAt.END);
        mt.addView(mTitle);
        mt.addView(mSub, Ui.top(this, .008f));
        LinearLayout.LayoutParams mtl = Ui.lp(0, -2, 1f);
        mtl.leftMargin = px(.025f);
        media.addView(mt, mtl);

        bPrev = Ui.roundButton(this, R.drawable.ic_prev, Ui.SOFT, 0, .092f, .052f);
        bPlay = Ui.roundButton(this, R.drawable.ic_play, Ui.ON_AMBER, Ui.AMBER, .117f, .052f);
        bNext = Ui.roundButton(this, R.drawable.ic_next, Ui.SOFT, 0, .092f, .052f);
        media.addView(bPrev);
        media.addView(bPlay);
        media.addView(bNext);

        LinearLayout dock = Ui.box(this, false);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(px(.833f), -1);
        dl.leftMargin = px(.025f);
        bottom.addView(dock, dl);
        dockTile(dock, R.drawable.ic_alarm, Ui.AMBER, new Runnable() {
            public void run() {
                showAlarms();
            }
        });
        dockTile(dock, R.drawable.ic_music, Ui.INK, new Runnable() {
            public void run() {
                Ui.launch(MainActivity.this, SPOTIFY, "Spotify");
            }
        });
        dockTile(dock, R.drawable.ic_radio, Ui.TEAL, new Runnable() {
            public void run() {
                startActivity(new Intent(MainActivity.this, RadioActivity.class));
            }
        });
        bulbIcon = dockTile(dock, R.drawable.ic_bulb, Ui.INK, new Runnable() {
            public void run() {
                toggleLight();
            }
        });
        ((View) bulbIcon.getParent()).setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                startActivity(new Intent(MainActivity.this, LightActivity.class));
                return true;
            }
        });
        dockTile(dock, R.drawable.ic_printer, Ui.INK, new Runnable() {
            public void run() {
                startActivity(new Intent(MainActivity.this, PrinterActivity.class));
            }
        });
        dockTile(dock, R.drawable.ic_grid, Ui.INK, new Runnable() {
            public void run() {
                startActivity(new Intent(MainActivity.this, AppsActivity.class));
            }
        });

        buildStandby(root);
        setContentView(root);

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

        msm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        listener = new ComponentName(this, MediaListener.class);
        showCachedWeather();
        Alarms.schedule(this);
    }

    private android.widget.ImageView dockTile(LinearLayout dock, int icon, int color, final Runnable action) {
        LinearLayout t = Ui.box(this, false);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.tile(this));
        android.widget.ImageView iv = Ui.icon(this, icon, color, .07f);
        t.addView(iv);
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                action.run();
            }
        });
        LinearLayout.LayoutParams p = Ui.lp(0, -1, 1f);
        if (dock.getChildCount() > 0) p.leftMargin = px(.017f);
        dock.addView(t, p);
        return iv;
    }

    // ================================================================== světlo (Yeelight)
    /** Krátký stisk 💡 = zapnout/vypnout, podržení = obrazovka světla. */
    private void toggleLight() {
        if (!Yeelight.configured(this)) {
            startActivity(new Intent(this, LightActivity.class));
            return;
        }
        Yeelight.toggle(this, new Yeelight.Done() {
            public void done(boolean ok, org.json.JSONArray r) {
                if (!ok) android.widget.Toast.makeText(MainActivity.this, "Žárovka neodpovídá", android.widget.Toast.LENGTH_SHORT).show();
                h.postDelayed(new Runnable() {
                    public void run() {
                        updateBulb();
                    }
                }, 500);
            }
        });
    }

    private void updateBulb() {
        if (!Yeelight.configured(this)) {
            Ui.tint(bulbIcon, Ui.DIM);
            return;
        }
        Yeelight.state(this, new Yeelight.StateDone() {
            public void done(Yeelight.State s) {
                Ui.tint(bulbIcon, s == null ? Ui.DIM : s.on ? Ui.AMBER : Ui.INK);
            }
        });
    }

    private void buildStandby(FrameLayout root) {
        standby = new FrameLayout(this);
        standby.setBackgroundColor(Ui.BG_STANDBY);

        sbCenter = Ui.box(this, true);
        sbCenter.setGravity(Gravity.CENTER);
        sbDate = Ui.text(this, "", .044f, Ui.DIM);
        sbDate.setGravity(Gravity.CENTER_HORIZONTAL);
        sbCenter.addView(sbDate, new LinearLayout.LayoutParams(-2, -2));
        sbClock = Ui.text(this, "", .42f, Ui.INK);
        sbClock.setTypeface(Ui.display());
        sbClock.setLetterSpacing(-0.045f);
        sbCenter.addView(sbClock, Ui.top(this, .02f));
        sbAlarm = Ui.box(this, false);
        sbAlarm.setGravity(Gravity.CENTER_VERTICAL);
        sbAlarmIcon = Ui.icon(this, R.drawable.ic_alarm, Ui.AMBER, .054f);
        sbAlarm.addView(sbAlarmIcon);
        sbAlarmTime = Ui.text(this, "", .048f, Ui.AMBER);
        sbAlarmTime.setTypeface(Ui.medium());
        sbAlarm.addView(sbAlarmTime, Ui.left(this, .02f));
        sbAlarmLeft = Ui.text(this, "", .048f, Ui.AMBER_SOFT);
        sbAlarm.addView(sbAlarmLeft, Ui.left(this, .012f));
        sbCenter.addView(sbAlarm, Ui.top(this, .045f));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1, -1);
        standby.addView(sbCenter, cp);

        // když něco hraje: hlavní prvek je přehrávání (obrázek + název + velká tlačítka), čas a budík malé nahoře
        sbMedia = Ui.box(this, false);
        sbMedia.setGravity(Gravity.CENTER_VERTICAL);
        sbMedia.setPadding(px(.067f), px(.067f), px(.067f), px(.067f));

        sbArtBox = Ui.box(this, false);
        sbArtBox.setGravity(Gravity.CENTER);
        sbArtBox.setBackground(Ui.round(Ui.TEAL_BG, px(.054f)));
        sbArtBox.setClipToOutline(true);
        sbMediaIcon = Ui.icon(this, R.drawable.ic_radio, Ui.TEAL, .2f);
        sbArtBox.addView(sbMediaIcon);
        sbArtImg = new ImageView(this);
        sbArtImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        sbArtImg.setVisibility(View.GONE);
        sbArtBox.addView(sbArtImg, new LinearLayout.LayoutParams(-1, -1));
        sbMedia.addView(sbArtBox, new LinearLayout.LayoutParams(px(.73f), px(.73f)));

        LinearLayout info = Ui.box(this, true);
        LinearLayout.LayoutParams il = Ui.lp(0, -1, 1f);
        il.leftMargin = px(.058f);
        sbMedia.addView(info, il);

        LinearLayout topLine = Ui.box(this, false);
        topLine.setGravity(Gravity.BOTTOM);
        sbSmallClock = Ui.text(this, "", .1f, Ui.INK);
        sbSmallClock.setTypeface(Ui.display());
        sbSmallClock.setLetterSpacing(-0.03f);
        topLine.addView(sbSmallClock);
        LinearLayout meta = Ui.box(this, true);
        sbSmallDate = Ui.text(this, "", .033f, Ui.DIM);
        sbSmallDate.setSingleLine(true);
        meta.addView(sbSmallDate);
        sbSmallAlarm = Ui.text(this, "", .033f, Ui.AMBER);
        sbSmallAlarm.setSingleLine(true);
        meta.addView(sbSmallAlarm, Ui.top(this, .008f));
        LinearLayout.LayoutParams ml = Ui.lp(0, -2, 1f);
        ml.leftMargin = px(.03f);
        ml.bottomMargin = px(.012f);
        topLine.addView(meta, ml);
        info.addView(topLine);

        View gap = new View(this);
        info.addView(gap, Ui.lp(-1, 0, 1f));

        sbTitle = Ui.text(this, "", .068f, Ui.INK);
        sbTitle.setTypeface(Ui.medium());
        sbTitle.setMaxLines(2);
        sbTitle.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(sbTitle);
        sbSub = Ui.text(this, "", .045f, Ui.DIM);
        sbSub.setSingleLine(true);
        sbSub.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(sbSub, Ui.top(this, .015f));

        View gap2 = new View(this);
        info.addView(gap2, Ui.lp(-1, 0, 1f));

        sbControls = Ui.box(this, false);
        sbControls.setGravity(Gravity.CENTER_VERTICAL);
        sbPrev = Ui.roundButton(this, R.drawable.ic_prev, Ui.SOFT, 0, .15f, .07f);
        sbPlay = Ui.roundButton(this, R.drawable.ic_pause, Ui.ON_AMBER, Ui.AMBER, .19f, .075f);
        sbNext = Ui.roundButton(this, R.drawable.ic_next, Ui.SOFT, 0, .15f, .07f);
        sbControls.addView(sbPrev);
        LinearLayout.LayoutParams ppl = new LinearLayout.LayoutParams(px(.19f), px(.19f));
        ppl.leftMargin = ppl.rightMargin = px(.04f);
        sbControls.addView(sbPlay, ppl);
        sbControls.addView(sbNext);
        info.addView(sbControls);

        standby.addView(sbMedia, new FrameLayout.LayoutParams(-1, -1));
        sbMedia.setVisibility(View.GONE);

        sbHint = Ui.text(this, "", .027f, Ui.DIM);
        sbHint.setVisibility(View.GONE);

        standby.setVisibility(View.GONE);
        root.addView(standby, new FrameLayout.LayoutParams(-1, -1));
    }

    // ================================================================== životní cyklus
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
        startSensors();
        startClap();
        loadToday(false);
        updateBulb();
    }

    /** Dvojí tlesknutí – jen když je zapnuté v nastavení a je povolený mikrofon. */
    private void startClap() {
        if (clap != null) clap.stop();
        clap = null;
        if (!Prefs.clapEnabled(this)) return;
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;
        clap = new ClapDetector(new ClapDetector.Listener() {
            public void onDoubleClap() {
                if (mc != null) {
                    if (isPlaying(mc)) mc.getTransportControls().pause();
                    else mc.getTransportControls().play();
                } else if (standbyOn) {
                    setStandby(false);
                }
                android.widget.Toast.makeText(MainActivity.this, "👏👏", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        clap.start();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        startClap();
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
        if (sensors != null) sensors.unregisterListener(sensorListener);
        if (clap != null) clap.stop();
        clap = null;
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

    @Override
    public boolean onKeyDown(int code, android.view.KeyEvent e) {
        lastKey = android.view.KeyEvent.keyCodeToString(code) + " (" + code + ")";
        if (volume == null) volume = new VolumeOverlay(this);
        if (volume.onKeyDown(code)) return true;
        return super.onKeyDown(code, e);
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
            if (!inside(sbControls, e)) {
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

    // ================================================================== hodiny a standby
    private final Runnable tick = new Runnable() {
        public void run() {
            long now = System.currentTimeMillis();
            Calendar c = Calendar.getInstance();
            String t = c.get(Calendar.HOUR_OF_DAY) + ":" + two(c.get(Calendar.MINUTE));
            if (!t.contentEquals(clock.getText())) {
                clock.setText(t);
                sbClock.setText(t);
                String d = dateFmt.format(c.getTime());
                date.setText(d);
                sbDate.setText(d.replace(" · ", " "));
                sbSmallClock.setText(t);
                sbSmallDate.setText(d.replace(" · ", " "));
                updateAlarm(); // odpočet do budíku
                if (standbyOn) {
                    styleStandby();
                    shiftStandby();
                }
                if (c.get(Calendar.HOUR_OF_DAY) == 0 && c.get(Calendar.MINUTE) == 1) loadToday(true);
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

    /** Tma: podle senzoru světla (když je), jinak podle hodin 22–7. */
    private boolean isDark() {
        return lightSensor != null && lux >= 0 ? darkRoom : isNightHours(Calendar.getInstance());
    }

    private void setStandby(boolean on) {
        standbyOn = on;
        standby.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) {
            shiftStandby();
            styleStandby();
        } else {
            lastTouch = System.currentTimeMillis();
            applyBrightness();
        }
    }

    /** Ochrana displeje: obsah standby se každou minutu posune o pár pixelů. */
    private void shiftStandby() {
        Random r = new Random();
        int max = px(.035f);
        sbCenter.setTranslationX(r.nextInt(2 * max + 1) - max);
        sbCenter.setTranslationY(r.nextInt(2 * max + 1) - max);
        int m = px(.015f);
        sbMedia.setTranslationX(r.nextInt(2 * m + 1) - m);
        sbMedia.setTranslationY(r.nextInt(2 * m + 1) - m);
    }

    /** Standby má vždy stejný vzhled (červený noční režim byl na přání vypnut). */
    private void styleStandby() {
        standby.setBackgroundColor(Ui.BG_STANDBY);
        applyBrightness();
    }

    /**
     * Jas z luxů (logaritmicky), plynule: tma ~12 %, pokoj večer ~45 %, den ~75 %, slunce 100 %.
     * Standby má stejný jas jako plocha.
     */
    private void applyBrightness() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        if (lightSensor == null || bright < 0) {
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        } else {
            lp.screenBrightness = Math.max(0.12f, bright);
        }
        getWindow().setAttributes(lp);
    }

    private void startSensors() {
        if (sensors == null) {
            sensors = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            lightSensor = sensors.getDefaultSensor(Sensor.TYPE_LIGHT);
            proxSensor = sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        }
        if (lightSensor != null) sensors.registerListener(sensorListener, lightSensor, SensorManager.SENSOR_DELAY_GAME);
        if (proxSensor != null) sensors.registerListener(sensorListener, proxSensor, SensorManager.SENSOR_DELAY_GAME);
        sensorInfo = "světlo: " + (lightSensor != null ? lightSensor.getName() : "není")
                + "\npřiblížení: " + (proxSensor != null ? proxSensor.getName() + " (max " + proxSensor.getMaximumRange() + ")" : "není");
    }

    private final SensorEventListener sensorListener = new SensorEventListener() {
        public void onSensorChanged(SensorEvent e) {
            long now = System.currentTimeMillis();
            if (e.sensor.getType() == Sensor.TYPE_LIGHT) {
                lux = e.values[0];
                // mávnutí přes senzor světla: rychlý pokles pod 40 % a návrat do 1,5 s
                if (luxBase < 0) luxBase = lux;
                if (luxDipAt == 0 && luxBase > 4 && lux < luxBase * 0.4f) {
                    luxDipAt = now;
                } else if (luxDipAt > 0) {
                    if (lux > luxBase * 0.65f) {
                        if (now - luxDipAt < 1500) wave();
                        luxDipAt = 0;
                    } else if (now - luxDipAt > 3000) {
                        luxDipAt = 0; // zhaslo se natrvalo, nebylo to mávnutí
                        luxBase = lux;
                    }
                }
                if (luxDipAt == 0) luxBase = luxBase + (lux - luxBase) * 0.15f;
                float target = (float) Math.min(1.0, 0.12 + Math.log10(lux + 1) / 2.8 * 0.88);
                bright = bright < 0 ? target : bright + (target - bright) * 0.35f;
                boolean wasDark = darkRoom;
                luxNow = lux;
                if (lux < 3) darkRoom = true;
                else if (lux > 8) darkRoom = false; // hystereze, ať to nebliká
                if (standbyOn && wasDark != darkRoom) styleStandby();
                else applyBrightness();
            } else if (e.sensor.getType() == Sensor.TYPE_PROXIMITY) {
                lastProx = e.values[0];
                boolean near = e.values[0] < e.sensor.getMaximumRange();
                if (near) {
                    proxNearAt = now;
                    wave(); // stačí přiblížit ruku
                } else {
                    proxNearAt = 0;
                }
            }
        }

        public void onAccuracyChanged(Sensor s, int a) {
        }
    };

    private long lastWave;

    private void wave() {
        long now = System.currentTimeMillis();
        lastTouch = now;
        if (now - lastWave < 1000) return;
        lastWave = now;
        if (standbyOn) setStandby(false);
    }

    /** Živé hodnoty pro nastavení. */
    static String sensorDebug() {
        return sensorInfo + "\nposlední tlačítko: " + lastKey + "\nteď: přiblížení " + lastProx + " · mikrofon špička " + ClapDetector.lastPeak
                + " / pozadí " + ClapDetector.lastBg;
    }

    // ================================================================== budík
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
            alarmTime.setText("žádný budík");
            alarmLeft.setText("");
            alarmTime.setTextColor(Ui.DIM);
            alarmPill.setBackground(Ui.tile(this, Ui.TILE, .033f));
            Ui.tint((ImageView) alarmPill.getChildAt(0), Ui.DIM);
            sbAlarm.setVisibility(View.GONE);
            sbSmallAlarm.setVisibility(View.GONE);
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
        String left = "· " + countdown(ai.getTriggerTime() - System.currentTimeMillis());
        alarmTime.setText(day + " " + time);
        alarmTime.setTextColor(Ui.AMBER);
        alarmLeft.setText(left);
        alarmPill.setBackground(Ui.tile(this, Ui.AMBER_BG, .033f));
        Ui.tint((ImageView) alarmPill.getChildAt(0), Ui.AMBER);
        sbAlarmTime.setText(time);
        sbAlarmLeft.setText(left);
        sbAlarm.setVisibility(View.VISIBLE);
        sbSmallAlarm.setText("⏰ " + time + " " + left);
        sbSmallAlarm.setVisibility(View.VISIBLE);
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

    // ================================================================== počasí (wttr.in)
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
            int hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            wIcon.setImageResource(weatherIcon(code, hr >= 7 && hr < 19));
            wTemp.setText(num(cur, "temp_C") + "°");
            String desc = val(cur, "lang_cs");
            if (desc == null || desc.trim().length() == 0) desc = describe(code);
            wDesc.setText(Prefs.weatherName(this) + " · " + desc.trim().toLowerCase(CS));

            JSONArray days = o.getJSONArray("weather");
            JSONObject today = days.getJSONObject(0);
            JSONArray hourly = today.getJSONArray("hourly");
            int rain = 0;
            for (int i = 0; i < hourly.length(); i++) {
                JSONObject h = hourly.getJSONObject(i);
                if (Integer.parseInt(h.getString("time")) / 100 + 3 > hr) rain = Math.max(rain, (int) num(h, "chanceofrain"));
            }
            wRange.setText("↑ " + num(today, "maxtempC") + "°   ↓ " + num(today, "mintempC") + "°"
                    + (rain >= 20 ? "   ·   déšť " + rain + " %" : ""));

            // další 4 tříhodinové úseky (wttr má hodnoty po 3 h), i přes půlnoc
            hours.removeAllViews();
            int shown = 0;
            for (int d = 0; d < days.length() && shown < 4; d++) {
                JSONArray hs = days.getJSONObject(d).getJSONArray("hourly");
                for (int i = 0; i < hs.length() && shown < 4; i++) {
                    JSONObject h = hs.getJSONObject(i);
                    int at = Integer.parseInt(h.getString("time")) / 100;
                    if (d == 0 && at <= hr) continue;
                    LinearLayout col = Ui.box(this, true);
                    col.setGravity(Gravity.CENTER_HORIZONTAL);
                    col.addView(Ui.text(this, at + "h", .031f, Ui.DIM));
                    ImageView ic = Ui.icon(this, weatherIcon((int) num(h, "weatherCode"), at >= 7 && at < 19), Ui.SOFT, .052f);
                    LinearLayout.LayoutParams il = (LinearLayout.LayoutParams) ic.getLayoutParams();
                    il.topMargin = il.bottomMargin = px(.012f);
                    col.addView(ic);
                    TextView t = Ui.text(this, num(h, "tempC") + "°", .036f, Ui.INK);
                    col.addView(t);
                    hours.addView(col, Ui.lp(0, -2, 1f));
                    shown++;
                }
            }
        } catch (Exception e) {
            wDesc.setText("počasí: chyba dat, ťukni pro obnovení");
            Prefs.get(this).edit().remove("weather_wttr").putLong("weather_ts", 0).apply();
        }
    }

    /** Kódy počasí World Weather Online (používá je wttr.in) → ikony. */
    private static int weatherIcon(int c, boolean day) {
        switch (c) {
            case 113: return day ? R.drawable.w_sun : R.drawable.w_moon;
            case 116: return day ? R.drawable.w_partly : R.drawable.w_cloud;
            case 119: case 122: return R.drawable.w_cloud;
            case 143: case 248: case 260: return R.drawable.w_fog;
            case 200: case 386: case 389: case 392: case 395: return R.drawable.w_storm;
            case 227: case 230: case 323: case 326: case 329: case 332: case 335: case 338: case 368: case 371:
                return R.drawable.w_snow;
            default: return R.drawable.w_rain;
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
                loadToday(true);
                startClap();
            }
        });
    }

    // ================================================================== hudba
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
        // standby: když něco hraje, je hlavní prvek přehrávání; jinak velký čas uprostřed
        sbCenter.setVisibility(mc != null ? View.GONE : View.VISIBLE);
        sbClock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.base(this) * .58f);
        if (mc == null) {
            showArt(null);
            mArtIcon.setImageResource(R.drawable.ic_radio);
            if (needMediaPermission) {
                mTitle.setText("Povol ovládání hudby");
                mSub.setText("ťukni sem → Hodiny → Povolit");
            } else {
                mTitle.setText("Nic nehraje");
                mSub.setText("ťukni pro rádio");
            }
            return;
        }
        boolean radio = getPackageName().equals(mc.getPackageName());
        int artRes = radio ? R.drawable.ic_radio : R.drawable.ic_music;
        mArtIcon.setImageResource(artRes);
        sbMediaIcon.setImageResource(artRes);
        MediaMetadata md = mc.getMetadata();
        String title = md != null ? md.getString(MediaMetadata.METADATA_KEY_TITLE) : null;
        String artist = md != null ? md.getString(MediaMetadata.METADATA_KEY_ARTIST) : null;
        String app = appLabel(mc.getPackageName());
        String t = title != null && title.length() > 0 ? title : app;
        String sub = radio ? "rádio · živě" : (artist != null && artist.length() > 0 ? artist : app);
        mTitle.setText(t);
        mSub.setText(sub);
        sbTitle.setText(t);
        sbSub.setText(sub);
        boolean playing = isPlaying(mc);
        Ui.setIcon(bPlay, playing ? R.drawable.ic_pause : R.drawable.ic_play);
        Ui.setIcon(sbPlay, playing ? R.drawable.ic_pause : R.drawable.ic_play);
        loadArt(md);
    }

    /** Obrázek z metadat: bitmapa (Spotify posílá obal alba), jinak adresa http(s) stažená na pozadí. */
    private void loadArt(MediaMetadata md) {
        if (md == null) {
            showArt(null);
            return;
        }
        android.graphics.Bitmap bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (bmp == null) bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (bmp == null) bmp = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        if (bmp != null) {
            artUri = null;
            showArt(bmp);
            return;
        }
        String uri = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
        if (uri == null) uri = md.getString(MediaMetadata.METADATA_KEY_ART_URI);
        if (uri == null) uri = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
        if (uri == null || !uri.startsWith("http")) {
            showArt(null);
            return;
        }
        if (uri.equals(artUri)) {
            showArt(artBitmap);
            return;
        }
        artUri = uri;
        final String u = uri;
        new Thread(new Runnable() {
            public void run() {
                android.graphics.Bitmap b = null;
                try {
                    java.net.HttpURLConnection con = (java.net.HttpURLConnection) new java.net.URL(u).openConnection();
                    con.setConnectTimeout(8000);
                    con.setReadTimeout(15000);
                    b = android.graphics.BitmapFactory.decodeStream(con.getInputStream());
                    con.disconnect();
                } catch (Exception ignored) {
                }
                final android.graphics.Bitmap fb = b;
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (!u.equals(artUri)) return; // mezitím hraje něco jiného
                        artBitmap = fb;
                        showArt(fb);
                    }
                });
            }
        }).start();
    }

    private void showArt(android.graphics.Bitmap b) {
        for (ImageView v : new ImageView[]{sbArtImg, mArtImg}) {
            v.setImageBitmap(b);
            v.setVisibility(b != null ? View.VISIBLE : View.GONE);
        }
        sbMediaIcon.setVisibility(b != null ? View.GONE : View.VISIBLE);
        mArtIcon.setVisibility(b != null ? View.GONE : View.VISIBLE);
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
        startActivity(new Intent(this, RadioActivity.class));
    }

    // ================================================================== svátek a narozeniny
    private boolean todayLoading;

    /** Z thermoprint-serveru (GET /api/today), jinak aspoň svátek ze svatky.adresa.info. Cache na den. */
    private void loadToday(boolean force) {
        final String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date());
        String cached = Prefs.get(this).getString("today2_" + day, null);
        if (cached != null && !force) {
            showToday(cached);
            return;
        }
        if (todayLoading) return;
        todayLoading = true;
        new Thread(new Runnable() {
            public void run() {
                JSONObject res = null;
                String token = Prefs.apiToken(MainActivity.this);
                if (token.length() > 0) {
                    try {
                        java.net.HttpURLConnection con = (java.net.HttpURLConnection)
                                new java.net.URL(Prefs.printerBase(MainActivity.this) + "/api/today").openConnection();
                        con.setConnectTimeout(6000);
                        con.setReadTimeout(15000);
                        con.setRequestProperty("Authorization", "Bearer " + token);
                        if (con.getResponseCode() == 200) {
                            java.io.InputStream in = con.getInputStream();
                            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                            byte[] buf = new byte[4096];
                            int n;
                            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                            res = new JSONObject(out.toString("UTF-8"));
                        }
                        con.disconnect();
                    } catch (Exception ignored) {
                    }
                }
                if (res == null) { // server bez /api/today nebo nedostupný → jen svátek
                    try {
                        JSONArray j = new JSONArray(Prefs.http("https://svatky.adresa.info/json?date="
                                + new SimpleDateFormat("ddMM", Locale.US).format(new java.util.Date())));
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < j.length(); i++) {
                            String nm = j.getJSONObject(i).optString("name");
                            if (nm.length() > 0) sb.append(sb.length() > 0 ? ", " : "").append(nm);
                        }
                        res = new JSONObject().put("nameday", sb.toString());
                    } catch (Exception ignored) {
                    }
                }
                final JSONObject r = res;
                runOnUiThread(new Runnable() {
                    public void run() {
                        todayLoading = false;
                        if (r == null) return; // zkusí se při dalším startu
                        Prefs.get(MainActivity.this).edit().putString("today2_" + day, r.toString()).apply();
                        showToday(r.toString());
                    }
                });
            }
        }).start();
    }

    /** Štítky pod hodinami: svátek, narozeniny (dnes – pozítří), případně státní svátek. */
    private void showToday(String js) {
        chips.removeAllViews();
        try {
            JSONObject o = new JSONObject(js);
            String nd = o.optString("nameday", "");
            if (nd.length() > 0 && !"null".equals(nd)) addChip(R.drawable.ic_nameday, Ui.TEAL, "svátek má " + nd);
            JSONArray b = o.optJSONArray("birthdays");
            for (int i = 0; b != null && i < b.length() && chips.getChildCount() < 3; i++) {
                JSONObject x = b.optJSONObject(i);
                int in = x.optInt("in_days", 99);
                if (in > 2) continue;
                String when = in == 0 ? "dnes" : in == 1 ? "zítra" : "pozítří";
                String age = x.isNull("age") ? "" : " (" + x.optInt("age") + ")";
                addChip(R.drawable.ic_cake, Ui.AMBER, when + " " + x.optString("name") + age);
            }
            JSONArray hol = o.optJSONArray("holidays");
            if (hol != null && hol.length() > 0 && chips.getChildCount() < 2) {
                addChip(R.drawable.ic_nameday, Ui.AMBER, hol.optString(0));
            }
        } catch (Exception ignored) {
        }
        chips.setVisibility(chips.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private void addChip(int icon, int iconColor, String text) {
        LinearLayout c = Ui.chip(this, icon, iconColor, text, Ui.SOFT, Ui.TILE, .035f);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        if (chips.getChildCount() > 0) p.leftMargin = px(.017f);
        chips.addView(c, p);
    }
}
