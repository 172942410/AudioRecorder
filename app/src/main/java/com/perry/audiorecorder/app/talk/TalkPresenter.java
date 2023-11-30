
package com.perry.audiorecorder.app.talk;


import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.alibaba.fastjson2.JSON;
import com.iflytek.aikit.core.AiEvent;
import com.iflytek.aikit.core.AiHandle;
import com.iflytek.aikit.core.AiHelper;
import com.iflytek.aikit.core.AiListener;
import com.iflytek.aikit.core.AiRequest;
import com.iflytek.aikit.core.AiResponse;
import com.perry.audiorecorder.AppConstants;
import com.perry.audiorecorder.BackgroundQueue;
import com.perry.audiorecorder.Mapper;
import com.perry.audiorecorder.R;
import com.perry.audiorecorder.app.AppRecorder;
import com.perry.audiorecorder.app.AppRecorderCallback;
import com.perry.audiorecorder.app.info.RecordInfo;
import com.perry.audiorecorder.app.records.RecordsContract;
import com.perry.audiorecorder.app.settings.SettingsMapper;
import com.perry.audiorecorder.app.talk.itemHolder.VHSendText;
import com.perry.audiorecorder.audio.AudioDecoder;
import com.perry.audiorecorder.audio.player.PcmAudioPlayer;
import com.perry.audiorecorder.audio.player.PlayerContractNew;
import com.perry.audiorecorder.audio.recorder.RecorderContract;
import com.perry.audiorecorder.bean.ReceiveMsgBean;
import com.perry.audiorecorder.data.FileRepository;
import com.perry.audiorecorder.data.Prefs;
import com.perry.audiorecorder.data.database.LocalRepository;
import com.perry.audiorecorder.data.database.Record;
import com.perry.audiorecorder.exception.AppException;
import com.perry.audiorecorder.exception.ErrorParser;
import com.perry.audiorecorder.network.HttpUploadFile;
import com.perry.audiorecorder.util.AndroidUtils;
import com.perry.audiorecorder.util.FileUtil;
import com.perry.audiorecorder.util.TimeUtils;
import com.perry.iflytek.AbilityConstant;

import org.xutils.common.Callback;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import timber.log.Timber;

public class TalkPresenter implements TalkContract.UserActionsListener {

    private final static String TAG = TalkPresenter.class.getName();
    private TalkContract.View view;
    private final AppRecorder appRecorder;
    private final PlayerContractNew.Player audioPlayer;
    private PlayerContractNew.PlayerCallback playerCallback;
    private AppRecorderCallback appRecorderCallback;
    private final BackgroundQueue loadingTasks;
    private final BackgroundQueue recordingsTasks;
    private final BackgroundQueue importTasks;
    private final BackgroundQueue processingTasks;
    private final FileRepository fileRepository;
    private final LocalRepository localRepository;
    private final Prefs prefs;
    private final SettingsMapper settingsMapper;
    private long songDuration = 0;
    private Record record;
    private boolean deleteRecord = false;
    private boolean listenPlaybackProgress = true;

    private boolean showBookmarks = false;

    int position;//item当前选择的那个播放

    HttpUploadFile httpUploadFile;

    int sampleRate = 24000;
    protected String modelPath = "models/cpu";
    private final String AMmodelName = "fastspeech2_csmsc_arm.nb";
    private final String VOCmodelName = "mb_melgan_csmsc_arm.nb";
    protected int cpuThreadNum = 1;
    protected String cpuPowerMode = "LITE_POWER_HIGH";
    private final String wavName = "tts_output.wav";
    private String wavFile = Environment.getExternalStorageDirectory() + File.separator + wavName;
    File pcmFile;
    private PcmAudioPlayer pcmAudioPlayer;
    PcmAudioPlayer.PcmPlayerListener pcmPlayerCallback;
    public TalkPresenter(final Prefs prefs, final FileRepository fileRepository,
                         final LocalRepository localRepository,
                         PlayerContractNew.Player audioPlayer,
                         AppRecorder appRecorder,
                         final BackgroundQueue recordingTasks,
                         final BackgroundQueue loadingTasks,
                         final BackgroundQueue processingTasks,
                         final BackgroundQueue importTasks,
                         SettingsMapper settingsMapper) {
        this.prefs = prefs;
        this.fileRepository = fileRepository;
        this.localRepository = localRepository;
        this.loadingTasks = loadingTasks;
        this.recordingsTasks = recordingTasks;
        this.importTasks = importTasks;
        this.processingTasks = processingTasks;
        this.audioPlayer = audioPlayer;
        this.appRecorder = appRecorder;
        this.settingsMapper = settingsMapper;
        httpUploadFile = new HttpUploadFile();
    }

