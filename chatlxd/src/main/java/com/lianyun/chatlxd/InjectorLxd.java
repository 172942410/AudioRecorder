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

package com.lianyun.chatlxd;

import android.content.Context;

import com.lianyun.chatlxd.ui.main.MainContract;
import com.lianyun.chatlxd.ui.main.MainPresenter;
import com.perry.audiorecorder.Injector;

public class InjectorLxd {

	private final Context context;

	private MainContract.UserActionsListener mainPresenter;

	private Injector injector;
	public InjectorLxd(Context context) {
		this.context = context;
	}

	public MainContract.UserActionsListener provideMainPresenter() {
		if (mainPresenter == null) {
			mainPresenter = new MainPresenter(injector.providePrefs(), injector.provideFileRepository(),
					injector.provideLocalRepository(), injector.provideAudioPlayer(), injector.provideAppRecorder(),
					injector.provideRecordingTasksQueue(), injector.provideLoadingTasksQueue(), injector.provideProcessingTasksQueue(),
					injector.provideImportTasksQueue(), injector.provideSettingsMapper());
		}
		return mainPresenter;
	}

	public void releaseMainPresenter() {
		if (mainPresenter != null) {
			mainPresenter.clear();
			mainPresenter = null;
		}
	}

	public void setSuper(Injector injector) {
		this.injector = injector;
	}
}
