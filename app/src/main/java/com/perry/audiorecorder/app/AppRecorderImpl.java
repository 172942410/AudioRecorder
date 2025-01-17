/*
 * Copyright 2020 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.perry.audiorecorder.app;

import static com.perry.audiorecorder.AppConstants.PLAYBACK_VISUALIZATION_INTERVAL;

import com.perry.audiorecorder.ARApplication;
import com.perry.audiorecorder.AppConstants;
import com.perry.audiorecorder.BackgroundQueue;
import com.perry.audiorecorder.IntArrayList;
import com.perry.audiorecorder.app.info.RecordInfo;
import com.perry.audiorecorder.audio.AudioDecoder;
import com.perry.audiorecorder.audio.recorder.RecorderContract;
import com.perry.audiorecorder.data.Prefs;
import com.perry.audiorecorder.data.database.LocalRepository;
import com.perry.audiorecorder.data.database.Record;
import com.perry.audiorecorder.exception.AppException;
import com.perry.audiorecorder.exception.RecordingException;
import com.perry.audiorecorder.util.AndroidUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import timber.log.Timber;

public class AppRecorderImpl implements AppRecorder {

    private final static String TAG = AppRecorderImpl.class.getName();
    private RecorderContract.Recorder audioRecorder;
    private final BackgroundQueue recordingsTasks;

    private final LocalRepository localRepository;
    private final RecorderContract.RecorderCallback recorderCallback;
    private final List<AppRecorderCallback> appCallbacks;
    private final Prefs prefs;
    private final IntArrayList recordingData;
    //	private long recordingDuration;
    private final IntArrayList apmpPool;
    private long durationMills = 0;
    private long updateTime = 0;
    private Timer timerProgress;
    private String recordFilePath = null;

    private volatile static AppRecorderImpl instance;

    public static AppRecorderImpl getInstance(RecorderContract.Recorder recorder,
                                              LocalRepository localRep, BackgroundQueue tasks, Prefs prefs) {
        if (instance == null) {
            synchronized (AppRecorderImpl.class) {
                if (instance == null) {
                    instance = new AppRecorderImpl(recorder, localRep, tasks, prefs);
                }
            }
        }
        return instance;
    }

    private AppRecorderImpl(RecorderContract.Recorder recorder,
                            LocalRepository localRep, BackgroundQueue tasks, Prefs pr) {
        this.audioRecorder = recorder;
        this.localRepository = localRep;
        this.recordingsTasks = tasks;
        this.prefs = pr;
        this.appCallbacks = new ArrayList<>();
        this.recordingData = new IntArrayList();
        this.apmpPool = new IntArrayList();

        recorderCallback = new RecorderContract.RecorderCallback() {

            @Override
            public void onStartRecord(File output) {
//				recordingDuration = 0;
                durationMills = 0;
                scheduleRecordingTimeUpdate();
                onRecordingStarted(output);
            }

            @Override
            public void onPauseRecord() {
                onRecordingPaused();
                pauseRecordingTimer();
            }

            @Override
            public void onResumeRecord() {
                scheduleRecordingTimeUpdate();
                onRecordingResumed();
            }

            @Override
            public void onRecordProgress(final long mills, final int amplitude) {
                apmpPool.add(amplitude);
            }

            @Override
            public void onStopRecord(final File output) {
                stopRecordingTimer();
                recordingsTasks.postRunnable(() -> {
                    RecordInfo info = AudioDecoder.readRecordInfo(output);
                    long duration = info.getDuration();
                    if (duration <= 0) {
                        duration = durationMills;
                    }
//					recordingDuration = 0;
                    durationMills = 0;

                    int[] waveForm = convertRecordingData(recordingData, (int) (duration / 1000000f));
                    final Record record = localRepository.getRecord((int) prefs.getActiveRecord());
                    if (record != null) {
                        record.duration = duration;
                        record.format = info.getFormat();
                        record.size = info.getSize();
                        record.sampleRate = info.getSampleRate();
                        record.channelCount = info.getChannelCount();
                        record.bitrate = info.getBitrate();
                        record.msgType = 1;
                        record.loadStatus = 2;
                        record.amps = AndroidUtils.int2byte(waveForm);
                        if (record.save()) {
                            recordingData.clear();
                            AndroidUtils.runOnUIThread(() -> onRecordingStopped(output, record));
                        } else {
                            //Try to update record again if failed.
                            if (record.save()) {
                                recordingData.clear();
                                AndroidUtils.runOnUIThread(() -> onRecordingStopped(output, record));
                            } else {
                                AndroidUtils.runOnUIThread(() -> onRecordingStopped(output, record));
                            }
                        }
                    } else {
                        AndroidUtils.runOnUIThread(() -> onRecordingError(new RecordingException()));
                    }
                });
            }

            @Override
            public void onError(AppException e) {
                Timber.e(e);
                onRecordingError(e);
            }
        };
        audioRecorder.setRecorderCallback(recorderCallback);
    }

    private int[] convertRecordingData(IntArrayList list, int durationSec) {
        if (durationSec > AppConstants.LONG_RECORD_THRESHOLD_SECONDS) {
            int sampleCount = ARApplication.getLongWaveformSampleCount();
            int[] waveForm = new int[sampleCount];
            if (list.size() < sampleCount * 2) {
                float scale = (float) list.size() / (float) sampleCount;
                for (int i = 0; i < sampleCount; i++) {
                    waveForm[i] = convertAmp(list.get((int) Math.floor(i * scale)));
                }
            } else {
                float scale = (float) list.size() / (float) sampleCount;
                for (int i = 0; i < sampleCount; i++) {
                    int val = 0;
                    int step = (int) Math.ceil(scale);
                    for (int j = 0; j < step; j++) {
                        val += list.get((int) (i * scale + j));
                    }
                    val = (int) ((float) val / scale);
                    waveForm[i] = convertAmp(val);
                }
            }
            return waveForm;
        } else {
            int[] waveForm = new int[list.size()];
            for (int i = 0; i < list.size(); i++) {
                waveForm[i] = convertAmp(list.get(i));
            }
            return waveForm;
        }
    }

    /**
     * Convert dB amp value to view amp.
     */
    private int convertAmp(double amp) {
        return (int) (255 * (amp / 32767f));
    }

    @Override
    public void addRecordingCallback(AppRecorderCallback callback) {
        appCallbacks.add(callback);
    }

    @Override
    public void removeRecordingCallback(AppRecorderCallback callback) {
        appCallbacks.remove(callback);
    }

    @Override
    public void setRecorder(RecorderContract.Recorder recorder) {
        this.audioRecorder = recorder;
        this.audioRecorder.setRecorderCallback(recorderCallback);
    }

    @Override
    public void startRecording(String filePath, int channelCount, int sampleRate, int bitrate) {
        if (!audioRecorder.isRecording()) {
            recordFilePath = filePath;
            audioRecorder.startRecording(filePath, channelCount, sampleRate, bitrate);
        }
    }

    @Override
    public void pauseRecording() {
        if (audioRecorder.isRecording()) {
            audioRecorder.pauseRecording();
        }
    }

    @Override
    public void resumeRecording() {
        if (audioRecorder.isPaused()) {
            audioRecorder.resumeRecording();
        }
    }

    @Override
    public void stopRecording() {
        if (audioRecorder.isRecording()) {
            if (durationMills < 1000) {
                onRecordShort();
            }
            audioRecorder.stopRecording();
        }
    }

    @Override
    public IntArrayList getRecordingData() {
        return recordingData;
    }

    @Override
    public long getRecordingDuration() {
//		return recordingDuration;
        return durationMills;
    }

    @Override
    public boolean isRecording() {
        return audioRecorder.isRecording();
    }

    @Override
    public boolean isPaused() {
        return audioRecorder.isPaused();
    }

    @Override
    public File getRecordFile() {
        if (recordFilePath != null) {
            return new File(recordFilePath);
        }
        return null;
    }

    @Override
    public void release() {
        stopRecordingTimer();
        recordingData.clear();
        apmpPool.clear();
        audioRecorder.stopRecording();
        appCallbacks.clear();
    }

    private void onRecordingStarted(File output) {
        if (!appCallbacks.isEmpty()) {
            for (int i = appCallbacks.size() - 1; i >= 0; i--) {
                appCallbacks.get(i).onRecordingStarted(output);
            }
        }
    }

    private void onRecordingPaused() {
        if (!appCallbacks.isEmpty()) {
            for (int i = appCallbacks.size() - 1; i >= 0; i--) {
                appCallbacks.get(i).onRecordingPaused();
            }
        }
    }

    private void onRecordingResumed() {
        if (!appCallbacks.isEmpty()) {
            for (int i = appCallbacks.size() - 1; i >= 0; i--) {
                appCallbacks.get(i).onRecordingResumed();
            }
        }
    }

    private void onRecordingStopped(File file, Record record) {
        if (!appCallbacks.isEmpty()) {
            for (int i = appCallbacks.size() - 1; i >= 0; i--) {
                appCallbacks.get(i).onRecordingStopped(file, record);
            }
        }
    }

    private void onRecordingProgress(long mills, int amp) {
        if (!appCallbacks.isEmpty()) {
            for (int i = appCallbacks.size() - 1; i >= 0; i--) {
                appCallbacks.get(i).onRecordingProgress(mills, amp);
            }
        }
    }

    private void onRecordingError(AppException e) {
        if (!appCallbacks.isEmpty()) {
            for (int i = appCallbacks.size() - 1; i >= 0; i--) {
                appCallbacks.get(i).onError(e);
            }
        }
    }

    private void onRecordShort() {
        if (!appCallbacks.isEmpty()) {
            for (int i = appCallbacks.size() - 1; i >= 0; i--) {
                appCallbacks.get(i).onRecordShort();
            }
        }
    }


    private void scheduleRecordingTimeUpdate() {
        updateTime = System.currentTimeMillis();
        timerProgress = new Timer();
        timerProgress.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    readProgress();
                } catch (IllegalStateException e) {
                    Timber.e(e);
                }
                long curTime = System.currentTimeMillis();
                durationMills += curTime - updateTime;
                updateTime = curTime;

//				recordingDuration += VISUALIZATION_INTERVAL2;
            }
        }, 0, PLAYBACK_VISUALIZATION_INTERVAL);
    }

    private void readProgress() {
        if (apmpPool.size() > 0) {
            int amp = apmpPool.get(apmpPool.size() - 1);
            apmpPool.clear();
            apmpPool.add(amp);
            recordingData.add(amp);
            onRecordingProgress(durationMills, amp);
        }
    }

    private void stopRecordingTimer() {
        timerProgress.cancel();
        timerProgress.purge();
        updateTime = 0;
    }

    private void pauseRecordingTimer() {
        timerProgress.cancel();
        timerProgress.purge();
        updateTime = 0;
    }
}
