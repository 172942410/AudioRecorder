package com.perry.audiorecorder.data.database;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.perry.audiorecorder.AppConstants;
import com.perry.audiorecorder.util.AndroidUtils;
import com.raizlabs.android.dbflow.annotation.Column;
import com.raizlabs.android.dbflow.annotation.PrimaryKey;
import com.raizlabs.android.dbflow.annotation.Table;
import com.raizlabs.android.dbflow.structure.BaseModel;

import java.io.Serializable;
import java.util.Arrays;

@Table(database = AppDataBase.class)
public class Record extends BaseModel implements Serializable {
    final String TAG = Record.class.getName();
//    public static final int NO_ID = -1;
    @PrimaryKey(autoincrement = true)
    public int id;
    @Column
    public String name;
    /**
     * 音频持续时间
     */
    @Column
    public long duration;
    /**
     * 创建时间戳
     */
    @Column
    public long created;
    /**
     * 添加的时间戳
     */
    @Column
    public long added;
    @Column
    public long removed;
    @Column
    public String path;
    @Column
    public String format;
    @Column
    public long size;
    @Column
    public int sampleRate;
    @Column
    public int channelCount;
    @Column
    public int bitrate;
    @Column
    public boolean bookmark;
    @Column
    public boolean waveformProcessed;

    public int[] ampsIntArray;
    @Column
    public byte[] amps;
    @Column
    public int msgType;
    @Column
    public String msgStr;

    @Column
    public String msgSpeak;
    /**
     * 0 成功；1失败；2加载中
     */
    @Column
    public int loadStatus;
    @Column
    public boolean isDelete;

    /**
     * dbFlow 要求必须有的无参构建函数
     */
    public Record(){

    }

    public Record(String name, long duration, long created, long added, long removed, String path,
                  String format, long size, int sampleRate, int channelCount, int bitrate,
                  boolean bookmark, boolean waveformProcessed, byte[] amps, int msgType, String msgStr, int loadStatus,String msgSpeak) {
        this.name = name;
        this.duration = duration;
        this.created = created;
        this.added = added;
        this.removed = removed;
        this.path = path;
        this.format = format;
        this.size = size;
        this.sampleRate = sampleRate;
        this.channelCount = channelCount;
        this.bitrate = bitrate;
        this.bookmark = bookmark;
        this.waveformProcessed = waveformProcessed;
		this.ampsIntArray = AndroidUtils.byte2int(amps);
        this.amps = amps;
        this.msgType = msgType;
        this.msgStr = msgStr;
        this.loadStatus = loadStatus;
        this.msgSpeak = msgSpeak;
    }

    public static Record createTextRecord(long createdTime, String msgStr) {
        byte[] arr = new byte[1];
        if (!TextUtils.isEmpty(msgStr)) {
            return new Record("", 0, createdTime, createdTime, 0, "", "", 0, 0, 0, 0, false, false, arr, 2, msgStr, 2,"");
        }
        return new Record( "", 0, createdTime, createdTime, 0, "", "", 0, 0, 0, 0, false, false, arr, 2, null, 2,"");
    }

    /**
     * 接收到的文本消息
     *
     * @param createdTime
     * @param msgStr
     * @return
     */
    public static Record createReceiveTextRecord(long createdTime, String msgStr,String msgSpeak) {
        byte[] arr = new byte[1];
        if (!TextUtils.isEmpty(msgStr) && !TextUtils.isEmpty(msgSpeak)) {
            return new Record("", 0, createdTime, createdTime, 0, "", "", 0, 0, 0, 0, false, false, arr, 13, msgStr, 0,msgSpeak);
        }
        return new Record("", 0, createdTime, createdTime, 0, "", "", 0, 0, 0, 0, false, false, arr, 13, null, 0,"");
    }

    public byte[] int2byte(int[] amps) {
        if(amps == null){
            Log.e(TAG,"int2byte amps 空的");
            return null;
        }
        byte[] bytes = new byte[amps.length];
        for (int i = 0; i < amps.length; i++) {
            if (amps[i] >= 255) {
                bytes[i] = 127;
            } else if (amps[i] < 0) {
                bytes[i] = 0;
            } else {
                bytes[i] = (byte) (amps[i] - 128);
            }
        }
        return bytes;
    }

    public int[] byte2int(byte[] amps) {
        if(amps == null){
            Log.e(TAG,"byte2int amps 入参空的");
            return null;
        }
        int[] ints = new int[amps.length];
        for (int i = 0; i < amps.length; i++) {
            ints[i] = amps[i] + 128;
        }
        return ints;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNameWithExtension() {
        return name + AppConstants.EXTENSION_SEPARATOR + format;
    }

    public long getCreated() {
        return created;
    }

    public long getAdded() {
        return added;
    }

    public long getRemoved() {
        return removed;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getFormat() {
        return format;
    }

    public long getSize() {
        return size;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getChannelCount() {
        return channelCount;
    }

    public int getBitrate() {
        return bitrate;
    }

    public byte[] getAmps() {
        return amps;
    }

    public void setAmps(byte[] amps) {
        this.amps = amps;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

//    public byte[] getData() {
//        return data;
//    }

    public boolean isBookmarked() {
        return bookmark;
    }

    public boolean isWaveformProcessed() {
        return waveformProcessed;
    }

    public void setBookmark(boolean b) {
        this.bookmark = b;
    }

    @NonNull
    @Override
    public String toString() {
        return "Record{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", duration=" + duration +
                ", created=" + created +
                ", added=" + added +
                ", removed=" + removed +
                ", path='" + path + '\'' +
                ", format='" + format + '\'' +
                ", size=" + size +
                ", sampleRate=" + sampleRate +
                ", channelCount=" + channelCount +
                ", bitrate=" + bitrate +
                ", bookmark=" + bookmark +
                ", waveformProcessed=" + waveformProcessed +
                ", amps=" + Arrays.toString(amps) +
//                ", data=" + Arrays.toString(data) +
                ", msgData=" + msgStr +
                '}';
    }

    public int getMsgType() {
        return msgType;
    }

    public String  getMsgData() {
        return msgStr;
    }

    /**
     * @return 0 成功；1 失败；2 加载中
     */
    public int getLoadStatus() {
        if (loadStatus == 2) {
            loadStatus = 1;
        }
        return loadStatus;
    }

    /**
     * @param loadStatus 0 成功；1 失败；2 加载中
     */
    public void setLoading(int loadStatus) {
        this.loadStatus = loadStatus;
    }

    public String getMsgSpeak() {
        return msgSpeak;
    }
}
