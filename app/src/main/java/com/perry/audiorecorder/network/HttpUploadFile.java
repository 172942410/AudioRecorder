package com.perry.audiorecorder.network;

import android.app.Activity;
import android.os.Environment;
import android.util.Log;

import org.xutils.common.Callback;
import org.xutils.common.util.KeyValue;
import org.xutils.http.RequestParams;
import org.xutils.http.body.MultipartBody;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.xutils.x;

public class HttpUploadFile {

    private static final String TAG = "HttpUploadFile";
    public static final String SD_PATH = Environment.getExternalStorageDirectory().getAbsolutePath();
    public static final String ROOT_PATH = "/app_lxd/";
    public static final String PATH = ROOT_PATH + "error_log/";
    public static final String FILE_PATH = SD_PATH + PATH;

//    public static String URL_HOST = "http://demo.lianyundata.com";
    public static String URL_HOST = "http://digitalghost.asuscomm.cn:6688";

    Activity activity;
    String saveDate, lastDate;

    public <T>void uploadAudio(String fileName, String filePath,Callback.CommonCallback<T> callback) {
        android.util.Log.d(TAG,"fileName:"+fileName+",filePath:"+filePath);
//        if (fileName.endsWith(".wav")) {
//            fileName = fileName.substring(0, fileName.length() - 4);
//        }
        File file = new File(filePath);
        if(!file.exists()){
            Log.e(TAG,"音频文件不存在啊");
            return;
        }
        Log.e(TAG,"开始接口上传音频文件了");
        String url = URL_HOST + "/uploadaudio";
        RequestParams params = new RequestParams(url);
//        params.setAsJsonContent(true);
//        params.addHeader("Content-Type", "application/json");
        params.addHeader("Filename", fileName+".wav");
        List<KeyValue> list = new ArrayList<>();
        list.add(new KeyValue("file", file));
//        list.add(new KeyValue("Filename", fileName+".wav"));
        MultipartBody body = new MultipartBody(list, "UTF-8");
        params.setRequestBody(body);
        params.setConnectTimeout(120000);
        x.http().post(params, callback);
    }

    public String uploadAudioSync(String fileName, File file) {
//        android.util.Log.d(TAG,"padId:"+padId+",title:"+title+",file:"+file);
        if (fileName.endsWith(".wav")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }
        String url = URL_HOST + "/uploadaudio";
        RequestParams params = new RequestParams(url);
//        params.setAsJsonContent(true);
        List<KeyValue> list = new ArrayList<>();
        list.add(new KeyValue("file", file));
        list.add(new KeyValue("Filename", fileName));
        MultipartBody body = new MultipartBody(list, "UTF-8");
        params.setRequestBody(body);
        params.setConnectTimeout(120000);
        String result = "error";
        try {
            result = x.http().postSync(params, String.class);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return result;
    }

    /**
     *
     */
//    private Observable uploadLog(File file, Context context) {
//        Observable observable = Observable.create(new ObservableOnSubscribe<Object>() {
//            @Override
//            public void subscribe(@NonNull ObservableEmitter<Object> emitter) throws Throwable {
//                String fileName = file.getName();
//                String dateStr = null;
//                if (fileName.startsWith("log_")) {
//                    dateStr = fileName.substring(4);
//                }
//                if (dateStr.endsWith(".txt")) {
//                    dateStr = dateStr.substring(0, dateStr.length() - 4);
//                }
////                Log.d(TAG,"saveDate:"+saveDate+",dateStr:"+dateStr);
//                if (TextUtils.isEmpty(saveDate) || compareDate(dateStr, saveDate)) {
//                    if (httpRequest == null) {
//                        httpRequest = new HttpRequest(null);
//                    }
//                    String deviceId = DeviceId.getDeviceId(context);
////                    String content = FileUtils.readLogFile(file.getName());
//                    String result = httpRequest.uploadLogSync(deviceId, file.getName(), file);
//                    Log.d(TAG, result);
//
//                    if (TextUtils.isEmpty(lastDate) || compareDate(dateStr, lastDate)) {
//                        lastDate = dateStr;
//                    }
//                }
//                emitter.onComplete();
//            }
//        });
//        return observable;
//    }

//    private boolean compareDate(String fileDate, String saveDate) {
//        return TimeUtils.compareDate(fileDate, saveDate, "yyyy年MM月dd日");
//    }
}
