package cz.mathex.hodiny;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Spuštění Spotify přímo z hodin: playlisty, alba a interpreti z knihovny (přes API přijímače Connect). */
public class SpotifyActivity extends BaseActivity {
    private static final String[] TABS = {"Playlisty", "Alba", "Interpreti"};
    private static final String[] PATHS = {"/library/playlists", "/library/albums", "/library/artists"};
    private static final LruCache<String, Bitmap> covers = new LruCache<String, Bitmap>(80);
    private static final ExecutorService pool = Executors.newFixedThreadPool(3);

    private final Handler h = new Handler(Looper.getMainLooper());
    private LinearLayout tabs, grid;
    private ScrollView sv;
    private TextView shuffleBtn;
    private int tab;
    private boolean shuffle;

    private int px(float f) {
        return Ui.px(this, f);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        shuffle = Prefs.get(this).getBoolean("spotify_shuffle", false);
        LinearLayout root = Ui.box(this, true);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(px(.0625f), px(.0375f), px(.0625f), 0);
        LinearLayout bar = Ui.bar(this, "Spotify", "", new View.OnClickListener() {
            public void onClick(View v) {
                shuffle = !shuffle;
                Prefs.get(SpotifyActivity.this).edit().putBoolean("spotify_shuffle", shuffle).apply();
                updateShuffle();
            }
        });
        shuffleBtn = (TextView) bar.getChildAt(bar.getChildCount() - 1);
        updateShuffle();
        root.addView(bar);

        tabs = Ui.box(this, false);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, px(.1f));
        tl.topMargin = px(.025f);
        root.addView(tabs, tl);

        sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        grid = Ui.box(this, true);
        grid.setPadding(0, px(.02f), 0, px(.05f));
        sv.addView(grid);
        root.addView(sv, Ui.lp(-1, 0, 1f));
        setContentView(root);
        tab = Prefs.get(this).getInt("spotify_tab", 0);
        renderTabs();
        load();
    }

    private void updateShuffle() {
        shuffleBtn.setText(shuffle ? "🔀  Náhodně" : "→  Popořadě");
        shuffleBtn.setTextColor(shuffle ? Ui.ON_AMBER : Ui.INK);
        shuffleBtn.setBackground(Ui.tile(this, shuffle ? Ui.AMBER : Ui.TILE, .035f));
    }

    private void renderTabs() {
        tabs.removeAllViews();
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            boolean on = i == tab;
            TextView t = Ui.text(this, TABS[i], .038f, on ? Ui.ON_AMBER : Ui.SOFT);
            if (on) t.setTypeface(Ui.medium());
            t.setGravity(Gravity.CENTER);
            t.setBackground(Ui.tile(this, on ? Ui.AMBER : Ui.TILE, .035f));
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    tab = idx;
                    Prefs.get(SpotifyActivity.this).edit().putInt("spotify_tab", idx).apply();
                    renderTabs();
                    load();
                }
            });
            LinearLayout.LayoutParams lp = Ui.lp(0, -1, 1f);
            if (i > 0) lp.leftMargin = px(.02f);
            tabs.addView(t, lp);
        }
    }

    // ------------------------------------------------------------------ načtení knihovny
    private void message(String s) {
        grid.removeAllViews();
        TextView t = Ui.text(this, s, .04f, Ui.DIM);
        t.setGravity(Gravity.CENTER);
        t.setPadding(px(.05f), px(.12f), px(.05f), 0);
        grid.addView(t, new LinearLayout.LayoutParams(-1, -2));
    }

    private void load() {
        if (SpotifyService.instance == null) {
            message("Spotify Connect není zapnutý.\nNastavení → Spotify → „Hodiny jako Spotify reproduktor“.");
            return;
        }
        message("Načítám…");
        final int myTab = tab;
        new Thread(new Runnable() {
            public void run() {
                String res = null;
                int code = 0;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + SpotifyService.PORT
                            + PATHS[myTab] + "?limit=200").openConnection();
                    c.setConnectTimeout(2000);
                    c.setReadTimeout(20000);
                    code = c.getResponseCode();
                    if (code == 200) res = new String(Prefs.readAll(c.getInputStream()), "UTF-8");
                    c.disconnect();
                } catch (Exception ignored) {
                }
                final String fr = res;
                final int fc = code;
                h.post(new Runnable() {
                    public void run() {
                        if (myTab != tab || isFinishing()) return;
                        if (fc == 204) {
                            message("Hodiny ještě nejsou přihlášené.\nJednou je vyber v aplikaci Spotify v mobilu (ikona zařízení → "
                                    + Prefs.get(SpotifyActivity.this).getString("spotify_name", "Hodiny") + ") – pak už to půjde odsud.");
                        } else if (fr == null) {
                            message("Knihovnu se nepodařilo načíst (" + (fc == 0 ? "přijímač neodpovídá" : "chyba " + fc) + ").");
                        } else {
                            show(fr);
                        }
                    }
                });
            }
        }).start();
    }

    private void show(String json) {
        grid.removeAllViews();
        try {
            JSONArray items = new JSONObject(json).optJSONArray("items");
            java.util.ArrayList<String[]> list = new java.util.ArrayList<String[]>(); // uri, název, podtitul, obrázek
            if (tab == 0 && SpotifyService.user.length() > 0) {
                list.add(new String[]{"spotify:user:" + SpotifyService.user + ":collection", "Oblíbené skladby", "♥", ""});
            }
            if (items != null) for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.getJSONObject(i);
                String sub;
                if (tab == 0) sub = o.optInt("length") + " skladeb";
                else if (tab == 1) {
                    JSONArray a = o.optJSONArray("artist_names");
                    sub = a != null && a.length() > 0 ? a.optString(0) : "";
                } else sub = "interpret";
                String name = o.optString("name");
                if (name.length() == 0) continue;
                list.add(new String[]{o.optString("uri"), name, sub, o.isNull("image_url") ? "" : o.optString("image_url")});
            }
            if (list.isEmpty()) {
                message("Nic tu není.");
                return;
            }
            LinearLayout row = null;
            int cols = 4, gap = px(.025f);
            int w = (getResources().getDisplayMetrics().widthPixels - 2 * px(.0625f) - (cols - 1) * gap) / cols;
            for (int i = 0; i < list.size(); i++) {
                if (i % cols == 0) {
                    row = Ui.box(this, false);
                    LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                    if (i > 0) rl.topMargin = px(.03f);
                    grid.addView(row, rl);
                }
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(w, -2);
                if (i % cols != 0) cl.leftMargin = gap;
                row.addView(card(list.get(i), w), cl);
            }
        } catch (Exception e) {
            message("Chyba při čtení knihovny.");
        }
    }

    private View card(final String[] it, int w) {
        LinearLayout col = Ui.box(this, true);
        FrameLayout art = new FrameLayout(this);
        art.setBackground(Ui.round(it[2].equals("♥") ? 0xFF4B3A8C : Ui.TILE, px(.025f)));
        art.setClipToOutline(true);
        final ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.addView(img, new FrameLayout.LayoutParams(-1, -1));
        if (it[2].equals("♥")) {
            TextView heart = Ui.text(this, "♥", .12f, Ui.INK);
            heart.setGravity(Gravity.CENTER);
            art.addView(heart, new FrameLayout.LayoutParams(-1, -1));
        } else {
            ImageView ph = Ui.icon(this, R.drawable.ic_music, Ui.FAINT, .07f);
            art.addView(ph, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            loadCover(it[3], img, tab == 2);
        }
        col.addView(art, new LinearLayout.LayoutParams(w, w));
        TextView name = Ui.text(this, it[1], .033f, Ui.INK);
        name.setTypeface(Ui.medium());
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(0, px(.012f), 0, 0);
        col.addView(name);
        TextView sub = Ui.text(this, it[2].equals("♥") ? "tvoje knihovna" : it[2], .028f, Ui.DIM);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(sub);
        col.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                play(it[0], it[1]);
            }
        });
        return col;
    }

    private void loadCover(final String url, final ImageView img, final boolean round) {
        if (url == null || url.length() == 0) return;
        img.setTag(url);
        Bitmap cached = covers.get(url);
        if (cached != null) {
            img.setImageBitmap(cached);
            return;
        }
        pool.execute(new Runnable() {
            public void run() {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(4000);
                    c.setReadTimeout(8000);
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inSampleSize = 2; // stačí malé
                    final Bitmap b = BitmapFactory.decodeStream(c.getInputStream(), null, o);
                    c.disconnect();
                    if (b == null) return;
                    covers.put(url, b);
                    h.post(new Runnable() {
                        public void run() {
                            if (url.equals(img.getTag())) img.setImageBitmap(b);
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        });
    }

    // ------------------------------------------------------------------ přehrání
    private void play(final String uri, String name) {
        Toast.makeText(this, "▶  " + name, Toast.LENGTH_SHORT).show();
        final boolean sh = shuffle;
        new Thread(new Runnable() {
            public void run() {
                post("/player/shuffle_context", "{\"shuffle_context\":" + sh + "}");
                try {
                    post("/player/play", new JSONObject().put("uri", uri).toString());
                } catch (Exception ignored) {
                }
            }
        }).start();
        h.postDelayed(new Runnable() { // zpět na plochu, kde je karta přehrávání
            public void run() {
                finish();
            }
        }, 600);
    }

    static int post(String path, String body) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + SpotifyService.PORT + path).openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(2000);
            c.setReadTimeout(15000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            java.io.OutputStream o = c.getOutputStream();
            o.write(body.getBytes("UTF-8"));
            o.close();
            int code = c.getResponseCode();
            c.disconnect();
            return code;
        } catch (Exception e) {
            return 0;
        }
    }
}
