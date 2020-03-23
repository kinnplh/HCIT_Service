package pcg.hcit_service;

import android.os.Bundle;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import java.sql.Ref;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import pcg.hcit_service.Selector.NodeSelector;
import pcg.hcit_service.Selector.OperationSelector;
import pcg.hcit_service.Selector.SelectorChain;
import pcg.hcit_service.Selector.SelectorChainRefSelector;
import pcg.hcit_service.Template.AppConfInfo;
import pcg.hcit_service.Template.AppPageTransitionInfo;
import pcg.hcit_service.Template.PageTemplateInfo;
import pcg.hcit_service.Template.TemplateManager;

/**
 * provide several methods to help you access nodes
 */
public class NodeAccessController {
    /**
     * a class to help you access the search result of the current page. You may not need to create instance of the class manually
     */
    public static class NodeSearchRes {
        /**
         * the alias of the search result
         */
        public String nodeAlias;
        /**
         * the alias of the referred node. null if you haven't set the alias or the node does not need a reference
         */
        public String referredNodeAlias;
        /**
         * the search result
         */
        public AccessibilityNodeInfoRecord result;
        /**
         * the referred node. null if the node does not need a reference
         */
        public AccessibilityNodeInfoRecord nodeReferred;

        public NodeSearchRes(String nodeAlias, String referredNodeAlias, AccessibilityNodeInfoRecord result, AccessibilityNodeInfoRecord nodeReferred){
            this.nodeAlias = nodeAlias;
            this.referredNodeAlias = referredNodeAlias;
            this.result = result;
            this.nodeReferred = nodeReferred;
        }
    }

    private static List<AccessibilityNodeInfoRecord> getNodesBySelectorChain(SelectorChain selectorChain, Map<String, List<AccessibilityNodeInfoRecord>> aliasToNodeList){
        if(selectorChain == null){
            return Collections.emptyList();
        }
        RefreshThread refreshThread = RefreshThread.getInstance();
        AppConfInfo appConfInfo = TemplateManager.getAppConfInfoByPackageName(refreshThread.getCurrentPackage());
        if(appConfInfo == null){
            return Collections.emptyList();
        }
        String alias = appConfInfo.getAliasBySelectorChain(selectorChain);
        List<AccessibilityNodeInfoRecord> result = new ArrayList<>();
        if(alias != null){
            List<NodeSearchRes> res = findNodeOnCurrentPageByAlias(alias, aliasToNodeList);
            for(NodeSearchRes r: res){
                result.add(r.result);
            }
        } else {
            List<AccessibilityNodeInfoRecord> directRes = selectorChain.getNode(AccessibilityNodeInfoRecord.root);
            if(directRes != null){
                result.addAll(directRes);
            }
        }

        return result;
    }

    private static List<AccessibilityNodeInfoRecord> getNodesBySelectorChainUsingSCMap(SelectorChain selectorChain, Map<SelectorChain, List<AccessibilityNodeInfoRecord>> selectorChainToNodes){
        if(selectorChain == null){
            return Collections.emptyList();
        }

        List<AccessibilityNodeInfoRecord> result;
        if(!(selectorChain.selectors.get(0) instanceof SelectorChainRefSelector)){
            result = selectorChain.getNode(AccessibilityNodeInfoRecord.root);
        } else {
            SelectorChain referred = ((SelectorChainRefSelector) selectorChain.selectors.get(0)).refChain;
            List<AccessibilityNodeInfoRecord> referredRes = selectorChainToNodes.get(referred);
            if(referredRes == null){
                Log.w("RefNotCached", "getNodesBySelectorChain: sc " + referred.identifier + " not cached");
                result = selectorChain.getNode(AccessibilityNodeInfoRecord.root);
            } else {
                List<AccessibilityNodeInfoRecord> crtNodes = new ArrayList<>(referredRes);
                for(int i = 1; i < selectorChain.selectors.size(); ++ i){
                    NodeSelector crt = selectorChain.selectors.get(i);
                    List<AccessibilityNodeInfoRecord> tmpRes = crt.getNode(AccessibilityNodeInfoRecord.root, crtNodes);
                    crtNodes = tmpRes;
                }
                result = crtNodes;
            }
        }

        return result;
    }

