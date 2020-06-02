package pcg.hcit_service;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static android.content.Context.WINDOW_SERVICE;

/**
 * Class {@code Utility} provides several useful methods and classes.
 */
public class Utility {
    /**
     * throws {@link AssertionError} if cond is false
     * @param cond the condition to test
     */
    public static void assertTrue(boolean cond){
        if(BuildConfig.DEBUG && !cond){
            throw new AssertionError();
        }
    }

    public static float screenWidthPixel;
    public static float screenHeightPixel;
    public static float screenWidthMM;
    public static float screenHeightMM;
    public static float xdpi;
    public static float ydpi;
    public static float statusBarHeightPixel;
    public static float statusBarHeightMM;
    public static float navBarHeightPixel;
    public static float navBarHeightMM;

    public static void init(Context s){
        Point p = new Point();
        ((WindowManager) s.getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealSize(p);
        screenHeightPixel = p.y;
        screenWidthPixel = p.x;
        DisplayMetrics dm = s.getResources().getDisplayMetrics();
        xdpi = dm.xdpi;
        ydpi = dm.ydpi;
        navBarHeightPixel = Utility.getNavBarHeight(s);
        navBarHeightMM = convertInchIntoMM(navBarHeightPixel / ydpi);

        // screenHeightPixel -= (navBarHeightPixel * (isNavigationBarShow(context)? 1:0));

        screenWidthMM = convertInchIntoMM(screenWidthPixel / xdpi);
        screenHeightMM = convertInchIntoMM(screenHeightPixel / ydpi);

        statusBarHeightPixel = Utility.getStatusBarHeight(s);
        statusBarHeightMM = convertInchIntoMM(statusBarHeightPixel / ydpi);
    }

    private static int getNavBarHeight(Context context){
        int res = 0;
        int resourceId = context.getResources().getIdentifier("navigation_bar_height","dimen", "android");
        if(resourceId > 0){
            res = context.getResources().getDimensionPixelSize(resourceId);
        }
        return res;
    }

    private static float convertInchIntoMM(float inch){
        return inch / 0.03937008f;
    }

    private static int getStatusBarHeight(Context context) {
        int result = 0;
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = context.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    /**
     * Whether the app is in debug mode
     * @return true if the app is in debug mode
     */
    public static boolean isDebug(){
        return BuildConfig.DEBUG;
    }

    /**
     * Compare the contents of two char sequence
     * @param s1 first char sequence
     * @param s2 second char sequence
     * @return true if the contents of two char sequence are same
     */
    public static boolean charSequenceEqual(CharSequence s1, CharSequence s2){
        if(s1 == s2){
            return true;
        }

        if(s1 == null || s2 == null){
            return false;
        }

        return Objects.equals(s1.toString(), s2.toString());
    }

    /**
     * interface to help you compare whether two elements are equal
     * @param <T1>
     * @param <T2>
     */
    public interface cmp<T1, T2> {
        /**
         *
         * @param a first object
         * @param b second object
         * @return true if two elements are equal
         */
        public boolean isEqual(T1 a, T2 b);
    }

    /**
     * interface to help you judge whether an object satisfies a given condition
     * @param <T>
     */
    public interface cond<T> {
        /**
         *
         * @param a object to be judged whether satisfies a given condition
         * @return true if satisfies a given condition
         */
        public boolean satisfy(T a);
    }

    /**
     * find the longest common sequence of two list
     * @param list1 first list
     * @param list2 second list
     * @param t1T2cmp comparator to determine whether two objects are equal
     * @param <T1> type of the elements in the first list
     * @param <T2> type of the elements in the second list. T1 and T2 can be different.
     * @return List of integer tuple. Every tuple is corresponding to a element in the longest common sequence. The first integer in the tuple is the index of the elements in the first list, and the second one is the index in the second list.
     */
    public static <T1, T2> List<Pair<Integer, Integer>> LCS(List<T1> list1, List<T2> list2, cmp<T1, T2> t1T2cmp){
        int length1 = list1.size();
        int length2 = list2.size();
        if(length1 == 0 || length2 == 0){
            return new ArrayList<>();
        }
        int [][] dp = new int[length1][length2];
        List<List<List<Pair<Integer, Integer>>>> record = new ArrayList<>();
        for(int i = 0; i < length1; ++ i){
            List<List<Pair<Integer, Integer>>> sub = new ArrayList<>();
            for(int j = 0; j < length2; ++ j){
                sub.add(new ArrayList<Pair<Integer, Integer>>());
            }
            record.add(sub);
        }

        for(int index1 = 0; index1 < length1; ++ index1){
            T1 element1 = list1.get(index1);
            for(int index2 = 0; index2 < length2; ++ index2){
                T2 element2 = list2.get(index2);
                if(t1T2cmp.isEqual(element1, element2)){
                    if(index1 > 0 && index2 > 0){
                        dp[index1][index2] = dp[index1 - 1][index2 - 1] + 1;
                        record.get(index1).get(index2).addAll(record.get(index1 - 1).get(index2 - 1));
                        record.get(index1).get(index2).add(new Pair<>(index1, index2));
                    } else {
                        dp[index1][index2] = 1;
                        record.get(index1).get(index2).add(new Pair<>(index1, index2));
                    }
                } else {
                    if(index1 != 0 && index2 != 0){
                        if(dp[index1 - 1][index2] > dp[index1][index2 - 1]){
                            dp[index1][index2] = dp[index1 - 1][index2];
                            record.get(index1).get(index2).addAll(record.get(index1 - 1).get(index2));
                        } else {
                            dp[index1][index2] = dp[index1][index2 - 1];
                            record.get(index1).get(index2).addAll(record.get(index1).get(index2 - 1));
                        }
                    } else if(index1 != 0){
                        dp[index1][index2] = dp[index1 - 1][index2];
                        record.get(index1).get(index2).addAll(record.get(index1 - 1).get(index2));
                    } else if(index2 != 0){
                        dp[index1][index2] = dp[index1][index2 - 1];
                        record.get(index1).get(index2).addAll(record.get(index1).get(index2 - 1));
                    }
                }
            }
        }

        return new ArrayList<>(record.get(length1 - 1).get(length2 - 1));
    }

    /**
     * An class helps you visit all the nodes in a UI tree
     */
    public static abstract class Visitor {
        /**
         * actions you want to do when encounter a node
         * @param record the node being visited
         */
        public abstract void visitNode(AccessibilityNodeInfoRecord record);

        /**
         * visit the given tree
         * @param root root of the UI tree (can be a sub tree of the whole)
         * @param v a visitor
         */
        public static void visit(AccessibilityNodeInfoRecord root, Visitor v){
            if(root == null || v == null){
                return;
            }
            v.visitNode(root);
            for(AccessibilityNodeInfoRecord child: root.getChildren()){
                visit(child, v);
            }
        }
    }

    /**
     * A bi-direction map mapping  {@code T1} to {@code List<T2} and {@code T2} to {@code List<T1}
     * The bi-direction map actually contains two traditional maps. The contents of the two maps are consistent.
     *
     * @param <T1> first type
     * @param <T2> second type
     */
    public static class BidirectionalListMap<T1, T2> {
        private Map<T1, LinkedHashSet<T2>> t1ToT2List;
        private Map<T2, LinkedHashSet<T1>> t2ToT1List;
        /**
         * Works you add add an key-value pair to the map and the key already exist.
         * if append is true, the value (which is a list) will be merged with the original list and then keep the data consistency.
         * Otherwise, the value will replace the original list and then keep the data consistency accordingly.
         */
        boolean append;

        /**
         * default {@code append == true}
         */
        public BidirectionalListMap(){
            t1ToT2List = new HashMap<>();
            t2ToT1List = new HashMap<>();
            append = true;
        }

        /**
         * get append value
         * @return append
         */
        public boolean getAppend(){
            return append;
        }

        /**
         * construct using a given {@code append}
         * @param append the value to set to {@code append}
         */
        public BidirectionalListMap(boolean append){
            t1ToT2List = new HashMap<>();
            t2ToT1List = new HashMap<>();
            this.append = append;
        }

        /**
         * copy from another map. Default constructor will be invoked if {@code map == null}
         * @param map another map
         */
        public BidirectionalListMap(BidirectionalListMap<T1, T2> map){
            if(map != null){
                t1ToT2List = new HashMap<>(map.t1ToT2List);
                t2ToT1List = new HashMap<>(map.t2ToT1List);
                append = map.append;
            } else {
                t1ToT2List = new HashMap<>();
                t2ToT1List = new HashMap<>();
                append = true;
            }
        }

        /**
         * add a key-value pair to the list according to the value of {@code append}.
         * Both {@code t1ToT2List} and {@code t2ToT1List} will be changed
         *
         * @param t1Key key
         * @param t2Value value
         */
        public void put(T1 t1Key, Collection<T2> t2Value){
            if(!t1ToT2List.containsKey(t1Key)){
                t1ToT2List.put(t1Key, new LinkedHashSet<T2>());
            }

            if(append){
                t1ToT2List.get(t1Key).addAll(t2Value);
                for(T2 t2: t2Value){
                    if(!t2ToT1List.containsKey(t2)){
                        t2ToT1List.put(t2, new LinkedHashSet<T1>());
                    }

                    t2ToT1List.get(t2).add(t1Key);
                }
            } else {
                LinkedHashSet<T2> oriRes = t1ToT2List.get(t1Key);
                t1ToT2List.put(t1Key, new LinkedHashSet<>(t2Value));

                // 对 oriRes 进行删除
                for(T2 t2: oriRes){
                    t2ToT1List.get(t2).remove(t1Key); // 之前能够由 t1Key 生成的 已经不能再由 t1Key 生成
                    if(t2ToT1List.get(t2).isEmpty()){
                        t2ToT1List.remove(t2);
                    }
                }

                for(T2 t2: t2Value){
                    if(!t2ToT1List.containsKey(t2)){
                        t2ToT1List.put(t2, new LinkedHashSet<T1>());
                    }

                    t2ToT1List.get(t2).add(t1Key);
                }
            }

            checkConsistence();
        }

        /**
         * merge another map according to the value of {@code append}
         * @param other another map
         */
        public void merge(BidirectionalListMap<T1, T2> other){
            for(T1 t1Key: other.t1ToT2List.keySet()){
                put(t1Key, other.getT2List(t1Key));
            }
        }

        /**
         * Similar to {@link BidirectionalListMap#put(Object, Collection)}, but the type of key if {@code T2}
         * Both {@code t1ToT2List} and {@code t2ToT1List} will be changed
         *
         * @param t2Key key
         * @param t1Value value
         */
        public void putReverse(T2 t2Key, Collection<T1> t1Value){
            if(!t2ToT1List.containsKey(t2Key)){
                t2ToT1List.put(t2Key, new LinkedHashSet<T1>());
            }

            if (append){
                t2ToT1List.get(t2Key).addAll(t1Value);
                for(T1 t1: t1Value){
                    if(!t1ToT2List.containsKey(t1)){
                        t1ToT2List.put(t1, new LinkedHashSet<T2>());
                    }

                    t1ToT2List.get(t1).add(t2Key);
                }
            } else {
                LinkedHashSet<T1> oriRes = t2ToT1List.get(t2Key);
                t2ToT1List.put(t2Key, new LinkedHashSet<>(t1Value));

                // 对 oriRes 进行处理
                for(T1 t1: oriRes){
                    t1ToT2List.get(t1).remove(t2Key);
                    if(t1ToT2List.get(t1).isEmpty()){
                        t1ToT2List.remove(t1);
                    }
                }

                for(T1 t1: t1Value){
                    if(!t1ToT2List.containsKey(t1)){
                        t1ToT2List.put(t1, new LinkedHashSet<T2>());
                    }

                    t1ToT2List.get(t1).add(t2Key);
                }
            }

            checkConsistence();
        }

        /**
         * get List of T1 from a T2 key
         * Node {@code LinkedHashSet} keeps order as the order of node added
         * @param key T2 key
         * @return List (actually LinkedHashSet) of T1
         */
        public LinkedHashSet<T1> getT1List(T2 key){
            if(!t2ToT1List.containsKey(key))
                return new LinkedHashSet<>();
            return t2ToT1List.get(key);
        }

        /**
         * similar to {@link BidirectionalListMap#getT1List(Object)}, but different type of key
         *
         * @param key T1 key
         * @return List (actually LinkedHashSet) of T2
         */
        public LinkedHashSet<T2> getT2List(T1 key){
            if(!t1ToT2List.containsKey(key))
                return new LinkedHashSet<>();
            return t1ToT2List.get(key);
        }

        /**
         * Whether the map contains t1 as key
         * @param t1 the key to be checked
         * @return true if he map contains t1 as key
         */
        public boolean hasT1(T1 t1){
            return t1ToT2List.containsKey(t1);
        }

        /**
         * Similar to {@link BidirectionalListMap#hasT1(Object)}
         * @param t2 the key to be checked
         * @return true if he map contains t2 as key
         */
        public boolean hasT2(T2 t2){
            return t2ToT1List.containsKey(t2);
        }

        /**
         *
         * @return number of T1 keys
         */
        public int t1Size(){
            return t1ToT2List.size();
        }

        /**
         *
         * @return number of T2 keys
         */
        public int t2Size(){
            return t2ToT1List.size();
        }

        /**
         * clear the map
         */
        public void clear(){
            t1ToT2List.clear();
            t2ToT1List.clear();
        }

        /**
         *
         * check whether the data is consistent.
         *
         *
         * @return true if the data is consistent.
         */
        private boolean checkConsistence(){
            if(!Utility.isDebug()){
                return true;
            }

            // todo not finished!
            return true;
        }

        /**
         *
         * @return all T1 key
         */
        public Set<T1> getT1Key(){
            return t1ToT2List.keySet();
        }

        /**
         *
         * @return all T2 key
         */
        public Set<T2> getT2Key(){
            return t2ToT1List.keySet();
        }

        /**
         * Build a BidirectionalListMapOverwrite from current map
         * The original map will not be changed.
         * @return a new created map
         */
        public BidirectionalListMapOverwrite<T1, T2> toOverwrite(){
            append = false;
            BidirectionalListMapOverwrite<T1, T2> res = new BidirectionalListMapOverwrite<>();
            ((BidirectionalListMap<T1, T2>)res).t1ToT2List = this.t1ToT2List;
            ((BidirectionalListMap<T1, T2>)res).t2ToT1List = this.t2ToT1List;

            return res;
        }
    }

    /**
     * BidirectionalListMap satisfies {@code append == false}
     *
     * @param <T1> first type
     * @param <T2> second type
     */
    public static class BidirectionalListMapOverwrite<T1, T2> extends BidirectionalListMap<T1, T2>{
        public BidirectionalListMapOverwrite(){
            super(false);
        }


        /**
         * build from existing BidirectionalListMap
         *
         * @param other other BidirectionalListMap
         */
        public BidirectionalListMapOverwrite(BidirectionalListMap<T1, T2> other){
            super(other);
            append = false;
        }
    }

    /**
     * Show toast in any thread.
     * Safe to use in non-UI thread
     *
     * @param context context. An activity or a service
     * @param msg text to display
     * @param dur last duration of the toast. {@code Toast.LENGTH_LONG} or {@code Toast.LENGTH_SHORT}
     */
    public static void toast(final Context context, final String msg, final int dur){
        android.os.Handler handler = new Handler(context.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, msg, dur).show();
            }
        });
    }

    /**
     * Print the UI tree. Mainly used for debug
     *
     * @param root the root node to print
     */
    public static String printTree(AccessibilityNodeInfo root){
        StringBuffer res = new StringBuffer();
        printNodeStructure(root, 0, res);
        return res.toString();
    }

    /**
     * Print the UI tree. Mainly used for debug
     *
     * @param root the root node to print
     * @param indent string to indicate there is an indent
     * @param newLine string to indicate end of a line
     */
    public static String printTree(AccessibilityNodeInfoRecord root, String indent, String newLine){
        StringBuffer res = new StringBuffer();
        printNodeStructure(root, 0, res);
        return res.toString();
    }

    /**
     * Print the UI tree. Mainly used for debug
     *
     * @param root the root node to print
     */
    public static String printTree(AccessibilityNodeInfoRecord root){
        return printTree(root, "\t", "\n");
    }

    /**
     * Print the UI tree. Mainly used for debug
     *
     * @param root the root node to print
     * @param depth depth of the given root
     * @param res a string buffer to store the print  result
     */
    public static void printNodeStructure(AccessibilityNodeInfo root, int depth, StringBuffer res){
        if(root == null){
            return;
        }
        //root.refresh();
        Rect border = new Rect();
        root.getBoundsInScreen(border);
        for(int i = 0; i < depth; i ++){
            res.append("\t");
        }

        res.append(root.hashCode()).append(" ")
                .append(root.getClassName()).append(" ").append(root.getPackageName()).append(" ")
                .append(root.getViewIdResourceName()).append(" ")
                .append(border.toString()).append(" ")
                .append(root.getText()).append(" ")
                .append(root.getContentDescription()).append(" ")
                .append("isClickable: ").append(root.isClickable()).append(" ")
                .append("isScrollable: ").append(root.isScrollable()).append(" ")
                .append("isVisible: ").append(root.isVisibleToUser()).append(" ")
                .append("isEnabled: ").append(root.isEnabled()).append(" ").append("\n");

        //res.append(root.toString()).append("\n");
        for(int i = 0; i < root.getChildCount(); ++ i){
            printNodeStructure(root.getChild(i), depth + 1, res);
        }

        root.recycle();
    }

    /**
     * Print the UI tree. Mainly used for debug
     *
     * @param root the root node to print
     * @param depth depth of the given root
     * @param res a string buffer to store the print  result
     */
    public static void printNodeStructure(AccessibilityNodeInfoRecord root, int depth, StringBuffer res){
        printNodeStructure(root, depth, res, "\t", "\n");
    }

    /**
     * Print the UI tree. Mainly used for debug
     *
     * @param root the root node to print
     * @param depth depth of the given root
     * @param res a string buffer to store the print  result
     * @param indent string to indicate there is an indent
     * @param newLine string to indicate end of a line
     */
    public static void printNodeStructure(AccessibilityNodeInfoRecord root, int depth, StringBuffer res, String indent, String newLine){
        if(root == null){
            return;
        }
        Rect border = new Rect();
        root.getBoundsInScreen(border);
        for(int i = 0; i < depth; i ++){
            res.append(indent);
        }

        res.append(root.getState()).append(' ').append(root.nodeInfoHash).append(" ").append(root.getPackageName()).append(" ")
                .append(root.getClassName()).append(" ")
                .append(root.getViewIdResourceName()).append(" ")
                .append(border.toString()).append(" ")
                .append(root.getText()).append(" ")
                .append(root.getContentDescription()).append(" ")
                .append("isClickable: ").append(root.isClickable()).append(" ")
                .append("isScrollable: ").append(root.isScrollable()).append(" ")
                .append("isVisible: ").append(root.isVisibleToUser()).append(" ")
                .append("isEnabled: ").append(root.isEnabled()).append(" ").append(newLine);

        //res.append(root.toString()).append("\n");
        for(int i = 0; i < root.getChildCount(); ++ i){
            printNodeStructure(root.getChild(i), depth + 1, res, indent, newLine);
        }
    }

    /**
     * Construct a new list from the given list. Any nodes contained in the new list will not be a successor of any other nodes.
     *
     * This method does not change the original list and will return a new list.
     *
     * @param nodes the given list, in which one element may be a successor of another
     * @return a new list. Any nodes contained in the new list will not be a successor of any other nodes.
     */
    public static List<AccessibilityNodeInfo> deleteNodeAsSuccessor(List<AccessibilityNodeInfo> nodes){
        List<AccessibilityNodeInfo> res = new ArrayList<>();
        if(nodes.isEmpty()){
            return res;
        }
        res.add(nodes.get(0));
        for(int i = 1; i < nodes.size(); ++ i){
            AccessibilityNodeInfo crtNode = nodes.get(i);
            boolean notAdd = false;
            for(int j = 0; j < res.size(); ++ j){
                if(isAncestorOf(res.get(j), crtNode)){
                    notAdd = true;
                    break;
                } else if(isAncestorOf(crtNode, res.get(j))){
                    res.set(j, null);
                }
            }
            if(!notAdd){
                res.add(crtNode);
            }
        }
        return res;
    }

    /**
     * Only keep the elements that satisfy a given condition, and other elements will be deleted.
     * The method will change the original collection.
     *
     * @param l an collection instance
     * @param d the condition needs to be satisfied
     * @param <T> the type of the collection, whose elements' type is E
     * @param <E> the type of the elements
     */
    public static <T extends Collection<E>, E> void filter(T l, cond<E> d){
        E[] all = (E[]) l.toArray();
        l.clear();
        for(E e: all){
            if(d.satisfy(e)){
                l.add(e);
            }
        }
    }

    /**
     * Test Whether Node a is an ancestor of Node b.
     *
     * If a is an ancestor of b, b is in a tree whose roo is b.
     *
     * @param a one node which may be the ancestor
     * @param b another node which may be the successor
     * @return true if a is an ancestor of b
     */
    public static boolean isAncestorOf(AccessibilityNodeInfo a, AccessibilityNodeInfo b){
        if(a == null || b == null){
            return false;
        }

        AccessibilityNodeInfo crtNode = AccessibilityNodeInfo.obtain(b);
        while (crtNode != null){
            if(crtNode.equals(a)){
                crtNode.recycle();
                return true;
            } else {
                AccessibilityNodeInfo tmp = crtNode.getParent();
                crtNode.recycle();
                crtNode = tmp;
            }
        }
        return false;
    }

    /**
     * This methods can convert an object to the given type.
     *
     * This is designed for {@code JSONObject}.
     * When we call {@code JSONObject.get(String)} (or other similar methods), and if the value corresponding to that key is {@code null}, the method returns {@code JSONObject.NULL} instead of {@code null}.
     * {@code JSONObject.NULL == null} returns {@code false} and expressions like {@code (Integer) JSONObject.NULL} throws exception.
     * Use this function, and you can convert the result from a {@code JSONObject} safely.
     *
     * @param a the object to be converted
     * @param <T> the type you want to convert to
     * @return the result
     */
    public static <T> T convert(Object a){
        if(a == null || Objects.equals(a, null)){
            return null;
        }
        return (T) a;
    }

    public static int getWindowIdByEvent(AccessibilityEvent event){
        if(event.getWindowId() != -1){
            return event.getWindowId();
        }
        AccessibilityNodeInfo node = event.getSource();
        if (node == null) {
            return -1;
        }

        int windowId = node.getWindowId();
        node.recycle();
        return windowId;
    }
}

