package pcg.hcit_service;

import android.util.Log;
import android.util.LongSparseArray;
import android.util.Pair;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import pcg.hcit_service.Selector.NodeSelector;
import pcg.hcit_service.Template.PageTemplateInfo;
import pcg.hcit_service.Template.TemplateManager;

/**
 * Thread to monitor the content of the screen and invoke callbacks
 */
public class RefreshThread extends Thread {
    /**
     * MUTEX to help communicate with other threads.
     * Wrap your codes with the MUTEX if you do not want the ui tree changed when your codes are running
     */
    public static final Integer MUTEX = 0;

    private static final Integer LISTENER_OP_MUTEX = "LISTENER_OP_MUTEX".hashCode();

    public static final Integer QUEUE_OP_MUTEX = "QUEUE_OP_MUTEX".hashCode();

    /**
     * callback when page content change.
     * The codes will be run in the {@link RefreshThread}, thus the refreshing of ui tree will be blocked. Manually create another thread if necessary
     */
    public interface ContentChangeListener {
        /**
         * the methods will be called if the screen content changes from one known page to another.
         * @param lastPageName the name of the last page. null if the last page is unknown.
         * @param newPageName the name of the current page.
         */
        void onPageChange(String lastPageName, String newPageName);

        /**
         * the methods will be called if the screen content changes but still a same page
         * @param currentPageName the name of the current page
         * @param changeTypeToNodeList the condition of changed. Every change type will map to a node list
         */
        void onPageUpdate(String currentPageName, Map<String, List<AccessibilityNodeInfoRecord>> changeTypeToNodeList);

        /**
         * the methods will be called if the screen content changes but still an unknown page
         * @param lastPageName the name of the last page. null if the last page is unknown.
         * @param changeTypeToNodeList the condition of changed. Every change type will map to a node list
         */
        void onUnknownPageContentChange(String lastPageName, Map<String, List<AccessibilityNodeInfoRecord>> changeTypeToNodeList);
    }

    private boolean hasFinished;
    private static RefreshThread instance;
    private Queue<AccessibilityNodeInfo> changedNodes;
    private PageTemplateInfo lastPageInfo;
    private PageTemplateInfo crtPageInfo;
    private List<ContentChangeListener> listeners;
    private String crtPageName;
    private LongSparseArray<List<ContentChangeListener>> storeIdToListeners;
    private RefreshThread(){
        super("RefreshThread");
        changedNodes = new LinkedList<>();
        lastPageInfo = null;
        listeners = new ArrayList<>();
        storeIdToListeners = new LongSparseArray<>();
    }

    /**
     *
     * @return instance of the current thread
     */
    public static RefreshThread getInstance(){
        if(instance == null){
            instance = new RefreshThread();
        }

        return instance;
    }

    private static boolean isScrolling = false;

    /**
     * call this methods if you just invoke {@link AccessibilityNodeInfoRecord#performAction(int)} and the parameter is {@link AccessibilityNodeInfo#ACTION_SCROLL_BACKWARD} or {@link AccessibilityNodeInfo#ACTION_SCROLL_FORWARD}
     * the thread will wait more time to make sure that the page content has fully updated.
     */
    public static void setScroll(){
        isScrolling = true;
    }

    /**
     *
     * @return whether it is scrolling.
     */
    public static boolean isScrolling(){
        return isScrolling;
    }

    private static final int maxWaitNextWhenScroll = 300;
    private static final int maxAccumulateTime = 100;
    private static final int handleDelay = 50;

    private long lastChangeNodeArrive = -1L;
    private long firstChangedNodeArrive = -1L;

    /**
     * recode time when node changed. Used in {@link HCITService#onAccessibilityEvent(AccessibilityEvent)} ane may be useless to you
     */
    public void setWhenChangeNodeArrive(){
        lastChangeNodeArrive = System.currentTimeMillis();
        if(changedNodes.isEmpty()){
            firstChangedNodeArrive = lastChangeNodeArrive;
        }
    }

    /**
     * monitor the content of the screen and invoke callbacks
     */
    @Override
    public void run() {
        while (!hasFinished){
            long crtTime = System.currentTimeMillis();
            if(isScrolling() && changedNodes.size() < 2 && crtTime - firstChangedNodeArrive <=maxWaitNextWhenScroll){
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            }

            if( ( changedNodes.isEmpty() || (crtTime - lastChangeNodeArrive <= handleDelay) && (firstChangedNodeArrive <= 0 || crtTime - firstChangedNodeArrive <= maxAccumulateTime))){
                try {
                    Thread.sleep(Math.max(0, Math.min(handleDelay, crtTime - lastChangeNodeArrive)));
                } catch (InterruptedException e) {
                    if(!hasFinished)
                        e.printStackTrace();
                }
                continue;
            }

            if(!changedNodes.isEmpty()) {
                List<AccessibilityNodeInfo> nodes;
                synchronized (QUEUE_OP_MUTEX){
                    nodes = new ArrayList<>(changedNodes);
                    changedNodes.clear();
                }
                Utility.filter(nodes, new Utility.cond<AccessibilityNodeInfo>() {
                    @Override
                    public boolean satisfy(AccessibilityNodeInfo a) {
                        return a != null;
                    }
                });
                firstChangedNodeArrive = 0;
                // AccessibilityNodeInfoRecord.buildTree();
                List<AccessibilityNodeInfo> useful = Utility.deleteNodeAsSuccessor(nodes); // 返回的节点并没有被重新obtain
                Utility.filter(useful, new Utility.cond<AccessibilityNodeInfo>() {
                    @Override
                    public boolean satisfy(AccessibilityNodeInfo a) {
                        return a != null;
                    }
                });

                Log.i("ChangedNodeNum", "run: node num " + useful.size() + " / " + nodes.size());
                AccessibilityNodeInfoRecord.markNodeAsNotRefreshed(AccessibilityNodeInfoRecord.root);

                synchronized (MUTEX){
                    for(AccessibilityNodeInfo n: useful){
                        AccessibilityNodeInfoRecord.partiallyRefreshTree(n);
                    }
                }

                if(AccessibilityNodeInfoRecord.root != null){
                    handlerWindowChange();
                }

                isScrolling = false;
                for(AccessibilityNodeInfo n: nodes){
                    if(n != null){
                        n.recycle();
                    }
                }

            }
        }
    }