    /**
     * find the node on the current page according to the node alias
     * You can controlle the search result using aliasToNodeList.
     * If the node needs a reference, you can set an alias for the reference node, construct a result list to the alias and pass them to the aliasToNodeList.
     * for example, if node A and node B are items in the mail list and node c is the textview indicating the username of A and d is that of B.
     * if c refers to A and you set c an alias <i>UserName</i> and A alias <i>Item</i>. there are four situations
     * 1. the parameter <i>alias</i> is <i>UserName</i> and <i>aliasToNodeList</i> is empty, the search result will be <i>c</i> and <i>d</i>.
     * 2. the parameter <i>alias</i> is <i>UserName</i> and in <i>aliasToNodeList</i>, <i>Item</i> is mapped to <i>A</i>, the search result will be <i>c</i> only.
     * 3. the parameter <i>alias</i> is <i>UserName</i> and in <i>aliasToNodeList</i>, <i>Item</i> is mapped to <i>B</i>, the search result will be <i>d</i> only.
     * 4. the parameter <i>alias</i> is <i>UserName</i> and in <i>aliasToNodeList</i>, <i>Item</i> is mapped to <i>A</i> and <i>B</i>, the search result will be <i>c</i> and <i>d</i>.
     *
     * @param alias alias of the node you want to search
     * @param aliasToNodeList a map from alias to node list.
     * @return search result
     */
    public static List<NodeSearchRes> findNodeOnCurrentPageByAlias(String alias, Map<String, List<AccessibilityNodeInfoRecord>> aliasToNodeList){
        RefreshThread refreshThread = RefreshThread.getInstance();
        AppConfInfo appConfInfo = TemplateManager.getAppConfInfoByPackageName(refreshThread.getCurrentPackage());
        if(appConfInfo == null){
            return Collections.emptyList();
        }
        SelectorChain sc = appConfInfo.getSCByNodeAlias(alias);
        if(sc.selectors.isEmpty()){
            return Collections.emptyList();
        }
        List<NodeSearchRes> result = new ArrayList<>();
        NodeSelector firstSelector = sc.selectors.get(0);
        if(firstSelector instanceof SelectorChainRefSelector){
            SelectorChainRefSelector scrs = (SelectorChainRefSelector) firstSelector;
            String aliasForReferred = appConfInfo.getAliasBySelectorChain(scrs.refChain);
            List<AccessibilityNodeInfoRecord> referredNodes = aliasToNodeList.get(aliasForReferred);
            if(referredNodes == null){
                // 重新进行定位就可以了
                referredNodes = getNodesBySelectorChain(scrs.refChain, aliasToNodeList);
            }

            if(referredNodes.isEmpty()){
                return Collections.emptyList();
            }

            // 然后后续一个一个使用referred node 来进行节点的查找
            for(AccessibilityNodeInfoRecord referredNode: referredNodes){
                List<AccessibilityNodeInfoRecord> crtNode = new ArrayList<>();
                crtNode.add(referredNode);

                for(int i = 1; i < sc.selectors.size(); ++ i){
                    NodeSelector selector = sc.selectors.get(i);
                    List<AccessibilityNodeInfoRecord> crtRes = selector.getNode(AccessibilityNodeInfoRecord.root, crtNode);
                    crtNode = crtRes;
                }
                for(AccessibilityNodeInfoRecord resNode: crtNode){
                    result.add(new NodeSearchRes(alias, aliasForReferred, resNode, referredNode));
                }
            }
            return result;
        } else if(firstSelector instanceof OperationSelector){
            OperationSelector os = (OperationSelector) firstSelector;
            SelectorChain sc1 = os.sc1;
            SelectorChain sc2 = os.sc2;
            Set<AccessibilityNodeInfoRecord> set1 = new LinkedHashSet<>(getNodesBySelectorChain(sc1, aliasToNodeList));
            Set<AccessibilityNodeInfoRecord> set2 = new LinkedHashSet<>(getNodesBySelectorChain(sc2, aliasToNodeList));
            OperationSelector.Operator operation = os.operation;

            List<AccessibilityNodeInfoRecord> crtNodes = new ArrayList<>();
            if(operation == OperationSelector.Operator.INTERSECTION){
                set1.retainAll(set2);
                crtNodes.addAll(set1);
            } else if(operation == OperationSelector.Operator.UNION){
                set1.addAll(set2);
                crtNodes.addAll(set1);
            } else if(operation == OperationSelector.Operator.DIFF){
                set1.removeAll(set2);
                crtNodes.addAll(set1);
            } else if(operation == OperationSelector.Operator.XOR){
                Set<AccessibilityNodeInfoRecord> set3 = new LinkedHashSet<>(set1);
                set3.addAll(set2);
                set1.retainAll(set2);
                set3.removeAll(set1);
                crtNodes.addAll(set3);
            }
            for(int i = 1; i < sc.selectors.size(); ++ i){
                NodeSelector selector = sc.selectors.get(i);
                List<AccessibilityNodeInfoRecord> crtRes = selector.getNode(AccessibilityNodeInfoRecord.root, crtNodes);
                crtNodes = crtRes;
            }
            for(AccessibilityNodeInfoRecord resNode: crtNodes){
                result.add(new NodeSearchRes(alias, null, resNode, null));
            }
            return result;
        } else {
            List<AccessibilityNodeInfoRecord> resNodes = sc.getNode(AccessibilityNodeInfoRecord.root);
            for(AccessibilityNodeInfoRecord resNode: resNodes){
                result.add(new NodeSearchRes(alias, null, resNode, null));
            }
            return result;
        }
    }

