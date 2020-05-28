package pcg.hcit_service;

import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;

/**
 * records of {@link AccessibilityNodeInfo}
 */
public class AccessibilityNodeInfoRecord {
    public static final int NOT_CHANGED =           0b00000000000000000000000000000000;
    public static final int SELF_TEXT_CHANGED =     0b00000000000000000000000000000001;
    public static final int SELF_CONTENT_CHANGED =  0b00000000000000000000000000000010;
    public static final int DESC_TEXT_CHANGED =     0b00000000000000000000000000000100;
    public static final int DESC_CONTENT_CHANGED =  0b00000000000000000000000000001000;
    public static final int DESC_ADDED =            0b00000000000000000000000000010000;
    public static final int DESC_DELETED =          0b00000000000000000000000000100000;
    public static final int SELF_NEW_CREATED =      0b00000000000000000000000001000000;

    public static final String CHANGE_TEXT_CHANGED = "TEXT_CHANGED";
    public static final String CHANGE_CONTENT_CHANGED = "CONTENT_CHANGED";
    public static final String CHANGE_NEW_CREATED = "NEW_CREATED";
    public static final String CHANGE_DELETED = "DELETED";

    private static Map<String, List<AccessibilityNodeInfoRecord>> nodeChangeStateToNodeList = new HashMap<>();

    /**
     * root of current ui tree
     */
    public static AccessibilityNodeInfoRecord root = null;
    private static Map<String, AccessibilityNodeInfoRecord> idToRecord = new HashMap<>();
    private static Map<Integer, String> nodeInfoHashToId = new HashMap<>();

    /**
     * get an {@link AccessibilityNodeInfoRecord} from an {@link AccessibilityNodeInfo}
     * @param nodeInfo given node info
     * @return record of given node info. null if the node info does not appear in the ui tree.
     */
    public static AccessibilityNodeInfoRecord getRecordFromNode(AccessibilityNodeInfo nodeInfo){
        if(nodeInfo == null){
            return null;
        }
        String pathId = nodeInfoHashToId.get(nodeInfo.hashCode());
        if(pathId == null){
            return null;
        }
        return idToRecord.get(pathId);
    }

    private static SparseArray<String> _eventTypeToString;
    static {
        _eventTypeToString = new SparseArray<>();
        _eventTypeToString.put(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, "无障碍Focus");
        _eventTypeToString.put(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, "清除无障碍Focus");
        _eventTypeToString.put(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS, "清除Focus");
        _eventTypeToString.put(AccessibilityNodeInfo.ACTION_CLEAR_SELECTION, "清除选择");
        _eventTypeToString.put(AccessibilityNodeInfo.ACTION_CLICK, "点击");
        _eventTypeToString.put(AccessibilityNodeInfo.ACTION_COLLAPSE, "折叠");
        _eventTypeToString.put(AccessibilityNodeInfo.ACTION_COPY, "拷贝");
        _eventTypeToString.put(AccessibilityNodeInfo.ACTION_CUT, "剪切");
        _eventTypeToString.put(AccessibilityNodeInfo.ACTION_DISMISS, "关闭");
        _eventTypeToString.put(AccessibilityNodeInfo.ACTION_EXPAND, "展开");
        _eventTypeToString.put(AccessibilityNodeInfo.ACTION_FOCUS, "Focus");
        _eventTypeToString.put(AccessibilityNodeInfo.ACTION_LONG_CLICK, "长按");
        _eventTypeToString.put(AccessibilityNodeInfo.ACTION_PASTE, "粘贴");
        _eventTypeToString.put(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, "向后滚动");
        _eventTypeToString.put(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, "向前滚动");
        _eventTypeToString.put(AccessibilityNodeInfo.ACTION_SELECT, "设置文本");

        clearNodeChangeStateToNodeList();
    }

    /**
     * map type id to a readable string. Mainly for debug.
     * @param type unique id for a action type.
     * @return nature language representing the event type
     */
    public static String eventTypeToString(int type){
        return _eventTypeToString.get(type, Integer.toHexString(type));
    }

    /**
     * rebuild the tree. It will be called in {@link RefreshThread} and you may not need to invoke it manually.
     * The original tree will be cleaned. <strong>Copy</strong> any data before calling this function if necessary.
     */
    public static void buildTree(){
        AccessibilityNodeInfoRecord oriRoot = AccessibilityNodeInfoRecord.root;
        clearTree();
        HCITService server = HCITService.getInstance();

        AccessibilityNodeInfo root = server.getRootInActiveWindow();
        AccessibilityNodeInfoRecord.root = new AccessibilityNodeInfoRecord(root, null, 0);
        AccessibilityNodeInfoRecord.root.ignoreUselessChild(false);
        removeInvisibleChildrenInList(AccessibilityNodeInfoRecord.root);
        AccessibilityNodeInfoRecord.root.refreshIndexAndDepth(0, 0);
        AccessibilityNodeInfoRecord.root.refreshAbsoluteId();
        clearNodeChangeStateToNodeList();
        nodeChangeStateToNodeList.get(CHANGE_NEW_CREATED).add(AccessibilityNodeInfoRecord.root);

        if(oriRoot != null){
            nodeChangeStateToNodeList.get(CHANGE_DELETED).add(oriRoot);
        }
    }

