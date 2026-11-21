package cz.mathex.hodiny;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Aktualizace z GitHub Releases (mathexresources/g04x-launcher).
 * Release musí mít tag „v1.5“ apod. a jako přílohu .apk podepsané stejným klíčem.
 */
final class Updater {
    static final String LATEST = "https://api.github.com/repos/mathexresources/g04x-launcher/releases/latest";

    private Updater() {}

    static String currentVersion(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    /** „1.10“ > „1.9“ – porovnání po číslech. */
    static boolean newer(String remote, String local) {
        String[] r = remote.replaceAll("[^0-9.]", "").split("\\.");
        String[] l = local.replaceAll("[^0-9.]", "").split("\\.");
        for (int i = 0; i < Math.max(r.length, l.length); i++) {
            int a = i < r.length && r[i].length() > 0 ? Integer.parseInt(r[i]) : 0;
            int b = i < l.length && l[i].length() > 0 ? Integer.parseInt(l[i]) : 0;
            if (a != b) return a > b;
        }
        return false;
    }

    static void check(final Activity a) {
        Toast.makeText(a, "Hledám aktualizaci…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            public void run() {
                String error = null, tag = null, url = null, notes = "";
                try {
                    JSONObject rel = new JSONObject(Prefs.http(LATEST));
                    tag = rel.getString("tag_name");
                    notes = rel.optString("body", "");
                    JSONArray assets = rel.getJSONArray("assets");
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject as = assets.getJSONObject(i);
                        if (as.getString("name").endsWith(".apk")) {
                            url = as.getString("browser_download_url");
                            break;
                        }
                    }
                    if (url == null) error = "Release " + tag + " nemá přiložené APK.";
                } catch (Exception e) {
                    error = "Nepodařilo se zjistit verzi (síť?)";
                }
                final String err = error, t = tag, u = url, n = notes;
                a.runOnUiThread(new Runnable() {
                    public void run() {
                        if (err != null) {
                            Toast.makeText(a, err, Toast.LENGTH_LONG).show();
                            return;
                        }
                        String cur = currentVersion(a);
                        if (!newer(t, cur)) {
                            Toast.makeText(a, "Máš nejnovější verzi (" + cur + ")", Toast.LENGTH_LONG).show();
                            return;
                        }
                        String msg = "Nainstalovaná: " + cur + "\nNová: " + t.replaceFirst("^v", "")
                                + (n.trim().length() > 0 ? "\n\n" + n.trim() : "");
                        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                .setTitle("Nová verze Hodin")
                                .setMessage(msg)
                                .setNegativeButton("Teď ne", null)
                                .setPositiveButton("Aktualizovat", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface d, int w) {
                                        install(a, u);
                                    }
                                })
                                .show();
                    }
                });
            }
        }).start();
    }

    static void install(final Activity a, final String url) {
        Toast.makeText(a, "Stahuji aktualizaci…", Toast.LENGTH_LONG).show();
        final Context app = a.getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                PackageInstaller.Session session = null;
                try {
                    PackageInstaller pi = app.getPackageManager().getPackageInstaller();
                    PackageInstaller.SessionParams params =
                            new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                    params.setAppPackageName(app.getPackageName());
                    int id = pi.createSession(params);
                    session = pi.openSession(id);

                    HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                    con.setConnectTimeout(15000);
                    con.setReadTimeout(60000);
                    con.setInstanceFollowRedirects(true);
                    con.setRequestProperty("User-Agent", "Hodiny/" + currentVersion(app));
                    InputStream in = con.getInputStream();
                    OutputStream out = session.openWrite("hodiny.apk", 0, -1);
                    byte[] buf = new byte[65536];
                    int n;
                    long total = 0;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        total += n;
                    }
                    session.fsync(out);
                    out.close();
                    in.close();
                    con.disconnect();
                    if (total < 10000) throw new Exception("stažený soubor je moc malý");

                    PendingIntent done = PendingIntent.getBroadcast(app, 3,
                            new Intent(app, UpdateReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT);
                    session.commit(done.getIntentSender());
                    session.close();
                } catch (final Exception e) {
                    if (session != null) session.abandon();
                    a.runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(a, "Aktualizace selhala: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }
}