    /**
     * Calculate a path from a given page to another
     * @param packageName indicates the app in which you want to calculate the transition path
     * @param startIndex source page index
     * @param endIndex destination page index
     * @param stopPathSet the action in the calculated path will not appear in the set.
     * @param stopNodeSet the index of pages in the calculated path will not appear in the set
     * @return list of {@link PageTemplateInfo.TransInfo} Trans info indicates what you need to do every setp
     */
    public static List<PageTemplateInfo.TransInfo> calTransitionPath(String packageName, int startIndex,
                                                                     int endIndex,
                                                                     Set<PageTemplateInfo.TransInfo> stopPathSet,
                                                                     Set<Integer> stopNodeSet){
        if(startIndex == endIndex){
            return Collections.emptyList();
        }
        AppPageTransitionInfo crtAppInfo = TemplateManager.getPackageNameToAppPageTransInfo().get(packageName);
        if(crtAppInfo == null){
            return null;
        }

        SparseArray<Pair<Integer, List<PageTemplateInfo.TransInfo>>> indexToPathReachFromSrc = new SparseArray<>();
        SparseArray<Pair<Integer, List<PageTemplateInfo.TransInfo>>> indexToPathCanReachDes = new SparseArray<>();
        Queue<Pair<Integer, List<PageTemplateInfo.TransInfo>>> qFromSrc = new LinkedList<>();
        Queue<Pair<Integer, List<PageTemplateInfo.TransInfo>>> qToDes = new LinkedList<>();
        Pair<Integer, List<PageTemplateInfo.TransInfo>> pairForSrc = new Pair<Integer, List<PageTemplateInfo.TransInfo>>(startIndex, new ArrayList<PageTemplateInfo.TransInfo>());
        Pair<Integer, List<PageTemplateInfo.TransInfo>> pairForDes = new Pair<Integer, List<PageTemplateInfo.TransInfo>>(endIndex, new ArrayList<PageTemplateInfo.TransInfo>());
        indexToPathReachFromSrc.put(pairForSrc.first, pairForSrc);
        indexToPathCanReachDes.put(pairForDes.first, pairForDes);
        qFromSrc.add(pairForSrc);
        qToDes.add(pairForDes);

        // 进行一个双向的搜索
        while ((!qFromSrc.isEmpty()) && (!qToDes.isEmpty())){
            Pair<Integer, List<PageTemplateInfo.TransInfo>> reachInfoFromSrc = qFromSrc.poll();
            List<PageTemplateInfo.TransInfo> allTransInfoInCrtPage = crtAppInfo.srcPageIndexToTransInfoList.get(reachInfoFromSrc.first);
            if(allTransInfoInCrtPage != null){
                allTransInfoInCrtPage.removeAll(stopPathSet);
                // 维护数据结构，判断是不是已经找到了路径
                for(PageTemplateInfo.TransInfo next: allTransInfoInCrtPage){
                    List<PageTemplateInfo.TransInfo> nextActionList = new ArrayList<>(reachInfoFromSrc.second);
                    nextActionList.add(next);
                    for(int indexToReach: next.transRes){
                        if(indexToPathReachFromSrc.indexOfKey(indexToReach) >= 0 || stopNodeSet.contains(indexToReach)){
                            continue;
                        }
                        if(indexToPathCanReachDes.indexOfKey(indexToReach) >= 0){
                            // 找到结果
                            Pair<Integer, List<PageTemplateInfo.TransInfo>> p = indexToPathCanReachDes.get(indexToReach);
                            List<PageTemplateInfo.TransInfo> res = new ArrayList<>();
                            res.addAll(nextActionList);
                            res.addAll(p.second);
                            return res;
                        }
                        Pair<Integer, List<PageTemplateInfo.TransInfo>> newP = new Pair<>(indexToReach, nextActionList);
                        qFromSrc.add(newP);
                        indexToPathReachFromSrc.put(indexToReach, newP);
                    }
                }
            }

            Pair<Integer, List<PageTemplateInfo.TransInfo>> reachInfoForDes = qToDes.poll();
            List<PageTemplateInfo.TransInfo> allTransInfoCanReachCrt = crtAppInfo.desPageIndexToTransInfoList.get(reachInfoForDes.first);
            if(allTransInfoCanReachCrt != null){
                allTransInfoCanReachCrt.removeAll(stopPathSet);
                for(PageTemplateInfo.TransInfo prev: allTransInfoCanReachCrt){
                    List<PageTemplateInfo.TransInfo> prevActionList = new ArrayList<>();
                    prevActionList.add(prev);
                    prevActionList.addAll(reachInfoForDes.second);
                    int newIndexCanReachDes = prev.page.getIndex();
                    if(indexToPathCanReachDes.indexOfKey(newIndexCanReachDes) >= 0 || stopNodeSet.contains(newIndexCanReachDes)){
                        continue;
                    }
                    if(indexToPathReachFromSrc.indexOfKey(newIndexCanReachDes) >= 0){
                        Pair<Integer, List<PageTemplateInfo.TransInfo>> p = indexToPathReachFromSrc.get(newIndexCanReachDes);
                        List<PageTemplateInfo.TransInfo> res = new ArrayList<>();
                        res.addAll(p.second);
                        res.addAll(prevActionList);
                        return res;
                    }
                    Pair<Integer, List<PageTemplateInfo.TransInfo>> newP = new Pair<>(newIndexCanReachDes, prevActionList);
                    qToDes.add(newP);
                    indexToPathCanReachDes.put(newP.first, newP);
                }
            }
        }
        return null;
    }