    private static void removeInvisibleChildrenInList(AccessibilityNodeInfoRecord crtNode){
        if(crtNode.isScrollable() ||
                (crtNode.getClassName() != null &&
                        (crtNode.getClassName().toString().contains("RecyclerView") || crtNode.getClassName().toString().contains("GridView")))){
            for(int i = crtNode.getChildCount() - 1; i >= 0; -- i){
                Rect r = new Rect();
                crtNode.getChild(i).getBoundsInScreen(r);
                if(r.height() <= 0 || r.width() <= 0){
                    if(crtNode.getChild(i).absoluteId != null && idToRecord.containsKey(crtNode.getChild(i).absoluteId)){
                        idToRecord.remove(crtNode.getChild(i).absoluteId);
                        nodeInfoHashToId.remove(crtNode.getChild(i).nodeInfo.hashCode());
                    }
                    if(crtNode.getChild(i).canStateMatch(SELF_NEW_CREATED)){
                        nodeChangeStateToNodeList.get(CHANGE_NEW_CREATED).remove(crtNode.getChild(i));
                    } else {
                        nodeChangeStateToNodeList.get(CHANGE_DELETED).add(crtNode.getChild(i));
                    }
                    clearSubTree(crtNode.getChild(i));
                    crtNode.children.remove(i);
                }
            }
        }
        for(AccessibilityNodeInfoRecord child: crtNode.children){
            removeInvisibleChildrenInList(child);
        }

    }

    /**
     * clean the tree
     */
    public static void clearTree(){
        idToRecord.clear();
        nodeInfoHashToId.clear();
        clearSubTree(root);
        root = null;
    }

    private static void clearSubTree(AccessibilityNodeInfoRecord record){
        if(record == null){
            return;
        }

        idToRecord.remove(record.absoluteId);
        nodeInfoHashToId.remove(record.nodeInfoHash);
        for(AccessibilityNodeInfoRecord child: record.children){
            clearSubTree(child);
        }
        if(record.nodeInfo != null) {
            record.nodeInfo.recycle();
            record.nodeInfo = null;
        }
        // record.children.clear();
        // record.parent = null;
        record.cleaned = true;
    }

    private static void cleanSubTreeWithoutRoot(AccessibilityNodeInfoRecord record){
        if(record == null){
            return;
        }

        for(AccessibilityNodeInfoRecord child: record.children){
            clearSubTree(child);
        }
        record.children.clear();
    }

    private static void markAncestorRefreshed(AccessibilityNodeInfoRecord record, int newState){
        AccessibilityNodeInfoRecord crt = record.getParent();
        while (crt != null && ((crt.state & newState) == 0)){
            crt.state |= newState;
            crt = crt.parent;
        }
    }

