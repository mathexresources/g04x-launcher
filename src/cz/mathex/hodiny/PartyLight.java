package cz.mathex.hodiny;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Žárovka v párty módu přes „music mode“ Yeelightu: žárovka se připojí k nám a pak bere
 * příkazy bez limitu (normálně ~1 příkaz/s). Když se to nepovede, padá na pomalé obyčejné příkazy.
 * Na konci vrátí žárovku do stavu, ve kterém byla před párty.
 */
final class PartyLight {
    private final Context c;
    private volatile Socket music;
    private ServerSocket server;
    private Yeelight.State before;
    volatile boolean ready;      // music mode běží
    volatile boolean fallback;   // jen pomalé příkazy
    private long lastSlow;
    private int id = 1000;

    PartyLight(Context c) {
        this.c = c.getApplicationContext();
    }

    void start() {
        if (!Yeelight.configured(c)) return;
        Yeelight.state(c, new Yeelight.StateDone() {
            public void done(Yeelight.State s) {
                before = s;
                if (s == null) return; // žárovka nedostupná – párty jen na displeji
                Yeelight.power(c, true, null);
                openMusic();
            }
        });
    }

    private void openMusic() {
        final String myIp = localIp();
        if (myIp == null) {
            fallback = true;
            return;
        }
        new Thread(new Runnable() {
            public void run() {
                try {
                    server = new ServerSocket(0);
                    server.setSoTimeout(6000);
                    final int port = server.getLocalPort();
                    Yeelight.send(c, "set_music", new Object[]{1, myIp, port}, null);
                    Socket s = server.accept();
                    s.setTcpNoDelay(true);
                    music = s;
                    ready = true;
                    writer(s);
                } catch (Exception e) {
                    fallback = true;
                } finally {
                    try {
                        if (server != null) server.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }, "party-light").start();
    }

    private String localIp() {
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) c.getSystemService(Context.WIFI_SERVICE);
            int ip = wm.getConnectionInfo().getIpAddress();
            if (ip == 0) return null;
            return (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF);
        } catch (Exception e) {
            return null;
        }
    }

    /** Barva + jas najednou (0xRRGGBB, 1–100). */
    void color(int rgb, int bright, int ms) {
        rgb &= 0xFFFFFF;
        if (rgb == 0) rgb = 0x010101;
        bright = Math.max(1, Math.min(100, bright));
        String eff = ms < 30 ? "sudden" : "smooth";
        int dur = Math.max(30, ms);
        if (ready) {
            write("set_rgb", new Object[]{rgb, eff, dur});
            write("set_bright", new Object[]{bright, eff, dur});
        } else if (fallback) {
            long now = System.currentTimeMillis();
            if (now - lastSlow < 1100) return; // limit žárovky
            lastSlow = now;
            Yeelight.send(c, "set_scene", new Object[]{"color", rgb, bright}, null);
        }
    }

    void bright(int bright, int ms) {
        if (!ready) return;
        bright = Math.max(1, Math.min(100, bright));
        write("set_bright", new Object[]{bright, ms < 30 ? "sudden" : "smooth", Math.max(30, ms)});
    }

    /** Plynulé přelévání barev přímo v žárovce (pro pomalý režim bez music mode). */
    void flowOnBulb(int[] colors, int stepMs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < colors.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(stepMs).append(",1,").append(colors[i] & 0xFFFFFF).append(",100");
        }
        Yeelight.send(c, "start_cf", new Object[]{0, 0, sb.toString()}, null);
    }

    private final java.util.concurrent.LinkedBlockingQueue<String> queue =
            new java.util.concurrent.LinkedBlockingQueue<String>();

    private void write(String method, Object[] params) {
        if (music == null) return;
        try {
            JSONArray p = new JSONArray();
            for (Object o : params) p.put(o);
            if (queue.size() > 8) queue.clear(); // nezahltit – na starých příkazech nezáleží
            queue.offer(new JSONObject().put("id", id++).put("method", method).put("params", p).toString() + "\r\n");
        } catch (Exception ignored) {
        }
    }

    /** Jediné vlákno, které posílá příkazy do žárovky (zachová pořadí). */
    private void writer(final Socket s) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    OutputStream os = s.getOutputStream();
                    while (music == s) {
                        String m = queue.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                        if (m == null) continue;
                        os.write(m.getBytes("UTF-8"));
                        os.flush();
                    }
                } catch (Exception e) {
                    if (music == s) {
                        ready = false;
                        fallback = true;
                    }
                }
            }
        }, "party-light-writer").start();
    }

    /** Ukončit music mode a vrátit žárovku, jak byla. */
    void stop() {
        final Yeelight.State b = before;
        final Socket s = music;
        music = null;
        ready = false;
        new Thread(new Runnable() {
            public void run() {
                try {
                    if (s != null) s.close();
                } catch (Exception ignored) {
                }
            }
        }).start();
        if (!Yeelight.configured(c)) return;
        Yeelight.send(c, "stop_cf", new Object[]{}, null);
        Yeelight.send(c, "set_music", new Object[]{0}, null);
        if (b == null) return;
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.postDelayed(new Runnable() { // po ukončení music mode chvilku počkat
            public void run() {
                if (!b.on) {
                    Yeelight.power(c, false, null);
                } else if (b.mode == 1) {
                    Yeelight.send(c, "set_scene", new Object[]{"color", b.rgb, b.bright}, null);
                } else {
                    Yeelight.send(c, "set_scene", new Object[]{"ct", b.ct, b.bright}, null);
                }
            }
        }, 1200);
    }
}
