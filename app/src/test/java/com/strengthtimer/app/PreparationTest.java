package com.strengthtimer.app;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class PreparationTest {
    private Workout.Item timed(){return new Workout.Item("深蹲",3,2,1,0);}
    private Workout.Engine session(Workout.Item... items){return new Workout.Engine(Workout.compileSession(Arrays.asList(items)),0);}

    @Test public void sessionPrependsOneFiveSecondTimedStageWithoutChangingPlan(){
        List<Workout.Item> plan=Arrays.asList(timed(),new Workout.Item("平板",2,1,0,0));
        List<Workout.Stage> core=Workout.compile(plan),stages=Workout.compileSession(plan);
        assertEquals(5,Workout.PREPARATION_SECONDS);assertEquals(core.size()+1,stages.size());
        Workout.Stage preparation=stages.get(0);
        assertTrue(preparation.preparation());assertFalse(preparation.exercise());assertFalse(preparation.manual);
        assertEquals("准备开始",preparation.name);assertEquals("准备",preparation.kind);assertEquals(5,preparation.seconds);
        assertEquals(0,preparation.set);assertEquals(0,preparation.sets);assertEquals(-1,preparation.itemIndex);
        assertEquals(2,plan.size());assertFalse(Workout.compile(plan).get(0).preparation());
        int preparationCount=0;for(Workout.Stage stage:stages)if(stage.preparation())preparationCount++;
        assertEquals(1,preparationCount);assertEquals(14000,new Workout.Engine(stages,0).totalMillis());
    }
    @Test public void fiveSecondBoundaryStartsFirstSetWithoutCountingPreparation(){
        Workout.Engine e=session(timed());
        assertEquals(5000,e.remaining(0));assertEquals(0,e.tick(4999));assertTrue(e.current().preparation());assertEquals(1,e.remaining(4999));
        assertEquals(1,e.tick(5000));assertTrue(e.current().exercise());assertEquals(1,e.current().set);assertEquals(3000,e.remaining(5000));
        assertEquals(0,e.completed);assertEquals(0,e.skipped);assertEquals(0,e.trainedMillis);assertTrue(e.counts.isEmpty());
        assertEquals(0,e.tick(5000));assertEquals(1,e.index);
    }
    @Test public void preparationRunsOnlyOnceAcrossSetsAndExercises(){
        Workout.Engine e=session(new Workout.Item("深蹲",1,2,0,0),new Workout.Item("平板",1,1,0,0));
        assertEquals(1,e.tick(5000));assertEquals(1,e.tick(6000));assertEquals(2,e.current().set);
        assertEquals(1,e.tick(7000));assertEquals("平板",e.current().name);assertFalse(e.current().preparation());
        assertEquals(1,e.tick(8000));assertTrue(e.finished);assertEquals(3,e.completed);assertEquals(3000,e.trainedMillis);
    }
    @Test public void repetitionModeStillWaitsFiveSecondsBeforeManualCompletion(){
        Workout.Item manual=new Workout.Item("哑铃划船",45,1,0,0);manual.manual=true;manual.repetitions=12;
        Workout.Engine e=session(manual);
        assertFalse(e.completeManual(1000,0));assertEquals(0,e.tick(4999));assertFalse(e.current().manual);
        assertEquals(1,e.tick(5000));assertTrue(e.current().manual);assertEquals(12,e.current().repetitions);assertEquals(0,e.manualElapsedMillis(5000));
        assertTrue(e.completeManual(7000,1));assertTrue(e.finished);assertEquals(1,e.completed);assertEquals(2000,e.trainedMillis);
    }
    @Test public void leadingRestStartsAfterPreparationAndPreservesItsDuration(){
        Workout.Engine e=session(Workout.Item.rest("热身休息",3,true),new Workout.Item("深蹲",2,1,0,0));
        assertTrue(e.current().preparation());assertEquals(1,e.tick(5000));assertEquals("热身休息",e.current().name);
        assertEquals("大休息",e.current().kind);assertEquals(3000,e.remaining(5000));assertEquals(0,e.current().itemIndex);
        assertEquals(1,e.tick(8000));assertEquals("深蹲",e.current().name);assertEquals(1,e.current().itemIndex);
        assertEquals(1,e.tick(10000));assertEquals(1,e.completed);assertEquals(2000,e.trainedMillis);
    }
    @Test public void preparationPauseAndResumePreserveRemainingTime(){
        Workout.Engine e=session(timed());e.pause(2000);
        assertTrue(e.paused);assertTrue(e.current().preparation());assertEquals(3000,e.remaining(999999));assertEquals(0,e.tick(999999));
        e.resume(1000000);assertEquals(0,e.tick(1002999));assertEquals(1,e.tick(1003000));assertTrue(e.current().exercise());
        assertEquals(0,e.completed);assertEquals(0,e.skipped);assertEquals(0,e.trainedMillis);
    }
    @Test public void skippingPreparationPreservesPauseAndDoesNotSkipASet(){
        Workout.Engine e=session(timed());e.pause(2000);e.skip(9000);
        assertTrue(e.paused);assertTrue(e.current().exercise());assertEquals(3000,e.remaining(999999));
        assertEquals(0,e.completed);assertEquals(0,e.skipped);assertEquals(0,e.trainedMillis);
        e.resume(1000000);assertEquals(1,e.tick(1003000));assertEquals(1,e.completed);assertEquals(3000,e.trainedMillis);
    }
    @Test public void skippingRunningPreparationStartsTheFullFirstSetImmediately(){
        Workout.Engine e=session(timed());e.skip(4999);
        assertFalse(e.paused);assertTrue(e.current().exercise());assertEquals(3000,e.remaining(4999));
        assertEquals(0,e.completed);assertEquals(0,e.skipped);assertEquals(0,e.trainedMillis);assertTrue(e.counts.isEmpty());
        assertEquals(0,e.tick(7998));assertEquals(1,e.tick(7999));assertEquals(1,e.completed);
    }
    @Test public void delayedTickCatchesUpButNeverCountsPreparationAsTraining(){
        Workout.Engine e=session(new Workout.Item("深蹲",2,2,1,0));
        assertEquals(4,e.tick(10000));assertTrue(e.finished);assertEquals(2,e.completed);assertEquals(0,e.skipped);
        assertEquals(4000,e.trainedMillis);assertEquals(Integer.valueOf(2),e.counts.get("深蹲"));assertEquals(1,e.counts.size());
        assertEquals(0,e.tick(999999));assertEquals(2,e.completed);
    }
    @Test public void delayedPreparationTickStopsAtAManualSet(){
        Workout.Item manual=new Workout.Item("哑铃划船",45,1,0,0);manual.manual=true;
        Workout.Engine e=session(manual);assertEquals(1,e.tick(999999));assertTrue(e.current().manual);
        assertEquals(1,e.index);assertEquals(0,e.completed);assertEquals(0,e.trainedMillis);assertFalse(e.finished);
    }
    @Test public void emptySessionFinishesWithoutAddingPreparation(){
        List<Workout.Stage> stages=Workout.compileSession(Collections.emptyList());assertTrue(stages.isEmpty());
        Workout.Engine e=new Workout.Engine(stages,0);assertTrue(e.finished);assertEquals(0,e.tick(5000));assertEquals(0,e.totalMillis());
    }
    @Test public void spokenCountdownResetsAtPreparationToTrainingBoundary(){
        Workout.Engine e=session(new Workout.Item("深蹲",5,1,0,0));SpokenCountdown voice=new SpokenCountdown();
        assertEquals(5,voice.update(e,0,true,Workout.PREPARATION_SECONDS));assertEquals(0,voice.update(e,200,true,Workout.PREPARATION_SECONDS));
        assertEquals(4,voice.update(e,1000,true,Workout.PREPARATION_SECONDS));assertEquals(1,voice.update(e,4000,true,Workout.PREPARATION_SECONDS));
        assertEquals(1,e.tick(5000));assertEquals(5,voice.update(e,5000,true,5));assertEquals(0,voice.update(e,5001,true,5));
        assertEquals(4,voice.update(e,6000,true,5));assertEquals(0,voice.update(e,7000,false,5));
        e.tick(10000);assertEquals(0,voice.update(e,10000,true,5));
    }
}