    /**
     * call back when jump according to list of {@link PageTemplateInfo.TransInfo} has finished
     */
    interface JumpResCallBack {
        /**
         *
         * @param successful true if all the steps in the given list has successfully finished
         * @param crtPageName finally achieved page name
         * @param successStep number of steps that has successfully executed.
         * @param crt the current focused trans info.
         * @param oriPath the original path
         * @param reason the fail reason. null if succeed
         */
        void onResult(boolean successful, String crtPageName, int successStep, PageTemplateInfo.TransInfo crt, List<PageTemplateInfo.TransInfo> oriPath, JumpFailReason reason);
    }

    /**
     * class indicating why execution failed
     */
    public static class JumpFailReason {
        enum Reason {
            PAGE_NOT_MATCHED, NODE_NOT_FOUND, ACTION_FAILED, PARA_NOT_FILLED, NULL_PATH
        }

        public Reason reason;
        /**
         * current page name. Only be set when {@code reason == PAGE_NOT_MATCHED}.
         */
        public String crtPageName;
        /**
         * page wanted to arrive. Only be set when {@code reason == PAGE_NOT_MATCHED}.
         */
        public String targetPageName;
        /**
         * id of the last node to inject event. Only be set when {@code reason == NODE_NOT_FOUND}.
         */
        public long actionVtId;
        /**
         * type of last injected event. Only be set when {@code reason == ACTION_FAILED}.
         */
        public PageTemplateInfo.TransInfo.Op actionType;

