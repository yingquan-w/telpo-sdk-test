package com.pos.demosdk.osapi;

import android.content.Context;
import android.os.Build;
import android.telephony.TelephonyManager;

import java.lang.reflect.Method;

/**
 * Created by Ray.
 * <p>
 * Date: 2024/4/2
 * <p>
 * Description:
 */
public class Util {



    public static String getSN(){
        return getProperties("ro.serialno","");
    }

    public static String getImei(){
        return getProperties("persist.sys.imei1","");
    }


    public static String getProperties(String key,String defaultvalue) {
        String value = "";
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class,String.class);
            value = (String) get.invoke(c, key,defaultvalue);//"ro.serialno"
        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;
    }
}