    private static boolean compareAndRebuildTree(AccessibilityNodeInfoRecord changedRecord, AccessibilityNodeInfo newNodeInfo){
        if(!changedRecord.nodeInfo.refresh()){
            return false;
        }

        if(changedRecord.nodeInfo.hashCode() != changedRecord.nodeInfoHash){
            nodeInfoHashToId.remove(changedRecord.nodeInfoHash);
            changedRecord.nodeInfoHash = changedRecord.nodeInfo.hashCode();
            nodeInfoHashToId.put(changedRecord.nodeInfoHash, changedRecord.absoluteId);
        }

        if(!Utility.charSequenceEqual(changedRecord.textCached, changedRecord.nodeInfo.getText())){
            // markAncestorRefreshed(changedRecord, DESC_TEXT_CHANGED); 之后进行统一修改
            changedRecord.state |= SELF_TEXT_CHANGED;
            changedRecord.textCached = changedRecord.nodeInfo.getText();
            nodeChangeStateToNodeList.get(CHANGE_TEXT_CHANGED).add(changedRecord);
        }

        if(!Utility.charSequenceEqual(changedRecord.contentCached, changedRecord.nodeInfo.getContentDescription())){
            // markAncestorRefreshed(changedRecord, DESC_CONTENT_CHANGED);
            changedRecord.state |= SELF_CONTENT_CHANGED;
            changedRecord.contentCached = changedRecord.nodeInfo.getContentDescription();
            nodeChangeStateToNodeList.get(CHANGE_CONTENT_CHANGED).add(changedRecord);
        }

        List<AccessibilityNodeInfo> actualChildren = new ArrayList<>();
        for(int i = 0; i < changedRecord.nodeInfo.getChildCount(); ++ i){
            AccessibilityNodeInfo info = changedRecord.nodeInfo.getChild(i);
            if(info != null)
                actualChildren.add(info);
            else
                Log.i("NullChild", "compareAndRebuildTree: null child");
        }

        List<Pair<Integer, Integer>> lcsRes = Utility.LCS(actualChildren, changedRecord.children, new Utility.cmp<AccessibilityNodeInfo, AccessibilityNodeInfoRecord>() {
            @Override
            public boolean isEqual(AccessibilityNodeInfo a, AccessibilityNodeInfoRecord b) {
                if(b.nodeInfo == null/* || !b.nodeInfo.refresh()*/)
                    return false;
                return a.equals(b.nodeInfo);
            }
        });

        List<AccessibilityNodeInfoRecord> newChildList = new ArrayList<>();
        for(int i = 0; i < actualChildren.size(); ++ i){
            newChildList.add(null);
        }
        for(Pair<Integer, Integer> newToOld: lcsRes){
            int newIdx = newToOld.first;
            int oldIdx = newToOld.second;
            newChildList.set(newIdx, changedRecord.children.get(oldIdx));
            compareAndRebuildTree(changedRecord.children.get(oldIdx), actualChildren.get(newIdx));
            changedRecord.children.set(oldIdx, null);
        }

        for(AccessibilityNodeInfoRecord oldChild: changedRecord.children){
            if(oldChild != null){
                markAncestorRefreshed(changedRecord, DESC_DELETED);
                clearSubTree(oldChild);
                nodeChangeStateToNodeList.get(CHANGE_DELETED).add(oldChild);
            }
        }

        for(int i = 0; i < newChildList.size(); ++ i){
            AccessibilityNodeInfoRecord crt = newChildList.get(i);
            if(crt != null){
                continue;
            }

            // markAncestorRefreshed(changedRecord, DESC_ADDED);
            AccessibilityNodeInfoRecord newChild = new AccessibilityNodeInfoRecord(AccessibilityNodeInfo.obtain(actualChildren.get(i)), changedRecord, i);
            newChildList.set(i, newChild);
            nodeChangeStateToNodeList.get(CHANGE_NEW_CREATED).add(newChild);
        }

        changedRecord.children = newChildList; // 任何的删除请在后面完成
        for(AccessibilityNodeInfo info: actualChildren){
            info.recycle();
        }
        return true;
    }

    private static boolean rebuildSubTree(AccessibilityNodeInfoRecord changedRecord, AccessibilityNodeInfo oldNodeInfo){
        if(changedRecord == null || changedRecord.nodeInfo == null){
            return false;
        }

        compareAndRebuildTree(changedRecord, oldNodeInfo);
        // 这边最后整体进行一个clean
        changedRecord.ignoreUselessChild(false);
        removeInvisibleChildrenInList(changedRecord);
        changedRecord.refreshIndexAndDepth(changedRecord.index, changedRecord.depth); // 自己的index 和 depth是不变的
        changedRecord.refreshAbsoluteId();
        updateChangeInfo(changedRecord);
        return true;
    }

    private static void updateChangeInfo(AccessibilityNodeInfoRecord root){
        if(root.canStateMatch(SELF_NEW_CREATED)){
            markAncestorRefreshed(root, DESC_ADDED);
            return;
        }

        if(root.canStateMatch(SELF_TEXT_CHANGED)){
            markAncestorRefreshed(root, DESC_TEXT_CHANGED);
        }

        if(root.canStateMatch(SELF_CONTENT_CHANGED)){
            markAncestorRefreshed(root, DESC_CONTENT_CHANGED);
        }

        for(AccessibilityNodeInfoRecord c: root.getChildren()){
            updateChangeInfo(c);
        }

    }

    /**
     * Mark a node and all its ancestors as {@link AccessibilityNodeInfoRecord#NOT_CHANGED}.
     * It will be called in {@link RefreshThread} and you may not need to manually invoke the function.
     * @param node the node to set the new state
     */
    public static void markNodeAsNotRefreshed(AccessibilityNodeInfoRecord node){
        if(node == null){
            return;
        }
        node.state = NOT_CHANGED;
        for(AccessibilityNodeInfoRecord n: node.children){
            markNodeAsNotRefreshed(n);
        }

        clearNodeChangeStateToNodeList();
    }

