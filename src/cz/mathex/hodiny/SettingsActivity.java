package cz.mathex.hodiny;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

/**
 * Nastavení na celou obrazovku: vlevo sekce, vpravo jejich obsah. Ukládá se hned (bez tlačítka Uložit).
 */
public class SettingsActivity extends BaseActivity {
    private static final String[] SECTIONS = {"Standby", "Počasí", "Tiskárna", "Světlo", "Mikrofon", "Systém"};
    private static final int[] ICONS = {R.drawable.w_moon, R.drawable.w_partly, R.drawable.ic_printer, R.drawable.ic_bulb,
            R.drawable.ic_music, R.drawable.ic_settings};

    private final Handler h = new Handler(Looper.getMainLooper());
    private LinearLayout nav, content;
    private int section;
    private TextView live; // živé hodnoty (mikrofon, senzory)

    private int px(float f) {
        return Ui.px(this, f);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.loadFonts(this);
        LinearLayout root = Ui.box(this, true);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.bar(this, "Nastavení", "v" + Updater.currentVersion(this), null));

        LinearLayout panes = Ui.box(this, false);
        panes.setPadding(px(.0625f), px(.02f), px(.0625f), px(.04f));
        root.addView(panes, Ui.lp(-1, 0, 1f));

        nav = Ui.box(this, true);
        panes.addView(nav, new LinearLayout.LayoutParams(px(.46f), -1));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        content = Ui.box(this, true);
        sv.addView(content);
        LinearLayout.LayoutParams cl = Ui.lp(0, -1, 1f);
        cl.leftMargin = px(.045f);
        panes.addView(sv, cl);

