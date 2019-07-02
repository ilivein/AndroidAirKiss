package com.ilivein.airkiss;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.ilivein.airkiss.core.AirKissEncoder;
import com.ilivein.airkiss.utils.DeviceUtils;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;


/**
 * @author ilivein
 * <p>
 * create at 2019/6/22 13:48
 */
public class MainActivity extends AppCompatActivity {

    private EditText ssidET, pwdET;
    private Subscription sendSubs, receiveSubs;
    private static final int REPLY_BYTE_CONFIRM_TIMES = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ssidET = findViewById(R.id.ssid);
        pwdET = findViewById(R.id.pwd);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkPermission(this)) {
            ssidET.setText(DeviceUtils.getSSID(this));
        }

//        ContentValues

        HashMap<String, String> stringHashMap = new HashMap<>();
//        stringHashMap.putAll();
    }

    /**
     * 一件配网按钮点击事件
     *
     * @param view
     */
    public void connect(View view) {
        final String ssid = ssidET.getText().toString().trim();
        final String password = pwdET.getText().toString().trim();
        if (TextUtils.isEmpty(ssid)) {//此处只需判断SSID空与否，支持未设置WiFi密码的配网操作
            Toast.makeText(this, "WiFi名称不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!DeviceUtils.isWifi(this)) {
            Toast.makeText(this, "未连接WiFi网络", Toast.LENGTH_SHORT).show();
            return;
        }
//        new AirKissTask(this, new AirKissEncoder(ssid, password)).execute();

        final AirKissEncoder airKissEncoder = new AirKissEncoder(ssid, password);
        sendSubs = Observable.create(new Observable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
                sendPackage(airKissEncoder, subscriber);
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<String>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        Toast.makeText(MainActivity.this, "连接失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onNext(String string) {

                    }
                });
        //接收udp包
        receiveSubs = Observable.create(new Observable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
                receivePackage(airKissEncoder, subscriber);
            }
        }).subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<String>() {
                    ProgressDialog mDialog = new ProgressDialog(MainActivity.this);

                    @Override
                    public void onStart() {
                        super.onStart();
                        mDialog.setMessage("正在连接...");
                        mDialog.setCancelable(false);
                        mDialog.show();
                    }

                    @Override
                    public void onCompleted() {
                        mDialog.dismiss();
                    }

                    @Override
                    public void onError(Throwable e) {
                        Toast.makeText(MainActivity.this, "连接失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        mDialog.dismiss();
                    }

                    @Override
                    public void onNext(String s) {
                        if ("success".equals(s)) {
                            Toast.makeText(MainActivity.this, "配网成功：", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void sendPackage(AirKissEncoder airKissEncoder, Subscriber subscriber) {
        byte[] DUMMY_DATA = new byte[1500];
        DatagramSocket sendSocket = null;
        try {
            sendSocket = new DatagramSocket();
            sendSocket.setBroadcast(true);
            int[] encoded_data = airKissEncoder.getEncodedData();
            for (int encoded_datum : encoded_data) {
                DatagramPacket pkg = new DatagramPacket(DUMMY_DATA,
                        encoded_datum,
                        InetAddress.getByName("255.255.255.255"),
                        10000);
                sendSocket.send(pkg);
                Thread.sleep(4);
            }
            subscriber.onCompleted();
        } catch (Exception e) {
            subscriber.onError(e);
            e.printStackTrace();
        } finally {
            sendSocket.close();
            sendSocket.disconnect();
        }
    }

    private void receivePackage(AirKissEncoder airKissEncoder, Subscriber subscriber) {
        byte[] buffer = new byte[15000];
        DatagramSocket udpServerSocket = null;
        char mRandomChar = airKissEncoder.getRandomChar();//获取UDP数据包中的随机字符
        try {
            int replyByteCounter = 0;
            udpServerSocket = new DatagramSocket(10000);
            udpServerSocket.setSoTimeout(1000 * 60);//设置超时时间
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            while (true) {
                udpServerSocket.receive(packet);
                byte[] receivedData = packet.getData();
                for (byte b : receivedData) {
                    if (b == mRandomChar)
                        replyByteCounter++;
                }
                if (replyByteCounter > REPLY_BYTE_CONFIRM_TIMES) {
                    subscriber.onNext("success");
                    break;
                }
            }
            subscriber.onCompleted();
        } catch (SocketException e) {
            subscriber.onError(e);
            e.printStackTrace();
        } catch (IOException e) {
            subscriber.onError(e);
            e.printStackTrace();
        } finally {
            udpServerSocket.close();
            udpServerSocket.disconnect();
        }
    }

    public void s(){

    }

    @Override
    protected void onDestroy() {
        if (sendSubs != null && sendSubs.isUnsubscribed()) {
            sendSubs.unsubscribe();
        }
        if (receiveSubs != null && receiveSubs.isUnsubscribed()) {
            receiveSubs.unsubscribe();
        }
        super.onDestroy();
    }

    //检查位置权限
    public boolean checkPermission(Activity context) {
        //9.0以前版本获取wifi ssid不用申请此权限
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return true;
        }
        int result = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (result == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        String[] permission = {Manifest.permission.ACCESS_COARSE_LOCATION};
        context.requestPermissions(permission, 1001);
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1001) {
            if (grantResults.length > 0 && PackageManager.PERMISSION_GRANTED == grantResults[0]) {
                if (DeviceUtils.isWifi(this)) {
                    ssidET.setText(DeviceUtils.getSSID(this));
                }
            } else {
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
                alertDialog.setTitle("权限拒绝");
                alertDialog.setMessage("请在设置中打开此应用的位置权限后重试");
                alertDialog.setCancelable(false);
                alertDialog.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                });
                alertDialog.show();
            }
        }
    }

    private class AirKissTask extends AsyncTask<Void, Void, Void> implements DialogInterface.OnDismissListener {
        private static final int PORT = 10000;
        private final byte[] DUMMY_DATA = new byte[1500];
        private static final int REPLY_BYTE_CONFIRM_TIMES = 5;
        private ProgressDialog mDialog;
        private Context mContext;
        private DatagramSocket mSocket;
        private char mRandomChar;
        private AirKissEncoder mAirKissEncoder;
        private volatile boolean mDone = false;

        AirKissTask(Activity activity, AirKissEncoder airKissEncoder) {
            mContext = activity;
            mDialog = new ProgressDialog(mContext);
            mDialog.setOnDismissListener(this);
            mRandomChar = airKissEncoder.getRandomChar();
            mAirKissEncoder = airKissEncoder;
        }

        @Override
        protected void onPreExecute() {
            this.mDialog.setMessage("Connecting :)");
            this.mDialog.show();

            new Thread(new Runnable() {
                public void run() {
                    byte[] buffer = new byte[15000];
                    try {
                        DatagramSocket udpServerSocket = new DatagramSocket(PORT);
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        int replyByteCounter = 0;
                        udpServerSocket.setSoTimeout(60000);
                        while (true) {
                            if (getStatus() == Status.FINISHED)
                                break;

                            try {
                                udpServerSocket.receive(packet);
                                byte[] receivedData = packet.getData();
                                for (byte b : receivedData) {
                                    if (b == mRandomChar)
                                        replyByteCounter++;
                                }

                                if (replyByteCounter > REPLY_BYTE_CONFIRM_TIMES) {
                                    mDone = true;
                                    break;
                                }
                            } catch (SocketTimeoutException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        udpServerSocket.close();
                    } catch (SocketException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }

        private void sendPacketAndSleep(int length) {
            try {
                DatagramPacket pkg = new DatagramPacket(DUMMY_DATA,
                        length,
                        InetAddress.getByName("255.255.255.255"),
                        PORT);
                mSocket.send(pkg);
                Thread.sleep(4);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                mSocket = new DatagramSocket();
                mSocket.setBroadcast(true);
            } catch (Exception e) {
                e.printStackTrace();
            }

            int[] encoded_data = mAirKissEncoder.getEncodedData();
            for (int i = 0; i < encoded_data.length; ++i) {
                sendPacketAndSleep(encoded_data[i]);
                if (i % 200 == 0) {
                    if (isCancelled() || mDone)
                        return null;
                }
            }
            return null;
        }

        @Override
        protected void onCancelled(Void params) {
            Toast.makeText(getApplicationContext(), "Air Kiss Cancelled.", Toast.LENGTH_LONG).show();
        }

        @Override
        protected void onPostExecute(Void params) {
            if (mDialog.isShowing()) {
                mDialog.dismiss();
            }
            String result;
            if (mDone) {
                result = "Air Kiss Successfully Done!";
            } else {
                result = "Air Kiss Timeout.";
            }
            Toast.makeText(getApplicationContext(), result, Toast.LENGTH_LONG).show();
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            if (mDone)
                return;
            this.cancel(true);
        }
    }
}
