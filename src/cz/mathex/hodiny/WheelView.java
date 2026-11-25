package cz.mathex.hodiny;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

/**
 * Otočné kolečko čísel (jako výběr času v hodinách od Googlu): táhne se prstem, má setrvačnost,
 * dojede přesně na číslo a je dokola (po 59 je 00). Uprostřed velké číslo, okolní menší a slabší.
 */
final class WheelView extends View {
    interface Listener {
        void onValue(int value);
    }

    private static final int VISIBLE = 5;

    private final int count;
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private VelocityTracker vt;
    private float rowH = 1;
    private float pos;          // spojitá pozice v řádcích
    private float lastY;
    private int value;
    private Listener listener;
    private final float big, small;

    WheelView(Context c, int count, int initial) {
        super(c);
        this.count = count;
        this.value = initial;
        this.pos = initial;
        Ui.loadFonts(c);
        text.setTypeface(Ui.display());
        text.setTextAlign(Paint.Align.CENTER);
        band.setColor(Ui.TILE);
        big = Ui.base(c) * .17f;
        small = Ui.base(c) * .085f;
    }

    void setListener(Listener l) {
        listener = l;
    }

    int getValue() {
        return value;
    }

    void setValue(int v) {
        stopAnim();
        value = wrap(v);
        pos = value;
        invalidate();
    }

    private int wrap(int i) {
        return ((i % count) + count) % count;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        rowH = h / (float) VISIBLE;
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float cy = h / 2f;
        float r = Ui.px(getContext(), .04f);
        c.drawRoundRect(new RectF(0, cy - rowH * .62f, w, cy + rowH * .62f), r, r, band);

        int base = (int) Math.floor(pos);
        for (int i = -3; i <= 3; i++) {
            int idx = base + i;
            float y = cy + (idx - pos) * rowH;
            float d = Math.min(1f, Math.abs(y - cy) / rowH);
            text.setTextSize(big + (small - big) * d);
            int alpha = (int) (255 * (1f - 0.65f * Math.min(1f, Math.abs(y - cy) / (rowH * 2f))));
            text.setColor(d < .5f ? Ui.INK : Ui.DIM);
            text.setAlpha(Math.max(30, alpha));
            Paint.FontMetrics fm = text.getFontMetrics();
            float baseline = y - (fm.ascent + fm.descent) / 2f;
            int v = wrap(idx);
            c.drawText(v < 10 ? "0" + v : String.valueOf(v), w / 2f, baseline, text);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (vt == null) vt = VelocityTracker.obtain();
        vt.addMovement(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                stopAnim();
                lastY = e.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                pos -= (e.getY() - lastY) / rowH;
                lastY = e.getY();
                changed();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                vt.computeCurrentVelocity(1000);
                float vRows = -vt.getYVelocity() / rowH; // řádků za sekundu
                vt.recycle();
                vt = null;
                // setrvačnost: cílové číslo podle rychlosti, pak plynulé dojetí přesně na něj
                float target = Math.round(pos + (Math.abs(vRows) > 2 ? vRows * .32f : 0));
                animateTo(target, Math.abs(vRows) > 2 ? 520 : 160);
                return true;
        }
        return true;
    }

    private android.animation.ValueAnimator anim;

    /** Dotočit rozjeté kolečko hned na cílové číslo (před uložením). */
    void finish() {
        if (anim != null) anim.end();
    }

    private void stopAnim() {
        if (anim != null) anim.cancel();
        anim = null;
    }

    private void animateTo(final float target, long ms) {
        stopAnim();
        final float from = pos;
        if (Math.abs(target - from) < 0.001f) {
            pos = target;
            changed();
            invalidate();
            return;
        }
        anim = android.animation.ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(ms);
        anim.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
        anim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(android.animation.ValueAnimator va) {
                pos = from + (target - from) * (Float) va.getAnimatedValue();
                changed();
                invalidate();
            }
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            boolean cancelled;

            @Override
            public void onAnimationCancel(android.animation.Animator a) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                if (cancelled) return;
                pos = target;
                changed();
                invalidate();
            }
        });
        anim.start();
    }

    /** Hodnota = číslo, které je právě uprostřed (i během otáčení). */
    private void changed() {
        int v = wrap(Math.round(pos));
        if (v != value) {
            value = v;
            if (listener != null) listener.onValue(v);
        }
    }
}
