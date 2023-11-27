package com.perry.audiorecorder.app.talk.itemHolder;

import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.recyclerview.widget.RecyclerView;

import com.perry.audiorecorder.ColorMap;
import com.perry.audiorecorder.R;
import com.perry.audiorecorder.app.talk.ItemData;
import com.perry.audiorecorder.app.talk.TalkAdapter;
import com.perry.audiorecorder.app.widget.CircleImageView;

public class VHSendText extends RecyclerView.ViewHolder {
    private final static String TAG = VHSendText.class.getName();
    TextView name;
    TextView duration;
    TextView durationCur;
    TextView created;
    TextView info;
    AppCompatTextView btnBookmark;
    CircleImageView btnMore;
    View view;

    FrameLayout voiceLayout;
    TalkAdapter talkAdapter;
    ColorMap colorMap;
    AppCompatTextView itemTime;
    AppCompatImageButton ibTtsPlay;
    public VHSendText(
            View itemView,
            TalkAdapter talkAdapter,
            ColorMap colorMap,
            TalkAdapter.OnItemClickListener onItemClickListener,
            TalkAdapter.OnItemLongClickListener longClickListener
    ) {
        super(itemView);
        this.talkAdapter = talkAdapter;
        this.colorMap = colorMap;
        view = itemView;
//        view.setOnClickListener(v -> {
//            int pos = getAbsoluteAdapterPosition();
//            if (pos != RecyclerView.NO_POSITION && onItemClickListener != null) {
//                onItemClickListener.onItemClick(pos, this);
//            }
//        });
//        view.setOnLongClickListener(v -> {
//            int pos = getAbsoluteAdapterPosition();
//            if (pos != RecyclerView.NO_POSITION && longClickListener != null) {
//                longClickListener.onItemLongClick(pos);
//            }
//            return false;
//        });
        itemTime = itemView.findViewById(R.id.item_time);
        name = itemView.findViewById(R.id.list_item_name);
        duration = itemView.findViewById(R.id.item_duration);
        durationCur = itemView.findViewById(R.id.item_duration_cur);
        created = itemView.findViewById(R.id.list_item_date);
        info = itemView.findViewById(R.id.list_item_info);
        btnBookmark = itemView.findViewById(R.id.list_item_bookmark);
//            waveformView = itemView.findViewById(R.id.list_item_waveform);
        btnMore = itemView.findViewById(R.id.item_iv_avatar);
//            10秒之内不用显示进度条了；太小了
        voiceLayout = itemView.findViewById(R.id.voice_layout);
        if (colorMap != null) {
            voiceLayout.setBackgroundResource(colorMap.getPlaybackPanelBackground());
        }
        ibTtsPlay = itemView.findViewById(R.id.btn_tts_play);
    }

    public void setItemData(ItemData item) {
        if(item == null){
            return;
        }
        if(itemTime != null){
            itemTime.setText(item.getAddedTimeStr());
        }
        if(name != null) {
            if(item.getItemData() != null){
                String msgStr = new String(item.getItemData());
                name.setText(msgStr);
            }
        }
        final int p = getAbsoluteAdapterPosition();
        if(btnMore != null) {
            btnMore.setOnClickListener(v -> talkAdapter.showMenu(v, p));
        }
        if(ibTtsPlay != null){
            ibTtsPlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Log.d(TAG,"点击了播放按钮：" + item.toString());
                    int pos = getAbsoluteAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION && onItemClickListener != null) {
                        onItemClickListener.onItemClick(pos, VHSendText.this, item);
                    }
                }
            });
        }
    }
    OnItemClickListener onItemClickListener;
    public void setOnItemClickListener(OnItemClickListener onItemClickListener){
        this.onItemClickListener = onItemClickListener;
    }
    public interface OnItemClickListener {
        void onItemClick(int position, VHSendText itemViewHolder,ItemData item);
    }
}
