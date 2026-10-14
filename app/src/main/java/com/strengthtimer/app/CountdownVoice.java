package com.strengthtimer.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;
import java.util.HashSet;
import java.util.Set;

/** Bundled Mandarin digits work offline without an installed speech engine. */
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
    private static final int[] sounds=new int[61];
    private static final Set<Integer> loaded=new HashSet<>();
    private static SoundPool pool;
    private static int stream,pending;
    private static long pendingUntil;
    private static float pendingVolume;
    static synchronized void init(Context context){
        if(pool!=null)return;
        try{
            pool=new SoundPool.Builder().setMaxStreams(1).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).build();
            pool.setOnLoadCompleteListener((p,id,status)->{synchronized(CountdownVoice.class){
                if(p!=pool)return;
                if(status==0)loaded.add(id);
                if(pending==id){if(status==0&&SystemClock.elapsedRealtime()<pendingUntil)playLoaded(id,pendingVolume);pending=0;}
            }});
            Context app=context.getApplicationContext();for(int n=1;n<sounds.length;n++)sounds[n]=pool.load(app,resources[n],1);
        }catch(RuntimeException error){if(pool!=null)pool.release();pool=null;loaded.clear();stream=0;pending=0;}
    }
    static synchronized void play(Context context,int second,long validUntil){
        if(second<1||second>60||SystemClock.elapsedRealtime()>=validUntil)return;
        init(context);if(pool==null)return;
        stop();float volume=Math.max(0,Math.min(100,Store.prefs(context).getInt("sound_volume",55)))/100f;if(volume==0)return;
        int id=sounds[second];if(loaded.contains(id))playLoaded(id,volume);
        else{pending=id;pendingUntil=validUntil;pendingVolume=volume;}
    }
    private static void playLoaded(int id,float volume){if(stream!=0)pool.stop(stream);stream=pool.play(id,volume,volume,1,0,1);}
    static synchronized void stop(){pending=0;if(pool!=null&&stream!=0)pool.stop(stream);stream=0;}
}
