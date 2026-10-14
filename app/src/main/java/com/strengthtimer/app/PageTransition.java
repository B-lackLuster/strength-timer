package com.strengthtimer.app;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** A snapshot keeps the outgoing page visible without leaving its controls active. */
final class PageTransition {
    private final FrameLayout host;
    private View current;
    private ImageView outgoing;
    private Bitmap snapshot;
    PageTransition(FrameLayout host){this.host=host;}
    void replace(View next,boolean animated,boolean back){
        finish();
        if(animated&&ValueAnimator.areAnimatorsEnabled()&&current!=null&&current.getWidth()>0&&current.getHeight()>0){
            float scale=Math.min(1,1080f/current.getWidth());
            snapshot=Bitmap.createBitmap(Math.max(1,Math.round(current.getWidth()*scale)),Math.max(1,Math.round(current.getHeight()*scale)),Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(snapshot);canvas.scale(scale,scale);current.draw(canvas);
            outgoing=new ImageView(host.getContext());outgoing.setScaleType(ImageView.ScaleType.FIT_XY);outgoing.setImageBitmap(snapshot);outgoing.setClickable(true);outgoing.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        host.removeAllViews();current=next;
        host.addView(next,new FrameLayout.LayoutParams(-1,-1));
        if(outgoing!=null)host.addView(outgoing,back?1:0,new FrameLayout.LayoutParams(-1,-1));
        if(outgoing==null)return;
        float width=host.getWidth();next.setTranslationX(back?-width*.28f:width);next.setAlpha(1f);
        outgoing.animate().translationX(back?width:-width*.28f).setDuration(260).setInterpolator(new DecelerateInterpolator(1.4f)).start();
        next.animate().translationX(0).alpha(1).setDuration(260).setInterpolator(new DecelerateInterpolator(1.4f)).withEndAction(this::releaseSnapshot).start();
    }
    private void releaseSnapshot(){
        if(outgoing!=null){outgoing.animate().cancel();outgoing.setImageDrawable(null);host.removeView(outgoing);outgoing=null;}
        if(snapshot!=null){snapshot.recycle();snapshot=null;}
    }
    void finish(){if(current!=null){current.animate().cancel();current.setTranslationX(0);current.setAlpha(1);}releaseSnapshot();}
}
