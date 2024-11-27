package com.pos.demosdk;

import android.app.Application;

import com.common.apiutil.util.SDKUtil;

/**
 * Created by Ray.
 * <p>
 * Date: 2024/1/4
 * <p>
 * Description:
 */
public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        //init common sdk
        SDKUtil.getInstance(this).initSDK();
    }
}
