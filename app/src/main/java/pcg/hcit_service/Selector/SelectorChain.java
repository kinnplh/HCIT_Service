package pcg.hcit_service.Selector;

import android.graphics.Rect;
import android.util.Log;
import android.util.Pair;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import pcg.hcit_service.AccessibilityNodeInfoRecord;
import pcg.hcit_service.Utility;

public class SelectorChain extends NodeSelector {
    public List<NodeSelector> selectors;
    public int startIndex;
    public Integer endIndex;

    public boolean needFindInPage;
    public boolean inexistence;

    public enum SortType {
        POS_TOP_TO_BOTTOM(1), NUM_SMALL_TO_LARGE(2), POS_IN_TREE(3), UNKNOWN(-1);
        private static Comparator<AccessibilityNodeInfoRecord> comparatorPosTopToBottom;
        private static Comparator<AccessibilityNodeInfoRecord> comparatorNumSmallToLarge;
        private static Comparator<AccessibilityNodeInfoRecord> comparatorPosInTree;

        static {
            comparatorPosTopToBottom = new Comparator<AccessibilityNodeInfoRecord>() {
                @Override
                public int compare(AccessibilityNodeInfoRecord o1, AccessibilityNodeInfoRecord o2) {
                    /*o1.nodeInfo.refresh();
                    o2.nodeInfo.refresh();*/
                    Rect r1 = new Rect();
                    Rect r2 = new Rect();
                    o1.getBoundsInScreen(r1);
                    o2.getBoundsInScreen(r2);
                    if(r1.top < r2.top){
                        return -1;
                    } else if(r1.top > r2.top){
                        return 1;
                    } else {
                        if(r1.right < r2.right){
                            return -1;
                        } else if(r1.right > r2.right){
                            return 1;
                        }
                        return 0;
                    }
                }
            };

            comparatorNumSmallToLarge = new Comparator<AccessibilityNodeInfoRecord>() {
                @Override
                public int compare(AccessibilityNodeInfoRecord o1, AccessibilityNodeInfoRecord o2) {
                    String text1 = o1.getText() == null? "": o1.getText().toString();
                    String text2 = o2.getText() == null? "": o2.getText().toString();
                    int num1 = Integer.MAX_VALUE;
                    int num2 = Integer.MAX_VALUE;
                    try {
                        num1 = Integer.valueOf(text1);
                    } catch (NumberFormatException ignored){}

                    try {
                        num2 = Integer.valueOf(text2);
                    } catch (NumberFormatException ignored){}

                    return Integer.compare(num1, num2);
                }
            };

            comparatorPosInTree = new Comparator<AccessibilityNodeInfoRecord>() {
                @Override
                public int compare(AccessibilityNodeInfoRecord o1, AccessibilityNodeInfoRecord o2) {
                    if(o1.depth != o2.depth){
                        return Integer.compare(o1.depth, o2.depth);
                    } else {
                        return Integer.compare(o1.index, o2.index);
                    }
                }
            };
        }

        final int v;
        SortType(int  v){
            this.v = v;
        }

        public static SortType valueOf(int n){
            switch (n){
                case 1:
                    return POS_TOP_TO_BOTTOM;
                case 2:
                    return NUM_SMALL_TO_LARGE;
                case 3:
                    return POS_IN_TREE;
                default:
                    return UNKNOWN;
            }
        }

        public Comparator<AccessibilityNodeInfoRecord> getComparator() {
            switch (this){
                case NUM_SMALL_TO_LARGE:
                    return comparatorNumSmallToLarge;
                case POS_IN_TREE:
                    return comparatorPosInTree;
                default:
                    return comparatorPosTopToBottom;
            }
        }
    }

    SortType sortType;

