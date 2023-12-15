package com.perry.audiorecorder.data.database;

import android.database.SQLException;
import android.text.TextUtils;

import com.perry.audiorecorder.ARApplication;
import com.perry.audiorecorder.AppConstants;
import com.perry.audiorecorder.data.FileRepository;
import com.perry.audiorecorder.data.Prefs;
import com.perry.audiorecorder.exception.FailedToRestoreRecord;
import com.perry.audiorecorder.util.FileUtil;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.raizlabs.android.dbflow.sql.language.property.IProperty;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import timber.log.Timber;

public class LocalRepositoryImpl implements LocalRepository {

    private final FileRepository fileRepository;

    private final Prefs prefs;

    private volatile static LocalRepositoryImpl instance;

    private OnRecordsLostListener onLostRecordsListener;

    private LocalRepositoryImpl(FileRepository fileRepository, Prefs prefs) {
        this.fileRepository = fileRepository;
        this.prefs = prefs;
    }

    public static LocalRepositoryImpl getInstance(FileRepository fileRepository, Prefs prefs) {
        if (instance == null) {
            synchronized (LocalRepositoryImpl.class) {
                if (instance == null) {
                    instance = new LocalRepositoryImpl(fileRepository, prefs);
                    instance.removeOutdatedTrashRecords();
                }
            }
        }
        return instance;
    }

    public Record getRecord(int id) {
        Record r = SQLite.select().from(Record.class).where(Record_Table.id.eq(id),Record_Table.isDelete.eq(false)).querySingle();
        if (r != null) {
            List<Record> l = new ArrayList<>(1);
            l.add(r);
            checkForLostRecords(l);
        }
        return r;
    }

    @Override
    public Record findRecordByPath(String path) {
        if (path.contains("'")) {
            path = path.replace("'", "''");
        }
        Record record = SQLite.select().from(Record.class).where(Record_Table.path.eq(path),Record_Table.isDelete.eq(false)).querySingle();
        return record;
    }

    @Override
    public List<Record> findRecordsByPath(String path) {
        if (path.contains("'")) {
            path = path.replace("'", "''");
        }
        List<Record> records = SQLite.select().from(Record.class).where(Record_Table.path.like(path),Record_Table.isDelete.eq(false)).queryList();
        return records;
    }

    @Override
    public boolean hasRecordsWithPath(String path) {
        if (path.contains("'")) {
            path = path.replace("'", "''");
        }
        List<Record> records = SQLite.select().from(Record.class).where(Record_Table.path.like(path),Record_Table.isDelete.eq(false)).limit(1).queryList();
        return records.size() > 0;
    }

    @Override
    public Record getTrashRecord(int id) {
        Record record = SQLite.select().from(Record.class).where(Record_Table.id.eq(id),Record_Table.isDelete.eq(true)).querySingle();
        return record;
    }

    @Override
    public Record insertEmptyFile(String path) throws IOException {
        if (!TextUtils.isEmpty(path)) {
            File file = new File(path);
            Record record = new Record(
                    FileUtil.removeFileExtension(file.getName()),
                    0, //mills
                    file.lastModified(),
                    new Date().getTime(),
                    Long.MAX_VALUE,
                    path,
                    prefs.getSettingRecordingFormat(),
                    0,
                    prefs.getSettingSampleRate(),
                    prefs.getSettingChannelCount(),
                    prefs.getSettingBitrate(),
                    false,
                    false,
                    new byte[ARApplication.getLongWaveformSampleCount()*4],
                    0,
                    "",
                    2,
                    "");
            record.save();
            return record;
        } else {
            Timber.e("Unable to read sound file by specified path!");
            throw new IOException("Unable to read sound file by specified path!");
        }
    }

    public List<Record> getAllRecords() {
        List<Record> list = SQLite.select().from(Record.class).where(Record_Table.isDelete.eq(false)).orderBy(Record_Table.added, false).queryList();
        checkForLostRecords(list);
        return list;
    }

