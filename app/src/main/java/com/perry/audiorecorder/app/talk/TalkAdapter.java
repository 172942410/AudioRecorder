package com.perry.audiorecorder.app.talk;

import static androidx.core.app.ActivityCompat.requestPermissions;
import static androidx.core.content.PermissionChecker.checkSelfPermission;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;
import androidx.recyclerview.widget.RecyclerView;

import com.perry.audiorecorder.AppConstants;
import com.perry.audiorecorder.ColorMap;
import com.perry.audiorecorder.R;
import com.perry.audiorecorder.app.settings.SettingsMapper;
import com.perry.audiorecorder.app.talk.itemHolder.VHSendText;
import com.perry.audiorecorder.app.widget.CircleImageView;
import com.perry.audiorecorder.app.widget.SimpleWaveformView;
import com.perry.audiorecorder.util.AndroidUtils;
import com.perry.audiorecorder.util.TimeUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class TalkAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final static String TAG = TalkAdapter.class.getName();
    private List<ItemData> data;
    private List<Integer> selected;
    private boolean isMultiSelectMode = false;

    private final SettingsMapper settingsMapper;
    private boolean showDateHeaders = false;
    private int activeItem = -1;
    private View btnTrash;
    private boolean showTrash = false;

    private ItemClickListener itemClickListener;
    private BtnTrashClickListener btnTrashClickListener;
    private OnAddToBookmarkListener onAddToBookmarkListener = null;
    private OnItemOptionListener onItemOptionListener = null;
    private OnMultiSelectModeListener onMultiSelectModeListener = null;

    ColorMap colorMap;
    TalkContract.UserActionsListener presenter;
    Activity activity;

    TalkAdapter(SettingsMapper mapper, Activity activity, ColorMap colorMap, TalkContract.UserActionsListener presenter) {
        this(mapper);
        this.colorMap = colorMap;
        this.presenter = presenter;
        this.activity = activity;
    }

    TalkAdapter(SettingsMapper mapper) {
        this.data = new ArrayList<>();
        this.selected = new ArrayList<>();
        this.settingsMapper = mapper;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int type) {
        if (type == ItemType.HEADER.typeId) {
            return new UniversalViewHolder(createHeaderView(viewGroup.getContext()));
        } else if (type == ItemType.FOOTER.typeId) {
            View view = new View(viewGroup.getContext());
            int height = (int) viewGroup.getContext().getResources().getDimension(R.dimen.panel_height);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, height);
            view.setLayoutParams(lp);
            return new UniversalViewHolder(view);
        } else if (type == ItemType.DATE.typeId) {
            //Create date list item layout programmatically.
            TextView textView = new TextView(viewGroup.getContext());
            ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            textView.setLayoutParams(lp);
            textView.setTypeface(textView.getTypeface(), Typeface.BOLD);

            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, viewGroup.getContext().getResources().getDimension(R.dimen.text_medium));

            int pad = (int) viewGroup.getContext().getResources().getDimension(R.dimen.spacing_small);
            textView.setPadding(pad, pad, pad, pad);
            textView.setGravity(Gravity.CENTER);

            return new UniversalViewHolder(textView);
        } else if (type == ItemType.SEND_VOICE.typeId) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_talk_send_voice, viewGroup, false);
            return new ItemViewHolder(v, (position, itemViewHolder) -> {
                if (isMultiSelectMode) {
                    if (!selected.contains(position) && data.get(position).getDuration() != 0) {
                        selected.add(position);
                    } else {
                        int pos = selected.indexOf(position);
                        if (pos != -1) {
                            selected.remove(pos);
                            if (selected.size() == 0) {
                                isMultiSelectMode = !isMultiSelectMode;
                                notifyDataSetChanged();
                                if (onMultiSelectModeListener != null) {
                                    onMultiSelectModeListener.onMultiSelectMode(false);
                                }
                            }
                        }
                    }
                    if (onMultiSelectModeListener != null) {
                        onMultiSelectModeListener.onSelectDeselect(selected.size());
                    }
                    notifyItemChanged(position);
                } else {
                    if (itemClickListener != null && data.size() > position) {
                        if (posTemp != -1 && position != posTemp) {
                            itemViewHolderPrev = itemViewHolderCur;//这样可能不行
                        }
                        itemViewHolderCur = itemViewHolder;
                        if (position == posTemp) {
                            Log.d(TAG, "上一个item view和这个一样的");
                        } else {
                            Log.d(TAG, "上一个item view和现在点击的是不同的俩个");
                        }
                        posPrev = posTemp;
                        itemClickListener.onItemClick(v, data.get(position).getId(), data.get(position).getPath(), position);
                        posTemp = position;
                    }
                }
            }, position -> {
//                isMultiSelectMode = !isMultiSelectMode;
//                notifyDataSetChanged();
//                if (onMultiSelectModeListener != null) {
//                    onMultiSelectModeListener.onMultiSelectMode(isMultiSelectMode);
//                }
//                if (isMultiSelectMode) {
//                    if (!selected.contains(position) && data.get(position).getDuration() != 0) {
//                        selected.add(position);
//                        notifyItemChanged(position);
//                    }
//                } else {
//                    selected.clear();
//                    if (onMultiSelectModeListener != null) {
//                        onMultiSelectModeListener.onMultiSelectMode(false);
//                    }
//                    notifyDataSetChanged();
//                }
//                if (onMultiSelectModeListener != null) {
//                    onMultiSelectModeListener.onSelectDeselect(selected.size());
//                }
            });
        } else if (type == ItemType.SEND_TEXT.typeId) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_talk_send_text, viewGroup, false);
            return new VHSendText(v, this,colorMap,null, null);
        } else if (type == ItemType.RECEIVE_TEXT.typeId) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_talk_receive_text, viewGroup, false);
            return new VHSendText(v, this,colorMap,null, null);
        }
        else {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_talk_send_text, viewGroup, false);
            return new VHSendText(v, this,colorMap,null, null);
        }
    }
    VHSendText.OnItemClickListener onReceiveClickListener;
    public void setReceiveClickListener(VHSendText.OnItemClickListener onReceiveClickListener){
        this.onReceiveClickListener = onReceiveClickListener;
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder viewHolder, final int pos) {
        if (viewHolder.getItemViewType() == ItemType.RECEIVE_TEXT.typeId) {
            final VHSendText holder = (VHSendText) viewHolder;
            final int p = holder.getAbsoluteAdapterPosition();
            final ItemData item = data.get(p);
//            Log.d(TAG, "item:" + item);
            holder.setItemData(item);
            if(onReceiveClickListener != null) {
                holder.setOnItemClickListener(onReceiveClickListener);
            }
        }else
        if (viewHolder.getItemViewType() == ItemType.SEND_TEXT.typeId) {
            final VHSendText holder = (VHSendText) viewHolder;
            final int p = holder.getAbsoluteAdapterPosition();
            final ItemData item = data.get(p);
//            Log.d(TAG, "item:" + item);
            holder.setItemData(item);
        } else if (viewHolder.getItemViewType() == ItemType.SEND_VOICE.typeId) {
            final ItemViewHolder holder = (ItemViewHolder) viewHolder;
            final int p = holder.getAbsoluteAdapterPosition();
            final ItemData item = data.get(p);
//            Log.d(TAG, "item:" + item);
            holder.name.setText(item.getName());
//            duration=4639875 4秒
            holder.setDurationInt((int) (item.getDuration() / 1000000));
//            Log.d(TAG, "获取到的 durationInt：" + holder.durationInt);
            String durationStr = holder.durationInt + "\"";
            holder.duration.setText(durationStr);
            holder.created.setText(item.getAddedTimeStr());
            Drawable drawable;
            if (item.isBookmarked()) {
                drawable = ContextCompat.getDrawable(viewHolder.itemView.getContext(), R.drawable.ic_bookmark_small);
            } else {
                drawable = ContextCompat.getDrawable(viewHolder.itemView.getContext(), R.drawable.ic_bookmark_bordered_small);
            }
            if(item.getLoadStatus() == 0){ //成功
                holder.buttonFailed.setVisibility(View.GONE);
                holder.progressBar.setVisibility(View.GONE);
            }else if(item.getLoadStatus() == 1){ //失败
                holder.buttonFailed.setVisibility(View.VISIBLE);
                holder.progressBar.setVisibility(View.GONE);
            }else if(item.getLoadStatus() == 2){ //加载中...
                holder.buttonFailed.setVisibility(View.GONE);
                holder.progressBar.setVisibility(View.VISIBLE);
            }else{ //默认成功状态
                holder.buttonFailed.setVisibility(View.GONE);
                holder.progressBar.setVisibility(View.GONE);
            }

            holder.btnBookmark.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
            if (viewHolder.getLayoutPosition() == activeItem) {
                holder.view.setBackgroundResource(R.color.selected_item_color);
            } else {
                if (selected.contains(p)) {
                    holder.view.setBackgroundResource(R.color.selected_item_color);
                } else {
                    holder.view.setBackgroundResource(android.R.color.transparent);
                }
            }

//            holder.btnBookmark.setOnClickListener(v -> {
//                if (onAddToBookmarkListener != null && data.size() > p) {
//                    if (item.isBookmarked()) {
//                        onAddToBookmarkListener.onRemoveFromBookmarks((int) item.getId());
//                    } else {
//                        onAddToBookmarkListener.onAddToBookmarks((int) item.getId());
//                    }
//                }
//            });
            holder.btnMore.setOnClickListener(v -> showMenu(v, p));
            holder.waveformViewItem.setWaveform(item.getAmps());
            if (isMultiSelectMode || item.getDuration() == 0) {
                holder.btnMore.setVisibility(View.GONE);
            } else {
                holder.btnMore.setVisibility(View.VISIBLE);
            }
            updateInformation(holder.info, item.getFormat(), item.getSampleRate(), item.getSize());
        } else if (viewHolder.getItemViewType() == ItemType.DATE.typeId) {
            UniversalViewHolder holder = (UniversalViewHolder) viewHolder;
            ((TextView) holder.view).setText(
                    TimeUtils.formatDateSmartLocale(
                            data.get(viewHolder.getAbsoluteAdapterPosition()).getAdded(),
                            holder.view.getContext()
                    )
            );
        }
    }

    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        if (holder.getItemViewType() == ItemType.HEADER.typeId) {
            btnTrash = holder.itemView.findViewById(R.id.btn_trash);
            if (btnTrash != null) {
                if (btnTrashClickListener != null) {
                    btnTrash.setOnClickListener(v -> btnTrashClickListener.onClick());
                }
                if (showTrash) {
                    btnTrash.setVisibility(View.VISIBLE);
                } else {
                    btnTrash.setVisibility(View.GONE);
                }
            }
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        if (holder.getItemViewType() == ItemType.HEADER.typeId) {
            btnTrash = null;
        }
    }

    public void showTrash(boolean show) {
        showTrash = show;
        if (btnTrash != null) {
            if (showTrash) {
                btnTrash.setVisibility(View.VISIBLE);
            } else {
                btnTrash.setVisibility(View.GONE);
            }
        }
    }

    public void showMenu(View v, final int pos) {
        PopupMenu popup = new PopupMenu(v.getContext(), v);
        popup.setOnMenuItemClickListener(item -> {
            if (onItemOptionListener != null && data.size() > pos) {
                onItemOptionListener.onItemOptionSelected(item.getItemId(), data.get(pos));
            }
            return false;
        });
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.menu_more, popup.getMenu());
        AndroidUtils.insertMenuItemIcons(v.getContext(), popup);
        popup.show();
    }

    void setActiveItem(int activeItem) {
        int prevItem = this.activeItem;
        this.activeItem = activeItem;
        notifyItemChanged(prevItem);
        notifyItemChanged(activeItem);
    }

