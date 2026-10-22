package com.strengthtimer.app;

import java.util.*;

/** Training timeline supports timed sets and manually completed repetition sets. */
public final class Workout {
    public static final int PREPARATION_SECONDS=5;
    public static final class Item {
        public String name;
        public int seconds, sets, smallRest, bigRest;
        public boolean rest, big;
        public boolean manual;
        public int repetitions=12;
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
        public boolean manual;
        public int repetitions;
        public Stage(String name,String kind,int seconds,int set,int sets,int itemIndex) {
            this.name=name;this.kind=kind;this.seconds=seconds;this.set=set;this.sets=sets;this.itemIndex=itemIndex;
        }
        public boolean exercise() { return kind.equals("训练"); }
        public boolean preparation() { return kind.equals("准备"); }
    }
    public static List<Stage> compile(List<Item> items) {
        List<Stage> out=new ArrayList<>();
        for(int index=0;index<items.size();index++) {
            Item i=items.get(index);
            if(((!i.manual||i.rest)&&i.seconds<1) || (i.manual&&!i.rest&&i.repetitions<1) || i.sets<1 || i.smallRest<0 || i.bigRest<0) throw new IllegalArgumentException("Invalid workout");
            if(i.rest) { out.add(new Stage(i.name,i.big?"大休息":"小休息",i.seconds,0,0,index)); continue; }
            for(int set=1;set<=i.sets;set++) {
                Stage work=new Stage(i.name,"训练",i.manual?0:i.seconds,set,i.sets,index);work.manual=i.manual;work.repetitions=i.manual?i.repetitions:0;out.add(work);
                if(set<i.sets && i.smallRest>0) out.add(new Stage(i.name+" · 组间休息","小休息",i.smallRest,set,i.sets,index));
            }
            if(i.bigRest>0) out.add(new Stage(i.name+" · 动作后休息","大休息",i.bigRest,i.sets,i.sets,index));
        }
        return out;
    }
    /** Only new sessions receive preparation; saved timelines are restored as-is. */
    public static List<Stage> compileSession(List<Item> items) {
        List<Stage> stages=compile(items);
        if(!stages.isEmpty())stages.add(0,new Stage("准备开始","准备",PREPARATION_SECONDS,0,0,-1));
        return stages;
    }
    public static final class Engine {
        public final List<Stage> stages;
        public final Map<String,Integer> counts=new LinkedHashMap<>();
        public int index=0, completed=0, skipped=0;
        public long deadline, remaining, trainedMillis;
        public boolean paused=false, finished=false;
        public String sessionId=UUID.randomUUID().toString();
        public long manualElapsed,manualStartedAt;
        public Engine(List<Stage> stages,long now) {
            this.stages=stages;
            if(stages.isEmpty()) { finished=true; return; }
            beginStage(now);
        }
        private void beginStage(long now){remaining=current().manual?0:current().seconds*1000L;deadline=now+remaining;manualElapsed=0;manualStartedAt=now;}
        public Stage current() { return finished?null:stages.get(index); }
        public long remaining(long now) { return finished||current().manual?0:paused?remaining:Math.max(0,deadline-now); }
        public long manualElapsedMillis(long now){return finished||!current().manual?0:manualElapsed+(paused?0:Math.max(0,now-manualStartedAt));}
        public int tick(long now) {
            int transitions=0;
            while(!paused && !finished && !current().manual && now>=deadline) {
                Stage s=current();
                if(s.exercise()) { completed++; counts.put(s.name,counts.getOrDefault(s.name,0)+1); trainedMillis+=s.seconds*1000L; }
                advance(deadline); transitions++;
            }
            return transitions;
        }
        private void advance(long now) {
            index++;
            if(index>=stages.size()) { finished=true; remaining=0; return; }
            beginStage(now);
        }
        public void pause(long now) { tick(now); if(!finished&&!paused) { if(current().manual)manualElapsed=manualElapsedMillis(now);remaining=remaining(now);paused=true; } }
        public void resume(long now) { if(paused&&!finished) { paused=false;deadline=now+remaining;manualStartedAt=now; } }
        public boolean completeManual(long now,int expectedIndex){
            if(finished||paused||index!=expectedIndex||!current().manual)return false;
            Stage s=current();completed++;counts.put(s.name,counts.getOrDefault(s.name,0)+1);trainedMillis+=manualElapsedMillis(now);advance(now);return true;
        }
        public void skip(long now) {
            tick(now); if(finished) return;
            if(current().exercise()) skipped++;
            advance(now);
        }
        public long totalMillis() { long total=0;for(Stage s:stages) total+=s.seconds*1000L;return total; }
        public boolean hasManualStages(){for(Stage s:stages)if(s.manual)return true;return false;}
        public float progressFraction(long now){
            if(finished)return 1;
            if(!hasManualStages())return elapsedMillis(now)/(float)Math.max(1,totalMillis());
            float part=current().manual?0:1-remaining(now)/(float)Math.max(1,current().seconds*1000L);
            return (index+part)/Math.max(1,stages.size());
        }
        public long elapsedMillis(long now) {
            long total=0;for(int i=0;i<Math.min(index,stages.size());i++)total+=stages.get(i).seconds*1000L;
            return total+(finished?0:current().seconds*1000L-remaining(now));
        }
    }
}