    @Override
    public List<Integer> getAllItemsIds() {
        ArrayList<Integer> items = new ArrayList<>();
//        FlowManager.getDatabase(AppDataBase.class).executeTransaction(
//                new ProcessModelTransaction.Builder<Integer>((integer, wrapper) -> {
        //
        List<Record> records = SQLite.select().from(Record.class).where(Record_Table.isDelete.eq(false)).queryList();
        if (records != null && records.size() > 0) {
            for (int i = 0; i < records.size(); i++) {
                Record record = records.get(i);
                items.add(record.id);
            }
        }
//                }).build());
        return items;
//        return dataSource.getAllItemsIds();
    }

    @Override
    public List<Record> getRecords(int page) {
        List<Record> list = SQLite.select().from(Record.class)
                .where(Record_Table.isDelete.eq(false))
                .orderBy(Record_Table.added, false)
                .limit(AppConstants.DEFAULT_PER_PAGE)
                .offset((page - 1) * AppConstants.DEFAULT_PER_PAGE)
                .queryList();
        checkForLostRecords(list);
        return list;
    }

    @Override
    public List<Record> getRecords(int page, int order) {
        IProperty property;
        boolean ascending;
        switch (order) {
            case AppConstants.SORT_NAME:
                property = Record_Table.name;
                ascending = true;
                break;
            case AppConstants.SORT_NAME_DESC:
                property = Record_Table.name;
                ascending = false;
                break;
            case AppConstants.SORT_DURATION:
                property = Record_Table.duration;
                ascending = true;
                break;
            case AppConstants.SORT_DURATION_DESC:
                property = Record_Table.duration;
                ascending = false;
                break;
            case AppConstants.SORT_DATE_DESC:
                property = Record_Table.added;
                ascending = true;//这里的值其实颠倒了
                break;
            case AppConstants.SORT_DATE:
            default:
                property = Record_Table.added;
                ascending = false;
        }
        List<Record> list = SQLite.select().from(Record.class)
                .orderBy(property, ascending)
                .limit(AppConstants.DEFAULT_PER_PAGE)
                .offset((page - 1) * AppConstants.DEFAULT_PER_PAGE)
                .queryList();
        checkForLostRecords(list);
        return list;
    }

    @Override
    public Record getLastRecord() {
        Record r = SQLite.select().from(Record.class)
                .where(Record_Table.isDelete.eq(false))
                .orderBy(Record_Table.id, false)
                .limit(1).querySingle();
        if (r == null) {
            return null;
        }
        if (!isFileExists(r.getPath())) {
            List<Record> l = new ArrayList<>(1);
            l.add(r);
            checkForLostRecords(l);
        }
        return r;
    }

    private boolean isFileExists(String path) {
        return new File(path).exists();
    }

    public void deleteRecord(int id) {
        Record recordToDelete = SQLite.select().from(Record.class).where(Record_Table.id.eq(id),Record_Table.isDelete.eq(false)).querySingle();
        if (recordToDelete != null) {
            String renamed = fileRepository.markAsTrashRecord(recordToDelete.getPath());
            if (renamed != null) {
                recordToDelete.isDelete = true;
                recordToDelete.setPath(renamed);
                recordToDelete.save();
            } else {
                recordToDelete.delete();
                fileRepository.deleteRecordFile(recordToDelete.getPath());
            }
        }
    }

    @Override
    public void deleteRecordForever(int id) {
        SQLite.delete(Record.class).where(Record_Table.id.eq(id)).execute();
    }

    @Override
    public List<Long> getRecordsDurations() {
        List<Record> records = SQLite.select().from(Record.class).where(Record_Table.isDelete.eq(false)).queryList();
        ArrayList<Long> items = new ArrayList<>();
        if(records != null && records.size() > 0){
            for (int i=0;i<records.size();i++){
                Record record = records.get(i);
                items.add(record.duration);
            }
        }
        return items;
    }

    @Override
    public boolean addToBookmarks(int id) {
        Record r = SQLite.select().from(Record.class).where(Record_Table.id.eq(id)).querySingle();
        if (r != null) {
            r.setBookmark(true);
            return r.save();
        } else {
            return false;
        }
    }

