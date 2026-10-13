package com.strengthtimer.app;
import android.content.*;
import org.json.*;
import java.util.*;

final class Store {
    static android.content.SharedPreferences prefs(Context c) { return c.getSharedPreferences("strength",0); }
    static JSONArray items(List<Workout.Item> list) {
        JSONArray a=new JSONArray();
        for(Workout.Item i:list) try { a.put(new JSONObject().put("name",i.name).put("seconds",i.seconds).put("sets",i.sets).put("small",i.smallRest).put("large",i.bigRest).put("rest",i.rest).put("big",i.big)); }catch(JSONException ignored){}
        return a;
    }
    static List<Workout.Item> parse(JSONArray a) throws JSONException {
        List<Workout.Item> list=new ArrayList<>();
        for(int n=0;n<a.length();n++) { JSONObject o=a.getJSONObject(n); Workout.Item i=new Workout.Item(o.getString("name"),o.getInt("seconds"),o.getInt("sets"),o.getInt("small"),o.getInt("large"));i.rest=o.optBoolean("rest");i.big=o.optBoolean("big");list.add(i); }
        return list;
    }
    static List<Workout.Item> load(Context c) {
        try { String value=prefs(c).getString("plan",null);if(value!=null)return parse(new JSONArray(value)); }catch(Exception ignored){}
        return new ArrayList<>(Arrays.asList(new Workout.Item("平板支撑",45,3,20,60),new Workout.Item("深蹲",60,3,30,60),new Workout.Item("臀桥",45,3,20,0)));
    }
    static void save(Context c,List<Workout.Item> list) { prefs(c).edit().putString("plan",items(list).toString()).apply(); }
    static JSONArray history(Context c) { try{return new JSONArray(prefs(c).getString("history","[]"));}catch(Exception e){return new JSONArray();} }
    static void finish(Context c,Workout.Engine e,boolean complete) {
        try {
            JSONObject entry=new JSONObject().put("date",System.currentTimeMillis()).put("complete",complete).put("count",e.completed).put("skipped",e.skipped).put("seconds",e.trainedMillis/1000).put("counts",new JSONObject(e.counts));
            JSONArray old=history(c), next=new JSONArray();next.put(entry);for(int n=0;n<Math.min(old.length(),199);n++)next.put(old.get(n));
            prefs(c).edit().putString("history",next.toString()).remove("session").apply();
        }catch(JSONException ignored){}
    }
    static void session(Context c,Workout.Engine e,long now) {
        if(e==null||e.finished)return;
        try {
            JSONArray stages=new JSONArray();for(Workout.Stage s:e.stages)stages.put(new JSONObject().put("name",s.name).put("kind",s.kind).put("seconds",s.seconds).put("set",s.set).put("sets",s.sets).put("item",s.itemIndex));
            JSONObject o=new JSONObject().put("stages",stages).put("index",e.index).put("remaining",e.remaining(now)).put("completed",e.completed).put("skipped",e.skipped).put("trained",e.trainedMillis).put("counts",new JSONObject(e.counts));
            prefs(c).edit().putString("session",o.toString()).apply();
        }catch(JSONException ignored){}
    }
    static Workout.Engine restore(Context c) {
        try {
            String raw=prefs(c).getString("session",null);if(raw==null)return null;JSONObject o=new JSONObject(raw);JSONArray a=o.getJSONArray("stages");List<Workout.Stage> stages=new ArrayList<>();
            for(int n=0;n<a.length();n++){JSONObject s=a.getJSONObject(n);stages.add(new Workout.Stage(s.getString("name"),s.getString("kind"),s.getInt("seconds"),s.getInt("set"),s.getInt("sets"),s.getInt("item")));}
            Workout.Engine e=new Workout.Engine(stages,android.os.SystemClock.elapsedRealtime());e.index=o.getInt("index");e.remaining=o.getLong("remaining");e.completed=o.getInt("completed");e.skipped=o.getInt("skipped");e.trainedMillis=o.getLong("trained");e.paused=true;
            JSONObject counts=o.getJSONObject("counts");Iterator<String> keys=counts.keys();while(keys.hasNext()){String k=keys.next();e.counts.put(k,counts.getInt(k));}return e;
        }catch(Exception e){prefs(c).edit().remove("session").apply();return null;}
    }
}
