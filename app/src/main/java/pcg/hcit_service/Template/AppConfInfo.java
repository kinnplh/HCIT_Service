package pcg.hcit_service.Template;

import android.renderscript.ScriptC;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import pcg.hcit_service.Selector.SelectorChain;

/**
 * read and store the conf res from the platform
 */
public class AppConfInfo {
    private Map<Integer,String > pageIndexToAlias;
    private Map<String, Integer> aliasToPageIndex;
    private Map<String, Integer> nodeAliasToPageIndex;
    private Map<String, SelectorChain> nodeAliasToSelectorChain;
    private Map<SelectorChain, String> selectorChainToNodeAlias;
    private String packageName;
    public AppConfInfo(JSONObject obj) throws JSONException {
        packageName = obj.getString("app_name");
        pageIndexToAlias = new TreeMap<>();
        aliasToPageIndex = new HashMap<>();
        JSONObject pageIndexToAliasObj  = obj.getJSONObject("pageIndexToPageAlias");
        Iterator<String> pageIndexIter = pageIndexToAliasObj.keys();
        while (pageIndexIter.hasNext()){
            String pageIndexStr = pageIndexIter.next();
            int pageIndex = -1;
            try {
                pageIndex = Integer.valueOf(pageIndexStr);
            } catch (NumberFormatException e){
                continue;
            }
            String alias = pageIndexToAliasObj.getString(pageIndexStr);
            pageIndexToAlias.put(pageIndex, alias);
            aliasToPageIndex.put(alias, pageIndex);
        }

        nodeAliasToPageIndex = new HashMap<>();
        nodeAliasToSelectorChain = new HashMap<>();
        selectorChainToNodeAlias = new HashMap<>();
        JSONObject pageIndexToScIdToAliasJson = obj.getJSONObject("pageIndexToScIdToAlias");
        pageIndexIter = pageIndexToScIdToAliasJson.keys();
        while (pageIndexIter.hasNext()){
            String pageIndexStr = pageIndexIter.next();
            int pageIndex = Integer.valueOf(pageIndexStr);
            PageTemplateInfo crtPageInfo = TemplateManager.packageNameToPageIndexAndInfo.get(packageName).get(pageIndex);

            JSONObject scIdToAlias = pageIndexToScIdToAliasJson.getJSONObject(pageIndexStr);
            Iterator<String> scIdIter = scIdToAlias.keys();
            while (scIdIter.hasNext()){
                String scIdStr = scIdIter.next();
                long scId = Long.valueOf(scIdStr);
                String alias = scIdToAlias.getString(scIdStr);
                SelectorChain sc = crtPageInfo.getIdToSelectorChain().get(scId);
                nodeAliasToPageIndex.put(alias, pageIndex);
                nodeAliasToSelectorChain.put(alias, sc);
                selectorChainToNodeAlias.put(sc, alias);
            }
        }
    }

    public List<String> getAllAlias(){
        return new ArrayList<>(nodeAliasToSelectorChain.keySet());
    }

    public SelectorChain getSCByNodeAlias(String alias){
        return nodeAliasToSelectorChain.get(alias);
    }

    public String getAliasBySelectorChain(SelectorChain sc){
        return selectorChainToNodeAlias.get(sc);
    }

    public int getPageIndexByNodeAlias(String alias){
        Integer pageIndex = nodeAliasToPageIndex.get(alias);
        return pageIndex == null? -1: pageIndex;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getPageAliasByIndex(int index){
        if(pageIndexToAlias.containsKey(index)){
            return pageIndexToAlias.get(index);
        }

        PageTemplateInfo pageTemplateInfo = TemplateManager.getPageTemplateInfoByPackageNameAndPageIndex(packageName, index);
        if(pageTemplateInfo != null){
            PageTemplateInfo main = pageTemplateInfo.getPageBelongsTo();
            if(main != null){
                if(pageIndexToAlias.containsKey(main.getIndex())){
                    return pageIndexToAlias.get(main.getIndex());
                } else {
                    return packageName + '-' + main.getIndex();
                }
            }
        }

        return packageName + '-' + index;
    }

    public int getPageIndexByPageAlias(String pageName){
        Integer res = aliasToPageIndex.get(pageName);
        return res == null? -1: res;
    }
}
