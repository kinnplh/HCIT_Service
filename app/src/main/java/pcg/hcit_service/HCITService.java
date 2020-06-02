package pcg.hcit_service;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import pcg.hcit_service.Template.TemplateManager;

/**
 * Main service of the project. What you need do is changing {@link HCITService#YOUR_CLASS_NAME}.
 */
public class HCITService extends AccessibilityService {
    public static final String TAG = "HCITService";
    public static final String YOUR_CLASS_NAME = "pcg.hcit_service.MyExampleClass";
    private static HCITService instance;
    private RefreshThread refreshThread;
    private String topActivityName;
    private InteractionProxy proxy;
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Utility.init(this);
        refreshThread  = RefreshThread.getInstance();
        new Thread(){
            @Override
            public void run() {
                TemplateManager.init(instance);
                TestUtility.test();
            }
        }.start();
    }

    /**
     *
     * @return an instance of current service
     */
    public static HCITService getInstance() {
        return instance;
    }

    @Override
    protected boolean onGesture(int gestureId) {
        if(proxy != null){
            return proxy.onGesture(gestureId);
        }
        return super.onGesture(gestureId);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if(proxy != null){
            return proxy.onKeyEvent(event);
        }
        return super.onKeyEvent(event);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        refreshThread.start();
        try {
            Class<?> cls = Class.forName(YOUR_CLASS_NAME);
            Constructor constructor = cls.getConstructor();
            proxy = (InteractionProxy) constructor.newInstance();
            proxy.onCreate(this);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            e.printStackTrace();
            Utility.toast(this, "Creating proxy failed", Toast.LENGTH_LONG);
            disableSelf();
            return;
        }
        if(proxy != null){
            proxy.onServiceConnected();
        }
    }

    // learn from ProcessorScreen.java
    private int windowIdForNotification = -1;
    private void updateWindowIdForNotification(AccessibilityEvent event){
        if(event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED){
            return;
        }
        List<CharSequence> texts = event.getText();
        if(texts.isEmpty()){
            return;
        }

        CharSequence title = texts.get(0);
        if(title != null && (title.toString().startsWith("通知栏")
                || title.toString().startsWith("Notification"))){
            windowIdForNotification = Utility.getWindowIdByEvent(event);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
        updateWindowIdForNotification(accessibilityEvent);
        if(accessibilityEvent.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && accessibilityEvent.getPackageName() != null && accessibilityEvent.getClassName() != null) {
            ComponentName componentName = new ComponentName(accessibilityEvent.getPackageName().toString(), accessibilityEvent.getClassName().toString());
            ActivityInfo activityInfo = getActivityInfo(componentName);
            boolean isActivity = activityInfo != null;
            if (isActivity) {
                String activityName = componentName.flattenToShortString();
                Log.i("activity name", "name: " + activityName + " " + String.valueOf(accessibilityEvent.getEventType()));
                topActivityName = activityName;
            }
        }

        if((accessibilityEvent.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                || accessibilityEvent.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                || accessibilityEvent.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
                ){ // todo 更好地处理测试逻辑
            final AccessibilityEvent copiedEvent = AccessibilityEvent.obtain(accessibilityEvent);
            int windowId = Utility.getWindowIdByEvent(accessibilityEvent);
            if((windowId != windowIdForNotification) && copiedEvent.getPackageName() != null && (copiedEvent.getPackageName().toString().contains("com.android.systemui") ||
                    copiedEvent.getPackageName().toString().contains("com.huawei.screenrecorder"))){
                // todo 处理系统变化
            } else if(copiedEvent.getPackageName() != null && copiedEvent.getPackageName().toString().contains("talkbackplus")){
                // todo 处理自身变化
            } else if(windowId == windowIdForNotification && proxy.ignoreNotificationWindow()){

            }
            else {
                AccessibilityNodeInfo source = copiedEvent.getSource();
                if(source == null) {
                    source = getRootInActiveWindow();
                }
                if(source != null && (isWindowActive(source)) || windowId == windowIdForNotification) {
                    synchronized (RefreshThread.QUEUE_OP_MUTEX){
                        refreshThread.setWhenChangeNodeArrive();
                        refreshThread.addChangedNode(source);
                    }
                }

                copiedEvent.recycle();
            }
        }

        if(proxy != null){
            proxy.onAccessibilityEvent(accessibilityEvent);
        }
    }

    private ActivityInfo getActivityInfo(ComponentName componentName) {
        try {
            return getPackageManager().getActivityInfo(componentName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /**
     * get current activity name of the running app (like alipay)
     * @return activity name
     */
    public String getTopActivityName() {
        return topActivityName;
    }

    @Override
    public void onInterrupt() {
        if(proxy != null){
            proxy.onInterrupt();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        refreshThread.stopSelf();
        if(proxy != null){
            proxy.onDestroy();
        }
    }

    private boolean isWindowActive(AccessibilityNodeInfo node){
        if(node == null)
            return false;
        // todo 27 及以下版本的通知栏识别
        if(Build.VERSION.SDK_INT >= 28 && node.getPaneTitle() != null && node.getPaneTitle().toString().contains("通知栏")){
            return true;
        }
        AccessibilityWindowInfo windowInfo = node.getWindow();
        if(windowInfo == null){
            return false;
        }
        boolean res = windowInfo.getType() == AccessibilityWindowInfo.TYPE_APPLICATION;
        windowInfo.recycle();
        return res;
    }
}
