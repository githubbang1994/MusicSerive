package cn.dongha.ido.ui.services;

import android.Manifest;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.support.v4.content.ContextCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.veryfit.multi.config.Constants;
import com.veryfit.multi.nativedatabase.FindPhoneOnOff;
import com.veryfit.multi.nativedatabase.NoticeOnOff;
import com.veryfit.multi.nativeprotocol.ProtocolEvt;
import com.veryfit.multi.nativeprotocol.ProtocolUtils;
import com.veryfit.multi.util.DebugLog;

import cn.dongha.ido.MyApplication;
import cn.dongha.ido.common.base.BaseCallBack;
import cn.dongha.ido.common.utils.AppSharedPreferencesUtils;
import cn.dongha.ido.common.utils.PhoneUtil;
import cn.dongha.ido.common.utils.SMSPhoneUtil;
import cn.dongha.ido.common.utils.SPUtils;
import cn.dongha.ido.ui.device.IntelligentRemindActivity;

/**
 * 辅助类service，监听来电服务
 *
 * @author Administrator
 */
public class AssistService extends Service {

    private static final String ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";
    Vibrator mVib;
    private boolean hasFirstReigsterPhone;
    private SmsObserver smsObserver;
    private ProtocolUtils protocolUtils = ProtocolUtils.getInstance();
    private boolean isCommingPhone = false;
    private boolean isRemind = false;

    /**
     * 所有的短信
     */
    private Uri SMS_URI = Uri.parse("content://sms/");


