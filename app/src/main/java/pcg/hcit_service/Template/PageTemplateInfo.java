package pcg.hcit_service.Template;

import android.accessibilityservice.AccessibilityService;
import android.util.ArrayMap;
import android.util.Log;
import android.util.LongSparseArray;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import pcg.hcit_service.Selector.NodeSelector;
import pcg.hcit_service.Selector.OperationSelector;
import pcg.hcit_service.Selector.SelectorChain;
import pcg.hcit_service.Selector.SelectorChainRefSelector;
import pcg.hcit_service.Selector.SelectorChainRefSelectorForDiff;
import pcg.hcit_service.Utility;

/**
 * Describe page.
 */
public class PageTemplateInfo {
    public static class TransInfo{
        public enum Op{
            CLICK, GLOBAL_BACK, ENTER_TEXT, SCROLL_FORWARD, SCROLL_BACKWARD, LONG_CLICK, UNKNOWN;
            public static Op valueFromName(String name){
                if(Objects.equals(name, "点击")){
                    return CLICK;
                } else if(Objects.equals(name, "返回")){
                    return GLOBAL_BACK;
                } else if(Objects.equals(name, "输入")){
                    return ENTER_TEXT;
                } else if(Objects.equals(name, "滚动1")
                        || Objects.equals(name, "滚动-1")
                        || Objects.equals(name, "向前滚动")){
                    return SCROLL_BACKWARD;
                } else if(Objects.equals(name, "滚动0")
                        || Objects.equals(name, "滚动-0")
                        || Objects.equals(name, "向下滚动")){
                    return SCROLL_FORWARD;
                } else if(Objects.equals(name, "长按")){
                    return LONG_CLICK;
                }
                return UNKNOWN;
            }
            public int getAccessibilityAction(){
                switch (this){
                    case CLICK:
                        return AccessibilityNodeInfo.ACTION_CLICK;
                    case ENTER_TEXT:
                        return AccessibilityNodeInfo.ACTION_SET_TEXT;
                    case SCROLL_FORWARD:
                        return AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
                    case SCROLL_BACKWARD:
                        return AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
                    case LONG_CLICK:
                        return AccessibilityNodeInfo.ACTION_LONG_CLICK;
                    default:
                        return -1;

                }
            }

            public int getGlobalAccessibilityAction(){
                switch (this){
                    case GLOBAL_BACK:
                        return AccessibilityService.GLOBAL_ACTION_BACK;
                    default:
                        return -1;

                }
            }

            public String getExtraValueName(){
                switch (this){
                    case ENTER_TEXT:
                        return AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE;
                    default:
                        return null;
                }
            }

        }

        public Long opVtId;
        public Op op;
        public Long paraVtId;
        public boolean usingPara;
        public String nlDesc;
        public String paraValue;
        public PageTemplateInfo page;
        public List<Integer> transRes;

        public TransInfo next;
        public TransInfo prev;
        public long nextId;
        public long controllerIdWhenDesign;

        public TransInfo(JSONObject obj, PageTemplateInfo page) throws JSONException {
            this.page = page;
            opVtId = Utility.convert(obj.get("action_vt_id"));
            op = Op.valueFromName(obj.getString("action_type"));
            paraVtId = Utility.convert(obj.get("para_vt_id"));
            String paraDesc = Utility.convert(obj.getString("para_desc"));
            if(paraDesc.startsWith("-")){
                paraDesc = paraDesc.substring(1);
            }
            if(paraDesc == null || Objects.equals(paraDesc, "null")){
                paraDesc = "";
            }
            if(paraDesc.isEmpty()){
                usingPara = false;
                nlDesc = paraValue = null;
            } else {
                String[] splitRes = paraDesc.split("-");
                if(splitRes.length == 1){
                    usingPara = true;
                    nlDesc = splitRes[0];
                    paraValue = null;
                } else if(splitRes.length > 1){
                    if(Objects.equals(splitRes[0], "可变")){
                        usingPara = true;
                        nlDesc = splitRes[1];
                        paraValue = null;
                    } else if(Objects.equals(splitRes[0], "不可变")){
                        usingPara = false;
                        nlDesc = null;
                        paraValue = splitRes[1];
                    } else {
                        Log.w("TransInfo", "Unknown para type " + splitRes[0]);
                        usingPara = true;
                        nlDesc = splitRes[1];
                        paraValue = null;
                    }
                }
            }
            transRes = new ArrayList<>();
            JSONArray transResJson = obj.getJSONArray("trans_res");
            for(int i = 0; i < transResJson.length(); ++ i){
                transRes.add(transResJson.getInt(i));
            }

            nextId = obj.getLong("next_trans_con_id");
            controllerIdWhenDesign = obj.getLong("con_id");
        }

