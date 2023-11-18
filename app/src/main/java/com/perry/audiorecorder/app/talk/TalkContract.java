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
import com.perry.audiorecorder.IntArrayList;
import com.perry.audiorecorder.app.info.RecordInfo;
import com.perry.audiorecorder.audio.recorder.RecorderContract;
import com.perry.audiorecorder.data.database.Record;

import java.io.File;
import java.util.List;

public interface TalkContract {

	interface View extends Contract.View {

		/**
		 * 显示提示控件
		 */
		void showNormalTipView();
		/**
		 * 正常录制
		 */
		void showRecordingTipView();
		/**
		 * 松开手指，取消发送
		 */
		void showCancelTipView();
		/**
		 * 语音录入完毕 隐藏提示view
		 */
		void hideTipView();
		/**
		 * 语音说话时的高低峰值差 调整当前音量
		 *
		 * @param db
		 */
		void updateCurrentVolume(int db);

		void keepScreenOn(boolean on);
		void showRecordingStart();
		void showRecordingStop();
		void showRecordingPause();
		void showRecordingResume();
		void onRecordingProgress(long mills, int amp);
		void startWelcomeScreen();

		void askRecordingNewName(long id, File file,  boolean showCheckbox);

		void startRecordingService();

		void startPlaybackService(String name);

		void showPlayStart(boolean animate);
		void showPlayPause();
		void showPlayStop();
		void onPlayProgress(long mills, int percent);

		void showOptionsMenu();
		void hideOptionsMenu();

		void showRecordProcessing();
		void hideRecordProcessing();

		void showWaveForm(int[] waveForm, long duration, long playbackMills);
		void waveFormToStart();
		void showDuration(String duration);
		void showRecordingProgress(String progress);
		void showName(String name);
		void showInformation(String info);
		void decodeRecord(int id);

		void askDeleteRecord(String name);

		void askDeleteRecordForever();

		void showRecordInfo(RecordInfo info);

		void updateRecordingView(IntArrayList data, long durationMills);

		void showRecordsLostMessage(List<Record> list);

		void shareRecord(Record record);

		void openFile(Record record);

		void downloadRecord(Record record);

		void showMigratePublicStorageWarning();
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

		void onRecordInfo();

		void disablePlaybackProgressListener();

		void enablePlaybackProgressListener();
	}
}
