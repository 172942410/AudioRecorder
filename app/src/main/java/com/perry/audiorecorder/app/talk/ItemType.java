package com.perry.audiorecorder.app.talk;

public enum ItemType {
    /**
     * SEND_VOICE      发送 语音
     * RECEIVE_VOICE   接收 语音
     */
    HEADER(-2),        // item头部布局
    DATE(-3),          // item日期布局
    FOOTER(-4),        // item尾部布局

    //      以下是 普通item数据类型
    SEND_VOICE(1),     //发送 语音
    SEND_TEXT(2),      //发送 文本
    SEND_VIDEO(3),     //发送 视频
    SEND_PIC(4),       //发送 图片
    SEND_FILE(5),      //发送 文件
    SEND_HTML(6),      //发送 网页
    SEND_LOCATION(7),  //发送 位置
    SEND_URL(8),       //发送 链接
    SEND_PHONE(9),     //发送 电话号码
    SEND_APPLE(10),     //发送 小程序
    SEND_OTHER(11),     //发送 其他

    //    以下是接收到的消息类型；其实同发送的类型一一对应
    RECEIVE_VOICE(12),     //接收 语音
    RECEIVE_TEXT(13),      //接收 文本
    RECEIVE_VIDEO(14),     //接收 视频
    RECEIVE_PIC(15),       //接收 图片
    RECEIVE_FILE(16),      //接收 文件
    RECEIVE_HTML(17),      //接收 网页
    RECEIVE_LOCATION(18),  //接收 位置
    RECEIVE_URL(19),       //接收 链接
    RECEIVE_PHONE(20),     //接收 电话号码
    RECEIVE_APPLE(21),     //接收 小程序
    RECEIVE_OTHER(22),     //接收 其他
    //这里可以添加补充和新增的枚举类型...

    ;

    int typeId;

    ItemType(int typeId) {
        this.typeId = typeId;
    }
}
