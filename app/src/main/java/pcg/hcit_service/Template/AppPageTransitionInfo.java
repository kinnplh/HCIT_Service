package pcg.hcit_service.Template;

import android.annotation.SuppressLint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pcg.hcit_service.Utility;

public class AppPageTransitionInfo {
    public Map<Integer, List<PageTemplateInfo.TransInfo>> srcPageIndexToTransInfoList;
    public Map<Integer, List<PageTemplateInfo.TransInfo>> desPageIndexToTransInfoList;
    public String packageName;

    @SuppressLint("UseSparseArrays")
    public AppPageTransitionInfo(String packageName){
        srcPageIndexToTransInfoList = new HashMap<>();
        desPageIndexToTransInfoList = new HashMap<>();

        this.packageName = packageName;
    }

    public void addNewPage(PageTemplateInfo pageTemplateInfo){
        if(pageTemplateInfo == null){
            return;
        }
        int srcIndex = pageTemplateInfo.getIndex();
        Utility.assertTrue(!srcPageIndexToTransInfoList.containsKey(srcIndex));
        List<PageTemplateInfo.TransInfo> allInfo = new ArrayList<>();
        Map<Long, List<PageTemplateInfo.TransInfo>> vtIdToInfoList = pageTemplateInfo.getVtIdToTransInfoList();
        for(List<PageTemplateInfo.TransInfo> infoList: vtIdToInfoList.values()){
            allInfo.addAll(infoList);
        }

        srcPageIndexToTransInfoList.put(srcIndex, allInfo);
        for(PageTemplateInfo.TransInfo info: allInfo){
            for(int desIndex: info.transRes){
                if(!desPageIndexToTransInfoList.containsKey(desIndex)){
                    desPageIndexToTransInfoList.put(desIndex, new ArrayList<PageTemplateInfo.TransInfo>());
                }
                desPageIndexToTransInfoList.get(desIndex).add(info);
            }
        }

    }
}
