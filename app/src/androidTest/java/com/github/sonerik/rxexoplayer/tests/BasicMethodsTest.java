package com.github.sonerik.rxexoplayer.tests;

import android.app.Activity;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.ActivityInstrumentationTestCase2;

import com.github.sonerik.rxexoplayer.BasicRxExoPlayer;
import com.github.sonerik.rxexoplayer.RxExoPlayer;
import com.github.sonerik.rxexoplayer.TestActivity;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import rx.observers.TestSubscriber;

@RunWith(AndroidJUnit4.class)
public class BasicMethodsTest extends ActivityInstrumentationTestCase2<TestActivity> {

    private Activity activity;

    @Rule
    public ActivityTestRule activityRule = new ActivityTestRule<>(TestActivity.class);

    @Before
    public void setUp() throws Exception {
        super.setUp();

        // Injecting the Instrumentation instance is required
        // for your test to run with AndroidJUnitRunner.
        injectInstrumentation(InstrumentationRegistry.getInstrumentation());
        activity = activityRule.getActivity();
    }

    public BasicMethodsTest() {
        super(TestActivity.class);
    }

    private RxExoPlayer getTestPlayer() {
        return new BasicRxExoPlayer(activity);
    }

    private Uri getTestUri() {
        return Uri.parse("file:///android_asset/test.mp3");
    }

    private void onUiThread(Runnable r) throws Throwable {
        activityRule.runOnUiThread(r);
    }

    @Test
    public void testPreparation() throws Throwable {
        final TestSubscriber<RxExoPlayer.PlayerEvent> testSubscriber = new TestSubscriber<>();

        onUiThread(() -> {
            RxExoPlayer player = getTestPlayer();
            player.prepare(getTestUri())
                  .subscribe(testSubscriber);
        });

        testSubscriber.awaitTerminalEvent(25, TimeUnit.SECONDS);

        testSubscriber.assertNoErrors();
        testSubscriber.assertCompleted();
        testSubscriber.assertValue(RxExoPlayer.PlayerEvent.READY);
    }

    @Test
    public void testStartAfterPrepare() throws Throwable {
        final TestSubscriber<RxExoPlayer.PlayerEvent> testSubscriber = new TestSubscriber<>();

        onUiThread(() -> {
            RxExoPlayer player = getTestPlayer();
            player.prepare(getTestUri())
                  .concatMap(e -> player.start())
                  .subscribe(testSubscriber);
        });

        testSubscriber.awaitTerminalEvent(25, TimeUnit.SECONDS);

        testSubscriber.assertNoErrors();
        testSubscriber.assertCompleted();
        testSubscriber.assertValue(RxExoPlayer.PlayerEvent.STARTED);
    }

    @Test
    public void testSeekTo() throws Throwable {
        final TestSubscriber<RxExoPlayer.PlayerEvent> testSubscriber = new TestSubscriber<>();

        onUiThread(() -> {
            RxExoPlayer player = getTestPlayer();
            player.prepare(getTestUri())
                  .concatMap(e -> player.start())
                  .concatMap(e -> player.seekTo(30000))
                  .subscribe(testSubscriber);
        });

        testSubscriber.awaitTerminalEvent(25, TimeUnit.SECONDS);

        testSubscriber.assertNoErrors();
        testSubscriber.assertCompleted();
        testSubscriber.assertValue(RxExoPlayer.PlayerEvent.READY);
    }
}