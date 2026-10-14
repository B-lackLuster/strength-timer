package com.strengthtimer.app;

/** Emits the displayed second once, without replaying ticks missed in the background. */
final class SpokenCountdown {
    private Workout.Engine previous;
    private int stage=-1,lastSecond;
    int update(Workout.Engine engine,long now,boolean enabled,int threshold){
        if(engine!=previous||(engine!=null&&engine.index!=stage)){
            previous=engine;stage=engine==null?-1:engine.index;lastSecond=0;
        }
        if(engine==null||engine.finished||engine.paused||engine.current().manual){lastSecond=0;return 0;}
        if(!enabled)return 0;
        int second=(int)((engine.remaining(now)+999)/1000);
        if(second<1||second>Math.min(60,threshold)){lastSecond=0;return 0;}
        if(second==lastSecond)return 0;
        lastSecond=second;return second;
    }
}
