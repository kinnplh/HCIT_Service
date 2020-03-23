package pcg.hcit_service.Selector;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pcg.hcit_service.AccessibilityNodeInfoRecord;

public class AbsoluteNodeSelector extends NodeSelector {
    public enum Mode {
        MODE_FIRST_SATISFY_ANCESTOR("MODE_FIRST_SATISFY_ANCESTOR"), MODE_SELF_SATISFY("MODE_SELF_SATISFY"), MODE_SUB_TREE_CONTAINS("MODE_SUB_TREE_CONTAINS"), MODE_EXCLUDE_SUB("MODE_EXCLUDE_SUB"), UNKNOWN("UNKNOWN");

        private final String str;
        Mode(String s){
            str = s;
        }

        public static Mode getFromStr(String in){
            if(Objects.equals(in, "MODE_SELF_SATISFY")){
                return MODE_SELF_SATISFY;
            } else if(Objects.equals(in, "MODE_SUB_TREE_CONTAINS")){
                return MODE_SUB_TREE_CONTAINS;
            } else if(Objects.equals(in, "MODE_EXCLUDE_SUB")){
                return MODE_EXCLUDE_SUB;
            } else if(Objects.equals(in, "MODE_FIRST_SATISFY_ANCESTOR")){
                return MODE_FIRST_SATISFY_ANCESTOR;
            }  else {
                return UNKNOWN;
            }
        }

        public String toString(){
            return str;
        }
    }

    public Mode mode;

    public boolean textMatters;
    public Pattern textReg;

    public boolean contentDescMatters;
    public Pattern contentDescReg;

    public boolean resourceIdMatters;
    public Pattern resourceIdReg;

    public boolean classNameMatters;
    public Pattern classNameReg;

    public boolean clickableMatters;
    public boolean shouldClickable;

    public boolean editableMatters;
    public boolean shouldEditable;

    public boolean scrollableMatters;
    public boolean shouldScrollable;

    public boolean checkableMatters;
    public boolean shouldCheckable;

    public boolean checkedMatters;
    public boolean shouldChecked;

    public boolean selectedMatters;
    public boolean shouldSelected;

    public int subDistance;

    public AbsoluteNodeSelector(JSONObject obj) throws JSONException {
        identifier = obj.getLong("id");
        mode = Mode.getFromStr(obj.getString("mode"));

        JSONObject props = obj.getJSONObject("props");
        Iterator<String> propsIter = props.keys();
        while (propsIter.hasNext()){
            String crtProp = propsIter.next();
            if(Objects.equals("subDistance", crtProp)){
                String res = props.getString("subDistance");
                if(Objects.equals(res, "*")){
                    subDistance = Integer.MAX_VALUE;
                } else {
                    subDistance = Integer.parseInt(res);
                }
                continue;
            }

            try {
                Field f = this.getClass().getDeclaredField(crtProp);
                if(f.getType().isAssignableFrom(Pattern.class)){
                    f.set(this, Pattern.compile(props.getString(crtProp)));
                } else {
                    f.set(this, props.get(crtProp));
                }

            } catch (NoSuchFieldException e) {
                Log.w("FieldNotFound", "AbsoluteNodeSelector: field not found " + crtProp);
            } catch (IllegalAccessException e) {
                Log.w("AccessFailed", "AbsoluteNodeSelector: can not access " + crtProp);
            }
        }


    }

    private boolean canMatchNode(AccessibilityNodeInfoRecord node){
        if(node == null){
            return false;
        }
        if(textMatters){
            String text = node.getText() == null? "": node.getText().toString();
            Matcher m = textReg.matcher(text);
            if(!m.find()){
                return false;
            }
        }

        if(contentDescMatters){
            String content = node.getContentDescription() == null? "": node.getContentDescription().toString();
            Matcher m = contentDescReg.matcher(content);
            if(!m.find()){
                return false;
            }
        }

        if(resourceIdMatters){
            String resourceId = node.getViewIdResourceName() == null? "": node.getViewIdResourceName().toString();
            Matcher m = resourceIdReg.matcher(resourceId);
            if(!m.find()){
                return false;
            }
        }

        if(classNameMatters){
            String className = node.getClassName() == null? "": node.getClassName().toString();
            Matcher m = classNameReg.matcher(className);
            if(!m.find()){
                return false;
            }
        }

        if(clickableMatters && node.isClickable() != shouldClickable){
            return false;
        }

        if(editableMatters && node.isEditable() != shouldEditable){
            return false;
        }

        if(scrollableMatters && node.isScrollable() != shouldScrollable){
            return false;
        }

        if(checkableMatters && node.isCheckable() != shouldCheckable){
            return false;
        }

        if(checkedMatters && node.isChecked() != shouldChecked){
            return false;
        }

        if(selectedMatters && node.isSelected() != shouldSelected){
            return false;
        }

        return true;
    }

    private void getResInSelfSatisfy(List<AccessibilityNodeInfoRecord> result, AccessibilityNodeInfoRecord crtNode){
        if(canMatchNode(crtNode)){
            result.add(crtNode);
        }

        for(AccessibilityNodeInfoRecord c: crtNode.getChildren()){
            getResInSelfSatisfy(result, c);
        }
    }

    private void getResInSelfSatisfyButNoSub(List<AccessibilityNodeInfoRecord> result, AccessibilityNodeInfoRecord crtNode){
        if(canMatchNode(crtNode)){
            result.add(crtNode);
        }
    }

    private boolean getResInSubTreeContain(List<AccessibilityNodeInfoRecord> result, AccessibilityNodeInfoRecord crtNode, int crtDepth){
        boolean isSubTreeContain = false;
        if(crtDepth <= subDistance){
            for(AccessibilityNodeInfoRecord c: crtNode.getChildren()){
                if(getResInSubTreeContain(result, c, crtDepth + 1)){
                    isSubTreeContain = true;
                }
            }
        }

        isSubTreeContain = isSubTreeContain || canMatchNode(crtNode);

        if(isSubTreeContain){
            result.add(crtNode);
            return true;
        }
        return false;
    }

    private boolean getResInFirstSatisfyAncestor(List<AccessibilityNodeInfoRecord> result, AccessibilityNodeInfoRecord crtNode){
        AccessibilityNodeInfoRecord crt = crtNode.getParent();
        while (crt != null){
            if(canMatchNode(crt)){
                result.add(crt);
                break;
            }
            crt = crt.getParent();
        }
        return true;
    }

    @Override
    public List<AccessibilityNodeInfoRecord> getNode(AccessibilityNodeInfoRecord root, List<AccessibilityNodeInfoRecord> inputs) {
        LinkedHashSet<AccessibilityNodeInfoRecord> resAsSet = new LinkedHashSet<>(); // 据说可以保证顺序 （但是这里主要的目的是去重）
        for(AccessibilityNodeInfoRecord n: inputs){
            List<AccessibilityNodeInfoRecord> subRes = new ArrayList<>();
            if(mode == Mode.MODE_SELF_SATISFY){
                getResInSelfSatisfy(subRes, n);
            } else if(mode == Mode.MODE_SUB_TREE_CONTAINS){
                getResInSubTreeContain(subRes, n, 0);
            } else if(mode == Mode.MODE_EXCLUDE_SUB){
                getResInSelfSatisfyButNoSub(subRes, n);
            } else if(mode == Mode.MODE_FIRST_SATISFY_ANCESTOR){
                getResInFirstSatisfyAncestor(subRes, n);
            }

            resAsSet.addAll(subRes);
        }
        return new ArrayList<>(resAsSet);
    }
}
