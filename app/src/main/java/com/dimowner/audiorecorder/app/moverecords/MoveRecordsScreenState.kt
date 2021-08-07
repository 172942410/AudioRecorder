package com.dimowner.audiorecorder.app.moverecords

/**
 * Created on 19.06.2021.
 * @author Dimowner
 */
data class MoveRecordsScreenState(
	val playState: PlayState = PlayState.IDLE,
	val list: List<MoveRecordsItem> = emptyList(),
	val showFooterProgressItem: Boolean = false,
	val showProgress: Boolean = false,
	val count: Int = 0,
	val isShowPlayPanel: Boolean = false,
	val activeRecordId: Int = -1,
	val activeRecordPos: Int = -1
)

enum class PlayState {
	IDLE,
	PLAYING,
	PAUSED,
	RECORDING
}