package pcg.hcit_service.Selector;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import pcg.hcit_service.AccessibilityNodeInfoRecord;

public class SelectorChainRefSelectorForDiff extends NodeSelector {
    public Long refSelectorChainId;
    public SelectorChain refChain; // 延后决定


    public SelectorChainRefSelectorForDiff(JSONObject obj) throws JSONException {
        identifier = obj.getLong("id");
        refSelectorChainId = obj.getLong("refId");
    }

    @Override
    public List<AccessibilityNodeInfoRecord> getNode(AccessibilityNodeInfoRecord root, List<AccessibilityNodeInfoRecord> inputs) {
        return new ArrayList<>(inputs); // 实际上不影响输入输出
    }

    public void assignSelectorChain(Map<Long, SelectorChain> idToSC){
        refChain = idToSC.get(refSelectorChainId);
        if(refChain == null){
            Log.e("RefSelectorNotFound", "assignSelectorChain: RefSelectorNotFound");
        }
    }
}
