package cz.mathex.hodiny;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Nativní ovládání termotiskárny přes API thermoprint-serveru (Bearer token z nastavení). */
public class PrinterActivity extends BaseActivity {
    private static final String[][] BUTTONS = {
            {"morning", "☀️", "Ranní zpráva"}, {"todo", "✅", "Todo"}, {"shopping", "🛒", "Nákup"},
            {"status", "🌐", "Stav webů"}, {"moodle", "🎓", "Moodle"}, {"planes", "✈️", "Letadla"},
            {"xkcd_random", "🤓", "xkcd"}, {"test", "🧾", "Test tisku"},
    };

    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView status;

    /** Výsledek HTTP volání: kód (-1 = síť) a tělo. */
    static final class Res {
        int code = -1;
        String body = "";
    }

    static Res call(Activity a, String method, String path) {
        Res r = new Res();
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(Prefs.printerBase(a) + path).openConnection();
            con.setRequestMethod(method);
            con.setConnectTimeout(6000);
            con.setReadTimeout(30000);
            con.setRequestProperty("Authorization", "Bearer " + Prefs.apiToken(a));
            if ("POST".equals(method)) {
                con.setRequestProperty("Content-Type", "application/json");
                con.setDoOutput(true);
                OutputStream os = con.getOutputStream();
                os.write("{}".getBytes("UTF-8"));
                os.close();
            }
            r.code = con.getResponseCode();
            InputStream in = r.code < 400 ? con.getInputStream() : con.getErrorStream();
            if (in != null) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                r.body = out.toString("UTF-8");
            }
            con.disconnect();
        } catch (Exception ignored) {
        }
        return r;
    }

    static String explain(Res r) {
        if (r.code == -1) return "Server tiskárny nedostupný";
        if (r.code == 401) return "Špatný API token (⚙ Nastavení)";
        try {
            String m = new JSONObject(r.body).optString("message", "");
            if (m.length() > 0) return m;
        } catch (Exception ignored) {
        }
        return r.code < 300 ? "Odesláno do tiskárny" : "Chyba serveru " + r.code;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = Ui.box(this, true);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.bar(this, "Tiskárna", "⟳", new View.OnClickListener() {
            public void onClick(View v) {
                loadStatus();
            }
        }));

        int pad = Ui.px(this, .045f);
        LinearLayout body = Ui.box(this, true);
        body.setPadding(pad, 0, pad, pad);
        root.addView(body, Ui.lp(-1, 0, 1f));

        status = Ui.text(this, "načítám stav…", .042f, Ui.DIM);
        status.setSingleLine(true);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        body.addView(status, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout grid = Ui.box(this, true);
        LinearLayout.LayoutParams gp = Ui.lp(-1, 0, 1f);
        gp.topMargin = Ui.px(this, .03f);
        body.addView(grid, gp);
        for (int row = 0; row < 2; row++) {
            LinearLayout r = Ui.box(this, false);
            LinearLayout.LayoutParams rp = Ui.lp(-1, 0, 1f);
            if (row > 0) rp.topMargin = Ui.px(this, .03f);
            grid.addView(r, rp);
            for (int col = 0; col < 4; col++) addButton(r, BUTTONS[row * 4 + col]);
        }
        setContentView(root);

        if (Prefs.apiToken(this).length() == 0) {
            status.setText("Chybí API token – podrž počasí na ploše → Nastavení");
            status.setTextColor(Ui.BAD);
        } else {
            loadStatus();
        }
    }

    private void addButton(LinearLayout row, final String[] def) {
        final LinearLayout t = Ui.box(this, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.tile(this));
        t.addView(Ui.text(this, def[1], .085f, Ui.INK));
        final TextView label = Ui.text(this, def[2], .042f, Ui.INK);
        t.addView(label, Ui.top(this, .02f));
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!t.isEnabled()) return;
                t.setEnabled(false);
                t.setAlpha(.45f);
                print(def[0], t);
            }
        });
        LinearLayout.LayoutParams p = Ui.lp(0, -1, 1f);
        if (row.getChildCount() > 0) p.leftMargin = Ui.px(this, .03f);
        row.addView(t, p);
    }

    private void print(final String what, final View button) {
        new Thread(new Runnable() {
            public void run() {
                final Res r = call(PrinterActivity.this, "POST", "/api/print/" + what);
                h.post(new Runnable() {
                    public void run() {
                        Toast.makeText(PrinterActivity.this, explain(r), Toast.LENGTH_SHORT).show();
                        loadStatus();
                    }
                });
                h.postDelayed(new Runnable() { // proti dvojímu ťuknutí
                    public void run() {
                        button.setEnabled(true);
                        button.setAlpha(1f);
                    }
                }, 3000);
            }
        }).start();
    }

    private void loadStatus() {
        new Thread(new Runnable() {
            public void run() {
                final Res r = call(PrinterActivity.this, "GET", "/api/status");
                h.post(new Runnable() {
                    public void run() {
                        showStatus(r);
                    }
                });
            }
        }).start();
    }

    private void showStatus(Res r) {
        if (r.code != 200) {
            status.setText(explain(r));
            status.setTextColor(Ui.BAD);
            return;
        }
        try {
            JSONObject o = new JSONObject(r.body);
            StringBuilder sb = new StringBuilder();
            JSONObject agent = o.optJSONObject("agent");
            Object online = agent != null ? agent.opt("online") : null;
            sb.append(Boolean.TRUE.equals(online) ? "🟢 tiskárna ok" : Boolean.FALSE.equals(online) ? "🔴 tiskárna offline" : "⚪ tiskárna ?");
            int pending = o.optInt("pending", 0);
            if (pending > 0) sb.append("   ·   fronta ").append(pending);
            JSONArray sites = o.optJSONArray("sites");
            StringBuilder down = new StringBuilder();
            if (sites != null) {
                for (int i = 0; i < sites.length(); i++) {
                    JSONObject s = sites.getJSONObject(i);
                    if ("down".equals(s.optString("state"))) {
                        if (down.length() > 0) down.append(", ");
                        down.append(s.optString("name"));
                    }
                }
            }
            sb.append("   ·   ").append(down.length() > 0 ? "🔴 dole: " + down : "🟢 weby ok");
            status.setText(sb.toString());
            status.setTextColor(down.length() > 0 || Boolean.FALSE.equals(online) ? Ui.BAD : Ui.DIM);
        } catch (Exception e) {
            status.setText("Stav: neznámá odpověď serveru");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
    }
}
