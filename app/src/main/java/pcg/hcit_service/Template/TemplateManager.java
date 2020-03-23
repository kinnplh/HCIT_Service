package pcg.hcit_service.Template;

import android.content.Context;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import pcg.hcit_service.AccessibilityNodeInfoRecord;
import pcg.hcit_service.HCITService;
import pcg.hcit_service.Selector.NodeSelector;
import pcg.hcit_service.Selector.SelectorChain;
import pcg.hcit_service.Selector.SelectorChainRefSelector;
import pcg.hcit_service.Utility;

public class TemplateManager {
    static Map<String, Map<Integer, PageTemplateInfo>> packageNameToPageIndexAndInfo;
    private static Map<String, Map<String, List<PageTemplateInfo>>> packageNameToActivityNameToCondResList;
    private static Map<String, AppConfInfo> packageNameToAppConf;
    private static Map<String, String> nodeAliasToPackageName;
    private static Map<String, AppPageTransitionInfo> packageNameToAppPageTransInfo;

    private static final String TAG = "TemplateManager";
    public static void clear(){
        packageNameToPageIndexAndInfo = null;
        packageNameToActivityNameToCondResList = null;
        packageNameToAppPageTransInfo = null;
    }

    public static void init(Context context) {
        // 载入数据
        packageNameToPageIndexAndInfo = new HashMap<>();
        packageNameToActivityNameToCondResList = new HashMap<>();
        nodeAliasToPackageName = new HashMap<>();
        packageNameToAppPageTransInfo = new HashMap<>();

        String appData = "app_data";
        try {
            String [] packageNames = context.getAssets().list(appData);
            if(packageNames == null){
                throw new IOException();
            }

            for(String packageName: packageNames){
                Map<Integer, PageTemplateInfo> idToPageSamePackage = new TreeMap<>();
                List<PageTemplateInfo> allPageAsSupplement = new ArrayList<>();

                String rootForApp = appData + '/' + packageName;
                String[] pageNames = context.getAssets().list(rootForApp);
                if(pageNames == null){
                    Log.w(TAG, rootForApp + " not exist");
                    throw new IOException();
                }
                AppPageTransitionInfo appPageTransitionInfo = new AppPageTransitionInfo(packageName);
                packageNameToAppPageTransInfo.put(packageName, appPageTransitionInfo);
                for(String pageName: pageNames){
                    if(!pageName.startsWith("Page")){
                        continue;
                    }
                    int pageIndex;
                    try {
                        pageIndex = Integer.valueOf(pageName.substring(4));
                    } catch (NumberFormatException e){
                        continue;
                    }

                    String rootForPage = rootForApp + '/' + pageName;
                    char [] buffer = new char[8 * 1024];
                    int length;

                    String pageInfoPath = rootForPage + "/PageInstance0_info.json";
                    InputStreamReader pageInfoStreamReader = new InputStreamReader(context.getAssets().open(pageInfoPath));
                    BufferedReader pageInfoBufferedReader = new BufferedReader(pageInfoStreamReader, 8 * 1024);
                    StringBuilder pageInfoBuilder = new StringBuilder();
                    while ((length = pageInfoBufferedReader.read(buffer)) != -1){
                        pageInfoBuilder.append(buffer, 0, length);
                    }
                    pageInfoBufferedReader.close();
                    pageInfoStreamReader.close();
                    JSONObject pageInfoObj = new JSONObject(pageInfoBuilder.toString());

                    String nodeSelectorInfoPath = rootForPage + "/PageInstance0_node_selector_info.json";
                    InputStreamReader nodeSelectorInfoStreamReader = new InputStreamReader(context.getAssets().open(nodeSelectorInfoPath));
                    BufferedReader nodeSelectorInfoBufferedReader = new BufferedReader(nodeSelectorInfoStreamReader, 8 * 1024);
                    StringBuilder nodeSelectorInfoBuilder = new StringBuilder();
                    while ((length = nodeSelectorInfoBufferedReader.read(buffer)) != -1){
                        nodeSelectorInfoBuilder.append(buffer, 0, length);
                    }
                    nodeSelectorInfoBufferedReader.close();
                    nodeSelectorInfoStreamReader.close();
                    JSONObject nodeSelectorInfo = new JSONObject(nodeSelectorInfoBuilder.toString());

                    PageTemplateInfo pageTemplateInfo = new PageTemplateInfo(nodeSelectorInfo, pageInfoObj, packageName);
                    Utility.assertTrue(pageTemplateInfo.getIndex() == pageIndex);
                    if(pageTemplateInfo.getPageBelongsToId() == -1){
                        idToPageSamePackage.put(pageTemplateInfo.getIndex(), pageTemplateInfo);
                    } else {
                        allPageAsSupplement.add(pageTemplateInfo);
                    }
                    appPageTransitionInfo.addNewPage(pageTemplateInfo);
                }

                // "page belongs to" complete
                for(PageTemplateInfo page: allPageAsSupplement){
                    PageTemplateInfo main = idToPageSamePackage.get(page.getPageBelongsToId());
                    if(main == null){
                        Log.w(TAG, packageName + " page " + page.getPageBelongsToId() + " not found");
                        continue;
                    }
                    page.updatePageBelongsTo(main);
                }
                Map<String, List<PageTemplateInfo>> activityNameToPageList = new HashMap<>();
                for(PageTemplateInfo page: idToPageSamePackage.values()){
                    String activityName  = page.getActivityName();
                    if(!activityNameToPageList.containsKey(activityName)){
                        activityNameToPageList.put(activityName, new ArrayList<PageTemplateInfo>());
                    }
                    if(page.getPageBelongsTo() == null || page.getPageBelongsTo() == page){
                        activityNameToPageList.get(activityName).add(page); // 只添加主页面
                    }
                }
                packageNameToActivityNameToCondResList.put(packageName, activityNameToPageList);
                packageNameToPageIndexAndInfo.put(packageName, idToPageSamePackage);

            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            Utility.toast(context, "Read app data failed", Toast.LENGTH_LONG);
        }

        // read data conf
        packageNameToAppConf  = new HashMap<>();
        try {
            String appConf = "app_conf";
            String [] confFileNames = context.getAssets().list(appConf);
            if(confFileNames == null){
                throw new IOException();
            }

            for(String confFileName: confFileNames){
                if(!confFileName.endsWith(".json")){
                    continue;
                }
                String crtPackageName = confFileName.substring(0, confFileName.lastIndexOf(".json"));
                InputStreamReader confStreamReader = new InputStreamReader(context.getAssets().open(appConf + "/" + confFileName));
                BufferedReader confBufferedReader = new BufferedReader(confStreamReader, 8 * 1024);
                int length;
                char[] buffer = new char[8 * 1024];
                StringBuilder confBuilder = new StringBuilder();
                while ((length = confBufferedReader.read(buffer)) != -1){
                    confBuilder.append(buffer, 0, length);
                }

                AppConfInfo info = new AppConfInfo(new JSONObject(confBuilder.toString()));
                Utility.assertTrue(Objects.equals(info.getPackageName(), crtPackageName));
                packageNameToAppConf.put(crtPackageName, info);
                for(String alias: info.getAllAlias()){
                    nodeAliasToPackageName.put(alias, crtPackageName);
                }
            }

        } catch (IOException | JSONException e){
            e.printStackTrace();
            Utility.toast(context, "Read app conf failed", Toast.LENGTH_LONG);
            return;
        }

    }

    private static Pair<Double, Pair<PageTemplateInfo, Utility.BidirectionalListMap<NodeSelector, AccessibilityNodeInfoRecord>>> getBestFit(AccessibilityNodeInfoRecord root, List<PageTemplateInfo> candidates){
        if (candidates.isEmpty()){
            return new Pair<>(0.0, new Pair<PageTemplateInfo, Utility.BidirectionalListMap<NodeSelector, AccessibilityNodeInfoRecord>>(null, null));
        }
        double maxFitRatio = -1;
        PageTemplateInfo bestFitTemplate = candidates.get(0);
        Utility.BidirectionalListMap<NodeSelector, AccessibilityNodeInfoRecord> nonRefForBest = null;

        for(PageTemplateInfo pageMain: candidates){
            // todo 考虑页面有补充的情况。采用更加有效的方式来完成
            Utility.BidirectionalListMap<NodeSelector, AccessibilityNodeInfoRecord> refForCrt = new Utility.BidirectionalListMap<>();
            HashSet<PageTemplateInfo> allPages = new HashSet<>(pageMain.getPageInfoAsSupplement());

            HashSet<AccessibilityNodeInfoRecord> allCreatedVariables = new HashSet<>();

            double fitRatio = 0.0;
            for(PageTemplateInfo page: allPages){
                double countSuccess = 0;
                HashSet<AccessibilityNodeInfoRecord> setForCrt = new HashSet<>(allCreatedVariables);
                boolean exitNotSatisfy = false;
                for(SelectorChain selector: page.getIdToSelectorChain().values()){
                    Pair<List<AccessibilityNodeInfoRecord>, Utility.BidirectionalListMap<NodeSelector, AccessibilityNodeInfoRecord>> resultForChain = selector.getNodesGivenRefCandidates(root, new Utility.BidirectionalListMapOverwrite<>(refForCrt));
                    if(resultForChain != null && resultForChain.first != null && resultForChain.first.size() > 0){
                        // countSuccess += (Math.log(1 + resultForChain.first.size()) / Math.log(2));
                        if(selector.inexistence){
                            countSuccess = 0; //  不允许找到
                            exitNotSatisfy = true;
                            break;
                        }
                        allCreatedVariables.addAll(resultForChain.first);

                        HashSet<AccessibilityNodeInfoRecord> tmp = new HashSet<>(setForCrt);
                        int sizeBefore = tmp.size();
                        tmp.addAll(resultForChain.first);
                        int sizeAfter = tmp.size();
                        int findNum = sizeAfter - sizeBefore;
                        countSuccess += (Math.log(1 + findNum) / Math.log(2));

                        if(resultForChain.second != null){
                            for(NodeSelector chain: resultForChain.second.getT1Key()){
                                if(chain instanceof SelectorChain){
                                    SelectorChain selectorChain = (SelectorChain) chain;
                                    if(selectorChain.selectors.isEmpty() || !(selectorChain.selectors.get(0) instanceof SelectorChainRefSelector)){
                                        refForCrt.put(chain, resultForChain.second.getT2List(chain));
                                    }
                                }
                            }
                        }
                    } else {
                        if(selector.needFindInPage){
                            countSuccess = 0;
                            exitNotSatisfy = true;
                            break;
                        }
                    }
                }
                if(exitNotSatisfy){
                    fitRatio = 0;
                    break;
                }
                fitRatio += countSuccess / page.getIdToSelectorChain().size();
            }

            if(fitRatio > maxFitRatio){
                bestFitTemplate = pageMain;
                maxFitRatio = fitRatio;
                nonRefForBest = refForCrt;
            }
        }

        Log.i("PageFitRes", "getPageIndexAndNonRefCache: page index = "
                + ((bestFitTemplate == null)? "unknown": bestFitTemplate.getIndex()) + " fit ratio " + maxFitRatio);

        return new Pair<>(maxFitRatio, new Pair<>(bestFitTemplate, nonRefForBest));
    }

    public static Pair<PageTemplateInfo, Utility.BidirectionalListMap<NodeSelector, AccessibilityNodeInfoRecord>> getPageIndexAndNonRefCache(AccessibilityNodeInfoRecord root, PageTemplateInfo last, double ratio){
        if(last != null){
            Pair<Double, Pair<PageTemplateInfo, Utility.BidirectionalListMap<NodeSelector, AccessibilityNodeInfoRecord>>> res = getBestFit(root, Collections.singletonList(last));
            if(res.first > (ratio + 1) / 2){
                return res.second;
            }
        }
        String packageName = root.getPackageName() == null? null: String.valueOf(root.getPackageName());
        if(!packageNameToActivityNameToCondResList.containsKey(packageName)){
            return null;
        }
        Map<String, List<PageTemplateInfo>> activityNameToRes = packageNameToActivityNameToCondResList.get(packageName);
        if(activityNameToRes ==  null){
            return null;
        }

        String activityName = HCITService.getInstance() == null? null: HCITService.getInstance().getTopActivityName();
        if(!activityNameToRes.containsKey(activityName)){
            if(!activityNameToRes.containsKey(null)){
                // return null;
                activityNameToRes.put(null, new ArrayList<PageTemplateInfo>());
            }
            activityName = null; // 如果这个activity name 没有涉及到到话，那么就从没有activity name 的那些开始找
        }

        List<PageTemplateInfo> candidates = new ArrayList<>(activityNameToRes.get(activityName));
        if(candidates.indexOf(last) > 0){
            candidates.remove(last);
            candidates.add(0, last);
        }

        Pair<Double, Pair<PageTemplateInfo, Utility.BidirectionalListMap<NodeSelector, AccessibilityNodeInfoRecord>>> resSameActivityName = getBestFit(root, candidates);
        if(resSameActivityName.first >= (ratio + 1) / 2){
            // 不检查其他activity name 不匹配的
            return resSameActivityName.second;
        } else {
            // 从不是同一个 activity name 里面找
            // 可能 activity name 刷新不及时
            int oldCandidatesSize = candidates.size();
            candidates.clear();
            for(String key: activityNameToRes.keySet()){
                if(!Objects.equals(key, activityName))
                    candidates.addAll(activityNameToRes.get(key));
            }


            Pair<Double, Pair<PageTemplateInfo, Utility.BidirectionalListMap<NodeSelector, AccessibilityNodeInfoRecord>>> resDiffActivityName =  getBestFit(root, candidates);
            if(resDiffActivityName.first >= (ratio + 1) / 2 || (resDiffActivityName.first > resSameActivityName.first * 10 && resDiffActivityName.first > ratio) || (oldCandidatesSize == 0 && resDiffActivityName.first  >= ratio)){
                return resDiffActivityName.second; // diff activity name 需要有更加苛刻条件
            } else {
                if(resSameActivityName.first >= ratio){
                    return resSameActivityName.second;
                }
                else return null;
            }
        }
    }

    public static String getPageName(String packageName, int index){
        if(!packageNameToAppConf.containsKey(packageName) || packageNameToAppConf.get(packageName) == null)
            return packageName + '-' + index;
        return packageNameToAppConf.get(packageName).getPageAliasByIndex(index);
    }

    public static int getPageIndexByname(String packageName, String pageName){
        if(pageName.startsWith(packageName + '-')){
            String prefix = packageName + '-';
            String pageIndexStr = pageName.substring(prefix.length());
            try {
                return Integer.valueOf(pageIndexStr);
            } catch (NumberFormatException ignore){}
        }
        if(!packageNameToAppConf.containsKey(packageName)){
            return -1;
        }
        AppConfInfo appConfInfo = packageNameToAppConf.get(packageName);
        if(appConfInfo == null){
            return -1;
        }
        return appConfInfo.getPageIndexByPageAlias(pageName);
    }

    public static AppConfInfo getAppConfInfoByPackageName(String packageName){
        return packageNameToAppConf.get(packageName);
    }

    public static Map<String, AppPageTransitionInfo> getPackageNameToAppPageTransInfo() {
        return packageNameToAppPageTransInfo;
    }

    public static PageTemplateInfo getPageTemplateInfoByPackageNameAndPageIndex(String packageName, int pageIndex){
        Map<Integer, PageTemplateInfo> pageIndexToInfo = packageNameToPageIndexAndInfo.get(packageName);
        if(pageIndexToInfo == null){
            return null;
        }
        return pageIndexToInfo.get(pageIndex);
    }
}
