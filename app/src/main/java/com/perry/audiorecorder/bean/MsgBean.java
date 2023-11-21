package com.perry.audiorecorder.bean;

import android.text.TextUtils;

import com.perry.audiorecorder.AppConstants;


public class MsgBean {

    public static final int NO_ID = -1;
    private final int id;
    private final String name;
    private long duration;
    private final long created;
    private final long added;
    private final long removed;
    private String path;
    private final String format;
    private final long size;
    private final int sampleRate;
    private final int channelCount;
    private final int bitrate;
    private boolean bookmark;
    private final boolean waveformProcessed;
    private int[] amps;
    private final byte[] data;
    private final int msgType;
    private byte[] msgData;

    public MsgBean(int id, String name, long duration, long created, long added, long removed, String path,
                   String format, long size, int sampleRate, int channelCount, int bitrate,
                   boolean bookmark, boolean waveformProcessed, int[] amps, int msgType, byte[] msgData) {
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
        this.msgData = msgData;
    }

    public MsgBean(int id, String name, long duration, long created, long added, long removed, String path,
                   String format, long size, int sampleRate, int channelCount, int bitrate,
                   boolean bookmark, boolean waveformProcessed, byte[] amps, int msgType, byte[] msgData) {
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
        this.msgData = msgData;
    }

    public static MsgBean createTextRecord(long createdTime , String msgStr){
        byte[] arr = new byte[1];
        if(!TextUtils.isEmpty(msgStr)){
            return new MsgBean(MsgBean.NO_ID,"",0,createdTime,createdTime,0,"","",0,0,0,0,false,false,arr,2,msgStr.getBytes());
        }
        return new MsgBean(MsgBean.NO_ID,"",0,createdTime,createdTime,0,"","",0,0,0,0,false,false,arr,2,null);
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
    public void setAmps(int[] amps){
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


    public int getMsgType() {
        return msgType;
    }

    public byte[] getMsgData(){
        return msgData;
    }
}
