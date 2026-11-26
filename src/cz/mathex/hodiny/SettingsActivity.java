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
    private static final String[] SECTIONS = {"Vzhled", "Budík", "Spotify", "Počasí", "Tiskárna", "Světlo", "Mikrofon", "Systém"};
    private static final int[] ICONS = {R.drawable.w_moon, R.drawable.ic_alarm, R.drawable.ic_music, R.drawable.w_partly, R.drawable.ic_printer, R.drawable.ic_bulb,
            R.drawable.ic_music, R.drawable.ic_settings};

    private final Handler h = new Handler(Looper.getMainLooper());
    private LinearLayout nav, content;
    private ScrollView sv;
    private int renderedSection = -1;
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

        sv = new ScrollView(this);
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
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(.084f));
            if (i > 0) lp.topMargin = px(.005f);
            nav.addView(item, lp);
        }
        final int keepY = section == renderedSection ? sv.getScrollY() : 0;
        renderedSection = section;
        content.removeAllViews();
        sv.post(new Runnable() {
            public void run() {
                sv.scrollTo(0, keepY);
            }
        });
        switch (section) {
            case 0: standby(); break;
            case 1: alarm(); break;
            case 2: spotify(); break;
            case 3: weather(); break;
            case 4: printer(); break;
            case 5: light(); break;
            case 6: mic(); break;
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
    // ------------------------------------------------------------------ vzhled
    interface Pick {
        void pick(int i);
    }

    /** Řada „pilulek“ s jednou vybranou volbou (zalomí se po 3). */
    private void choices(String[] names, int sel, final Pick pick) {
        LinearLayout row = null;
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            if (i % 3 == 0) {
                row = Ui.box(this, false);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, px(.1f));
                rl.topMargin = px(.015f);
                content.addView(row, rl);
            }
            boolean on = i == sel;
            TextView t = Ui.text(this, names[i], .036f, on ? Ui.ON_AMBER : Ui.SOFT);
            if (on) t.setTypeface(Ui.medium());
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setBackground(Ui.tile(this, on ? Ui.AMBER : Ui.TILE, .035f));
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    pick.pick(idx);
                }
            });
            LinearLayout.LayoutParams lp = Ui.lp(0, -1, 1f);
            if (i % 3 != 0) lp.leftMargin = px(.015f);
            row.addView(t, lp);
        }
        for (int i = names.length % 3; i != 0 && i < 3; i++) { // dorovnat řádek
            LinearLayout.LayoutParams lp = Ui.lp(0, -1, 1f);
            lp.leftMargin = px(.015f);
            row.addView(new View(this), lp);
        }
    }

    private void standby() {
        final android.content.SharedPreferences sp = Prefs.get(this);
        heading("Barevná paleta");
        choices(Ui.PALETTES, sp.getInt("palette", 0), new Pick() {
            public void pick(int i) {
                sp.edit().putInt("palette", i).apply();
                Ui.loadFonts(SettingsActivity.this);
                recreate();
            }
        });

        heading("Vzhled standby");
        note("Klepnutím vybereš, podržením označíš ★ oblíbené – mezi nimi se pak střídá.");
        final int rot = StandbyFace.rotation(this);
        final int sel = sp.getInt("standby_style", StandbyFace.CLASSIC);
        final int favs = StandbyFace.favs(this);
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
                rl.topMargin = px(i == 0 ? .02f : .025f);
                content.addView(row, rl);
            }
            boolean on = rot == 0 && i == sel;
            boolean fav = (favs & (1 << i)) != 0;
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
            TextView name = Ui.text(this, (fav ? "★ " : "") + StandbyFace.NAMES[i], .036f,
                    on ? Ui.AMBER : fav ? Ui.INK : Ui.SOFT);
            if (on || fav) name.setTypeface(Ui.medium());
            name.setPadding(px(.01f), px(.012f), 0, 0);
            tile.addView(name);
            tile.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    sp.edit().putInt("standby_style", idx).putInt("standby_rotate", 0)
                            .putBoolean("standby_random", false).apply();
                    render();
                }
            });
            tile.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    sp.edit().putInt("standby_favs", StandbyFace.favs(SettingsActivity.this) ^ (1 << idx)).apply();
                    render();
                    return true;
                }
            });
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(tileW, -2);
            if (i % 2 == 1) tl.leftMargin = gap;
            row.addView(tile, tl);
        }

        heading("Střídání vzhledů");
        choices(new String[]{"Ne", "Každý den", "Každou hodinu"}, rot, new Pick() {
            public void pick(int i) {
                sp.edit().putInt("standby_rotate", i).putBoolean("standby_random", false).apply();
                render();
            }
        });
        note(favs == 0 ? "Střídají se všechny vzhledy (žádný není ★)." : "Střídají se jen ★ oblíbené.");

        heading("Barvy");
        toggle("Barvy podle žárovky", "standby_ambient", false, null);
        note("Pozadí standby se jemně zabarví podle žárovky a Láva, Bauhaus, Memphis a Pop art převezmou její barvu. Při hudbě má přednost barva obalu.");
        toggle("Standby v barvě obalu alba", "art_color", true, null);

        heading("Pozadí plochy");
        choices(PatternView.NAMES, sp.getInt("home_pattern", 0), new Pick() {
            public void pick(int i) {
                sp.edit().putInt("home_pattern", i).apply();
                render();
            }
        });

        heading("Zvuky rozhraní");
        toggle("Jemné zvuky klepnutí", "ui_sounds", true, null);
        toggle("Zvuky i v noci (22–7)", "ui_sounds_night", false, null);

        action("Ukázat standby teď", Ui.AMBER, new Runnable() {
            public void run() {
                startActivity(new Intent(SettingsActivity.this, MainActivity.class)
                        .putExtra("standby", true)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                finish();
            }
        });
        note("Na standby: podržením otevřeš rychlé menu (vzhled, světlo, budík za 8 h, rádio).");
    }

    // ------------------------------------------------------------------ budík
    private void alarm() {
        final android.content.SharedPreferences sp = Prefs.get(this);
        heading("Po vypnutí budíku");
        toggle("Pustit rádio", "alarm_radio", false, new Runnable() {
            public void run() {
                render();
            }
        });
        org.json.JSONArray fav;
        try {
            fav = new JSONArray(getSharedPreferences("radio", MODE_PRIVATE).getString("fav", "[]"));
        } catch (Exception e) {
            fav = new JSONArray();
        }
        if (fav.length() == 0) {
            note("Nejdřív si v Rádiu označ ★ oblíbené stanice – tady pak vybereš, která hraje po budíku.");
            action("Otevřít rádio", Ui.TILE, new Runnable() {
                public void run() {
                    startActivity(new Intent(SettingsActivity.this, RadioActivity.class));
                }
            });
        } else {
            String cur = sp.getString("alarm_radio_url", "");
            if (cur.length() == 0) { // výchozí = první oblíbená
                org.json.JSONObject o = fav.optJSONObject(0);
                sp.edit().putString("alarm_radio_url", o.optString("url")).putString("alarm_radio_name", o.optString("name"))
                        .putString("alarm_radio_favicon", o.optString("favicon", "")).apply();
                cur = o.optString("url");
            }
            TextView l = Ui.text(this, "Stanice", .034f, Ui.DIM);
            l.setPadding(0, px(.03f), 0, 0);
            content.addView(l);
            String[] names = new String[fav.length()];
            int sel = -1;
            for (int i = 0; i < fav.length(); i++) {
                org.json.JSONObject o = fav.optJSONObject(i);
                names[i] = o.optString("name").trim();
                if (o.optString("url").equals(cur)) sel = i;
            }
            final JSONArray ff = fav;
            choices(names, sel, new Pick() {
                public void pick(int i) {
                    org.json.JSONObject o = ff.optJSONObject(i);
                    sp.edit().putString("alarm_radio_url", o.optString("url")).putString("alarm_radio_name", o.optString("name"))
                            .putString("alarm_radio_favicon", o.optString("favicon", "")).putBoolean("alarm_radio", true).apply();
                    render();
                }
            });
            note("Hraje hned po stisku Vypnout (a po ranním tisku). Odložení rádio nepouští.");
        }
        heading("Vyzkoušet");
        action("Zkušební budík za 1 minutu", Ui.AMBER, new Runnable() {
            public void run() {
                Alarms.test(SettingsActivity.this, 60);
                Toast.makeText(SettingsActivity.this, "Budík zazvoní za minutu", Toast.LENGTH_LONG).show();
            }
        });
        action("Nastavit budíky", Ui.TILE, new Runnable() {
            public void run() {
                startActivity(new Intent(SettingsActivity.this, AlarmsActivity.class));
            }
        });
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
        toggle("Tleskání: 2× světlo, 3× pauza / přehrát", "clap", false, new Runnable() {
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

    // ------------------------------------------------------------------ spotify connect
    private void spotify() {
        final android.content.SharedPreferences sp = Prefs.get(this);
        heading("Spotify Connect");
        if (!SpotifyService.available(this)) {
            note("V této verzi aplikace chybí přijímač Spotify Connect.");
            return;
        }
        toggle("Hodiny jako Spotify reproduktor", "spotify_connect", false, new Runnable() {
            public void run() {
                SpotifyService.sync(SettingsActivity.this);
                render();
            }
        });
        note("Hudbu pak pouštíš z aplikace Spotify v mobilu nebo PC: ikona zařízení → „"
                + sp.getString("spotify_name", "Hodiny") + "“. Musí být ve stejné Wi-Fi a potřebuješ Spotify Premium. "
                + "Po prvním připojení jde hudbu pouštět i přímo z hodin – tlačítko ♪ v doku otevře tvoje playlisty, alba a interprety.");
        field("Název zařízení", sp.getString("spotify_name", "Hodiny"), "Hodiny",
                android.text.InputType.TYPE_CLASS_TEXT, new Saver() {
                    public void save(String v) {
                        sp.edit().putString("spotify_name", v.trim().length() == 0 ? "Hodiny" : v.trim()).apply();
                        SpotifyService.restart(SettingsActivity.this);
                    }
                });
        TextView l = Ui.text(this, "Kvalita", .034f, Ui.DIM);
        l.setPadding(0, px(.03f), 0, 0);
        content.addView(l);
        final int[] rates = {96, 160, 320};
        int cur = sp.getInt("spotify_bitrate", 160), sel = 1;
        for (int i = 0; i < 3; i++) if (rates[i] == cur) sel = i;
        choices(new String[]{"Úsporná", "Normální", "Vysoká"}, sel, new Pick() {
            public void pick(int i) {
                sp.edit().putInt("spotify_bitrate", rates[i]).apply();
                SpotifyService.restart(SettingsActivity.this);
                render();
            }
        });
        if (SpotifyService.enabled(this)) {
            action("Restartovat přijímač", Ui.TILE, new Runnable() {
                public void run() {
                    SpotifyService.restart(SettingsActivity.this);
                    Toast.makeText(SettingsActivity.this, "Restartuji…", Toast.LENGTH_SHORT).show();
                }
            });
            action("Odhlásit účet", Ui.TILE, new Runnable() {
                public void run() {
                    SpotifyService.logout(SettingsActivity.this);
                    Toast.makeText(SettingsActivity.this, "Odhlášeno – připoj se znovu z mobilu", Toast.LENGTH_SHORT).show();
                }
            });
            liveText(new Runnable() {
                public void run() {
                    live.setText("stav: " + SpotifyService.status + "\n\n" + SpotifyService.logTail(12));
                }
            });
        }
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
