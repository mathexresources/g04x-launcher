package cz.mathex.hodiny;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.Socket;

/**
 * Ovládání žárovek Yeelight / Mi LED Smart Bulb po lokální síti (Yeelight LAN protokol: JSON přes TCP 55443,
 * hledání přes SSDP 239.255.255.250:1982). U žárovky musí být zapnuté „LAN Control“.
 * Všechna volání běží na pozadí; výsledek přijde na hlavní vlákno.
 */
final class Yeelight {
    interface Done {
        void done(boolean ok, JSONArray result);
    }

    static final class State {
        boolean on;
        int bright = 100, ct = 4000, rgb = 0xFFFFFF, mode = 2; // mode 1 = barva, 2 = teplota bílé
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static int seq = 1;

    private Yeelight() {}

    static String ip(Context c) {
        return Prefs.get(c).getString("bulb_ip", "");
    }

    static boolean configured(Context c) {
        return ip(c).length() > 0;
    }

    /** Najde žárovku v síti (SSDP) a uloží její IP. done(ok, null). */
    static void discover(final Context c, final Done done) {
        new Thread(new Runnable() {
            public void run() {
                String found = null;
                WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiManager.MulticastLock lock = wm != null ? wm.createMulticastLock("hodiny-yeelight") : null;
                MulticastSocket s = null;
                try {
                    if (lock != null) lock.acquire();
                    s = new MulticastSocket();
                    s.setSoTimeout(1500);
                    byte[] q = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1982\r\nMAN: \"ssdp:discover\"\r\nST: wifi_bulb\r\n")
                            .getBytes("UTF-8");
                    InetAddress grp = InetAddress.getByName("239.255.255.250");
                    long end = System.currentTimeMillis() + 4500;
                    byte[] buf = new byte[2048];
                    while (found == null && System.currentTimeMillis() < end) {
                        s.send(new DatagramPacket(q, q.length, grp, 1982));
                        try {
                            DatagramPacket p = new DatagramPacket(buf, buf.length);
                            s.receive(p);
                            String r = new String(p.getData(), 0, p.getLength(), "UTF-8");
                            for (String line : r.split("\r\n")) {
                                if (line.toLowerCase().startsWith("location:")) {
                                    // Location: yeelight://192.168.0.50:55443
                                    String loc = line.substring(line.indexOf("//") + 2).trim();
                                    found = loc.contains(":") ? loc.substring(0, loc.indexOf(':')) : loc;
                                }
                            }
                        } catch (java.net.SocketTimeoutException ignored) {
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    if (s != null) s.close();
                    if (lock != null && lock.isHeld()) lock.release();
                }
                final String f = found;
                if (f != null) Prefs.get(c).edit().putString("bulb_ip", f).apply();
                MAIN.post(new Runnable() {
                    public void run() {
                        if (done != null) done.done(f != null, null);
                    }
                });
            }
        }).start();
    }

    /** Pošle příkaz a vrátí pole "result" z odpovědi. */
    static void send(final Context c, final String method, final Object[] params, final Done done) {
        final String ip = ip(c);
        new Thread(new Runnable() {
            public void run() {
                boolean ok = false;
                JSONArray result = null;
                if (ip.length() > 0) {
                    Socket sock = null;
                    try {
                        sock = new Socket();
                        sock.connect(new InetSocketAddress(ip, 55443), 2500);
                        sock.setSoTimeout(3000);
                        int id;
                        synchronized (Yeelight.class) {
                            id = seq++;
                        }
                        JSONArray p = new JSONArray();
                        for (Object o : params) p.put(o);
                        String msg = new JSONObject().put("id", id).put("method", method).put("params", p).toString() + "\r\n";
                        OutputStream os = sock.getOutputStream();
                        os.write(msg.getBytes("UTF-8"));
                        os.flush();
                        BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), "UTF-8"));
                        long end = System.currentTimeMillis() + 3000;
                        String line;
                        while (System.currentTimeMillis() < end && (line = in.readLine()) != null) {
                            JSONObject o = new JSONObject(line);
                            if (o.optInt("id", -1) != id) continue; // notifikace „props“ přeskočit
                            if (o.has("result")) {
                                ok = true;
                                result = o.optJSONArray("result");
                            }
                            break;
                        }
                    } catch (Exception ignored) {
                    } finally {
                        try {
                            if (sock != null) sock.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
                final boolean fok = ok;
                final JSONArray fr = result;
                MAIN.post(new Runnable() {
                    public void run() {
                        if (done != null) done.done(fok, fr);
                    }
                });
            }
        }).start();
    }

    static void state(Context c, final StateDone done) {
        send(c, "get_prop", new Object[]{"power", "bright", "ct", "rgb", "color_mode"}, new Done() {
            public void done(boolean ok, JSONArray r) {
                if (!ok || r == null || r.length() < 5) {
                    done.done(null);
                    return;
                }
                State s = new State();
                s.on = "on".equals(r.optString(0));
                s.bright = parse(r.optString(1), 100);
                s.ct = parse(r.optString(2), 4000);
                s.rgb = parse(r.optString(3), 0xFFFFFF);
                s.mode = parse(r.optString(4), 2);
                done.done(s);
            }
        });
    }

    interface StateDone {
        void done(State s); // null = žárovka nedostupná
    }

    private static int parse(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    static void toggle(Context c, Done d) {
        send(c, "toggle", new Object[]{}, d);
    }

    static void power(Context c, boolean on, Done d) {
        send(c, "set_power", new Object[]{on ? "on" : "off", "smooth", 400}, d);
    }

    static void bright(Context c, int b, Done d) {
        send(c, "set_bright", new Object[]{Math.max(1, Math.min(100, b)), "smooth", 300}, d);
    }

    static void ct(Context c, int k, Done d) {
        send(c, "set_ct_abx", new Object[]{Math.max(1700, Math.min(6500, k)), "smooth", 300}, d);
    }

    static void rgb(Context c, int rgb, Done d) {
        send(c, "set_rgb", new Object[]{rgb & 0xFFFFFF, "smooth", 300}, d);
    }

    /** Zapne a nastaví teplotu bílé i jas jedním příkazem (předvolby). */
    static void scene(Context c, int k, int b, Done d) {
        send(c, "set_scene", new Object[]{"ct", Math.max(1700, Math.min(6500, k)), Math.max(1, Math.min(100, b))}, d);
    }

    /** Zapne naplno studenou bílou (po vypnutí budíku). */
    static void full(Context c) {
        send(c, "set_scene", new Object[]{"ct", 5000, 100}, null);
    }

    /** Svítání: zapne na 1 % velmi teplé a během `seconds` přejde na 100 % denního světla. */
    static void sunrise(Context c, int seconds) {
        String flow = "500,2,1700,1," + (seconds * 500) + ",2,2700,45," + (seconds * 500) + ",2,4000,100";
        send(c, "set_scene", new Object[]{"cf", 3, 1, flow}, null); // 3 kroky, 1 = po skončení zůstat svítit
    }
}
