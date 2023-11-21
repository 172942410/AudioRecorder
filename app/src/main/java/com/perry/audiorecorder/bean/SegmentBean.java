package com.perry.audiorecorder.bean;

import java.util.List;

public class SegmentBean {
    public int id;
    public int seek;
    public float start;
    public float end;
    public String text;
    public List<Integer> tokens;
    public float temperature;
    public double avg_logprob;
    public float compression_ratio;
    public double no_speech_prob;

    @Override
    public String toString() {
        return "SegmentBean{" +
                "id=" + id +
                ", seek=" + seek +
                ", start=" + start +
                ", end=" + end +
                ", text='" + text + '\'' +
                ", tokens=" + tokens +
                ", temperature=" + temperature +
                ", avg_logprob=" + avg_logprob +
                ", compression_ratio=" + compression_ratio +
                ", no_speech_prob=" + no_speech_prob +
                '}';
    }
}
