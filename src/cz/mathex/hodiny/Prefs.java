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
import java.net.HttpURLConnection;
import java.net.URL;

/** Nastavení (město pro počasí, adresa tiskárny) a HTTP helper. */
final class Prefs {
    static final String DEFAULT_PRINTER = "http://192.168.0.101:8630/clock";

    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences("hodiny", Context.MODE_PRIVATE);
    }

    /** Název místa zobrazený u počasí. */
    static String weatherName(Context c) {
        return get(c).getString("w_name", "Škvorec");
    }

    /** Dotaz pro wttr.in – výchozí jsou souřadnice Škvorce, ať se netrefí jiná obec stejného jména. */
    static String weatherQuery(Context c) {
        return get(c).getString("w_query", "50.047,14.730");
    }

    static String printerUrl(Context c) {
        return get(c).getString("printer", DEFAULT_PRINTER);
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

        box.addView(label(a, "Adresa tiskárny"));
        final EditText url = new EditText(a);
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setText(printerUrl(a));
        box.addView(url);

        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Nastavení hodin")
                .setView(box)
                .setNegativeButton("Zrušit", null)
                .setPositiveButton("Uložit", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String u = url.getText().toString().trim();
                        if (u.length() == 0) u = DEFAULT_PRINTER;
                        if (!u.startsWith("http")) u = "http://" + u;
                        get(a).edit().putString("printer", u).apply();
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
