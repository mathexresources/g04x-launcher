package cz.mathex.hodiny;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Nastavení (město pro počasí, adresa tiskárny) a HTTP helper. */
final class Prefs {
    static final String DEFAULT_PRINTER = "http://192.168.0.101:8630";

    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences("hodiny", Context.MODE_PRIVATE);
    }

    /** Název místa zobrazený u počasí. */
    static String weatherName(Context c) {
        return get(c).getString("w_name", "Praha");
    }

    /** Dotaz pro wttr.in – výchozí jsou souřadnice Prahy (centrum). */
    static String weatherQuery(Context c) {
        return get(c).getString("w_query", "50.088,14.421");
    }

    static String printerUrl(Context c) {
        return get(c).getString("printer", DEFAULT_PRINTER);
    }

    static String apiToken(Context c) {
        return get(c).getString("api_token", "");
    }

    static boolean clapEnabled(Context c) {
        return get(c).getBoolean("clap", false);
    }

    static boolean morningOnAlarm(Context c) {
        return get(c).getBoolean("morning_on_alarm", true);
    }

    /** http://host:port z adresy tiskárny (např. …/clock → http://192.168.0.101:8630). */
    static String printerBase(Context c) {
        try {
            URL u = new URL(printerUrl(c));
            return u.getProtocol() + "://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
        } catch (Exception e) {
            return "http://192.168.0.101:8630";
        }
    }

    /**
     * Vytiskne ranní zprávu přes API thermoprint-serveru. Bez force nejvýš jednou denně (po vypnutí budíku).
     * Za den se označí až po úspěšném odeslání. Výsledek ukáže jako toast.
     * Vrací false, když je funkce vypnutá nebo chybí token.
     */
    static boolean printMorning(final Context ctx, final boolean force) {
        final Context c = ctx.getApplicationContext();
        final String token = apiToken(c);
        if (token.length() == 0) {
            if (force) toast(c, "Chybí API token tiskárny (nastavení)");
            return false;
        }
        if (!force && !morningOnAlarm(c)) return false;
        final String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        if (!force && today.equals(get(c).getString("morning_printed", ""))) return false;
        final String url = printerBase(c) + "/api/print/morning";
        new Thread(new Runnable() {
            public void run() {
                String result = "Server tiskárny nedostupný (" + printerBase(c) + ")";
                for (int attempt = 0; attempt < 5; attempt++) { // Wi-Fi se po ránu může chvíli probouzet
                    int code = -1;
                    try {
                        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                        con.setRequestMethod("POST");
                        con.setConnectTimeout(8000);
                        con.setReadTimeout(30000);
                        con.setRequestProperty("Authorization", "Bearer " + token);
                        con.setRequestProperty("Content-Type", "application/json");
                        con.setDoOutput(true);
                        OutputStream os = con.getOutputStream();
                        os.write("{}".getBytes("UTF-8"));
                        os.close();
                        code = con.getResponseCode();
                        con.disconnect();
                    } catch (Exception ignored) {
                    }
                    if (code >= 200 && code < 300) {
                        get(c).edit().putString("morning_printed", today).apply();
                        result = "🖨  Ranní zpráva se tiskne";
                        break;
                    }
                    if (code == 401) { result = "Tiskárna: špatný API token"; break; }
                    if (code == 404) { result = "Server nezná /api/print/morning – je adresa tiskárny správně?"; break; }
                    if (code == 502) result = "Tiskárna: server nenačetl data pro ranní zprávu";
                    else if (code > 0) result = "Tiskárna: chyba serveru " + code;
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                toast(c, result);
            }
        }).start();
        return true;
    }

    static void toast(final Context c, final String msg) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                android.widget.Toast.makeText(c, msg, android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }

    static String http(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(10000);
        con.setReadTimeout(15000);
        con.setRequestProperty("User-Agent", "Hodiny/1.0");
        try {
            if (con.getResponseCode() != 200) throw new Exception("HTTP " + con.getResponseCode());
            InputStream in = con.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            con.disconnect();
        }
    }

    /** Dialog nastavení. Po uložení zavolá done (na UI vlákně). */
    static void showSettings(final Activity a, final Runnable done) {
        int pad = Ui.px(a, .05f);
        LinearLayout box = Ui.box(a, true);
        box.setPadding(pad, pad / 2, pad, 0);

        box.addView(label(a, "Místo pro počasí (obec nebo „šířka,délka“)"));
        final EditText city = new EditText(a);
        city.setSingleLine(true);
        city.setText(weatherName(a));
        box.addView(city);

        box.addView(label(a, "Adresa thermoprint-serveru"));
        final EditText url = new EditText(a);
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setText(printerUrl(a));
        box.addView(url);

        box.addView(label(a, "API token tiskárny (thermoprint → Nastavení → API)"));
        final EditText token = new EditText(a);
        token.setSingleLine(true);
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        token.setText(apiToken(a));
        box.addView(token);

        TextView test = new TextView(a);
        test.setText("🖨  Vyzkoušet ranní tisk (s tokenem výše)");
        test.setTextColor(Ui.ACCENT);
        test.setPadding(0, Ui.px(a, .03f), 0, Ui.px(a, .02f));
        test.setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(android.view.View v) {
                get(a).edit().putString("api_token", token.getText().toString().trim())
                        .putString("printer", url.getText().toString().trim().length() > 0
                                ? url.getText().toString().trim() : DEFAULT_PRINTER).apply();
                if (printMorning(a, true)) android.widget.Toast.makeText(a, "Posílám…", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        box.addView(test);

        box.addView(label(a, "IP žárovky Yeelight / Mi LED (prázdné = najít automaticky)"));
        final EditText bulb = new EditText(a);
        bulb.setSingleLine(true);
        bulb.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        bulb.setText(get(a).getString("bulb_ip", ""));
        box.addView(bulb);

        final android.widget.CheckBox clap = new android.widget.CheckBox(a);
        clap.setText("👏  Tlesknutí 2× = pauza / přehrát hudbu (mikrofon)");
        clap.setChecked(clapEnabled(a));
        box.addView(clap);

        final TextView sens = new TextView(a);
        sens.setTextColor(Ui.DIM);
        sens.setTextSize(12);
        sens.setPadding(0, Ui.px(a, .02f), 0, 0);
        box.addView(sens);
        final android.os.Handler hh = new android.os.Handler(android.os.Looper.getMainLooper());
        final boolean[] seen = {false};
        hh.post(new Runnable() {
            public void run() {
                if (sens.isAttachedToWindow()) seen[0] = true;
                else if (seen[0]) return; // dialog zavřený
                sens.setText(MainActivity.sensorDebug() + "\nsvětlo teď: " + MainActivity.luxNow + " lx\n" + App.status(a));
                hh.postDelayed(this, 300);
            }
        });

        android.widget.ScrollView sv = new android.widget.ScrollView(a);
        sv.addView(box);

        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Nastavení hodin · " + Updater.currentVersion(a))
                .setNeutralButton("⟳ Aktualizovat", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        Updater.check(a);
                    }
                })
                .setView(sv)
                .setNegativeButton("Zrušit", null)
                .setPositiveButton("Uložit", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String u = url.getText().toString().trim();
                        if (u.length() == 0) u = DEFAULT_PRINTER;
                        if (!u.startsWith("http")) u = "http://" + u;
                        get(a).edit().putString("printer", u)
                                .putString("api_token", token.getText().toString().trim())
                                .putBoolean("clap", clap.isChecked())
                                .putString("bulb_ip", bulb.getText().toString().trim()).apply();
                        Alarms.schedule(a);
                        if (clap.isChecked() && a.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            a.requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 7);
                        }
                        String c = city.getText().toString().trim();
                        if (c.length() > 0 && !c.equals(weatherName(a))) {
                            get(a).edit().putString("w_name", c).putString("w_query", c)
                                    .putLong("weather_ts", 0).remove("weather_wttr").apply();
                        }
                        if (done != null) done.run();
                    }
                })
                .show();
    }

    private static TextView label(Activity a, String s) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextColor(Ui.DIM);
        t.setPadding(0, Ui.px(a, .03f), 0, 0);
        return t;
    }
}
