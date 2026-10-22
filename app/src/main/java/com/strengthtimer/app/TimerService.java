package com.strengthtimer.app;

import android.app.*;
import android.content.*;
import android.os.*;
import android.media.*;

public class TimerService extends Service {
    static Workout.Engine engine;
    private Handler handler;
    private PowerManager.WakeLock lock;
    private boolean recorded=false;
    private long lastSave=0;
    private final SpokenCountdown spoken=new SpokenCountdown();
    static final String CHANNEL="training";
    static long now(){return SystemClock.elapsedRealtime();}
    @Override public void onCreate(){
        super.onCreate();handler=new Handler(getMainLooper());
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"训练计时",NotificationManager.IMPORTANCE_LOW));
        lock=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"strength:timer");
        Chime.init(this);
        CountdownVoice.init(this);
        if(engine==null)engine=Store.restore(this);
        recorded=engine!=null&&engine.finished;
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?"RESTORE":intent.getAction();
        // Even late control intents must satisfy Android's foreground-start contract.
        startForeground(1,notification());
        if("START".equals(action)){
            if(engine==null||engine.finished){engine=new Workout.Engine(Workout.compileSession(Store.load(this)),now());recorded=false;}
            if(!engine.finished)signal();
        }else if(engine!=null&&!engine.finished){
            if("PAUSE".equals(action)){if(engine.paused){engine.resume(now());signal(Chime.TRAINING);}else{engine.pause(now());spoken.update(engine,now(),false,0);signal(Chime.REST);}}
            if("SKIP".equals(action)){CountdownVoice.stop();engine.skip(now());if(!engine.finished)signal();}
            if("COMPLETE".equals(action)&&engine.sessionId.equals(intent.getStringExtra("session_id"))&&engine.completeManual(now(),intent.getIntExtra("stage_index",-1))&&!engine.finished)signal();
            if("STOP".equals(action)){engine.tick(now());Store.finish(this,engine,engine.finished);recorded=true;signal(Chime.COMPLETE);engine=null;shutdown();return START_NOT_STICKY;}
        }
        if(engine==null||engine.finished){if(engine!=null&&!recorded){Store.finish(this,engine,true);recorded=true;signal();}shutdown();return START_NOT_STICKY;}
        startForeground(1,notification());
        handler.removeCallbacks(tick);handler.post(tick);
        Store.session(this,engine,now());
        return START_STICKY;
    }
    private final Runnable tick=new Runnable(){public void run(){
        if(engine==null){CountdownVoice.stop();return;}
        if(engine.tick(now())>0)signal();
        if(engine.finished){if(!recorded){Store.finish(TimerService.this,engine,true);recorded=true;}shutdown();return;}
        readCountdown();
        if(engine.paused){if(lock.isHeld())lock.release();}else if(!lock.isHeld())lock.acquire(6*60*60*1000L);
        getSystemService(NotificationManager.class).notify(1,notification());
        if(now()-lastSave>=1000){Store.session(TimerService.this,engine,now());lastSave=now();}
        handler.postDelayed(this,200);
    }};
    private Notification notification(){
        if(engine==null||engine.finished)return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_app).setContentTitle("力量计时").setContentText("正在准备训练").setOnlyAlertOnce(true).setOngoing(true).build();
        Workout.Stage s=engine.current();
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        String label=engine.paused?"继续":"暂停";
        PendingIntent pause=PendingIntent.getService(this,1,new Intent(this,TimerService.class).setAction("PAUSE"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent skip=PendingIntent.getService(this,2,new Intent(this,TimerService.class).setAction("SKIP"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder=new Notification.Builder(this,CHANNEL).setSmallIcon(com.strengthtimer.app.R.drawable.ic_app).setContentTitle((engine.paused?"已暂停 · ":"")+(s.manual?"次数训练":s.kind)+" · "+s.name).setContentText((s.manual?"目标 "+s.repetitions+" 次 · 第 "+s.set+"/"+s.sets+" 组":MainActivity.clock(engine.remaining(now())))+" · 已完成 "+engine.completed+" 组").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true);
        if(s.manual&&!engine.paused){Intent complete=new Intent(this,TimerService.class).setAction("COMPLETE").setData(android.net.Uri.parse("strengthtimer://complete/"+engine.sessionId+"/"+engine.index)).putExtra("session_id",engine.sessionId).putExtra("stage_index",engine.index);PendingIntent done=PendingIntent.getService(this,3,complete,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);builder.addAction(new Notification.Action.Builder(null,"完成这一组",done).build());}
        return builder.addAction(new Notification.Action.Builder(null,label,pause).build()).addAction(new Notification.Action.Builder(null,"跳过",skip).build()).build();
    }
    private void signal(){
        signal(engine!=null&&engine.finished?Chime.COMPLETE:engine!=null&&(engine.current().exercise()||engine.current().preparation())?Chime.TRAINING:Chime.REST);
    }
    private void signal(int kind){
        CountdownVoice.stop();
        if(Store.prefs(this).getBoolean("sound",true))Chime.play(this,kind);
        if(Store.prefs(this).getBoolean("vibrate",true)){Vibrator v=(Vibrator)getSystemService(VIBRATOR_SERVICE);if(v!=null&&v.hasVibrator())v.vibrate(VibrationEffect.createOneShot(180,VibrationEffect.DEFAULT_AMPLITUDE));}
    }
    private void readCountdown(){
        boolean preparation=engine!=null&&!engine.finished&&engine.current().preparation();
        boolean training=engine!=null&&!engine.finished&&(engine.current().exercise()||preparation);
        String key=training?"voice_training":"voice_rest";
        boolean enabled=Store.prefs(this).getBoolean(key,false)&&engine!=null&&!engine.finished&&!engine.current().manual;int threshold=preparation?Workout.PREPARATION_SECONDS:Store.prefs(this).getInt(key+"_seconds",10);long instant=now();
        int second=spoken.update(engine,instant,enabled,threshold);
        if(engine==null||engine.finished||engine.paused||!enabled||engine.remaining(instant)>Math.min(60,threshold)*1000L)CountdownVoice.stop();
        else if(second>0)CountdownVoice.play(this,second,instant+engine.remaining(instant)-(second-1)*1000L);
    }
    private void shutdown(){CountdownVoice.stop();handler.removeCallbacks(tick);if(lock!=null&&lock.isHeld())lock.release();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    @Override public void onDestroy(){CountdownVoice.stop();handler.removeCallbacks(tick);if(engine!=null&&!engine.finished)Store.session(this,engine,now());if(lock!=null&&lock.isHeld())lock.release();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
