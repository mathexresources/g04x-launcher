package cz.mathex.hodiny;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Vlastní ukazatel hlasitosti (design Ember) místo systémového panelu: velký jantarový pruh s procenty
 * nahoře na displeji, po 1,5 s zmizí. Hlasitost hudby se mění tlačítky na hodinách bez systémového UI.
 */
final class VolumeOverlay {
    private final Activity a;
    private final AudioManager audio;
    private final Handler h = new Handler(Looper.getMainLooper());
    private LinearLayout card;
    private ImageView icon;
    private View fill, rest;
    private TextView pct;

    private final Runnable hide = new Runnable() {
        public void run() {
            if (card != null) card.animate().alpha(0f).setDuration(250).withEndAction(new Runnable() {
                public void run() {
                    card.setVisibility(View.GONE);
                }
            });
        }
    };

    VolumeOverlay(Activity a) {
        this.a = a;
        audio = (AudioManager) a.getSystemService(Context.AUDIO_SERVICE);
    }

    /** Vrací true, když klávesu zpracoval (hlasitost nahoru / dolů). */
    boolean onKeyDown(int code) {
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return false;
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int v = audio.getStreamVolume(AudioManager.STREAM_MUSIC) + (code == KeyEvent.KEYCODE_VOLUME_UP ? 1 : -1);
        v = Math.max(0, Math.min(max, v));
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0); // 0 = bez systémového panelu
        show(v, max);
        return true;
    }

    private void build() {
        ViewGroup content = (ViewGroup) a.findViewById(android.R.id.content);
        card = Ui.box(a, false);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.round(0xF2171B21, Ui.px(a, .05f)));
        card.setPadding(Ui.px(a, .04f), 0, Ui.px(a, .04f), 0);
        card.setElevation(Ui.px(a, .02f));

        icon = Ui.icon(a, R.drawable.ic_volume, Ui.AMBER, .065f);
        card.addView(icon);

        LinearLayout bar = Ui.box(a, false);
        bar.setBackground(Ui.round(Ui.LINE, Ui.px(a, .03f)));
        fill = new View(a);
        fill.setBackground(Ui.round(Ui.AMBER, Ui.px(a, .03f)));
        rest = new View(a);
        bar.addView(fill, Ui.lp(0, -1, 0f));
        bar.addView(rest, Ui.lp(0, -1, 1f));
        LinearLayout.LayoutParams bl = Ui.lp(0, Ui.px(a, .05f), 1f);
        bl.leftMargin = bl.rightMargin = Ui.px(a, .035f);
        card.addView(bar, bl);

        pct = Ui.text(a, "", .05f, Ui.INK);
        pct.setTypeface(Ui.display());
        pct.setGravity(Gravity.END);
        card.addView(pct, new LinearLayout.LayoutParams(Ui.px(a, .16f), -2));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.px(a, 1.15f), Ui.px(a, .15f),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = Ui.px(a, .05f);
        content.addView(card, lp);
    }

    private void show(int v, int max) {
        if (card == null) build();
        float f = max > 0 ? (float) v / max : 0;
        ((LinearLayout.LayoutParams) fill.getLayoutParams()).weight = f;
        ((LinearLayout.LayoutParams) rest.getLayoutParams()).weight = 1f - f;
        fill.setVisibility(f > 0 ? View.VISIBLE : View.GONE);
        fill.requestLayout();
        icon.setImageResource(v == 0 ? R.drawable.ic_volume_off : R.drawable.ic_volume);
        Ui.tint(icon, v == 0 ? Ui.DIM : Ui.AMBER);
        pct.setText(Math.round(f * 100) + " %");
        card.animate().cancel();
        card.setAlpha(1f);
        card.setVisibility(View.VISIBLE);
        card.bringToFront();
        h.removeCallbacks(hide);
        h.postDelayed(hide, 1500);
    }
}
