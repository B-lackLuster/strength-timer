package com.strengthtimer.app;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import android.view.*;
import android.widget.TextView;
import org.hamcrest.Matcher;
import androidx.test.platform.app.InstrumentationRegistry;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AppTest {
    Context context;
    @Before public void reset(){context=InstrumentationRegistry.getInstrumentation().getTargetContext();InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{context.stopService(new Intent(context,TimerService.class));TimerService.engine=null;Store.prefs(context).edit().clear().commit();});SystemClock.sleep(200);}
    void shot(String name)throws Exception {InstrumentationRegistry.getInstrumentation().waitForIdleSync();SystemClock.sleep(600);capture(name);}
    void capture(String name)throws Exception {Bitmap b=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();File dir=new File(context.getExternalFilesDir(null),"screenshots");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    View pageView(MainActivity activity){try{java.lang.reflect.Field field=MainActivity.class.getDeclaredField("pageRoot");field.setAccessible(true);return (View)field.get(activity);}catch(ReflectiveOperationException error){throw new AssertionError(error);}}
    @Test public void editPlanAndInsertRest()throws Exception{
        try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)){
            onView(withText("平板支撑")).check(matches(isDisplayed()));shot("01-plan");onView(withText("平板支撑")).perform(click());shot("08-edit-exercise");onView(withText("1分")).perform(scrollTo(),click());onView(withText("保存")).perform(click());assertEquals(60,Store.load(context).get(0).seconds);
            onView(withText("＋ 休息")).perform(scrollTo(),click());onView(withText("大休息")).perform(click());shot("09-add-rest");onView(withText("保存")).perform(click());assertEquals(4,Store.load(context).size());assertTrue(Store.load(context).get(3).rest);assertTrue(Store.load(context).get(3).big);
            onView(withText("＋ 动作")).perform(scrollTo(),click());shot("10-add-exercise");onView(withText("保存")).perform(click());assertEquals(5,Store.load(context).size());
        }
    }
    @Test public void runPauseSkipAndSaveHistory()throws Exception{
        Store.save(context,Arrays.asList(new Workout.Item("测试平板",30,2,20,10)));
        try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)){
            onView(withText("开始训练 →")).perform(click());SystemClock.sleep(700);shot("02-training");
            onView(withText("暂停")).perform(scrollTo(),click());SystemClock.sleep(500);assertTrue(TimerService.engine.paused);
            onView(withText("跳过当前阶段")).perform(scrollTo(),click());onView(withText("跳过")).inRoot(isDialog()).perform(click());SystemClock.sleep(500);assertEquals(0,TimerService.engine.completed);assertEquals(1,TimerService.engine.skipped);assertEquals("小休息",TimerService.engine.current().kind);shot("03-rest-paused");
            onView(withText("结束本次训练")).perform(scrollTo(),click());onView(withText("结束并保存")).inRoot(isDialog()).perform(click());SystemClock.sleep(700);assertEquals(1,Store.history(context).length());assertEquals(0,Store.history(context).getJSONObject(0).getInt("count"));shot("04-history");
        }
    }
    @Test public void automaticallyTransitionsAndCompletes()throws Exception{
        Store.save(context,Arrays.asList(new Workout.Item("快速验证",1,2,1,1)));
        try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)){
            onView(withText("开始训练 →")).perform(click());a.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);SystemClock.sleep(5000);a.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);SystemClock.sleep(300);onView(withText("训练完成 ✓")).check(matches(isDisplayed()));assertEquals(2,TimerService.engine.completed);assertEquals(1,Store.history(context).length());assertEquals(2,Store.history(context).getJSONObject(0).getInt("count"));shot("05-complete");context.startForegroundService(new Intent(context,TimerService.class).setAction("SKIP"));SystemClock.sleep(400);assertEquals(1,Store.history(context).length());
        }
    }
    private View findText(View view,String value){
        if(view instanceof TextView && value.contentEquals(((TextView)view).getText()))return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++){View found=findText(group.getChildAt(n),value);if(found!=null)return found;}}
        return null;
    }
    @Test public void saveAndSwitchWorkoutPlans()throws Exception{
        try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)){
            onView(withText("保存方案")).perform(click());
            onView(withContentDescription("方案名称（最多 24 字）")).perform(scrollTo(),click());SystemClock.sleep(500);
            onView(withContentDescription("方案名称（最多 24 字）")).perform(typeText("My workout"),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());shot("19-save-plan-name");
            onView(withContentDescription("方案名称（最多 24 字）")).check(matches(withText("My workout")));
            onView(withText("保存")).perform(click());assertTrue("Saved plans: "+Store.plans(context),Store.plans(context).has("My workout"));
            onView(withText("切换方案")).perform(click());onView(withText("模板 · 下肢训练")).perform(click());
            onView(withText("使用方案")).perform(click());assertEquals("深蹲",Store.load(context).get(0).name);
            onView(withText("切换方案")).perform(click());onView(withText("已保存 · My workout")).perform(click());
            onView(withText("使用方案")).perform(click());assertEquals("平板支撑",Store.load(context).get(0).name);
            onView(withText("开始训练 →")).perform(click());SystemClock.sleep(500);
            onView(withContentDescription("返回训练计划")).perform(click());onView(withText("切换方案")).perform(click());
            assertNotNull(TimerService.engine);assertEquals("平板支撑",TimerService.engine.current().name);
        }
    }
    @Test public void longPressDragReordersPlan(){
        try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)){
            onView(withContentDescription("长按拖动 平板支撑")).perform(new ViewAction(){
                public Matcher<View> getConstraints(){return isDisplayed();}
                public String getDescription(){return "Long press exercise handle and drag onto next exercise";}
                public void perform(UiController controller,View view){
                    View target=findText(view.getRootView(),"深蹲");assertNotNull(target);
                    int[] from=new int[2],to=new int[2];view.getLocationOnScreen(from);target.getLocationOnScreen(to);
                    float x=from[0]+view.getWidth()/2f,y=from[1]+view.getHeight()/2f,endY=to[1]+target.getHeight()/2f;
                    long down=SystemClock.uptimeMillis();
                    send(controller,down,MotionEvent.ACTION_DOWN,x,y);controller.loopMainThreadForAtLeast(750);
                    for(int n=1;n<=12;n++){send(controller,down,MotionEvent.ACTION_MOVE,x,y+(endY-y)*n/12f);controller.loopMainThreadForAtLeast(40);}
                    send(controller,down,MotionEvent.ACTION_UP,x,endY);controller.loopMainThreadForAtLeast(400);
                }
                private void send(UiController c,long down,int action,float x,float y){MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);try{c.injectMotionEvent(e);}catch(Exception error){throw new RuntimeException(error);}finally{e.recycle();}}
            });
            assertEquals("深蹲",Store.load(context).get(0).name);assertEquals("平板支撑",Store.load(context).get(1).name);
        }
    }
    @Test public void fixedStartAndSettingsPersist()throws Exception{
        List<Workout.Item> longPlan=new ArrayList<>();for(int n=0;n<12;n++)longPlan.add(new Workout.Item("动作 "+n,30,2,10,20));Store.save(context,longPlan);
        try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)){
            int[] before=new int[2],after=new int[2];
            onView(withText("开始训练 →")).check(matches(isDisplayed()));
            a.onActivity(activity->{View start=findText(activity.getWindow().getDecorView(),"开始训练 →");start.getLocationOnScreen(before);assertFalse(start.getParent() instanceof android.widget.ScrollView);});
            onView(withText("动作 11")).perform(scrollTo());
            a.onActivity(activity->findText(activity.getWindow().getDecorView(),"开始训练 →").getLocationOnScreen(after));assertArrayEquals(before,after);shot("07-fixed-start");
            onView(withText("设置")).perform(click());shot("06-settings");
            onView(withContentDescription("阶段结束提示音")).perform(click());assertFalse(Store.prefs(context).getBoolean("sound",true));
            onView(withContentDescription("阶段结束振动")).perform(click());assertFalse(Store.prefs(context).getBoolean("vibrate",true));
            onView(withContentDescription("提示音音量")).perform(scrollTo(),swipeRight());assertTrue(Store.prefs(context).getInt("sound_volume",55)>55);
            onView(withText("试听开始")).perform(scrollTo(),click());onView(withText("试听暂停 / 结束")).perform(click());
            onView(withContentDescription("返回训练计划")).perform(click());a.recreate();onView(withText("设置")).perform(click());
            onView(withContentDescription("阶段结束提示音")).check(matches(isNotChecked()));onView(withContentDescription("阶段结束振动")).check(matches(isNotChecked()));
            onView(withContentDescription("返回训练计划")).perform(click());onView(withText("开始训练 →")).perform(click());SystemClock.sleep(500);assertNotNull(TimerService.engine);
        }
    }
    @Test public void headerHistoryAndBackPreserveActiveTimer()throws Exception{
        try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)){
            onView(withText("训练计划")).check(doesNotExist());onView(withText("计时")).check(doesNotExist());
            onView(withText("训练记录")).check(matches(isDisplayed())).perform(click());
            onView(withContentDescription("返回训练计划")).perform(click());
            onView(withText("开始训练 →")).perform(click());SystemClock.sleep(500);Workout.Engine running=TimerService.engine;
            onView(withText("训练记录")).perform(click());onView(withContentDescription("返回正在进行的训练")).perform(click());assertSame(running,TimerService.engine);
            onView(withContentDescription("返回训练计划")).perform(click());onView(withText("返回正在进行的训练")).perform(click());assertSame(running,TimerService.engine);
            onView(withText("训练记录")).perform(click());androidx.test.espresso.Espresso.pressBack();onView(withText("暂停")).check(matches(isDisplayed()));assertSame(running,TimerService.engine);
        }
    }
    @Test public void exerciseSheetPresetsValidationAndCancel()throws Exception{
        try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)){
            onView(withText("平板支撑")).perform(click());
            onView(withText("选择常用动作  ›")).perform(scrollTo(),click());onView(withText("哑铃划船")).perform(scrollTo(),click());
            onView(withContentDescription("单次时长 / 秒（1–3600）")).perform(scrollTo(),replaceText("0"),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            onView(withText("保存")).perform(click());assertEquals("平板支撑",Store.load(context).get(0).name);onView(withText("设置训练动作")).check(matches(isDisplayed()));
            onView(withContentDescription("单次时长 / 秒（1–3600）")).perform(scrollTo(),replaceText("60"),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            onView(withText("保存")).perform(click());assertEquals("哑铃划船",Store.load(context).get(0).name);assertEquals(60,Store.load(context).get(0).seconds);
            onView(withText("哑铃划船")).perform(click());onView(withContentDescription("动作名称（可自由输入）")).perform(replaceText("Discard"),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());onView(withText("取消")).perform(click());assertEquals("哑铃划船",Store.load(context).get(0).name);
        }
    }
    @Test public void swipeClosesPlanPanelsWithoutSaving(){
        try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)){
            for(String entry:new String[]{"切换方案","保存方案","＋ 动作","＋ 休息"}){
                if(entry.startsWith("＋"))onView(withText(entry)).perform(scrollTo(),click());else onView(withText(entry)).perform(click());
                onView(withContentDescription("下拉关闭")).perform(new ViewAction(){
                    public Matcher<View> getConstraints(){return isDisplayed();}
                    public String getDescription(){return "Pull sheet handle down to dismiss";}
                    public void perform(UiController controller,View view){
                        int[] origin=new int[2];view.getLocationOnScreen(origin);float x=origin[0]+view.getWidth()/2f,y=origin[1]+view.getHeight()/2f,delta=180*view.getResources().getDisplayMetrics().density;long down=SystemClock.uptimeMillis();
                        for(int n=0;n<=11;n++){int action=n==0?MotionEvent.ACTION_DOWN:n==11?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y+delta*Math.min(n,10)/10,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);try{controller.injectMotionEvent(e);}catch(Exception error){throw new RuntimeException(error);}finally{e.recycle();}controller.loopMainThreadForAtLeast(20);}
                        controller.loopMainThreadForAtLeast(400);
                    }
                });onView(withContentDescription("下拉关闭")).check(doesNotExist());
            }
            assertEquals(3,Store.load(context).size());assertEquals(0,Store.plans(context).length());
        }
    }
    @Test public void trainingAndRestVoiceSettingsPersist(){
        try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)){
            onView(withText("设置")).perform(click());onView(withContentDescription("训练倒计时朗读")).perform(scrollTo(),click());
            onView(withContentDescription("训练起读秒数")).perform(scrollTo(),click());
            onView(withContentDescription("起读秒数（1–60）")).perform(replaceText("0"),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());onView(withText("保存")).perform(click());assertEquals(10,Store.prefs(context).getInt("voice_training_seconds",10));
            onView(withContentDescription("起读秒数（1–60）")).perform(replaceText("5"),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());onView(withText("保存")).perform(click());
            onView(withContentDescription("休息倒计时朗读")).perform(scrollTo(),click());onView(withContentDescription("休息起读秒数")).perform(scrollTo(),click());onView(withText("15 秒")).perform(click());onView(withText("保存")).perform(click());
            onView(withContentDescription("返回训练计划")).perform(click());a.recreate();onView(withText("设置")).perform(click());
            onView(withContentDescription("训练倒计时朗读")).perform(scrollTo()).check(matches(isChecked()));onView(withContentDescription("休息倒计时朗读")).perform(scrollTo()).check(matches(isChecked()));
            assertEquals(5,Store.prefs(context).getInt("voice_training_seconds",10));assertEquals(15,Store.prefs(context).getInt("voice_rest_seconds",10));
            onView(withContentDescription("返回训练计划")).perform(click());
        }
    }
    @Test public void manualRepetitionSetsCompleteAndRestAutomatically()throws Exception{
        Store.save(context,Arrays.asList(new Workout.Item("哑铃划船",45,2,1,1)));
        try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)){
            onView(withText("哑铃划船")).perform(click());onView(withContentDescription("非计时训练")).perform(scrollTo(),click());
            onView(withContentDescription("每组次数（1–999）")).perform(scrollTo(),replaceText("12"),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());onView(withText("保存")).perform(click());assertTrue(Store.load(context).get(0).manual);assertEquals(12,Store.load(context).get(0).repetitions);
            onView(withText("开始训练 →")).perform(click());SystemClock.sleep(700);onView(withText("12 次")).check(matches(isDisplayed()));assertEquals(0,TimerService.engine.completed);shot("11-manual-set");
            onView(withText("完成这一组 →")).perform(click());SystemClock.sleep(1500);assertTrue(TimerService.engine.current().manual);assertEquals(2,TimerService.engine.current().set);assertEquals(1,TimerService.engine.completed);
            onView(withText("完成这一组 →")).perform(click());SystemClock.sleep(1600);onView(withText("训练完成 ✓")).check(matches(isDisplayed()));assertEquals(2,TimerService.engine.completed);assertEquals(2,Store.history(context).getJSONObject(0).getInt("count"));
        }
    }
    @Test public void historyDeletionAndExportChoices()throws Exception{
        org.json.JSONArray records=new org.json.JSONArray();for(int n=0;n<2;n++)records.put(new org.json.JSONObject().put("date",1760400012345L+n).put("complete",true).put("count",2).put("seconds",30).put("skipped",0).put("counts",new org.json.JSONObject().put("哑铃划船",2)));
        Store.prefs(context).edit().putString("history",records.toString()).commit();String id=Store.history(context).getJSONObject(0).getString("id");
        try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)){
            onView(withText("训练记录")).perform(click());shot("12-history-manage");onView(withContentDescription("删除训练记录 "+id)).perform(scrollTo(),click());onView(withText("取消")).inRoot(isDialog()).perform(click());assertEquals(2,Store.history(context).length());
            onView(withText("导出记录")).perform(scrollTo(),click());onView(withText("CSV · 表格文件")).check(matches(isDisplayed()));onView(withText("JSON · 完整记录")).check(matches(isDisplayed()));shot("13-export-options");onView(withText("取消")).inRoot(isDialog()).perform(click());
            onView(withContentDescription("删除训练记录 "+id)).perform(scrollTo(),click());onView(withText("删除")).inRoot(isDialog()).perform(click());assertEquals(1,Store.history(context).length());
            onView(withText("清空记录")).perform(scrollTo(),click());onView(withText("取消")).inRoot(isDialog()).perform(click());assertEquals(1,Store.history(context).length());onView(withText("清空记录")).perform(scrollTo(),click());onView(withText("清空")).inRoot(isDialog()).perform(click());assertEquals(0,Store.history(context).length());onView(withText("开始第一场训练，记录就会出现在这里。")).check(matches(isDisplayed()));
        }
    }
    @Test public void unifiedSubpagesRestoreTheirOriginAndActiveTimer()throws Exception{
        try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)){
            onView(withText("开始训练 →")).perform(click());SystemClock.sleep(500);Workout.Engine running=TimerService.engine;
            a.onActivity(activity->{assertTrue(android.animation.ValueAnimator.areAnimatorsEnabled());findText(activity.getWindow().getDecorView(),"训练记录").performClick();assertTrue(pageView(activity).getTranslationX()>0);});SystemClock.sleep(90);capture("15-history-enter-animation");SystemClock.sleep(350);
            a.onActivity(activity->{activity.onBackPressed();assertTrue(pageView(activity).getTranslationX()<0);});SystemClock.sleep(90);capture("16-history-back-animation");SystemClock.sleep(350);
            onView(withText("训练记录")).perform(click());onView(withText("设置")).perform(click());onView(withText("训练设置")).check(matches(isDisplayed()));onView(withContentDescription("下拉关闭")).check(doesNotExist());shot("14-settings-page");
            a.recreate();onView(withContentDescription("返回训练记录")).perform(click());onView(withText("每一组，都留下痕迹。")).check(matches(isDisplayed()));androidx.test.espresso.Espresso.pressBack();onView(withText("暂停")).check(matches(isDisplayed()));assertSame(running,TimerService.engine);
            a.onActivity(activity->{findText(activity.getWindow().getDecorView(),"设置").performClick();assertTrue(pageView(activity).getTranslationX()>0);});SystemClock.sleep(90);capture("17-settings-enter-animation");SystemClock.sleep(350);
            a.onActivity(activity->{activity.onBackPressed();assertTrue(pageView(activity).getTranslationX()<0);});SystemClock.sleep(90);capture("18-settings-back-animation");SystemClock.sleep(350);onView(withText("暂停")).check(matches(isDisplayed()));assertSame(running,TimerService.engine);
        }
    }
    @Test public void historyExportsPreserveFieldsAndDeletionSnapshot()throws Exception{
        org.json.JSONObject original=new org.json.JSONObject().put("date",1760400012345L).put("complete",false).put("count",2).put("seconds",45).put("skipped",1).put("counts",new org.json.JSONObject().put("哑铃\" A,\n划船",2)).put("extra","保留");
        Store.prefs(context).edit().putString("history",new org.json.JSONArray().put(original).toString()).commit();org.json.JSONArray records=Store.history(context);String id=records.getJSONObject(0).getString("id");String csv=HistoryExport.csv(records,TimeZone.getTimeZone("Asia/Shanghai"));assertTrue(csv.startsWith("\uFEFF\"记录编号\""));assertTrue(csv.contains("+08:00"));assertTrue(csv.contains("提前结束"));assertTrue(csv.contains("哑铃"));org.json.JSONObject json=new org.json.JSONObject(HistoryExport.json(records,123,TimeZone.getTimeZone("Asia/Shanghai")));assertEquals(records.toString(),json.getJSONArray("records").toString());
        Workout.Engine newer=new Workout.Engine(Workout.compile(Arrays.asList(new Workout.Item("新训练",1,1,0,0))),0);newer.tick(1000);Store.finish(context,newer,true);assertEquals(1,Store.deleteHistory(context,Collections.singleton(id)));assertEquals(1,Store.history(context).length());assertNotEquals(id,Store.history(context).getJSONObject(0).getString("id"));assertEquals(0,Store.deleteHistory(context,Collections.singleton(id)));
    }
    @After public void cleanup(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->context.stopService(new Intent(context,TimerService.class)));}
}
