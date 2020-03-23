package pcg.hcit_service.Selector;

import android.graphics.Rect;
import android.util.Log;
import android.util.Pair;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import pcg.hcit_service.AccessibilityNodeInfoRecord;
import pcg.hcit_service.Utility;

public class RelativeNodeSelector extends NodeSelector {
    static public class PathInfo{
        enum RelationShip{
            PARENT(0), CHILD(1), SIBLING(2), UNKNOWN(-1);

            private final int v;
            RelationShip(int n){
                v = n;
            }

            public static RelationShip valueOf(int n){
                switch (n){
                    case 0:
                        return PARENT;
                    case 1:
                        return CHILD;
                    case 2:
                        return SIBLING;
                    default:
                        return UNKNOWN;
                }
            }

            public int toInt(){
                return v;
            }

            @Override
            public String toString() {
                return super.toString();
            }
        }
        int index;
        boolean indexMatters;
        RelationShip rel;
        boolean youngerBro;
        boolean elderBro;
        public PathInfo(String idx, int relation){
            rel = RelationShip.valueOf(relation);
            if(rel == RelationShip.UNKNOWN){
                Log.e("UnknownRelation", "PathInfo: Unknown Relationship " + relation);
            }
            if(Objects.equals(idx, "*")){
                index = -1;
                indexMatters = false;
            } else if((Objects.equals(idx, "-") || Objects.equals(idx, "+")) && rel == RelationShip.SIBLING){
                index = -1;
                indexMatters = true;
                if(Objects.equals(idx, "+")){
                    youngerBro = true;
                } else {
                    elderBro = true;
                }
            } else {
                try {
                    index = Integer.parseInt(idx);
                    indexMatters = true;
                } catch (NumberFormatException e){
                    index = -2;
                    indexMatters = false;
                    Log.e("InvalidIndex", "PathInfo: invalid index " + idx);
                }
            }
        }

        public List<AccessibilityNodeInfoRecord> mapTo(AccessibilityNodeInfoRecord node){
            List<AccessibilityNodeInfoRecord> res = new ArrayList<>();
            if(rel == RelationShip.PARENT){
                int crtIndex = index;
                AccessibilityNodeInfoRecord crt = node;
                while (crt != null && crtIndex != 0){
                    crt = crt.getParent();
                    crtIndex -= 1;
                    if(crt != null){
                        if(crtIndex <= 0){
                            res.add(crt);
                        }
                    }
                }
            } else if(rel == RelationShip.SIBLING){
                if(node.getParent() != null){
                    AccessibilityNodeInfoRecord parent = node.getParent();
                    if(!indexMatters){
                        res.addAll(parent.getChildren());
                    } else if(elderBro) {
                        int crtIndex = parent.getChildren().indexOf(node);
                        if(crtIndex > 0){
                            res.addAll(parent.getChildren().subList(0, crtIndex));
                        }
                    } else if(youngerBro){
                        int crtIndex = parent.getChildren().indexOf(node);
                        if(crtIndex >= 0 && crtIndex < parent.getChildCount()){
                            res.addAll(parent.getChildren().subList(crtIndex + 1, parent.getChildCount()));
                        }
                    } else {
                        int indexOfCrt = parent.getChildren().indexOf(node);
                        if(indexOfCrt < 0){
                            Log.e("NodeNotChildOfParent", "mapTo: node not found in parent's child list");
                        } else {
                            int newIndex = indexOfCrt + index;
                            if(newIndex >= 0 && newIndex < parent.getChildren().size()){
                                res.add(parent.getChildren().get(newIndex));
                            }
                        }
                    }
                }
            } else if(rel == RelationShip.CHILD){
                if(!indexMatters){
                    res.addAll(node.getChildren());
                } else if(index >= 0 && index < node.getChildCount()){
                    res.add(node.getChild(index));
                }
            }

            return res;
        }
    }

