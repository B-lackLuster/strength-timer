package com.strengthtimer.app;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.PowerManager;
import android.os.SystemClock;
import java.io.IOException;

/** One wake-aware player for the current bundled Mandarin digit, including screen-off playback. */
final class CountdownVoice {
    private static final int[] resources={0,
        R.raw.count_01,R.raw.count_02,R.raw.count_03,R.raw.count_04,R.raw.count_05,
        R.raw.count_06,R.raw.count_07,R.raw.count_08,R.raw.count_09,R.raw.count_10,
        R.raw.count_11,R.raw.count_12,R.raw.count_13,R.raw.count_14,R.raw.count_15,
        R.raw.count_16,R.raw.count_17,R.raw.count_18,R.raw.count_19,R.raw.count_20,
        R.raw.count_21,R.raw.count_22,R.raw.count_23,R.raw.count_24,R.raw.count_25,
        R.raw.count_26,R.raw.count_27,R.raw.count_28,R.raw.count_29,R.raw.count_30,
        R.raw.count_31,R.raw.count_32,R.raw.count_33,R.raw.count_34,R.raw.count_35,
        R.raw.count_36,R.raw.count_37,R.raw.count_38,R.raw.count_39,R.raw.count_40,
        R.raw.count_41,R.raw.count_42,R.raw.count_43,R.raw.count_44,R.raw.count_45,
        R.raw.count_46,R.raw.count_47,R.raw.count_48,R.raw.count_49,R.raw.count_50,
        R.raw.count_51,R.raw.count_52,R.raw.count_53,R.raw.count_54,R.raw.count_55,
        R.raw.count_56,R.raw.count_57,R.raw.count_58,R.raw.count_59,R.raw.count_60};
    private static MediaPlayer player;
    static synchronized void play(Context context,int second,long validUntil){
        if(second<1||second>60||SystemClock.elapsedRealtime()>=validUntil)return;
        stop();
        float volume=Math.max(0,Math.min(100,Store.prefs(context).getInt("sound_volume",55)))/100f;
        if(volume==0)return;
        MediaPlayer next=new MediaPlayer();player=next;
        try(AssetFileDescriptor file=context.getResources().openRawResourceFd(resources[second])){
            next.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            next.setWakeMode(context.getApplicationContext(),PowerManager.PARTIAL_WAKE_LOCK);
            next.setVolume(volume,volume);
            next.setDataSource(file.getFileDescriptor(),file.getStartOffset(),file.getLength());
            next.setOnPreparedListener(ready->{synchronized(CountdownVoice.class){
                if(player!=ready)return;
                // Slow decoding must not replay a digit whose second has already passed.
                if(SystemClock.elapsedRealtime()>=validUntil){stop();return;}
                try{ready.start();}catch(RuntimeException error){stop();}
            }});
            next.setOnCompletionListener(done->{synchronized(CountdownVoice.class){if(player==done)stop();}});
            next.setOnErrorListener((failed,what,extra)->{synchronized(CountdownVoice.class){if(player==failed)stop();}return true;});
            next.prepareAsync();
        }catch(IOException|RuntimeException error){stop();}
    }
    static synchronized void stop(){
        if(player==null)return;
        MediaPlayer old=player;player=null;
        old.setOnPreparedListener(null);old.setOnCompletionListener(null);old.setOnErrorListener(null);
        old.release();
    }
}
