package cn.dongha.ido.ui.services;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import com.veryfit.multi.config.Constants;
import com.veryfit.multi.nativedatabase.NoticeOnOff;
import com.veryfit.multi.nativeprotocol.ProtocolUtils;
import com.veryfit.multi.util.ByteDataConvertUtil;
import com.veryfit.multi.util.DebugLog;
import cn.dongha.ido.R;
import cn.dongha.ido.common.utils.AppSharedPreferencesUtils;
import cn.dongha.ido.common.utils.Constant;
import cn.dongha.ido.common.utils.SPUtils;
import cn.dongha.ido.ui.MainActivity;
import cn.dongha.ido.ui.device.IntelligentRemindActivity;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * TODO Description of class。
 * <p/>
 * TODO Detail Description
 * <p/>
 * TODO Sample Code
 * <pre>
 * </pre>
 *
 * @author wujie
 * @version VeryFit v.2.0 2016年1月27日
 * @since VeryFit v.2.0
 */

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class IntelligentNotificationService extends NotificationListenerService {

    private AppSharedPreferencesUtils share = AppSharedPreferencesUtils.getInstance();
    private ProtocolUtils protocolUtils = ProtocolUtils.getInstance();

    // 智能提醒命令
    public static final byte WECHAT = 0x03;
    public static final byte QQ = 0x04;
    public static final byte FACEBOOK = 0x06;
    public static final byte TWITTER = 0x07;
    public static final byte WHATSAPP = 0x08;
    public static final byte MESSENGER = 0x09;
    public static final byte INSTAGRAM = 0x0A;
    public static final byte LINKEDIN = 0x0B;
    //	CoreServiceProxy mCore = CoreServiceProxy.getInstance();
//	private IncomingMessage mSmsMessage = null;
    private byte[] mSmsContent = null;
    private int smsModLen = 0;
    private int smsIndex = 0;
    private String mTitle = null;
    private String mText = null;
    private byte mType;
//	private AppSharedPreferences share = AppSharedPreferences.getInstance();

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate() {
        super.onCreate();
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);
//        notification.contentIntent = contentIntent;
        Notification.Builder builder = new Notification.Builder(this);
        builder.setContentTitle("Foreground Service");
        builder.setContentText("Foreground Service Started.");
        builder.setContentIntent(contentIntent);
        builder.setWhen(System.currentTimeMillis());
        builder.setSmallIcon(R.drawable.ic_launcher);


        Notification notification = builder.build();
//        notification.setLatestEventInfo(this, "Foreground Service",
//                "Foreground Service Started.", contentIntent);
        // 注意使用  startForeground ，id 为 0 将不会显示 notification
        startForeground(0, notification);

        DebugLog.d("IntelligentNotificationService: onCreate()");

        //
//        ensureCollectorRunning();
    }

    private void ensureCollectorRunning() {
        ComponentName collectorComponent = new ComponentName(this, /*NotificationListenerService Inheritance*/ IntelligentNotificationService.class);
        DebugLog.d("ensureCollectorRunning collectorComponent: " + collectorComponent);
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        boolean collectorRunning = false;
        List<ActivityManager.RunningServiceInfo> runningServices = manager.getRunningServices(Integer.MAX_VALUE);
        if (runningServices == null) {
            DebugLog.d("ensureCollectorRunning() runningServices is NULL");
            return;
        }
        for (ActivityManager.RunningServiceInfo service : runningServices) {
            if (service.service.equals(collectorComponent)) {
                DebugLog.d("ensureCollectorRunning service - pid: " + service.pid + ", currentPID: " + android.os.Process.myPid() + ", clientPackage: " + service.clientPackage + ", clientCount: " + service.clientCount
                        + ", clientLabel: " + ((service.clientLabel == 0) ? "0" : "(" + getResources().getString(service.clientLabel) + ")"));
                if (service.pid == android.os.Process.myPid() /*&& service.clientCount > 0 && !TextUtils.isEmpty(service.clientPackage)*/) {
                    collectorRunning = true;
                }
            }
        }
        if (collectorRunning) {
            DebugLog.d("ensureCollectorRunning: collector is running");
            return;
        }
        DebugLog.d("ensureCollectorRunning: collector not running, reviving...");
        toggleNotificationListenerService();
    }

    private void toggleNotificationListenerService() {
        DebugLog.d("toggleNotificationListenerService() called");
        ComponentName thisComponent = new ComponentName(this, /*getClass()*/ IntelligentNotificationService.class);
        PackageManager pm = getPackageManager();
        pm.setComponentEnabledSetting(thisComponent, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        pm.setComponentEnabledSetting(thisComponent, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

    }

    @Override
    public IBinder onBind(Intent intent) {
        DebugLog.d("IntelligentNotificationService:onBind");
        return super.onBind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
        DebugLog.d("IntelligentNotificationService:onRebind");
        super.onRebind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopForeground(true);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {

        // 同步数据&实时心率&蓝牙断开连接等情况时不发送给手环
//		if(LibSharedPreferences.getInstance().isSyncData()) return;
//		if(AppSharedPreferences.getInstance().getIsRealTime()) return;
//		if(!mCore.isDeviceConnected()) return;
        DebugLog.d("onNotificationPosted ------->sbn:" + sbn);
        Notification notification = sbn.getNotification();
        NoticeOnOff noticeOnOff = ProtocolUtils.getInstance().getNotice();

        DebugLog.d("noticeOnOff:" + noticeOnOff);
        boolean state= (boolean) SPUtils.get(IntelligentRemindActivity.INTELLIGENT_REMIND_STATE,false);
        DebugLog.d("总开关状态:"+state);
        if (!state){

            return;
        }
        if (null != notification && noticeOnOff != null) {
            String pkgName = sbn.getPackageName();
//            DebugLog.d("p:" + pkgName + ",tickerText:" + notification.tickerText+","+notification.toString());
            DebugLog.d("notification:" + notification.toString());
//            String intelType = share.getIntelligentRemindSwitch();
//            String intelType = "123456789";
            if ("com.tencent.mm".equals(pkgName) && noticeOnOff.getWxonOff()) { // 微信
//                if ((noticeOnOff == null) || (noticeOnOff != null && noticeOnOff.getWxonOff())) {
                sendText(notification, (byte) Constants.MSG_TYPE_WX);
//                }
            } else if (("com.tencent.mobileqq".equals(pkgName)||"com.tencent.qqlite".equals(pkgName)) && noticeOnOff.getQQonOff()) { // QQ
                sendText(notification, (byte) Constants.MSG_TYPE_QQ);
            } else if ("com.facebook.katana".equals(pkgName) && noticeOnOff.getFacebookonOff()) { // Facebook
                sendText(notification, (byte) Constants.MSG_TYPE_FACEBOOK);
            } else if ("com.twitter.android".equals(pkgName) && noticeOnOff.getTwitteronOff()) { // Twitter
                sendText(notification, (byte) Constants.MSG_TYPE_TWITTER);
            } else if ("com.whatsapp".equals(pkgName) && noticeOnOff.getWhatsapponOff()) { // Whatsapp
                sendText(notification, (byte) Constants.MSG_TYPE_WHATSAPP);
            } else if ("com.linkedin.android".equals(pkgName) && noticeOnOff.getLinkedinonOff()) { // linkedin
                sendText(notification, (byte) Constants.MSG_TYPE_LINKEDIN);
            } else if ("com.instagram.android".equals(pkgName) && noticeOnOff.getInstagramonOff()) { // instagram
                sendText(notification, (byte) Constants.MSG_TYPE_INSTAGRAM);
            } else if ("com.facebook.orca".equals(pkgName) && noticeOnOff.getMessengeronOff()) { // messenger
                sendText(notification, (byte) Constants.MSG_TYPE_MESSENGER);
            } else if (getEmailPkgNames().contains(pkgName)&&noticeOnOff.getEmailonOff()) {//邮件提醒
                sendText(notification, (byte) Constants.MSG_TYPE_EMAIL);
            } else if (getCalendarPkgNames().contains(pkgName)&&noticeOnOff.getCalendaronOff()) { //日历提醒
                sendText(notification, (byte) Constants.MSG_TYPE_CALENDAR);
            } else if("com.kakao.talk".equals(pkgName)&&noticeOnOff.kakaoTalkOnOff){  //kakaotalk
                sendText(notification, (byte) Constants.MES_TYPE_KAKAO_TALK);
            } else if("com.viber.voip".equals(pkgName)&&noticeOnOff.viberOnOff){        //viber
                sendText(notification, (byte) Constants.MES_TYPE_VIBER);
            } else if("jp.naver.line.android".equals(pkgName)&&noticeOnOff.lineOnOff){  //line
                sendText(notification, (byte) Constants.MES_TYPE_LINE);
            } else if("com.vkontakte.android".equals(pkgName)&&noticeOnOff.vKontakteOnOff){ //vk
                sendText(notification, (byte) Constants.MES_TYPE_VK);
            }else if(("com.skype.raider".equals(pkgName)||"com.skype.rover".equals(pkgName))&&noticeOnOff.skype){ //skype
                sendText(notification, (byte) Constants.MSG_TYPE_SKEY);
            }else if("com.google.android.gm".equals(pkgName)&&noticeOnOff.gmailOnOff){ //gmail
                sendText(notification, (byte) Constants.MES_TYPE_GMAIL);
            }else if("com.microsoft.office.outlook".equals(pkgName)&&noticeOnOff.outLookOnOff){ //outlook
                sendText(notification, (byte) Constants.MES_TYPE_OUTLOOK);
            }else if("com.snapchat.android".equals(pkgName)&&noticeOnOff.snapchatOnOff) { //snapchat
                sendText(notification, (byte) Constants.MES_TYPE_SNAPCHAT);
            }
        }
    }

    /**
     * 获取日历提醒包
     *
     * @return
     */
    public ArrayList<String> getCalendarPkgNames() {
        ArrayList<String> pkgNames = new ArrayList<>();
        pkgNames.add("com.android.calendar");//日历
        return pkgNames;
    }

    /**
     * 获取邮件提醒包
     *
     * @return
     */
    public ArrayList<String> getEmailPkgNames() {
        ArrayList<String> pkgNames = new ArrayList<>();
        pkgNames.add("com.tencent.androidqqmail");//qq邮箱

        pkgNames.add("com.netease.mobimail");//网易邮箱大师
       // pkgNames.add("com.google.android.gm");//Gmail
        pkgNames.add("com.my.mail");//myMail免费电子邮件
       // pkgNames.add("com.microsoft.office.outlook");//Microsoft Outlook
        pkgNames.add("com.trtf.blue");//电子邮件
        pkgNames.add("me.bluemail.mail");//Blue Mail
        pkgNames.add("com.motorola.email");//Moto 电子邮件
        pkgNames.add("com.htc.android.mail");//HTC 邮件
        pkgNames.add("com.google.android.apps.inbox");//Inbox by Gmail
        pkgNames.add("com.asus.email");//华硕电子邮件
        pkgNames.add("jp.co.yahoo.android.ymail");//Yahoo
        pkgNames.add("com.fuzixx.dokidokipostbox");//DOKIDOKI邮箱
        pkgNames.add("ru.mail.mailapp");//Mail.Ru
        pkgNames.add("air.kukulive.mailnow");//Instant Email Address
        pkgNames.add("com.mail.emails");//所有电子邮件
        pkgNames.add("com.nhn.android.mail");//NAVER 邮件
        pkgNames.add("com.zoho.mail");//Zoho Mail
        pkgNames.add("com.syntomo.email");//Exchange+ Mail Client 交换邮件
        pkgNames.add("com.corp21cn.mail189");//189邮箱
        pkgNames.add("com.email.email");//Email
        pkgNames.add("com.motorola.blur.email");//Email
        pkgNames.add("com.jdex.gmail");//电子邮箱
        return pkgNames;
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {

    }

    @SuppressLint("NewApi")
    private void sendText(Notification notification, byte type) {
        DebugLog.i("");
//        if (mSmsMessage != null) {
//            return;
//        }
        //通知内容
        String title = null, text = null;
        try {
            if (android.os.Build.VERSION.SDK_INT > 18) {
                Bundle extras = notification.extras;
                title = extras.getString(Notification.EXTRA_TITLE);
                text = extras.getString(Notification.EXTRA_TEXT);
                if (TextUtils.isEmpty(text)) {
                    text = notification.tickerText.toString();
                }
                // 特殊消息处理如：[88条]name:hello world
                if (!TextUtils.isEmpty(text)) {
                    if (text.contains(":") && type == WECHAT) {
                        text = text.split(":")[1].trim();
                    } else if (text.contains("]")) {
                        text = text.substring(text.indexOf("]") + 1);
                    }
                }
            } else {
                title = null;
                text = TextUtils.isEmpty(notification.tickerText) ? null : notification.tickerText.toString();
                if (!TextUtils.isEmpty(text)) {
                    if (text.contains(":") && type == WECHAT) {
                        String[] tmp = text.split(":");
                        title = tmp[0];
                        text = tmp[1];
                        if (tmp[0].contains("]")) {
                            title = tmp[0].substring(tmp[0].indexOf("]") + 1);
                        }
                    }
                }
            }
        } catch (Exception e) {
            title = null;
            text = null;
        }

        mTitle = title+":";
        mText = text;
        mType = type;
//		getSmsFromPhone();
//        resolveData();
        if (mTitle.length() > 20) {
            mTitle = mTitle.substring(0, 20);
        }
        if (mText.length() > 20) {
            mText = mText.substring(0, 20);
        }
        DebugLog.d("打印智能提醒的信息,mTitle:" + mTitle + ",mText:" + mText + ",mType:" + mType);
//        NormalToast.showToast(getApplicationContext(), "打印智能提醒的信息,mTitle:" + mTitle + ",mText:" + mText + ",mType:" + mType, NormalToast.showTime);
        if (!TextUtils.isEmpty(mText) && share.getStartTimer()) {
            share.setStartTimer(false);
            sendBroadcast(new Intent(Constant.STOP_START_TIMER));
        }
        if (!TextUtils.isEmpty(mText)){
            protocolUtils.setSmsEvt(mType, mTitle, "", mText);
        }

//        ProtocolUtils.getInstance().setSmsEvt(mType, "shdh", "dsa", "dasd");
    }

    /**
     * 如果内容长度>64Byte,截取64Byte
     */
    private void resolveData() {
        String contact = mTitle, smsContent = mText;
        // 提醒信息配置
//        int tipInfo = LibSharedPreferences.getInstance().getDeviceFunTipInfoNotify();
//        boolean tipInfoContact = (tipInfo & 0x01) == BaseInfo.FUNCTION_OK ? true : false;
        // boolean tipInfoNum = ((tipInfo & 0x02) >> 1) == BaseInfo.FUNCTION_OK ? true : false;
//        boolean tipInfoContent = ((tipInfo & 0x04) >> 2) == BaseInfo.FUNCTION_OK ? true	: false;
//        mSmsMessage = new IncomingMessage();
//        mSmsMessage.serial = 1;
//        mSmsMessage.type = mType;

        // 是否支持电话号码
        mSmsContent = null;
        // 是否支持联系人
        if (null != contact) {
            try {
                byte[] contactByte = contact.getBytes("UTF-8");
                //DebugLog.i("contact:"+ByteDataConvertUtil.bytesToHexString(contactByte));
                if (null != mSmsContent) {
                    mSmsContent = ByteDataConvertUtil.byteMerger(
                            mSmsContent, contactByte);
                } else {
                    mSmsContent = contactByte;
                }
//                mSmsMessage.contactLength = contactByte.length;
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        // 是否支持信息内容
        if (!TextUtils.isEmpty(smsContent)) {
            try {
                byte[] smsByte = smsContent.getBytes("UTF-8");
                if (null != mSmsContent) {
                    mSmsContent = ByteDataConvertUtil.byteMerger(mSmsContent,
                            smsByte);
                } else {
                    mSmsContent = smsByte;
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        //如果内容长度>64Byte,截取64Byte
        if (mSmsContent != null && mSmsContent.length > 64) {
            mSmsContent = Arrays.copyOf(mSmsContent, 64);
//            mSmsMessage.contentLength = mSmsContent.length;
        }
        //   mText=mSmsContent;
    }

//	protected APPCoreServiceListener mAppListener = new APPCoreServiceListener() {
//		@Override
//		public void onBLEDisConnected(String str) {
//			mSmsMessage = null;
//			if (!TextUtils.isEmpty(str)) {
//				boolean needScanBeforeConnect = BleScanTool.getInstance()
//						.isNeedScanDevice();
//				if (needScanBeforeConnect) {
//					mCore.connect(str);
//				}
//			}
//			super.onBLEDisConnected(str);
//		}
//
//		@Override
//		public void onBLEConnected() {
//
//		}
//
//		@Override
//		public void onOtherDataReceive(byte[] value) {
//			// 短信提醒
//			if (value[0] == 0x05 && value[1] == 0x03) {
//				if (mSmsMessage == null)
//					return;
//				if (mSmsMessage.serial == value[3]) {
//					sendIncomingMessagePacket();
//				}
//				else {
//					mSmsMessage = null;
//					if (value[2] != 0 && value[3] != 0) {
//						getSmsFromPhone();
//					}
//				}
//
//			}
//
//
//		}
//	};
//
//	private void sendIncomingMessagePacket() {
//		if (mSmsMessage != null && mSmsMessage.totalPacket > mSmsMessage.serial) {
//			mSmsMessage.serial += 1;
//
//			int len;
//			if (smsModLen - 16 > 0) {
//				smsModLen -= 16;
//				len = 16;
//			} else {
//				len = smsModLen;
//			}
//
//			byte[] tmp = new byte[len];
//			// 第一包数据（总包数1byte+序列号1byte+信息类型1byte+内容长度1byte+号码长度1byte+姓名长度1byte+12byte）
//			// 从第二包开始的数据（总包数1byte+序列号1byte+16byte）
//			ByteDataConvertUtil.BinnCat(mSmsContent, tmp, smsIndex, len);
//			mSmsMessage.smsContent = tmp;
//			mCore.writeForce(NotifyCmd.getInstance().getIncomingMessageCmd(
//					mSmsMessage));
//
//			smsIndex += len;
//		} else {
//			mSmsMessage = null;
//		}
//
//		//DebugLog.i("packge "+ mSmsMessage.serial + ":"+ByteDataConvertUtil.bytesToHexString(mSmsMessage.smsContent));
//	}
//
//	public synchronized void getSmsFromPhone() {
//		if(mSmsMessage != null){
//			return;
//		}
//
//		String contact = mTitle, smsContent = mText;
//		// 提醒信息配置
//		int tipInfo = LibSharedPreferences.getInstance().getDeviceFunTipInfoNotify();
//		boolean tipInfoContact = (tipInfo & 0x01) == BaseInfo.FUNCTION_OK ? true : false;
//		// boolean tipInfoNum = ((tipInfo & 0x02) >> 1) == BaseInfo.FUNCTION_OK ? true : false;
//		boolean tipInfoContent = ((tipInfo & 0x04) >> 2) == BaseInfo.FUNCTION_OK ? true	: false;
//		mSmsMessage = new IncomingMessage();
//		mSmsMessage.serial = 1;
//		mSmsMessage.type = mType;
//
//		// 是否支持电话号码
//		mSmsContent = null;
//		mSmsMessage.numberLength = 0;
//		// 是否支持联系人
//		if (null != contact && tipInfoContact) {
//			 try {
//			 byte[] contactByte = contact.getBytes("UTF-8");
//			 //DebugLog.i("contact:"+ByteDataConvertUtil.bytesToHexString(contactByte));
//			 if (null != mSmsContent) {
//			 mSmsContent = ByteDataConvertUtil.byteMerger(
//			 mSmsContent, contactByte);
//			 } else {
//			 mSmsContent = contactByte;
//			 }
//			 mSmsMessage.contactLength = contactByte.length;
//			 } catch (UnsupportedEncodingException e) {
//			 e.printStackTrace();
//			 }
//		} else {
//			mSmsMessage.contactLength = 0;
//		}
//		// 是否支持信息内容
//		if (tipInfoContent && !TextUtils.isEmpty(smsContent)) {
//			try {
//				byte[] smsByte = smsContent.getBytes("UTF-8");
//				//DebugLog.i("content:"+ByteDataConvertUtil.bytesToHexString(smsByte));
//				if (null != mSmsContent) {
//					mSmsContent = ByteDataConvertUtil.byteMerger(mSmsContent,
//							smsByte);
//				} else {
//					mSmsContent = smsByte;
//				}
//				mSmsMessage.contentLength = smsByte.length;
//			} catch (UnsupportedEncodingException e) {
//				e.printStackTrace();
//			}
//		} else {
//			mSmsMessage.contentLength = 0;
//		}
//
//		//如果内容长度>64Byte,截取64Byte
//		if(mSmsContent != null && mSmsContent.length > 64){
//			mSmsContent = Arrays.copyOf(mSmsContent, 64);
//			mSmsMessage.contentLength = mSmsContent.length;
//		}
//
//		if (null != mSmsContent) {
//			int hasCount = 12;
//			smsModLen = mSmsContent.length - hasCount;
//			smsIndex = hasCount;
//			if (mSmsContent.length - hasCount > 0) {
//				mSmsMessage.totalPacket = (int) Math
//						.ceil((mSmsContent.length - hasCount) / 16.0) + 1;
//				byte[] tmp = new byte[hasCount];
//				ByteDataConvertUtil.BinnCat(mSmsContent, tmp, 0, hasCount);
//				mSmsMessage.smsContent = tmp;
//			} else {
//				mSmsMessage.totalPacket = 1;
//				byte[] tmp = new byte[mSmsContent.length];
//				ByteDataConvertUtil.BinnCat(mSmsContent, tmp, 0,
//						mSmsContent.length);
//				mSmsMessage.smsContent = tmp;
//			}
//		} else {
//			mSmsMessage.totalPacket = 1;
//		}
//
//		//DebugLog.i("packge "+ mSmsMessage.serial + ":"+ByteDataConvertUtil.bytesToHexString(mSmsMessage.smsContent));
//		mCore.writeForce(NotifyCmd.getInstance().getMessageCmd(mSmsMessage));
//
//	}
}
