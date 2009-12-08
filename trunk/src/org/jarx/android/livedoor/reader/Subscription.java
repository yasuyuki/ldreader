package org.jarx.android.livedoor.reader;

import java.io.Serializable;
import java.util.Comparator;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Parcelable;
import android.os.Parcel;
import android.provider.BaseColumns;
import static org.jarx.android.livedoor.reader.Utils.*; 

public final class Subscription implements Serializable, BaseColumns {

    public static final String TABLE_NAME = "subscription";

    public static final Uri CONTENT_URI
        = Uri.parse(ReaderProvider.SUB_CONTENT_URI_NAME);
    public static final Uri FOLDER_CONTENT_URI
        = Uri.parse(ReaderProvider.SUB_FOLDER_CONTENT_URI_NAME);
    public static final Uri RATE_CONTENT_URI
        = Uri.parse(ReaderProvider.SUB_RATE_CONTENT_URI_NAME);

    public static final String _URI = "uri";
    public static final String _TITLE = "title";
    public static final String _ICON_URI = "icon_uri";
    public static final String _ICON = "icon";
    public static final String _RATE = "rate";
    public static final String _SUBSCRIBERS_COUNT = "subscribers_count";
    public static final String _UNREAD_COUNT = "unread_count";
    public static final String _FOLDER = "folder";
    public static final String _MODIFIED_TIME = "modified_time";
    public static final String _ITEM_SYNC_TIME = "item_sync_time";
    // NOTE database version 5 or later
    public static final String _DISABLED = "disabled";
    public static final String _READ_ITEM_ID = "read_item_id";

    public static final int GROUP_FOLDER = 1;
    public static final int GROUP_RATE = 2;

    public static final String SQL_CREATE_TABLE
        = "create table if not exists " + TABLE_NAME + " ("
        + _ID + " integer primary key, "
        + _URI + " text,"
        + _TITLE + " text,"
        + _ICON_URI + " text,"
        + _ICON + " blob,"
        + _RATE + " integer,"
        + _SUBSCRIBERS_COUNT + " integer,"
        + _UNREAD_COUNT + " integer,"
        + _FOLDER + " text,"
        + _MODIFIED_TIME + " integer,"
        + _ITEM_SYNC_TIME + " integer default 0,"
        + _DISABLED + " integer,"
        + _READ_ITEM_ID + " integer"
        + ")";

    public static final String[] INDEX_COLUMNS = {
        _RATE,
        _SUBSCRIBERS_COUNT,
        _UNREAD_COUNT,
        _FOLDER,
        _MODIFIED_TIME,
        _ITEM_SYNC_TIME,
        _DISABLED
    };

    public static final String[] SORT_ORDERS = {
        _MODIFIED_TIME + " desc",
        _MODIFIED_TIME + " asc",
        _UNREAD_COUNT + " desc",
        _UNREAD_COUNT + " asc",
        _TITLE + " asc",
        _RATE + " desc",
        _SUBSCRIBERS_COUNT + " desc",
        _SUBSCRIBERS_COUNT + " asc"
    };

    public static String[] sqlForUpgrade(int oldVersion, int newVersion) {
        if (oldVersion < 5) {
            return new String[] {
                "alter table " + TABLE_NAME + " add " + _DISABLED + " integer",
                "alter table " + TABLE_NAME + " add " + _READ_ITEM_ID + " integer",
                ReaderProvider.sqlCreateIndex(TABLE_NAME, _DISABLED)
            };
        }
        return new String[0];
    }

    private long id;
    private String uri;
    private String title;
    private String iconUri;
    private int rate;
    private int subscribersCount;
    private int unreadCount;
    private String folder;
    private Bitmap icon;
    private long modifiedTime;
    private long itemSyncTime;
    private boolean disabled;
    private long readItemId;

    public Subscription() {
    }

