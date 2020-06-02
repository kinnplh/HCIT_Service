package pcg.hcit_service;

import android.content.Context;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * override this class to run your code.
 * do not forget to change {@link HCITService#YOUR_CLASS_NAME}
 */
abstract public class InteractionProxy implements RefreshThread.ContentChangeListener {
    public InteractionProxy(){
        RefreshThread.getInstance().bindContentChangeListener(this);
    }

    abstract public boolean ignoreNotificationWindow();
    /**
     *
     * called in {@link HCITService#onCreate()}
     * @param context an accessibility service
     */
    abstract public void onCreate(Context context);

    /**
     * called in {@link HCITService#onServiceConnected()}
     */
    abstract public void onServiceConnected();

    /**
     * called in {@link HCITService#onDestroy()}
     */
    abstract public void onDestroy();

    /**
     * handle accessibility event. called in {@link HCITService#onAccessibilityEvent(AccessibilityEvent)}
     * @param event accessibility event. This event is owned by the caller and cannot be used after
     * this method returns. Services wishing to use the event after this method returns should
     * make a copy.
     */
    public void onAccessibilityEvent(AccessibilityEvent event){}

    /**
     * called in {@link HCITService#onInterrupt()}
     */
    abstract public void onInterrupt();

    /**
     * called in {@link HCITService#onGesture(int)}
     * only works if touch exploration mode is enabled.
     * @param gestureId The unique id of the performed gesture.
     * @return Whether the gesture was handled
     */
    public boolean onGesture(int gestureId){return false;}

    /**
     * Callback that allows an accessibility service to observe the key events before they are passed to the rest of the system.
     *
     * This event is owned by the caller and cannot be used
     * after this method returns. Services wishing to use the event after this method returns should
     * make a copy.
     * @param event the event to handle
     * @return If true then the event will be consumed and not delivered to applications, otherwise it will be delivered as usual.
     */
    public boolean onKeyEvent(KeyEvent event){return true;}
}