    public SelectorChain(JSONObject obj) throws JSONException {
        selectors = new ArrayList<>();
        startIndex = 0;
        if(obj.has("indexStart") && !Objects.equals(obj.get("indexStart"), null)){
            startIndex = obj.getInt("indexStart");
        }
        endIndex = null;
        if(obj.has("indexEnd") && !Objects.equals(obj.get("indexEnd"), null)){
            endIndex = obj.getInt("indexEnd");
        }
        identifier = obj.getLong("id");

        JSONArray selectorObjList = obj.getJSONArray("selectors");
        for(int i = 0; i < selectorObjList.length(); ++ i){
            NodeSelector subSelector = NodeSelector.obtain(selectorObjList.getJSONObject(i));

            if(subSelector == null){
                Log.e("BuildSelectorChain", "SelectorChain: sub selector build failed");
                break;
            }

            if(subSelector instanceof SelectorChain){
                Log.w("SelectorChainAsSubSelector", "SelectorChain: SelectorChain as sub selector. Use SelectorChainRefSelector instead");
            }

            selectors.add(subSelector);
        }

        sortType = SortType.POS_TOP_TO_BOTTOM;
        if(obj.has("sortType")){
            sortType = SortType.valueOf(obj.getInt("sortType"));
        }

        needFindInPage = obj.has("needFindInPage") && obj.getBoolean("needFindInPage");
        inexistence = obj.has("inexistence") && obj.getBoolean("inexistence");
    }

    public Pair<List<AccessibilityNodeInfoRecord>, Utility.BidirectionalListMap<NodeSelector, AccessibilityNodeInfoRecord>> getNodesGivenRefCandidates(AccessibilityNodeInfoRecord root,
                                                                                                                                                       Utility.BidirectionalListMapOverwrite<NodeSelector, AccessibilityNodeInfoRecord> prevRes){
        if(prevRes == null){
            return new Pair<>(getNode(root), new Utility.BidirectionalListMap<NodeSelector, AccessibilityNodeInfoRecord>());
        }

        Utility.BidirectionalListMap<NodeSelector, AccessibilityNodeInfoRecord> newRef = new Utility.BidirectionalListMap<>();

        int startSelectionIndex = 0;
        List<AccessibilityNodeInfoRecord> inputs = new ArrayList<>();
        if(selectors.isEmpty() || !((selectors.get(0) instanceof SelectorChainRefSelector) || (selectors.get(0) instanceof OperationSelector))){
            inputs.add(root);
        } else if(selectors.get(0) instanceof SelectorChainRefSelector){
            SelectorChainRefSelector firstSelector = (SelectorChainRefSelector) selectors.get(0);
            SelectorChain referred = firstSelector.refChain;
            if(referred == null){
                Log.e("ReferredChainIsNull", "getNodesGivenRefCandidates: referred chain not filled id = " + firstSelector.refSelectorChainId);
                return new Pair<List<AccessibilityNodeInfoRecord>, Utility.BidirectionalListMap<NodeSelector, AccessibilityNodeInfoRecord>>(new ArrayList<AccessibilityNodeInfoRecord>(), new Utility.BidirectionalListMap<NodeSelector, AccessibilityNodeInfoRecord>());
            }
            if(!prevRes.hasT1(referred)){
                Log.w("ReferredChainNotCached", "getNodesGivenRefCandidates: referred chain not cached");
                Pair<List<AccessibilityNodeInfoRecord>, Utility.BidirectionalListMap<NodeSelector, AccessibilityNodeInfoRecord>> resFromNewRef = referred.getNodesGivenRefCandidates(root, prevRes);
                inputs.addAll(resFromNewRef.first);
                newRef.merge(resFromNewRef.second);
                // 不对cache进行更新
            } else {
                inputs.addAll(prevRes.getT2List(referred));
            }


            startSelectionIndex = 1;
        } else if(selectors.get(0) instanceof OperationSelector){
            OperationSelector operationSelector = (OperationSelector) selectors.get(0);
            SelectorChain sc1 = operationSelector.sc1;
            SelectorChain sc2 = operationSelector.sc2;

            Set<AccessibilityNodeInfoRecord> set1 = new LinkedHashSet<>();
            Set<AccessibilityNodeInfoRecord> set2 = new LinkedHashSet<>();
            if(!prevRes.hasT1(sc1)){
                Pair<List<AccessibilityNodeInfoRecord>, Utility.BidirectionalListMap<NodeSelector, AccessibilityNodeInfoRecord>> resFromNewRef = sc1.getNodesGivenRefCandidates(root, prevRes);
                set1.addAll(resFromNewRef.first);
                newRef.merge(resFromNewRef.second);
            } else {
                set1.addAll(prevRes.getT2List(sc1));
            }

            if(!prevRes.hasT1(sc2)){
                Pair<List<AccessibilityNodeInfoRecord>, Utility.BidirectionalListMap<NodeSelector, AccessibilityNodeInfoRecord>> resFromNewRef = sc2.getNodesGivenRefCandidates(root, prevRes);
                set2.addAll(resFromNewRef.first);
                newRef.merge(resFromNewRef.second);
            } else {
                set2.addAll(prevRes.getT2List(sc2));
            }

            OperationSelector.Operator operation = operationSelector.operation;

            if(operation == OperationSelector.Operator.INTERSECTION){
                set1.retainAll(set2);
                inputs.addAll(set1);
            } else if(operation == OperationSelector.Operator.UNION){
                set1.addAll(set2);
                inputs.addAll(set1);
            } else if(operation == OperationSelector.Operator.DIFF){
                set1.removeAll(set2);
                inputs.addAll(set1);
            } else if(operation == OperationSelector.Operator.XOR){
                Set<AccessibilityNodeInfoRecord> set3 = new LinkedHashSet<>(set1);
                set3.addAll(set2);
                set1.retainAll(set2);
                set3.removeAll(set1);
                inputs.addAll(set3);
            }
            startSelectionIndex = 1;

        }
        if(selectors.size() > 1 && selectors.get(0) instanceof SelectorChainRefSelector && selectors.get(1) instanceof SelectorChainRefSelectorForDiff){
            if(inputs.size() != 1){
                Log.w("NoOrMultiInput", "size for diff = " + inputs.size());
            } else {
                AccessibilityNodeInfoRecord referredRes = inputs.get(0);
                SelectorChainRefSelectorForDiff second = (SelectorChainRefSelectorForDiff) selectors.get(1);
                List<AccessibilityNodeInfoRecord> nodesForDiff =  Collections.emptyList();
                if(second.refChain != null){
                    nodesForDiff = second.refChain.getNode(root, Collections.singletonList(root));
                }
                int index = nodesForDiff.indexOf(referredRes);
                if(index == -1){
                    Log.w("notFound", "referredNode");
                }
                int nextIndex = index + 1;
                if(nextIndex < nodesForDiff.size()){
                    AccessibilityNodeInfoRecord next = nodesForDiff.get(nextIndex);
                    List<AccessibilityNodeInfoRecord> finalRes1 = getNode(startSelectionIndex, root, inputs);
                    List<AccessibilityNodeInfoRecord> finalRes2 = getNode(startSelectionIndex, root, Collections.singletonList(next));
                    LinkedHashSet<AccessibilityNodeInfoRecord> set1 = new LinkedHashSet<>(finalRes1);
                    LinkedHashSet<AccessibilityNodeInfoRecord> set2 = new LinkedHashSet<>(finalRes2);
                    set1.removeAll(set2);
                    List<AccessibilityNodeInfoRecord> finalRes = new ArrayList<>(set1);
                    newRef.put(this, finalRes);
                    return new Pair<>(finalRes, newRef);
                }
            }
        }

        List<AccessibilityNodeInfoRecord> finalRes = getNode(startSelectionIndex, root, inputs); // 不在这个地方加入
        newRef.put(this, finalRes);
        return new Pair<>(finalRes, newRef);
    }