    @Override
    public void bindView(final TalkContract.View v) {
        this.view = v;

        if (!prefs.isMigratedDb3()) {
            migrateDb3();
        }
        if (!prefs.hasAskToRenameAfterStopRecordingSetting()) {
            prefs.setAskToRenameAfterStopRecording(false);
        }
        prefs.setRecordOrder(AppConstants.SORT_DATE_DESC);//这里默认日期从最底下出来
        if (appRecorderCallback == null) {
            appRecorderCallback = new AppRecorderCallback() {

                long prevTime = 0;

                @Override
                public void onRecordingStarted(final File file) {
                    if (view != null) {
                        view.showRecordingStart();
                        view.keepScreenOn(prefs.isKeepScreenOn());
                    }
                    updateInformation(
                            prefs.getSettingRecordingFormat(),
                            prefs.getSettingSampleRate(),
                            0
                    );
                }

                @Override
                public void onRecordingPaused() {
                    if (view != null) {
                        view.keepScreenOn(false);
                        view.showRecordingPause();
                    }
                    if (deleteRecord) {
                        if (view != null) {
                            view.askDeleteRecordForever();
                            deleteRecord = false;
                        }
                    }
                }

                @Override
                public void onRecordingResumed() {
                    if (view != null) {
                        view.showRecordingResume();
                        view.keepScreenOn(prefs.isKeepScreenOn());
                    }
                }

                @Override
                public void onRecordingStopped(final File file, final Record rec) {
                    if (deleteRecord) {
                        record = rec;
                        deleteActiveRecord(true);
                        deleteRecord = false;
                    } else {
                        if (view != null) {
                            if (prefs.isAskToRenameAfterStopRecording()) {
                                view.askRecordingNewName(rec.getId(), file, true);
                            }
                        }
                        record = rec;
                        songDuration = rec.getDuration();
                        if (view != null) {
                            view.showWaveForm(rec.getAmps(), songDuration, 0);
                            view.showName(rec.getName());
                            view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(songDuration / 1000));
                            view.showOptionsMenu();
                        }
                        updateInformation(rec.getFormat(), rec.getSampleRate(), rec.getSize());
                        //每次录音完成这里其实可以只添加最后一条的
                        long startTimeLong = System.currentTimeMillis();
                        Log.d(TAG, "每次录音完成这里其实可以只添加最后一条的:" + startTimeLong);
//                        loadRecords();
                        rec.setAmps(rec.byte2int(rec.int2byte(rec.getAmps())));
                        ItemData itemData = Mapper.recordToItemType(rec);
//                        这里先请求网络接口
                        httpUploadFile.uploadAudio(itemData.getName(), itemData.getPath(), new HttpCallback(itemData));
                        addLastNewRecord(itemData);
                    }
                    if (view != null) {
                        view.keepScreenOn(false);
                        view.hideProgress();
                        view.showRecordingStop();
                    }
                }

                @Override
                public void onRecordingProgress(final long mills, final int amp) {
                    //TODO 这里是获取 音频amp 最大振幅 的地方
                    if (view != null) {
                        view.onRecordingProgress(mills, amp);
                        File recFile = appRecorder.getRecordFile();
                        long curTime = System.currentTimeMillis();
                        if (recFile != null && curTime - prevTime > 3000) { //Update record info every second when recording.
                            updateInformation(
                                    prefs.getSettingRecordingFormat(),
                                    prefs.getSettingSampleRate(),
                                    recFile.length()
                            );
                            prevTime = curTime;
                        }
                    }
                }

                @Override
                public void onError(AppException throwable) {
                    Timber.e(throwable);
                    if (view != null) {
                        view.keepScreenOn(false);
                        view.hideProgress();
                        view.showRecordingStop();
                    }
                }

                @Override
                public void onRecordShort() {
                    Log.d(TAG, "录音时间太短的回调 删除文件");
                    deleteRecord = true;
                    view.showRecordTooShortTipView();
                }
            };
        }
        appRecorder.addRecordingCallback(appRecorderCallback);

        if (playerCallback == null) {
            playerCallback = new PlayerContractNew.PlayerCallback() {
                @Override
                public void onStartPlay() {
                    if (record != null && view != null) {
                        view.startPlaybackService(record.getName());
                        view.showPlayStart(true, position);
                    }
                }

                @Override
                public void onPlayProgress(final long mills) {
                    if (view != null && listenPlaybackProgress) {
                        long duration = songDuration / 1000;
                        if (duration > 0) {
                            view.onPlayProgress(mills, (int) (1000 * mills / duration));
                        }
                    }
                }

                @Override
                public void onStopPlay() {
                    if (view != null) {
                        audioPlayer.seek(0);
                        view.showPlayStop(position);
                        view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(songDuration / 1000));
                    }
                }

                @Override
                public void onPausePlay() {
                    if (view != null) {
                        view.showPlayPause(position);
                    }
                }

                @Override
                public void onSeek(long mills) {
                }

                @Override
                public void onError(@NonNull AppException throwable) {
                    Timber.e(throwable);
                    if (view != null) {
                        view.showError(ErrorParser.parseException(throwable));
                    }
                }
            };
        }

        this.audioPlayer.addPlayerCallback(playerCallback);

        if (audioPlayer.isPlaying()) {
            view.showPlayStart(false, position);
        } else if (audioPlayer.isPaused()) {
            view.showPlayPause(position);
        } else {
            audioPlayer.seek(0);
            view.showPlayStop(position);
        }

        if (appRecorder.isPaused()) {
            view.keepScreenOn(false);
            view.showRecordingPause();
            view.showRecordingProgress(TimeUtils.formatTimeIntervalHourMinSec2(appRecorder.getRecordingDuration()));
//			view.updateRecordingView(appRecorder.getRecordingData(), appRecorder.getRecordingDuration());
        } else if (appRecorder.isRecording()) {
            view.showRecordingStart();
            view.showRecordingProgress(TimeUtils.formatTimeIntervalHourMinSec2(appRecorder.getRecordingDuration()));
            view.keepScreenOn(prefs.isKeepScreenOn());
//			view.updateRecordingView(appRecorder.getRecordingData(), appRecorder.getRecordingDuration());
        } else {
            view.showRecordingStop();
            view.keepScreenOn(false);
        }
