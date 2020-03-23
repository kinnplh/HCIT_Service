package pcg.hcit_service;

import android.util.Log;

import java.util.Collections;
import java.util.List;

import pcg.hcit_service.Template.PageTemplateInfo;

/**
 * This class provides methods for the test of the platform.
 * They are very possible to be useless to you.
 */
public class TestUtility {
    public static void test(){
        testCalPath();
    }

    private static void testCalPath(){
        List<PageTemplateInfo.TransInfo> res = NodeAccessController.calTransitionPath("com.eg.android.AlipayGphone", 0, 151, Collections.<PageTemplateInfo.TransInfo>emptySet(), Collections.<Integer>emptySet());
        Log.i("TestTransitionController", "init: 0->151 " +  (res == null? "inf" : res.size()));
        List<PageTemplateInfo.TransInfo> res2 = NodeAccessController.calTransitionPath("com.eg.android.AlipayGphone", 0, 70, Collections.<PageTemplateInfo.TransInfo>emptySet(), Collections.<Integer>emptySet());
        Log.i("TestTransitionController", "init: 0->70 " + (res2 == null? "inf" : res2.size()));
    }
}