//	public void setActiveItemById(long id) {
//		int pos = findPositionById(id);
//		if (pos >= 0) {
//			setActiveItem(pos);
//		}
//	}

    int findPositionById(long id) {
        if (id >= 0) {
            for (int i = 0; i < data.size(); i++) {
                if (data.get(i).getId() == id) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @Override
    public int getItemViewType(int position) {
        ItemData itemData = data.get(position);
//        Log.d(TAG, "itemData size:" + data.size() + ",itemData;" + itemData);
        return itemData.getType();
    }

    void setData(List<ItemData> d, int order) {
//        updateShowHeader(order);
        if (showDateHeaders) {
            data = addDateHeaders(d);
        } else {
            data = d;
        }
//        data.add(0, ItemType.createHeaderItem());
        notifyDataSetChanged();
    }

//	public void addData(List<ItemType> d) {
//		this.data.addAll(addDateHeaders(d));
//		notifyItemRangeInserted(data.size() - d.size(), d.size());
//	}

    void addData(List<ItemData> d, int order) {
        if (data.size() > 0) {
//            updateShowHeader(order);
            if (showDateHeaders) {
                if (findFooter() >= 0) {
                    data.addAll(data.size() - 1, addDateHeaders(d));
                } else {
                    data.addAll(addDateHeaders(d));
                }
            } else {
                if (findFooter() >= 0) {
                    data.addAll(data.size() - 1, d);
                } else {
                    data.addAll(d);
                }
            }
            notifyItemRangeInserted(data.size() - d.size(), d.size());
        } else {
            //首次走这里
            data.addAll(d);
        }
    }

    private void updateShowHeader(int order) {
        if (order == AppConstants.SORT_DATE || order == AppConstants.SORT_DATE_DESC) {
            showDateHeaders = true;
        } else {
            showDateHeaders = false;
        }
    }

    public ItemData getItem(int pos) {
        return data.get(pos);
    }

    private List<ItemData> addDateHeaders(List<ItemData> data) {
        if (data.size() > 0) {
            if (!hasDateHeader(data, data.get(0).getAdded())) {
                data.add(0, ItemData.createDateItem(data.get(0).getAdded()));
            }
            Calendar d1 = Calendar.getInstance();
            d1.setTimeInMillis(data.get(0).getAdded());
            Calendar d2 = Calendar.getInstance();
            for (int i = 1; i < data.size(); i++) {
                d1.setTimeInMillis(data.get(i - 1).getAdded());
                d2.setTimeInMillis(data.get(i).getAdded());
                if (!TimeUtils.isSameDay(d1, d2) && !hasDateHeader(data, data.get(i).getAdded())) {
                    data.add(i, ItemData.createDateItem(data.get(i).getAdded()));
                }
            }
        }
        return data;
    }

    public void deleteItem(long id) {
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).getId() == id) {
                data.remove(i);
//				if (getAudioRecordsCount() == 0) {
//					data.clear();
//					notifyDataSetChanged();
//				} else {
                notifyItemRemoved(i);
                //this line below gives you the animation and also updates the
                //list items after the deleted item
                notifyItemRangeChanged(i, getItemCount());
//				}
                break;
            }
        }
    }

    int getAudioRecordsCount() {
        int count = 0;
        for (int i = 0; i < data.size(); i++) {
            int type = data.get(i).getType();
            if (type != ItemType.HEADER.typeId && type != ItemType.FOOTER.typeId && type != ItemType.DATE.typeId) {
                count++;
            }
        }
        return count;
    }

    public void showFooter() {
        if (findFooter() == -1) {
            this.data.add(ItemData.createFooterItem());
            notifyItemInserted(data.size() - 1);
        }
    }

    public void hideFooter() {
        int pos = findFooter();
        if (pos != -1) {
            this.data.remove(pos);
            notifyItemRemoved(pos);
        }
    }

    long getNextTo(long id) {
        if (id >= 0) {
            for (int i = 0; i < data.size() - 1; i++) {
                if (data.get(i).getId() == id) {
                    if (data.get(i + 1).getId() == -1 && i + 2 < data.size()) {
                        return data.get(i + 2).getId();
                    } else {
                        return data.get(i + 1).getId();
                    }
                }
            }
        }
        return -1;
    }

    long getPrevTo(long id) {
        if (id >= 0) {
            for (int i = 1; i < data.size(); i++) {
                if (data.get(i).getId() == id) {
                    if (data.get(i - 1).getId() == -1 && i - 2 >= 0) {
                        return data.get(i - 2).getId();
                    } else {
                        return data.get(i - 1).getId();
                    }
                }
            }
        }
        return -1;
    }

    private int findFooter() {
        for (int i = data.size() - 1; i >= 0; i--) {
            if (data.get(i).getType() == ItemType.FOOTER.typeId) {
                return i;
            }
        }
        return -1;
    }

    void markAddedToBookmarks(int id) {
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).getId() == id) {
                data.get(i).setBookmarked(true);
                notifyItemChanged(i);
            }
        }
    }

    void markRemovedFromBookmarks(int id) {
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).getId() == id) {
                data.get(i).setBookmarked(false);
                notifyItemChanged(i);
            }
        }
    }

    private void updateInformation(TextView view, String format, int sampleRate, long size) {
        if (format.equals(AppConstants.FORMAT_3GP)) {
            view.setText(settingsMapper.formatSize(size) + AppConstants.SEPARATOR
                    + settingsMapper.convertFormatsToString(format) + AppConstants.SEPARATOR
                    + settingsMapper.convertSampleRateToString(sampleRate)
            );
        } else {
            switch (format) {
                case AppConstants.FORMAT_M4A:
                case AppConstants.FORMAT_WAV:
                    view.setText(settingsMapper.formatSize(size) + AppConstants.SEPARATOR
                            + settingsMapper.convertFormatsToString(format) + AppConstants.SEPARATOR
                            + settingsMapper.convertSampleRateToString(sampleRate)// + AppConstants.SEPARATOR
                    );
                    break;
                default:
                    view.setText(settingsMapper.formatSize(size) + AppConstants.SEPARATOR
                            + format + AppConstants.SEPARATOR
                            + settingsMapper.convertSampleRateToString(sampleRate) + AppConstants.SEPARATOR
                    );
            }
        }
    }

    private boolean hasDateHeader(List<ItemData> data, long time) {
        for (int i = data.size() - 1; i >= 0; i--) {
            if (data.get(i).getType() == ItemType.DATE.typeId) {
                Calendar d1 = Calendar.getInstance();
                d1.setTimeInMillis(data.get(i).getAdded());
                Calendar d2 = Calendar.getInstance();
                d2.setTimeInMillis(time);
                if (TimeUtils.isSameDay(d1, d2)) {
                    return true;
                }
            }
        }
        return false;
    }

    private View createHeaderView(Context context) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        View headerView = new View(context);
        int height = (int) context.getResources().getDimension(R.dimen.toolbar_height);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, height);
        headerView.setLayoutParams(headerParams);

        LinearLayout btnLayout = new LinearLayout(context);
        btnLayout.setOrientation(LinearLayout.VERTICAL);
        btnLayout.setId(R.id.btn_trash);
        ViewGroup.LayoutParams frameParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLayout.setLayoutParams(frameParams);
        btnLayout.setClickable(true);
        btnLayout.setFocusable(true);
        btnLayout.setBackgroundResource(R.drawable.button_translcent);

        int pad = (int) context.getResources().getDimension(R.dimen.spacing_normal);
        int medium = (int) context.getResources().getDimension(R.dimen.spacing_medium);
        TextView buttonTrash = new TextView(context);
        ViewGroup.LayoutParams btnParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonTrash.setLayoutParams(btnParams);
        buttonTrash.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        buttonTrash.setText(R.string.trash);
        buttonTrash.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_delete, 0, 0, 0);
        buttonTrash.setCompoundDrawablePadding(pad * 2);
        buttonTrash.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimension(R.dimen.text_large));
        buttonTrash.setTextColor(ContextCompat.getColor(context, R.color.white));
        buttonTrash.setPadding(pad, medium, pad, medium);
        buttonTrash.setGravity(Gravity.CENTER);

        int dividerColor = ContextCompat.getColor(context, R.color.divider);
        int dividerSize = (int) context.getResources().getDimension(R.dimen.divider);

        View dividerView = new View(context);
        ViewGroup.LayoutParams dividerParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dividerSize);
        dividerView.setLayoutParams(dividerParams);
        dividerView.setBackgroundColor(dividerColor);

        View dividerView2 = new View(context);
        ViewGroup.LayoutParams dividerParams2 = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dividerSize);
        dividerView2.setLayoutParams(dividerParams2);
        dividerView2.setBackgroundColor(dividerColor);

        btnLayout.addView(dividerView);
        btnLayout.addView(buttonTrash);
        btnLayout.addView(dividerView2);

        container.addView(headerView);
        container.addView(btnLayout);
        return container;
    }

    public List<Integer> getSelected() {
        return new ArrayList<>(selected);
    }

    public void cancelMultiSelect() {
        selected.clear();
        isMultiSelectMode = false;
        notifyDataSetChanged();
        if (onMultiSelectModeListener != null) {
            onMultiSelectModeListener.onMultiSelectMode(false);
        }
    }

    public void setBtnTrashClickListener(BtnTrashClickListener btnTrashClickListener) {
        this.btnTrashClickListener = btnTrashClickListener;
    }

    void setItemClickListener(ItemClickListener itemClickListener) {
        this.itemClickListener = itemClickListener;
    }

    /**
     * 此功能已注释掉了
     *
     * @param onAddToBookmarkListener
     */
    void setOnAddToBookmarkListener(OnAddToBookmarkListener onAddToBookmarkListener) {
        this.onAddToBookmarkListener = onAddToBookmarkListener;
    }

    public void setColorMap(ColorMap colorMap) {
        this.colorMap = colorMap;
    }

    public void setPresenter(TalkContract.UserActionsListener presenter) {
        this.presenter = presenter;
    }

    public void setProgress(long mills, int percent) {
//        Log.d(TAG, "setProgress mills:" + mills + ",percent:" + percent);
        if (itemViewHolderCur != null) {
//            Log.d(TAG, "setProgress:" + percent);
            itemViewHolderCur.playProgress.setProgress(percent);
            String curTime = mills / 1000 + "";
            itemViewHolderCur.durationCur.setText(curTime + "/");
            itemViewHolderCur.durationCur.setVisibility(View.VISIBLE);
            notifyItemChanged(activeItem);
        }
//        if (playProgressCur != null) {
////            Log.d(TAG,"setProgress:"+percent);
//            playProgressCur.setProgress(percent);
//            notifyItemChanged(posCur);
//        }
    }

    public void showPlayStart(boolean animate, int index) {
        if (itemViewHolderCur != null) {
            Log.d(TAG, "index:" + index + ",activeItem:" + activeItem + ",posPrev:" + posPrev);
            if (index == posPrev) {
                itemViewHolderCur.btnPlay.setImageResource(R.drawable.ic_pause);
            } else {
                if (itemViewHolderPrev != null) {
                    itemViewHolderPrev.btnPlay.setImageResource(R.drawable.ic_play);
                    itemViewHolderPrev.playProgress.setProgress(0);
                    itemViewHolderPrev.durationCur.setText("0/");
                    itemViewHolderPrev.durationCur.setVisibility(View.INVISIBLE);
                }
                itemViewHolderCur.btnPlay.setImageResource(R.drawable.ic_pause);
                itemViewHolderCur.playProgress.setProgress(0);
            }
        }
    }

    public void showPlayPause(int index) {
        if (itemViewHolderCur != null) {
            if (index == activeItem) {
                itemViewHolderCur.btnPlay.setImageResource(R.drawable.ic_play);
            } else {
                itemViewHolderCur.btnPlay.setImageResource(R.drawable.ic_stop);
                itemViewHolderCur.playProgress.setProgress(0);
                itemViewHolderCur.durationCur.setText("0/");
                itemViewHolderCur.durationCur.setVisibility(View.INVISIBLE);
            }
        }
    }

    public void showPlayStop(int index) {
        if (itemViewHolderCur != null) {
            if (index == activeItem) {
                itemViewHolderCur.btnPlay.setImageResource(R.drawable.ic_play);
                itemViewHolderCur.playProgress.setProgress(0);
                itemViewHolderCur.durationCur.setText("0/");
                itemViewHolderCur.durationCur.setVisibility(View.INVISIBLE);
            } else {

            }
        }
        setActiveItem(-1);
    }

    public void addTextData(ItemData itemData) {
        Log.d(TAG, "addTextData 发送消息事件：" );
        data.add(itemData);
        Log.d(TAG,"addTextData data.size():"+data.size());
        notifyDataSetChanged();
    }

    public interface ItemClickListener {
        void onItemClick(View view, long id, String path, int position);
    }

    public interface BtnTrashClickListener {
        void onClick();
    }

    void setOnItemOptionListener(OnItemOptionListener onItemOptionListener) {
        this.onItemOptionListener = onItemOptionListener;
    }

    void setOnMultiSelectModeListener(OnMultiSelectModeListener listener) {
        this.onMultiSelectModeListener = listener;
    }

    interface OnAddToBookmarkListener {
        void onAddToBookmarks(int id);

        void onRemoveFromBookmarks(int id);
    }

    interface OnItemOptionListener {
        void onItemOptionSelected(int menuId, ItemData item);
    }

    int posPrev = -1;
    int posTemp = -1;
    ItemViewHolder itemViewHolderPrev;
    ItemViewHolder itemViewHolderCur;
    SeekBar playProgressCur;

    class ItemViewHolder extends RecyclerView.ViewHolder {
        public int durationInt;
        TextView name;
        TextView duration;
        TextView durationCur;
        TextView created;
        TextView info;
        AppCompatTextView btnBookmark;
        CircleImageView btnMore;
        SimpleWaveformView waveformViewItem;
        View view;

        LinearLayout voiceLayout;
        FrameLayout messageLayout;

        ImageButton btnPlay;
        SeekBar playProgress;
        ProgressBar progressBar;
        AppCompatImageButton buttonFailed;
        ItemViewHolder(
                View itemView,
                OnItemClickListener onItemClickListener,
                OnItemLongClickListener longClickListener
        ) {
            super(itemView);
            view = itemView;
            view.setOnClickListener(v -> {
                playProgressCur = playProgress;
                int pos = getAbsoluteAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && onItemClickListener != null) {
                    onItemClickListener.onItemClick(pos, this);
                }
            });
            view.setOnLongClickListener(v -> {
                int pos = getAbsoluteAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && longClickListener != null) {
                    longClickListener.onItemLongClick(pos);
                }
                return false;
            });
            name = itemView.findViewById(R.id.list_item_name);
            duration = itemView.findViewById(R.id.item_duration);
            durationCur = itemView.findViewById(R.id.item_duration_cur);
            created = itemView.findViewById(R.id.list_item_date);
            info = itemView.findViewById(R.id.list_item_info);
            btnBookmark = itemView.findViewById(R.id.list_item_bookmark);