    /**
     * Partially refresh the tree.
     * It will be called in {@link RefreshThread} and you may not need to manually invoke the function.
     * @param nodeChangedOri node that changed
     */
    public static void partiallyRefreshTree(AccessibilityNodeInfo nodeChangedOri){
        if(nodeChangedOri == null){
            return;
        }

        AccessibilityNodeInfo nodeChanged = AccessibilityNodeInfo.obtain(nodeChangedOri);

        boolean isPartiallyRefreshed = false;
        if(AccessibilityNodeInfoRecord.root != null){
            // markNodeAsNotRefreshed(AccessibilityNodeInfoRecord.root); // 在外面已经刷新过了
            // 检查节点是不是在现在的树中出现
            while (nodeChanged != null){
                int changedNodeHash = nodeChanged.hashCode();
                if(nodeInfoHashToId.containsKey(changedNodeHash)){
                    // 说明变化的节点实际上已经存在在原始的树上了
                    AccessibilityNodeInfoRecord changedRecord = idToRecord.get(nodeInfoHashToId.get(changedNodeHash));
                    isPartiallyRefreshed = rebuildSubTree(changedRecord, nodeChanged);
                }
                if(isPartiallyRefreshed){
                    break;
                } else {
                    AccessibilityNodeInfo nodeChangedParent = nodeChanged.getParent();
                    nodeChanged.recycle();
                    nodeChanged = nodeChangedParent;
                }
            }
        }
        if(nodeChanged != null){
            nodeChanged.recycle();
        }
        if(!isPartiallyRefreshed){
            // 局部更新失败的时候
            AccessibilityNodeInfoRecord.buildTree();
        }
    }

    /**
     * Gets whether this node is checked.
     *
     * @return True if the node is checked.
     */
    public boolean isChecked(){
        return nodeInfo.isChecked();
    }

    /**
     * Gets whether this node is enabled.
     *
     * @return True if the node is enabled.
     */
    public boolean isEnabled(){
        return nodeInfo.isEnabled();
    }

    /**
     * Gets whether this node is focused.
     *
     * @return True if the node is focused.
     */
    public boolean isFocused(){
        return nodeInfo.isFocused();
    }

    /**
     * Gets whether this node is a password.
     *
     * @return True if the node is a passward.
     */
    public boolean isPassword(){
        return nodeInfo.isPassword();
    }

    /**
     * Gets whether this node is accessibility focused.
     *
     * @return True if the node is accessibility focused.
     */
    public boolean isAccessibilityFocused(){
        return nodeInfo.isAccessibilityFocused();
    }

    private AccessibilityNodeInfo nodeInfo;
    private List<AccessibilityNodeInfoRecord> children;
    private AccessibilityNodeInfoRecord parent;
    public int index;
    public int depth;
    private String absoluteId;
    private boolean isImportant;
    private List<AccessibilityNodeInfoRecord> uselessChildren;

    private String allTexts;
    private String allContents;

    private String allValidText;
    private String allValidContent;
    private int state;
    int nodeInfoHash;

    private CharSequence textCached;
    private CharSequence contentCached;
    private CharSequence hintTextCached;

    private boolean _isClickable;
    private boolean _isScrollable;
    private boolean _isLongClickable;
    private boolean _isEditable;
    private boolean _isCheckable;
    private CharSequence _className;
    private Rect _boundInScreen;
    private boolean _isSelected;
    private CharSequence _packageName;
    private CharSequence _viewIdResourceName;  // nullable
    private boolean _isVisibleToUser;
    private boolean _isFocusable;
    private boolean _isDismissable;

    private boolean cleaned;

    private boolean interactAncestor;
    private boolean interactAncestorDirty;

    private boolean canStateMatch(int target){
        return state == target || (state & target) != 0;
    }

    /**
     * Get state of the node
     *
     * @return a string representing the status
     */
    public String getState(){
        return Integer.toBinaryString(state);
    }

    private AccessibilityNodeInfoRecord(AccessibilityNodeInfo nodeInfo, AccessibilityNodeInfoRecord parent, int index) {
        this.nodeInfo = nodeInfo;
        this.children = new ArrayList<>();
        this.uselessChildren = new ArrayList<>();
        this.parent = parent;
        this.index = index;
        if(nodeInfo != null) {
            nodeInfoHash = nodeInfo.hashCode();
            textCached = nodeInfo.getText();
            contentCached = nodeInfo.getContentDescription();

            _isClickable = nodeInfo.isClickable();
            _isScrollable = nodeInfo.isScrollable();
            _isLongClickable = nodeInfo.isLongClickable();
            _isEditable = nodeInfo.isEditable();
            _isCheckable = nodeInfo.isCheckable();
            _className = nodeInfo.getClassName();
            _boundInScreen = new Rect();
            nodeInfo.getBoundsInScreen(_boundInScreen);
            _isSelected = nodeInfo.isSelected();
            _packageName = nodeInfo.getPackageName();
            _viewIdResourceName = nodeInfo.getViewIdResourceName();
            _isVisibleToUser = nodeInfo.isVisibleToUser();
            _isFocusable =  nodeInfo.isFocusable();
            _isDismissable = nodeInfo.isDismissable();

            for (int i = 0; i < nodeInfo.getChildCount(); ++i) {
                AccessibilityNodeInfo crtNode = nodeInfo.getChild(i);
                if (crtNode == null) {
                    continue;
                }
                children.add(new AccessibilityNodeInfoRecord(crtNode, this, i));
            }
        }
        state = SELF_NEW_CREATED;
        cleaned = false;

        interactAncestorDirty = true;
    }

