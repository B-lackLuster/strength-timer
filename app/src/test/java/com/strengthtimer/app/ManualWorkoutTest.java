package com.strengthtimer.app;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ManualWorkoutTest {
    private Workout.Item manual(int sets,int small,int large){Workout.Item item=new Workout.Item("哑铃划船",45,sets,small,large);item.manual=true;item.repetitions=12;return item;}
    @Test public void manualSetNeverCompletesByTime(){
        Workout.Engine e=new Workout.Engine(Workout.compile(Arrays.asList(manual(2,5,20))),0);
        assertEquals(0,e.tick(3600000));assertEquals(0,e.completed);assertEquals(0,e.index);assertTrue(e.current().manual);assertEquals(12,e.current().repetitions);assertEquals(0,e.remaining(3600000));
    }
    @Test public void manualGroupsEnterSmallThenBigRest(){
        Workout.Engine e=new Workout.Engine(Workout.compile(Arrays.asList(manual(2,5,20))),0);
        assertTrue(e.completeManual(2000,0));assertEquals(1,e.completed);assertEquals("小休息",e.current().kind);assertEquals(5000,e.remaining(2000));
        e.tick(7000);assertTrue(e.current().manual);assertEquals(2,e.current().set);assertEquals(0,e.tick(9000));
        assertTrue(e.completeManual(10000,2));assertEquals(2,e.completed);assertEquals("大休息",e.current().kind);assertEquals(20000,e.remaining(10000));assertEquals(5000,e.trainedMillis);
        e.tick(30000);assertTrue(e.finished);assertEquals(2,e.counts.get("哑铃划船").intValue());
    }
    @Test public void duplicateStageCompletionCannotFinishNextSet(){
        Workout.Engine e=new Workout.Engine(Workout.compile(Arrays.asList(manual(2,0,0))),0);
        assertTrue(e.completeManual(1000,0));assertFalse(e.completeManual(1001,0));assertEquals(1,e.completed);assertTrue(e.current().manual);assertEquals(2,e.current().set);
        assertTrue(e.completeManual(2000,1));assertTrue(e.finished);assertEquals(2,e.completed);assertFalse(e.completeManual(2001,1));
    }
    @Test public void pauseExcludesPausedTimeAndBlocksCompletion(){
        Workout.Engine e=new Workout.Engine(Workout.compile(Arrays.asList(manual(1,0,0))),0);e.pause(2000);
        assertFalse(e.completeManual(999999,0));assertEquals(2000,e.manualElapsedMillis(999999));e.resume(1000000);assertTrue(e.completeManual(1002000,0));assertEquals(4000,e.trainedMillis);
    }
    @Test public void delayedTimedTickStopsOnNextManualStage(){
        Workout.Engine e=new Workout.Engine(Workout.compile(Arrays.asList(new Workout.Item("热身",2,1,0,0),manual(1,0,0))),0);
        assertEquals(1,e.tick(999999));assertEquals(1,e.completed);assertTrue(e.current().manual);assertEquals(1,e.index);assertEquals(2000,e.trainedMillis);
    }
    @Test public void skipDoesNotCountManualGroup(){
        Workout.Engine e=new Workout.Engine(Workout.compile(Arrays.asList(manual(2,5,10))),0);e.skip(2000);assertEquals(0,e.completed);assertEquals(1,e.skipped);assertEquals(0,e.trainedMillis);assertEquals("小休息",e.current().kind);
    }
    @Test public void allManualWorkoutHasFiniteStageProgress(){
        Workout.Engine e=new Workout.Engine(Workout.compile(Arrays.asList(manual(2,0,0))),0);assertEquals(0,e.totalMillis());assertEquals(0,e.progressFraction(1000),.001);e.completeManual(1000,0);assertEquals(.5,e.progressFraction(2000),.001);e.completeManual(2000,1);assertEquals(1,e.progressFraction(3000),.001);
    }
    @Test public void manualSetDoesNotReadCountdown(){
        Workout.Engine e=new Workout.Engine(Workout.compile(Arrays.asList(manual(2,5,0))),0);SpokenCountdown voice=new SpokenCountdown();assertEquals(0,voice.update(e,0,true,10));assertEquals(0,voice.update(e,100000,true,10));e.completeManual(100000,0);assertEquals(5,voice.update(e,100000,true,10));
    }
}
