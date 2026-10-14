package com.strengthtimer.app;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;

/** Shared dismissal gesture; content keeps normal scrolling and input gestures. */
final class SheetLayout extends LinearLayout {
    private final Runnable dismiss;
    private final int slop;
    private final float density;
    private View header;
    private ScrollView scroll;
    private float downX,downY;
    private boolean candidate,dragging;
    private VelocityTracker velocity;

    SheetLayout(Context context,Runnable dismiss){
        super(context);this.dismiss=dismiss;density=getResources().getDisplayMetrics().density;
        slop=ViewConfiguration.get(context).getScaledTouchSlop();setOrientation(VERTICAL);
    }
    void setDragRegions(View header,ScrollView scroll){this.header=header;this.scroll=scroll;}
    private boolean touchesControl(View view,float x,float y){
        if(x<0||y<0||x>=view.getWidth()||y>=view.getHeight())return false;
        if(view instanceof EditText||view instanceof SeekBar||view instanceof Switch||view.isClickable())return true;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=group.getChildCount()-1;n>=0;n--){View child=group.getChildAt(n);if(child.getVisibility()==VISIBLE&&touchesControl(child,x+view.getScrollX()-child.getLeft(),y+view.getScrollY()-child.getTop()))return true;}}
        return false;
    }
    private void recycleVelocity(){if(velocity!=null){velocity.recycle();velocity=null;}}
    private boolean pullsDown(MotionEvent e){float dy=e.getRawY()-downY,dx=e.getRawX()-downX;return candidate&&dy>slop&&dy>Math.abs(dx)*1.5f;}
    @Override public boolean onInterceptTouchEvent(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){
            animate().cancel();recycleVelocity();velocity=VelocityTracker.obtain();velocity.addMovement(e);
            downX=e.getRawX();downY=e.getRawY();dragging=false;
            boolean onHeader=header!=null&&e.getY()>=header.getTop()&&e.getY()<header.getBottom();
            boolean onBody=scroll!=null&&e.getY()>=scroll.getTop()&&e.getY()<scroll.getBottom();
            candidate=onHeader||(onBody&&!scroll.canScrollVertically(-1)&&!touchesControl(scroll,e.getX()-scroll.getLeft(),e.getY()-scroll.getTop()));
        }else if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&pullsDown(e)){
            dragging=true;return true;
        }else if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){
            if(!dragging)recycleVelocity();
        }
        return dragging;
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN)return candidate;
        if(!candidate)return super.onTouchEvent(e);
        if(velocity!=null)velocity.addMovement(e);
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_MOVE:
                if(!dragging&&pullsDown(e))dragging=true;
                if(dragging){float distance=Math.max(0,e.getRawY()-downY);setTranslationY(distance);setAlpha(Math.max(.4f,1-distance/Math.max(1,getHeight())));}
                return true;
            case MotionEvent.ACTION_UP:
                float speed=0;if(velocity!=null){velocity.computeCurrentVelocity(1000);speed=velocity.getYVelocity();}
                boolean close=dragging&&(getTranslationY()>Math.min(96*density,getHeight()*.2f)||(getTranslationY()>24*density&&speed>1200*density));
                recycleVelocity();candidate=false;dragging=false;
                if(close)animate().translationY(getHeight()).alpha(0).setDuration(170).withEndAction(dismiss).start();
                else animate().translationY(0).alpha(1).setDuration(150).start();
                return true;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_DOWN:
                recycleVelocity();candidate=false;dragging=false;animate().translationY(0).alpha(1).setDuration(150).start();return true;
            default:return true;
        }
    }
    @Override protected void onDetachedFromWindow(){animate().cancel();recycleVelocity();super.onDetachedFromWindow();}
}