    /**
     * 收件箱短信
     */
    private Uri SMS_INBOX = Uri.parse("content://sms/inbox");
    private boolean isRingOrVibrate = true;    //是否响铃或振动
    private TelephonyManager tpm;
    private MyPhoneStateListener phoneStateListener;
    private Handler handler = new Handler();
    private long exitTime = 0;
    private MediaPlayer mMediaPlayer;
    Runnable vibrateAndMediaRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRingOrVibrate) {
                if (System.currentTimeMillis() - exitTime >= (30 * 1000)) {
                    if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
                        mMediaPlayer.stop();
                        mMediaPlayer.release();
                        mMediaPlayer = null;
                    }
                    if (mVib != null) {
                        mVib.cancel();
                        mVib = null;
                    }
                }
                handler.postDelayed(this, 1000);
            } else {
                handler.removeCallbacks(this);
            }
        }
    };
    // 通过音量键来停止寻找手机的铃声和振动
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_VOLUME_CHANGED)) {
                //通过音量键来停止寻找手机的铃声和振动
                isRingOrVibrate = false;
                try {
                    if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
                        mMediaPlayer.stop();
                        mMediaPlayer.release();
                        mMediaPlayer = null;
                    }
                    if (mVib != null && mVib.hasVibrator()) {
                        mVib.cancel();
                        mVib = null;
                    }
                } catch (Exception e) {

                }
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        DebugLog.d("onCreate ");

        protocolUtils.setProtocalCallBack(baseCallBack);

        smsObserver = new SmsObserver(this, handler);
        getContentResolver()
                .registerContentObserver(SMS_URI, true, smsObserver);

        tpm = (TelephonyManager) this
                .getSystemService(Context.TELEPHONY_SERVICE);
        phoneStateListener = new MyPhoneStateListener();

        registerPhoneListener();

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_VOLUME_CHANGED);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        filter.addAction(Intent.ACTION_MEDIA_BUTTON);
        registerReceiver(receiver, filter);

    }

    private BaseCallBack baseCallBack = new BaseCallBack() {
        @Override
        public void onSysEvt(int evt_base, int evt_type, int error, int value) {
            super.onSysEvt(evt_base, evt_type, error, value);

            if (evt_type == ProtocolEvt.BLE_TO_APP_FIND_PHONE_START.toIndex()) {//收到手环命令--开始寻找手机
                if (error == ProtocolEvt.SUCCESS) {
                    DebugLog.d("收到手环命令--开始寻找手机设置成功");
                    FindPhoneOnOff findPhoneOnOff = protocolUtils.getFindPhone();
                    if (findPhoneOnOff != null && findPhoneOnOff.getOnOff()) {
                        isRingOrVibrate = true;
                        playRingtone(true);
                    } else {

                    }
                } else {
                    DebugLog.d("收到手环命令--开始寻找手机设置失败");
                }
            } else if (evt_type == ProtocolEvt.BLE_TO_APP_FIND_PHONE_STOP.toIndex()) {//收到手环命令--停止寻找手机
                if (error == ProtocolEvt.SUCCESS) {
                    DebugLog.d("收到手环命令--停止寻找手机设置成功");
                } else {
                    DebugLog.d("收到手环命令--停止寻找手机设置失败");
                }
            } else if (evt_type == ProtocolEvt.SET_NOTICE_CALL.toIndex()) {//来电提醒的回调
                if (error == ProtocolEvt.SUCCESS) {
                    DebugLog.d("来电提醒设置成功");
                } else {
                    DebugLog.d("来电提醒设置失败");
                }
            } else if (evt_type == ProtocolEvt.BLE_TO_APP_PHONE_ANSWER.toIndex()) {//接听电话的回调
                DebugLog.d("接听电话的回调");
                PhoneUtil.answerRingingCall(getApplicationContext());
            } else if (evt_type == ProtocolEvt.BLE_TO_APP_PHONE_REJECT.toIndex()) {//拒接电话的回调
                DebugLog.d("拒接电话的回调");
                PhoneUtil.endCall(getApplicationContext());
            }
        }
    };

    private void playRingtone(boolean isVibrate) {
        handler.removeCallbacks(vibrateAndMediaRunnable);

        if (mMediaPlayer == null) {
            mMediaPlayer = new MediaPlayer();
        }

        if (mVib == null && isVibrate) {
            mVib = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);
        }

        boolean isPlaying = false;
        try {
            isPlaying = mMediaPlayer.isPlaying();
        } catch (Exception e) {
            mMediaPlayer = null;
            mMediaPlayer = new MediaPlayer();
        }

        if (isPlaying) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mMediaPlayer = new MediaPlayer();
        }

        try {
            Uri uri = getSystemDefultRingtoneUri();// mAppSharedPreferences.getRingtoneUrl();
            // mMediaPlayer.reset();
            mMediaPlayer.setDataSource(this, uri);
            mMediaPlayer.setLooping(true);
            mMediaPlayer.prepare();
            //开始计时
            exitTime = System.currentTimeMillis();
            mMediaPlayer.start();
            if (isVibrate) {
                mVib.vibrate(new long[]{500, 2000}, 0);
            }
            handler.postDelayed(vibrateAndMediaRunnable, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Uri getSystemDefultRingtoneUri() {
        return RingtoneManager.getActualDefaultRingtoneUri(this,
                RingtoneManager.TYPE_RINGTONE);
    }

    // TODO 监听电话状态改变事件
    private void registerPhoneListener() {
        DebugLog.d("电话监听事件，电话监听的开关：" + protocolUtils.getNotice().getCallonOff());
        // 创建一个监听对象，监听电话状态改变事件
        tpm.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    private void unregisterPhoneListener() {
        tpm.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
    }

    /**
     * 发送电话号码和联系人姓名
     */
    long time;
    private void sendData(final String phoneNumber, final String contactName) {
        DebugLog.d("sendData   phoneNumber: " + phoneNumber
                + " ---contactName:" + contactName);
        time =  System.currentTimeMillis();
        int delay = AppSharedPreferencesUtils.getInstance().getPhoneValue();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isRemind) {
                    protocolUtils.setCallEvt(contactName, phoneNumber);
                }
            }
        }, delay * 1000);


    }

    private boolean checkPhonebookPermission() {
        PackageManager pm = getPackageManager();
        boolean permission = (PackageManager.PERMISSION_GRANTED == pm
                .checkPermission("android.permission.READ_CONTACTS",
                        AssistService.this.getPackageName()));
        return permission;
    }
    long lastDate=-1;
    public void getSmsFromPhone() {
        DebugLog.i("getSmsFromPhone");
        StringBuffer SMSContent = new StringBuffer();
        String phoneNumber = "138";
        String contact = "438";
        int read = -1;
        long date = 0;
//        long currDate = System.currentTimeMillis();
        ContentResolver cr = getContentResolver();
        String body = "0438";
        String[] projection = new String[]{"body", "address", "person",
                "read", "date","_id"};
        //判断读取短信的权限
        if (ContextCompat.checkSelfPermission(MyApplication.getInstance(), Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 3);
            return;
        }
        Cursor cur = cr.query(SMS_INBOX, projection, "read=? and type=?",
                new String[]{"0", "1"}, "date desc limit 1");
        if (null == cur) {
            return;
        }
        boolean flag = false;
        int tempId=0;
        while (cur.moveToNext()) {
            phoneNumber = cur.getString(cur.getColumnIndex("address")); // 手机号
            tempId = cur.getInt(cur.getColumnIndex("_id")); // 手机号
            contact = cur.getString(cur.getColumnIndex("person")); // 联系人索引
            body = (cur.getString(cur.getColumnIndex("body"))); // 内容
            date = cur.getLong(cur.getColumnIndex("date"));
            read = cur.getInt(cur.getColumnIndex("read")); // 0:未读，1：已读

            flag = true;
            DebugLog.d("date:"+date);
            DebugLog.d("lastDate:"+lastDate);
            DebugLog.d("body:"+body);
        }
        cur.close();
        if (!flag) {
            DebugLog.d("没有未读短信.....");
            return;
        }
        if (lastDate==date){
            return;
        }else{
            lastDate=date;
        }
        long currDate = System.currentTimeMillis();
        DebugLog.d("date:"+date);
        DebugLog.d("currDate:"+currDate);
        DebugLog.d("ddd:"+((currDate - date) / 1000));
//        if ((currDate - date) / 1000 > 5) {
//            return;
//        }
//        if (contact == null) {
//            contact = SMSPhoneUtil.getContactNameFromPhoneBook(AssistService.this, phoneNumber);
//        }
//        if (contact.equalsIgnoreCase("0")) {
            contact = SMSPhoneUtil.getContactNameFromPhoneBook(AssistService.this, phoneNumber);
//        }
        if (contact.equals("")) {
            contact = phoneNumber;
        }
        if (contact.length() > 20) {
            contact = contact.substring(0, 20);
        }

        if (body.length() > 20) {
            body = body.substring(0, 20);
        }
        DebugLog.d("phoneNumber:" + phoneNumber + ",contact:" + contact + ",body:" + body);
        protocolUtils.setSmsEvt(Constants.MSG_TYPE_MSG, contact+":", phoneNumber, body);

    }

    private String phoneNumber;
    private String contactName;

    @Override
    public void onDestroy() {
        super.onDestroy();
        DebugLog.d("onDestroy......");
        unregisterPhoneListener();
        getContentResolver().unregisterContentObserver(smsObserver);
        protocolUtils.removeProtocalCallBack(baseCallBack);
    }

    private class MyPhoneStateListener extends PhoneStateListener {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            super.onCallStateChanged(state, incomingNumber);
            DebugLog.d("TelephonyManager   state = " + state
                    + " ---incomingNumber" + incomingNumber);

            phoneNumber = incomingNumber;
            contactName = SMSPhoneUtil.getContactNameFromPhoneBook(AssistService.this, phoneNumber);

            // 刚刚注册监听的时候，会调用一次onCallStateChange,需要屏蔽掉
            if (!hasFirstReigsterPhone) {
                hasFirstReigsterPhone = true;
                return;
            }

            // 判断来电提醒的开关是否开启
            if (!protocolUtils.getNotice().getCallonOff()) {
                return;
            }

            switch (state) {
                case TelephonyManager.CALL_STATE_IDLE: // 空闲【拒接电话】
                    DebugLog.d("空闲");
                    // 这边发命令设置手环，取消手环的提示
                    if (isCommingPhone) {
                        int delay = AppSharedPreferencesUtils.getInstance().getPhoneValue();
                       if((System.currentTimeMillis() - time)>delay*1000){
                        protocolUtils.stopCall();
                        isRemind = false;}
                    }
                    break;
                case TelephonyManager.CALL_STATE_RINGING: // 来电
                    DebugLog.d("来电");
                    isCommingPhone = true;
                    isRemind = true;
                    sendData(incomingNumber, contactName);
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK: // 摘机【接听来电】
                    DebugLog.d("摘机");
                    // 这边发命令设置手环，取消手环的提示
                    int delay = AppSharedPreferencesUtils.getInstance().getPhoneValue();
                    if((System.currentTimeMillis() - time)>delay*1000){
                        protocolUtils.stopCall();
                        isRemind = false;}
                    break;
            }
        }
    }

    class SmsObserver extends ContentObserver {
        public SmsObserver(Context context, Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            DebugLog.d("onChange..............selfChange:" + selfChange);

            // 每当有新短信到来时，使用我们获取短消息的方法
            NoticeOnOff onOff = protocolUtils.getNotice();
            boolean state= (boolean) SPUtils.get(IntelligentRemindActivity.INTELLIGENT_REMIND_STATE, false);
            DebugLog.d("总开关状态:"+state);
            if (!state){

                return;
            }
            if (onOff != null && onOff.getMsgonOff()) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            getSmsFromPhone();
                        }catch (Exception e){

                        }

                    }
                }, 3000);
            }
        }

        @Override
        public boolean deliverSelfNotifications() {
            return super.deliverSelfNotifications();
        }
    }

}
