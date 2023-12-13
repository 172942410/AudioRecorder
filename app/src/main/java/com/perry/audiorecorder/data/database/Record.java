package com.perry.audiorecorder.data.database;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.perry.audiorecorder.AppConstants;
import com.raizlabs.android.dbflow.annotation.Column;
import com.raizlabs.android.dbflow.annotation.PrimaryKey;
import com.raizlabs.android.dbflow.annotation.Table;
import com.raizlabs.android.dbflow.structure.BaseModel;

import java.io.Serializable;
import java.util.Arrays;

@Table(database = AppDataBase.class)
public class Record extends BaseModel implements Serializable {

    public static final int NO_ID = -1;
    @PrimaryKey
    @Column
    private int id;
    @Column
    private String name;
    /**
     * 音频持续时间
     */
    @Column
    private long duration;
    /**
     * 创建时间戳
     */
    @Column
    private long created;
    /**
     * 添加的时间戳
     */
    @Column
    private long added;
    @Column
    private long removed;
    @Column
    private String path;
    @Column
    private String format;
    @Column
    private long size;
    @Column
    private int sampleRate;
    @Column
    private int channelCount;
    @Column
    private int bitrate;
    @Column
    private boolean bookmark;
    @Column
    private boolean waveformProcessed;
    @Column
    private int[] amps;
    @Column
    private byte[] data;
    @Column
    private int msgType;
    @Column
//    private byte[] msgData;
    private String msgStr;

    private String msgSpeak;
    /**
     * 0 成功；1失败；2加载中
     */
    @Column
    private int loadStatus;

    /**
     * dbFlow 要求必须有的无参构建函数
     */
    public Record(){

    }

    public Record(int id, String name, long duration, long created, long added, long removed, String path,
                  String format, long size, int sampleRate, int channelCount, int bitrate,
                  boolean bookmark, boolean waveformProcessed, int[] amps, int msgType, String msgStr, int loadStatus,String msgSpeak) {
        this.id = id;
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
        this.amps = amps;
        this.data = int2byte(amps);
//		this.data = AndroidUtils.int2byte(amps);
        this.msgType = msgType;
        this.msgStr = msgStr;
        this.loadStatus = loadStatus;
        this.msgSpeak = msgSpeak;
    }

    public Record(int id, String name, long duration, long created, long added, long removed, String path,
                  String format, long size, int sampleRate, int channelCount, int bitrate,
                  boolean bookmark, boolean waveformProcessed, byte[] amps, int msgType, String msgStr, int loadStatus) {
        this.id = id;
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
        this.amps = byte2int(amps);
//		this.amps = AndroidUtils.byte2int(amps);
        this.data = amps;
        this.msgType = msgType;
        this.msgStr = msgStr;
        this.loadStatus = loadStatus;
    }

    public Record(int id, String name, long duration, long created, long added, long removed, String path,
                  String format, long size, int sampleRate, int channelCount, int bitrate,
                  boolean bookmark, boolean waveformProcessed, byte[] amps, int msgType, String msgStr, int loadStatus,String msgSpeak) {
        this.id = id;
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
        this.amps = byte2int(amps);
//		this.amps = AndroidUtils.byte2int(amps);
        this.data = amps;
        this.msgType = msgType;
        this.msgStr = msgStr;
        this.loadStatus = loadStatus;
        this.msgSpeak = msgSpeak;
    }

    public static Record createTextRecord(long createdTime, String msgStr) {
        byte[] arr = new byte[1];
        if (!TextUtils.isEmpty(msgStr)) {
            return new Record(Record.NO_ID, "", 0, createdTime, createdTime, 0, "", "", 0, 0, 0, 0, false, false, arr, 2, msgStr, 2);
        }
        return new Record(Record.NO_ID, "", 0, createdTime, createdTime, 0, "", "", 0, 0, 0, 0, false, false, arr, 2, null, 2);
    }

    /**
     * 接收到的文本消息
     *
     * @param createdTime
     * @param msgStr
     * @return
     */
    public static Record createReceiveTextRecord(long createdTime, String msgStr) {
        byte[] arr = new byte[1];
        if (!TextUtils.isEmpty(msgStr)) {
            return new Record(Record.NO_ID, "", 0, createdTime, createdTime, 0, "", "", 0, 0, 0, 0, false, false, arr, 13, msgStr, 0);
        }
        return new Record(Record.NO_ID, "", 0, createdTime, createdTime, 0, "", "", 0, 0, 0, 0, false, false, arr, 13, null, 0);
    }

    public static Record createReceiveTextRecord(long createdTime, String msgStr,String msgSpeak) {
        byte[] arr = new byte[1];
        if (!TextUtils.isEmpty(msgStr) && !TextUtils.isEmpty(msgSpeak)) {
            return new Record(Record.NO_ID, "", 0, createdTime, createdTime, 0, "", "", 0, 0, 0, 0, false, false, arr, 13, msgStr, 0,msgSpeak);
        }
        return new Record(Record.NO_ID, "", 0, createdTime, createdTime, 0, "", "", 0, 0, 0, 0, false, false, arr, 13, null, 0);
    }

    public byte[] int2byte(int[] amps) {
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

    public int[] getAmps() {
        return amps;
    }

    public void setAmps(int[] amps) {
        this.amps = amps;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public byte[] getData() {
        return data;
    }

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
                ", data=" + Arrays.toString(data) +
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
