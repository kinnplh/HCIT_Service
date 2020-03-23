package pcg.hcit_service.Selector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pcg.hcit_service.AccessibilityNodeInfoRecord;
import pcg.hcit_service.Utility;

public class OperationSelector extends NodeSelector {
    public enum Operator {
        INTERSECTION(1), UNION(2), DIFF(3), XOR(4), UNKNOWN(-1);
        private final int v;
        Operator(int in){
            v = in;
        }

        public static Operator getFromInt(int in){
            switch (in){
                case 1:
                    return INTERSECTION;
                case 2:
                    return UNION;
                case 3:
                    return DIFF;
                case 4:
                    return XOR;
                default:
                    return UNKNOWN;
            }
        }

        public int toInt(){
            return v;
        }
    }

    public Operator operation;
    public Long scId1;
    public Long scId2;

    public SelectorChain sc1; // 延迟确认
    public SelectorChain sc2;

    public OperationSelector(JSONObject obj) throws JSONException {
        identifier = obj.getLong("id");
        JSONArray refIdJson = obj.getJSONArray("refIds");
        scId1 = refIdJson.getLong(0);
        scId2 = refIdJson.getLong(1);

        operation = Operator.getFromInt(obj.getInt("operation"));
    }

    @Override
    public List<AccessibilityNodeInfoRecord> getNode(AccessibilityNodeInfoRecord root, List<AccessibilityNodeInfoRecord> inputs) {
        Utility.assertTrue(false);
        Set<AccessibilityNodeInfoRecord> set1 = new LinkedHashSet<>();
        Set<AccessibilityNodeInfoRecord> set2 = new LinkedHashSet<>();
        if(sc1 != null){
            set1.addAll(sc1.getNode(root));
        }
        if(sc2 != null){
            set2.addAll(sc2.getNode(root));
        }

        if(operation == Operator.INTERSECTION){
            set1.retainAll(set2);
            return new ArrayList<>(set1);
        } else if(operation == Operator.UNION){
            set1.addAll(set2);
            return new ArrayList<>(set1);
        } else if(operation == Operator.DIFF){
            set1.removeAll(set2);
            return new ArrayList<>(set1);
        } else if(operation == Operator.XOR){
            Set<AccessibilityNodeInfoRecord> set3 = new LinkedHashSet<>(set1);
            set3.addAll(set2);
            set1.retainAll(set2);
            set3.removeAll(set1);
            return new ArrayList<>(set3);
        }

        return new ArrayList<>();
    }

    public void assignSelectorChain(Map<Long, SelectorChain> idToSC){
        sc1 = idToSC.get(scId1);
        sc2 = idToSC.get(scId2);
    }
}
