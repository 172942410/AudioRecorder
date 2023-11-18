package com.perry.audiorecorder.app.talk;

import android.Manifest;
import android.animation.Animator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.perry.audiorecorder.ARApplication;
import com.perry.audiorecorder.ColorMap;
import com.perry.audiorecorder.Mapper;
import com.perry.audiorecorder.R;
import com.perry.audiorecorder.app.DecodeService;
import com.perry.audiorecorder.app.DecodeServiceListener;
import com.perry.audiorecorder.app.DownloadService;
import com.perry.audiorecorder.app.PlaybackService;
import com.perry.audiorecorder.app.RecordingService;
import com.perry.audiorecorder.app.info.ActivityInformation;
import com.perry.audiorecorder.app.info.RecordInfo;
import com.perry.audiorecorder.app.moverecords.MoveRecordsActivity;
import com.perry.audiorecorder.app.records.RecordsActivity;
import com.perry.audiorecorder.app.records.RecordsContract;
import com.perry.audiorecorder.app.settings.SettingsActivity;
import com.perry.audiorecorder.app.trash.TrashActivity;
import com.perry.audiorecorder.app.welcome.WelcomeActivity;
import com.perry.audiorecorder.app.widget.RecordAudioButton;
import com.perry.audiorecorder.app.widget.RecordVoicePopWindow;
import com.perry.audiorecorder.app.widget.SimpleWaveformView;
import com.perry.audiorecorder.app.widget.TouchLayout;
import com.perry.audiorecorder.app.widget.WaveformViewNew;
import com.perry.audiorecorder.audio.AudioDecoder;
import com.perry.audiorecorder.data.FileRepository;
import com.perry.audiorecorder.data.database.Record;
import com.perry.audiorecorder.exception.CantCreateFileException;
import com.perry.audiorecorder.exception.ErrorParser;
import com.perry.audiorecorder.util.AndroidUtils;
import com.perry.audiorecorder.util.AnimationUtil;
import com.perry.audiorecorder.util.FileUtil;
import com.perry.audiorecorder.util.TimeUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

public class TalkActivity extends Activity implements TalkContract.View, View.OnClickListener {
    private static final String TAG = TalkActivity.class.getName();
    public static final int REQ_CODE_REC_AUDIO_AND_WRITE_EXTERNAL = 101;
    public static final int REQ_CODE_RECORD_AUDIO = 303;
    public static final int REQ_CODE_WRITE_EXTERNAL_STORAGE = 404;
    public static final int REQ_CODE_READ_EXTERNAL_STORAGE_IMPORT = 405;
    public static final int REQ_CODE_READ_EXTERNAL_STORAGE_PLAYBACK = 406;
    public static final int REQ_CODE_READ_EXTERNAL_STORAGE_DOWNLOAD = 407;
    public static final int REQ_CODE_IMPORT_AUDIO = 11;

    LinearLayout mRoot;
    private WaveformViewNew waveformView;
    private TextView txtProgress;
    private TextView txtDuration;
    private TextView txtName;
    private ImageButton btnPlay;
    private ImageButton btnStop;
    private ImageButton btnShare;
    private SeekBar playProgress;

    private TalkContract.UserActionsListener presenter;
    private ColorMap colorMap;
    private FileRepository fileRepository;
    private ColorMap.OnThemeColorChangeListener onThemeColorChangeListener;

    RecordAudioButton mBtnVoice;//底部录制按钮
    private RecordVoicePopWindow mRecordVoicePopWindow;//提示

    int delayMillis = 0;//是否需要延迟关闭对话框

    private TalkAdapter talkAdapter; //适配器
    RecyclerView recyclerView;//消息列表

    private ProgressBar panelProgress;

    private ImageButton btnCheckBookmark;

    private TouchLayout touchLayout;
    private TextView txtEmpty;
    private TextView txtSelectedCount;
    final private List<String> downloadRecords = new ArrayList<>();

    private View multiSelectPanel;
    private LinearLayout toolbar;

    private ImageButton btnBookmarks;
    private TextView txtTitle;
    private ProgressBar progressBar;

    private ImageButton btnCloseMulti;
    private ImageButton btnShareMulti;
    private ImageButton btnDeleteMulti;
    private ImageButton btnDownloadMulti;

