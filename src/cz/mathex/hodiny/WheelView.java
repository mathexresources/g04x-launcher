package cz.mathex.hodiny;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.OverScroller;

/**
 * Otočné kolečko čísel (jako výběr času v hodinách od Googlu): táhne se prstem, má setrvačnost,
 * dojede na nejbližší číslo a je dokola (po 59 je 00). Uprostřed velké číslo, okolní menší a slabší.
 */
final class WheelView extends View {
    interface Listener {
        void onValue(int value);
    }

    private static final int VISIBLE = 5;

    private final int count;
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final OverScroller scroller;
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
        scroller = new OverScroller(c);
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
        scroller.forceFinished(true);
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
                scroller.forceFinished(true);
                lastY = e.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                pos -= (e.getY() - lastY) / rowH;
                lastY = e.getY();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                vt.computeCurrentVelocity(1000);
                float vy = vt.getYVelocity();
                vt.recycle();
                vt = null;
                if (Math.abs(vy) > rowH * 2) {
                    scroller.fling(0, Math.round(pos * rowH), 0, Math.round(-vy), 0, 0,
                            Integer.MIN_VALUE / 2, Integer.MAX_VALUE / 2);
                } else {
                    snap();
                }
                invalidate();
                return true;
        }
        return true;
    }

    /** Dojet na nejbližší číslo. */
    private void snap() {
        int target = Math.round(pos);
        int from = Math.round(pos * rowH);
        scroller.startScroll(0, from, 0, Math.round(target * rowH) - from, 180);
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            pos = scroller.getCurrY() / rowH;
            invalidate();
            if (scroller.isFinished()) settle();
        } else if (Math.abs(pos - Math.round(pos)) > 0.001f && vt == null) {
            snap();
            invalidate();
        } else if (vt == null) {
            settle();
        }
    }

    private void settle() {
        int v = wrap(Math.round(pos));
        if (Math.abs(pos - Math.round(pos)) > 0.001f) {
            snap();
            invalidate();
            return;
        }
        if (v != value) {
            value = v;
            if (listener != null) listener.onValue(v);
        }
    }
}
