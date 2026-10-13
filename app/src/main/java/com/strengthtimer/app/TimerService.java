package com.strengthtimer.app;

import android.app.*;
import android.content.*;
import android.os.*;
import android.media.*;

public class TimerService extends Service {
    static Workout.Engine engine;
    private Handler handler;
    private PowerManager.WakeLock lock;
    private ToneGenerator tone;
    private boolean recorded=false;
    private long lastSave=0;
    static final String CHANNEL="training";
    static long now(){return SystemClock.elapsedRealtime();}
    @Override public void onCreate(){
        super.onCreate();handler=new Handler(getMainLooper());
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"训练计时",NotificationManager.IMPORTANCE_LOW));
        lock=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"strength:timer");
        try{tone=new ToneGenerator(AudioManager.STREAM_ALARM,70);}catch(RuntimeException ignored){}
        if(engine==null)engine=Store.restore(this);
        recorded=engine!=null&&engine.finished;
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?"RESTORE":intent.getAction();
        if("START".equals(action)){
            if(engine==null||engine.finished){engine=new Workout.Engine(Workout.compile(Store.load(this)),now());recorded=false;}
        }else if(engine!=null&&!engine.finished){
            if("PAUSE".equals(action)){if(engine.paused)engine.resume(now());else engine.pause(now());}
            if("SKIP".equals(action))engine.skip(now());
            if("STOP".equals(action)){engine.tick(now());Store.finish(this,engine,engine.finished);recorded=true;engine=null;shutdown();return START_NOT_STICKY;}
        }
        if(engine==null||engine.finished){if(engine!=null&&!recorded){Store.finish(this,engine,true);recorded=true;signal();}shutdown();return START_NOT_STICKY;}
        startForeground(1,notification());
        handler.removeCallbacks(tick);handler.post(tick);
        Store.session(this,engine,now());
        return START_STICKY;
    }
    private final Runnable tick=new Runnable(){public void run(){
        if(engine==null)return;
        if(engine.tick(now())>0)signal();
        if(engine.finished){if(!recorded){Store.finish(TimerService.this,engine,true);recorded=true;}shutdown();return;}
        if(engine.paused){if(lock.isHeld())lock.release();}else if(!lock.isHeld())lock.acquire(6*60*60*1000L);
        getSystemService(NotificationManager.class).notify(1,notification());
        if(now()-lastSave>=1000){Store.session(TimerService.this,engine,now());lastSave=now();}
        handler.postDelayed(this,200);
    }};
    private Notification notification(){
        Workout.Stage s=engine.current();
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        String label=engine.paused?"继续":"暂停";
        PendingIntent pause=PendingIntent.getService(this,1,new Intent(this,TimerService.class).setAction("PAUSE"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent skip=PendingIntent.getService(this,2,new Intent(this,TimerService.class).setAction("SKIP"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(com.strengthtimer.app.R.drawable.ic_app).setContentTitle((engine.paused?"已暂停 · ":"")+s.kind+" · "+s.name).setContentText(MainActivity.clock(engine.remaining(now()))+" · 已完成 "+engine.completed+" 组").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).addAction(new Notification.Action.Builder(null,label,pause).build()).addAction(new Notification.Action.Builder(null,"跳过",skip).build()).build();
    }
    private void signal(){
        if(Store.prefs(this).getBoolean("sound",true)&&tone!=null)tone.startTone(ToneGenerator.TONE_PROP_BEEP2,250);
        if(Store.prefs(this).getBoolean("vibrate",true)){Vibrator v=(Vibrator)getSystemService(VIBRATOR_SERVICE);if(v!=null&&v.hasVibrator())v.vibrate(VibrationEffect.createOneShot(180,VibrationEffect.DEFAULT_AMPLITUDE));}
    }
    private void shutdown(){handler.removeCallbacks(tick);if(lock!=null&&lock.isHeld())lock.release();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    @Override public void onDestroy(){handler.removeCallbacks(tick);if(engine!=null&&!engine.finished)Store.session(this,engine,now());if(lock!=null&&lock.isHeld())lock.release();if(tone!=null)tone.release();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