    private List<AccessibilityNodeInfoRecord> getNode(int startSelectorIndex, AccessibilityNodeInfoRecord root, List<AccessibilityNodeInfoRecord> inputs) {
        List<AccessibilityNodeInfoRecord> crtInput = new ArrayList<>(inputs);
        for(int i = startSelectorIndex; i < selectors.size(); ++ i){
            NodeSelector subSelector = selectors.get(i);
            crtInput = subSelector.getNode(root, crtInput);
        }
        Utility.assertTrue(new HashSet<>(crtInput).size() == crtInput.size());
        // 对结果进行排序
        if(sortType.getComparator() != null)
            Collections.sort(crtInput, sortType.getComparator());

        if(crtInput.size() == 0){
            return new ArrayList<>();
        }
        int actualStartIndex = startIndex >= 0? startIndex: (startIndex + crtInput.size()) % (crtInput.size());
        int actualEndIndex = (endIndex == null || endIndex >= crtInput.size())? crtInput.size(): (endIndex + crtInput.size()) % crtInput.size();

        if(actualStartIndex < 0 || actualEndIndex > crtInput.size() || actualStartIndex > actualEndIndex){
            return new ArrayList<>();
        }

        return crtInput.subList(actualStartIndex, actualEndIndex);
    }

    @Override
    public List<AccessibilityNodeInfoRecord> getNode(AccessibilityNodeInfoRecord root, List<AccessibilityNodeInfoRecord> inputs) {
        //  在必要的时候直接调用这个函数
        return getNode(0, root, inputs);
    }
}
