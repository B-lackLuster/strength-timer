package com.strengthtimer.app;

import java.util.*;

/** Pure Java training timeline: a completed timed set counts once; skipped sets do not. */
public final class Workout {
    public static final class Item {
        public String name;
        public int seconds, sets, smallRest, bigRest;
        public boolean rest, big;
        public Item(String name, int seconds, int sets, int smallRest, int bigRest) {
            this.name=name; this.seconds=seconds; this.sets=sets; this.smallRest=smallRest; this.bigRest=bigRest;
        }
        public static Item rest(String name,int seconds,boolean big) {
            Item i=new Item(name,seconds,1,0,0); i.rest=true; i.big=big; return i;
        }
    }
    public static final class Stage {
        public String name, kind;
        public int seconds, set, sets, itemIndex;
        public Stage(String name,String kind,int seconds,int set,int sets,int itemIndex) {
            this.name=name;this.kind=kind;this.seconds=seconds;this.set=set;this.sets=sets;this.itemIndex=itemIndex;
        }
        public boolean exercise() { return kind.equals("训练"); }
    }
    public static List<Stage> compile(List<Item> items) {
        List<Stage> out=new ArrayList<>();
        for(int index=0;index<items.size();index++) {
            Item i=items.get(index);
            if(i.seconds<1 || i.sets<1 || i.smallRest<0 || i.bigRest<0) throw new IllegalArgumentException("Invalid workout");
            if(i.rest) { out.add(new Stage(i.name,i.big?"大休息":"小休息",i.seconds,0,0,index)); continue; }
            for(int set=1;set<=i.sets;set++) {
                out.add(new Stage(i.name,"训练",i.seconds,set,i.sets,index));
                if(set<i.sets && i.smallRest>0) out.add(new Stage(i.name+" · 组间休息","小休息",i.smallRest,set,i.sets,index));
            }
            if(i.bigRest>0) out.add(new Stage(i.name+" · 动作后休息","大休息",i.bigRest,i.sets,i.sets,index));
        }
        return out;
    }
    public static final class Engine {
        public final List<Stage> stages;
        public final Map<String,Integer> counts=new LinkedHashMap<>();
        public int index=0, completed=0, skipped=0;
        public long deadline, remaining, trainedMillis;
        public boolean paused=false, finished=false;
        public Engine(List<Stage> stages,long now) {
            this.stages=stages;
            if(stages.isEmpty()) { finished=true; return; }
            remaining=stages.get(0).seconds*1000L; deadline=now+remaining;
        }
        public Stage current() { return finished?null:stages.get(index); }
        public long remaining(long now) { return finished?0:paused?remaining:Math.max(0,deadline-now); }
        public int tick(long now) {
            int transitions=0;
            while(!paused && !finished && now>=deadline) {
                Stage s=current();
                if(s.exercise()) { completed++; counts.put(s.name,counts.getOrDefault(s.name,0)+1); trainedMillis+=s.seconds*1000L; }
                advance(deadline); transitions++;
            }
            return transitions;
        }
        private void advance(long now) {
            index++;
            if(index>=stages.size()) { finished=true; remaining=0; return; }
            remaining=current().seconds*1000L; deadline=now+remaining;
        }
        public void pause(long now) { tick(now); if(!finished&&!paused) { remaining=remaining(now);paused=true; } }
        public void resume(long now) { if(paused&&!finished) { paused=false;deadline=now+remaining; } }
        public void skip(long now) {
            tick(now); if(finished) return;
            if(current().exercise()) skipped++;
            advance(now);
        }
        public long totalMillis() { long total=0;for(Stage s:stages) total+=s.seconds*1000L;return total; }
        public long elapsedMillis(long now) {
            long total=0;for(int i=0;i<Math.min(index,stages.size());i++)total+=stages.get(i).seconds*1000L;
            return total+(finished?0:current().seconds*1000L-remaining(now));
        }
    }
}
