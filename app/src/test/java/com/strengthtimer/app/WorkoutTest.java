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
    @Test public void setsAreCountedInsideTheirOwnExercise(){
        Workout.Engine e=new Workout.Engine(Workout.compileSession(Arrays.asList(new Workout.Item("平板",2,2,1,3),Workout.Item.rest("自由休息",4,false),new Workout.Item("深蹲",2,2,0,0))),0);
        assertEquals(0,e.completedInCurrent());e.tick(5000);assertEquals(0,e.completedInCurrent());
        e.tick(7000);assertEquals("小休息",e.current().kind);assertEquals(1,e.completedInCurrent());e.tick(10000);assertEquals("大休息",e.current().kind);assertEquals(2,e.completedInCurrent());
        // A standalone rest and the next exercise start from zero while the session total keeps growing.
        e.tick(13000);assertEquals("自由休息",e.current().name);assertEquals(0,e.completedInCurrent());assertEquals(2,e.completed);
        e.tick(17000);assertEquals("深蹲",e.current().name);assertEquals(0,e.completedInCurrent());e.tick(19000);assertEquals(1,e.completedInCurrent());assertEquals(3,e.completed);
        e.tick(21000);assertTrue(e.finished);assertEquals(0,e.completedInCurrent());assertEquals(4,e.completed);
    }
    @Test public void skippedSetsAndSameNamedExercisesStaySeparate(){
        Workout.Engine e=new Workout.Engine(Workout.compile(Arrays.asList(new Workout.Item("深蹲",2,2,1,0),new Workout.Item("深蹲",2,1,0,0))),0);
        e.skip(500);assertEquals(1,e.skipped);assertEquals("小休息",e.current().kind);assertEquals(0,e.completedInCurrent());
        e.tick(3500);assertEquals(3,e.index);assertEquals(0,e.completedInCurrent());assertEquals(Integer.valueOf(1),e.counts.get("深蹲"));
        e.tick(5500);assertTrue(e.finished);assertEquals(Integer.valueOf(2),e.counts.get("深蹲"));assertEquals(Integer.valueOf(1),e.itemCounts.get(0));assertEquals(Integer.valueOf(1),e.itemCounts.get(1));
    }
    @Test public void skipForAnEndedStageLeavesTheNextOneAlone(){
        Workout.Engine e=new Workout.Engine(Workout.compileSession(Arrays.asList(new Workout.Item("深蹲",3,2,1,0))),0);
        // The preparation ran out before the request arrived, so the set that follows is not skipped.
        assertFalse(e.skip(5200,0));assertEquals(1,e.index);assertEquals(0,e.skipped);assertEquals(2800,e.remaining(5200));
        assertTrue(e.skip(5300,1));assertFalse(e.skip(5400,1));assertEquals(2,e.index);assertEquals(1,e.skipped);
        e.pause(5500);assertTrue(e.skip(9999,2));assertTrue(e.paused);assertEquals(3,e.index);assertEquals(1,e.skipped);assertFalse(e.skip(9999,7));
        e.resume(20000);e.tick(23000);assertTrue(e.finished);assertFalse(e.skip(23000,3));assertEquals(1,e.completed);
    }
    @Test public void olderSnapshotsRebuildCountsFromTheSetsAlreadyPassed(){
        Workout.Engine e=new Workout.Engine(Workout.compile(Arrays.asList(new Workout.Item("平板",2,2,1,0),new Workout.Item("深蹲",2,2,0,0))),0);e.skip(100);e.tick(7000);
        assertEquals(4,e.index);Map<Integer,Integer> live=new HashMap<>(e.itemCounts);assertEquals(2,live.size());
        e.rebuildItemCounts();assertEquals(live,e.itemCounts);assertEquals(1,e.completedInCurrent());
    }
    @Test public void emptyPlanFinishesSafely(){Workout.Engine e=new Workout.Engine(Workout.compile(Collections.emptyList()),0);assertTrue(e.finished);assertEquals(0,e.remaining(100));}
}
