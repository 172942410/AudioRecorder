package com.perry.audiorecorder.bean;

import java.util.List;

public class ReceiveMsgBean {

    public static final int NO_ID = -1;
    public int id;
    public String text;
    public String language;
    public List<SegmentBean> segments;

    @Override
    public String toString() {
        return "ReceiveMsgBean{" +
                "id=" + id +
                ", text='" + text + '\'' +
                ", language='" + language + '\'' +
                ", segments=" + segments +
                '}';
    }
}
