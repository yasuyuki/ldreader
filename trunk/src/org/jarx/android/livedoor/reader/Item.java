package org.jarx.android.livedoor.reader;

import java.io.Serializable;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import static org.jarx.android.livedoor.reader.Utils.*; 

public class Item implements Serializable, BaseColumns {

    public static final String TABLE_NAME = "item";

    public static final String CONTENT_URI_NAME
        = "content://" + ReaderProvider.AUTHORITY + "/" + TABLE_NAME;

    public static final Uri CONTENT_URI = Uri.parse(CONTENT_URI_NAME);

    public static final String _SUBSCRIPTION_ID = "subscription_id";
    public static final String _URI = "uri";
    public static final String _TITLE = "title";
    public static final String _BODY = "body";
    public static final String _AUTHOR = "author";
    public static final String _UNREAD = "unread";
    public static final String _CREATED_TIME = "created_time";
    public static final String _MODIFIED_TIME = "modified_time";

    public static final String SQL_CREATE_TABLE
        = "create table if not exists " + TABLE_NAME + " ("
        + _ID + " integer primary key,"
        + _SUBSCRIPTION_ID + " integer,"
        + _URI + " text,"
        + _TITLE + " text,"
        + _BODY + " text,"
        + _AUTHOR + " text,"
        + _UNREAD + " integer,"
        + _CREATED_TIME + " integer,"
        + _MODIFIED_TIME + " integer"
        + ")";

    public static final String[] INDEX_COLUMNS = {
        _SUBSCRIPTION_ID,
        _TITLE,
        _UNREAD,
        _CREATED_TIME,
        _MODIFIED_TIME
    };

    public static String[] sqlForUpgrade(int oldVersion, int newVersion) {
        return new String[0];
    }

    private long id;
    private long subscriptionId;
    private String uri;
    private String title;
    private String body;
    private String author;
    private boolean unread;
    private long createdTime;
    private long modifiedTime;

    public Item() {
    }

    public long getId() {
        return this.id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getSubscriptionId() {
        return this.subscriptionId;
    }

    public void setSubscriptionId(long subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return this.body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getUri() {
        return this.uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getAuthor() {
        return this.author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public boolean isUnread() {
        return this.unread;
    }

    public void setUnread(boolean unread) {
        this.unread = unread;
    }

    public long getCreatedTime() {
        return this.createdTime;
    }

    public void setCreatedTime(long createdTime) {
        this.createdTime = createdTime;
    }

    public long getModifiedTime() {
        return this.modifiedTime;
    }

    public void setModifiedTime(long modifiedTime) {
        this.modifiedTime = modifiedTime;
    }

    public long getCreatedOrModifiedTime() {
        return Math.max(this.createdTime, this.modifiedTime);
    }

    public static class FilterCursor {

        private final Item item;
        private final Cursor cursor;
        private final int posId;
        private final int posSubscriptionId;
        private final int posUri;
        private final int posTitle;
        private final int posBody;
        private final int posAuthor;
        private final int posUnread;
        private final int posCreatedTime;
        private final int posModifiedTime;

        public FilterCursor(Cursor cursor) {
            this(cursor, null);
        }

        public FilterCursor(Cursor cursor, Item item) {
            this.item = item;
            this.cursor = cursor;
            this.posId = cursor.getColumnIndex(Item._ID);
            this.posSubscriptionId = cursor.getColumnIndex(Item._SUBSCRIPTION_ID);
            this.posUri = cursor.getColumnIndex(Item._URI);
            this.posTitle = cursor.getColumnIndex(Item._TITLE);
            this.posBody = cursor.getColumnIndex(Item._BODY);
            this.posAuthor = cursor.getColumnIndex(Item._AUTHOR);
            this.posUnread = cursor.getColumnIndex(Item._UNREAD);
            this.posCreatedTime = cursor.getColumnIndex(Item._CREATED_TIME);
            this.posModifiedTime = cursor.getColumnIndex(Item._MODIFIED_TIME);
        }

        public Item getItem() {
            Item item = (this.item == null) ? new Item(): this.item;
            item.setId(this.cursor.getLong(this.posId));
            item.setSubscriptionId(this.cursor.getLong(this.posSubscriptionId));
            item.setUri(this.cursor.getString(this.posUri));
            item.setTitle(this.cursor.getString(this.posTitle));
            item.setBody(this.cursor.getString(this.posBody));
            item.setAuthor(this.cursor.getString(this.posAuthor));
            item.setUnread(this.cursor.getInt(this.posUnread) == 1);
            item.setCreatedTime(this.cursor.getLong(this.posCreatedTime));
            item.setModifiedTime(this.cursor.getLong(this.posModifiedTime));
            return item;
        }

        public int getCount() {
            return this.cursor.getCount();
        }

        public boolean moveToPosition(int position) {
            return this.cursor.moveToPosition(position);
        }

        public boolean moveToNext() {
            return this.cursor.moveToNext();
        }

        public Cursor getCursor() {
            return this.cursor;
        }

        public void deactivate() {
            this.cursor.deactivate();
        }

        public boolean requery() {
            return this.cursor.requery();
        }

        public boolean isClosed() {
            return this.cursor.isClosed();
        }

        public void close() {
            this.cursor.close();
        }
    }
}