    /**
     * Gets whether this node has changed.
     *
     * @return True if the node has chenged.
     */
    public boolean isChanged(){
        return state != NOT_CHANGED;
    }

    private boolean ignoreUselessChild(boolean isForceUseless){
        /*if(getClassName() != null && getClassName().toString().contains("WebView")){
            // 删除所有的 webview
            children.clear();
            return true;
        }*/


        isImportant = false;
        //boolean isRefresh = getViewIdResourceName() != null && Objects.equals("uik_refresh_header", getViewIdResourceName().toString());
        boolean isRefresh = false;
        for(AccessibilityNodeInfoRecord child: children){
            if(child.ignoreUselessChild(isRefresh)){
                isImportant = true;
            }
        }

        if(!isImportant){
            isImportant = isClickable() || isCheckable() || isScrollable() || isEditable()
                    || isLongClickable() || (getText() != null && getText().length() > 0)
                    || (getContentDescription() != null && getContentDescription().length() > 0)
                    || (getViewIdResourceName() != null && getViewIdResourceName().length() > 0);
        }

        isImportant = isImportant && !isForceUseless && !isRefresh;
        // 把所有不重要的节点从 children 里转移到 uselessChild 里
        uselessChildren.clear();
        uselessChildren.addAll(children);
        for(AccessibilityNodeInfoRecord child: children){
            if(child.isImportant){
                uselessChildren.remove(child);
            }
        }
        for(AccessibilityNodeInfoRecord c: uselessChildren){
            if(c.canStateMatch(SELF_NEW_CREATED)){
                nodeChangeStateToNodeList.get(CHANGE_NEW_CREATED).remove(c);
            } else {
                nodeChangeStateToNodeList.get(CHANGE_DELETED).add(c);
            }
        }
        children.removeAll(uselessChildren);

        return isImportant;
    }


    private void refreshIndexAndDepth(int newIndex, int newDepth){
        // 修改了树之后进行更新
        index = newIndex;
        depth = newDepth;
        for(int i = 0; i < getChildCount(); ++ i){
            getChild(i).refreshIndexAndDepth(i, depth + 1);
        }
    }

    private void refreshAbsoluteId(){
        if(absoluteId != null){
            AccessibilityNodeInfoRecord.idToRecord.remove(absoluteId);
        }
        if(parent == null){
            absoluteId = getClassName() == null? "null" : getClassName().toString();
        } else {
            absoluteId = parent.absoluteId + "|" + index + ";" + (getClassName() == null? "null" :getClassName().toString());
        }
        AccessibilityNodeInfoRecord.idToRecord.put(absoluteId, this);
        if(this.nodeInfo != null)
            AccessibilityNodeInfoRecord.nodeInfoHashToId.put(this.nodeInfo.hashCode(),absoluteId);
        for(AccessibilityNodeInfoRecord child: children){
            child.refreshAbsoluteId();
        }
    }

    /**
     * Gets the parent.
     *
     * @return The parent.
     */
    public AccessibilityNodeInfoRecord getParent(){
        return parent;
    }

    /**
     * Gets whether the node has been cleaned.
     *
     * @return True if the node has been cleaned.
     */
    public boolean isCleaned(){
        if(nodeInfo == null){
            Log.w("CleanedRecord", "isCleaned: " + textCached + ' ' + contentCached);
        }

        return nodeInfo == null;
    }

    /**
     * The record may be cleaned but and another record to the same node is create. Invoke this method to get the new record.
     * If the current has not been cleaned, it will return {@code this}.
     * In fact, if the system works as expected, this method returns either {@code this} or {@code null}
     *
     * @return an not cleaned record. returns null if the record do not exist in the tree
     */
    public AccessibilityNodeInfoRecord convertToNotCleanedRecord(){
        if(!isCleaned()){
            return this;
        }
        int hashCode = nodeInfoHash;
        String id = nodeInfoHashToId.get(hashCode);
        return idToRecord.get(id);
    }

    /**
     * Gets whether this node is clickable.
     *
     * @return True if the node is clickable.
     */
    public boolean isClickable(){
        if(isCleaned())
            return _isClickable;
        return nodeInfo.isClickable();
    }

    /**
     * Gets if the node is scrollable.
     *
     * @return True if the node is scrollable, false otherwise.
     */
    public boolean isScrollable(){
        if(isCleaned())
            return _isScrollable;
        return nodeInfo.isScrollable();
    }

    /**
     * Gets whether this node is long clickable.
     *
     * @return True if the node is long clickable.
     */
    public boolean isLongClickable(){
        if(isCleaned())
            return _isLongClickable;
        return nodeInfo.isLongClickable();
    }