        /**
         * para name that not provided. Only be set when {@code reason == PARA_NOT_FILLED}.
         */
        public String paraNameNotFilled;

        public JumpFailReason setPageNotMatchInfo(String crt, String target){
            Utility.assertTrue(reason == null);
            reason = Reason.PAGE_NOT_MATCHED;
            crtPageName = crt;
            targetPageName = target;
            return this;
        }

        public JumpFailReason setNodeNotFound(long id){
            Utility.assertTrue(reason == null);
            reason = Reason.NODE_NOT_FOUND;
            actionVtId = id;
            return this;
        }

        public JumpFailReason setActionFailed(PageTemplateInfo.TransInfo.Op type){
            Utility.assertTrue(reason == null);
            reason = Reason.ACTION_FAILED;
            actionType = type;
            return this;
        }

        public JumpFailReason setParaNotFilled(String paraName){
            Utility.assertTrue(reason == null);
            reason = Reason.PARA_NOT_FILLED;
            paraNameNotFilled = paraName;
            return this;
        }

        public JumpFailReason setNullPath(){
            Utility.assertTrue(reason == null);
            reason = Reason.NULL_PATH;
            return this;
        }
    }

    private static class AutoJumpChangeListener implements RefreshThread.ContentChangeListener {
        private long storeId;
        private JumpResCallBack cb;
        private List<PageTemplateInfo.TransInfo> transInfoList;
        private boolean finished;
        private int crtStep;
        private Timer timerForPage;
        private Timer timerForNode;
        private Timer timerForNextPage;
        private Map<String, String> paraNameToRes;
        private PageTemplateInfo.TransInfo toExecInfo;
        private void cancelPageTimer(){
            if(timerForPage != null){
                timerForPage.cancel();
                timerForPage = null;
            }
        }
        private void cancelNodeTimer(){
            if(timerForNode != null){
                timerForNode.cancel();
                timerForNode = null;
            }
        }
        private void cancelNextPageTimer(){
            if(timerForNextPage != null){
                timerForNextPage.cancel();
                timerForNextPage = null;
            }
        }

        public AutoJumpChangeListener(List<PageTemplateInfo.TransInfo> transInfoList, JumpResCallBack cb, Map<String, String> paraNameToRes){
            this.storeId = RefreshThread.getInstance().storeCurrentListenersAndClear();
            this.cb = cb;
            this.transInfoList = new ArrayList<>(transInfoList);
            this.paraNameToRes = paraNameToRes;
            if(this.paraNameToRes == null){
                this.paraNameToRes = new HashMap<>();
            }
            crtStep = 0;
            toExecInfo = transInfoList.get(crtStep);
            execCrt(0, 0, 3000);
            if(!finished)
                RefreshThread.getInstance().bindContentChangeListener(this);
        }

        private void execCrt(){
            execCrt(3000, 1000, 3000);
        }