        public TransInfo assignLinkInfo(Map<Long, TransInfo> controllerIdToInfo){
            if(nextId != -1){
                next = controllerIdToInfo.get(nextId);
                if(next != null){
                    if(next.prev != null){
                        Log.w("PrevExist", "assignLinkInfo next already has a prev");
                    } else {
                        next.prev = this;
                    }
                }
                return next;
            }
            return null;
        }
    }


    private Map<Long, SelectorChain> idToSelectorChain;
    private Map<Long, Long> vtIdToSelectorChainId;
    private Map<String, Long> nodePathIdToSelectorChainId;

    private String activityName;
    private int pageBelongsToId;
    private PageTemplateInfo pageBelongsTo;
    private int index;
    private String packageName;

    private List<PageTemplateInfo> pageInfoAsSupplement;
    private Map<Long, List<TransInfo>> vtIdToTransInfoList;

    public PageTemplateInfo(JSONObject obj, JSONObject pageInfoObj, String packageName) throws JSONException {
        this.packageName = packageName;
        index = pageInfoObj.getInt("index");
        idToSelectorChain = new TreeMap<>();
        JSONObject selectorChainIdToSelectorChainInfo = obj.getJSONObject("selector_chain_id_to_selector_chain_info");
        Iterator<String> selectorChainIdIter = selectorChainIdToSelectorChainInfo.keys();
        while (selectorChainIdIter.hasNext()){
            String crtId = selectorChainIdIter.next();
            JSONObject selectorChainInfo = selectorChainIdToSelectorChainInfo.getJSONObject(crtId);
            SelectorChain crtSelectorChain = new SelectorChain(selectorChainInfo);
            Utility.assertTrue(crtSelectorChain.identifier == Long.valueOf(crtId));
            idToSelectorChain.put(crtSelectorChain.identifier, crtSelectorChain);
        }

        for(SelectorChain chain: idToSelectorChain.values()){
            for(NodeSelector selector: chain.selectors){
                if(selector instanceof SelectorChainRefSelector){
                    ((SelectorChainRefSelector) selector).assignSelectorChain(idToSelectorChain);
                } else if(selector instanceof OperationSelector){
                    ((OperationSelector) selector).assignSelectorChain(idToSelectorChain);
                } else if(selector instanceof SelectorChainRefSelectorForDiff){
                    ((SelectorChainRefSelectorForDiff) selector).assignSelectorChain(idToSelectorChain);
                }
            }
        }

        vtIdToSelectorChainId = new ArrayMap<>();
        JSONObject vtIdToSelectorChainIdObj = obj.getJSONObject("vt_id_to_selector_chain_id");
        Iterator<String> vtIdIter = vtIdToSelectorChainIdObj.keys();
        while (vtIdIter.hasNext()){
            String crtId = vtIdIter.next();
            String selectorChainId = vtIdToSelectorChainIdObj.getString(crtId);
            vtIdToSelectorChainId.put(Long.valueOf(crtId), Long.valueOf(selectorChainId));
            if(!idToSelectorChain.containsKey(Long.valueOf(selectorChainId))){
                Log.w("CannotLocate", "PageTemplateInfo: pageIndex: " + index + " vt id: " + crtId + " sc id (not found): " + selectorChainId);
            }
        }

        nodePathIdToSelectorChainId = new TreeMap<>();
        JSONObject nodePathIdToVtIdObj = obj.getJSONObject("node_path_id_to_vt_id");
        Iterator<String> nodePathIter = nodePathIdToVtIdObj.keys();
        while (nodePathIter.hasNext()){
            String crtNodePathId = nodePathIter.next();
            String vtId = nodePathIdToVtIdObj.getString(crtNodePathId);
            nodePathIdToSelectorChainId.put(crtNodePathId, Long.valueOf(vtId));
        }

        activityName = pageInfoObj.getString("activity_name");
        pageBelongsToId = pageInfoObj.getInt("belongs_to_index");
        if(pageBelongsToId == -1 || pageBelongsToId == this.index){
            pageBelongsTo = this;
            pageInfoAsSupplement = new ArrayList<>();
            pageInfoAsSupplement.add(this);
        }

        vtIdToTransInfoList = new HashMap<>();
        JSONObject vtIdToTransInfoListJson = obj.getJSONObject("vt_id_to_trans_info_list");
        vtIdIter = vtIdToTransInfoListJson.keys();

        ArrayMap<Long, TransInfo> conIdToTransInfo = new ArrayMap<>(); // to configure 'next'
        while (vtIdIter.hasNext()){
            String vtIdStr = vtIdIter.next();
            Long vtId = vtIdStr.equals("null") ? null: Long.valueOf(vtIdStr);
            JSONArray transInfoListJsonArray = vtIdToTransInfoListJson.getJSONArray(vtIdStr);
            for(int i = 0; i < transInfoListJsonArray.length(); ++ i){
                JSONObject transInfoJson = transInfoListJsonArray.getJSONObject(i);
                TransInfo info = new TransInfo(transInfoJson, this);
                Utility.assertTrue(Objects.equals(vtId, info.opVtId));
                if(!vtIdToSelectorChainId.containsKey(vtId)){
                    Log.w("CannotLocate", "PageTemplateInfo: cannot locate. page index: " + index + " vt id: " + vtId);
                    continue;
                }
                if(!vtIdToTransInfoList.containsKey(vtId)){
                    vtIdToTransInfoList.put(vtId, new ArrayList<TransInfo>());
                }
                vtIdToTransInfoList.get(vtId).add(info);
                conIdToTransInfo.put(info.controllerIdWhenDesign, info);
            }
        }

        JSONObject conIdToTransInfoJson = obj.getJSONObject("con_id_to_trans_info");
        Iterator<String> conIdIt = conIdToTransInfoJson.keys();
        while (conIdIt.hasNext()){
            String crtConIdStr = conIdIt.next();
            long crtConId = Long.parseLong(crtConIdStr);
            if(conIdToTransInfo.containsKey(crtConId)){
                continue;
            }
            JSONObject crtTransInfoObj = conIdToTransInfoJson.getJSONObject(crtConIdStr);
            TransInfo info = new TransInfo(crtTransInfoObj, this);
            conIdToTransInfo.put(crtConId, info);
        }

        for(long conId: conIdToTransInfo.keySet()){
            TransInfo transInfo = conIdToTransInfo.get(conId);
            if(transInfo == null){
                continue;
            }
            TransInfo next = transInfo.assignLinkInfo(conIdToTransInfo);
            if(next != null){
                long nextVtId = next.opVtId;
                if(vtIdToTransInfoList.containsKey(nextVtId)){
                    vtIdToTransInfoList.get(nextVtId).remove(next);
                    if(vtIdToTransInfoList.get(nextVtId).isEmpty()){
                        vtIdToTransInfoList.remove(nextVtId);
                    }
                }
            }
        }
    }

    public int getPageBelongsToId() {
        return pageBelongsToId;
    }

    public PageTemplateInfo getPageBelongsTo() {
        return pageBelongsTo;
    }

    public String getActivityName() {
        return activityName;
    }

    public int getIndex() {
        return index;
    }

    public void updatePageBelongsTo(PageTemplateInfo mainPage){
        if(mainPage.index != this.index
                && mainPage.index == this.pageBelongsToId
                && mainPage.pageInfoAsSupplement != null){
            Utility.assertTrue(!mainPage.pageInfoAsSupplement.contains(this));
            mainPage.pageInfoAsSupplement.add(this);
            this.pageBelongsTo = mainPage;
        }
    }

    public List<PageTemplateInfo> getPageInfoAsSupplement() {
        return pageInfoAsSupplement;
    }

    public Map<Long, SelectorChain> getIdToSelectorChain() {
        return idToSelectorChain;
    }

    public String getPackageName() {
        return packageName;
    }

    public Map<Long, List<TransInfo>> getVtIdToTransInfoList() {
        return vtIdToTransInfoList;
    }

    public SelectorChain getSelectorChainByVtId(long vtId){
        return idToSelectorChain.get(vtIdToSelectorChainId.get(vtId));
    }
}