//		view.hideRecordProcessing();
        updateInformation(
                prefs.getSettingRecordingFormat(),
                prefs.getSettingSampleRate(),
                0
        );
    }

    class HttpCallback implements Callback.CommonCallback<String> {
        ItemData itemData;

        HttpCallback(ItemData itemData) {
            this.itemData = itemData;
        }

        @Override
        public void onSuccess(String result) {
            view.sendSuccess(itemData);
            TalkPresenter.this.record.setLoading(0);
            localRepository.updateRecord(TalkPresenter.this.record);
            long endTimeLong = System.currentTimeMillis();
            Log.d(TAG, "uploadAudio onSuccess 原始数据: result：" + result);
//                                字符串开始转义
            result = result.replace("\\", "");
            if (result.startsWith("\"")) {
                result = result.substring(1);
            }
            if (result.endsWith("\"")) {
                result = result.substring(0, result.length() - 1);
            }
//                                字符串在解析json之前需要先转义成功；因为服务端有可能把字符串外面又多节了双引号导致的问题
//            Log.d(TAG, "uploadAudio onSuccess 请求耗时:" + (endTimeLong - startTimeLong) + "，result：" + result);
            ReceiveMsgBean receiveMsgBean = JSON.parseObject(result, ReceiveMsgBean.class);
            Log.d(TAG, "耗时：" + (System.currentTimeMillis() - endTimeLong) + ",uploadAudio json解析完成 :" + receiveMsgBean);
            Record receiveRecord = Record.createReceiveTextRecord(System.currentTimeMillis(), receiveMsgBean.text);
            Record receiveRecordDb = localRepository.insertRecord(receiveRecord);
            ItemData itemData = Mapper.recordToItemType(receiveRecordDb);
            view.sendTextShow(itemData);
//            这里调用播放器播放文本
            startTTSpeaking(-1,itemData);
//            if(predictor.isLoaded()){
////                boolean isRun = predictor.runModel(itemData.getItemData());
//                boolean isRun = predictor.runModel(new float[]{155,73,71,29,179,71,199,126,177,115,138,241,120,71,42,39,57,69,184,186});
//                Log.d(TAG,"执行文字转语音结果："+isRun);
//                wavFile = fileRepository.getRecordingDir().getAbsolutePath() + wavName;
//                try {
//                    Utils.rawToWave(wavFile, predictor.wav, sampleRate);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//                if (audioPlayer.isPlaying()) {
//                    audioPlayer.pause();
//                } else if (audioPlayer.isPaused()) {
//                    audioPlayer.unpause();
//                } else {
////                    这里播放的是tts转好的文件
//                    audioPlayer.play(wavFile);
//                }
//            }else{
//                Log.e(TAG,"paddleSpeech tts 加载失败了...无法文字转语音了");
//            }

        }

        @Override
        public void onError(Throwable ex, boolean isOnCallback) {
            view.sendFailed(itemData, ex);
            TalkPresenter.this.record.setLoading(1);
            localRepository.updateRecord(TalkPresenter.this.record);
            Log.d(TAG, "uploadAudio onError :" + ex + ",isOnCallback:" + isOnCallback);
        }

        @Override
        public void onCancelled(CancelledException cex) {
            view.sendFailed(itemData, cex);
            TalkPresenter.this.record.setLoading(1);
            localRepository.updateRecord(TalkPresenter.this.record);
            Log.d(TAG, "uploadAudio onCancelled :" + cex);
        }

        @Override
        public void onFinished() {
            Log.d(TAG, "uploadAudio onFinished");
        }

    }

    byte[] cacheArray;

    /**
     * 设置合成PCM数据缓存
     */
    private void setCacheArray(byte[] cacheArray) {
        this.cacheArray = cacheArray;
    }

    private AtomicInteger totalPercent = new AtomicInteger(100);
    //会话对象
    private AiHandle aiHandle;
    private LinkedHashMap<String, Object> ttsParamsMap = new LinkedHashMap<>();

    /**
     * 设置发音人
     */
    void setVCN(String vcn) {
        Log.i(TAG, "设置发音人==>" + vcn);
        ttsParamsMap.put("vcn", "xiaoyan");//
        ttsParamsMap.put("language", 1);
    }

    /**
     * 设置发音人语速
     */
    void setSpeed(int speed) {
        Log.i(TAG, "设置发音人语速==>" + speed);
        ttsParamsMap.put("speed", speed);
    }

    /**
     * 设置发音人音调
     */
    void setPitch(int pitch) {
        Log.i(TAG, "设置发音人音调==>" + pitch);
        ttsParamsMap.put("pitch", pitch);
    }

    /**
     * 设置发音人音量
     */
    void setVolume(int volume) {
        Log.i(TAG, "设置发音人音量==>" + volume);
        ttsParamsMap.put("volume", volume);
    }

    int hasNextPosition;
    ItemData hasItemData;
    /**
     * TTS文字转语音开始说话
     */
    public void startTTSpeaking(int position,ItemData itemData) {
            if (audioPlayer.isPlaying()) {
                audioPlayer.stop();
            }
        if(curTtsPlayPosition == position) {
            if (pcmAudioPlayer.isPlaying()) {
                itemData.playStatus = 2;
                pcmAudioPlayer.pause();
                view.showItemTtsPlay(position,itemData);
                return;
            }else if(!pcmAudioPlayer.isOver()){
                itemData.playStatus = 1;
                pcmAudioPlayer.resume();
                view.showItemPaused(position,itemData);
                return;
            }

        }else{
            if (!pcmAudioPlayer.isOver()) {
                curTtsPlayItem.playStatus = 0;
                view.showItemTtsPlay(curTtsPlayPosition,curTtsPlayItem);
                //播放其他的
                Log.d(TAG,"tts 调用停止");
                pcmAudioPlayer.stop();
                hasNextPosition = position;
                hasItemData = itemData;

//                Message message = new Message();
//                message.obj = itemData;
//                message.what = position;
//                handler.sendMessageDelayed(message,600);
                return;
            }
        }
        startTTS(position,itemData);
    }

    Handler handler = new Handler(message -> {
        startTTS(message.what, (ItemData) message.obj);
        return false;
    });

    /**
     * TTS文字转语音开始说话
     */
    public void startTTS(int position,ItemData itemData) {
        if(itemData == null){
            return;
        }
        curTtsPlayPosition = position;
        curTtsPlayItem = itemData;
        itemData.playStatus = 1;
        view.showItemPaused(position,itemData);

        String text = new String(itemData.getItemData());
        //以下两行代码是后来添加的
        hasNextPosition = -1;
        hasItemData = null;
        Log.d(TAG,"startTTSpeaking:"+text);
        int ret = AiHelper.getInst().engineInit(AbilityConstant.XTTS_ID);
        if (ret != AbilityConstant.ABILITY_SUCCESS_CODE) {
            Log.w(TAG, "open ivw error code ===> $ret");
            view.onAbilityError(ret, new Throwable("引擎初始化失败"));
            return;
        }
        setCacheArray(null);
        totalPercent.set(text.length());
        if (aiHandle != null) {
            AiHelper.getInst().end(aiHandle);
        }
        AiRequest.Builder paramBuilder = AiRequest.builder().param("rdn", 0)
                .param("reg", 0)
                .param("textEncoding", "UTF-8");  //可选参数，文本编码格式，默认为65001，UTF8格式
        Log.d(TAG,ttsParamsMap.toString());
        if(ttsParamsMap.size() == 0){
            paramBuilder.param("vcn", "xiaoyan");//
            paramBuilder.param("language", 1);
        }else {
            Set<String> keySet = ttsParamsMap.keySet();
            while (keySet.iterator().hasNext()) {
                String key = keySet.iterator().next();
                Object value = ttsParamsMap.get(key);
                if (value instanceof Integer) {
                    paramBuilder.param(key, (int) value);
                } else {
                    paramBuilder.param(key, (String) value);
                }
            }
        }

        //启动会话
        aiHandle = AiHelper.getInst().start(AbilityConstant.XTTS_ID, paramBuilder.build(), null);
        if (aiHandle.getCode() != AbilityConstant.ABILITY_SUCCESS_CODE) {
            Log.w(TAG, "启动会话失败");
            view.onAbilityError(aiHandle.getCode(), new Throwable("启动会话失败"));
            return;
        }
        // 构建写入数据
        AiRequest.Builder dataBuilder = AiRequest.builder().text("text", text);
        // 写入数据
        ret = AiHelper.getInst().write(dataBuilder.build(), aiHandle);
        if (ret != AbilityConstant.ABILITY_SUCCESS_CODE) {
            Log.w(TAG, "合成写入数据失败");
            view.onAbilityError(ret, new Throwable("合成写入数据失败"));
            return;
        }
        Log.w(TAG, "合成写入成功");
    }

    @Override
    public void sendText(String msgStr) {
//        1,显示到界面上
        Record recordTemp = Record.createTextRecord(System.currentTimeMillis(), msgStr);
        Record recordReturn = localRepository.insertRecord(recordTemp);
        ItemData itemData = Mapper.recordToItemType(recordReturn);
        view.sendTextShow(itemData);

//        2，发送成功后还需要回调的

    }

    int curTtsPlayPosition;
    ItemData curTtsPlayItem;
    @Override
    public void startTtsPlay(int position, VHSendText itemViewHolder, ItemData item) {
        startTTSpeaking(position,item);
    }

    private void addLastNewRecord(ItemData itemData) {
        if (view != null) {
            final int order = prefs.getRecordsOrder();
            ArrayList list = new ArrayList<ItemData>();
            view.showItemProgress(itemData);
            list.add(itemData);
            view.addRecords(list, order);
        }
    }

    @Override
    public void unbindView() {
        if (view != null) {
            audioPlayer.removePlayerCallback(playerCallback);
            appRecorder.removeRecordingCallback(appRecorderCallback);
            this.localRepository.setOnRecordsLostListener(null);
            this.view = null;
        }
    }

    @Override
    public void clear() {
        if (view != null) {
            unbindView();
        }
        localRepository.close();
        audioPlayer.release();
        appRecorder.release();
        loadingTasks.close();
        recordingsTasks.close();
    }

    @Override
    public void checkFirstRun() {
        if (prefs.isFirstRun()) {
            if (view != null) {
                view.startWelcomeScreen();
            }
        }
    }

    @Override
    public void storeInPrivateDir(Context context) {
        if (prefs.isStoreDirPublic()) {
            prefs.setStoreDirPublic(false);
            fileRepository.updateRecordingDir(context, prefs);
        }
    }

    @Override
    public void setAudioRecorder(RecorderContract.Recorder recorder) {
        appRecorder.setRecorder(recorder);
    }

    @Override
    public void pauseUnpauseRecording(Context context) {
        deleteRecord = false;
        try {
            if (fileRepository.hasAvailableSpace(context)) {
                if (appRecorder.isPaused()) {
                    appRecorder.resumeRecording();
                } else if (appRecorder.isRecording()) {
                    appRecorder.pauseRecording();
                }
            } else {
                if (view != null) {
                    view.showError(R.string.error_no_available_space);
                }
            }
        } catch (IllegalArgumentException e) {
            if (view != null) {
                view.showError(R.string.error_failed_access_to_storage);
            }
        }
    }

    @Override
    public void stopRecording(boolean delete) {
        Log.d(TAG, "停止录音 逻辑执行 判断下是否正在录音的状态：" + appRecorder.isRecording());
        if (appRecorder.isRecording()) {
            deleteRecord = delete;
            if (view != null) {
                view.showProgress();
                view.waveFormToStart();
                view.hideTipView();
            }
            audioPlayer.seek(0);
            appRecorder.stopRecording();
        }
    }

    @Override
    public void cancelRecording() {
        if (appRecorder.isPaused()) {
            if (view != null) {
                view.askDeleteRecordForever();
                deleteRecord = false;
            }
        } else {
            deleteRecord = true;
            appRecorder.pauseRecording();
        }
    }

    @Override
    public void startPlayback(int position) {
        if(!pcmAudioPlayer.isOver()){
            pcmAudioPlayer.stop();
        }
        this.position = position;
        if (record != null) {
            if (audioPlayer.isPlaying()) {
                audioPlayer.pause();
            } else if (audioPlayer.isPaused()) {
                audioPlayer.unpause();
            } else {
                audioPlayer.play(record.getPath());
            }
        }
    }

    @Override
    public void seekPlayback(long mills) {
        audioPlayer.seek(mills);
//				AndroidUtils.convertPxToMills(px, AndroidUtils.dpToPx(dpPerSecond)));
    }

    @Override
    public void stopPlayback(int position) {
        if (this.position != position) {
            audioPlayer.stop();
        }
    }

    @Override
    public void renameRecord(final long id, final String newName, final String extension) {
        if (id < 0 || newName == null || newName.isEmpty()) {
            AndroidUtils.runOnUIThread(() -> {
                if (view != null) {
                    view.showError(R.string.error_failed_to_rename);
                }
            });
            return;
        }
        if (view != null) {
            view.showProgress();
        }
        final String name = FileUtil.removeUnallowedSignsFromName(newName);
        recordingsTasks.postRunnable(() -> {
            final Record record = localRepository.getRecord((int) id);
            if (record != null) {
                String nameWithExt = name + AppConstants.EXTENSION_SEPARATOR + extension;
                File file = new File(record.getPath());
                File renamed = new File(file.getParentFile().getAbsolutePath() + File.separator + nameWithExt);

                if (renamed.exists()) {
                    AndroidUtils.runOnUIThread(() -> {
                        if (view != null) {
                            view.showError(R.string.error_file_exists);
                        }
                    });
                } else {
                    if (fileRepository.renameFile(record.getPath(), name, extension)) {
                        TalkPresenter.this.record = new Record(
                                record.getId(),
                                name,
                                record.getDuration(),
                                record.getCreated(),
                                record.getAdded(),
                                record.getRemoved(),
                                renamed.getAbsolutePath(),
                                record.getFormat(),
                                record.getSize(),
                                record.getSampleRate(),
                                record.getChannelCount(),
                                record.getBitrate(),
                                record.isBookmarked(),
                                record.isWaveformProcessed(),
                                record.getAmps(),
                                1,
                                null,
                                0);
                        if (localRepository.updateRecord(TalkPresenter.this.record)) {
                            AndroidUtils.runOnUIThread(() -> {
                                if (view != null) {
                                    view.hideProgress();
                                    view.showName(name);
                                }
                            });
                        } else {
                            AndroidUtils.runOnUIThread(() -> {
                                if (view != null) {
                                    view.showError(R.string.error_failed_to_rename);
                                }
                            });
                            //Restore file name after fail update path in local database.
                            if (renamed.exists()) {
                                //Try to rename 3 times;
                                if (!renamed.renameTo(file)) {
                                    if (!renamed.renameTo(file)) {
                                        renamed.renameTo(file);
                                    }
                                }
                            }
                        }

                    } else {
                        AndroidUtils.runOnUIThread(() -> {
                            if (view != null) {
                                view.showError(R.string.error_failed_to_rename);
                            }
                        });
                    }
                }
                AndroidUtils.runOnUIThread(() -> {
                    if (view != null) {
                        view.hideProgress();
                    }
                });
            } else {
                AndroidUtils.runOnUIThread(() -> {
                    if (view != null) {
                        view.showError(R.string.error_failed_to_rename);
                    }
                });
            }
        });
    }

    @Override
    public void decodeRecord(long id) {
        loadingTasks.postRunnable(() -> {
            final Record rec = localRepository.getRecord((int) prefs.getActiveRecord());
            if (view != null && rec != null && rec.getDuration() / 1000 < AppConstants.DECODE_DURATION && !rec.isWaveformProcessed()) {
                view.decodeRecord(rec.getId());
            }
        });
    }

    @Override
    public void loadActiveRecord() {
        if (!appRecorder.isRecording()) {
            if (view != null) {
                view.showProgress();
            }
            loadingTasks.postRunnable(() -> {
                final Record rec = localRepository.getRecord((int) prefs.getActiveRecord());
                record = rec;
                if (rec != null) {
                    songDuration = rec.getDuration();
                    AndroidUtils.runOnUIThread(() -> {
                        if (view != null) {
                            if (audioPlayer.isPaused()) {
                                long duration = songDuration / 1000;
                                if (duration > 0) {
                                    long playProgressMills = audioPlayer.getPauseTime();
                                    view.onPlayProgress(playProgressMills, (int) (1000 * playProgressMills / duration));
                                    view.showWaveForm(rec.getAmps(), songDuration, playProgressMills);
                                }
                            } else {
                                view.showWaveForm(rec.getAmps(), songDuration, 0);
                            }

                            view.showName(rec.getName());
                            view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(songDuration / 1000));
                            view.showOptionsMenu();
                            view.hideProgress();
                            updateInformation(rec.getFormat(), rec.getSampleRate(), rec.getSize());
                        }
                    });
                } else {
                    AndroidUtils.runOnUIThread(() -> {
                        if (view != null) {
                            view.hideProgress();
                            view.showWaveForm(new int[]{}, 0, 0);
                            view.showName("");
                            view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(0));
                            view.hideOptionsMenu();
                        }
                    });
                }
            });
        }
    }

    @Deprecated //Remove soon
    @Override
    public void checkPublicStorageRecords() {
        if (!prefs.isPublicStorageMigrated()) {
            loadingTasks.postRunnable(() -> {
                long lastTimeCheck = prefs.getLastPublicStorageMigrationAsked();
                long curTime = System.currentTimeMillis();
                if (curTime - lastTimeCheck > AppConstants.MIGRATE_PUBLIC_STORAGE_WARNING_COOLDOWN_MILLS &&
                        localRepository.hasRecordsWithPath(fileRepository.getPublicDir().getAbsolutePath())) {
                    prefs.setLastPublicStorageMigrationAsked(curTime);
                    AndroidUtils.runOnUIThread(() -> {
                        if (view != null) {
                            view.showMigratePublicStorageWarning();
                        }
                    });
                }
            });
        }
    }

    @Override
    public void setAskToRename(boolean value) {
        prefs.setAskToRenameAfterStopRecording(value);
    }

    @Override
    public void updateRecordingDir(Context context) {
        fileRepository.updateRecordingDir(context, prefs);
//        boolean ttsSuccess = predictor.init(context, modelPath, AMmodelName, VOCmodelName, cpuThreadNum, cpuPowerMode);
//        if(ttsSuccess){
//            Log.d(TAG,"paddleSpeech tts 初始化成功");
//        }else{
//            Log.e(TAG,"paddleSpeech tts 初始化失败");
//        }
        if(pcmAudioPlayer == null) {
            pcmAudioPlayer = new PcmAudioPlayer(context);
        }
        //能力回调
        Log.d(TAG,"updateRecordingDir tts能力初始化");
        AiHelper.getInst().registerListener(AbilityConstant.XTTS_ID, new AiListener() {
            /**
             * 能力输出回调
             * @param handleID  会话ID
             * @param usrContext  用户自定义标识
             * @param responseData List<AiResponse> 是 能力执行结果
             */
            @Override
            public void onResult(int handleID, List<AiResponse> responseData, Object usrContext) {
                if (responseData == null || responseData.isEmpty()) {
                    return;
                }
                for (AiResponse aiResponse : responseData) {
                    byte[] bytes = aiResponse.getValue();
                    if (bytes == null || bytes.length == 0) {
                        continue;
                    }
                    if (cacheArray == null) {
                        cacheArray = bytes;
                    } else {
                        byte[] resBytes = new byte[cacheArray.length + bytes.length];
                        System.arraycopy(cacheArray, 0, resBytes, 0, cacheArray.length);
                        System.arraycopy(bytes, 0, resBytes, cacheArray.length, bytes.length);
                        cacheArray = resBytes;
                    }
                }
            }

            /**
             * 能力输出回调
             * @param handleID 会话ID
             * @param eventID  0=未知;1=开始;2=结束;3=超时;4=进度
             * @param usrContext Object 用户自定义标识
             * @param eventData List<AiResponse>  事件消息数据
             */
            @Override
            public void onEvent(int handleID, int eventID, List<AiResponse> eventData, Object usrContext) {
                if (eventID == AiEvent.EVENT_START.getValue()) {
                    //引擎计算开始
                    Log.d(TAG,"tts 引擎计算开始");
                    view.onAbilityBegin();
//                    播放器播放
                    pcmAudioPlayer.prepareAudio(() -> {
                        Log.i(TAG, "开始播放");
                        return null;
                    });
                } else if (eventID == AiEvent.EVENT_PROGRESS.getValue()) {
                    //引擎计算中
                } else if (eventID == AiEvent.EVENT_END.getValue()) {
                    //引擎计算结束
                    view.onAbilityEnd();
                    Log.d(TAG,"tts 引擎计算结束");
                    pcmAudioPlayer.writeMemFile(cacheArray);
                    pcmAudioPlayer.play(totalPercent.get(), pcmPlayerCallback);
                } else if (eventID == AiEvent.EVENT_TIMEOUT.getValue()) {
                    //引擎超时
                    view.onAbilityError(AbilityConstant.ABILITY_CUSTOM_UNKNOWN_CODE, new Throwable("引擎超时"));
                }
            }


            /**
             * 能力输出失败回调
             * @param handleID 会话ID
             * @param errID  错误码
             * @param usrContext Object 用户自定义标识
             * @param errorMsg 错误描述
             */
            @Override
            public void onError(int handleID, int errID, String errorMsg, Object usrContext) {
                if (TextUtils.isEmpty(errorMsg)) {
                    errorMsg = "能力输出失败";
                }
                view.onAbilityError(errID, new Throwable(errorMsg));
            }
        });
    }
    @Override
    public void setPcmPlayerListener(PcmAudioPlayer.PcmPlayerListener pcmPlayerCallback){
         this.pcmPlayerCallback = pcmPlayerCallback;
    }

    @Override
    public void stopTtsPlay() {
        if(curTtsPlayItem != null && curTtsPlayPosition >= -1){
            curTtsPlayItem.playStatus = 0;
            view.showItemTtsPlay(curTtsPlayPosition,curTtsPlayItem);
        }
        // 这里理论也可以播放下一条的 代替之前的600毫秒延迟的handle的发送请求
        if(hasItemData != null) {
            startTTS(hasNextPosition, hasItemData);
        }
    }
    /**
     * 所有能力在退出的时候需要手动去释放会话
     * AiHelper.getInst().end(aiHandle)
     */
    @Override
    public void destroy() {
        AiHelper.getInst().end(aiHandle);
        aiHandle = null;
        audioPlayer.stop();
        audioPlayer.release();
    }

    @Override
    public void setStoragePrivate(Context context) {
        prefs.setStoreDirPublic(false);
        fileRepository.updateRecordingDir(context, prefs);
    }

    @Override
    public void onShareRecordClick() {
        if (view != null && record != null) {
            view.shareRecord(record);
        }
    }

    @Override
    public void onRenameRecordClick() {
        if (view != null && record != null) {
            view.askRecordingNewName(record.getId(), new File(record.getPath()), false);
        }
    }

    @Override
    public void onOpenFileClick() {
        if (view != null && record != null) {
            view.openFile(record);
        }
    }

    @Override
    public void onSaveAsClick() {
        if (view != null && record != null) {
            view.downloadRecord(record);
        }
    }

    @Override
    public void onDeleteClick() {
        if (view != null && record != null) {
            view.askDeleteRecord(record.getName());
        }
    }

    private void updateInformation(String format, int sampleRate, long size) {
        if (format.equals(AppConstants.FORMAT_3GP)) {
//			if (view != null) {
//				view.showInformation(settingsMapper.formatSize(size) + AppConstants.SEPARATOR
//						+ settingsMapper.convertFormatsToString(format) + AppConstants.SEPARATOR
//						+ settingsMapper.convertSampleRateToString(sampleRate)
//				);
//			}
        } else {
            if (view != null) {
                switch (format) {
                    case AppConstants.FORMAT_M4A:
                    case AppConstants.FORMAT_WAV:
//						view.showInformation(settingsMapper.formatSize(size) + AppConstants.SEPARATOR
//								+ settingsMapper.convertFormatsToString(format) + AppConstants.SEPARATOR
//								+ settingsMapper.convertSampleRateToString(sampleRate)
//						);
                        break;
                    default:
//						view.showInformation(settingsMapper.formatSize(size) + AppConstants.SEPARATOR
//								+ format + AppConstants.SEPARATOR
//								+ settingsMapper.convertSampleRateToString(sampleRate)
//						);
                }
            }
        }
    }

    @Override
    public boolean isStorePublic() {
        return prefs.isStoreDirPublic();
    }

    @Override
    public String getActiveRecordPath() {
        if (record != null) {
            return record.getPath();
        } else {
            return null;
        }
    }

    @Override
    public void deleteActiveRecord(final boolean forever) {
        final Record rec = record;
        if (rec != null) {
            audioPlayer.stop();
            recordingsTasks.postRunnable(() -> {
                if (forever) {
                    localRepository.deleteRecordForever(rec.getId());
                    fileRepository.deleteRecordFile(rec.getPath());
                } else {
                    localRepository.deleteRecord(rec.getId());
                }
                prefs.setActiveRecord(-1);
                AndroidUtils.runOnUIThread(() -> {
                    if (view != null) {
                        view.showWaveForm(new int[]{}, 0, 0);
                        view.showName("");
                        view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(0));
                        if (!forever) {
                            view.showMessage(R.string.record_moved_into_trash);
                        }
                        view.hideOptionsMenu();
                        view.onPlayProgress(0, 0);
                        view.hideProgress();
                        record = null;
                        updateInformation(
                                prefs.getSettingRecordingFormat(),
                                prefs.getSettingSampleRate(),
                                0
                        );
                    }
                });
            });
        }
    }

    @Override
    public void onRecordInfo(RecordInfo info) {
        if (view != null) {
            view.showRecordInfo(info);
        }
    }

    @Override
    public void disablePlaybackProgressListener() {
        listenPlaybackProgress = false;
    }

    @Override
    public void enablePlaybackProgressListener() {
        listenPlaybackProgress = true;
    }

    @Override
    public void setActiveRecord(long id, RecordsContract.Callback callback) {
        if (id >= 0 && !appRecorder.isRecording()) {
            prefs.setActiveRecord(id);
            if (view != null) {
                view.showPanelProgress();
            }
            loadingTasks.postRunnable(() -> {
                final Record rec = localRepository.getRecord((int) id);
                record = rec;
                if (rec != null) {
                    AndroidUtils.runOnUIThread(() -> {
                        if (view != null) {
                            view.showWaveForm(rec.getAmps(), rec.getDuration(), 0);
                            view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(rec.getDuration() / 1000));
                            view.showRecordName(rec.getName());
                            callback.onSuccess();
                            if (rec.isBookmarked()) {
                                view.addedToBookmarks(rec.getId(), true);
                            } else {
                                view.removedFromBookmarks(rec.getId(), true);
                            }
                            view.hidePanelProgress();
                            view.showPlayerPanel();
                        }
                    });
                } else {
                    AndroidUtils.runOnUIThread(() -> {
                        callback.onError(new Exception("Record is NULL!"));
                        if (view != null) {
                            view.hidePanelProgress();
                        }
                    });
                }
            });
        }
    }

    @Override
    public void checkBookmarkActiveRecord() {
        recordingsTasks.postRunnable(() -> {
            final Record rec = record;
            if (rec != null) {
                boolean success;
                if (rec.isBookmarked()) {
                    success = localRepository.removeFromBookmarks(rec.getId());
                } else {
                    success = localRepository.addToBookmarks(rec.getId());
                }
                if (success) {
                    rec.setBookmark(!rec.isBookmarked());

                    AndroidUtils.runOnUIThread(() -> {
                        if (view != null) {
                            if (rec.isBookmarked()) {
                                view.addedToBookmarks(rec.getId(), true);
                            } else {
                                view.removedFromBookmarks(rec.getId(), true);
                            }
                        }
                    });
                }
            }
        });
    }

    @Override
    public void addToBookmark(int id) {
        recordingsTasks.postRunnable(() -> {
            final Record r = localRepository.getRecord(id);
            if (r != null) {
                if (localRepository.addToBookmarks(r.getId())) {
                    AndroidUtils.runOnUIThread(() -> {
                        if (view != null) {
                            view.addedToBookmarks(r.getId(), record != null && r.getId() == record.getId());
                        }
                    });
                }
            }
        });
    }

    @Override
    public void removeFromBookmarks(int id) {
        recordingsTasks.postRunnable(() -> {
            final Record r = localRepository.getRecord(id);
            if (r != null) {
                localRepository.removeFromBookmarks(r.getId());
                AndroidUtils.runOnUIThread(() -> {
                    if (view != null) {
                        view.removedFromBookmarks(r.getId(), record != null && r.getId() == record.getId());
                    }
                });
            }
        });
    }

    @Override
    public void loadRecords() {
        if (view != null) {
            view.showProgress();
            view.showPanelProgress();
            loadingTasks.postRunnable(() -> {
                final int order = prefs.getRecordsOrder();
                final List<Record> recordList = localRepository.getRecords(0, order);
                final Record rec = localRepository.getRecord((int) prefs.getActiveRecord());
                record = rec;
                AndroidUtils.runOnUIThread(() -> {
                    if (view != null) {
                        view.showRecords(Mapper.recordsToItemType(recordList), order);
                        if (audioPlayer.isPaused() || audioPlayer.isPlaying()) {
                            if (rec != null) {
                                if (audioPlayer.isPaused()) {
                                    long duration = rec.getDuration() / 1000;
                                    if (duration > 0) {
                                        long playProgressMills = audioPlayer.getPauseTime();
                                        view.onPlayProgress(playProgressMills, (int) (1000 * playProgressMills / duration));
                                        view.showWaveForm(rec.getAmps(), rec.getDuration(), playProgressMills);
                                    }
                                } else {
                                    view.showWaveForm(rec.getAmps(), rec.getDuration(), 0);
                                }
                                view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(rec.getDuration() / 1000));
                                view.showRecordName(rec.getName());
                                if (rec.isBookmarked()) {
                                    view.bookmarksSelected();
                                } else {
                                    view.bookmarksUnselected();
                                }
                                if (audioPlayer.isPlaying() || audioPlayer.isPaused()) {
                                    view.showActiveRecord(rec.getId());
                                }
                            }
                        }

                        view.hideProgress();
                        view.hidePanelProgress();
                        view.bookmarksUnselected();
                        if (recordList.size() == 0) {
                            view.showEmptyList();
                        }
                    }
                });
            });
        }
    }

    @Override
    public void loadRecordsPage(final int page) {
        if (view != null && !showBookmarks) {
            view.showProgress();
            view.showPanelProgress();
            loadingTasks.postRunnable(() -> {
                final int order = prefs.getRecordsOrder();
                final List<Record> recordList = localRepository.getRecords(page, order);
                AndroidUtils.runOnUIThread(() -> {
                    if (view != null) {
                        view.addRecords(Mapper.recordsToItemType(recordList), order);
                        view.hideProgress();
                        view.hidePanelProgress();
                        view.bookmarksUnselected();
                    }
                });
            });
        }
    }

    @Override
    public void deleteRecords(List<Long> ids) {
        recordingsTasks.postRunnable(() -> {
            for (Long id : ids) {
                localRepository.deleteRecord(id.intValue());
                AndroidUtils.runOnUIThread(() -> {
                    if (view != null) {
                        view.showTrashBtn();
                        view.onDeleteRecord(id);
                    }
                });
            }
            AndroidUtils.runOnUIThread(() -> {
                if (view != null) {
                    view.cancelMultiSelect();
                    view.showMessage(R.string.selected_records_moved_into_trash);
                }
            });
        });
    }

    @Override
    public void applyBookmarksFilter() {
        showBookmarks = !showBookmarks;
        loadBookmarks();
    }

    public void loadBookmarks() {
        if (!showBookmarks) {
            loadRecords();
        } else {
            if (view != null) {
                view.showProgress();
                view.showPanelProgress();
                loadingTasks.postRunnable(() -> {
                    final List<Record> recordList = localRepository.getBookmarks();
                    AndroidUtils.runOnUIThread(() -> {
                        if (view != null) {
                            view.showRecords(Mapper.recordsToItemType(recordList), AppConstants.SORT_DATE);
                            view.hideProgress();
                            view.hidePanelProgress();
                            view.bookmarksSelected();
                            if (recordList.size() == 0) {
                                view.showEmptyBookmarksList();
                            }
                        }
                    });
                });
            }
        }
    }

    @Override
    public void deleteRecord(long id, String path) {
        final Record rec = record;
        if (rec != null && rec.getId() == id) {
            audioPlayer.stop();
        }
        recordingsTasks.postRunnable(() -> {
            localRepository.deleteRecord((int) id);
//				fileRepository.deleteRecordFile(path);
            if (rec != null && rec.getId() == id) {
                prefs.setActiveRecord(-1);
            }
            AndroidUtils.runOnUIThread(() -> {
                if (view != null) {
                    view.showTrashBtn();
                    view.onDeleteRecord(id);
                    view.showMessage(R.string.record_moved_into_trash);
                    if (rec != null && rec.getId() == id) {
                        view.hidePlayPanel();
                        record = null;
                    }
                }
            });
        });
    }


    private void migrateDb3() {
        processingTasks.postRunnable(() -> {
            //Update records table.
            List<Integer> ids = localRepository.getAllItemsIds();
            Record rec;
            for (int i = 0; i < ids.size(); i++) {
                if (ids.get(i) != null) {
                    rec = localRepository.getRecord(ids.get(i));
                    if (rec != null) {
                        RecordInfo info = AudioDecoder.readRecordInfo(new File(rec.getPath()));
                        localRepository.updateRecord(new Record(
                                rec.getId(),
                                FileUtil.removeFileExtension(rec.getName()),
                                rec.getDuration(),
                                rec.getCreated(),
                                rec.getAdded(),
                                rec.getRemoved(),
                                rec.getPath(),
                                info.getFormat(),
                                info.getSize(),
                                info.getSampleRate(),
                                info.getChannelCount(),
                                info.getBitrate(),
                                rec.isBookmarked(),
                                rec.isWaveformProcessed(),
                                rec.getAmps(),
                                1,
                                null,
                                0));
                    }
                }
            }
            //Update trash records table.
            List<Integer> trashIds = localRepository.getTrashRecordsIds();
            Record trashRecord;
            for (int i = 0; i < trashIds.size(); i++) {
                if (trashIds.get(i) != null) {
                    trashRecord = localRepository.getTrashRecord(trashIds.get(i));
                    if (trashRecord != null) {
                        RecordInfo info = AudioDecoder.readRecordInfo(new File(trashRecord.getPath()));
                        localRepository.updateTrashRecord(new Record(
                                trashRecord.getId(),
                                FileUtil.removeFileExtension(trashRecord.getName()),
                                trashRecord.getDuration(),
                                trashRecord.getCreated(),
                                trashRecord.getAdded(),
                                trashRecord.getRemoved(),
                                trashRecord.getPath(),
                                info.getFormat(),
                                info.getSize(),
                                info.getSampleRate(),
                                info.getChannelCount(),
                                info.getBitrate(),
                                trashRecord.isBookmarked(),
                                trashRecord.isWaveformProcessed(),
                                trashRecord.getAmps(),
                                1,
                                null,
                                0));
                    }
                }
            }
            prefs.migrateDb3Finished();
        });
    }

    private String extractFileName(Context context, Uri uri) {
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null, null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
//				TODO: find a better way to extract file extension.
                if (!name.contains(".")) {
                    return name + ".m4a";
                }
                return name;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }
}