    public enum OrientationSelection{
        UP(0), RIGHT(1), DOWN(2), LEFT(3), UNKNOWN(-1);

        private final int v;
        private OrientationSelection(int n){
            v = n;
        }

        public int toInt(){
            return v;
        }

        public static OrientationSelection valueOf(int v){
            switch (v){
                case 0:
                    return UP;
                case 1:
                    return RIGHT;
                case 2:
                    return DOWN;
                case 3:
                    return LEFT;
                default:
                    return UNKNOWN;
            }
        }

    }
    public enum BoundarySelection{
        INNER(0), OUTER(1), UNKNOWN(-1);

        private final int v;
        private BoundarySelection(int n){
            v = n;
        }

        public static BoundarySelection valueOf(int n){
            switch (n){
                case 0:
                    return INNER;
                case 1:
                    return OUTER;
                default:
                    return UNKNOWN;
            }
        }

        public int toInt(){
            return v;
        }
    }


    // public SelectorChainRefSelector base;

    public boolean boundaryMatters;
    public boolean pathMatters;
    public boolean orientationMatters;

    public List<PathInfo> pathInfoList;
    public OrientationSelection orientationSelect;
    public BoundarySelection boundarySelect;

    public RelativeNodeSelector(JSONObject obj) throws JSONException {
        identifier = obj.getLong("id");

        JSONObject props = obj.getJSONObject("props");
        pathMatters = props.getBoolean("pathMatters");
        if(pathMatters){
            pathInfoList = new ArrayList<>();
            JSONArray pathInfoArray = props.getJSONArray("pathExpArray");
            for(int i = 0; i < pathInfoArray.length(); ++ i){
                JSONObject crt = pathInfoArray.getJSONObject(i);
                String idx = crt.getString("value");
                int rel = crt.getInt("rel");
                pathInfoList.add(new PathInfo(idx, rel));
            }
        } else {
            pathInfoList = null;
        }

        orientationMatters = props.getBoolean("orientationMatters");
        if(orientationMatters){
            orientationSelect = OrientationSelection.valueOf(props.getInt("orientationSelect"));
        } else {
            orientationSelect = OrientationSelection.UNKNOWN;
        }

        boundaryMatters = props.getBoolean("boundaryMatters");
        if(boundaryMatters){
            boundarySelect = BoundarySelection.valueOf(props.getInt("boundarySelect"));
        } else {
            boundarySelect = BoundarySelection.UNKNOWN;
        }

    }

    private LinkedHashSet<AccessibilityNodeInfoRecord> getNodeByPath(List<AccessibilityNodeInfoRecord> inputs){
        List<AccessibilityNodeInfoRecord> crtNodes = new ArrayList<>(inputs);
        for(PathInfo info: pathInfoList){
            List<AccessibilityNodeInfoRecord> tmpRes = new ArrayList<>();
            for(AccessibilityNodeInfoRecord n: crtNodes){
                tmpRes.addAll(info.mapTo(n));
            }

            crtNodes = tmpRes;
        }
        return new LinkedHashSet<>(crtNodes);
    }

    private static Rect getBoundsCoverAll(Collection<AccessibilityNodeInfoRecord> records){
        if(records == null || records.isEmpty()){
            return new Rect(0,0,0,0);
        }
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;

        for(AccessibilityNodeInfoRecord r: records){
            Rect crt = new Rect();
            r.getBoundsInScreen(crt);
            left = Math.min(left, crt.left);
            top = Math.min(top, crt.top);
            right = Math.max(right, crt.right);
            bottom = Math.max(bottom, crt.bottom);
        }

        return new Rect(left, top, right, bottom);
    }