    /**
     * stop monitor. This has already been called in {@link HCITService#onDestroy()}, and you do not need to invoke it manually.
     */
    public void stopSelf(){
        hasFinished = true;
        instance  = null;
        this.interrupt();
    }

    private void handlerWindowChange(){
        lastPageInfo = crtPageInfo;
        Pair<PageTemplateInfo, Utility.BidirectionalListMap<NodeSelector, AccessibilityNodeInfoRecord>> judgeRes =
                TemplateManager.getPageIndexAndNonRefCache(AccessibilityNodeInfoRecord.root, lastPageInfo, 0.5);
        crtPageInfo = judgeRes == null? null: judgeRes.first;
        if(crtPageInfo != null){
            crtPageName = TemplateManager.getPageName(crtPageInfo.getPackageName(), crtPageInfo.getIndex());
        }
        String lastPageName = null;
        if(lastPageInfo != null){
            lastPageName = TemplateManager.getPageName(lastPageInfo.getPackageName(), lastPageInfo.getIndex());
        }
        if(judgeRes == null){
            synchronized (LISTENER_OP_MUTEX){
                for(ContentChangeListener l: listeners){
                    l.onUnknownPageContentChange(lastPageName, AccessibilityNodeInfoRecord.getNodeChangeStateToNodeList());
                }
            }
        } else {
            PageTemplateInfo crtPage = judgeRes.first;
            if(judgeRes.first == lastPageInfo){
                synchronized (LISTENER_OP_MUTEX){
                    for(ContentChangeListener l: listeners){
                        l.onPageUpdate(lastPageName, AccessibilityNodeInfoRecord.getNodeChangeStateToNodeList());
                    }
                }
            } else {
                synchronized (LISTENER_OP_MUTEX){
                    for(ContentChangeListener l: listeners){
                        l.onPageChange(lastPageName, TemplateManager.getPageName(
                                crtPage.getPackageName(), crtPage.getIndex()));
                    }
                }
            }
        }
    }

    /**
     * add an listener
     * @param l the listener to add
     */
    public void bindContentChangeListener(ContentChangeListener l){
        synchronized (LISTENER_OP_MUTEX){
            listeners.add(l);
        }
    }

    /**
     * delete an listener
     * @param l the listener to delete
     * @return true if the listener is deleted successfully
     */
    public boolean unbindContentChangeListener(ContentChangeListener l){
        synchronized (LISTENER_OP_MUTEX){
            return listeners.remove(l);
        }
    }

    /**
     * clear the current listener list 
     * @return storeId, you can recover the list use {@link RefreshThread#restoreListeners(long)}
     */
    public long storeCurrentListenersAndClear(){
        synchronized (LISTENER_OP_MUTEX) {
            long storeId = System.currentTimeMillis();
            List<ContentChangeListener> store = new ArrayList<>(this.listeners);
            storeIdToListeners.put(storeId, store);
            this.listeners.clear();
            return storeId;
        }
    }

    /**
     * clear the current listener list and restore a previous list according to the given id
     * @param id the storeId, which must be the return value of {@link RefreshThread#storeCurrentListenersAndClear()}
     * @return true if succeed
     */
    public boolean restoreListeners(long id){
        if(storeIdToListeners.indexOfKey(id) >= 0){
            this.listeners.clear();
            this.listeners.addAll(storeIdToListeners.get(id));
            storeIdToListeners.delete(id);
            return true;
        }
        return false;
    }

    /**
     * add a node to the changed list. It is used in {@link HCITService#onAccessibilityEvent(AccessibilityEvent)} and may be useless to you.
     * @param node node that changed
     * {@hide}
     */
    public void addChangedNode(AccessibilityNodeInfo node){
        changedNodes.add(node);
    }

    /**
     * return current page index. If current page is unknown, it will return -1
     * @return current page index
     */
    public int getCurrentPageIndex(){
        if(crtPageInfo == null){
            return -1;
        }
        return crtPageInfo.getIndex();
    }

    /**
     * return current page name.
     * If current page is unknown, it will return null.
     * If you haven't set an alias for the current page, it will return [package name]_[index]
     * @return current page name
     */
    public String getCurrentPageName(){
        if(crtPageInfo == null){
            return null;
        }

        return TemplateManager.getPageName(crtPageInfo.getPackageName(), crtPageInfo.getIndex());
    }

    /**
     * get current page info
     * @return current page info
     */
    public PageTemplateInfo getCurrentPageInfo(){
        return crtPageInfo;
    }

    /**
     * get current package name. One package name is corresponding to one app.
     * @return current package name
     */
    public String getCurrentPackage(){
        if(crtPageInfo == null){
            return null;
        }

        return crtPageInfo.getPackageName();
    }
}
