07-14 13:46:24.306 17272 17272 D HomeScreen: LaunchedEffect(celebrationState) enter: isShowing=false, message='太棒了！', level=LOW, showLevelUp=false, showAchievementUnlock=false
07-14 13:46:24.306 17272 17272 D HomeScreen: LaunchedEffect(celebrationState) early return: isShowing=false
07-14 13:46:28.239 24676 24693 I AndroidRuntime: VM exiting with result code 0, cleanup skipped.
07-14 13:46:28.362 24820 24836 I AndroidRuntime: VM exiting with result code 0, cleanup skipped.
07-14 13:47:29.065 24243 24243 I AndroidRuntime: VM exiting with result code 0, cleanup skipped.
07-14 13:47:29.147 24942 24942 I AndroidRuntime: VM exiting with result code 0, cleanup skipped.
07-14 13:47:35.244 17272 17272 D HomeScreen: LaunchedEffect(celebrationState) enter: isShowing=false, message='太棒了！', level=LOW, showLevelUp=true, showAchievementUnlock=false
07-14 13:47:35.244 17272 17272 D HomeScreen: LaunchedEffect(celebrationState) early return: isShowing=false
07-14 13:47:35.358 17272 17272 D HomeViewModel: handleTaskCompleted: set celebrationState isShowing=true, level=LOW, message=太棒了！又完成一个！
07-14 13:47:35.380 17272 17272 D HomeScreen: LaunchedEffect(celebrationState) enter: isShowing=true, message='太棒了！又完成一个！', level=LOW, showLevelUp=true, showAchievementUnlock=false
07-14 13:47:35.380 17272 17272 D HomeScreen: LaunchedEffect(celebrationState) early return: dialog still showing
07-14 13:47:36.359 17272 17272 D HomeViewModel: handleTaskCompleted: delay 1000ms done, resetting isShowing=false
07-14 13:47:36.375 17272 17272 D HomeScreen: LaunchedEffect(celebrationState) enter: isShowing=false, message='太棒了！', level=LOW, showLevelUp=true, showAchievementUnlock=false
07-14 13:47:36.375 17272 17272 D HomeScreen: LaunchedEffect(celebrationState) early return: isShowing=false
07-14 13:47:36.396 17272 17272 D HomeScreen: LaunchedEffect(celebrationState) enter: isShowing=false, message='太棒了！', level=LOW, showLevelUp=false, showAchievementUnlock=false
07-14 13:47:36.396 17272 17272 D HomeScreen: LaunchedEffect(celebrationState) early return: isShowing=false
07-14 13:47:37.804 17272 17272 D HomeViewModel: handleTaskCompleted: set celebrationState isShowing=true, level=LOW, message=太棒了！又完成一个！
07-14 13:47:37.814 17272 17272 D HomeScreen: LaunchedEffect(celebrationState) enter: isShowing=true, message='太棒了！又完成一个！', level=LOW, showLevelUp=false, showAchievementUnlock=false
07-14 13:47:37.814 17272 17272 D HomeScreen: LaunchedEffect(celebrationState) about to showSnackbar: message='😊 太棒了！又完成一个！'
07-14 13:47:37.817 17272 17272 D MainScreen: snackbarHost lambda invoke: message='😊 太棒了！又完成一个！', actionLabel=null, withDismissAction=false
07-14 13:47:37.817 17272 17272 D CorgiSnackbar: render enter: message='😊 太棒了！又完成一个！', actionLabel=null, onAction!=null=true
07-14 13:47:37.857 17272 17272 E AndroidRuntime: FATAL EXCEPTION: main
07-14 13:47:37.857 17272 17272 E AndroidRuntime: Process: com.corgimemo.app, PID: 17272
07-14 13:47:37.857 17272 17272 E AndroidRuntime: java.lang.IllegalArgumentException: Only VectorDrawables and rasterized asset types are supported ex. PNG, JPG, WEBP
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.ui.res.PainterResources_androidKt.loadVectorResource(PainterResources.android.kt:95)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.ui.res.PainterResources_androidKt.painterResource(PainterResources.android.kt:67)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at com.corgimemo.app.ui.components.CorgiSnackbarKt.CorgiSnackbar$lambda$0(CorgiSnackbar.kt:77)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at com.corgimemo.app.ui.components.CorgiSnackbarKt$$ExternalSyntheticLambda0.invoke(D8$$SyntheticClass:0)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.kt:122)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.kt:52)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.material3.SurfaceKt$Surface$1.invoke(Surface.kt:130)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.material3.SurfaceKt$Surface$1.invoke(Surface.kt:110)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.kt:122)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.kt:52)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.runtime.CompositionLocalKt.CompositionLocalProvider(CompositionLocal.kt:408)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.material3.SurfaceKt.Surface-T9BRK9s(Surface.kt:107)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at com.corgimemo.app.ui.components.CorgiSnackbarKt.CorgiSnackbar(CorgiSnackbar.kt:58)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at com.corgimemo.app.ui.screens.main.ComposableSingletons$MainScreenKt.lambda_1709545928$lambda$0(MainScreen.kt:837)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at com.corgimemo.app.ui.screens.main.ComposableSingletons$MainScreenKt$$ExternalSyntheticLambda6.invoke(D8$$SyntheticClass:0)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.kt:131)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.kt:52)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.material3.SnackbarHostKt$FadeInFadeOutWithScale$2$1$1.invoke(SnackbarHost.kt:383)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.material3.SnackbarHostKt$FadeInFadeOutWithScale$2$1$1.invoke(SnackbarHost.kt:383)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.kt:122)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.kt:52)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.material3.SnackbarHostKt$FadeInFadeOutWithScale$1$1.invoke(SnackbarHost.kt:376)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.material3.SnackbarHostKt$FadeInFadeOutWithScale$1$1.invoke(SnackbarHost.kt:338)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.kt:131)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.kt:52)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.material3.SnackbarHostKt.FadeInFadeOutWithScale(SnackbarHost.kt:383)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.material3.SnackbarHostKt.SnackbarHost(SnackbarHost.kt:235)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.material3.SnackbarHostKt.SnackbarHost$lambda$1(Unknown Source:11)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.material3.SnackbarHostKt$$ExternalSyntheticLambda0.invoke(D8$$SyntheticClass:0)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.runtime.RecomposeScopeImpl.compose(RecomposeScopeImpl.kt:204)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.runtime.GapComposer.recomposeToGroupEnd(GapComposer.kt:1678)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.runtime.GapComposer.skipCurrentGroup(GapComposer.kt:2014)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.runtime.GapComposer.doCompose-aFTiNEg(GapComposer.kt:2655)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.runtime.GapComposer.recompose-aFTiNEg$runtime(GapComposer.kt:2577)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.runtime.CompositionImpl.recompose(Composition.kt:1164)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.runtime.Recomposer.performRecompose(Recomposer.kt:1318)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.runtime.Recomposer.access$performRecompose(Recomposer.kt:191)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.runtime.Recomposer$runRecomposeAndApplyChanges$2.invokeSuspend$lambda$2(Recomposer.kt:655)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.runtime.Recomposer$runRecomposeAndApplyChanges$2$$ExternalSyntheticLambda0.invoke(D8$$SyntheticClass:0)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.ui.platform.AndroidUiFrameClock$withFrameNanos$2$callback$1.doFrame(AndroidUiFrameClock.android.kt:39)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.ui.platform.AndroidUiDispatcher.performFrameDispatch(AndroidUiDispatcher.android.kt:108)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.ui.platform.AndroidUiDispatcher.access$performFrameDispatch(AndroidUiDispatcher.android.kt:41)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at androidx.compose.ui.platform.AndroidUiDispatcher$dispatchCallback$1.doFrame(AndroidUiDispatcher.android.kt:69)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at android.view.Choreographer$CallbackRecord.run(Choreographer.java:2081)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at android.view.Choreographer$CallbackRecord.run(Choreographer.java:2092)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at android.view.Choreographer.doCallbacks(Choreographer.java:1567)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at android.view.Choreographer.doFrame(Choreographer.java:1424)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at android.view.Choreographer$FrameDisplayEventReceiver.run(Choreographer.java:2046)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at android.os.Handler.handleCallback(Handler.java:1029)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at android.os.Handler.dispatchMessage(Handler.java:107)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at android.os.Looper.loopOnce(Looper.java:274)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at android.os.Looper.loop(Looper.java:369)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at android.app.ActivityThread.main(ActivityThread.java:10090)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at java.lang.reflect.Method.invoke(Native Method)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:616)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:1137)
07-14 13:47:37.857 17272 17272 E AndroidRuntime:        Suppressed: kotlinx.coroutines.internal.DiagnosticCoroutineContextException: [androidx.compose.runtime.PausableMonotonicFrameClock@7790b00, androidx.compose.ui.platform.MotionDurationScaleImpl@1e3df39, StandaloneCoroutine{Cancelling}@b1f997e, AndroidUiDispatcher@2aceedf]
07-14 13:47:43.547 19620 19620 D HomeScreen: LaunchedEffect(celebrationState) enter: isShowing=false, message='太棒了！', level=LOW, showLevelUp=false, showAchievementUnlock=false
07-14 13:47:43.547 19620 19620 D HomeScreen: LaunchedEffect(celebrationState) early return: isShowing=false
07-14 13:47:49.886 19620 19620 D MainScreen: snackbarHost lambda invoke: message='已删除『2』', actionLabel=撤回, withDismissAction=true
07-14 13:47:49.886 19620 19620 D CorgiSnackbar: render enter: message='已删除『2』', actionLabel=撤回, onAction!=null=true
07-14 13:47:49.921 19620 19620 E AndroidRuntime: FATAL EXCEPTION: main
07-14 13:47:49.921 19620 19620 E AndroidRuntime: Process: com.corgimemo.app, PID: 19620
07-14 13:47:49.921 19620 19620 E AndroidRuntime: java.lang.IllegalArgumentException: Only VectorDrawables and rasterized asset types are supported ex. PNG, JPG, WEBP
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.ui.res.PainterResources_androidKt.loadVectorResource(PainterResources.android.kt:95)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.ui.res.PainterResources_androidKt.painterResource(PainterResources.android.kt:67)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at com.corgimemo.app.ui.components.CorgiSnackbarKt.CorgiSnackbar$lambda$0(CorgiSnackbar.kt:77)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at com.corgimemo.app.ui.components.CorgiSnackbarKt$$ExternalSyntheticLambda0.invoke(D8$$SyntheticClass:0)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.kt:122)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.kt:52)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.material3.SurfaceKt$Surface$1.invoke(Surface.kt:130)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.material3.SurfaceKt$Surface$1.invoke(Surface.kt:110)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.kt:122)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.kt:52)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.runtime.CompositionLocalKt.CompositionLocalProvider(CompositionLocal.kt:408)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.material3.SurfaceKt.Surface-T9BRK9s(Surface.kt:107)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at com.corgimemo.app.ui.components.CorgiSnackbarKt.CorgiSnackbar(CorgiSnackbar.kt:58)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at com.corgimemo.app.ui.screens.main.ComposableSingletons$MainScreenKt.lambda_1709545928$lambda$0(MainScreen.kt:837)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at com.corgimemo.app.ui.screens.main.ComposableSingletons$MainScreenKt$$ExternalSyntheticLambda6.invoke(D8$$SyntheticClass:0)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.kt:131)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.kt:52)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.material3.SnackbarHostKt$FadeInFadeOutWithScale$2$1$1.invoke(SnackbarHost.kt:383)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.material3.SnackbarHostKt$FadeInFadeOutWithScale$2$1$1.invoke(SnackbarHost.kt:383)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.kt:122)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.kt:52)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.material3.SnackbarHostKt$FadeInFadeOutWithScale$1$1.invoke(SnackbarHost.kt:376)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.material3.SnackbarHostKt$FadeInFadeOutWithScale$1$1.invoke(SnackbarHost.kt:338)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.kt:131)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.kt:52)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.material3.SnackbarHostKt.FadeInFadeOutWithScale(SnackbarHost.kt:383)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.material3.SnackbarHostKt.SnackbarHost(SnackbarHost.kt:235)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.material3.SnackbarHostKt.SnackbarHost$lambda$1(Unknown Source:11)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.material3.SnackbarHostKt$$ExternalSyntheticLambda0.invoke(D8$$SyntheticClass:0)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.runtime.RecomposeScopeImpl.compose(RecomposeScopeImpl.kt:204)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.runtime.GapComposer.recomposeToGroupEnd(GapComposer.kt:1678)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.runtime.GapComposer.skipCurrentGroup(GapComposer.kt:2014)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.runtime.GapComposer.doCompose-aFTiNEg(GapComposer.kt:2655)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.runtime.GapComposer.recompose-aFTiNEg$runtime(GapComposer.kt:2577)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.runtime.CompositionImpl.recompose(Composition.kt:1164)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.runtime.Recomposer.performRecompose(Recomposer.kt:1318)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.runtime.Recomposer.access$performRecompose(Recomposer.kt:191)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.runtime.Recomposer$runRecomposeAndApplyChanges$2.invokeSuspend$lambda$2(Recomposer.kt:655)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.runtime.Recomposer$runRecomposeAndApplyChanges$2$$ExternalSyntheticLambda0.invoke(D8$$SyntheticClass:0)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.ui.platform.AndroidUiFrameClock$withFrameNanos$2$callback$1.doFrame(AndroidUiFrameClock.android.kt:39)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.ui.platform.AndroidUiDispatcher.performFrameDispatch(AndroidUiDispatcher.android.kt:108)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.ui.platform.AndroidUiDispatcher.access$performFrameDispatch(AndroidUiDispatcher.android.kt:41)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at androidx.compose.ui.platform.AndroidUiDispatcher$dispatchCallback$1.doFrame(AndroidUiDispatcher.android.kt:69)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at android.view.Choreographer$CallbackRecord.run(Choreographer.java:2081)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at android.view.Choreographer$CallbackRecord.run(Choreographer.java:2092)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at android.view.Choreographer.doCallbacks(Choreographer.java:1567)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at android.view.Choreographer.doFrame(Choreographer.java:1424)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at android.view.Choreographer$FrameDisplayEventReceiver.run(Choreographer.java:2046)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at android.os.Handler.handleCallback(Handler.java:1029)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at android.os.Handler.dispatchMessage(Handler.java:107)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at android.os.Looper.loopOnce(Looper.java:274)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at android.os.Looper.loop(Looper.java:369)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at android.app.ActivityThread.main(ActivityThread.java:10090)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at java.lang.reflect.Method.invoke(Native Method)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:616)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:1137)
07-14 13:47:49.921 19620 19620 E AndroidRuntime:        Suppressed: kotlinx.coroutines.internal.DiagnosticCoroutineContextException: [androidx.compose.runtime.PausableMonotonicFrameClock@8779e4d, androidx.compose.ui.platform.MotionDurationScaleImpl@9b80d02, StandaloneCoroutine{Cancelling}@8e05613, AndroidUiDispatcher@770a050]