    /**
     * Gets if the node is editable.
     *
     * @return True if the node is editable, false otherwise.
     */
    public boolean isEditable(){
        if(isCleaned())
            return _isEditable;
        return nodeInfo.isEditable();
    }

    /**
     * Gets whether this node is checkable.
     *
     * @return True if the node is checkable.
     */
    public boolean isCheckable(){
        if(isCleaned())
            return _isCheckable;
        return nodeInfo.isCheckable();
    }

    /**
     * Gets the number of children.
     *
     * @return The child count.
     */
    public int getChildCount(){
        return children.size();
    }

    /**
     * Get the child at given index.
     *
     * @param index The child index.
     * @return The child node.
     */
    public AccessibilityNodeInfoRecord getChild(int index){
        return children.get(index);
    }

    /**
     * Gets the text of this node.
     *
     * @return The text.
     */
    public CharSequence getText(){
        if(isCleaned())
            return textCached;
        textCached = nodeInfo.getText();
        return textCached;
    }

    /**
     * Gets the hint text of this node. Only applies to nodes where text can be entered.
     *
     * @return The hint text.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public CharSequence getHintText(){
        if(isCleaned()){
            return hintTextCached;
        }
        hintTextCached = nodeInfo.getHintText();
        return hintTextCached;
    }

    /**
     * Gets the content description of this node.
     *
     * @return The content description.
     */
    public CharSequence getContentDescription(){
        if(isCleaned())
            return contentCached;
        contentCached = nodeInfo.getContentDescription();
        return contentCached;
    }

    /**
     * Performs an action on the node.
     *
     * @param action The action to perform.
     * @return True if the action was performed.
     */
    public boolean performAction(int action){
        if(isCleaned())
            return false;
        return nodeInfo.performAction(action);
    }

    /**
     * Performs an action on the node.
     *
     * @param action The action to perform.
     * @param info A bundle with additional arguments.
     * @return True if the action was performed.
     */
    public boolean performAction(int action, Bundle info){
        if(isCleaned())
            return false;
        return nodeInfo.performAction(action, info);
    }

    /**
     * Gets the class this node comes from.
     *
     * @return The class name.
     */
    public CharSequence getClassName(){
        if(isCleaned())
            return _className;
        return nodeInfo.getClassName();
    }

    /**
     * Gets the window to which this node belongs
     *
     * @return The window.
     */
    public AccessibilityWindowInfo getWindow(){
        if(isCleaned())
            return null;
        return nodeInfo.getWindow();
    }

    /**
     * Gets the node bounds in screen coordinates.
     *
     * @param r The output node bounds.
     */
    public void getBoundsInScreen(Rect r){
        if(isCleaned()){
            r.set(_boundInScreen);
            return;
        }
        nodeInfo.getBoundsInScreen(r); // 不需要进行缓存
    }

    /**
     * Gets whether this node is selected.
     *
     * @return True if the node is selected.
     */
    public boolean isSelected(){
        if(isCleaned())
            return _isSelected;
        return nodeInfo.isSelected();
    }

    /**
     * Gets the package this node comes from.
     *
     * @return The package name.
     */
    public CharSequence getPackageName(){
        if(isCleaned())
            return _packageName;
        return nodeInfo.getPackageName();
    }

/*    public int getDrawingOrder(){
        return nodeInfo.getDrawingOrder();
    }*/

    /**
     * Gets the fully qualified resource name of the source view's id.
     *
     * @return The id resource name.
     */
    public CharSequence getViewIdResourceName(){
        if(isCleaned())
            return _viewIdResourceName;
        return nodeInfo.getViewIdResourceName();
    }

    /**
     * Gets whether this node is visible to the user.
     *
     * @return Whether the node is visible to the user.
     */
    public boolean isVisibleToUser(){
        if(isCleaned())
            return _isVisibleToUser;
        return nodeInfo.isVisibleToUser();
    }

    /**
     * Gets whether this node is focusable.
     *
     * @return True if the node is focusable.
     */
    public boolean isFocusable(){
        if(isCleaned())
            return _isFocusable;
        return nodeInfo.isFocusable();
    }

    /**
     * Gets if the node can be dismissed.
     *
     * @return If the node can be dismissed.
     */
    public boolean isDismissable(){
        if(isCleaned())
            return _isDismissable;
        return nodeInfo.isDismissable();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        if(this.nodeInfo != null && ((AccessibilityNodeInfoRecord) obj).nodeInfo != null){
            return this.nodeInfo.equals(((AccessibilityNodeInfoRecord) obj).nodeInfo);
        } else if(this.nodeInfo == null && ((AccessibilityNodeInfoRecord) obj).nodeInfo == null){
            return this.nodeInfoHash == ((AccessibilityNodeInfoRecord) obj).nodeInfoHash;
        } else {
            return false;
        }

    }

    @Override
    public int hashCode() {
        if(nodeInfo != null){
            return nodeInfo.hashCode();
        }
        return super.hashCode();
    }