    private final ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            DecodeService.LocalBinder binder = (DecodeService.LocalBinder) service;
            DecodeService decodeService = binder.getService();
            decodeService.setDecodeListener(new DecodeServiceListener() {
                @Override
                public void onStartProcessing() {
                    Log.d(TAG, "onStartProcessing");
//                    runOnUiThread(TalkActivity.this::showRecordProcessing);
                }

                @Override
                public void onFinishProcessing() {
                    Log.d(TAG, "onFinishProcessing");
                    runOnUiThread(() -> {
//                        hideRecordProcessing();
                        presenter.loadActiveRecord();
                    });
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
//            hideRecordProcessing();
        }

        @Override
        public void onBindingDied(ComponentName name) {
//            hideRecordProcessing();
        }
    };

    private float space = 75;

    public static Intent getStartIntent(Context context) {
        return new Intent(context, TalkActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        colorMap = ARApplication.getInjector().provideColorMap();
        SimpleWaveformView.setWaveformColorRes(colorMap.getPrimaryColorRes());
        setTheme(colorMap.getAppThemeResource());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_talk);
        mRoot = findViewById(R.id.root);
        waveformView = findViewById(R.id.record);
        txtProgress = findViewById(R.id.txt_progress);
        txtDuration = findViewById(R.id.txt_duration);
        txtName = findViewById(R.id.txt_name);
        btnPlay = findViewById(R.id.btn_play);
        btnStop = findViewById(R.id.btn_stop);
        ImageButton btnSettings = findViewById(R.id.btn_settings);
        btnShare = findViewById(R.id.btn_share);
        playProgress = findViewById(R.id.play_progress);


        btnCloseMulti = findViewById(R.id.btn_close_multi_select);
        btnCloseMulti.setOnClickListener(this);
        btnShareMulti = findViewById(R.id.btn_share_multi);
        btnDeleteMulti = findViewById(R.id.btn_delete_multi);
        btnDownloadMulti = findViewById(R.id.btn_download_multi);
        btnShareMulti.setOnClickListener(this);
        btnDeleteMulti.setOnClickListener(this);
        btnDownloadMulti.setOnClickListener(this);
        progressBar = findViewById(R.id.progress);
        txtTitle = findViewById(R.id.txt_title);
        btnBookmarks = findViewById(R.id.btn_bookmarks);
        btnBookmarks.setOnClickListener(this);
        toolbar = findViewById(R.id.toolbar);
        multiSelectPanel = findViewById(R.id.menu_multi_select);
        multiSelectPanel.setBackgroundResource(colorMap.getPrimaryDarkColorRes());
        txtSelectedCount = findViewById(R.id.txt_selected_multi);
        txtEmpty = findViewById(R.id.txtEmpty);
        touchLayout = findViewById(R.id.touch_layout);
        touchLayout.setBackgroundResource(colorMap.getPlaybackPanelBackground());
        touchLayout.setOnThresholdListener(new TouchLayout.ThresholdListener() {
            @Override
            public void onTopThreshold() {
                hidePanel();
                presenter.stopPlayback();
            }

            @Override
            public void onBottomThreshold() {
                hidePanel();
                presenter.stopPlayback();
            }

            @Override
            public void onTouchDown() {
            }

            @Override
            public void onTouchUp() {
            }
        });
        btnCheckBookmark = findViewById(R.id.btn_check_bookmark);
        btnCheckBookmark.setOnClickListener(this);
        panelProgress = findViewById(R.id.wave_progress);
        mBtnVoice = findViewById(R.id.btnVoice);
        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(TalkActivity.this));
        presenter = ARApplication.getInjector().provideTalkPresenter();
        talkAdapter = new TalkAdapter(ARApplication.getInjector().provideSettingsMapper(),TalkActivity.this,colorMap,presenter);
        talkAdapter.setItemClickListener((view, id, path, position) -> presenter.setActiveRecord(id, new RecordsContract.Callback() {
            @Override
            public void onSuccess() {
                presenter.stopPlayback();
                if (startPlayback()) {
                    talkAdapter.setActiveItem(position);
                }
            }

            @Override
            public void onError(Exception e) {
                Timber.e(e);
            }
        }));
        talkAdapter.setOnAddToBookmarkListener(new TalkAdapter.OnAddToBookmarkListener() {
            @Override
            public void onAddToBookmarks(int id) {
                presenter.addToBookmark(id);
            }

            @Override
            public void onRemoveFromBookmarks(int id) {
                presenter.removeFromBookmarks(id);
            }
        });
        talkAdapter.setOnItemOptionListener((menuId, item) -> {
            if (menuId == R.id.menu_share) {
                AndroidUtils.shareAudioFile(getApplicationContext(), item.getPath(), item.getName(), item.getFormat());
            } else if (menuId == R.id.menu_info) {
                presenter.onRecordInfo(Mapper.toRecordInfo(item));
            } else if (menuId == R.id.menu_rename) {
                setRecordName(item.getId(), item.getName(), item.getFormat());
            } else if (menuId == R.id.menu_open_with) {
                AndroidUtils.openAudioFile(getApplicationContext(), item.getPath(), item.getName());
            } else if (menuId == R.id.menu_save_as) {
                if (isPublicDir(item.getPath())) {
                    if (checkStoragePermissionDownload()) {
                        //Download record file with Service
                        DownloadService.startNotification(getApplicationContext(), item.getPath());
                    } else {
                        downloadRecords.add(item.getPath());
                    }
                } else {
                    //Download record file with Service
                    DownloadService.startNotification(getApplicationContext(), item.getPath());
                }
            } else if (menuId == R.id.menu_delete) {
                AndroidUtils.showDialogYesNo(TalkActivity.this, R.drawable.ic_delete_forever_dark, getString(R.string.warning), getString(R.string.delete_record, item.getName()), v -> presenter.deleteRecord(item.getId(), item.getPath()));
            }
        });
        talkAdapter.setBtnTrashClickListener(() -> startActivity(TrashActivity.getStartIntent(getApplicationContext())));
        talkAdapter.setOnMultiSelectModeListener(new TalkAdapter.OnMultiSelectModeListener() {
            @Override
            public void onMultiSelectMode(boolean selected) {
                stopPlayback();
                if (selected) {
                    multiSelectPanel.setVisibility(View.VISIBLE);
                } else {
                    multiSelectPanel.setVisibility(View.GONE);
                }
            }

            @Override
            public void onSelectDeselect(int selectedCount) {
                txtSelectedCount.setText(getResources().getString(R.string.selected, selectedCount));
            }
        });
        recyclerView.setAdapter(talkAdapter);


        mBtnVoice.setOnVoiceButtonCallBack(new RecordAudioButton.OnVoiceButtonCallBack() {
            @Override
            public void onStartRecord() {
                startRecordingService();
//				startRecording();
                Log.d(TAG, "开始录音");
            }

            @Override
            public void onStopRecord() {
                presenter.stopRecording(false);
                Log.d(TAG, "停止录音；最终执行：录音完毕");
            }

            @Override
            public void onWillCancelRecord() {
//				presenter.stopRecording(true);
//				presenter.willCancelRecord();
                Log.d(TAG, "即将取消录音；只在界面UI上有所变化录音继续");
                showCancelTipView();
            }

            @Override
            public void onCancelRecord() {
                //TODO 取消发送录音；删除录音文件
                presenter.stopRecording(true);
            }

            @Override
            public void onContinueRecord() {
//				presenter.continueRecord();
                Log.d(TAG, "持续录音中");
                showRecordingTipView();
            }
        });
        txtProgress.setText(TimeUtils.formatTimeIntervalHourMinSec2(0));
        btnPlay.setOnClickListener(this);
        btnStop.setOnClickListener(this);
        btnSettings.setOnClickListener(this);
        btnShare.setOnClickListener(this);
        txtName.setOnClickListener(this);
        space = getResources().getDimension(R.dimen.spacing_xnormal);

        playProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int val = (int) AndroidUtils.dpToPx(progress * waveformView.getWaveformLength() / 1000);
                    waveformView.seekPx(val);
                    //TODO: Find a better way to convert px to mills here
                    presenter.seekPlayback(waveformView.pxToMill(val));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                presenter.disablePlaybackProgressListener();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                presenter.enablePlaybackProgressListener();
            }
        });

        fileRepository = ARApplication.getInjector().provideFileRepository();

        waveformView.setOnSeekListener(new WaveformViewNew.OnSeekListener() {
            @Override
            public void onStartSeek() {
                presenter.disablePlaybackProgressListener();
            }

            @Override
            public void onSeek(int px, long mills) {
                presenter.enablePlaybackProgressListener();
                //TODO: Find a better way to convert px to mills here
                presenter.seekPlayback(waveformView.pxToMill(px));

                int length = waveformView.getWaveformLength();
                if (length > 0) {
                    playProgress.setProgress(1000 * (int) AndroidUtils.pxToDp(px) / length);
                }
                txtProgress.setText(TimeUtils.formatTimeIntervalHourMinSec2(mills));
            }

            @Override
            public void onSeeking(int px, long mills) {
                int length = waveformView.getWaveformLength();
                if (length > 0) {
                    playProgress.setProgress(1000 * (int) AndroidUtils.pxToDp(px) / length);
                }
                txtProgress.setText(TimeUtils.formatTimeIntervalHourMinSec2(mills));
            }
        });
        onThemeColorChangeListener = colorKey -> {
            setTheme(colorMap.getAppThemeResource());
            recreate();
        };
        colorMap.addOnThemeColorChangeListener(onThemeColorChangeListener);

        //Check start recording shortcut
        if ("android.intent.action.ACTION_RUN".equals(getIntent().getAction())) {
            if (checkRecordPermission2()) {
                if (checkStoragePermission2()) {
                    //Start or stop recording
                    startRecordingService();
                }
            }
        }
    }

    @Override
    public void hidePlayPanel() {
        hidePanel();
    }

    @Override
    public void onDeleteRecord(long id) {
        talkAdapter.deleteItem(id);
        if (talkAdapter.getAudioRecordsCount() == 0) {
            showEmptyList();
        }
    }

    @Override
    public void addRecords(List<ItemType> records, int order) {
        talkAdapter.addData(records, order);
        txtEmpty.setVisibility(View.GONE);
    }

    @Override
    public void showEmptyBookmarksList() {
        txtEmpty.setText(R.string.no_bookmarks);
        txtEmpty.setVisibility(View.VISIBLE);
    }

    @Override
    public void showActiveRecord(int id) {
        int pos = talkAdapter.findPositionById(id);
        if (pos >= 0) {
            talkAdapter.setActiveItem(pos);
        }
    }

    @Override
    public void bookmarksUnselected() {
        btnBookmarks.setImageResource(R.drawable.ic_bookmark_bordered);
        txtTitle.setText(R.string.records);
    }

    @Override
    public void bookmarksSelected() {
        btnBookmarks.setImageResource(R.drawable.ic_bookmark);
        txtTitle.setText(R.string.bookmarks);
    }

    @Override
    public void showEmptyList() {
        txtEmpty.setText(R.string.no_records);
        txtEmpty.setVisibility(View.VISIBLE);
    }

    @Override
    public void showTrashBtn() {
        talkAdapter.showTrash(true);
    }

    @Override
    public void cancelMultiSelect() {
        multiSelectPanel.setVisibility(View.GONE);
        talkAdapter.cancelMultiSelect();
    }

    private void stopPlayback() {
        presenter.stopPlayback();
        hidePanel();
    }

    @Override
    public void startPlayAnim(int position) {
//		mAdapter.startPlayAnim(position);
    }

    @Override
    public void stopPlayAnim() {
//		mAdapter.stopPlayAnim();
    }

    private boolean startPlayback() {
        String path = presenter.getActiveRecordPath();
        if (FileUtil.isFileInExternalStorage(getApplicationContext(), path)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                AndroidUtils.showRecordFileNotAvailable(this, path);
                return false;
            } else if (checkStoragePermissionPlayback()) {
                presenter.startPlayback();
                return true;
            }
        } else {
            presenter.startPlayback();
            return true;
        }
        return false;
    }

    @Override
    public void showRecords(List<ItemType> records, int order) {
        if (records.size() == 0) {
            txtEmpty.setVisibility(View.VISIBLE);
            talkAdapter.setData(new ArrayList<>(), order);
        } else {
            talkAdapter.setData(records, order);
            txtEmpty.setVisibility(View.GONE);
            if (touchLayout.getVisibility() == View.VISIBLE) {
                talkAdapter.showFooter();
            }
        }
    }

    private void showToolbar() {
        AnimationUtil.viewAnimationY(toolbar, 0f, null);
    }

    public void hidePanel() {
        if (touchLayout.getVisibility() == View.VISIBLE) {
            talkAdapter.hideFooter();
            showToolbar();
            final ViewPropertyAnimator animator = touchLayout.animate();
            animator.translationY(touchLayout.getHeight())
                    .setDuration(200)
                    .setListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            touchLayout.setVisibility(View.GONE);
                            animator.setListener(null);
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {
                        }

                        @Override
                        public void onAnimationRepeat(Animator animation) {
                        }
                    })
                    .start();
        }
    }

    @Override
    public void showPanelProgress() {
        panelProgress.setVisibility(View.VISIBLE);
    }

    @Override
    public void showRecordTooShortTipView() {
        delayMillis = 500;
        if (mRecordVoicePopWindow != null) {
            if (mRecordVoicePopWindow.isShowing()) {
                Log.d(TAG, "这里判断弹框在 显示中...");
            } else {
                Log.d(TAG, "这里判断弹框 没有显示");
                mRecordVoicePopWindow.showAsDropDown(mRoot);
                handlerClosePop.sendEmptyMessageDelayed(0, delayMillis);
            }
            mRecordVoicePopWindow.showRecordTooShortTipView();
        }
    }

    @Override
    public void updateCurrentVolume(int db) {
        if (mRecordVoicePopWindow != null) {
            mRecordVoicePopWindow.updateCurrentVolume(db);
        }
    }

    //延迟关闭窗口
    Handler handlerClosePop = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message message) {
            if (mRecordVoicePopWindow != null) {
                mRecordVoicePopWindow.dismiss();
            }
            return false;
        }
    });

    /**
     * 语音录入完毕 隐藏提示view
     */
    @Override
    public void hideTipView() {
        if (mRecordVoicePopWindow != null) {
            if (delayMillis > 0) {
                handlerClosePop.sendEmptyMessageDelayed(0, delayMillis);
            } else {
                mRecordVoicePopWindow.dismiss();
            }

        }
    }

    @Override
    public void showPlayerPanel() {
        if (touchLayout.getVisibility() != View.VISIBLE) {
            touchLayout.setVisibility(View.VISIBLE);
            if (touchLayout.getHeight() == 0) {
                touchLayout.setTranslationY(AndroidUtils.dpToPx(800));
            } else {
                touchLayout.setTranslationY(touchLayout.getHeight());
            }
            talkAdapter.showFooter();
            final ViewPropertyAnimator animator = touchLayout.animate();
            animator.translationY(0)
                    .setDuration(200)
                    .setListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            int o = recyclerView.computeVerticalScrollOffset();
                            int r = recyclerView.computeVerticalScrollRange();
                            int e = recyclerView.computeVerticalScrollExtent();
                            float k = (float) o / (float) (r - e);
                            recyclerView.smoothScrollBy(0, (int) (touchLayout.getHeight() * k));
                            animator.setListener(null);
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {
                        }

                        @Override
                        public void onAnimationRepeat(Animator animation) {
                        }
                    })
                    .start();
        }
    }

    private void downloadSelectedRecords() {
        downloadRecords.clear();
        List<Integer> selected = talkAdapter.getSelected();
        for (int i = 0; i < selected.size(); i++) {
            ItemType item = talkAdapter.getItem(selected.get(i));
            downloadRecords.add(item.getPath());
        }
        boolean hasPublicDir = false;
        for (int i = 0; i < downloadRecords.size(); i++) {
            if (isPublicDir(downloadRecords.get(i))) {
                hasPublicDir = true;
                break;
            }
        }
        if (hasPublicDir) {
            if (checkStoragePermissionDownload()) {
                //Download record file with Service
                DownloadService.startNotification(
                        getApplicationContext(),
                        new ArrayList<>(downloadRecords)
                );
            } else {
                showError(getResources().getQuantityString(
                        R.plurals.downloading_failed_count,
                        downloadRecords.size(),
                        downloadRecords.size())
                );
            }
        } else {
            //Download record file with Service
            DownloadService.startNotification(
                    getApplicationContext(),
                    new ArrayList<>(downloadRecords)
            );
        }
        cancelMultiSelect();
        downloadRecords.clear();
    }

    @Override
    public void hidePanelProgress() {
        panelProgress.setVisibility(View.GONE);
    }

    @Override
    public void addedToBookmarks(int id, boolean isActive) {
        if (isActive) {
            btnCheckBookmark.setImageResource(R.drawable.ic_bookmark);
        }
        talkAdapter.markAddedToBookmarks(id);
    }

    @Override
    public void removedFromBookmarks(int id, boolean isActive) {
        if (isActive) {
            btnCheckBookmark.setImageResource(R.drawable.ic_bookmark_bordered);
        }
        talkAdapter.markRemovedFromBookmarks(id);
    }

    @Override
    public void showRecordName(String name) {
        txtName.setText(name);
    }

    @Override
    public void showNormalTipView() {
        if (mRecordVoicePopWindow == null) {
            mRecordVoicePopWindow = new RecordVoicePopWindow(TalkActivity.this);
        }
        mRecordVoicePopWindow.showAsDropDown(mRoot);
        delayMillis = 0;
    }

    /**
     * 按住说话的状态显示界面
     */
    @Override
    public void showRecordingTipView() {
        if (mRecordVoicePopWindow != null) {
            mRecordVoicePopWindow.showRecordingTipView();
        }
    }

    /**
     * 向上滑动取消录音的UI界面显示
     */
    @Override
    public void showCancelTipView() {
        if (mRecordVoicePopWindow != null) {
            mRecordVoicePopWindow.showCancelTipView();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        presenter.bindView(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            //This is needed for scoped storage support
            presenter.storeInPrivateDir(getApplicationContext());
//			presenter.checkPublicStorageRecords();
        }
        presenter.checkFirstRun();
        presenter.setAudioRecorder(ARApplication.getInjector().provideAudioRecorder());
        presenter.updateRecordingDir(getApplicationContext());
        presenter.loadActiveRecord();
        presenter.loadRecords();
        Intent intent = new Intent(this, DecodeService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(connection);
        if (presenter != null) {
            presenter.unbindView();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        colorMap.removeOnThemeColorChangeListener(onThemeColorChangeListener);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.btn_play) {
            String path = presenter.getActiveRecordPath();
            //This method Starts or Pause playback.
            if (FileUtil.isFileInExternalStorage(getApplicationContext(), path)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    AndroidUtils.showRecordFileNotAvailable(this, path);
                } else if (checkStoragePermissionPlayback()) {
                    presenter.startPlayback();
                }
            } else {
                presenter.startPlayback();
            }
        } else if (id == R.id.btn_record) {
            if (checkRecordPermission2()) {
                if (checkStoragePermission2()) {
                    //Start or stop recording
                    startRecordingService();
                    presenter.pauseUnpauseRecording(getApplicationContext());
                }
            }
        } else if (id == R.id.btn_record_stop) {
            presenter.stopRecording(false);
        } else if (id == R.id.btn_record_delete) {
            presenter.cancelRecording();
        } else if (id == R.id.btn_stop) {
            presenter.stopPlayback();
        } else if (id == R.id.btn_records_list) {
            startActivity(RecordsActivity.getStartIntent(getApplicationContext()));
        } else if (id == R.id.btn_settings) {
            startActivity(SettingsActivity.getStartIntent(getApplicationContext()));
        } else if (id == R.id.btn_share) {
            showMenu(view);
        } else if (id == R.id.btn_import) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startFileSelector();
            } else {
                if (checkStoragePermissionImport()) {
                    startFileSelector();
                }
            }
        } else if (id == R.id.txt_name) {
            presenter.onRenameRecordClick();
        } else if (id == R.id.btn_check_bookmark) {
            presenter.checkBookmarkActiveRecord();
        } else if (id == R.id.btn_bookmarks) {
            presenter.applyBookmarksFilter();
        } else if (id == R.id.btn_close_multi_select) {
            cancelMultiSelect();
        } else if (id == R.id.btn_delete_multi) {
            int count = talkAdapter.getSelected().size();
            AndroidUtils.showDialogYesNo(
                    TalkActivity.this,
                    R.drawable.ic_delete_forever_dark,
                    getString(R.string.warning),
                    this.getResources().getQuantityString(R.plurals.delete_selected_records, count, count),
                    v -> deleteSelectedRecords()
            );
        } else if (id == R.id.btn_share_multi) {
            shareSelectedRecords();
        } else if (id == R.id.btn_download_multi) {
            int count = talkAdapter.getSelected().size();
            AndroidUtils.showDialogYesNo(
                    TalkActivity.this,
                    R.drawable.ic_save_alt_dark,
                    getString(R.string.save_as),
                    this.getResources().getQuantityString(R.plurals.download_selected_records, count, count),
                    v -> downloadSelectedRecords()
            );
        }
    }

    private void shareSelectedRecords() {
        List<Integer> selected = talkAdapter.getSelected();
        List<String> share = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            ItemType item = talkAdapter.getItem(selected.get(i));
            share.add(item.getPath());
        }
        AndroidUtils.shareAudioFiles(getApplicationContext(), share);
        cancelMultiSelect();
    }

    private void deleteSelectedRecords() {
        List<Long> ids = new ArrayList<>();
        List<Integer> selected = talkAdapter.getSelected();
        for (int i = 0; i < selected.size(); i++) {
            ItemType item = talkAdapter.getItem(selected.get(i));
            ids.add(item.getId());
        }
        presenter.deleteRecords(ids);
    }

    private void startFileSelector() {
        Intent intent_upload = new Intent();
        intent_upload.setType("audio/*");
        intent_upload.addCategory(Intent.CATEGORY_OPENABLE);
//		intent_upload.setAction(Intent.ACTION_GET_CONTENT);
        intent_upload.setAction(Intent.ACTION_OPEN_DOCUMENT);
        try {
            startActivityForResult(intent_upload, REQ_CODE_IMPORT_AUDIO);
        } catch (ActivityNotFoundException e) {
            Timber.e(e);
            showError(R.string.cant_import_files);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void keepScreenOn(boolean on) {
        if (on) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public void showProgress() {
        progressBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideProgress() {
        progressBar.setVisibility(View.GONE);
    }

    @Override
    public void showError(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void showError(int resId) {
        Toast.makeText(getApplicationContext(), resId, Toast.LENGTH_LONG).show();
    }

    @Override
    public void showMessage(int resId) {
        Toast.makeText(getApplicationContext(), resId, Toast.LENGTH_LONG).show();
    }

    @Override
    public void showRecordingStart() {
        txtName.setClickable(false);
        txtName.setFocusable(false);
        txtName.setCompoundDrawables(null, null, null, null);
        txtName.setVisibility(View.VISIBLE);
        txtName.setText(R.string.recording_progress);
        txtDuration.setVisibility(View.INVISIBLE);
        btnPlay.setEnabled(false);
        btnShare.setEnabled(false);
        btnPlay.setVisibility(View.GONE);
        btnShare.setVisibility(View.GONE);
        playProgress.setProgress(0);
        playProgress.setEnabled(false);
        txtDuration.setText(R.string.zero_time);
        waveformView.setVisibility(View.GONE);
    }

    @Override
    public void showRecordingStop() {
        txtName.setClickable(true);
        txtName.setFocusable(true);
        txtDuration.setVisibility(View.VISIBLE);
        txtName.setCompoundDrawablesWithIntrinsicBounds(null, null, getDrawable(R.drawable.ic_pencil_small), null);
        btnPlay.setEnabled(true);
        btnShare.setEnabled(true);
        btnPlay.setVisibility(View.VISIBLE);
        btnShare.setVisibility(View.VISIBLE);
        playProgress.setEnabled(true);
        waveformView.setVisibility(View.VISIBLE);
        txtProgress.setText(TimeUtils.formatTimeIntervalHourMinSec2(0));
    }

    @Override
    public void showRecordingPause() {
        txtName.setClickable(false);
        txtName.setFocusable(false);
        txtName.setCompoundDrawables(null, null, null, null);
        txtName.setText(R.string.recording_paused);
        txtName.setVisibility(View.VISIBLE);
        btnPlay.setEnabled(false);
        btnShare.setEnabled(false);
        btnPlay.setVisibility(View.GONE);
        btnShare.setVisibility(View.GONE);
        playProgress.setEnabled(false);
    }

    @Override
    public void showRecordingResume() {
        txtName.setClickable(false);
        txtName.setFocusable(false);
        txtName.setCompoundDrawables(null, null, null, null);
        txtName.setVisibility(View.VISIBLE);
        txtName.setText(R.string.recording_progress);
        txtDuration.setVisibility(View.INVISIBLE);
        btnPlay.setEnabled(false);
        btnShare.setEnabled(false);
        btnPlay.setVisibility(View.GONE);
        btnShare.setVisibility(View.GONE);
        playProgress.setProgress(0);
        playProgress.setEnabled(false);
        txtDuration.setText(R.string.zero_time);
    }

    @Override
    public void askRecordingNewName(long id, File file, boolean showCheckbox) {
        setRecordName(id, file, showCheckbox);
    }

    @Override
    public void onRecordingProgress(long mills, int amp) {
        runOnUiThread(() -> {
            txtProgress.setText(TimeUtils.formatTimeIntervalHourMinSec2(mills));
            Log.d(TAG, "amp:" + amp);
            updateCurrentVolume(amp);
        });
    }

    @Override
    public void startWelcomeScreen() {
        startActivity(WelcomeActivity.getStartIntent(getApplicationContext()));
        finish();
    }

    @Override
    public void startRecordingService() {
        showNormalTipView();
        try {
            String path = fileRepository.provideRecordFile().getAbsolutePath();
            Intent intent = new Intent(getApplicationContext(), RecordingService.class);
            intent.setAction(RecordingService.ACTION_START_RECORDING_SERVICE);
            intent.putExtra(RecordingService.EXTRAS_KEY_RECORD_PATH, path);
            startService(intent);
        } catch (CantCreateFileException e) {
            showError(ErrorParser.parseException(e));
        }
    }

    @Override
    public void startPlaybackService(final String name) {
        PlaybackService.startServiceForeground(getApplicationContext(), name);
    }

    @Override
    public void showPlayStart(boolean animate) {
        if (animate) {
            AnimationUtil.viewAnimationX(btnPlay, -space, new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    btnStop.setVisibility(View.VISIBLE);
                    btnPlay.setImageResource(R.drawable.ic_pause);
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }
            });
        } else {
            btnPlay.setTranslationX(-space);
            btnStop.setVisibility(View.VISIBLE);
            btnPlay.setImageResource(R.drawable.ic_pause);
        }
    }

    @Override
    public void showPlayPause() {
        btnStop.setVisibility(View.VISIBLE);
        btnPlay.setTranslationX(-space);
        btnPlay.setImageResource(R.drawable.ic_play);
    }

    @Override
    public void showPlayStop() {
        btnPlay.setImageResource(R.drawable.ic_play);
        waveformView.moveToStart();
        playProgress.setProgress(0);
        talkAdapter.setProgress(0);
        txtProgress.setText(TimeUtils.formatTimeIntervalHourMinSec2(0));
        AnimationUtil.viewAnimationX(btnPlay, 0f, new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                btnStop.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });
    }

    @Override
    public void showWaveForm(int[] waveForm, long duration, long playbackMills) {
        if (waveForm.length > 0) {
            btnPlay.setVisibility(View.VISIBLE);
            txtDuration.setVisibility(View.VISIBLE);
            waveformView.setVisibility(View.VISIBLE);
        } else {
            btnPlay.setVisibility(View.INVISIBLE);
            txtDuration.setVisibility(View.INVISIBLE);
            waveformView.setVisibility(View.INVISIBLE);
        }
        waveformView.setWaveform(waveForm, duration / 1000, playbackMills);
    }

    @Override
    public void waveFormToStart() {
        waveformView.seekPx(0);
    }

    @Override
    public void showDuration(final String duration) {
        txtDuration.setText(duration);
    }

    @Override
    public void showRecordingProgress(String progress) {
        txtProgress.setText(progress);
    }

    @Override
    public void showName(String name) {
        if (name == null || name.isEmpty()) {
            txtName.setVisibility(View.INVISIBLE);
        } else {
            txtName.setVisibility(View.VISIBLE);
        }
        txtName.setText(name);
    }

    @Override
    public void decodeRecord(int id) {
        DecodeService.Companion.startNotification(getApplicationContext(), id);
    }

    @Override
    public void askDeleteRecord(String name) {
        AndroidUtils.showDialogYesNo(TalkActivity.this, R.drawable.ic_delete_forever_dark, getString(R.string.warning), getString(R.string.delete_record, name), v -> presenter.deleteActiveRecord(false));
    }

    @Override
    public void askDeleteRecordForever() {
        AndroidUtils.showDialogYesNo(TalkActivity.this, R.drawable.ic_delete_forever_dark, getString(R.string.warning), getString(R.string.delete_this_record), v -> presenter.stopRecording(true));
    }

    @Override
    public void showRecordInfo(RecordInfo info) {
        startActivity(ActivityInformation.getStartIntent(getApplicationContext(), info));
    }

    @Override
    public void showRecordsLostMessage(List<Record> list) {
        AndroidUtils.showLostRecordsDialog(this, list);
    }

    @Override
    public void shareRecord(Record record) {
        AndroidUtils.shareAudioFile(getApplicationContext(), record.getPath(), record.getName(), record.getFormat());
    }

    @Override
    public void openFile(Record record) {
        AndroidUtils.openAudioFile(getApplicationContext(), record.getPath(), record.getName());
    }

    @Override
    public void downloadRecord(Record record) {
        if (isPublicDir(record.getPath())) {
            if (checkStoragePermissionDownload()) {
                DownloadService.startNotification(getApplicationContext(), record.getPath());
            }
        } else {
            DownloadService.startNotification(getApplicationContext(), record.getPath());
        }
    }

    private boolean isPublicDir(String path) {
        return path.contains(FileUtil.getAppDir().getAbsolutePath());
    }

    @Override
    public void showMigratePublicStorageWarning() {
        AndroidUtils.showDialog(this, R.drawable.ic_warning_yellow, R.string.view_records, R.string.later, R.string.move_records_needed, R.string.move_records_info, false, v -> {
            startActivity(MoveRecordsActivity.Companion.getStartIntent(getApplicationContext(), false));
        }, v -> {
        });
    }

    @Override
    public void showList(List<File> list) {
//		mAdapter.setNewData(list);
    }

    @Override
    public void onPlayProgress(final long mills, int percent) {
        playProgress.setProgress(percent);
        talkAdapter.setProgress(percent);
        waveformView.setPlayback(mills);
        txtProgress.setText(TimeUtils.formatTimeIntervalHourMinSec2(mills));
    }

    @Override
    public void showOptionsMenu() {
        btnShare.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideOptionsMenu() {
        btnShare.setVisibility(View.INVISIBLE);
    }

    private void showMenu(View v) {
        PopupMenu popup = new PopupMenu(v.getContext(), v);
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_share) {
                presenter.onShareRecordClick();
            } else if (id == R.id.menu_rename) {
                presenter.onRenameRecordClick();
            } else if (id == R.id.menu_open_with) {
                presenter.onOpenFileClick();
            } else if (id == R.id.menu_save_as) {
                AndroidUtils.showDialogYesNo(TalkActivity.this, R.drawable.ic_save_alt_dark, getString(R.string.save_as), getString(R.string.record_will_be_copied_into_downloads), view -> presenter.onSaveAsClick());
            } else if (id == R.id.menu_delete) {
                presenter.onDeleteClick();
            }
            return false;
        });
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.menu_more, popup.getMenu());
        AndroidUtils.insertMenuItemIcons(v.getContext(), popup);
        popup.show();
    }

    public void setRecordName(final long recordId, final String name, final String extension) {
        AndroidUtils.showRenameDialog(this, name, false, newName -> {
            if (!name.equalsIgnoreCase(newName)) {
                presenter.renameRecord(recordId, newName, extension);
            }
        }, v -> {
        }, null);
    }

    public void setRecordName(final long recordId, File file, boolean showCheckbox) {
        final RecordInfo info = AudioDecoder.readRecordInfo(file);
        AndroidUtils.showRenameDialog(this, info.getName(), showCheckbox, newName -> {
            if (!info.getName().equalsIgnoreCase(newName)) {
                presenter.renameRecord(recordId, newName, info.getFormat());
            }
        }, v -> {
        }, (buttonView, isChecked) -> presenter.setAskToRename(!isChecked));
    }

    private boolean checkStoragePermissionDownload() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_CODE_READ_EXTERNAL_STORAGE_DOWNLOAD);
                return false;
            }
        }
        return true;
    }

    private boolean checkStoragePermissionImport() {
        if (presenter.isStorePublic()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_CODE_READ_EXTERNAL_STORAGE_IMPORT);
                    return false;
                }
            }
        }
        return true;
    }

    private boolean checkStoragePermissionPlayback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_CODE_READ_EXTERNAL_STORAGE_PLAYBACK);
                return false;
            }
        }
        return true;
    }

    private boolean checkRecordPermission2() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_CODE_RECORD_AUDIO);
                return false;
            }
        }
        return true;
    }

    private boolean checkStoragePermission2() {
        if (presenter.isStorePublic()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    AndroidUtils.showDialog(this, R.string.warning, R.string.need_write_permission, v -> requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_CODE_WRITE_EXTERNAL_STORAGE), null
//							new View.OnClickListener() {
//								@Override
//								public void onClick(View v) {
//									presenter.setStoragePrivate(getApplicationContext());
//									presenter.startRecording();
//								}
//							}
                    );
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQ_CODE_REC_AUDIO_AND_WRITE_EXTERNAL && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED && grantResults[2] == PackageManager.PERMISSION_GRANTED) {
            startRecordingService();
        } else if (requestCode == REQ_CODE_RECORD_AUDIO && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (checkStoragePermission2()) {
                startRecordingService();
            }
        } else if (requestCode == REQ_CODE_WRITE_EXTERNAL_STORAGE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            if (checkRecordPermission2()) {
                startRecordingService();
            }
        } else if (requestCode == REQ_CODE_READ_EXTERNAL_STORAGE_IMPORT && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            startFileSelector();
        } else if (requestCode == REQ_CODE_READ_EXTERNAL_STORAGE_DOWNLOAD && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            presenter.onSaveAsClick();
        } else if (requestCode == REQ_CODE_READ_EXTERNAL_STORAGE_PLAYBACK && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            presenter.startPlayback();
        } else if (requestCode == REQ_CODE_WRITE_EXTERNAL_STORAGE && grantResults.length > 0 && (grantResults[0] == PackageManager.PERMISSION_DENIED || grantResults[1] == PackageManager.PERMISSION_DENIED)) {
            presenter.setStoragePrivate(getApplicationContext());
            startRecordingService();
        }
    }
}
