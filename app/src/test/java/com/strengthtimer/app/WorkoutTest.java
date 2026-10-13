package com.strengthtimer.app;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class WorkoutTest {
    @Test public void timelineHasSmallBetweenSetsAndBigAfterLast(){
        List<Workout.Stage> s=Workout.compile(Arrays.asList(new Workout.Item("平板",45,3,20,60),Workout.Item.rest("自由休息",15,false),new Workout.Item("深蹲",60,1,0,0)));
        assertEquals(8,s.size());assertEquals("训练",s.get(0).kind);assertEquals("小休息",s.get(1).kind);assertEquals("训练",s.get(4).kind);assertEquals("大休息",s.get(5).kind);assertEquals("自由休息",s.get(6).name);assertEquals("深蹲",s.get(7).name);
    }
    @Test public void delayedTickCatchesUpAndCountsOnlyExercise(){Workout.Engine e=new Workout.Engine(Workout.compile(Arrays.asList(new Workout.Item("平板",2,2,1,0))),100);assertEquals(3,e.tick(5100));assertTrue(e.finished);assertEquals(2,e.completed);assertEquals(4000,e.trainedMillis);assertEquals(Integer.valueOf(2),e.counts.get("平板"));}
    @Test public void pausePreservesRemainingAndResumeUsesNewDeadline(){Workout.Engine e=new Workout.Engine(Workout.compile(Arrays.asList(new Workout.Item("平板",45,1,0,0))),0);e.pause(10000);e.tick(999999);assertEquals(35000,e.remaining(999999));e.resume(1000000);assertEquals(0,e.tick(1034999));assertEquals(1,e.tick(1035000));assertEquals(1,e.completed);}
    @Test public void skipDoesNotCountAndKeepsPause(){Workout.Engine e=new Workout.Engine(Workout.compile(Arrays.asList(new Workout.Item("平板",45,2,20,0))),0);e.pause(1000);e.skip(2000);assertTrue(e.paused);assertEquals("小休息",e.current().kind);assertEquals(20000,e.remaining(9000));assertEquals(0,e.completed);assertEquals(1,e.skipped);}
    @Test public void zeroRestsDisappearAndCustomRestCanLead(){List<Workout.Stage> s=Workout.compile(Arrays.asList(Workout.Item.rest("热身",10,true),new Workout.Item("深蹲",60,2,0,0)));assertEquals(3,s.size());assertEquals("热身",s.get(0).name);}
    @Test public void boundaryCompletionBeforeSkipCountsExactlyOnce(){Workout.Engine e=new Workout.Engine(Workout.compile(Arrays.asList(new Workout.Item("深蹲",1,2,1,0))),0);e.skip(1000);assertEquals(1,e.completed);assertEquals(0,e.skipped);assertEquals(2,e.index);e.tick(2000);assertEquals(2,e.completed);e.tick(999999);assertEquals(2,e.completed);}
    @Test public void duplicateNamesAccumulate(){Workout.Engine e=new Workout.Engine(Workout.compile(Arrays.asList(new Workout.Item("深蹲",1,1,0,0),new Workout.Item("深蹲",1,1,0,0))),0);e.tick(2000);assertEquals(Integer.valueOf(2),e.counts.get("深蹲"));}
    @Test public void emptyPlanFinishesSafely(){Workout.Engine e=new Workout.Engine(Workout.compile(Collections.emptyList()),0);assertTrue(e.finished);assertEquals(0,e.remaining(100));}
}