        private void execCrt(long waitUntilPageAppear, long waitUntilNodeAppear, long waitNextPage){
            cancelNextPageTimer();
            if(crtStep == transInfoList.size()){
                // successfully finish!
                finished = true;
                RefreshThread.getInstance().restoreListeners(storeId);
                cb.onResult(true, RefreshThread.getInstance().getCurrentPageName(), crtStep, null, transInfoList, null);
                return;
            }
            final String targetPageName = TemplateManager.getPageName(toExecInfo.page.getPackageName(), toExecInfo.page.getIndex());
            final String crtPageName = RefreshThread.getInstance().getCurrentPageName();
            // 在判断页面是不是相同的时候，应该考虑补充情况
            PageTemplateInfo crtPageInfo = RefreshThread.getInstance().getCurrentPageInfo();
            if(crtPageInfo == null || !crtPageInfo.getPageInfoAsSupplement().contains(toExecInfo.page)){
                if(timerForPage != null){ // 说明已经有一个计时器了，超时的情况交给那个计时器去处理
                    return;
                }
                TimerTask task = new TimerTask() {
                    @Override
                    public void run() {
                        cancelPageTimer();
                        finished = true;
                        RefreshThread.getInstance().restoreListeners(storeId);
                        cb.onResult(false, crtPageName, crtStep, toExecInfo, transInfoList, new JumpFailReason().setPageNotMatchInfo(crtPageName, targetPageName));
                    }
                };
                if(waitUntilPageAppear <= 0){
                    task.run();
                } else {
                    timerForPage = new Timer();
                    timerForPage.schedule(task, waitUntilPageAppear);
                }
                return;
            }
            cancelPageTimer();
            final Long actionVtId = toExecInfo.opVtId;
            AccessibilityNodeInfoRecord actNode = null;
            String textToEnter = "";
            if(actionVtId != null) {
                SelectorChain sc = toExecInfo.page.getSelectorChainByVtId(actionVtId);
                List<AccessibilityNodeInfoRecord> res = getNodesBySelectorChain(sc, Collections.<String, List<AccessibilityNodeInfoRecord>>emptyMap());
                for (AccessibilityNodeInfoRecord node : res) {
                    if ((!toExecInfo.usingPara) && toExecInfo.paraValue == null) {
                        actNode = node;
                        break;
                    }
                    String textToMatch = null;
                    if (!toExecInfo.usingPara) {
                        textToMatch = toExecInfo.paraValue;
                    } else {
                        if (!paraNameToRes.containsKey(toExecInfo.nlDesc)) {
                            cancelNodeTimer();
                            finished = true;
                            RefreshThread.getInstance().restoreListeners(storeId);
                            cb.onResult(false, crtPageName, crtStep, toExecInfo, transInfoList, new JumpFailReason().setParaNotFilled(toExecInfo.nlDesc));
                            return;
                        }
                        textToMatch = paraNameToRes.get(toExecInfo.nlDesc);
                    }

                    // todo: 没有办法处理需要参数定位输入框，然后再确定输入元素
                    if(toExecInfo.op != PageTemplateInfo.TransInfo.Op.ENTER_TEXT){
                        AccessibilityNodeInfoRecord referredNode = node;
                        if (toExecInfo.paraVtId != null) {
                            SelectorChain paraNodeSC = toExecInfo.page.getSelectorChainByVtId(toExecInfo.paraVtId);
                            Map<SelectorChain, List<AccessibilityNodeInfoRecord>> selectorChainListMap = new ArrayMap<>();
                            selectorChainListMap.put(sc, Collections.singletonList(node));
                            List<AccessibilityNodeInfoRecord> paraNodeRes = getNodesBySelectorChainUsingSCMap(paraNodeSC, selectorChainListMap);
                            if (paraNodeRes.size() > 1) {
                                Log.i("MultiParaNodeFound", "multi para node found. size = " + paraNodeRes.size());
                            }
                            if (paraNodeRes.isEmpty()) {
                                referredNode = null;
                            } else {
                                referredNode = paraNodeRes.get(0);
                            }
                        }

                        // 检查是否匹配
                        String text = (referredNode == null) ?
                                "" : referredNode.getAllTexts() + " " + referredNode.getAllContents();
                        if (text.contains(textToMatch)) {
                            actNode = node;
                            break;
                        }
                    } else {
                        actNode = node;
                        textToEnter = textToMatch;
                        break;
                    }
                }
                // 最终执行操作的时候一定要确定是处在"未完成"的状态
                if (actNode == null) {
                    if (timerForNode != null) {
                        return;
                    }

                    TimerTask task = new TimerTask() {
                        @Override
                        public void run() {
                            cancelNodeTimer();
                            finished = true;
                            RefreshThread.getInstance().restoreListeners(storeId);
                            cb.onResult(false, crtPageName, crtStep, toExecInfo, transInfoList, new JumpFailReason().setNodeNotFound(actionVtId));
                            return;
                        }
                    };

                    if (waitUntilNodeAppear <= 0) {
                        task.run();
                    } else {
                        timerForNode = new Timer();
                        timerForNode.schedule(task, waitUntilNodeAppear);
                    }
                    return;
                }
            }

            cancelNodeTimer();
            int actionInAccessibility = toExecInfo.op.getAccessibilityAction();
            String extraDataName = toExecInfo.op.getExtraValueName();
            boolean success = false;
            if(actionInAccessibility != -1 && actNode != null){
                if(extraDataName == null){
                    success = actNode.performAction(actionInAccessibility);
                } else {
                    Bundle bundle = new Bundle();
                    bundle.putCharSequence(extraDataName, textToEnter);
                    success = actNode.performAction(actionInAccessibility, bundle);
                }
            } else {
                int globalActionInAccessibility = toExecInfo.op.getGlobalAccessibilityAction();
                if(globalActionInAccessibility != -1){
                    success = HCITService.getInstance().performGlobalAction(globalActionInAccessibility);
                }
            }
            if(!success){
                finished = true;
                RefreshThread.getInstance().restoreListeners(storeId);
                cb.onResult(false, crtPageName, crtStep, toExecInfo, transInfoList, new JumpFailReason().setActionFailed(toExecInfo.op));
                return;
            }

            if(toExecInfo.next == null){
                crtStep += 1;
                toExecInfo = crtStep == transInfoList.size()? null: transInfoList.get(crtStep);
            } else {
                toExecInfo = toExecInfo.next;
            }
            TimerTask taskForNextPage = new TimerTask() {
                @Override
                public void run() {
                    execCrt();
                }
            };
            timerForNextPage = new Timer();
            timerForNextPage.schedule(taskForNextPage, waitNextPage);
        }

