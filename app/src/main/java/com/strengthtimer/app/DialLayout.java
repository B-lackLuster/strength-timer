package com.strengthtimer.app;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

/** Square frame for the countdown ring; the digits keep the same share of it at every size. */
final class DialLayout extends FrameLayout {
    private final View face;
    DialLayout(Context context, View face) { super(context); this.face = face; }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int side = Math.min(limit(widthSpec), limit(heightSpec));
        if (side == Integer.MAX_VALUE) side = 0;
        // Auto-sized digits follow the height of their view, so that height follows the dial.
        if (face.getLayoutParams() != null) face.getLayoutParams().height = Math.round(side * .32f);
        int exact = MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY);
        super.onMeasure(exact, exact);
    }
    private static int limit(int spec) { return MeasureSpec.getMode(spec) == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE : MeasureSpec.getSize(spec); }
}
