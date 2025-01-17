
package com.perry.audiorecorder.app.talk;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.perry.audiorecorder.AppConstants;
import com.perry.audiorecorder.util.TimeUtils;

import java.util.Arrays;

public class ItemData implements Parcelable {

	private final long id;
	private final int type;
	private final String name;
	private final String format;
	private final String path;
	private final String description;
	private final String durationStr;
	private final long duration;
	private final long size;
	private final int sampleRate;
	private final int channelCount;
	private final int bitrate;
	private final long created;
	public long added;
	public String addedTime;
	private final String createTime;
	/**
	 * 0：停止 1：播放； 2：暂停 ； -1：异常
	 */
	public int playStatus;//0：停止 1：播放； 2：暂停 ； -1：异常
	public int playProgress;//当前的播放进度实施变化的进度条的单独的
	public int durationCur;//当前的播放进度实施变化的
	private boolean bookmarked;
	private final String avatar_url;
	private int[] amps;
	private String  itemData;

	/**
	 * 0 成功；1失败；2加载中
	 */
	private int loadStatus = 0; // 0 成功；1失败；2加载中

	public String msgSpeak;

	public ItemData(long id, int type, String name, String format, String description, long duration,
					long size, long created, long added, String path, int sampleRate, int channelCount, int bitrate,
					boolean bookmarked, int[] amps ,String itemData,int loadStatus,String msgSpeak) {
		this.id = id;
		this.type = type;
		this.name = name;
		this.format = format;
		this.description = description;
		this.created = created;
		this.createTime = convertTimeToStr(created);
		this.added = added;
		this.addedTime = convertTimeToStr(added);
		this.duration = duration;
		this.size = size;
		this.durationStr = convertDurationToStr(duration/1000);
		this.path = path;
		this.sampleRate = sampleRate;
		this.channelCount = channelCount;
		this.bitrate = bitrate;
		this.bookmarked = bookmarked;
		this.avatar_url = "";
		this.amps = amps;
		this.itemData = itemData;
		this.loadStatus = loadStatus;
		this.msgSpeak = msgSpeak;
	}

	public static ItemData createHeaderItem() {
		return new ItemData(-1, ItemType.HEADER.typeId, "HEADER", "", "", 0, 0, 0, 0, "", 0, 0, 0, false, null, null,0,"");
	}

	public static ItemData createFooterItem() {
		return new ItemData(-1, ItemType.FOOTER.typeId, "FOOTER", "", "", 0, 0, 0, 0, "", 0, 0, 0, false, null,null,0,"");
	}

	public static ItemData createDateItem(long date) {
		return new ItemData(-1, ItemType.DATE.typeId, "DATE", "", "", 0, 0, 0, date, "", 0, 0, 0, false, null, null,0,"");
	}

	public static ItemData createTextItem(String msgStr) {
		if(!TextUtils.isEmpty(msgStr)) {
			return new ItemData(-1, ItemType.SEND_TEXT.typeId, "TEXT", "", "", 0, 0, System.currentTimeMillis(), 0, "", 0, 0, 0, false, null,  msgStr,0,"");
		}else{
			return null;
		}
	}

	public long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getNameWithExtension() {
		return name + AppConstants.EXTENSION_SEPARATOR + format;
	}

	public String getFormat() {
		return format;
	}

	public String getDescription() {
		return description;
	}

	public long getCreated() {
		return created;
	}

	public String getCreateTimeStr() {
		return createTime;
	}

	public long getAdded() {
		return added;
	}

	public String getAddedTimeStr() {
		return addedTime;
	}

	public String getDurationStr() {
		return durationStr;
	}

	public long getDuration() {
		return duration;
	}

	public long getSize() {
		return size;
	}

	public String getPath() {
		return path;
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

	public String getImageUrl() {
		return avatar_url;
	}

	public int getType() {
		return type;
	}

	public boolean isBookmarked() {
		return bookmarked;
	}

	public void setBookmarked(boolean bookmarked) {
		this.bookmarked = bookmarked;
	}

	public int[] getAmps() {
		return amps;
	}

	public String  getItemData(){
		return itemData;
	}
	private String convertTimeToStr(long time) {
		return TimeUtils.formatDateTimeLocale(time);
//		return TimeUtils.formatTime(time);
	}

	private String convertDurationToStr(long dur) {
		return TimeUtils.formatTimeIntervalHourMinSec2(dur);
	}

	//----- START Parcelable implementation ----------
	private ItemData(Parcel in) {
		int[] ints = new int[6];
		in.readIntArray(ints);
		type = ints[0];
		sampleRate = ints[1];
		channelCount = ints[2];
		bitrate = ints[3];
		playStatus = ints[4];
		loadStatus = ints[5];
		long[] longs = new long[5];
		in.readLongArray(longs);
		id = longs[0];
		duration = longs[1];
		size = longs[2];
		created = longs[3];
		added = longs[4];
		String[] data = new String[10];
		in.readStringArray(data);
		name = data[0];
		format = data[1];
		path = data[2];
		description = data[3];
		durationStr = data[4];
		addedTime = data[5];
		createTime = data[6];
		avatar_url = data[7];
		itemData = data[8];
		msgSpeak = data[9];
		in.readIntArray(amps);
		boolean[] bools = new boolean[1];
		in.readBooleanArray(bools);
		bookmarked = bools[0];

	}

	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel out, int flags) {
		out.writeIntArray(new int[] {type, sampleRate, channelCount, bitrate,playStatus,loadStatus});
		out.writeLongArray(new long[] {id, duration, size, created, added});
		out.writeStringArray(new String[] {name, format, path, description, durationStr, addedTime, createTime, avatar_url,itemData,msgSpeak});
		out.writeIntArray(amps);
		out.writeBooleanArray(new boolean[] {bookmarked});
	}

	public static final Creator<ItemData> CREATOR
			= new Creator<ItemData>() {
		public ItemData createFromParcel(Parcel in) {
			return new ItemData(in);
		}

		public ItemData[] newArray(int size) {
			return new ItemData[size];
		}
	};
	//----- END Parcelable implementation ----------

	@Override
	public String toString() {
		return "ItemType{" +
				"id=" + id +
				", type=" + type +
				", name='" + name + '\'' +
				", format='" + format + '\'' +
				", path='" + path + '\'' +
				", description='" + description + '\'' +
				", durationStr='" + durationStr + '\'' +
				", duration=" + duration +
				", size=" + size +
				", sampleRate=" + sampleRate +
				", channelCount=" + channelCount +
				", bitrate=" + bitrate +
				", created=" + created +
				", added=" + added +
				", addedTime='" + addedTime + '\'' +
				", createTime='" + createTime + '\'' +
				", bookmarked=" + bookmarked +
				", avatar_url='" + avatar_url + '\'' +
				", amps=" + Arrays.toString(amps) +
				'}';
	}

	/**
	 * @return 0 成功；1 失败；2 加载中
	 */
	public int getLoadStatus() {
		return loadStatus;
	}
	/**
	 * @param loadStatus 0 成功；1 失败；2 加载中
	 */
	public void setLoading(int loadStatus){
		this.loadStatus = loadStatus;
	}
}
