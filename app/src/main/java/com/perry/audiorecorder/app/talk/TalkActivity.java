package com.perry.audiorecorder.app.talk;

import android.Manifest;
import android.annotation.SuppressLint;
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
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.google.android.material.textfield.TextInputEditText;
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
import com.perry.audiorecorder.audio.AudioDecoder;
import com.perry.audiorecorder.data.FileRepository;
import com.perry.audiorecorder.data.database.Record;
import com.perry.audiorecorder.exception.CantCreateFileException;
import com.perry.audiorecorder.exception.ErrorParser;
import com.perry.audiorecorder.util.AndroidUtils;
import com.perry.audiorecorder.util.AnimationUtil;
import com.perry.audiorecorder.util.FileUtil;
import com.perry.audiorecorder.util.KeyboardsUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
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
    private ImageButton btnShare;

    private TalkContract.UserActionsListener presenter;
    private ColorMap colorMap;
    private FileRepository fileRepository;
    private ColorMap.OnThemeColorChangeListener onThemeColorChangeListener;

    RecordAudioButton mBtnVoice;//底部录制按钮
    private RecordVoicePopWindow mRecordVoicePopWindow;//提示

    int delayMillis = 0;//是否需要延迟关闭对话框

    private TalkAdapter talkAdapter; //适配器
    RecyclerView recyclerView;//消息列表


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
    private TextInputEditText editText;

    AppCompatImageButton buttonSwitchText, buttonSwitchVoice;
    AppCompatButton buttonSend;
    RelativeLayout relativeText;
    LinearLayout linearVoice;
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

    @SuppressLint({"WrongViewCast", "MissingInflatedId"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        colorMap = ARApplication.getInjector().provideColorMap();
        SimpleWaveformView.setWaveformColorRes(colorMap.getPrimaryColorRes());
        setTheme(colorMap.getAppThemeResource());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_talk);
        mRoot = findViewById(R.id.root);
        ImageButton btnSettings = findViewById(R.id.btn_settings);
        btnShare = findViewById(R.id.btn_share);

        linearVoice = findViewById(R.id.linear_voice);
        relativeText = findViewById(R.id.relative_text);
        buttonSwitchVoice = findViewById(R.id.button_switch_voice);
        buttonSwitchText = findViewById(R.id.button_switch_text);
        buttonSend = findViewById(R.id.button_send);
        buttonSwitchVoice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                relativeText.setVisibility(View.GONE);
                linearVoice.setVisibility(View.VISIBLE);
            }
        });
        buttonSwitchText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                relativeText.setVisibility(View.VISIBLE);
                linearVoice.setVisibility(View.GONE);
            }
        });
        editText = findViewById(R.id.edit_text);
        buttonSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String msgStr = editText.getEditableText().toString();
                Log.d(TAG, "发送消息事件：" + msgStr);
                presenter.sendText(msgStr);
            }
        });
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
        mBtnVoice = findViewById(R.id.btnVoice);
        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(TalkActivity.this));
        presenter = ARApplication.getInjector().provideTalkPresenter();
        talkAdapter = new TalkAdapter(ARApplication.getInjector().provideSettingsMapper(), TalkActivity.this, colorMap, presenter);
        talkAdapter.setItemClickListener((view, id, path, position) -> presenter.setActiveRecord(id, new RecordsContract.Callback() {
            @Override
            public void onSuccess() {
                presenter.stopPlayback(position);
                if (startPlayback(position)) {
                    talkAdapter.setActiveItem(position);
                }
            }

            @Override
            public void onError(Exception e) {
                Timber.e(e);
            }
        }));