    /**
     * get index in the siblings
     * @return the index
     */
    public int getIndex(){
        return index;
    }

    /**
     * get children list
     * @return the list of children
     */
    public List<AccessibilityNodeInfoRecord> getChildren() {
        return children;
    }


    private boolean isMeaningful(){
        if(isCheckable() || isEditable() || isScrollable() || isLongClickable() || isClickable()){
            return true;
        }
        if(getText() != null && getText().length() > 0){
            return true;
        }
        if(getContentDescription() != null && getContentDescription().length() > 0){
            return true;
        }
        if(getViewIdResourceName() != null && getViewIdResourceName().length() > 0){
            return true;
        }
        if(children.size() != 1){
            return true;
        }
        return false;
    }

    /**
     * return all texts the node and its successors contains
     *
     * @return all texts the sub tree contains
     */
    public String getAllTexts() {
        /*if (allTexts != null)
            return allTexts;*/
        allTexts = getText() == null? "": getText().toString();
        for(AccessibilityNodeInfoRecord child: children){
            allTexts += child.getAllTexts();
        }
        return allTexts;
    }

    /**
     * return all contents the node and its successors contains
     *
     * @return all contents the sub tree contains
     */
    public String getAllContents(){
        if (allContents != null)
            return allContents;
        allContents = getContentDescription() == null? "": getContentDescription().toString();
        for(AccessibilityNodeInfoRecord child: children){
            allContents += child.getAllContents();
        }
        return allContents;
    }

    /**
     * Gets all valid text
     * @return all valid text
     */
    public String getAllValidText(){
        if(allValidText != null){
            return allValidText;
        }
        StringBuilder builder = new StringBuilder();
        Queue<AccessibilityNodeInfoRecord> q = new LinkedList<>();
        q.add(this);
        while (!q.isEmpty()){
            AccessibilityNodeInfoRecord crt = q.poll();
            if(crt == null){
                continue;
            }
            if(crt.getText() != null && crt.getText().length() > 0){
                builder.append(crt.getText()).append(" ");
            }
            for(AccessibilityNodeInfoRecord c: crt.children){
                if(c.isScrollable() || c.isCheckable() || c.isEditable() || c.isClickable() || c.isLongClickable()){
                    continue;
                }
                q.add(c);
            }
        }
        allValidText = builder.toString();
        return allValidText;
    }

    /**
     * Gets all valid contents
     *
     * @return all valid contents
     */
    public String getAllValidContent(){
        if(allValidContent != null){
            return allValidContent;
        }
        StringBuilder builder = new StringBuilder();
        Queue<AccessibilityNodeInfoRecord> q = new LinkedList<>();
        q.add(this);
        while (!q.isEmpty()){
            AccessibilityNodeInfoRecord crt = q.poll();
            if(crt == null){
                continue;
            }
            if(crt.getContentDescription() != null && crt.getContentDescription().length() > 0){
                builder.append(crt.getContentDescription()).append(" ");
            }
            for(AccessibilityNodeInfoRecord c: crt.children){
                if(c.isScrollable() || c.isCheckable() || c.isEditable() || c.isClickable() || c.isLongClickable()){
                    continue;
                }
                q.add(c);
            }
        }
        allValidContent = builder.toString();
        return allValidContent;
    }

    public AccessibilityNodeInfoRecord getNodeByRelativeId(String relativeId){
        String[] subIdList = relativeId.split(";");
        AccessibilityNodeInfoRecord crtNode = this;
        for(int i = 0; i < subIdList.length - 1; ++ i){
            String subId = subIdList[i];
            String[] subIdSplited = subId.split("\\|");
            if(!crtNode.getClassName().toString().equals(subIdSplited[0])){
                return null;
            }

            int intendedIndex = Integer.valueOf(subIdSplited[1]);
            AccessibilityNodeInfoRecord targetChild = null;
            for(AccessibilityNodeInfoRecord child: crtNode.children){
                if (child.index == intendedIndex){
                    targetChild = child;
                    break;
                }
            }

            if(targetChild == null){
                return null;
            }
            crtNode = targetChild;
        }
        if(!crtNode.getClassName().toString().equals(subIdList[subIdList.length - 1])){
            return null;
        }
        return crtNode;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if(this.nodeInfo != null){
            this.nodeInfo.recycle();
            this.nodeInfo = null;
        }
    }

    @Override
    public String toString() {
        return getText() + " --- " + getContentDescription();
    }

    public String toAllString() {
        return getAllValidText() + " --- " + getAllValidContent();
    }

    public static Map<String, List<AccessibilityNodeInfoRecord>> getNodeChangeStateToNodeList() {
        return nodeChangeStateToNodeList;
    }

