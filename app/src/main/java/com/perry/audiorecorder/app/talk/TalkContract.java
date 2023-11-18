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
import android.net.Uri;

import com.perry.audiorecorder.Contract;
import com.perry.audiorecorder.app.info.RecordInfo;
import com.perry.audiorecorder.app.records.ListItem;
import com.perry.audiorecorder.app.records.RecordsContract;
import com.perry.audiorecorder.audio.recorder.RecorderContract;
import com.perry.audiorecorder.data.database.Record;

import java.io.File;
import java.util.List;

public interface TalkContract {

    interface View extends Contract.View {
        void addRecords(List<ListItem> records, int order);

        void showEmptyBookmarksList();

        void showActiveRecord(int id);

        void bookmarksUnselected();

        void bookmarksSelected();

        void showEmptyList();

        void hidePlayPanel();

        void onDeleteRecord(long id);

        void showTrashBtn();

        void cancelMultiSelect();

        void showRecords(List<ListItem> records, int order);

        void showPlayerPanel();

        void hidePanelProgress();

        void addedToBookmarks(int id, boolean isActive);

        void removedFromBookmarks(int id, boolean isActive);

        void showRecordName(String name);

        /**
         * 发送语音时：显示提示控件
         */
        void showNormalTipView();

        /**
         * 发送语音时：正常录制
         */
        void showRecordingTipView();

        /**
         * 发送语音时：松开手指，取消发送
         */
        void showCancelTipView();

        /**
         * 发送语音时：语音录入完毕 隐藏提示view
         */
        void hideTipView();

        /**
         * 发送语音时：语音说话时的高低峰值差 调整当前音量
         *
         * @param db
         */
        void updateCurrentVolume(int db);

        /**
         * 发送语音时：录制时间太短
         */
        void showRecordTooShortTipView();

        /**
         * 发送语音时：开始录音启动录音服务
         */
        void showRecordingStart();

        /**
         * 发送语音时：这里是获取 音频amp 最大振幅 的地方
         *
         * @param mills
         * @param amp
         */
        void onRecordingProgress(long mills, int amp);

        void keepScreenOn(boolean on);

        void showRecordingStop();

        void showRecordingPause();

        void showRecordingResume();

        void startWelcomeScreen();

        void askRecordingNewName(long id, File file, boolean showCheckbox);

        void startRecordingService();

        void startPlaybackService(String name);

        void showPlayStart(boolean animate);

        void showPlayPause();

        void showPlayStop();

        void onPlayProgress(long mills, int percent);

        void showOptionsMenu();

        void hideOptionsMenu();

        void showWaveForm(int[] waveForm, long duration, long playbackMills);

        void waveFormToStart();

        void showDuration(String duration);

        void showRecordingProgress(String progress);

        void showName(String name);

        void decodeRecord(int id);

        void askDeleteRecord(String name);

        void askDeleteRecordForever();

        void showRecordInfo(RecordInfo info);

        void showRecordsLostMessage(List<Record> list);

        void shareRecord(Record record);

        void openFile(Record record);

        void downloadRecord(Record record);

        void showMigratePublicStorageWarning();

        /**
         * 显示对话列表
         */
        void showList(List<File> list);

        /**
         * 开始播放动画
         *
         * @param position
         */
        void startPlayAnim(int position);

        /**
         * 停止播放动画
         */
        void stopPlayAnim();

        void showPanelProgress();
    }

    interface UserActionsListener extends Contract.UserActionsListener<TalkContract.View> {

        void checkFirstRun();

        void storeInPrivateDir(Context context);

        void setAudioRecorder(RecorderContract.Recorder recorder);

        void pauseUnpauseRecording(Context context);

        void stopRecording(boolean deleteRecord);

        void cancelRecording();

        void startPlayback();

        void seekPlayback(long mills);

        void stopPlayback();

        void renameRecord(long id, String name, String extension);

        void decodeRecord(long id);

        void loadActiveRecord();

        void checkPublicStorageRecords();

        void setAskToRename(boolean value);

        void updateRecordingDir(Context context);

        void setStoragePrivate(Context context);

        void onShareRecordClick();

        void onRenameRecordClick();

        void onOpenFileClick();

        void onSaveAsClick();

        void onDeleteClick();

        //TODO: Remove this getters
        boolean isStorePublic();

        String getActiveRecordPath();

        void deleteActiveRecord(boolean forever);

        void onRecordInfo(RecordInfo info);

        void disablePlaybackProgressListener();

        void enablePlaybackProgressListener();

        void setActiveRecord(long id, RecordsContract.Callback callback);

        void checkBookmarkActiveRecord();

        void addToBookmark(int id);

        void removeFromBookmarks(int id);

        void deleteRecord(long id, String path);

        void loadRecords();

        void applyBookmarksFilter();

        void loadRecordsPage(int page);

        void deleteRecords(List<Long> ids);
    }
}
