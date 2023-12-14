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

package com.perry.audiorecorder;

import android.content.Context;

import com.perry.audiorecorder.app.AppRecorder;
import com.perry.audiorecorder.app.AppRecorderImpl;
import com.perry.audiorecorder.app.browser.FileBrowserContract;
import com.perry.audiorecorder.app.browser.FileBrowserPresenter;
import com.perry.audiorecorder.app.lostrecords.LostRecordsContract;
import com.perry.audiorecorder.app.lostrecords.LostRecordsPresenter;
import com.perry.audiorecorder.app.moverecords.MoveRecordsViewModel;
import com.perry.audiorecorder.app.settings.SettingsMapper;
import com.perry.audiorecorder.app.setup.SetupContract;
import com.perry.audiorecorder.app.setup.SetupPresenter;
import com.perry.audiorecorder.app.talk.TalkContract;
import com.perry.audiorecorder.app.talk.TalkPresenter;
import com.perry.audiorecorder.app.trash.TrashContract;
import com.perry.audiorecorder.app.trash.TrashPresenter;
import com.perry.audiorecorder.audio.AudioWaveformVisualization;
import com.perry.audiorecorder.audio.player.AudioPlayerNew;
import com.perry.audiorecorder.audio.player.PlayerContractNew;
import com.perry.audiorecorder.audio.recorder.AudioRecorder;
import com.perry.audiorecorder.audio.recorder.ThreeGpRecorder;
import com.perry.audiorecorder.audio.recorder.RecorderContract;
import com.perry.audiorecorder.audio.recorder.WavRecorder;
import com.perry.audiorecorder.data.FileRepository;
import com.perry.audiorecorder.data.FileRepositoryImpl;
import com.perry.audiorecorder.data.Prefs;
import com.perry.audiorecorder.data.PrefsImpl;
import com.perry.audiorecorder.data.database.LocalRepository;
import com.perry.audiorecorder.data.database.LocalRepositoryImpl;
import com.perry.audiorecorder.app.main.MainContract;
import com.perry.audiorecorder.app.main.MainPresenter;
import com.perry.audiorecorder.app.records.RecordsContract;
import com.perry.audiorecorder.app.records.RecordsPresenter;
import com.perry.audiorecorder.app.settings.SettingsContract;
import com.perry.audiorecorder.app.settings.SettingsPresenter;

public class Injector {

	private final Context context;

	private BackgroundQueue loadingTasks;
	private BackgroundQueue recordingTasks;
	private BackgroundQueue importTasks;
	private BackgroundQueue processingTasks;
	private BackgroundQueue copyTasks;

	private TalkContract.UserActionsListener talkPresenter;
	private MainContract.UserActionsListener mainPresenter;
	private RecordsContract.UserActionsListener recordsPresenter;
	private SettingsContract.UserActionsListener settingsPresenter;
	private LostRecordsContract.UserActionsListener lostRecordsPresenter;
	private FileBrowserContract.UserActionsListener fileBrowserPresenter;
	private TrashContract.UserActionsListener trashPresenter;
	private SetupContract.UserActionsListener setupPresenter;

	private MoveRecordsViewModel moveRecordsViewModel;

	private AudioPlayerNew audioPlayer = null;

	public Injector(Context context) {
		this.context = context;
	}

	public Context getContext() {
		return context;
	}

	public Prefs providePrefs() {
		return PrefsImpl.getInstance(context);
	}



	public FileRepository provideFileRepository() {
		return FileRepositoryImpl.getInstance(context, providePrefs());
	}

	public LocalRepository provideLocalRepository() {
		return LocalRepositoryImpl.getInstance(provideFileRepository(), providePrefs());
	}

	public AppRecorder provideAppRecorder() {
		return AppRecorderImpl.getInstance(provideAudioRecorder(), provideLocalRepository(),
				provideLoadingTasksQueue(), providePrefs());
	}

	public AudioWaveformVisualization provideAudioWaveformVisualization() {
		return new AudioWaveformVisualization(provideProcessingTasksQueue());
	}

	public BackgroundQueue provideLoadingTasksQueue() {
		if (loadingTasks == null) {
			loadingTasks = new BackgroundQueue("LoadingTasks");
		}
		return loadingTasks;
	}

	public BackgroundQueue provideRecordingTasksQueue() {
		if (recordingTasks == null) {
			recordingTasks = new BackgroundQueue("RecordingTasks");
		}
		return recordingTasks;
	}

	public BackgroundQueue provideImportTasksQueue() {
		if (importTasks == null) {
			importTasks = new BackgroundQueue("ImportTasks");
		}
		return importTasks;
	}

	public BackgroundQueue provideProcessingTasksQueue() {
		if (processingTasks == null) {
			processingTasks = new BackgroundQueue("ProcessingTasks");
		}
		return processingTasks;
	}

	public BackgroundQueue provideCopyTasksQueue() {
		if (copyTasks == null) {
			copyTasks = new BackgroundQueue("CopyTasks");
		}
		return copyTasks;
	}

	public ColorMap provideColorMap() {
		return ColorMap.getInstance(providePrefs());
	}

	public SettingsMapper provideSettingsMapper() {
		return SettingsMapper.getInstance(context);
	}

	public PlayerContractNew.Player provideAudioPlayer() {
		if (audioPlayer == null) {
			synchronized (PlayerContractNew.Player.class) {
				if (audioPlayer == null) {
					audioPlayer = new AudioPlayerNew();
				}
			}
		}
		return audioPlayer;
	}