//        此功能已注释掉了
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
//        下面这行代码很重要就是说话的时候seek进度条不闪动了
        ((SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);


        mBtnVoice.setOnVoiceButtonCallBack(new RecordAudioButton.OnVoiceButtonCallBack() {
            @Override
            public void onStartRecord() {
                if (checkRecordPermission2()) {
                    if (checkStoragePermission2()) {
                        startRecordingService();
                        Log.d(TAG, "开始录音");
                    }
                } else {
                    Toast.makeText(TalkActivity.this, "如果多次拒绝后就需要手动开启录音权限了", Toast.LENGTH_SHORT).show();
                }
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
        btnSettings.setOnClickListener(this);
        btnShare.setOnClickListener(this);
        space = getResources().getDimension(R.dimen.spacing_xnormal);

        fileRepository = ARApplication.getInjector().provideFileRepository();

        onThemeColorChangeListener = colorKey -> {
            setTheme(colorMap.getAppThemeResource());
            recreate();
        };
        colorMap.addOnThemeColorChangeListener(onThemeColorChangeListener);

        //Check start recording shortcut
//        if ("android.intent.action.ACTION_RUN".equals(getIntent().getAction())) {
        if (checkRecordPermission2()) {
            if (checkStoragePermission2()) {
                //Start or stop recording
//                    startRecordingService();
            }
        }
//        }
    }

    @Override
    public void sendTextShow(String msgStr){
        talkAdapter.addTextData(msgStr);
        editText.setText("");
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
    public void addRecords(List<ItemData> records, int order) {
        //TODO 这里获取的录音数据的数组不一样；是个bug；需要寻找问题
        talkAdapter.addData(records, order);
        txtEmpty.setVisibility(View.GONE);
        recyclerView.scrollToPosition(talkAdapter.getItemCount() - 1);
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
//        txtTitle.setText(R.string.records);
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
        presenter.stopPlayback(0);
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

    private boolean startPlayback(int position) {
        String path = presenter.getActiveRecordPath();
        if (FileUtil.isFileInExternalStorage(getApplicationContext(), path)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                AndroidUtils.showRecordFileNotAvailable(this, path);
                return false;
            } else if (checkStoragePermissionPlayback()) {
                presenter.startPlayback(position);
                return true;
            }
        } else {
            presenter.startPlayback(position);
            return true;
        }
        return false;
    }

    @Override
    public void showRecords(List<ItemData> records, int order) {
        if (records.size() == 0) {
            txtEmpty.setVisibility(View.VISIBLE);
            talkAdapter.setData(new ArrayList<>(), order);
        } else {
            talkAdapter.setData(records, order);
            txtEmpty.setVisibility(View.GONE);
            recyclerView.scrollToPosition(records.size() - 1);
        }
    }

    private void showToolbar() {
        AnimationUtil.viewAnimationY(toolbar, 0f, null);
    }

    public void hidePanel() {
    }

    @Override
    public void showPanelProgress() {
//        panelProgress.setVisibility(View.VISIBLE);
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
    }

    private void downloadSelectedRecords() {
        downloadRecords.clear();
        List<Integer> selected = talkAdapter.getSelected();
        for (int i = 0; i < selected.size(); i++) {
            ItemData item = talkAdapter.getItem(selected.get(i));
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
//        panelProgress.setVisibility(View.GONE);
    }

    @Override
    public void addedToBookmarks(int id, boolean isActive) {
        if (isActive) {
//            btnCheckBookmark.setImageResource(R.drawable.ic_bookmark);
        }
        talkAdapter.markAddedToBookmarks(id);
    }

    @Override
    public void removedFromBookmarks(int id, boolean isActive) {
//        if (isActive) {
//            btnCheckBookmark.setImageResource(R.drawable.ic_bookmark_bordered);
//        }
        talkAdapter.markRemovedFromBookmarks(id);
    }

    @Override
    public void showRecordName(String name) {
//        txtName.setText(name);
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
//        TODO 此行代码注释掉就不会有第一次的提示了
//        presenter.checkFirstRun();
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
        if (id == R.id.btn_records_list) {
            startActivity(RecordsActivity.getStartIntent(getApplicationContext()));
        } else if (id == R.id.btn_settings) {
            startActivity(SettingsActivity.getStartIntent(getApplicationContext()));
        } else if (id == R.id.btn_share) {
            Toast.makeText(this, "敬请期待...", Toast.LENGTH_SHORT).show();
            startActivity(RecordsActivity.getStartIntent(TalkActivity.this));
//            showMenu(view);
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
            ItemData item = talkAdapter.getItem(selected.get(i));
            share.add(item.getPath());
        }
        AndroidUtils.shareAudioFiles(getApplicationContext(), share);
        cancelMultiSelect();
    }

    private void deleteSelectedRecords() {
        List<Long> ids = new ArrayList<>();
        List<Integer> selected = talkAdapter.getSelected();
        for (int i = 0; i < selected.size(); i++) {
            ItemData item = talkAdapter.getItem(selected.get(i));
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
        btnShare.setEnabled(false);
        btnShare.setVisibility(View.INVISIBLE);
    }

    @Override
    public void showRecordingStop() {
        btnShare.setEnabled(true);
        btnShare.setVisibility(View.VISIBLE);
    }

    @Override
    public void showRecordingPause() {
        btnShare.setEnabled(false);
        btnShare.setVisibility(View.INVISIBLE);
    }

    @Override
    public void showRecordingResume() {
        btnShare.setEnabled(false);
        btnShare.setVisibility(View.INVISIBLE);
    }

    @Override
    public void askRecordingNewName(long id, File file, boolean showCheckbox) {
        setRecordName(id, file, showCheckbox);
    }

    @Override
    public void onRecordingProgress(long mills, int amp) {
        runOnUiThread(() -> {
//            txtProgress.setText(TimeUtils.formatTimeIntervalHourMinSec2(mills));
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
    public void showPlayStart(boolean animate, int index) {
        talkAdapter.showPlayStart(animate, index);
    }

    @Override
    public void showPlayPause(int index) {
        talkAdapter.showPlayPause(index);
    }

    @Override
    public void showPlayStop(int index) {
        talkAdapter.showPlayStop(index);
//        txtProgress.setText(TimeUtils.formatTimeIntervalHourMinSec2(0));
    }

    @Override
    public void showWaveForm(int[] waveForm, long duration, long playbackMills) {
    }

    @Override
    public void waveFormToStart() {
    }

    @Override
    public void showDuration(final String duration) {
        Log.d(TAG, "showDuration:" + duration);
    }

    @Override
    public void showRecordingProgress(String progress) {
    }

    @Override
    public void showName(String name) {
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
        talkAdapter.setProgress(mills, percent);
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

    /**
     * 点击非编辑区域收起键盘
     * 获取点击事件
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            View view = getCurrentFocus();
            if (KeyboardsUtils.isShouldHideKeyBord(view, ev) && KeyboardsUtils.isShouldHideKeyBord(buttonSend, ev)) {
                KeyboardsUtils.hintKeyBoards(view);
            }
        }
        return super.dispatchTouchEvent(ev);
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
                Log.d(TAG, "没有录音权限；拒绝嘞");
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
        Log.d(TAG, "onRequestPermissionsResult permissions:" + Arrays.toString(permissions) + ",grantResults:" + Arrays.toString(grantResults));
        if (requestCode == REQ_CODE_REC_AUDIO_AND_WRITE_EXTERNAL && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED && grantResults[2] == PackageManager.PERMISSION_GRANTED) {
//            startRecordingService();
        } else if (requestCode == REQ_CODE_RECORD_AUDIO && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (checkStoragePermission2()) {
//                startRecordingService();
            }
        } else if (requestCode == REQ_CODE_WRITE_EXTERNAL_STORAGE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            if (checkRecordPermission2()) {
//                startRecordingService();
            }
        } else if (requestCode == REQ_CODE_READ_EXTERNAL_STORAGE_IMPORT && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            startFileSelector();
        } else if (requestCode == REQ_CODE_READ_EXTERNAL_STORAGE_DOWNLOAD && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            presenter.onSaveAsClick();
        } else if (requestCode == REQ_CODE_READ_EXTERNAL_STORAGE_PLAYBACK && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
//            presenter.startPlayback();
        } else if (requestCode == REQ_CODE_WRITE_EXTERNAL_STORAGE && grantResults.length > 0 && (grantResults[0] == PackageManager.PERMISSION_DENIED || grantResults[1] == PackageManager.PERMISSION_DENIED)) {
//            presenter.setStoragePrivate(getApplicationContext());
//            startRecordingService();
        }
    }
}
