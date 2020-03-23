package pcg.hcit_service.Selector;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import pcg.hcit_service.AccessibilityNodeInfoRecord;

public abstract class NodeSelector {
    public long identifier = -1;
    final public List<AccessibilityNodeInfoRecord> getNode(AccessibilityNodeInfoRecord root){
        List<AccessibilityNodeInfoRecord> inputs = new ArrayList<>();
        inputs.add(root);
        return getNode(root, inputs);
    }
    public abstract List<AccessibilityNodeInfoRecord> getNode(AccessibilityNodeInfoRecord root, List<AccessibilityNodeInfoRecord> inputs);

    public static NodeSelector obtain(JSONObject obj){
        try {
            if(obj == null){
                return null;
            }
            String selectorName = "SelectorChain";
            if(obj.has("type")){
                selectorName = obj.getString("type");
            } else if(obj.has("name")){
                selectorName = obj.getString("name");
            }

            Class<?> selectorClass = Class.forName("pcg.hcit_service.Selector." + selectorName);
            Constructor c = selectorClass.getConstructor(JSONObject.class);
            return (NodeSelector) c.newInstance(obj);
        } catch (NoSuchMethodException | JSONException | ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            return null;
        }
    }
}
