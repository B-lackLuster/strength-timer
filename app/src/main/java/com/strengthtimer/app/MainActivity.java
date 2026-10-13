package com.strengthtimer.app;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final int BG=0xff101711,CARD=0xff1e2921,ACCENT=0xffcaef76,TEXT=0xfff2f5ed,MUTED=0xffa1afa4,ORANGE=0xffffbb7c;
    private LinearLayout root,content,nav;
    private TextView timer,stageTitle,stageMeta,nextText,countsText,pauseButton;
    private ProgressBar progress;
    private CountdownRing ring;
    private List<Workout.Item> plan;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String page="plan";
    private boolean wasFinished=false;
    private ScrollView scroller;
    private final Runnable refresh=new Runnable(){public void run(){
        Workout.Engine e=TimerService.engine;
        if(page.equals("timer")&&e!=null){if(e.finished&&!wasFinished){wasFinished=true;showTimer();}else updateTimer();}
        if(page.equals("timer")&&e==null)showPlan();
        handler.postDelayed(this,100);
    }};
    static String clock(long millis){long s=(Math.max(0,millis)+999)/1000;return String.format(Locale.US,"%02d:%02d",s/60,s%60);}
    private String time(int seconds){return seconds>=60?(seconds/60+"分"+(seconds%60==0?"":seconds%60+"秒")):seconds+"秒";}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onCreate(Bundle b){super.onCreate(b);plan=Store.load(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(dp(20),0,dp(20),0);
        if(Build.VERSION.SDK_INT>=30)root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());root.setPadding(dp(20),bars.top,dp(20),bars.bottom);return insets;});
        // API 26–29 use legacy insets; modern edge-to-edge devices use the same safe areas.
        if(Build.VERSION.SDK_INT<30)root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(dp(20),insets.getSystemWindowInsetTop(),dp(20),insets.getSystemWindowInsetBottom());return insets;});
        setContentView(root);root.requestApplyInsets();
        if(TimerService.engine==null)TimerService.engine=Store.restore(this);
        if(TimerService.engine!=null&&!TimerService.engine.finished)showTimer();else showPlan();
    }
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresh);handler.post(refresh);}
    @Override protected void onPause(){super.onPause();handler.removeCallbacks(refresh);}
    private boolean active(){return TimerService.engine!=null&&!TimerService.engine.finished;}
    private GradientDrawable background(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(color==CARD)d.setStroke(dp(1),0xff344039);return d;}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(0,dp(3),0,dp(3));return t;}
    private TextView bold(String value,int size,int color){TextView t=text(value,size,color);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private TextView button(String value,int color,Runnable action){TextView t=bold(value,15,color==ACCENT?BG:TEXT);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(48));t.setPadding(dp(14),dp(10),dp(14),dp(10));t.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33ffffff),background(color,16),background(Color.WHITE,16)));t.setFocusable(true);t.setOnClickListener(v->action.run());return t;}
    private View metric(String label,String value,int color){LinearLayout box=column();box.setPadding(dp(12),dp(14),dp(12),dp(14));box.setBackground(background(CARD,16));TextView number=bold(value,22,color);number.setAutoSizeTextTypeUniformWithConfiguration(12,22,1,android.util.TypedValue.COMPLEX_UNIT_SP);box.addView(number,new LinearLayout.LayoutParams(-1,dp(30)));box.addView(text(label,12,MUTED));return box;}
    private void gap(LinearLayout v,int h){Space s=new Space(this);v.addView(s,new LinearLayout.LayoutParams(1,dp(h)));}
    private void weighted(LinearLayout r,View v){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),0,dp(3),0);r.addView(v,lp);}
    private void base(String selected){
        page=selected;root.removeAllViews();
        LinearLayout header=row();header.setPadding(0,dp(14),0,dp(12));TextView brand=bold("力量 / TIMER",19,ACCENT);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));TextView settings=text("设置",14,MUTED);settings.setPadding(dp(16),dp(10),0,dp(10));settings.setOnClickListener(v->settings());header.addView(settings);root.addView(header);
        scroller=new ScrollView(this);scroller.setFillViewport(true);content=column();scroller.addView(content);root.addView(scroller,new LinearLayout.LayoutParams(-1,0,1));
        nav=row();nav.setPadding(0,dp(10),0,dp(10));weighted(nav,button("训练计划",selected.equals("plan")?ACCENT:CARD,()->showPlan()));weighted(nav,button(active()?"正在计时":"计时",selected.equals("timer")?ACCENT:CARD,()->{if(TimerService.engine!=null)showTimer();else toast("先在训练计划中点击开始");}));weighted(nav,button("训练记录",selected.equals("history")?ACCENT:CARD,()->showHistory()));root.addView(nav);
    }
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_SHORT).show();}
    private void showPlan(){
        base("plan");content.addView(text("WORKOUT / 今日训练",12,ACCENT));content.addView(bold("今天，练得更稳。",28,TEXT));content.addView(text("长按 ≡ 拖动排序 · 点击卡片编辑",13,MUTED));gap(content,18);
        List<Workout.Stage> stages=Workout.compile(plan);long seconds=0;int sets=0;for(Workout.Stage s:stages){seconds+=s.seconds;if(s.exercise())sets++;}
        int exercises=0;for(Workout.Item item:plan)if(!item.rest)exercises++;
        LinearLayout stats=row();weighted(stats,metric("训练动作",String.valueOf(exercises),TEXT));weighted(stats,metric("目标组数",String.valueOf(sets),ACCENT));weighted(stats,metric("预计用时",time((int)seconds),TEXT));content.addView(stats);gap(content,16);
        LinearLayout library=row();weighted(library,button("切换方案",CARD,()->choosePlan()));weighted(library,button("保存方案",CARD,()->savePlan()));content.addView(library);gap(content,18);
        content.addView(bold("训练清单",18,TEXT));gap(content,10);
        if(plan.isEmpty()){content.addView(text("还没有训练动作。添加动作，或从切换方案中选择一个模板。",15,MUTED));gap(content,16);}
        for(int n=0;n<plan.size();n++){
            final int index=n;Workout.Item item=plan.get(n);
            LinearLayout card=row();card.setPadding(dp(12),dp(12),dp(12),dp(12));card.setBackground(background(CARD,18));
            TextView handle=bold("≡",28,MUTED);handle.setGravity(Gravity.CENTER);handle.setContentDescription("长按拖动 "+item.name);card.addView(handle,new LinearLayout.LayoutParams(dp(42),dp(64)));
            handle.setOnLongClickListener(v->{if(active()){toast("结束训练后再修改计划");return true;}ClipData data=ClipData.newPlainText("training-item",String.valueOf(index));v.startDragAndDrop(data,new View.DragShadowBuilder(card),Integer.valueOf(index),0);return true;});
            LinearLayout labels=column();labels.addView(bold((item.rest?(item.big?"大休息":"小休息")+" · ":"")+item.name,18,item.rest?ORANGE:TEXT));labels.addView(text(item.rest?time(item.seconds):time(item.seconds)+" × "+item.sets+" 组",15,item.rest?MUTED:ACCENT));
            if(!item.rest)labels.addView(text("小休息 "+time(item.smallRest)+" / 大休息 "+time(item.bigRest),12,MUTED));
            card.addView(labels,new LinearLayout.LayoutParams(0,-2,1));card.addView(text("›",24,MUTED));card.setOnClickListener(v->{if(active())toast("结束训练后再修改计划");else edit(index,item.rest,item.big);});
            card.setOnDragListener((v,event)->{
                switch(event.getAction()){
                    case DragEvent.ACTION_DRAG_STARTED:return event.getLocalState() instanceof Integer;
                    case DragEvent.ACTION_DRAG_ENTERED:card.setBackground(background(0xff344736,18));return true;
                    case DragEvent.ACTION_DRAG_EXITED:card.setBackground(background(CARD,18));return true;
                    case DragEvent.ACTION_DROP:int from=(Integer)event.getLocalState();if(from!=index){Workout.Item moved=plan.remove(from);plan.add(index,moved);Store.save(this,plan);handler.post(()->showPlan());}return true;
                    case DragEvent.ACTION_DRAG_ENDED:card.setBackground(background(CARD,18));return true;
                    default:return true;
                }
            });
            content.addView(card,new LinearLayout.LayoutParams(-1,-2));gap(content,10);
        }
        LinearLayout add=row();weighted(add,button("＋ 动作",CARD,()->{if(active())toast("结束训练后再修改计划");else edit(-1,false,false);}));weighted(add,button("＋ 休息",CARD,()->{if(active())toast("结束训练后再修改计划");else new AlertDialog.Builder(this).setTitle("插入独立休息块").setItems(new String[]{"小休息","大休息"},(d,w)->edit(-1,true,w==1)).show();}));content.addView(add);gap(content,16);
        content.addView(text("小休息出现在组与组之间；最后一组后进入大休息。休息可设为 0 秒跳过，也可插入独立休息块自由排序。",12,MUTED));gap(content,20);
        content.addView(button(active()?"返回正在进行的训练":"开始训练 →",ACCENT,()->{if(active()){showTimer();return;}if(setsCount()==0){toast("至少添加一个训练动作");return;}start();}));gap(content,20);
    }
    private int setsCount(){int n=0;for(Workout.Item i:plan)if(!i.rest)n+=i.sets;return n;}
    private void savePlan(){
        if(active()){toast("结束训练后再保存方案");return;}
        if(setsCount()==0){toast("至少添加一个训练动作");return;}
        LinearLayout form=column();form.setPadding(dp(24),dp(8),dp(24),dp(8));EditText name=input(form,"方案名称（最多 24 字）","",false);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("保存当前训练方案").setView(form).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String title=name.getText().toString().trim();if(title.isEmpty()||title.length()>24){name.setError("请输入 1–24 字的方案名称");return;}
            Runnable save=()->{try{Store.savePlan(this,title,plan);dialog.dismiss();toast("方案已保存");}catch(JSONException error){toast("保存失败，请重试");}};
            if(Store.plans(this).has(title))new AlertDialog.Builder(this).setTitle("覆盖同名方案？").setMessage(title).setNegativeButton("取消",null).setPositiveButton("覆盖",(a,w)->save.run()).show();else save.run();
        }));dialog.show();
    }
    private void choosePlan(){
        if(active()){toast("结束训练后再切换方案");return;}
        JSONObject saved=Store.plans(this);List<String> names=new ArrayList<>();Iterator<String> keys=saved.keys();while(keys.hasNext())names.add(keys.next());Collections.sort(names);
        List<String> labels=new ArrayList<>(Arrays.asList("模板 · 全身基础","模板 · 核心训练","模板 · 下肢训练"));for(String name:names)labels.add("已保存 · "+name);
        new AlertDialog.Builder(this).setTitle("选择训练方案").setItems(labels.toArray(new String[0]),(d,which)->{
            List<Workout.Item> selected;
            if(which==0)selected=Arrays.asList(new Workout.Item("平板支撑",45,3,20,60),new Workout.Item("深蹲",60,3,30,60),new Workout.Item("臀桥",45,3,20,0));
            else if(which==1)selected=Arrays.asList(new Workout.Item("平板支撑",30,3,20,45),new Workout.Item("侧平板支撑",30,3,20,45),new Workout.Item("卷腹",30,3,20,0));
            else if(which==2)selected=Arrays.asList(new Workout.Item("深蹲",45,3,30,60),new Workout.Item("弓步蹲",45,3,30,60),new Workout.Item("臀桥",45,3,20,0));
            else try{selected=Store.parse(saved.getJSONArray(names.get(which-3)));}catch(JSONException error){toast("方案读取失败");return;}
            final List<Workout.Item> replacement=new ArrayList<>(selected);
            new AlertDialog.Builder(this).setTitle("替换当前训练计划？").setMessage("将使用「"+labels.get(which)+"」。当前清单如需保留，请先保存方案。模板时长和组数可以自由编辑。").setNegativeButton("取消",null).setPositiveButton("使用方案",(a,w)->{plan=replacement;Store.save(this,plan);showPlan();}).show();
        }).setNegativeButton("取消",null).setNeutralButton(names.isEmpty()?null:"删除已保存方案",(d,w)->{
            new AlertDialog.Builder(this).setTitle("删除哪个方案？").setItems(names.toArray(new String[0]),(a,index)->new AlertDialog.Builder(this).setTitle("删除「"+names.get(index)+"」？").setMessage("当前训练清单和历史记录会保留。").setNegativeButton("取消",null).setPositiveButton("删除",(b,n)->{Store.deletePlan(this,names.get(index));toast("方案已删除");}).show()).setNegativeButton("取消",null).show();
        }).show();
    }
    private EditText input(LinearLayout parent,String label,String value,boolean numeric){parent.addView(text(label,13,MUTED));EditText e=new EditText(this);e.setSingleLine(true);e.setText(value);e.setTextColor(TEXT);e.setTextSize(17);e.setInputType(numeric?android.text.InputType.TYPE_CLASS_NUMBER:android.text.InputType.TYPE_CLASS_TEXT);parent.addView(e,new LinearLayout.LayoutParams(-1,dp(48)));return e;}
    private int number(EditText e,int low,int high){int v=Integer.parseInt(e.getText().toString().trim());if(v<low||v>high)throw new IllegalArgumentException();return v;}
    private void edit(int index,boolean rest,boolean big){
        Workout.Item item=index>=0?plan.get(index):rest?Workout.Item.rest("自由休息",big?90:30,big):new Workout.Item("平板支撑",45,3,20,60);
        ScrollView scroll=new ScrollView(this);LinearLayout form=column();form.setPadding(dp(22),dp(12),dp(22),dp(12));form.setBackgroundColor(BG);scroll.addView(form);
        EditText name=input(form,rest?"休息名称":"动作名称（可自由输入）",item.name,false);
        if(!rest){Spinner names=new Spinner(this);String[] presets={"选择常用动作…","平板支撑","深蹲","俯卧撑","靠墙静蹲","臀桥","弓步蹲","卷腹","侧平板支撑","登山跑","俄罗斯转体","哑铃推举","哑铃划船"};ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,presets);names.setAdapter(adapter);form.addView(names);names.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int position,long id){if(position>0)name.setText(presets[position]);}});}
        EditText duration=input(form,"单次时长 / 秒（1–3600）",String.valueOf(item.seconds),true);
        LinearLayout quick=row();for(int s:new int[]{30,45,60,90})weighted(quick,button(time(s),CARD,()->duration.setText(String.valueOf(s))));form.addView(quick);
        EditText sets=rest?null:input(form,"训练组数（1–99）",String.valueOf(item.sets),true);
        EditText small=rest?null:input(form,"组间小休息 / 秒（0–3600）",String.valueOf(item.smallRest),true);
        EditText large=rest?null:input(form,"动作后大休息 / 秒（0–3600）",String.valueOf(item.bigRest),true);
        if(!rest)form.addView(text("最后一个动作的大休息也会执行；不需要时设为 0。",12,MUTED));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(rest?(big?"大休息":"小休息"):"设置训练动作").setView(scroll).setPositiveButton("保存",null).setNegativeButton("取消",null).setNeutralButton(index>=0?"更多":null,null).create();
        dialog.setOnShowListener(d->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                try{String title=name.getText().toString().trim();if(title.isEmpty()||title.length()>40)throw new IllegalArgumentException();int secs=number(duration,1,3600);Workout.Item updated=rest?Workout.Item.rest(title,secs,big):new Workout.Item(title,secs,number(sets,1,99),number(small,0,3600),number(large,0,3600));if(index>=0)plan.set(index,updated);else plan.add(updated);Store.save(this,plan);dialog.dismiss();showPlan();}catch(Exception e){toast("请填写名称，并检查时长和组数范围");}
            });
            if(index>=0)dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->new AlertDialog.Builder(this).setTitle("调整项目").setItems(new String[]{"上移","下移","复制","删除"},(a,w)->{
                if(w==0&&index>0)Collections.swap(plan,index,index-1);
                if(w==1&&index<plan.size()-1)Collections.swap(plan,index,index+1);
                if(w==2){Workout.Item copy=new Workout.Item(item.name,item.seconds,item.sets,item.smallRest,item.bigRest);copy.rest=item.rest;copy.big=item.big;plan.add(index+1,copy);}
                if(w==3)plan.remove(index);Store.save(this,plan);dialog.dismiss();showPlan();
            }).show());
        });dialog.show();
    }
    private void start(){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},7);
        TimerService.engine=new Workout.Engine(Workout.compile(plan),TimerService.now());wasFinished=false;startForegroundService(new Intent(this,TimerService.class).setAction("START"));showTimer();
    }
    private void command(String action){startForegroundService(new Intent(this,TimerService.class).setAction(action));}
    private void showTimer(){
        base("timer");Workout.Engine e=TimerService.engine;if(e==null){showPlan();return;}
        if(e.finished){
            wasFinished=true;gap(content,36);content.addView(bold("训练完成 ✓",32,ACCENT));content.addView(text("每一次坚持，都算数。",16,MUTED));gap(content,28);content.addView(bold(e.completed+" 组",56,TEXT));content.addView(text("有效训练 "+time((int)(e.trainedMillis/1000))+" · 跳过 "+e.skipped+" 组",15,MUTED));
            for(Map.Entry<String,Integer> entry:e.counts.entrySet())content.addView(text(entry.getKey()+"  ·  "+entry.getValue()+" 组",18,TEXT));gap(content,24);content.addView(button("查看训练记录",ACCENT,()->showHistory()));gap(content,12);content.addView(button("回到训练计划",CARD,()->showPlan()));return;
        }
        gap(content,24);stageMeta=bold("",15,ACCENT);stageMeta.setGravity(Gravity.CENTER);content.addView(stageMeta);stageTitle=bold("",27,TEXT);stageTitle.setGravity(Gravity.CENTER);content.addView(stageTitle);gap(content,24);
        FrameLayout dial=new FrameLayout(this);ring=new CountdownRing(this);dial.addView(ring,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout display=column();display.setGravity(Gravity.CENTER);display.setPadding(dp(32),dp(12),dp(32),dp(12));timer=bold("",64,ACCENT);timer.setGravity(Gravity.CENTER);timer.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);timer.setAutoSizeTextTypeUniformWithConfiguration(28,64,2,android.util.TypedValue.COMPLEX_UNIT_SP);display.addView(timer,new LinearLayout.LayoutParams(-1,dp(90)));countsText=text("",14,MUTED);countsText.setGravity(Gravity.CENTER);display.addView(countsText);dial.addView(display,new FrameLayout.LayoutParams(-1,-1));content.addView(dial,new LinearLayout.LayoutParams(-1,dp(280)));gap(content,22);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));content.addView(progress,new LinearLayout.LayoutParams(-1,dp(6)));gap(content,16);nextText=text("",14,MUTED);content.addView(nextText);gap(content,24);
        LinearLayout controls=row();pauseButton=button(e.paused?"继续":"暂停",ACCENT,()->command("PAUSE"));weighted(controls,pauseButton);weighted(controls,button("跳过当前阶段",CARD,()->new AlertDialog.Builder(this).setTitle("跳过当前阶段？").setMessage("跳过的训练组不会计入完成次数。").setPositiveButton("跳过",(d,w)->command("SKIP")).setNegativeButton("取消",null).show()));content.addView(controls);gap(content,12);
        content.addView(button("结束本次训练",CARD,()->new AlertDialog.Builder(this).setTitle("结束训练？").setMessage("已完成的组数会保存到训练记录。").setPositiveButton("结束并保存",(d,w)->{command("STOP");handler.postDelayed(()->showHistory(),300);}).setNegativeButton("继续训练",null).show()));gap(content,18);
        content.addView(text("切换应用或锁屏后仍会计时。若被系统强制关闭，下次打开会恢复到暂停状态。",12,MUTED));updateTimer();
    }
    private void updateTimer(){
        Workout.Engine e=TimerService.engine;if(e==null||e.finished||timer==null)return;Workout.Stage s=e.current();
        stageMeta.setText((e.paused?"已暂停 · ":"")+s.kind+" / "+(e.index+1)+" OF "+e.stages.size());stageMeta.setTextColor(s.exercise()?ACCENT:ORANGE);stageTitle.setText(s.name);timer.setText(clock(e.remaining(TimerService.now())));timer.setTextColor(s.exercise()?ACCENT:ORANGE);countsText.setText(s.exercise()?"第 "+s.set+" / "+s.sets+" 组 · 已完成 "+e.completed+" 组":"放松呼吸 · 已完成 "+e.completed+" 组");pauseButton.setText(e.paused?"继续":"暂停");
        progress.setProgress((int)(1000*e.elapsedMillis(TimerService.now())/Math.max(1,e.totalMillis())));
        ring.update(e.remaining(TimerService.now()),s.seconds*1000L,s.exercise()?ACCENT:ORANGE);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(s.exercise()?ACCENT:ORANGE));
        nextText.setText(e.index+1<e.stages.size()?"接下来  /  "+e.stages.get(e.index+1).name+" · "+time(e.stages.get(e.index+1).seconds):"接下来  /  训练完成");
    }
    private void showHistory(){
        base("history");content.addView(bold("每一组，都留下痕迹。",26,TEXT));content.addView(text("次数按完成的计时组记录，不是动作重复次数。",12,MUTED));gap(content,20);
        JSONArray records=Store.history(this);int total=0;long duration=0;for(int n=0;n<records.length();n++){JSONObject o=records.optJSONObject(n);total+=o.optInt("count");duration+=o.optLong("seconds");}
        LinearLayout stats=row();weighted(stats,metric("累计训练",records.length()+" 次",TEXT));weighted(stats,metric("完成组数",total+" 组",ACCENT));weighted(stats,metric("有效时长",time((int)duration),TEXT));content.addView(stats);gap(content,12);content.addView(text("最近 200 条记录 · 每次坚持都值得记录",13,MUTED));gap(content,20);
        if(records.length()==0)content.addView(text("开始第一场训练，记录就会出现在这里。",16,TEXT));
        SimpleDateFormat date=new SimpleDateFormat("MM月dd日 HH:mm",Locale.CHINA);
        for(int n=0;n<records.length();n++){
            JSONObject o=records.optJSONObject(n);LinearLayout card=column();card.setPadding(dp(16),dp(14),dp(16),dp(14));card.setBackground(background(CARD,18));card.addView(bold(date.format(new Date(o.optLong("date")))+" · "+(o.optBoolean("complete")?"已完成":"提前结束"),17,TEXT));card.addView(text(o.optInt("count")+" 组 · 有效训练 "+time(o.optInt("seconds"))+" · 跳过 "+o.optInt("skipped")+" 组",14,ACCENT));JSONObject counts=o.optJSONObject("counts");if(counts!=null){Iterator<String> keys=counts.keys();while(keys.hasNext()){String k=keys.next();card.addView(text(k+"  × "+counts.optInt(k)+" 组",14,MUTED));}}content.addView(card);gap(content,12);
        }gap(content,16);
    }
    private void settings(){
        LinearLayout box=column();box.setPadding(dp(24),dp(10),dp(24),dp(16));box.setBackgroundColor(BG);
        Switch sound=new Switch(this);sound.setText("阶段结束提示音");sound.setTextColor(TEXT);sound.setChecked(Store.prefs(this).getBoolean("sound",true));box.addView(sound,new LinearLayout.LayoutParams(-1,dp(56)));sound.setOnCheckedChangeListener((b,on)->Store.prefs(this).edit().putBoolean("sound",on).apply());
        Switch vibration=new Switch(this);vibration.setText("阶段结束振动");vibration.setTextColor(TEXT);vibration.setChecked(Store.prefs(this).getBoolean("vibrate",true));box.addView(vibration,new LinearLayout.LayoutParams(-1,dp(56)));vibration.setOnCheckedChangeListener((b,on)->Store.prefs(this).edit().putBoolean("vibrate",on).apply());
        box.addView(text("力量计时 1.1\n离线使用 · 数据保存在本机\n提示音音量跟随手机闹钟音量",13,MUTED));new AlertDialog.Builder(this).setTitle("训练设置").setView(box).setPositiveButton("完成",null).show();
    }
}
