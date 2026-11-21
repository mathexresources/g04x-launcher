package cz.mathex.hodiny;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
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

/**
 * Internetová rádia z radio-browser.info: ★ oblíbené jako velké dlaždice (ťuknout = hrát, podržet = odebrat),
 * pod nimi nejposlouchanější české stanice a hledání.
 */
public class RadioActivity extends Activity {
    private static final String[] SERVERS = {"de1", "nl1", "at1", "de2", "fi1"};

    private final Handler h = new Handler(Looper.getMainLooper());
    private LinearLayout list;
    private JSONArray results = new JSONArray();
    private String heading = "NEJPOSLOUCHANĚJŠÍ V ČESKU";

    private int px(float f) {
        return Ui.px(this, f);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.loadFonts(this);
        LinearLayout root = Ui.box(this, true);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(px(.0625f), px(.0375f), px(.0625f), 0);

        // horní lišta: zpět · hledání · stop
        LinearLayout bar = Ui.box(this, false);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(Ui.back(this));

        LinearLayout field = Ui.box(this, false);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setBackground(Ui.round(Ui.TILE, px(.033f)));
        field.setPadding(px(.033f), 0, px(.02f), 0);
        field.addView(Ui.icon(this, R.drawable.ic_search, Ui.DIM, .05f));
        final EditText search = new EditText(this);
        search.setHint("hledat stanici…");
        search.setSingleLine(true);
        search.setBackground(null);
        search.setTypeface(Ui.regular());
        search.setTextColor(Ui.INK);
        search.setHintTextColor(Ui.FAINT);
        search.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.base(this) * .044f);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int id, KeyEvent e) {
                String q = v.getText().toString().trim();
                if (q.length() > 0) load("name=" + enc(q) + "&limit=40", "VÝSLEDKY: " + q.toUpperCase());
                else load(null, null);
                return true;
            }
        });
        field.addView(search, Ui.lp(0, -2, 1f));
        LinearLayout.LayoutParams fl = Ui.lp(0, px(.125f), 1f);
        fl.leftMargin = px(.02f);
        bar.addView(field, fl);

        LinearLayout stop = Ui.box(this, false);
        stop.setGravity(Gravity.CENTER_VERTICAL);
        stop.setBackground(Ui.tile(this, Ui.TILE, .033f));
        stop.setPadding(px(.033f), 0, px(.033f), 0);
        stop.addView(Ui.icon(this, R.drawable.ic_stop, Ui.INK, .042f));
        stop.addView(Ui.text(this, "Stop", .044f, Ui.INK), Ui.left(this, .02f));
        stop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                RadioService.stop(RadioActivity.this);
                later();
            }
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, px(.125f));
        sl.leftMargin = px(.025f);
        bar.addView(stop, sl);
        root.addView(bar, new LinearLayout.LayoutParams(-1, px(.14f)));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        list = Ui.box(this, true);
        list.setPadding(0, 0, 0, px(.046f));
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

    private VolumeOverlay volume;

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (volume == null) volume = new VolumeOverlay(this);
        if (volume.onKeyDown(code)) return true;
        return super.onKeyDown(code, e);
    }

    private void later() {
        h.postDelayed(new Runnable() {
            public void run() {
                render();
            }
        }, 350);
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
        Toast.makeText(this, removed ? "Odebráno z oblíbených" : "Přidáno do oblíbených", Toast.LENGTH_SHORT).show();
        render();
    }

    /** query == null → nejposlouchanější české stanice. */
    private void load(final String query, final String title) {
        final String q = query != null ? query : "countrycode=CZ&order=clickcount&reverse=true&limit=40";
        heading = title != null ? title : "NEJPOSLOUCHANĚJŠÍ V ČESKU";
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
            section("OBLÍBENÉ");
            LinearLayout row = null;
            for (int i = 0; i < fav.length(); i++) {
                if (i % 3 == 0) {
                    row = Ui.box(this, false);
                    LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, px(.27f));
                    if (i > 0) rl.topMargin = px(.025f);
                    list.addView(row, rl);
                }
                JSONObject o = fav.optJSONObject(i);
                favCard(row, o.optString("name"), o.optString("url"));
            }
            for (int i = fav.length() % 3; i != 0 && i < 3; i++) { // dorovnat poslední řádek
                View spacer = new View(this);
                LinearLayout.LayoutParams p = Ui.lp(0, -1, 1f);
                p.leftMargin = px(.025f);
                row.addView(spacer, p);
            }
        }
        section(heading);
        if (results.length() == 0) {
            list.addView(Ui.text(this, "načítám…", .045f, Ui.DIM));
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
        TextView t = Ui.text(this, title, .036f, Ui.DIM);
        t.setLetterSpacing(0.03f);
        t.setPadding(0, px(.04f), 0, px(.022f));
        list.addView(t);
    }

    private void favCard(LinearLayout row, final String name, final String url) {
        boolean playing = url.equals(RadioService.nowUrl);
        LinearLayout c = Ui.box(this, true);
        c.setBackground(Ui.tile(this, playing ? Ui.AMBER : Ui.TILE, .046f));
        c.setPadding(px(.04f), px(.04f), px(.04f), px(.035f));
        c.addView(Ui.icon(this, playing ? R.drawable.ic_pause : R.drawable.ic_play, playing ? Ui.ON_AMBER : Ui.TEAL, .062f));
        View fill = new View(this);
        c.addView(fill, Ui.lp(-1, 0, 1f));
        TextView n = Ui.text(this, name, .05f, playing ? Ui.ON_AMBER : Ui.INK);
        n.setTypeface(Ui.medium());
        n.setMaxLines(2);
        n.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(n);
        c.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (url.equals(RadioService.nowUrl)) RadioService.stop(RadioActivity.this);
                else RadioService.play(RadioActivity.this, name, url);
                later();
            }
        });
        c.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                toggleFav(name, url);
                return true;
            }
        });
        LinearLayout.LayoutParams p = Ui.lp(0, -1, 1f);
        if (row.getChildCount() > 0) p.leftMargin = px(.025f);
        row.addView(c, p);
    }

    private void row(final String name, final String url, String sub) {
        boolean playing = url.equals(RadioService.nowUrl);
        LinearLayout r = Ui.box(this, false);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackground(Ui.tile(this, Ui.TILE, .033f));
        r.setPadding(px(.042f), px(.03f), px(.015f), px(.03f));
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                RadioService.play(RadioActivity.this, name, url);
                later();
            }
        });

        LinearLayout txt = Ui.box(this, true);
        TextView n = Ui.text(this, name, .05f, playing ? Ui.AMBER : Ui.INK);
        n.setTypeface(Ui.medium());
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        txt.addView(n);
        if (sub.length() > 0) {
            TextView s = Ui.text(this, sub, .035f, Ui.DIM);
            s.setSingleLine(true);
            s.setEllipsize(TextUtils.TruncateAt.END);
            txt.addView(s, Ui.top(this, .012f));
        }
        r.addView(txt, Ui.lp(0, -2, 1f));

        boolean fav = isFav(url);
        LinearLayout star = Ui.box(this, false);
        star.setGravity(Gravity.CENTER);
        star.setBackground(Ui.press(this));
        star.addView(Ui.icon(this, fav ? R.drawable.ic_star_fill : R.drawable.ic_star, fav ? Ui.AMBER : Ui.DIM, .065f));
        star.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toggleFav(name, url);
            }
        });
        r.addView(star, new LinearLayout.LayoutParams(px(.14f), px(.14f)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = px(.022f);
        list.addView(r, lp);
    }
}