    public long getId() {
        return this.id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUri() {
        return this.uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getTitle() {
        return this.title = title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getIconUri() {
        return this.iconUri;
    }

    public void setIconUri(String iconUri) {
        this.iconUri = iconUri;
    }

    public Bitmap getIcon() {
        return this.icon;
    }

    public void setIcon(Bitmap icon) {
        this.icon = icon;
    }

    public int getRate() {
        return this.rate;
    }

    public void setRate(int rate) {
        this.rate = rate;
    }

    public int getSubscribersCount() {
        return this.subscribersCount;
    }

    public void setSubscribersCount(int subscribersCount) {
        this.subscribersCount = subscribersCount;
    }

    public int getUnreadCount() {
        return this.unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    public String getFolder() {
        return this.folder;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }

    public long getModifiedTime() {
        return this.modifiedTime;
    }

    public void setModifiedTime(long modifiedTime) {
        this.modifiedTime = modifiedTime;
    }

    public long getItemSyncTime() {
        return this.itemSyncTime;
    }

    public void setItemSyncTime(long itemSyncTime) {
        this.itemSyncTime = itemSyncTime;
    }

    public boolean isDisabled() {
        return this.disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public long getReadItemId() {
        return this.readItemId;
    }

    public void setReadItemId(long readItemId) {
        this.readItemId = readItemId;
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof Subscription) {
            Subscription s = (Subscription) o;
            return s.getId() == this.getId();
        }
        return false;
    }

    public String toString() {
        return "Subscription{id=" + this.id + ",title=" + this.title + "}";
    }

    public static class FilterCursor extends CursorWrapper {

        private final Cursor cursor;
        private final Subscription sub;
        private final int posId;
        private final int posUri;
        private final int posTitle;
        private final int posIcon;
        private final int posRate;
        private final int posSubsCount;
        private final int posUnreadCount;
        private final int posFolder;
        private final int posModifiedTime;
        private final int posItemSyncTime;
        private final int posDisabled;
        private final int posReadItemId;

        public FilterCursor(Cursor cursor) {
            this(cursor, null);
        }

        public FilterCursor(Cursor cursor, Subscription sub) {
            super(cursor);
            this.cursor = cursor;
            this.sub = sub;
            this.posId = cursor.getColumnIndex(Subscription._ID);
            this.posUri = cursor.getColumnIndex(Subscription._URI);
            this.posTitle = cursor.getColumnIndex(Subscription._TITLE);
            this.posIcon = cursor.getColumnIndex(Subscription._ICON);
            this.posRate = cursor.getColumnIndex(Subscription._RATE);
            this.posSubsCount = cursor.getColumnIndex(Subscription._SUBSCRIBERS_COUNT);
            this.posUnreadCount = cursor.getColumnIndex(Subscription._UNREAD_COUNT);
            this.posFolder = cursor.getColumnIndex(Subscription._FOLDER);
            this.posModifiedTime = cursor.getColumnIndex(Subscription._MODIFIED_TIME);
            this.posItemSyncTime = cursor.getColumnIndex(Subscription._ITEM_SYNC_TIME);
            this.posDisabled = cursor.getColumnIndex(Subscription._DISABLED);
            this.posReadItemId = cursor.getColumnIndex(Subscription._READ_ITEM_ID);
        }

        public Subscription getSubscription() {
            Subscription sub = (this.sub == null) ? new Subscription(): this.sub;
            sub.setId(this.cursor.getLong(this.posId));
            sub.setUri(this.cursor.getString(this.posUri));
            sub.setTitle(this.cursor.getString(this.posTitle));
            sub.setRate(this.cursor.getInt(this.posRate));
            sub.setSubscribersCount(this.cursor.getInt(this.posSubsCount));
            sub.setUnreadCount(this.cursor.getInt(this.posUnreadCount));
            sub.setFolder(this.cursor.getString(this.posFolder));
            sub.setModifiedTime(this.cursor.getLong(this.posModifiedTime));
            sub.setItemSyncTime(this.cursor.getLong(this.posItemSyncTime));
            sub.setDisabled(this.cursor.getInt(this.posDisabled) == 1);
            sub.setReadItemId(this.cursor.getInt(this.posReadItemId));
            byte[] data = cursor.getBlob(this.posIcon);
            if (data != null) {
                Bitmap icon = BitmapFactory.decodeByteArray(data, 0, data.length);
                sub.setIcon(icon);
                data = null;
            }
            return sub;
        }

        public Cursor getCursor() {
            return this.cursor;
        }
    }
}