    private static void clearNodeChangeStateToNodeList(){
        if(nodeChangeStateToNodeList == null){
            nodeChangeStateToNodeList = new HashMap<>();
        }
        nodeChangeStateToNodeList.clear();
        nodeChangeStateToNodeList.put(CHANGE_NEW_CREATED, new ArrayList<AccessibilityNodeInfoRecord>());
        nodeChangeStateToNodeList.put(CHANGE_TEXT_CHANGED, new ArrayList<AccessibilityNodeInfoRecord>());
        nodeChangeStateToNodeList.put(CHANGE_CONTENT_CHANGED, new ArrayList<AccessibilityNodeInfoRecord>());
        nodeChangeStateToNodeList.put(CHANGE_DELETED, new ArrayList<AccessibilityNodeInfoRecord>());
    }

    private boolean hasInteractAncestor(){
        if(!interactAncestorDirty){
            return interactAncestor;
        }

        if(parent == null){
            interactAncestorDirty = false;
            interactAncestor = false;
            return false;
        }

        if(parent.isClickable() || parent.isLongClickable() || parent.isEditable() || parent.isCheckable()){
            interactAncestorDirty = false;
            interactAncestor = true;
            return true;
        }

        interactAncestor = parent.hasInteractAncestor();
        interactAncestorDirty = false;
        return interactAncestor;
    }

    /**
     * get next node
     * @param wrap if wrap is true and there is no next, this method will return the first node in the tree. Otherwise it will returns null.
     * @return next node
     */
    public AccessibilityNodeInfoRecord next(boolean wrap){
        AccessibilityNodeInfoRecord crt = this;
        while (true){
            crt = crt.nextRaw();
            if(crt == null){
                break;
            }
            if(crt.isClickable() || crt.isLongClickable() || crt.isEditable() || crt.isCheckable()){
                if((!crt.getAllValidText().isEmpty()) || (!crt.getAllValidContent().isEmpty())){
                    return crt;
                }
            }
            if(crt.getText() != null && crt.getText().length() > 0 && !crt.hasInteractAncestor()){
                return crt;
            }
            if(crt.getContentDescription() != null && crt.getContentDescription().length() > 0 && !crt.hasInteractAncestor()){
                return crt;
            }
        }
        if(!wrap){
            return null;
        }
        return AccessibilityNodeInfoRecord.root.next(false);
    }

    /**
     * get previous node
     * @param wrap if wrap is true and there is no previous, this method will return the last node in the tree. Otherwise it will returns null.
     * @return next node
     */
    public AccessibilityNodeInfoRecord prev(boolean wrap){
        AccessibilityNodeInfoRecord crt = this;
        while (true){
            crt = crt.prevRaw();
            if(crt == null){
                break;
            }
            if(crt.isClickable() || crt.isLongClickable() || crt.isEditable() || crt.isCheckable()){
                if((!crt.getAllValidText().isEmpty()) || (!crt.getAllValidContent().isEmpty())){
                    return crt;
                }
            }
            if(crt.getText() != null && crt.getText().length() > 0 && !crt.hasInteractAncestor()){
                return crt;
            }
            if(crt.getContentDescription() != null && crt.getContentDescription().length() > 0 && !crt.hasInteractAncestor()){
                return crt;
            }
        }
        if(!wrap){
            return null;
        }
        return AccessibilityNodeInfoRecord.root.lastInSubTree().prev(false);
    }

    private AccessibilityNodeInfoRecord getNextSibling(){
        // get sibling
        if(parent != null){
            index = parent.children.indexOf(this);
            int resIndex = index + 1;
            if(resIndex != parent.children.size()){
                return parent.children.get(resIndex);
            }
        }
        return null;
    }

    private AccessibilityNodeInfoRecord getPrevSibling(){
        // get sibling
        if(parent != null){
            index = parent.children.indexOf(this);
            int resIndex = index - 1;
            if(resIndex >= 0){
                return parent.children.get(resIndex);
            }
        }
        return null;
    }

    private AccessibilityNodeInfoRecord nextRaw(){
        if(!children.isEmpty()){
            return children.get(0);
        }
        AccessibilityNodeInfoRecord sibling = getNextSibling();
        if(sibling != null ){
            return sibling;
        }
        AccessibilityNodeInfoRecord ancestor = parent;

        while (ancestor != null){
            AccessibilityNodeInfoRecord c =  ancestor.getNextSibling();
            if(c != null){
                return c;
            }
            ancestor = ancestor.parent;
        }
        return null;
    }

    /**
     * Get last node in sub tree.
     *
     * @return last node in sub tree
     */
    public AccessibilityNodeInfoRecord lastInSubTree(){
        if(children.isEmpty()){
            return this;
        }
        AccessibilityNodeInfoRecord lastChild = children.get(children.size() - 1);
        return lastChild.lastInSubTree();
    }

    private AccessibilityNodeInfoRecord prevRaw(){
        AccessibilityNodeInfoRecord prevSibling = getPrevSibling();
        if(prevSibling == null){
            return parent;
        }
        return prevSibling.lastInSubTree();
    }
}
