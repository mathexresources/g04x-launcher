package cz.mathex.hodiny;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;

/**
 * Společný základ vedlejších obrazovek (budíky, rádio, světlo, tiskárna, nastavení…):
 * celá obrazovka bez lišt a po 30 s bez dotyku návrat na plochu rovnou do standby.
 */
public abstract class BaseActivity extends Activity {
    static final long IDLE_MS = 30 * 1000L;

    private final Handler idle = new Handler(Looper.getMainLooper());
    private long lastTouch;
    private int builtGen = -1;

    @Override
    protected void onCreate(android.os.Bundle b) {
        super.onCreate(b);
        Ui.loadFonts(this);
        builtGen = Ui.paletteGen;
    }

    private final Runnable check = new Runnable() {
        public void run() {
            long left = lastTouch + IDLE_MS - System.currentTimeMillis();
            // otevřený dialog nebo klávesnice → nečekat
            if (left <= 0 && hasWindowFocus()) {
                goStandby();
                return;
            }
            idle.postDelayed(this, Math.max(1000, left));
        }
    };

    /** Vypnout automatický návrat (např. když něco běží). */
    protected boolean keepAwake() {
        return false;
    }

    private void goStandby() {
        if (keepAwake()) {
            lastTouch = System.currentTimeMillis();
            idle.postDelayed(check, IDLE_MS);
            return;
        }
        startActivity(new Intent(this, MainActivity.class).putExtra("standby", true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.loadFonts(this);
        if (builtGen != -1 && builtGen != Ui.paletteGen) { // mezitím se změnila paleta
            builtGen = Ui.paletteGen;
            recreate();
            return;
        }
        Ui.immersive(this);
        lastTouch = System.currentTimeMillis();
        idle.removeCallbacks(check);
        idle.postDelayed(check, IDLE_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        idle.removeCallbacks(check);
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) {
            Ui.immersive(this);
            lastTouch = System.currentTimeMillis();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        lastTouch = System.currentTimeMillis();
        Sounds.touch(this, e);
        return super.dispatchTouchEvent(e);
    }
}
