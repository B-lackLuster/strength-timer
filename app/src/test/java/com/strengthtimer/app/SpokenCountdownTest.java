package com.strengthtimer.app;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class SpokenCountdownTest {
    private Workout.Engine workout(){return new Workout.Engine(Workout.compile(Arrays.asList(new Workout.Item("训练",20,2,15,0))),0);}
    @Test public void startsAtThresholdAndEmitsEachDisplayedSecondOnce(){
        Workout.Engine e=workout();SpokenCountdown voice=new SpokenCountdown();
        assertEquals(0,voice.update(e,9999,true,10));assertEquals(10,voice.update(e,10000,true,10));
        assertEquals(0,voice.update(e,10200,true,10));assertEquals(0,voice.update(e,10999,true,10));assertEquals(9,voice.update(e,11000,true,10));
        assertEquals(1,voice.update(e,19000,true,10));assertEquals(0,voice.update(e,20000,true,10));
    }
    @Test public void delayedTicksReadCurrentNumberWithoutBacklog(){
        Workout.Engine e=workout();SpokenCountdown voice=new SpokenCountdown();
        assertEquals(10,voice.update(e,10000,true,10));assertEquals(6,voice.update(e,14000,true,10));assertEquals(0,voice.update(e,14200,true,10));
    }
    @Test public void pauseStopsAndResumeReadsCurrentSecond(){
        Workout.Engine e=workout();SpokenCountdown voice=new SpokenCountdown();assertEquals(5,voice.update(e,15000,true,10));
        e.pause(15200);assertEquals(0,voice.update(e,60000,true,10));e.resume(60000);assertEquals(5,voice.update(e,60000,true,10));assertEquals(4,voice.update(e,60800,true,10));
    }
    @Test public void trainingAndRestUseIndependentThresholds(){
        Workout.Engine e=workout();SpokenCountdown voice=new SpokenCountdown();
        assertEquals(5,voice.update(e,15000,true,5));e.tick(20000);assertFalse(e.current().exercise());
        assertEquals(0,voice.update(e,20000,true,3));assertEquals(3,voice.update(e,32000,true,3));e.tick(35000);assertTrue(e.current().exercise());
        assertEquals(0,voice.update(e,35000,true,5));assertEquals(5,voice.update(e,50000,true,5));
    }
    @Test public void shortStageStartsAtItsActualRemainingTime(){
        Workout.Engine e=new Workout.Engine(Workout.compile(Arrays.asList(new Workout.Item("训练",3,1,0,0))),0);SpokenCountdown voice=new SpokenCountdown();
        assertEquals(3,voice.update(e,0,true,10));assertEquals(2,voice.update(e,1000,true,10));assertEquals(1,voice.update(e,2000,true,10));e.tick(3000);assertEquals(0,voice.update(e,3000,true,10));
    }
    @Test public void disableSkipAndNewWorkoutDoNotReuseOldNumbers(){
        Workout.Engine e=workout();SpokenCountdown voice=new SpokenCountdown();assertEquals(10,voice.update(e,10000,true,10));assertEquals(0,voice.update(e,11000,false,10));
        assertEquals(8,voice.update(e,12000,true,10));e.skip(12000);assertEquals(0,voice.update(e,12000,true,10));assertEquals(10,voice.update(e,17000,true,10));
        Workout.Engine replacement=new Workout.Engine(Workout.compile(Arrays.asList(new Workout.Item("新训练",10,1,0,0))),17000);assertEquals(10,voice.update(replacement,17000,true,10));assertEquals(0,voice.update(null,17000,true,10));
    }
}