//            waveformView = itemView.findViewById(R.id.list_item_waveform);
            btnMore = itemView.findViewById(R.id.item_iv_avatar);
            waveformViewItem = itemView.findViewById(R.id.item_waveform);
            playProgress = itemView.findViewById(R.id.item_play_progress);
//            10秒之内不用显示进度条了；太小了
            voiceLayout = itemView.findViewById(R.id.voice_layout);
            messageLayout = itemView.findViewById(R.id.message_layout);
            buttonFailed = itemView.findViewById(R.id.button_failed);
            progressBar = itemView.findViewById(R.id.progress);
            if (colorMap != null) {
                messageLayout.setBackgroundResource(colorMap.getPlaybackPanelBackground());
            }
            btnPlay = itemView.findViewById(R.id.btn_play);
            btnPlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int pos = getAbsoluteAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION && onItemClickListener != null) {
                        onItemClickListener.onItemClick(pos, ItemViewHolder.this);
                    }
//                    String path = presenter.getActiveRecordPath();
//                    //This method Starts or Pause playback.
//                    if (FileUtil.isFileInExternalStorage(activity, path)) {
//                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                            AndroidUtils.showRecordFileNotAvailable(activity, path);
//                        } else if (checkStoragePermissionPlayback()) {
//                            presenter.startPlayback();
//                        }
//                    } else {
//                        presenter.startPlayback();
//                    }
                    playProgressCur = playProgress;
                }
            });

            playProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