	public RecorderContract.Recorder provideAudioRecorder() {
		switch (providePrefs().getSettingRecordingFormat()) {
			default:
			case AppConstants.FORMAT_M4A:
				return AudioRecorder.getInstance();
			case AppConstants.FORMAT_WAV:
				return WavRecorder.getInstance();
			case AppConstants.FORMAT_3GP:
				return ThreeGpRecorder.getInstance();
		}
	}

	public TalkContract.UserActionsListener provideTalkPresenter() {
		if (talkPresenter == null) {
			talkPresenter = new TalkPresenter(providePrefs(), provideFileRepository(),
					provideLocalRepository(), provideAudioPlayer(), provideAppRecorder(),
					provideRecordingTasksQueue(), provideLoadingTasksQueue(), provideProcessingTasksQueue(),
					provideImportTasksQueue(), provideSettingsMapper());
		}
		return talkPresenter;
	}

	public MainContract.UserActionsListener provideMainPresenter() {
		if (mainPresenter == null) {
			mainPresenter = new MainPresenter(providePrefs(), provideFileRepository(),
					provideLocalRepository(), provideAudioPlayer(), provideAppRecorder(),
					provideRecordingTasksQueue(), provideLoadingTasksQueue(), provideProcessingTasksQueue(),
					provideImportTasksQueue(), provideSettingsMapper());
		}
		return mainPresenter;
	}

	public RecordsContract.UserActionsListener provideRecordsPresenter() {
		if (recordsPresenter == null) {
			recordsPresenter = new RecordsPresenter(provideLocalRepository(), provideFileRepository(),
					provideLoadingTasksQueue(), provideRecordingTasksQueue(),
					provideAudioPlayer(), provideAppRecorder(), providePrefs());
		}
		return recordsPresenter;
	}

	public SettingsContract.UserActionsListener provideSettingsPresenter() {
		if (settingsPresenter == null) {
			settingsPresenter = new SettingsPresenter(provideLocalRepository(), provideFileRepository(),
					provideRecordingTasksQueue(), provideLoadingTasksQueue(), providePrefs(),
					provideSettingsMapper(), provideAppRecorder());
		}
		return settingsPresenter;
	}

	public TrashContract.UserActionsListener provideTrashPresenter() {
		if (trashPresenter == null) {
			trashPresenter = new TrashPresenter(provideLoadingTasksQueue(), provideRecordingTasksQueue(),
					provideFileRepository(), provideLocalRepository());
		}
		return trashPresenter;
	}

	public SetupContract.UserActionsListener provideSetupPresenter() {
		if (setupPresenter == null) {
			setupPresenter = new SetupPresenter(providePrefs());
		}
		return setupPresenter;
	}

	public MoveRecordsViewModel provideMoveRecordsViewModel() {
		if (moveRecordsViewModel == null) {
			moveRecordsViewModel = new MoveRecordsViewModel(
					provideLoadingTasksQueue(),
					provideLocalRepository(),
					provideFileRepository(),
					provideSettingsMapper(),
					provideAudioPlayer(),
					provideAppRecorder(),
					providePrefs()
			);
		}
		return moveRecordsViewModel;
	}

	public LostRecordsContract.UserActionsListener provideLostRecordsPresenter() {
		if (lostRecordsPresenter == null) {
			lostRecordsPresenter = new LostRecordsPresenter(provideLoadingTasksQueue(), provideRecordingTasksQueue(),
					provideLocalRepository(), providePrefs());
		}
		return lostRecordsPresenter;
	}

	public FileBrowserContract.UserActionsListener provideFileBrowserPresenter() {
		if (fileBrowserPresenter == null) {
			fileBrowserPresenter = new FileBrowserPresenter(providePrefs(), provideAppRecorder(), provideImportTasksQueue(),
					provideLoadingTasksQueue(), provideRecordingTasksQueue(),
					provideLocalRepository(), provideFileRepository());
		}
		return fileBrowserPresenter;
	}

	public void releaseTrashPresenter() {
		if (trashPresenter != null) {
			trashPresenter.clear();
			trashPresenter = null;
		}
	}

	public void releaseLostRecordsPresenter() {
		if (lostRecordsPresenter != null) {
			lostRecordsPresenter.clear();
			lostRecordsPresenter = null;
		}
	}

	public void releaseFileBrowserPresenter() {
		if (fileBrowserPresenter != null) {
			fileBrowserPresenter.clear();
			fileBrowserPresenter = null;
		}
	}

	public void releaseRecordsPresenter() {
		if (recordsPresenter != null) {
			recordsPresenter.clear();
			recordsPresenter = null;
		}
	}

	public void releaseTalkPresenter() {
		if (talkPresenter != null) {
			talkPresenter.clear();
			talkPresenter = null;
		}
	}

	public void releaseMainPresenter() {
		if (mainPresenter != null) {
			mainPresenter.clear();
			mainPresenter = null;
		}
	}

	public void releaseSettingsPresenter() {
		if (settingsPresenter != null) {
			settingsPresenter.clear();
			settingsPresenter = null;
		}
	}

	public void releaseSetupPresenter() {
		if (setupPresenter != null) {
			setupPresenter.clear();
			setupPresenter = null;
		}
	}

	public void releaseMoveRecordsViewModel() {
		if (moveRecordsViewModel != null) {
			moveRecordsViewModel.clear();
			moveRecordsViewModel = null;
		}
	}

	public void closeTasks() {
		loadingTasks.cleanupQueue();
		loadingTasks.close();
		importTasks.cleanupQueue();
		importTasks.close();
		processingTasks.cleanupQueue();
		processingTasks.close();
		recordingTasks.cleanupQueue();
		recordingTasks.close();
	}
}
