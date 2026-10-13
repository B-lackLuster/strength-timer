package com.strengthtimer.app;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
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
    void shot(String name)throws Exception {Bitmap b=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();File dir=new File(context.getExternalFilesDir(null),"screenshots");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    @Test public void editPlanAndInsertRest()throws Exception{
        try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)){
            shot("01-plan");onView(withText("平板支撑")).perform(click());onView(withText("1分")).perform(click());onView(withText("保存")).perform(click());assertEquals(60,Store.load(context).get(0).seconds);
            onView(withText("＋ 休息")).perform(scrollTo(),click());onView(withText("大休息")).perform(click());onView(withText("保存")).perform(click());assertEquals(4,Store.load(context).size());assertTrue(Store.load(context).get(3).rest);assertTrue(Store.load(context).get(3).big);
            onView(withText("＋ 动作")).perform(scrollTo(),click());onView(withText("保存")).perform(click());assertEquals(5,Store.load(context).size());
        }
    }
    @Test public void runPauseSkipAndSaveHistory()throws Exception{
        Store.save(context,Arrays.asList(new Workout.Item("测试平板",30,2,20,10)));
        try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)){
            onView(withText("开始训练 →")).perform(scrollTo(),click());SystemClock.sleep(700);shot("02-training");
            onView(withText("暂停")).perform(scrollTo(),click());SystemClock.sleep(500);assertTrue(TimerService.engine.paused);
            onView(withText("跳过当前阶段")).perform(click());onView(withText("跳过")).perform(click());SystemClock.sleep(500);assertEquals(0,TimerService.engine.completed);assertEquals(1,TimerService.engine.skipped);assertEquals("小休息",TimerService.engine.current().kind);shot("03-rest-paused");
            onView(withText("结束本次训练")).perform(scrollTo(),click());onView(withText("结束并保存")).perform(click());SystemClock.sleep(700);assertEquals(1,Store.history(context).length());assertEquals(0,Store.history(context).getJSONObject(0).getInt("count"));shot("04-history");
        }
    }
    @Test public void automaticallyTransitionsAndCompletes()throws Exception{
        Store.save(context,Arrays.asList(new Workout.Item("快速验证",1,2,1,1)));
        try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)){
            onView(withText("开始训练 →")).perform(scrollTo(),click());SystemClock.sleep(5000);onView(withText("训练完成 ✓")).check(matches(isDisplayed()));assertEquals(2,TimerService.engine.completed);assertEquals(1,Store.history(context).length());assertEquals(2,Store.history(context).getJSONObject(0).getInt("count"));shot("05-complete");
        }
    }
    @After public void cleanup(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->context.stopService(new Intent(context,TimerService.class)));}
}