//                        int val = (int) AndroidUtils.dpToPx(progress * waveformView.getWaveformLength() / 1000);
//                        waveformView.seekPx(val);
//                        //TODO: Find a better way to convert px to mills here
//                        presenter.seekPlayback(waveformView.pxToMill(val));
                        presenter.seekPlayback(progress);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    presenter.disablePlaybackProgressListener();
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    presenter.enablePlaybackProgressListener();
                }
            });

        }

        public void setDurationInt(int durationInt) {
            this.durationInt = durationInt;
//            Log.d(TAG, "durationInt:" + durationInt);
            if (playProgress != null) {
                if (durationInt < 10) {
                    playProgress.setVisibility(View.INVISIBLE);
                } else {
//                    先隐藏了不显示；之后再做这里
                    playProgress.setVisibility(View.INVISIBLE);
                }
            }
            if (durationInt >= 0 && voiceLayout != null) {
                //voiceLayout跟进时长计算长度；目测最小 ：40dp 或 60dp ：最长 200dp 语音最少1秒最多60秒
                ViewGroup.LayoutParams params =  messageLayout.getLayoutParams();
                int width = 50 + durationInt * 4;
                if (width > 200) {
                    width = 200;
                }
                int progressSize = activity.getResources().getDimensionPixelSize(R.dimen.item_progress_size);
//                params.width = (int) AndroidUtils.dpToPx(40);
                params.width = (int) (AndroidUtils.dpToPx(width));
//            params.width = (int) AndroidUtils.dpToPx(60);
//            Log.d(TAG,"params.width:" + params.width);
            }
        }
    }

    private boolean checkStoragePermissionPlayback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PermissionChecker.PERMISSION_GRANTED && checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) != PermissionChecker.PERMISSION_GRANTED) {
                requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, TalkActivity.REQ_CODE_READ_EXTERNAL_STORAGE_PLAYBACK);
                return false;
            }
        }
        return true;
    }

    static class UniversalViewHolder extends RecyclerView.ViewHolder {
        View view;

        UniversalViewHolder(View view) {
            super(view);
            this.view = view;
        }
    }

    public interface OnItemClickListener {
        void onItemClick(int position, ItemViewHolder itemViewHolder);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(int position);
    }

    public interface OnMultiSelectModeListener {
        void onMultiSelectMode(boolean selected);

        void onSelectDeselect(int selectedCount);
    }
}
