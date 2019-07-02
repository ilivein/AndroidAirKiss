package com.ilivein.airkiss.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

public class DeviceUtils {

    /**
     * 隐藏软键盘
     * <p>
     * {@link InputMethodManager#hideSoftInputFromWindow}第一个参数如果使用getCurrentFocus.getWindowToken,在使用模拟器调试时，
     * 存在获取不到焦点的情况，故会出现空指针异常。
     *
     * @param view 任意一个当前layout的view
     */
    public static void hideKeyboard(View view) {
        InputMethodManager inputMethodManager = (InputMethodManager) view.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * 判断网络连接是否是WiFi，否则返回false
     */
    public static boolean isWifi(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            if (netInfo != null && netInfo.isConnected()) {
                return netInfo.getType() == ConnectivityManager.TYPE_WIFI;
            }
        }
        return false;
    }


    public static String getSSID(Context context) {
     String ssid = "unknown id";
     //android 8.1
     if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1){
         ConnectivityManager connManager = (ConnectivityManager) context.getApplicationContext().
                 getSystemService(Context.CONNECTIVITY_SERVICE);
         assert connManager != null;
         NetworkInfo networkInfo = connManager.getActiveNetworkInfo();
         if (networkInfo.isConnected()){
             Log.e("DeviceUtils", "getSSID111: "+ networkInfo.getExtraInfo() );
             if (networkInfo.getExtraInfo() != null){
                 return networkInfo.getExtraInfo().replace("\"","");
             }
         }
     }else {
         if (isWifi(context)){
             final WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
             WifiInfo connectionInfo = null;
             if (wifiManager != null) {
                 connectionInfo = wifiManager.getConnectionInfo();
             }
             if (connectionInfo != null) {
                 ssid = connectionInfo.getSSID();
                 Log.e("DeviceUtils", "getSSID: "+ssid );
                 if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                     ssid = ssid.replaceAll("^\"|\"$", "");
                 }
             }
         }
     }
     return ssid;
 }

    public static boolean isApkDebuggable(Context context){
        try {
            ApplicationInfo info = context.getApplicationInfo();
            return (info.flags & ApplicationInfo.FLAG_DEBUGGABLE)!=0;
        }catch (Exception e){
            e.printStackTrace();
        }
        return false;
    }
}