    @Override
    public boolean removeFromBookmarks(int id) {
        Record r = SQLite.select().from(Record.class).where(Record_Table.id.eq(id)).querySingle();
        if (r != null) {
            r.setBookmark(false);
            return r.save();
        } else {
            return false;
        }
    }

    @Override
    public List<Record> getBookmarks() {
        List<Record> list = SQLite.select().from(Record.class)
                .where(Record_Table.bookmark.eq(true),Record_Table.isDelete.eq(false))
                .orderBy(Record_Table.created,false).queryList();
        return list;
    }

    @Override
    public List<Record> getTrashRecords() {
        List<Record> list = SQLite.select().from(Record.class)
                .where(Record_Table.isDelete.eq(true))
                .orderBy(Record_Table.added,false).queryList();
        return list;
    }

    @Override
    public List<Integer> getTrashRecordsIds() {
        ArrayList<Integer> items = new ArrayList<>();
        List<Record> records = SQLite.select().from(Record.class)
                .where(Record_Table.isDelete.eq(true)).queryList();
        if (records != null && records.size() > 0) {
            for (int i = 0; i < records.size(); i++) {
                Record record = records.get(i);
                items.add(record.id);
            }
        }
        return items;
    }

    @Override
    public int getTrashRecordsCount() {
        long count = SQLite.select().from(Record.class).where(Record_Table.isDelete.eq(true)).count();
        return (int)count;
    }

    @Override
    public void restoreFromTrash(int id) throws FailedToRestoreRecord {
        Record recordToRestore = SQLite.select().from(Record.class).where(Record_Table.isDelete.eq(true),Record_Table.id.eq(id)).querySingle();
        if (recordToRestore != null) {
            recordToRestore.isDelete= false;
//            recordToRestore.save();
            restoreRecord(recordToRestore);
        } else {
            throw new FailedToRestoreRecord();
        }
    }

    private void restoreRecord(Record record) {
        String renamed = fileRepository.unmarkTrashRecord(record.getPath());
        if (renamed != null) {
            record.setPath(renamed);
            record.save();
        } else {
            renamed = fileRepository.unmarkTrashRecord(record.getPath());
            if (renamed != null) {
                record.setPath(renamed);
                record.save();
            } else {
                record.save();
//				throw new FailedToRestoreRecord();
            }
        }
    }

    @Override
    public boolean removeFromTrash(int id) {
        SQLite.delete(Record.class).where(Record_Table.isDelete.eq(true),Record_Table.id.eq(id)).execute();
//        return trashDataSource.deleteItem(id) > 0;
        return true;
    }

    @Override
    public boolean emptyTrash() {
        try {
            SQLite.delete(Record.class).where(Record_Table.isDelete.eq(true)).execute();
            return true;
        } catch (SQLException e) {
            Timber.e(e);
            return false;
        }
    }

    @Override
    public void removeOutdatedTrashRecords() {
        long curTime = new Date().getTime();
//        List<Record> list = trashDataSource.getAll();
        List<Record> list = SQLite.select().from(Record.class).where(Record_Table.isDelete.eq(true)).queryList();
        for (int i = 0; i < list.size(); i++) {
            Record record = list.get(i);
            if (record.getRemoved() + AppConstants.RECORD_IN_TRASH_MAX_DURATION < curTime) {
                fileRepository.deleteRecordFile(record.getPath());
                record.delete();
            }
        }
    }

    @Override
    public boolean deleteAllRecords() {
        SQLite.delete(Record.class).execute();
        return true;
    }

    private void checkForLostRecords(List<Record> list) {
        List<Record> lost = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Record temp = list.get(i);
            if (!isFileExists(temp.getPath())) {
                lost.add(temp);
            }
        }
        if (onLostRecordsListener != null && !lost.isEmpty()) {
            onLostRecordsListener.onLostRecords(lost);
        }
    }

    @Override
    public void setOnRecordsLostListener(OnRecordsLostListener onLostRecordsListener) {
        this.onLostRecordsListener = onLostRecordsListener;
    }
}
