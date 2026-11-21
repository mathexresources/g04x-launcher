package cz.mathex.hodiny;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;

/** Internetová rádia z radio-browser.info: ★ oblíbené nahoře, pod nimi nejposlouchanější české stanice, hledání. */
public class RadioActivity extends Activity {
    private static final String[] SERVERS = {"de1", "nl1", "at1", "de2", "fi1"};

    private final Handler h = new Handler(Looper.getMainLooper());
    private LinearLayout list;
    private JSONArray results = new JSONArray();
    private String heading = "Nejposlouchanější v Česku";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = Ui.box(this, true);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.bar(this, "Rádio", "⏹  Stop", new View.OnClickListener() {
            public void onClick(View v) {
                RadioService.stop(RadioActivity.this);
                h.postDelayed(new Runnable() {
                    public void run() {
                        render();
                    }
                }, 300);
            }
        }));

        int pad = Ui.px(this, .04f);
        final EditText search = new EditText(this);
        search.setHint("🔍  hledat stanici (např. lofi, Evropa 2, jazz)…");
        search.setSingleLine(true);
        search.setTextColor(Ui.INK);
        search.setHintTextColor(Ui.DIM);
        search.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.base(this) * .045f);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int id, KeyEvent e) {
                String q = v.getText().toString().trim();
                if (q.length() > 0) load("name=" + enc(q) + "&limit=40", "Výsledky: " + q);
                else load(null, null);
                return true;
            }
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.leftMargin = sp.rightMargin = pad;
        root.addView(search, sp);

        ScrollView sv = new ScrollView(this);
        list = Ui.box(this, true);
        list.setPadding(pad, 0, pad, pad);
        sv.addView(list);
        root.addView(sv, Ui.lp(-1, 0, 1f));
        setContentView(root);

        render();
        load(null, null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
        render();
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private SharedPreferences sp() {
        return getSharedPreferences("radio", MODE_PRIVATE);
    }

    private JSONArray favorites() {
        try {
            return new JSONArray(sp().getString("fav", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private boolean isFav(String url) {
        JSONArray f = favorites();
        for (int i = 0; i < f.length(); i++) if (f.optJSONObject(i).optString("url").equals(url)) return true;
        return false;
    }

    private void toggleFav(String name, String url) {
        JSONArray f = favorites(), out = new JSONArray();
        boolean removed = false;
        for (int i = 0; i < f.length(); i++) {
            JSONObject o = f.optJSONObject(i);
            if (o.optString("url").equals(url)) removed = true;
            else out.put(o);
        }
        if (!removed) {
            try {
                out.put(new JSONObject().put("name", name).put("url", url));
            } catch (Exception ignored) {
            }
        }
        sp().edit().putString("fav", out.toString()).apply();
        render();
    }

    /** query == null → nejposlouchanější české stanice. */
    private void load(final String query, final String title) {
        final String q = query != null ? query
                : "countrycode=CZ&order=clickcount&reverse=true&limit=40";
        heading = title != null ? title : "Nejposlouchanější v Česku";
        new Thread(new Runnable() {
            public void run() {
                JSONArray res = null;
                for (String s : SERVERS) {
                    try {
                        String url = "https://" + s + ".api.radio-browser.info/json/stations/search?hidebroken=true&" + q
                                + (q.contains("order=") ? "" : "&order=clickcount&reverse=true");
                        res = new JSONArray(Prefs.http(url));
                        break;
                    } catch (Exception ignored) {
                    }
                }
                final JSONArray r = res;
                h.post(new Runnable() {
                    public void run() {
                        if (r == null) {
                            Toast.makeText(RadioActivity.this, "Seznam stanic se nepodařilo načíst", Toast.LENGTH_LONG).show();
                            return;
                        }
                        results = r;
                        render();
                    }
                });
            }
        }).start();
    }

    private void render() {
        list.removeAllViews();
        JSONArray fav = favorites();
        if (fav.length() > 0) {
            section("★  Oblíbené");
            for (int i = 0; i < fav.length(); i++) {
                JSONObject o = fav.optJSONObject(i);
                row(o.optString("name"), o.optString("url"), "");
            }
        }
        section(heading);
        if (results.length() == 0) {
            TextView t = Ui.text(this, "načítám…", .042f, Ui.DIM);
            t.setPadding(0, Ui.px(this, .02f), 0, 0);
            list.addView(t);
        }
        for (int i = 0; i < results.length(); i++) {
            JSONObject o = results.optJSONObject(i);
            String url = o.optString("url_resolved", o.optString("url"));
            if (url.length() == 0) continue;
            String sub = o.optString("codec", "") + (o.optInt("bitrate", 0) > 0 ? " " + o.optInt("bitrate") + " kb/s" : "");
            String tags = o.optString("tags", "");
            if (tags.length() > 0) sub += "  ·  " + tags.replace(",", ", ");
            row(o.optString("name").trim(), url, sub);
        }
    }

    private void section(String title) {
        TextView t = Ui.text(this, title, .04f, Ui.DIM);
        t.setPadding(0, Ui.px(this, .035f), 0, Ui.px(this, .01f));
        list.addView(t);
    }

    private void row(final String name, final String url, String sub) {
        boolean playing = url.equals(RadioService.nowUrl);
        LinearLayout r = Ui.box(this, false);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackground(Ui.tile(this));
        int p = Ui.px(this, .035f);
        r.setPadding(p, p / 2, p / 3, p / 2);
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                RadioService.play(RadioActivity.this, name, url);
                h.postDelayed(new Runnable() {
                    public void run() {
                        render();
                    }
                }, 300);
            }
        });

        LinearLayout txt = Ui.box(this, true);
        TextView n = Ui.text(this, (playing ? "▶  " : "") + name, .048f, playing ? Ui.ACCENT : Ui.INK);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        txt.addView(n);
        if (sub.length() > 0) {
            TextView s = Ui.text(this, sub, .034f, Ui.DIM);
            s.setSingleLine(true);
            s.setEllipsize(TextUtils.TruncateAt.END);
            txt.addView(s, Ui.top(this, .008f));
        }
        r.addView(txt, Ui.lp(0, -2, 1f));

        boolean fav = isFav(url);
        TextView star = Ui.text(this, fav ? "★" : "☆", .07f, fav ? Ui.ACCENT : Ui.DIM);
        star.setGravity(Gravity.CENTER);
        star.setBackground(Ui.press(this));
        star.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toggleFav(name, url);
            }
        });
        r.addView(star, new LinearLayout.LayoutParams(Ui.px(this, .14f), Ui.px(this, .12f)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.px(this, .02f);
        list.addView(r, lp);
    }
}
