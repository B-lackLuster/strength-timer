package com.strengthtimer.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import java.util.HashSet;
import java.util.Set;

/** Small process-owned pool keeps the final chime alive after the service stops. */
final class Chime {
    static final int TRAINING=0, REST=1, COMPLETE=2;
    private static SoundPool pool;
    private static final int[] sounds=new int[3];
    private static final Set<Integer> loaded=new HashSet<>();
    private static int pending, stream;
    private static float pendingVolume;
    static synchronized void init(Context context){
        if(pool!=null)return;
        try{
            pool=new SoundPool.Builder().setMaxStreams(1).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            pool.setOnLoadCompleteListener((p,id,status)->{synchronized(Chime.class){if(status==0){loaded.add(id);if(pending==id){playLoaded(id,pendingVolume);pending=0;}}}});
            Context app=context.getApplicationContext();sounds[TRAINING]=pool.load(app,R.raw.cue_start,1);sounds[REST]=pool.load(app,R.raw.cue_pause_end,1);sounds[COMPLETE]=sounds[REST];
        }catch(RuntimeException error){if(pool!=null)pool.release();pool=null;}
    }
    static synchronized void play(Context context,int kind){
        init(context);if(pool==null)return;
        float volume=Math.max(0,Math.min(100,Store.prefs(context).getInt("sound_volume",55)))/100f;
        // A fresh request replaces a preview still waiting for asynchronous decoding.
        pending=0;if(stream!=0){pool.stop(stream);stream=0;}if(volume==0)return;
        int id=sounds[kind];if(loaded.contains(id))playLoaded(id,volume);else{pending=id;pendingVolume=volume;}
    }
    private static void playLoaded(int id,float volume){stream=pool.play(id,volume,volume,1,0,1);}
}
