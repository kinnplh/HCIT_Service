package pcg.hcit_service.Selector;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

import pcg.hcit_service.AccessibilityNodeInfoRecord;
import pcg.hcit_service.Utility;

public class SelectorChainRefSelector extends NodeSelector {
    public Long refSelectorChainId;
    public SelectorChain refChain; // 延后决定


    public SelectorChainRefSelector(JSONObject obj) throws JSONException {
        identifier = obj.getLong("id");
        refSelectorChainId = obj.getLong("refId");
    }

    @Override
    public List<AccessibilityNodeInfoRecord> getNode(AccessibilityNodeInfoRecord root, List<AccessibilityNodeInfoRecord> inputs) {
        //  请使用refChain 的结果
        if(refChain != null){
            return refChain.getNode(root, inputs);
        }

        return null; // 一般来说找不到是 return 空。只有当这个 selector 配置出错当时候才会返回 null
    }

    public void assignSelectorChain(Map<Long, SelectorChain> idToSC){
        refChain = idToSC.get(refSelectorChainId);
        if(refChain == null){
            Log.e("RefSelectorNotFound", "assignSelectorChain: RefSelectorNotFound");
        }
    }
}