        setContentView(root);
        section = getIntent().getIntExtra("section", 0);
        render();
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacksAndMessages(null);
        View f = getCurrentFocus();
        if (f != null) f.clearFocus(); // uložit rozepsané pole
    }

    // ------------------------------------------------------------------ kostra
    private void render() {
        h.removeCallbacksAndMessages(null);
        live = null;
        nav.removeAllViews();
        for (int i = 0; i < SECTIONS.length; i++) {
            final int idx = i;
            boolean sel = i == section;
            LinearLayout item = Ui.box(this, false);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(px(.035f), 0, px(.02f), 0);
            item.setBackground(sel ? Ui.round(Ui.TILE, px(.035f)) : Ui.press(this));
            item.addView(Ui.icon(this, ICONS[i], sel ? Ui.AMBER : Ui.DIM, .05f));
            TextView t = Ui.text(this, SECTIONS[i], .042f, sel ? Ui.INK : Ui.SOFT);
            if (sel) t.setTypeface(Ui.medium());
            item.addView(t, Ui.left(this, .03f));
            item.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    View f = getCurrentFocus();
                    if (f != null) f.clearFocus();
                    section = idx;
                    render();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(.105f));
            if (i > 0) lp.topMargin = px(.008f);
            nav.addView(item, lp);
        }
        content.removeAllViews();
        switch (section) {
            case 0: standby(); break;
            case 1: weather(); break;
            case 2: printer(); break;
            case 3: light(); break;
            case 4: mic(); break;
            default: system(); break;
        }
    }

    // ------------------------------------------------------------------ prvky
    interface Saver {
        void save(String v);
    }

    private void heading(String s) {
        TextView t = Ui.text(this, s, .05f, Ui.INK);
        t.setTypeface(Ui.medium());
        t.setPadding(0, content.getChildCount() == 0 ? px(.015f) : px(.04f), 0, px(.012f));
        content.addView(t);
    }

    private void note(String s) {
        TextView t = Ui.text(this, s, .033f, Ui.DIM);
        t.setPadding(0, px(.012f), 0, 0);
        content.addView(t);
    }

    private EditText field(String label, String value, String hint, int inputType, final Saver saver) {
        TextView l = Ui.text(this, label, .034f, Ui.DIM);
        l.setPadding(0, px(.025f), 0, px(.01f));
        content.addView(l);
        final EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setText(value);
        e.setHint(hint);
        e.setInputType(inputType);
        e.setTypeface(Ui.regular());
        e.setTextColor(Ui.INK);
        e.setHintTextColor(Ui.FAINT);
        e.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.base(this) * .042f);
        e.setBackground(Ui.round(Ui.TILE, px(.03f)));
        e.setPadding(px(.035f), px(.025f), px(.035f), px(.025f));
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        final String[] last = {value};
        Runnable commit = new Runnable() {
            public void run() {
                String v = e.getText().toString().trim();
                if (!v.equals(last[0])) {
                    last[0] = v;
                    saver.save(v);
                    Toast.makeText(SettingsActivity.this, "Uloženo", Toast.LENGTH_SHORT).show();
                }
            }
        };
        final Runnable fc = commit;
        e.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                if (!has) fc.run();
            }
        });
        e.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int id, android.view.KeyEvent ev) {
                fc.run();
                e.clearFocus();
                android.view.inputmethod.InputMethodManager im = (android.view.inputmethod.InputMethodManager)
                        getSystemService(INPUT_METHOD_SERVICE);
                im.hideSoftInputFromWindow(e.getWindowToken(), 0);
                return true;
            }
        });
        content.addView(e, new LinearLayout.LayoutParams(-1, -2));
        return e;
    }

    private void toggle(String label, final String key, boolean def, final Runnable after) {
        LinearLayout row = Ui.box(this, false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.tile(this, Ui.TILE, .035f));
        row.setPadding(px(.035f), 0, px(.02f), 0);
        TextView t = Ui.text(this, label, .038f, Ui.INK);
        row.addView(t, Ui.lp(0, -2, 1f));
        final TextView pill = Ui.text(this, "", .034f, Ui.INK);
        pill.setTypeface(Ui.medium());
        pill.setGravity(Gravity.CENTER);
        row.addView(pill, new LinearLayout.LayoutParams(px(.15f), px(.075f)));
        final Runnable paint = new Runnable() {
            public void run() {
                boolean on = Prefs.get(SettingsActivity.this).getBoolean(key, false);
                pill.setText(on ? "ZAP" : "VYP");
                pill.setTextColor(on ? Ui.ON_AMBER : Ui.DIM);
                pill.setBackground(Ui.round(on ? Ui.AMBER : Ui.TILE_PRESSED, px(.04f)));
            }
        };
        if (!Prefs.get(this).contains(key)) Prefs.get(this).edit().putBoolean(key, def).apply();
        paint.run();
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.get(SettingsActivity.this).edit()
                        .putBoolean(key, !Prefs.get(SettingsActivity.this).getBoolean(key, false)).apply();
                paint.run();
                if (after != null) after.run();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(.12f));
        lp.topMargin = px(.02f);
        content.addView(row, lp);
    }

    private void action(String label, int color, final Runnable r) {
        TextView t = Ui.text(this, label, .04f, color == Ui.AMBER ? Ui.ON_AMBER : Ui.INK);
        t.setTypeface(Ui.medium());
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.tile(this, color, .035f));
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                View f = getCurrentFocus();
                if (f != null) f.clearFocus();
                r.run();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(.11f));
        lp.topMargin = px(.025f);
        content.addView(t, lp);
    }

    private void liveText(final Runnable update) {
        live = Ui.text(this, "", .03f, Ui.DIM);
        live.setPadding(0, px(.03f), 0, 0);
        content.addView(live);
        h.post(new Runnable() {
            public void run() {
                if (live == null) return;
                update.run();
                h.postDelayed(this, 400);
            }
        });
    }

    // ------------------------------------------------------------------ sekce
    // ------------------------------------------------------------------ standby
    private void standby() {
        heading("Vzhled standby");
        final boolean random = Prefs.get(this).getBoolean("standby_random", false);
        final int sel = Prefs.get(this).getInt("standby_style", StandbyFace.CLASSIC);
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int sw = Math.max(dm.widthPixels, dm.heightPixels), sh = Math.min(dm.widthPixels, dm.heightPixels);
        int gap = px(.025f);
        int tileW = (sw - 2 * px(.0625f) - px(.46f) - px(.045f) - gap) / 2;
        int prevH = tileW * sh / sw;
        LinearLayout row = null;
        for (int i = 0; i < StandbyFace.COUNT; i++) {
            final int idx = i;
            if (i % 2 == 0) {
                row = Ui.box(this, false);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                rl.topMargin = px(i == 0 ? .01f : .025f);
                content.addView(row, rl);
            }
            boolean on = !random && i == sel;
            LinearLayout tile = Ui.box(this, true);
            android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(Ui.BG_STANDBY);
            bg.setCornerRadius(px(.03f));
            bg.setStroke(Math.max(2, px(on ? .008f : .003f)), on ? Ui.AMBER : Ui.LINE);
            frame.setBackground(bg);
            frame.setClipToOutline(true);
            int pad = px(.008f);
            frame.setPadding(pad, pad, pad, pad);
            StandbyFace face = new StandbyFace(this);
            face.setStyle(i);
            face.setPreview(true);
            face.setAlarm("6:30", "· za 7 h");
            frame.addView(face, new android.widget.FrameLayout.LayoutParams(-1, -1));
            tile.addView(frame, new LinearLayout.LayoutParams(tileW, prevH));
            TextView name = Ui.text(this, StandbyFace.NAMES[i], .036f, on ? Ui.AMBER : Ui.SOFT);
            if (on) name.setTypeface(Ui.medium());
            name.setPadding(px(.01f), px(.012f), 0, 0);
            tile.addView(name);
            tile.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Prefs.get(SettingsActivity.this).edit().putInt("standby_style", idx)
                            .putBoolean("standby_random", false).apply();
                    render();
                }
            });
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(tileW, -2);
            if (i % 2 == 1) tl.leftMargin = gap;
            row.addView(tile, tl);
        }
        toggle("Každý den jiný vzhled", "standby_random", false, new Runnable() {
            public void run() {
                render();
            }
        });
        action("Ukázat standby teď", Ui.AMBER, new Runnable() {
            public void run() {
                startActivity(new Intent(SettingsActivity.this, MainActivity.class)
                        .putExtra("standby", true)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                finish();
            }
        });
        note("Když něco hraje, standby místo hodin ukazuje přehrávač (obal, název, ovládání).");
    }

    private void weather() {
        heading("Počasí");
        field("Místo", Prefs.weatherName(this), "Praha", InputType.TYPE_CLASS_TEXT, new Saver() {
            public void save(String v) {
                if (v.length() == 0) return;
                Prefs.get(SettingsActivity.this).edit().putString("w_name", v).putString("w_query", v)
                        .putLong("weather_ts", 0).remove("weather_wttr").apply();
            }
        });
        note("Obec, město nebo souřadnice „šířka,délka“ (např. 50.088,14.421). Data z wttr.in, obnova každých 15 min.");
    }

    private void printer() {
        heading("Tiskárna");
        field("Adresa thermoprint-serveru", Prefs.printerUrl(this), Prefs.DEFAULT_PRINTER,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, new Saver() {
                    public void save(String v) {
                        if (v.length() == 0) v = Prefs.DEFAULT_PRINTER;
                        if (!v.startsWith("http")) v = "http://" + v;
                        Prefs.get(SettingsActivity.this).edit().putString("printer", v).apply();
                    }
                });
        field("API token", Prefs.apiToken(this), "z thermoprintu → Nastavení → API",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, new Saver() {
                    public void save(String v) {
                        Prefs.get(SettingsActivity.this).edit().putString("api_token", v).apply();
                    }
                });
        action("🖨  Vyzkoušet ranní tisk", Ui.TILE, new Runnable() {
            public void run() {
                if (Prefs.printMorning(SettingsActivity.this, true)) {
                    Toast.makeText(SettingsActivity.this, "Posílám…", Toast.LENGTH_SHORT).show();
                }
            }
        });
        note("Ranní tisk po vypnutí budíku se zapíná u každého budíku zvlášť.");
    }

    private void light() {
        heading("Světlo");
        final EditText ip = field("IP žárovky (Yeelight / Mi LED)", Yeelight.ip(this), "najde se automaticky",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, new Saver() {
                    public void save(String v) {
                        Prefs.get(SettingsActivity.this).edit().putString("bulb_ip", v).apply();
                        Alarms.schedule(SettingsActivity.this);
                    }
                });
        action("Najít žárovku v síti", Ui.TILE, new Runnable() {
            public void run() {
                Toast.makeText(SettingsActivity.this, "Hledám…", Toast.LENGTH_SHORT).show();
                Yeelight.discover(SettingsActivity.this, new Yeelight.Done() {
                    public void done(boolean ok, JSONArray r) {
                        Toast.makeText(SettingsActivity.this, ok ? "Nalezena: " + Yeelight.ip(SettingsActivity.this)
                                : "Nenalezena – je zapnuté LAN Control?", Toast.LENGTH_SHORT).show();
                        if (ok) {
                            ip.setText(Yeelight.ip(SettingsActivity.this));
                            Alarms.schedule(SettingsActivity.this);
                        }
                    }
                });
            }
        });
        Runnable resched = new Runnable() {
            public void run() {
                Alarms.schedule(SettingsActivity.this);
            }
        };
        toggle("Svítání 10 min před budíkem", "bulb_sunrise", true, resched);
        toggle("Po vypnutí budíku naplno", "bulb_full_on_dismiss", true, null);
        action("Otevřít ovládání světla  ›", Ui.TILE, new Runnable() {
            public void run() {
                startActivity(new Intent(SettingsActivity.this, LightActivity.class));
            }
        });
    }

    private void mic() {
        heading("Mikrofon");
        toggle("Tlesknutí 2× = pauza / přehrát", "clap", false, new Runnable() {
            public void run() {
                if (Prefs.clapEnabled(SettingsActivity.this) && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 7);
                }
            }
        });
        note("Zvuk se nikam neukládá ani neposílá – hodiny sledují jen hlasitost. Funguje na ploše a ve standby.");
        liveText(new Runnable() {
            public void run() {
                live.setText("mikrofon teď: špička " + ClapDetector.lastPeak + " · pozadí " + ClapDetector.lastBg
                        + "\n(tlesknutí musí mít špičku nad 5500)");
            }
        });
    }

    private void system() {
        heading("Systém");
        action("⟳  Zkontrolovat aktualizaci", Ui.AMBER, new Runnable() {
            public void run() {
                Updater.check(SettingsActivity.this);
            }
        });
        action("Nastavení Androidu  ›", Ui.TILE, new Runnable() {
            public void run() {
                startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
            }
        });
        liveText(new Runnable() {
            public void run() {
                live.setText(App.status(SettingsActivity.this) + "\n\n" + MainActivity.sensorDebug()
                        + "\nsvětlo teď: " + MainActivity.luxNow + " lx");
            }
        });
    }
}
