package com.strengthtimer.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class BackgroundTimerTest {
    private Context context;
    private AudioManager audio;
    @Before public void reset(){
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();audio=context.getSystemService(AudioManager.class);
        main(()->{context.stopService(new Intent(context,TimerService.class));TimerService.engine=null;Store.prefs(context).edit().clear().commit();});
        SystemClock.sleep(300);
        Store.prefs(context).edit().putBoolean("voice_training",true).putBoolean("voice_rest",true)
            .putInt("voice_training_seconds",60).putInt("voice_rest_seconds",60).putBoolean("sound",false).putBoolean("vibrate",false).commit();
    }
    @After public void cleanup()throws Exception{
        shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard");
        main(()->context.stopService(new Intent(context,TimerService.class)));
    }
    private void main(Runnable task){InstrumentationRegistry.getInstrumentation().runOnMainSync(task);}
    private void shell(String command)throws Exception{
        try(ParcelFileDescriptor descriptor=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
            FileInputStream input=new FileInputStream(descriptor.getFileDescriptor())){
            byte[] buffer=new byte[1024];while(input.read(buffer)!=-1){} // Wait for the command to complete.
        }
    }
    private Notification notification(){
        for(StatusBarNotification active:context.getSystemService(NotificationManager.class).getActiveNotifications())if(active.getId()==1)return active.getNotification();
        throw new AssertionError("Missing training notification");
    }
    private void action(String title)throws Exception{
        for(Notification.Action action:notification().actions)if(title.contentEquals(action.title)){action.actionIntent.send();SystemClock.sleep(350);return;}
        throw new AssertionError("Missing notification action: "+title);
    }
    private boolean speechPlaying(){
        // Public playback monitoring already filters the list to active players.
        for(AudioPlaybackConfiguration config:audio.getActivePlaybackConfigurations())if(config.getAudioAttributes().getContentType()==AudioAttributes.CONTENT_TYPE_SPEECH)return true;
        return false;
    }
    private void assertSilent(){SystemClock.sleep(250);assertFalse("Old digit must stop",speechPlaying());}
    private AtomicInteger observeSpeech(AudioManager.AudioPlaybackCallback[] holder){
        AtomicInteger starts=new AtomicInteger();
        holder[0]=new AudioManager.AudioPlaybackCallback(){@Override public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configurations){
            for(AudioPlaybackConfiguration config:configurations)if(config.getAudioAttributes().getContentType()==AudioAttributes.CONTENT_TYPE_SPEECH){starts.incrementAndGet();break;}
        }};
        audio.registerAudioPlaybackCallback(holder[0],new android.os.Handler(android.os.Looper.getMainLooper()));return starts;
    }
    @Test public void homeAndScreenOffKeepSpeakingThroughTrainingAndRest()throws Exception{
        Store.save(context,Arrays.asList(new Workout.Item("后台朗读",5,2,5,0)));
        AudioManager.AudioPlaybackCallback[] callback=new AudioManager.AudioPlaybackCallback[1];AtomicInteger starts=observeSpeech(callback);
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            onView(withText("开始训练 →")).perform(click());
            activity.moveToState(Lifecycle.State.CREATED);shell("input keyevent KEYCODE_HOME");
            int before=starts.get();SystemClock.sleep(2200);assertTrue("Preparation must play actual speech while at Home",starts.get()>=before+2);
            Notification running=notification();assertTrue(running.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER));assertTrue(running.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN));
            shell("input keyevent KEYCODE_SLEEP");SystemClock.sleep(350);assertFalse(context.getSystemService(PowerManager.class).isInteractive());
            long end=SystemClock.elapsedRealtime()+11000;int training=0,rest=0;int last=starts.get();
            while(SystemClock.elapsedRealtime()<end){
                SystemClock.sleep(100);int count=starts.get();int delta=count-last;last=count;
                if(TimerService.engine.current()!=null){if(TimerService.engine.current().exercise())training+=delta;else if(!TimerService.engine.current().preparation())rest+=delta;}
            }
            assertTrue("Training must play multiple digits with screen off",training>=2);
            assertTrue("Rest must play multiple digits with screen off",rest>=2);
            assertTrue(TimerService.engine.completed>=1);
        }finally{audio.unregisterAudioPlaybackCallback(callback[0]);}
    }
    @Test public void notificationPauseAndResumeFreezeAndRebaseCountdown()throws Exception{
        Store.save(context,Arrays.asList(new Workout.Item("通知验证",30,1,0,0)));
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            onView(withText("开始训练 →")).perform(click());action("跳过");
            activity.moveToState(Lifecycle.State.CREATED);shell("input keyevent KEYCODE_HOME");
            Notification running=notification();long end=running.when;
            assertTrue(running.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN));
            SystemClock.sleep(1300);assertEquals("System UI counts down against one stable deadline",end,notification().when);
            action("暂停");Notification paused=notification();long remaining=TimerService.engine.remaining(TimerService.now());
            assertFalse(paused.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER));assertTrue(paused.extras.getString(Notification.EXTRA_TEXT).startsWith(MainActivity.clock(remaining)));
            assertSilent();SystemClock.sleep(1300);assertEquals(remaining,TimerService.engine.remaining(TimerService.now()));
            action("继续");Notification resumed=notification();assertTrue(resumed.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN));assertTrue(resumed.when>end+1000);
            assertTrue(Math.abs(resumed.when-System.currentTimeMillis()-TimerService.engine.remaining(TimerService.now()))<100);
            action("跳过");assertTrue(TimerService.engine.finished);SystemClock.sleep(300);assertEquals(0,context.getSystemService(NotificationManager.class).getActiveNotifications().length);assertSilent();
        }
    }
    @Test public void manualNotificationHasNoClockAndStartsRestFromBackground()throws Exception{
        Workout.Item item=new Workout.Item("次数验证",30,2,5,0);item.manual=true;Store.save(context,Arrays.asList(item));
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            onView(withText("开始训练 →")).perform(click());action("跳过");activity.moveToState(Lifecycle.State.CREATED);
            assertTrue(TimerService.engine.current().manual);assertFalse(notification().extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER));assertSilent();
            action("完成这一组");assertEquals("小休息",TimerService.engine.current().kind);assertEquals(1,TimerService.engine.completed);assertTrue(notification().extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN));
        }
    }
    @Test public void cancelledOrExpiredDigitsNeverPlayLater()throws Exception{
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            main(()->{CountdownVoice.play(context,60,TimerService.now()+1000);CountdownVoice.stop();});assertSilent();
            main(()->CountdownVoice.play(context,5,TimerService.now()-1));assertSilent();
            Store.prefs(context).edit().putInt("sound_volume",0).commit();main(()->CountdownVoice.play(context,5,TimerService.now()+1000));assertSilent();
        }
    }
}
