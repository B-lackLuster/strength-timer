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
    void shot(String name)throws Exception {InstrumentationRegistry.getInstrumentation().waitForIdleSync();SystemClock.sleep(600);Bitmap b=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();File dir=new File(context.getExternalFilesDir(null),"screenshots");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    @Test public void editPlanAndInsertRest()throws Exception{
        try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)){
            onView(withText("平板支撑")).check(matches(isDisplayed()));shot("01-plan");onView(withText("平板支撑")).perform(click());onView(withText("1分")).perform(click());onView(withText("保存")).perform(click());assertEquals(60,Store.load(context).get(0).seconds);
            onView(withText("＋ 休息")).perform(scrollTo(),click());onView(withText("大休息")).perform(click());onView(withText("保存")).perform(click());assertEquals(4,Store.load(context).size());assertTrue(Store.load(context).get(3).rest);assertTrue(Store.load(context).get(3).big);
            onView(withText("＋ 动作")).perform(scrollTo(),click());onView(withText("保存")).perform(click());assertEquals(5,Store.load(context).size());
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
            onView(isAssignableFrom(android.widget.EditText.class)).perform(typeText("My workout"),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            onView(withText("保存")).perform(click());assertTrue(Store.plans(context).has("My workout"));
            onView(withText("切换方案")).perform(click());onView(withText("模板 · 下肢训练")).perform(click());
            onView(withText("使用方案")).perform(click());assertEquals("深蹲",Store.load(context).get(0).name);
            onView(withText("切换方案")).perform(click());onView(withText("已保存 · My workout")).perform(click());
            onView(withText("使用方案")).perform(click());assertEquals("平板支撑",Store.load(context).get(0).name);
            onView(withText("开始训练 →")).perform(click());SystemClock.sleep(500);
            onView(withText("训练计划")).perform(click());onView(withText("切换方案")).perform(click());
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
            onView(withContentDescription("提示音音量")).perform(swipeRight());assertTrue(Store.prefs(context).getInt("sound_volume",55)>55);
            onView(withText("试听训练")).perform(scrollTo(),click());onView(withText("试听休息")).perform(click());onView(withText("试听完成")).perform(click());
            onView(withText("完成")).perform(click());a.recreate();onView(withText("设置")).perform(click());
            onView(withContentDescription("阶段结束提示音")).check(matches(isNotChecked()));onView(withContentDescription("阶段结束振动")).check(matches(isNotChecked()));
            onView(withText("完成")).perform(click());onView(withText("开始训练 →")).perform(click());SystemClock.sleep(500);assertNotNull(TimerService.engine);
        }
    }
    @After public void cleanup(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->context.stopService(new Intent(context,TimerService.class)));}
}
