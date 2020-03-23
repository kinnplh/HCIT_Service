package pcg.hcit_service;

import android.content.Context;
import android.media.AudioManager;
import android.util.ArrayMap;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import pcg.hcit_service.Template.PageTemplateInfo;

/**
 * an example class
 */
public class MyExampleClass extends InteractionProxy {
    public static final String TAG  = "MyExampleClass";
    private Context context;
    private boolean autoJumpTried;

    @Override
    public void onCreate(Context context) {
        this.context = context;
    }

    @Override
    public void onServiceConnected() {

    }

    @Override
    public void onDestroy() {

    }

    @Override
    public void onInterrupt() {

    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                AudioManager mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (mAudioManager != null) {
                    mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                }
                autoJumpTried = false;
                break;
            case KeyEvent.KEYCODE_VOLUME_UP:
                mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (mAudioManager != null) {
                    mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                }
                autoJumpTried = false;
                break;
        }
        return super.onKeyEvent(event);
    }

    @Override
    public void onPageChange(String lastPageName, String newPageName) {
        if(Objects.equals(newPageName, "alipay_index")){
            AccessibilityNodeInfoRecord crt = AccessibilityNodeInfoRecord.root;
            while (crt != null){
                crt = crt.next(false);
                if(crt != null)
                    Log.i(TAG, "next: " + crt.toAllString());
            }
            Log.i(TAG, "onPageChange: ========");
            crt = AccessibilityNodeInfoRecord.root.lastInSubTree();
            while (crt != null){
                crt = crt.prev(false);
                if(crt != null)
                    Log.i(TAG, "prev: " + crt.toAllString());
            }
            if(!autoJumpTried){
               Utility.toast(context, "Enter Alipay", Toast.LENGTH_LONG);
               List<PageTemplateInfo.TransInfo> res = NodeAccessController.calTransitionPath("com.eg.android.AlipayGphone", 0, 74, Collections.<PageTemplateInfo.TransInfo>emptySet(), Collections.<Integer>emptySet());
               Map<String, String> paraValues = new ArrayMap<>();
               paraValues.put("列表朋友", "段续光");
               paraValues.put("联系人", "段续光");
               paraValues.put("转账金额", "0.01");
               NodeAccessController.jumpByTransInfoList(res, new NodeAccessController.JumpResCallBack() {
                   @Override
                   public void onResult(boolean successful, String crtPageName, int successStep, PageTemplateInfo.TransInfo crt, List<PageTemplateInfo.TransInfo> oriPath, NodeAccessController.JumpFailReason reason) {
                       Log.i(TAG, "onResult: res " + successful);
                       autoJumpTried = true;
                   }
               }, paraValues);
           }
        }
    }

    @Override
    public void onPageUpdate(String currentPageName, Map<String, List<AccessibilityNodeInfoRecord>> changeTypeToNodeList) {
        Log.i(TAG, "page change");
        List<AccessibilityNodeInfoRecord> newCreated = changeTypeToNodeList.get(AccessibilityNodeInfoRecord.CHANGE_NEW_CREATED);
        if (newCreated != null) {
            for(AccessibilityNodeInfoRecord node: newCreated){
                Log.i(TAG, "new create: " + node.getAllTexts());
            }
        } else {
            Log.w(TAG, "new created is null");
        }
    }

    @Override
    public void onUnknownPageContentChange(String lastPageName, Map<String, List<AccessibilityNodeInfoRecord>> changeTypeToNodeList) {

    }

}
