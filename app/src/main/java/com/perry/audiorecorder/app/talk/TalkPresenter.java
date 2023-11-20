/*
 * Copyright 2018 Dmytro Ponomarenko
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

package com.perry.audiorecorder.app.talk;


import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.annotation.NonNull;

import com.perry.audiorecorder.AppConstants;
import com.perry.audiorecorder.BackgroundQueue;
import com.perry.audiorecorder.Mapper;
import com.perry.audiorecorder.R;
import com.perry.audiorecorder.app.AppRecorder;
import com.perry.audiorecorder.app.AppRecorderCallback;
import com.perry.audiorecorder.app.info.RecordInfo;
import com.perry.audiorecorder.app.records.RecordsContract;
import com.perry.audiorecorder.app.settings.SettingsMapper;
import com.perry.audiorecorder.audio.AudioDecoder;
import com.perry.audiorecorder.audio.player.PlayerContractNew;
import com.perry.audiorecorder.audio.recorder.RecorderContract;
import com.perry.audiorecorder.data.FileRepository;
import com.perry.audiorecorder.data.Prefs;
import com.perry.audiorecorder.data.database.LocalRepository;
import com.perry.audiorecorder.data.database.Record;
import com.perry.audiorecorder.exception.AppException;
import com.perry.audiorecorder.exception.ErrorParser;
import com.perry.audiorecorder.util.AndroidUtils;
import com.perry.audiorecorder.util.FileUtil;
import com.perry.audiorecorder.util.TimeUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * Flag true defines that presenter called to show import progress when view was not bind.
     * And after view bind we need to show import progress.
     */
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
                        Log.d(TAG, "每次录音完成这里其实可以只添加最后一条的");
//                        loadRecords();
                        addLastNewRecord(record);
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

        this.localRepository.setOnRecordsLostListener(list -> view.showRecordsLostMessage(list));
    }

    @Override
    public void sendText(String msgStr){
//        1,显示到界面上
        Record recordTemp = Record.createTextRecord(System.currentTimeMillis(),msgStr);
        Record recordReturn = localRepository.insertRecord(recordTemp);
        ItemData itemData = Mapper.recordToItemType(recordReturn);
        view.sendTextShow(itemData);

//        2，发送成功后还需要回调的

    }
    private void addLastNewRecord(Record record) {
        if (view != null) {
            final int order = prefs.getRecordsOrder();
            ArrayList list = new ArrayList<ItemData>();
            record.setAmps(record.byte2int(record.int2byte(record.getAmps())));
            list.add(Mapper.recordToItemType(record));
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
                                null);
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
                                null));
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
                                null));
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