        public boolean isFinished() {
            return finished;
        }

        @Override
        public void onPageChange(String lastPageName, String newPageName) {
            execCrt();
        }

        @Override
        public void onPageUpdate(String currentPageName, Map<String, List<AccessibilityNodeInfoRecord>> changeTypeToNodeList) {
            execCrt();
        }

        @Override
        public void onUnknownPageContentChange(String lastPageName, Map<String, List<AccessibilityNodeInfoRecord>> changeTypeToNodeList) {
            execCrt();
        }
    }

    /**
     * Jump according to transInfoList
     * all listeners will be temporarily removed until the function finished (which means {@link JumpResCallBack#onResult(boolean, String, int, PageTemplateInfo.TransInfo, List, JumpFailReason)} is called).
     *
     * @param transInfoList transition path
     * @param cb callback
     * @param paraToRes provides value to a given parameter. Refer to {@link PageTemplateInfo.TransInfo} for more details
     */
    public static void jumpByTransInfoList(List<PageTemplateInfo.TransInfo> transInfoList, JumpResCallBack cb, Map<String, String> paraToRes){
        if(transInfoList == null){
            cb.onResult(false, RefreshThread.getInstance().getCurrentPageName(), 0, null, null, new JumpFailReason().setNullPath());
            return;
        } else if(transInfoList.isEmpty()) {
            cb.onResult(true, RefreshThread.getInstance().getCurrentPageName(), 0, null, transInfoList, null);
            return;
        }
        AutoJumpChangeListener listener = new AutoJumpChangeListener(transInfoList, cb, paraToRes);
        return;
    }

}
