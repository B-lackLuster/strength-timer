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
import java.io.*;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int BG=0xff101711,CARD=0xff1e2921,ACCENT=0xffcaef76,TEXT=0xfff2f5ed,MUTED=0xffa1afa4,ORANGE=0xffffbb7c;
    private LinearLayout root,content,header,pageRoot;
    private FrameLayout pageHost;
    private PageTransition pageTransition;
    private final Deque<String> backStack=new ArrayDeque<>();
    private final Map<String,Integer> scrollPositions=new HashMap<>();
    private boolean returning;
    private String pendingExport;
    private static final int EXPORT_REQUEST=81;
    private TextView brand;
    private TextView timer,stageTitle,stageMeta,nextText,countsText,pauseButton,doneButton;
    private int displayedStage=-1;
    private ProgressBar progress;
    private CountdownRing ring;
    private List<Workout.Item> plan;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String page="plan";
    private boolean wasFinished=false;
    private ScrollView scroller;
    private final List<Dialog> dialogs=new ArrayList<>();
    private final Runnable refresh=new Runnable(){public void run(){
        Workout.Engine e=TimerService.engine;
        if(page.equals("timer")&&e!=null){if((e.finished&&!wasFinished)||(!e.finished&&e.index!=displayedStage)){wasFinished=e.finished;showTimer();}else updateTimer();}
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
        setContentView(root);root.requestApplyInsets();Chime.init(this);
        if(TimerService.engine==null)TimerService.engine=Store.restore(this);
        if(b!=null){ArrayList<String> stack=b.getStringArrayList("backStack");if(stack!=null)backStack.addAll(stack);Bundle positions=b.getBundle("scrollPositions");if(positions!=null)for(String key:positions.keySet())scrollPositions.put(key,positions.getInt(key));pendingExport=b.getString("pendingExport");}
        String restored=b==null?"":b.getString("page","");
        if(restored.equals("history")||restored.equals("settings")||restored.equals("plan"))renderPage(restored);else if(TimerService.engine!=null&&(restored.equals("timer")||!TimerService.engine.finished))showTimer();else showPlan();
    }
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresh);handler.post(refresh);}
    @Override protected void onPause(){super.onPause();handler.removeCallbacks(refresh);}
    @Override protected void onDestroy(){if(pageTransition!=null)pageTransition.finish();for(Dialog dialog:new ArrayList<>(dialogs))dialog.dismiss();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("page",page);state.putStringArrayList("backStack",new ArrayList<>(backStack));if(scroller!=null)scrollPositions.put(page,scroller.getScrollY());Bundle positions=new Bundle();for(Map.Entry<String,Integer> entry:scrollPositions.entrySet())positions.putInt(entry.getKey(),entry.getValue());state.putBundle("scrollPositions",positions);state.putString("pendingExport",pendingExport);}
    @Override public void onBackPressed(){if(!page.equals("plan"))navigateBack();else super.onBackPressed();}
    private void openPage(String target){if(page.equals(target))return;backStack.addLast(page);renderPage(target);}
    private void renderPage(String target){if(target.equals("history"))showHistory();else if(target.equals("settings"))settings();else if(target.equals("timer")&&TimerService.engine!=null)showTimer();else showPlan();}
    private void navigateBack(){String target=backStack.isEmpty()?"plan":backStack.removeLast();returning=true;try{renderPage(target);}finally{returning=false;}}
    private void historyAfterEnd(){backStack.clear();backStack.addLast("plan");showHistory();}
    private boolean active(){return TimerService.engine!=null&&!TimerService.engine.finished;}
    private GradientDrawable background(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(color==CARD)d.setStroke(dp(1),0xff344039);return d;}
    private android.graphics.drawable.LayerDrawable roundedTrack(int radius){android.graphics.drawable.LayerDrawable track=new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{background(0xff344039,radius),new android.graphics.drawable.ScaleDrawable(background(ACCENT,radius),Gravity.LEFT,1f,0f)});track.setId(0,android.R.id.background);track.setId(1,android.R.id.progress);return track;}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(0,dp(3),0,dp(3));return t;}
    private TextView bold(String value,int size,int color){TextView t=text(value,size,color);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private void buttonStyle(TextView button,int color){button.setTextColor(color==ACCENT?BG:TEXT);button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33ffffff),background(color,16),background(Color.WHITE,16)));}
    private TextView button(String value,int color,Runnable action){TextView t=bold(value,15,TEXT);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(48));t.setPadding(dp(14),dp(10),dp(14),dp(10));buttonStyle(t,color);t.setFocusable(true);t.setOnClickListener(v->action.run());return t;}
    private View metric(String label,String value,int color){LinearLayout box=column();box.setPadding(dp(12),dp(14),dp(12),dp(14));box.setBackground(background(CARD,16));TextView number=bold(value,22,color);number.setAutoSizeTextTypeUniformWithConfiguration(12,22,1,android.util.TypedValue.COMPLEX_UNIT_SP);box.addView(number,new LinearLayout.LayoutParams(-1,dp(30)));box.addView(text(label,12,MUTED));return box;}
    private void gap(LinearLayout v,int h){Space s=new Space(this);v.addView(s,new LinearLayout.LayoutParams(1,dp(h)));}
    private void weighted(LinearLayout r,View v){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),0,dp(3),0);r.addView(v,lp);}
    private void base(String selected){
        boolean animate=header!=null&&!page.equals(selected);
        if(scroller!=null)scrollPositions.put(page,scroller.getScrollY());
        page=selected;
        if(header==null){
            header=row();header.setPadding(0,dp(10),0,dp(10));brand=bold("",17,ACCENT);brand.setMinHeight(dp(48));header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
            header.addView(button("训练记录",BG,()->openPage("history")));header.addView(button("设置",BG,()->openPage("settings")));root.addView(header);
            pageHost=new FrameLayout(this);root.addView(pageHost,new LinearLayout.LayoutParams(-1,0,1));pageTransition=new PageTransition(pageHost);
        }
        String previous=backStack.isEmpty()?"plan":backStack.peekLast();
        brand.setText(selected.equals("plan")?"力量 / TIMER":"‹ 返回");brand.setContentDescription(selected.equals("plan")?"力量计时":previous.equals("timer")&&active()?"返回正在进行的训练":previous.equals("history")?"返回训练记录":previous.equals("settings")?"返回设置":"返回训练计划");
        brand.setOnClickListener(selected.equals("plan")?null:v->navigateBack());
        pageRoot=column();pageRoot.setBackgroundColor(BG);scroller=new ScrollView(this);scroller.setFillViewport(true);styleScroll(scroller);content=column();scroller.addView(content);pageRoot.addView(scroller,new LinearLayout.LayoutParams(-1,0,1));
        pageTransition.replace(pageRoot,animate,returning);final ScrollView restoredScroll=scroller;final int restoredY=scrollPositions.getOrDefault(selected,0);restoredScroll.post(()->restoredScroll.scrollTo(0,restoredY));
    }
    private void toast(String value){TextView message=text(value,14,TEXT);message.setPadding(dp(18),dp(12),dp(18),dp(12));message.setBackground(background(CARD,18));Toast hint=new Toast(this);hint.setView(message);hint.setDuration(Toast.LENGTH_SHORT);hint.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,0,dp(88));hint.show();}
    private void styleScroll(ScrollView scroll){
        scroll.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_INSET);scroll.setVerticalScrollbarPosition(View.SCROLLBAR_POSITION_RIGHT);
        scroll.setScrollBarSize(dp(2));scroll.setScrollbarFadingEnabled(true);scroll.setScrollBarDefaultDelayBeforeFade(500);scroll.setScrollBarFadeDuration(250);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);scroll.setPadding(0,0,dp(6),0);
        if(Build.VERSION.SDK_INT>=29){scroll.setVerticalScrollbarThumbDrawable(background(0xff53635a,2));scroll.setVerticalScrollbarTrackDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));}
    }
    private void showPlan(){
        base("plan");content.addView(text("WORKOUT / 今日训练",12,ACCENT));content.addView(bold("今天，练得更稳。",28,TEXT));content.addView(text("长按 ≡ 拖动排序 · 点击卡片编辑",13,MUTED));gap(content,18);
        List<Workout.Stage> stages=Workout.compileSession(plan);long seconds=0;int sets=0;for(Workout.Stage s:stages){seconds+=s.seconds;if(s.exercise())sets++;}
        int exercises=0,manuals=0;for(Workout.Item item:plan)if(!item.rest){exercises++;if(item.manual)manuals++;}
        LinearLayout stats=row();weighted(stats,metric("训练动作",String.valueOf(exercises),TEXT));weighted(stats,metric("目标组数",String.valueOf(sets),ACCENT));weighted(stats,metric(manuals>0?"固定用时":"预计用时",time((int)seconds),TEXT));content.addView(stats);if(manuals>0)content.addView(text("另含 "+manuals+" 个次数动作，完成时间由你决定。",12,MUTED));gap(content,16);
        LinearLayout library=row();weighted(library,button("切换方案",CARD,()->choosePlan()));weighted(library,button("保存方案",CARD,()->savePlan()));content.addView(library);gap(content,18);
        content.addView(bold("训练清单",18,TEXT));gap(content,10);
        if(plan.isEmpty()){content.addView(text("还没有训练动作。添加动作，或从切换方案中选择一个模板。",15,MUTED));gap(content,16);}
        for(int n=0;n<plan.size();n++){
            final int index=n;Workout.Item item=plan.get(n);
            LinearLayout card=row();card.setPadding(dp(12),dp(12),dp(12),dp(12));card.setBackground(background(CARD,18));
            TextView handle=bold("≡",28,MUTED);handle.setGravity(Gravity.CENTER);handle.setContentDescription("长按拖动 "+item.name);card.addView(handle,new LinearLayout.LayoutParams(dp(42),dp(64)));
            handle.setOnLongClickListener(v->{if(active()){toast("结束训练后再修改计划");return true;}ClipData data=ClipData.newPlainText("training-item",String.valueOf(index));v.startDragAndDrop(data,new View.DragShadowBuilder(card),Integer.valueOf(index),0);return true;});
            LinearLayout labels=column();labels.addView(bold((item.rest?(item.big?"大休息":"小休息")+" · ":"")+item.name,18,item.rest?ORANGE:TEXT));labels.addView(text(item.rest?time(item.seconds):(item.manual?item.repetitions+" 次":time(item.seconds))+" × "+item.sets+" 组"+(item.manual?" · 非计时":""),15,item.rest?MUTED:ACCENT));
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
        LinearLayout add=row();weighted(add,button("＋ 动作",CARD,()->{if(active())toast("结束训练后再修改计划");else edit(-1,false,false);}));weighted(add,button("＋ 休息",CARD,()->{if(active())toast("结束训练后再修改计划");else edit(-1,true,false);}));content.addView(add);gap(content,16);
        content.addView(text("开始后先准备 5 秒，再进入第一项任务。小休息出现在组与组之间；最后一组后进入大休息。休息可设为 0 秒跳过，也可插入独立休息块自由排序。",12,MUTED));gap(content,20);
        LinearLayout dock=column();dock.setPadding(0,dp(12),0,dp(2));dock.setBackgroundColor(BG);
        dock.addView(button(active()?"返回正在进行的训练":"开始训练 →",ACCENT,()->{if(active()){showTimer();return;}if(setsCount()==0){toast("至少添加一个训练动作");return;}start();}));
        pageRoot.addView(dock,new LinearLayout.LayoutParams(-1,-2));
    }
    private int setsCount(){int n=0;for(Workout.Item i:plan)if(!i.rest)n+=i.sets;return n;}
    private void savePlan(){
        if(active()){toast("结束训练后再保存方案");return;}
        if(setsCount()==0){toast("至少添加一个训练动作");return;}
        Panel panel=new Panel("LIBRARY / 我的方案","保存当前训练方案","为这份清单起个名字，下次直接开始");EditText name=input(panel.body,"方案名称（最多 24 字）","",false);
        panel.body.addView(text("动作、组数与休息时长都会保存在本机。",13,MUTED));
        LinearLayout controls=row();weighted(controls,button("取消",CARD,()->panel.dialog.dismiss()));weighted(controls,button("保存",ACCENT,()->{
            String title=name.getText().toString().trim();if(title.isEmpty()||title.length()>24){fieldError(name,"请输入 1–24 字的方案名称");return;}
            Runnable save=()->{try{Store.savePlan(this,title,plan);panel.dialog.dismiss();toast("方案已保存");}catch(JSONException error){toast("保存失败，请重试");}};
            if(Store.plans(this).has(title))confirm("覆盖同名方案？","将替换「"+title+"」的动作与休息安排。", "覆盖",save);else save.run();
        }));panel.footer.addView(controls);panel.show(410);
    }
    private void choosePlan(){
        if(active()){toast("结束训练后再切换方案");return;}
        JSONObject saved=Store.plans(this);List<String> names=new ArrayList<>();Iterator<String> keys=saved.keys();while(keys.hasNext())names.add(keys.next());Collections.sort(names);
        List<String> labels=new ArrayList<>(Arrays.asList("模板 · 全身基础","模板 · 核心训练","模板 · 下肢训练"));for(String name:names)labels.add("已保存 · "+name);
        Panel panel=new Panel("LIBRARY / 训练方案","选择训练方案","从模板或自己的方案中，选择今天的训练");
        for(int n=0;n<labels.size();n++){
            final int which=n;
            if(n==0||n==3){panel.body.addView(text(n==0?"内置模板":"我的方案",12,MUTED));gap(panel.body,8);}
            panel.body.addView(button(labels.get(n),CARD,()->{
                List<Workout.Item> selected;
                if(which==0)selected=Arrays.asList(new Workout.Item("平板支撑",45,3,20,60),new Workout.Item("深蹲",60,3,30,60),new Workout.Item("臀桥",45,3,20,0));
                else if(which==1)selected=Arrays.asList(new Workout.Item("平板支撑",30,3,20,45),new Workout.Item("侧平板支撑",30,3,20,45),new Workout.Item("卷腹",30,3,20,0));
                else if(which==2)selected=Arrays.asList(new Workout.Item("深蹲",45,3,30,60),new Workout.Item("弓步蹲",45,3,30,60),new Workout.Item("臀桥",45,3,20,0));
                else try{selected=Store.parse(saved.getJSONArray(names.get(which-3)));}catch(JSONException error){toast("方案读取失败");return;}
                final List<Workout.Item> replacement=new ArrayList<>(selected);
                confirm("替换当前训练计划？","将使用「"+labels.get(which)+"」。当前清单如需保留，请先保存方案。模板时长和组数可以自由编辑。","使用方案",()->{plan=replacement;Store.save(this,plan);panel.dialog.dismiss();showPlan();});
            }));gap(panel.body,10);
        }
        if(names.isEmpty())panel.body.addView(text("保存当前清单后，就会出现在「我的方案」中。",13,MUTED));
        else{gap(panel.body,8);panel.body.addView(button("删除已保存方案",CARD,()->deletePlan(names,panel)));}
        panel.footer.addView(button("取消",CARD,()->panel.dialog.dismiss()));panel.show(620);
    }
    private void deletePlan(List<String> names,Panel chooser){
        Panel panel=new Panel("LIBRARY / 管理方案","删除哪个方案？","删除保存的方案，当前训练清单和记录会保留");
        for(String name:names){panel.body.addView(button(name,CARD,()->confirm("删除「"+name+"」？","此操作无法撤销。当前训练清单和历史记录会保留。","删除",()->{Store.deletePlan(this,name);panel.dialog.dismiss();chooser.dialog.dismiss();toast("方案已删除");choosePlan();})));gap(panel.body,10);}
        panel.footer.addView(button("取消",CARD,()->panel.dialog.dismiss()));panel.show(560);
    }
    private void confirm(String title,String message,String actionLabel,Runnable action){confirm(title,message,"取消",actionLabel,action);}
    private void confirm(String title,String message,String cancelLabel,String actionLabel,Runnable action){
        Panel panel=new Panel("CONFIRM / 确认操作",title,"");
        TextView detail=text(message,15,TEXT);detail.setPadding(dp(16),dp(16),dp(16),dp(16));detail.setBackground(background(CARD,18));panel.body.addView(detail);
        LinearLayout controls=row();weighted(controls,button(cancelLabel,CARD,()->panel.dialog.dismiss()));weighted(controls,button(actionLabel,ACCENT,()->{action.run();panel.dialog.dismiss();}));panel.footer.addView(controls);panel.show(390);
    }
    private final class Panel {
        final Dialog dialog=new Dialog(MainActivity.this);
        final SheetLayout sheet=new SheetLayout(MainActivity.this,()->dialog.dismiss());
        final LinearLayout body=column(),footer=column();
        Panel(String label,String title,String subtitle){
            sheet.setPadding(dp(20),0,dp(20),dp(16));sheet.setBackground(background(BG,24));
            LinearLayout heading=column();FrameLayout dragArea=new FrameLayout(MainActivity.this);dragArea.setContentDescription("下拉关闭");dragArea.setFocusable(true);dragArea.setOnClickListener(v->dialog.dismiss());
            View handle=new View(MainActivity.this);handle.setBackground(background(0xff53635a,2));dragArea.addView(handle,new FrameLayout.LayoutParams(dp(36),dp(4),Gravity.CENTER));heading.addView(dragArea,new LinearLayout.LayoutParams(-1,dp(40)));
            heading.addView(text(label,11,ACCENT));heading.addView(bold(title,26,TEXT));if(!subtitle.isEmpty())heading.addView(text(subtitle,13,MUTED));gap(heading,16);sheet.addView(heading);
            ScrollView scroll=new ScrollView(MainActivity.this);styleScroll(scroll);scroll.setFocusableInTouchMode(true);scroll.addView(body);sheet.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));gap(sheet,12);sheet.addView(footer);sheet.setDragRegions(heading,scroll);
            dialog.setContentView(sheet);dialog.setOnDismissListener(d->dialogs.remove(dialog));
        }
        void show(int height){
            Window window=dialog.getWindow();
            if(window!=null){window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);WindowManager.LayoutParams params=window.getAttributes();params.dimAmount=.6f;params.gravity=Gravity.BOTTOM;window.setAttributes(params);}
            dialogs.add(dialog);dialog.show();if(window!=null)window.setLayout(getResources().getDisplayMetrics().widthPixels-dp(24),Math.min(dp(height),(int)(getResources().getDisplayMetrics().heightPixels*.86f)));
        }
    }
    private EditText input(LinearLayout parent,String label,String value,boolean numeric){
        LinearLayout card=column();card.setPadding(dp(16),dp(12),dp(16),dp(12));card.setBackground(background(CARD,18));card.addView(text(label,12,MUTED));
        EditText e=new EditText(this);e.setSingleLine(true);e.setInputType(numeric?android.text.InputType.TYPE_CLASS_NUMBER:android.text.InputType.TYPE_CLASS_TEXT);e.setText(value);e.setTextColor(TEXT);e.setTextSize(18);e.setContentDescription(label);e.setSelectAllOnFocus(true);e.setPadding(dp(12),dp(6),dp(12),dp(6));e.setBackground(background(BG,12));e.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);card.addView(e,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView error=text("",12,ORANGE);error.setVisibility(View.GONE);error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);card.addView(error);e.setTag(error);
        e.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){error.setVisibility(View.GONE);}public void afterTextChanged(android.text.Editable value){}});
        parent.addView(card);gap(parent,10);return e;
    }
    private void fieldError(EditText field,String message){TextView error=(TextView)field.getTag();error.setText(message);error.setVisibility(View.VISIBLE);}
    private int number(EditText e,int low,int high){try{int v=Integer.parseInt(e.getText().toString().trim());if(v<low||v>high)throw new IllegalArgumentException();return v;}catch(IllegalArgumentException error){fieldError(e,"请输入 "+low+"–"+high+" 之间的整数");throw error;}}
    private int retainedNumber(String value,int fallback,int high){try{int n=Integer.parseInt(value.trim());return n>=1&&n<=high?n:fallback;}catch(NumberFormatException error){return fallback;}}
    private void edit(int index,boolean rest,boolean big){
        Workout.Item item=index>=0?plan.get(index):rest?Workout.Item.rest("自由休息",big?90:30,big):new Workout.Item("平板支撑",45,3,20,60);
        Panel panel=new Panel(rest?"RECOVERY / 自由休息":"EXERCISE / 训练动作",rest?(index<0?"添加休息":"设置休息"):(index<0?"添加训练动作":"设置训练动作"),rest?"插入清单，自由安排恢复节奏":"按自己的节奏，安排每一组训练");LinearLayout form=panel.body;
        final boolean[] restBig={big};
        if(rest){
            LinearLayout types=row();TextView smallType=button("小休息",big?CARD:ACCENT,()->{}),largeType=button("大休息",big?ACCENT:CARD,()->{});weighted(types,smallType);weighted(types,largeType);form.addView(types);gap(form,8);form.addView(text("休息类型仅用于标记；独立休息按清单顺序执行。",12,MUTED));gap(form,12);
            smallType.setOnClickListener(v->{restBig[0]=false;buttonStyle(smallType,ACCENT);buttonStyle(largeType,CARD);});
            largeType.setOnClickListener(v->{restBig[0]=true;buttonStyle(largeType,ACCENT);buttonStyle(smallType,CARD);});
        }
        EditText name=input(form,rest?"休息名称":"动作名称（可自由输入）",item.name,false);
        if(!rest){
            LinearLayout choices=column();choices.setVisibility(View.GONE);String[] presets={"平板支撑","深蹲","俯卧撑","靠墙静蹲","臀桥","弓步蹲","卷腹","侧平板支撑","登山跑","俄罗斯转体","哑铃推举","哑铃划船"};
            form.addView(button("选择常用动作  ›",CARD,()->choices.setVisibility(choices.getVisibility()==View.GONE?View.VISIBLE:View.GONE)));gap(form,8);
            for(int n=0;n<presets.length;n+=2){LinearLayout pair=row();for(int j=n;j<Math.min(n+2,presets.length);j++){String preset=presets[j];weighted(pair,button(preset,CARD,()->{name.setText(preset);choices.setVisibility(View.GONE);}));}choices.addView(pair);gap(choices,8);}form.addView(choices);gap(form,4);
        }
        final boolean[] manual={item.manual&&!rest};final String[] values={String.valueOf(item.seconds),String.valueOf(item.repetitions)};
        Switch mode=rest?null:styledToggle("非计时训练",manual[0]);
        if(!rest){LinearLayout modeCard=row();modeCard.setPadding(dp(16),dp(12),dp(12),dp(12));modeCard.setBackground(background(CARD,18));LinearLayout labels=column();labels.addView(bold("非计时（按次数）",15,TEXT));labels.addView(text("完成每组目标后，手动点击完成",12,MUTED));modeCard.addView(labels,new LinearLayout.LayoutParams(0,-2,1));modeCard.addView(mode);modeCard.setOnClickListener(v->mode.setChecked(!mode.isChecked()));form.addView(modeCard);gap(form,10);}
        EditText duration=input(form,manual[0]?"每组次数（1–999）":"单次时长 / 秒（1–3600）",values[manual[0]?1:0],true);
        LinearLayout quick=row();Runnable quickChoices=()->{quick.removeAllViews();for(int value:manual[0]?new int[]{8,10,12,15}:new int[]{30,45,60,90}){TextView preset=button(manual[0]?value+" 次":time(value),CARD,()->duration.setText(String.valueOf(value)));preset.setPadding(dp(4),dp(10),dp(4),dp(10));weighted(quick,preset);}};quickChoices.run();form.addView(quick);gap(form,14);
        if(mode!=null)mode.setOnCheckedChangeListener((view,on)->{values[manual[0]?1:0]=duration.getText().toString();manual[0]=on;String label=on?"每组次数（1–999）":"单次时长 / 秒（1–3600）";((TextView)((LinearLayout)duration.getParent()).getChildAt(0)).setText(label);duration.setContentDescription(label);duration.setText(values[on?1:0]);quickChoices.run();});
        EditText sets=rest?null:input(form,"训练组数（1–99）",String.valueOf(item.sets),true);
        EditText small=rest?null:input(form,"组间小休息 / 秒（0–3600）",String.valueOf(item.smallRest),true);
        EditText large=rest?null:input(form,"动作后大休息 / 秒（0–3600）",String.valueOf(item.bigRest),true);
        if(!rest)form.addView(text("最后一个动作的大休息也会执行；不需要时设为 0。",12,MUTED));
        if(index>=0){
            gap(form,16);form.addView(text("调整项目",12,MUTED));gap(form,6);LinearLayout reorder=row();weighted(reorder,button("上移",CARD,()->{if(index>0){Collections.swap(plan,index,index-1);Store.save(this,plan);panel.dialog.dismiss();showPlan();}}));weighted(reorder,button("下移",CARD,()->{if(index<plan.size()-1){Collections.swap(plan,index,index+1);Store.save(this,plan);panel.dialog.dismiss();showPlan();}}));form.addView(reorder);gap(form,8);
            LinearLayout actions=row();weighted(actions,button("复制",CARD,()->{Workout.Item copy=new Workout.Item(item.name,item.seconds,item.sets,item.smallRest,item.bigRest);copy.rest=item.rest;copy.big=item.big;copy.manual=item.manual;copy.repetitions=item.repetitions;plan.add(index+1,copy);Store.save(this,plan);panel.dialog.dismiss();showPlan();}));weighted(actions,button("删除",CARD,()->{plan.remove(index);Store.save(this,plan);panel.dialog.dismiss();showPlan();}));form.addView(actions);
        }
        LinearLayout controls=row();weighted(controls,button("取消",CARD,()->panel.dialog.dismiss()));weighted(controls,button("保存",ACCENT,()->{
            try{String title=name.getText().toString().trim();if(title.isEmpty()||title.length()>40){fieldError(name,"请输入 1–40 字的名称");return;}int value=number(duration,1,manual[0]?999:3600);Workout.Item updated=rest?Workout.Item.rest(title,value,restBig[0]):new Workout.Item(title,manual[0]?retainedNumber(values[0],item.seconds,3600):value,number(sets,1,99),number(small,0,3600),number(large,0,3600));updated.manual=manual[0];updated.repetitions=manual[0]?value:retainedNumber(values[1],item.repetitions,999);if(index>=0)plan.set(index,updated);else plan.add(updated);Store.save(this,plan);panel.dialog.dismiss();showPlan();}catch(IllegalArgumentException e){toast("请检查时长、次数和组数范围");}
        }));panel.footer.addView(controls);panel.show(rest?570:740);
    }
    private void start(){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},7);
        TimerService.engine=new Workout.Engine(Workout.compileSession(plan),TimerService.now());wasFinished=false;startForegroundService(new Intent(this,TimerService.class).setAction("START"));openPage("timer");
    }
    private void command(String action){startForegroundService(new Intent(this,TimerService.class).setAction(action));}
    private String stageDetail(Workout.Stage stage){return stage.manual?stage.repetitions+" 次 · 手动完成":time(stage.seconds);}
    private void showTimer(){
        base("timer");Workout.Engine e=TimerService.engine;if(e==null){showPlan();return;}displayedStage=e.index;doneButton=null;
        if(e.finished){
            wasFinished=true;gap(content,36);content.addView(bold("训练完成 ✓",32,ACCENT));content.addView(text("每一次坚持，都算数。",16,MUTED));gap(content,28);content.addView(bold(e.completed+" 组",56,TEXT));content.addView(text("有效训练 "+time((int)(e.trainedMillis/1000))+" · 跳过 "+e.skipped+" 组",15,MUTED));
            for(Map.Entry<String,Integer> entry:e.counts.entrySet())content.addView(text(entry.getKey()+"  ·  "+entry.getValue()+" 组",18,TEXT));gap(content,24);content.addView(button("查看训练记录",ACCENT,()->openPage("history")));gap(content,12);content.addView(button("回到训练计划",CARD,()->{backStack.clear();returning=true;showPlan();returning=false;}));return;
        }
        gap(content,24);stageMeta=bold("",15,ACCENT);stageMeta.setGravity(Gravity.CENTER);content.addView(stageMeta);stageTitle=bold("",27,TEXT);stageTitle.setGravity(Gravity.CENTER);content.addView(stageTitle);gap(content,24);
        FrameLayout dial=new FrameLayout(this);ring=new CountdownRing(this);dial.addView(ring,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout display=column();display.setGravity(Gravity.CENTER);display.setPadding(dp(32),dp(12),dp(32),dp(12));timer=bold("",64,ACCENT);timer.setGravity(Gravity.CENTER);timer.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);timer.setAutoSizeTextTypeUniformWithConfiguration(28,64,2,android.util.TypedValue.COMPLEX_UNIT_SP);display.addView(timer,new LinearLayout.LayoutParams(-1,dp(90)));countsText=text("",14,MUTED);countsText.setGravity(Gravity.CENTER);display.addView(countsText);dial.addView(display,new FrameLayout.LayoutParams(-1,-1));content.addView(dial,new LinearLayout.LayoutParams(-1,dp(280)));gap(content,22);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);
        progress.setProgressDrawable(roundedTrack(3));progress.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));content.addView(progress,new LinearLayout.LayoutParams(-1,dp(6)));gap(content,16);nextText=text("",14,MUTED);content.addView(nextText);gap(content,24);
        LinearLayout controls=row();pauseButton=button(e.paused?"继续":"暂停",e.current().manual?CARD:ACCENT,()->command("PAUSE"));weighted(controls,pauseButton);weighted(controls,button("跳过当前阶段",CARD,()->confirm("跳过当前阶段？","跳过的训练组不会计入完成次数。","跳过",()->command("SKIP"))));content.addView(controls);gap(content,12);
        content.addView(button("结束本次训练",CARD,()->confirm("结束训练？","已完成的组数会保存到训练记录。","继续训练","结束并保存",()->{command("STOP");handler.postDelayed(()->historyAfterEnd(),300);})));gap(content,18);
        content.addView(text("切换应用或锁屏后仍会计时。若被系统强制关闭，下次打开会恢复到暂停状态。",12,MUTED));updateTimer();
        if(e.current().manual){final int expectedStage=e.index;final String session=e.sessionId;LinearLayout dock=column();dock.setPadding(0,dp(12),0,dp(2));doneButton=button("完成这一组 →",ACCENT,()->{doneButton.setEnabled(false);startForegroundService(new Intent(this,TimerService.class).setAction("COMPLETE").putExtra("stage_index",expectedStage).putExtra("session_id",session));});dock.addView(doneButton);pageRoot.addView(dock,new LinearLayout.LayoutParams(-1,-2));updateTimer();}
    }
    private void updateTimer(){
        Workout.Engine e=TimerService.engine;if(e==null||e.finished||timer==null)return;Workout.Stage s=e.current();
        int stageColor=s.exercise()||s.preparation()?ACCENT:ORANGE;
        stageMeta.setText((e.paused?"已暂停 · ":"")+(s.preparation()?"准备 · 第一项任务即将开始":(s.manual?"次数训练":s.kind)+" / "+(e.index+1)+" OF "+e.stages.size()));stageMeta.setTextColor(stageColor);stageTitle.setText(s.name);timer.setText(s.manual?s.repetitions+" 次":clock(e.remaining(TimerService.now())));timer.setTextColor(stageColor);countsText.setText(s.preparation()?"调整姿势 · 准备好再开始":s.exercise()?(s.manual?"手动完成 · ":"")+"第 "+s.set+" / "+s.sets+" 组 · 已完成 "+e.completed+" 组":"放松呼吸 · 已完成 "+e.completed+" 组");pauseButton.setText(e.paused?"继续":"暂停");
        if(doneButton!=null){doneButton.setEnabled(!e.paused);doneButton.setAlpha(e.paused?.45f:1);doneButton.setText(e.paused?"继续后完成这一组":"完成这一组 →");}
        progress.setProgress((int)(1000*e.progressFraction(TimerService.now())));
        ring.update(s.manual?1:e.remaining(TimerService.now()),s.manual?1:s.seconds*1000L,stageColor);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(stageColor));
        nextText.setText(e.index+1<e.stages.size()?"接下来  /  "+e.stages.get(e.index+1).name+" · "+stageDetail(e.stages.get(e.index+1)):"接下来  /  训练完成");
    }
    private void showHistory(){
        base("history");content.addView(text("HISTORY / 训练记录",12,ACCENT));content.addView(bold("每一组，都留下痕迹。",26,TEXT));content.addView(text("按完成的训练组记录；次数动作手动完成后记一组。",12,MUTED));gap(content,20);
        JSONArray records=Store.history(this);int total=0;long duration=0;for(int n=0;n<records.length();n++){JSONObject o=records.optJSONObject(n);if(o!=null){total+=o.optInt("count");duration+=o.optLong("seconds");}}
        LinearLayout stats=row();weighted(stats,metric("累计训练",records.length()+" 次",TEXT));weighted(stats,metric("完成组数",total+" 组",ACCENT));weighted(stats,metric("有效时长",time((int)duration),TEXT));content.addView(stats);gap(content,12);content.addView(text("最近 200 条记录 · 每次坚持都值得记录",13,MUTED));gap(content,20);
        if(records.length()>0){LinearLayout actions=row();weighted(actions,button("导出记录",CARD,this::chooseHistoryExport));weighted(actions,button("清空记录",CARD,()->{
            Set<String> ids=new HashSet<>();for(int n=0;n<records.length();n++){JSONObject entry=records.optJSONObject(n);if(entry!=null)ids.add(entry.optString("id"));}
            confirm("清空这 "+ids.size()+" 条记录？","将删除当前显示的训练记录，此操作无法撤销。你可以先导出备份。","清空",()->{Store.deleteHistory(this,ids);showHistory();toast("记录已清空");});
        }));content.addView(actions);gap(content,16);}
        if(records.length()==0)content.addView(text("开始第一场训练，记录就会出现在这里。",16,TEXT));
        SimpleDateFormat date=new SimpleDateFormat("MM月dd日 HH:mm",Locale.CHINA);
        for(int n=0;n<records.length();n++){
            JSONObject o=records.optJSONObject(n);if(o==null)continue;LinearLayout card=column();card.setPadding(dp(16),dp(14),dp(16),dp(14));card.setBackground(background(CARD,18));String when=date.format(new Date(o.optLong("date")));card.addView(bold(when+" · "+(o.optBoolean("complete")?"已完成":"提前结束"),17,TEXT));card.addView(text(o.optInt("count")+" 组 · 有效训练 "+time(o.optInt("seconds"))+" · 跳过 "+o.optInt("skipped")+" 组",14,ACCENT));JSONObject counts=o.optJSONObject("counts");if(counts!=null){Iterator<String> keys=counts.keys();while(keys.hasNext()){String k=keys.next();card.addView(text(k+"  × "+counts.optInt(k)+" 组",14,MUTED));}}gap(card,10);
            final String id=o.optString("id");TextView remove=button("删除记录",BG,()->confirm("删除这条训练记录？",when+" 的训练记录将被删除，此操作无法撤销。","删除",()->{Store.deleteHistory(this,Collections.singleton(id));showHistory();toast("记录已删除");}));remove.setContentDescription("删除训练记录 "+id);card.addView(remove);content.addView(card);gap(content,12);
        }gap(content,16);
    }
    private void chooseHistoryExport(){
        Panel panel=new Panel("EXPORT / 导出记录","选择导出格式","接下来选择保存位置，训练记录会保留在应用中");
        panel.body.addView(button("CSV · 表格文件",CARD,()->{panel.dialog.dismiss();exportHistory(false);}));gap(panel.body,12);panel.body.addView(text("适合用 Excel 或其他表格应用查看。",13,MUTED));gap(panel.body,16);
        panel.body.addView(button("JSON · 完整记录",CARD,()->{panel.dialog.dismiss();exportHistory(true);}));gap(panel.body,12);panel.body.addView(text("保留完整记录字段和每个动作完成的组数。",13,MUTED));panel.footer.addView(button("取消",CARD,()->panel.dialog.dismiss()));panel.show(460);
    }
    private void exportHistory(boolean json){
        JSONArray records=Store.history(this);if(records.length()==0){toast("暂无可导出的记录");return;}
        File file=null;
        try{
            String value=json?HistoryExport.json(records,System.currentTimeMillis(),TimeZone.getDefault()):HistoryExport.csv(records,TimeZone.getDefault());
            File directory=new File(getCacheDir(),"history-exports");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create export cache");
            file=File.createTempFile("history-",json?".json":".csv",directory);try(OutputStream out=new FileOutputStream(file)){out.write(value.getBytes(StandardCharsets.UTF_8));}
            pendingExport=file.getName();String stamp=new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date());
            Intent picker=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(json?"application/json":"text/csv").putExtra(Intent.EXTRA_TITLE,"力量计时-训练记录-"+stamp+(json?".json":".csv"));startActivityForResult(picker,EXPORT_REQUEST);
        }catch(IOException|JSONException|ActivityNotFoundException error){if(file!=null)file.delete();pendingExport=null;toast("无法打开导出，请稍后重试");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=EXPORT_REQUEST)return;
        String name=pendingExport;pendingExport=null;if(name==null||!name.startsWith("history-")||name.contains("/")||name.contains("\\")){toast("导出已失效，请重新导出");return;}
        File snapshot=new File(new File(getCacheDir(),"history-exports"),name);
        if(result!=RESULT_OK||data==null||data.getData()==null){snapshot.delete();return;}
        android.net.Uri destination=data.getData();ContentResolver resolver=getContentResolver();
        new Thread(()->{
            boolean saved=false;try(InputStream in=new FileInputStream(snapshot);OutputStream out=resolver.openOutputStream(destination,"wt")){
                if(out==null)throw new IOException("No output stream");byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);out.flush();saved=true;
            }catch(IOException|SecurityException|IllegalArgumentException error){saved=false;}finally{snapshot.delete();}
            final boolean complete=saved;runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())toast(complete?"训练记录已导出":"导出失败，请重新选择保存位置");});
        },"history-export").start();
    }
    private void settings(){
        base("settings");LinearLayout box=content;box.addView(text("PREFERENCES / 偏好设置",12,ACCENT));box.addView(bold("训练设置",26,TEXT));box.addView(text("让每一次阶段切换更舒服",13,MUTED));gap(box,20);
        box.addView(settingToggle("阶段结束提示音","开始与继续使用开始音；休息、暂停与结束使用结束音","sound"));gap(box,10);
        box.addView(settingToggle("阶段结束振动","短暂触感提醒，可独立开关","vibrate"));gap(box,10);
        gap(box,8);box.addView(bold("倒计时朗读",18,TEXT));box.addView(text("训练和休息分别设置，中文真人女声离线读秒。训练朗读开启时，也会朗读开始前的 5 秒准备倒计时。",12,MUTED));gap(box,10);
        box.addView(countdownSetting(true));gap(box,10);box.addView(countdownSetting(false));gap(box,14);
        LinearLayout audio=column();audio.setPadding(dp(16),dp(12),dp(16),dp(12));audio.setBackground(background(CARD,18));
        TextView volume=text("音效与朗读音量 · "+Store.prefs(this).getInt("sound_volume",55)+"%",14,TEXT);audio.addView(volume);
        SeekBar slider=new SeekBar(this,null,0,R.style.SlimSeekBar);slider.setContentDescription("提示音音量");slider.setMax(100);slider.setProgressDrawable(roundedTrack(2));slider.setProgress(Store.prefs(this).getInt("sound_volume",55));slider.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));slider.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));audio.addView(slider,new LinearLayout.LayoutParams(-1,dp(48)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int value,boolean user){volume.setText("音效与朗读音量 · "+value+"%");if(user)Store.prefs(MainActivity.this).edit().putInt("sound_volume",value).apply();}});
        audio.addView(text("音效与读秒共用音量，叠加手机闹钟音量；试听不受音效开关影响。",12,MUTED));gap(audio,8);
        LinearLayout previews=row();weighted(previews,button("试听开始",BG,()->Chime.play(this,Chime.TRAINING)));weighted(previews,button("试听暂停 / 结束",BG,()->Chime.play(this,Chime.COMPLETE)));audio.addView(previews);box.addView(audio);gap(box,14);
        box.addView(button("读秒录音来源与许可  ›",CARD,this::voiceCredits));gap(box,14);
        box.addView(button("后台计时与朗读设置  ›",CARD,this::backgroundSettings));gap(box,14);
        box.addView(text("力量计时 "+BuildConfig.VERSION_NAME+" · 离线使用\n方案与训练记录保存在本机",12,MUTED));gap(box,20);
    }
    private void backgroundSettings(){
        boolean unrestricted=getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        Panel panel=new Panel("BACKGROUND / 后台运行","后台计时与朗读","切换应用或锁屏后，继续计时和读秒");
        panel.body.addView(text((unrestricted?"系统电池优化：已允许后台运行":"系统电池优化：尚未豁免")+"\n\n如切到后台后停止朗读，请允许后台运行，并允许通知。vivo / iQOO 手机还可能需要在系统的应用耗电管理中，为力量计时开启「允许后台高耗电」。\n\n朗读需开启训练或休息倒计时朗读，并调高应用音量和手机闹钟音量。",14,TEXT));
        panel.footer.addView(button(unrestricted?"查看应用系统设置":"允许后台运行",ACCENT,()->{
            Intent details=new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName()));
            Intent request=new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,android.net.Uri.parse("package:"+getPackageName()));
            try{startActivity(unrestricted?details:request);panel.dialog.dismiss();}catch(ActivityNotFoundException error){try{startActivity(details);panel.dialog.dismiss();}catch(ActivityNotFoundException missing){toast("请在系统设置中允许力量计时后台运行");}}
        }));panel.show(500);
    }
    private void voiceCredits(){
        Panel panel=new Panel("VOICE / 读秒录音","真人女声录音","中文数字录音来自 Shtooka 开放音频库");
        TextView detail=text("录音：Yue Tan\nCopyright © 2009 Yue Tan\n\n授权：CC BY-SA 3.0 US\nhttps://creativecommons.org/licenses/by-sa/3.0/us/\n\n来源：Shtooka / University of Caen，cmn-caen-tan\nhttps://fsi-languages.yojik.eu/audiocollections/detailled/cmn-caen-tan/readme.txt\n\n1–10 使用原数字录音；11–60 由同一录音者的数字片段组合。音频经裁剪空白、音量调整、重采样，长数字保持音高调整速度以适应逐秒播放；处理后的音频沿用相同授权。",13,TEXT);
        detail.setPadding(dp(16),dp(16),dp(16),dp(16));detail.setBackground(background(CARD,18));detail.setLinkTextColor(ACCENT);android.text.util.Linkify.addLinks(detail,android.text.util.Linkify.WEB_URLS);detail.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());panel.body.addView(detail);
        panel.footer.addView(button("完成",ACCENT,()->panel.dialog.dismiss()));panel.show(650);
    }
    private View countdownSetting(boolean training){
        String key=training?"voice_training":"voice_rest",title=training?"训练倒计时朗读":"休息倒计时朗读";
        LinearLayout card=column();card.setPadding(dp(16),dp(12),dp(12),dp(12));card.setBackground(background(CARD,18));LinearLayout controls=row();
        LinearLayout labels=column();labels.addView(bold(title,15,TEXT));labels.addView(text(training?"每组训练结束前读秒":"大小休息及独立休息结束前读秒",12,MUTED));controls.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        Switch toggle=preferenceToggle(title,key,false);controls.addView(toggle);controls.setOnClickListener(v->toggle.setChecked(!toggle.isChecked()));card.addView(controls);gap(card,10);
        TextView trigger=button("剩余 "+Store.prefs(this).getInt(key+"_seconds",10)+" 秒时开始  ›",BG,()->{});trigger.setContentDescription(training?"训练起读秒数":"休息起读秒数");trigger.setOnClickListener(v->configureCountdown(key,training,trigger));card.addView(trigger,new LinearLayout.LayoutParams(-1,-2));return card;
    }
    private void configureCountdown(String key,boolean training,TextView trigger){
        Panel panel=new Panel("VOICE / 倒计时朗读",training?"训练起读秒数":"休息起读秒数","进入设定的剩余秒数后，逐秒朗读直到 1");
        EditText seconds=input(panel.body,"起读秒数（1–60）",String.valueOf(Store.prefs(this).getInt(key+"_seconds",10)),true);
        LinearLayout presets=row();for(int value:new int[]{5,10,15,30}){TextView preset=button(value+" 秒",CARD,()->seconds.setText(String.valueOf(value)));preset.setPadding(dp(4),dp(10),dp(4),dp(10));weighted(presets,preset);}panel.body.addView(presets);gap(panel.body,12);
        panel.body.addView(text("例如设置 10 秒，就会朗读「10、9……2、1」。阶段短于起读秒数时，从当前剩余秒数开始；暂停时停止朗读。",13,MUTED));
        LinearLayout controls=row();weighted(controls,button("取消",CARD,()->panel.dialog.dismiss()));weighted(controls,button("保存",ACCENT,()->{try{int value=number(seconds,1,60);Store.prefs(this).edit().putInt(key+"_seconds",value).apply();trigger.setText("剩余 "+value+" 秒时开始  ›");panel.dialog.dismiss();}catch(IllegalArgumentException ignored){/* The input card shows the range error. */}}));panel.footer.addView(controls);panel.show(490);
    }
    private View settingToggle(String title,String subtitle,String key){
        LinearLayout card=row();card.setPadding(dp(16),dp(12),dp(12),dp(12));card.setBackground(background(CARD,18));LinearLayout labels=column();labels.addView(bold(title,15,TEXT));labels.addView(text(subtitle,12,MUTED));card.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        Switch toggle=preferenceToggle(title,key,true);card.addView(toggle);card.setOnClickListener(v->toggle.setChecked(!toggle.isChecked()));return card;
    }
    private Switch preferenceToggle(String title,String key,boolean defaultOn){
        Switch toggle=styledToggle(title,Store.prefs(this).getBoolean(key,defaultOn));toggle.setOnCheckedChangeListener((b,on)->Store.prefs(this).edit().putBoolean(key,on).apply());return toggle;
    }
    private Switch styledToggle(String title,boolean on){
        Switch toggle=new Switch(this);toggle.setContentDescription(title);toggle.setMinHeight(dp(48));toggle.setMinWidth(dp(48));toggle.setChecked(on);
        int[][] states={new int[]{android.R.attr.state_checked},new int[]{}};
        toggle.setThumbTintList(new android.content.res.ColorStateList(states,new int[]{ACCENT,MUTED}));toggle.setTrackTintList(new android.content.res.ColorStateList(states,new int[]{0xff687f42,0xff455249}));
        return toggle;
    }
}
