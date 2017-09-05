package cn.dongha.ido.ui.services;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.view.KeyEvent;

import com.veryfit.multi.nativeprotocol.ProtocolEvt;
import com.veryfit.multi.nativeprotocol.ProtocolUtils;
import com.veryfit.multi.util.DebugLog;
import cn.dongha.ido.common.base.BaseCallBack;
import cn.dongha.ido.common.utils.AppSharedPreferencesUtils;

/**
 * @author: sslong
 * @package: cn.dongha.ido.
 * @description: ${TODO}{一句话描述该类的作用}   通过蓝牙 控制手机音乐播放
 * @date: 2016/7/11 12:02
 */
@SuppressLint("OverrideAbstract")
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class MusicContrlService extends NotificationListenerService {

    private AudioManager audioManager;//音频管理器
    private AppSharedPreferencesUtils shared = AppSharedPreferencesUtils.getInstance();
    private String mMusicPlayPackageName;
    private PackageManager packageManager;
    private ProtocolUtils protocolUtils = ProtocolUtils.getInstance();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    private Handler handler=new Handler();
    @Override
    public void onCreate() {
        super.onCreate();

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        packageManager = getPackageManager();

        protocolUtils.setProtocalCallBack(new BaseCallBack() {
            @Override
            public void onSysEvt(int evt_base, int evt_type, int error, int value) {
                super.onSysEvt(evt_base, evt_type, error, value);
                  if (evt_type == ProtocolEvt.BLE_TO_APP_MUSIC_START.toIndex()) {//手环控制App开始音乐
                    if (error == ProtocolEvt.SUCCESS) {
                        DebugLog.d("手环控制App开始音乐成功");
                        mMusicPlayPackageName = shared.getMusicPlayPackageName();
                        if (!mMusicPlayPackageName.equals("")) {
                            Intent intent = packageManager.getLaunchIntentForPackage(mMusicPlayPackageName);
                            if (intent != null) {
                                startActivity(intent);
                            }

                            if (audioManager.isMusicActive()) {
                                sendMusicKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE);
                            } else {
                                sendMusicKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY);
                            }

                        }
                    } else {
                        DebugLog.d("手环控制App开始音乐失败");
                    }
                } else if (evt_type == ProtocolEvt.BLE_TO_APP_MUSIC_PAUSE.toIndex()) {//手环控制App结束音乐
                    if (error == ProtocolEvt.SUCCESS) {
                        DebugLog.d("手环控制App结束音乐成功");
                        mMusicPlayPackageName = shared.getMusicPlayPackageName();
                        if (!mMusicPlayPackageName.equals("")) {
                            Intent intent = packageManager
                                    .getLaunchIntentForPackage(mMusicPlayPackageName);
                            if (intent != null) {
                                startActivity(intent);
                            }
                        }

                        if (audioManager.isMusicActive()) {
                            sendMusicKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE);//暂停
                        } else {
                            sendMusicKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY);
                        }
                    } else {
                        DebugLog.d("手环控制App结束音乐失败");
                    }
                } else if (evt_type == ProtocolEvt.BLE_TO_APP_MUSIC_LAST.toIndex()) {//上一曲
                    if (error == ProtocolEvt.SUCCESS) {
                        DebugLog.d("上一曲成功");
                        mMusicPlayPackageName = shared.getMusicPlayPackageName();
                        if (!mMusicPlayPackageName.equals("")) {
                            Intent intent = packageManager.getLaunchIntentForPackage(mMusicPlayPackageName);
                            if (intent != null) {
                                startActivity(intent);
                            }
                        }
                        if (!audioManager.isMusicActive()) {
                            sendMusicKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY);
                        }

                        sendMusicKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                        DebugLog.d("android.os.Build.MANUFACTURER:"+android.os.Build.MANUFACTURER);
                        if (android.os.Build.MANUFACTURER.equalsIgnoreCase("Meizu")){
                            DebugLog.d("魅族手机再发一次");
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    sendMusicKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS);//上一曲
                                    if (!audioManager.isMusicActive()) {
                                        sendMusicKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY);
                                    }
                                }
                            },100);
                        }
                    } else {
                        DebugLog.d("上一曲失败");
                    }
                } else if (evt_type == ProtocolEvt.BLE_TO_APP_MUSIC_NEXT.toIndex()) {//下一曲
                    if (error == ProtocolEvt.SUCCESS) {
                        DebugLog.d("下一曲成功");
                        mMusicPlayPackageName = shared.getMusicPlayPackageName();
                        if (!mMusicPlayPackageName.equals("")) {
                            Intent intent = packageManager.getLaunchIntentForPackage(mMusicPlayPackageName);
                            if (intent != null) {
                                startActivity(intent);
                            }
                        }
                        if (!audioManager.isMusicActive()) {
                            sendMusicKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY);//播放
                        }
                        sendMusicKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT);//下一曲
                    } else {
                        DebugLog.d("下一曲失败");
                    }
                } else if (evt_type == ProtocolEvt.BLE_TO_APP_VOLUME_UP.toIndex()) {//音量加
                    if (error == ProtocolEvt.SUCCESS) {
                        DebugLog.d("音量加成功");
                        sendMusicKeyEvent(KeyEvent.KEYCODE_VOLUME_UP);
                    } else {
                        DebugLog.d("音量加失败");
                    }
                } else if (evt_type == ProtocolEvt.BLE_TO_APP_VOLUME_DOWN.toIndex()) {//音量减
                    if (error == ProtocolEvt.SUCCESS) {
                        DebugLog.d("音量减成功");
                        sendMusicKeyEvent(KeyEvent.KEYCODE_VOLUME_DOWN);
                    } else {
                        DebugLog.d("音量减失败");
                    }
                }
            }
        });

    }

    public boolean sendMusicKeyEvent(int keyCode) {

        long eventTime = SystemClock.uptimeMillis()-1;
        KeyEvent key = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN,
                keyCode, 0);
        dispatchMediaKeyToAudioService(key, keyCode);
        dispatchMediaKeyToAudioService(KeyEvent.changeAction(key, KeyEvent.ACTION_UP), keyCode);
        DebugLog.d("----AudioManager 发送键值成功----");


//      换成广播形式控制音乐播放，音量控制应该用之前的方法，先注释，到时再调
//        Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
//        if(!mMusicPlayPackageName.equals("")){
//            intent.setPackage(mMusicPlayPackageName);
//        }
//
//        long eventTime = SystemClock.uptimeMillis()-1;
//        KeyEvent keyEvent = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN,
//                keyCode, 0);
//        intent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent);
//        getApplicationContext().sendBroadcast(intent);
//        intent.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent.changeAction(keyEvent, KeyEvent.ACTION_UP));
//        getApplicationContext().sendBroadcast(intent);
        return false;
    }

    private void dispatchMediaKeyToAudioService(KeyEvent event, int keyCode) {
        if (audioManager == null) {
            audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            // 控制音量增
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);

        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            // 控制音量减
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);

        } else {

            try {
                audioManager.dispatchMediaKeyEvent(event);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

}