    private LinkedHashSet<AccessibilityNodeInfoRecord> getNodeByOrientation(AccessibilityNodeInfoRecord root, List<AccessibilityNodeInfoRecord> inputs){
        if(inputs.isEmpty()){
            return new LinkedHashSet<>();
        }

        final Rect allCover = getBoundsCoverAll(inputs);
        final List<AccessibilityNodeInfoRecord> res = new ArrayList<>();
        Utility.Visitor mVisitor = new Utility.Visitor() {
            @Override
            public void visitNode(AccessibilityNodeInfoRecord record) {
                Rect r = new Rect();
                record.getBoundsInScreen(r);
                switch (orientationSelect){
                    case UP:
                        if(r.bottom <= allCover.top ){
                            res.add(record);
                        }
                        break;
                    case DOWN:
                        if(r.top >= allCover.bottom){
                            res.add(record);
                        }
                        break;
                    case LEFT:
                        if(r.right <= allCover.left){
                            res.add(record);
                        }
                        break;
                    case RIGHT:
                        if(r.left >= allCover.right){
                            res.add(record);
                        }
                        break;
                    default:
                        break;
                }
            }
        };

        Utility.Visitor.visit(root, mVisitor);
        return new LinkedHashSet<>(res);
    }

    private LinkedHashSet<AccessibilityNodeInfoRecord> getNodeByBoundary(AccessibilityNodeInfoRecord root, List<AccessibilityNodeInfoRecord> inputs){
        if(inputs.isEmpty()){
            return new LinkedHashSet<>();
        }
        final List<AccessibilityNodeInfoRecord> res = new ArrayList<>();
        Utility.Visitor myVisitor = null;
        final List<Pair<AccessibilityNodeInfoRecord, Rect>> allRect = new ArrayList<>();
        for(AccessibilityNodeInfoRecord node: inputs){
            Rect r = new Rect();
            node.getBoundsInScreen(r);
            allRect.add(new Pair<>(node, r));
        }

        if(boundarySelect == BoundarySelection.INNER){
            // 在其中任意一个节点内部就好
            myVisitor = new Utility.Visitor() {
                @Override
                public void visitNode(AccessibilityNodeInfoRecord record) {
                    Rect crt = new Rect();
                    record.getBoundsInScreen(crt);
                    for(Pair<AccessibilityNodeInfoRecord, Rect> r: allRect){
                        if(record != r.first && r.second.contains(crt)){
                            res.add(record);
                            return;
                        }
                    }
                }
            };
        } else if(boundarySelect == BoundarySelection.OUTER){
            // 要求在所有的节点的外面
            myVisitor = new Utility.Visitor() {
                @Override
                public void visitNode(AccessibilityNodeInfoRecord record) {
                    boolean hasRectContainCrt = false;
                    Rect crt = new Rect();
                    record.getBoundsInScreen(crt);
                    for(Pair<AccessibilityNodeInfoRecord, Rect> p: allRect){
                        if(p.second.contains(crt)){ // 两个相同的rect，contains 为 true
                            hasRectContainCrt = true;
                            break;
                        }
                    }
                    if(!hasRectContainCrt){
                        res.add(record);
                    }
                }
            };
        }

        Utility.Visitor.visit(root, myVisitor);
        return new LinkedHashSet<>(res);
    }

    @Override
    public List<AccessibilityNodeInfoRecord> getNode(AccessibilityNodeInfoRecord root, List<AccessibilityNodeInfoRecord> inputs) {
        LinkedHashSet<AccessibilityNodeInfoRecord> res = new LinkedHashSet<>();
        if(pathMatters){
            res.addAll(getNodeByPath(inputs));
        }

        if(orientationMatters){
            LinkedHashSet<AccessibilityNodeInfoRecord> subRes = getNodeByOrientation(root, inputs);
            if(!pathMatters){
                res.addAll(subRes);
            } else {
                res.retainAll(subRes);
            }
        }

        if(boundaryMatters){
            LinkedHashSet<AccessibilityNodeInfoRecord> subRes = getNodeByBoundary(root, inputs);
            if(!pathMatters && !orientationMatters){
                res.addAll(subRes);
            } else {
                res.retainAll(subRes);
            }
        }

        return new ArrayList<>(res);
    }
}
