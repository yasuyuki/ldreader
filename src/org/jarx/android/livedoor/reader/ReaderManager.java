package org.jarx.android.livedoor.reader;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.simple.parser.ContentHandler;
import org.json.simple.parser.ParseException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import static org.jarx.android.livedoor.reader.Utils.*; 

public class ReaderManager {

    public static final int API_ALL_LIMIT = 200;

    private static final String TAG = "ReaderManager";

    public static ReaderManager newInstance(Context context) {
        return new ReaderManager(context);
    }

    private final ApiClient client = new ApiClient();
    private final Context context;

    public ReaderManager(Context context) {
        this.context = context;
    }

    public int sync() throws IOException, ReaderException {
        if (!isLogined()) {
            login();
        }

        final boolean unreadOnly = ReaderPreferences.isSyncUnreadOnly(this.context);
        Log.d(TAG, "sync " + (unreadOnly ? "unread only": "all"));
        SubsHandler subsHandler = new SubsHandler();
        syncSubs(unreadOnly, subsHandler);

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            return 0;
        }

        ReaderException firstError = null;
        int syncCount = 0;
        String subWhere = Subscription._MODIFIED_TIME
            + " <> " + Subscription._ITEM_SYNC_TIME;
        ContentResolver cr = this.context.getContentResolver();
        Subscription.FilterCursor cursor = new Subscription.FilterCursor(
            cr.query(Subscription.CONTENT_URI, null, subWhere, null, null));
        try {
            while (cursor.moveToNext()) {
                Subscription sub = cursor.getSubscription();
                Log.d(TAG, "sync items for subscription " + sub.getUri());
                try {
                    syncCount += syncItems(sub, null, unreadOnly);
                } catch (ReaderException e) {
                    if (firstError == null) {
                        firstError = e;
                    }
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
        } finally {
            cursor.close();
        }

        if (ReaderPreferences.isAutoTouchAll(this.context)) {
            for (long id: subsHandler.ids) {
                try {
                    this.client.touchAll(id);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
        }

        return syncCount;
    }

    public int syncSubs(boolean unreadOnly) throws IOException, ReaderException {
        return syncSubs(unreadOnly, null);
    }

    private int syncSubs(boolean unreadOnly, SubsHandler subsHandler)
            throws IOException, ReaderException {
        if (!isLogined()) {
            login();
        }
        if (subsHandler == null) {
            subsHandler = new SubsHandler();
        }

        final int limit = 100;
        int syncCount = 0;
        int len = 0;
        try {
            do {
                this.client.handleSubs(unreadOnly, syncCount, limit, subsHandler);
                len = subsHandler.counter;
                syncCount += len;
            } while (len == limit);
        } catch (ParseException e) {
            throw new ReaderException("json parse error", e);
        }

        this.context.sendBroadcast(
            new Intent(ReaderService.ACTION_SYNC_SUBS_FINISHED));

        return syncCount;
    }

    public int syncItems(long subId, boolean unreadOnly)
            throws IOException, ReaderException {
        Uri subUri = ContentUris.withAppendedId(Subscription.CONTENT_URI, subId);
        ContentResolver cr = this.context.getContentResolver();
        Cursor cursor = cr.query(subUri, null, null, null, null);
        if (!cursor.moveToFirst()) {
            return 0;
        }
        Subscription sub = new Subscription.FilterCursor(cursor).getSubscription();
        return syncItems(sub, subUri, unreadOnly);
    }

    public int syncItems(Subscription sub, Uri subUri, boolean unreadOnly)
            throws IOException, ReaderException {
        if (!isLogined()) {
            login();
        }

        long subId = sub.getId();
        long subModifiedTime = sub.getModifiedTime();
        if (subUri == null) {
            subUri = ContentUris.withAppendedId(Subscription.CONTENT_URI, subId);
        }

        int syncCount = 0;
        ContentResolver cr = this.context.getContentResolver();
        ItemsHandler itemHandler = new ItemsHandler(subId);
        try {
            try {
                this.client.handleUnread(subId, itemHandler);
                syncCount = itemHandler.counter;
            } catch (IOException e) {
                // NOTE: ignore. if no unread item, server http status 500
            }

            if (syncCount == 0 && !unreadOnly) {
                do {
                    itemHandler.unread = false;
                    this.client.handleAll(subId, syncCount, API_ALL_LIMIT, itemHandler);
                    if (itemHandler.nomore || itemHandler.counter < API_ALL_LIMIT) {
                        break;
                    }
                    syncCount += itemHandler.counter;
                } while (false);
            }

            String where = Item._SUBSCRIPTION_ID + " = " + subId
                + " and " + Item._UNREAD + " = 1";
            Cursor cursor = cr.query(Item.CONTENT_URI, null, where, null, null);
            int unreadCount = cursor.getCount();
            cursor.close();

            ContentValues subValues = new ContentValues();
            subValues.put(Subscription._ITEM_SYNC_TIME, subModifiedTime);
            subValues.put(Subscription._UNREAD_COUNT, unreadCount);
            cr.update(subUri, subValues, null, null);
        } catch (ParseException e) {
            throw new ReaderException("json parse error", e);
        }
        return syncCount;
    }

    public boolean login() throws IOException, ReaderException {
        String loginId = ReaderPreferences.getLoginId(this.context);
        String password = ReaderPreferences.getPassword(this.context);
        return login(loginId, password);
    }

    public boolean login(String loginId, String password)
            throws IOException, ReaderException {
        return this.client.login(loginId, password);
    }

    public void logout() throws IOException {
        this.client.logout();
    }

    public boolean isLogined() {
        return this.client.isLogined();
    }

    public String getLoginId() {
        return this.client.getLoginId();
    }

    public int countUnread() {
        ContentResolver cr = this.context.getContentResolver();
        String where = Item._UNREAD + " = 1";
        Cursor cursor = cr.query(Item.CONTENT_URI, null, where, null, null);
        try {
            return cursor.getCount();
        } finally {
            cursor.close();
        }
    }

    private class SubsHandler extends ContentHandlerAdapter {

        private ContentValues values;
        private int unreadCount;
        private int counter;
        private List<Long> ids = new ArrayList<Long>();

        public void startJSON() throws ParseException, IOException {
            this.counter = 0;
        }

        public boolean startObject() throws ParseException, IOException {
            this.values = new ContentValues();
            this.unreadCount = 0;
            this.counter++;
            return true;
        }

        public boolean endObject() throws ParseException, IOException {
            if (this.values != null) {
                long id = this.values.getAsLong(Subscription._ID);
                Uri uri = ContentUris.withAppendedId(Subscription.CONTENT_URI, id);
                ContentResolver cr = ReaderManager.this.context.getContentResolver();
                if (cr.update(uri, this.values, null, null) == 0) {
                    try {
                        bindIcon();
                    } catch (IOException e) {
                        // ignore error for icon
                    }
                    this.values.put(Subscription._UNREAD_COUNT, this.unreadCount);
                    cr.insert(Subscription.CONTENT_URI, this.values);
                }
                this.ids.add(id);
                this.values = null;
            }
            return true;
        }

        public boolean primitive(Object value) throws ParseException, IOException {
            if (this.key == null || this.values == null) {
                return true;
            } else if (this.key.equals("subscribe_id")) {
                this.values.put(Subscription._ID, asLong(value));
            } else if (this.key.equals("title")) {
                this.values.put(Subscription._TITLE, asString(value));
            } else if (this.key.equals("icon")) {
                String iconUri = asString(value);
                this.values.put(Subscription._ICON_URI, iconUri);
            } else if (this.key.equals("link")) {
                this.values.put(Subscription._URI, asString(value));
            } else if (this.key.equals("folder")) {
                this.values.put(Subscription._FOLDER, asString(value));
            } else if (this.key.equals("rate")) {
                this.values.put(Subscription._RATE, asInt(value));
            } else if (this.key.equals("unread_count")) {
                this.unreadCount = asInt(value);
            } else if (this.key.equals("subscribers_count")) {
                this.values.put(Subscription._SUBSCRIBERS_COUNT, asInt(value));
            } else if (this.key.equals("modified_on")) {
                this.values.put(Subscription._MODIFIED_TIME, asLong(value));
            }
            return true;
        }

        private void bindIcon() throws IOException {
            String iconUri = this.values.getAsString(Subscription._ICON_URI);
            if (TextUtils.isEmpty(iconUri)) {
                return;
            }
            Bitmap icon = null;
            InputStream in = ReaderManager.this.client.doGetInputStream(iconUri);
            try {
                icon = BitmapFactory.decodeStream(in);
            } finally {
                in.close();
            }
            int size = icon.getWidth() * icon.getHeight() * 2;
            ByteArrayOutputStream out = new ByteArrayOutputStream(size);
            icon.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            this.values.put(Subscription._ICON, out.toByteArray());
        }
    }

    private class ItemsHandler extends ContentHandlerAdapter {

        private long subscriptionId;
        private ContentValues values;
        private boolean startItems;
        private int counter;
        private boolean nomore;
        private boolean unread = true;

        private ItemsHandler(long subscriptionId) {
            this.subscriptionId = subscriptionId;
        }

        public void startJSON() throws ParseException, IOException {
            this.counter = 0;
        }

        public boolean startObject() throws ParseException, IOException {
            if (this.startItems) {
                this.values = new ContentValues();
                this.values.put(Item._SUBSCRIPTION_ID, this.subscriptionId);
                this.counter++;
            }
            return true;
        }

        public boolean endObject() throws ParseException, IOException {
            if (this.startItems) {
                long id = this.values.getAsLong(Item._ID);
                Uri uri = ContentUris.withAppendedId(Item.CONTENT_URI, id);
                ContentResolver cr = ReaderManager.this.context.getContentResolver();
                Cursor cursor = cr.query(uri, null, null, null, null);
                this.nomore = (cursor.getCount() > 0);
                cursor.close();
                if (this.nomore) {
                    return false;
                }
                this.values.put(Item._UNREAD, (this.unread ? 1: 0));
                cr.insert(Item.CONTENT_URI, this.values);
                Log.d(TAG, "insert item " + this.values.get(Item._URI));
                this.values = null;
            }
            return true;
        }

        public boolean startArray() throws ParseException, IOException {
            if (!this.startItems && this.key.equals("items")) {
                this.startItems = true;
            }
            return true;
        }

        public boolean endArray() throws ParseException, IOException {
            if (this.startItems) {
                this.startItems = false;
            }
            return true;
        }

        public boolean primitive(Object value)
                throws ParseException, IOException {
            if (this.key == null || this.values == null) {
                return true;
            } else if (this.key.equals("id")) {
                this.values.put(Item._ID, asLong(value));
            } else if (this.key.equals("title")) {
                this.values.put(Item._TITLE, asString(value));
            } else if (this.key.equals("body")) {
                this.values.put(Item._BODY, asString(value));
            } else if (this.key.equals("author")) {
                this.values.put(Item._AUTHOR, asString(value));
            } else if (this.key.equals("link")) {
                this.values.put(Item._URI, asString(value));
            } else if (this.key.equals("created_on")) {
                this.values.put(Item._CREATED_TIME, asLong(value));
            } else if (this.key.equals("modified_on")) {
                this.values.put(Item._MODIFIED_TIME, asLong(value));
            }
            return true;
        }
    }

    private static abstract class ContentHandlerAdapter implements ContentHandler {

        protected String key;

        public void startJSON() throws ParseException, IOException {
        }

        public void endJSON() throws ParseException, IOException {
        }

        public boolean startObject() throws ParseException, IOException {
            return true;
        }

        public boolean endObject() throws ParseException, IOException {
            return true;
        }

        public boolean startObjectEntry(String key)
                throws ParseException, IOException {
            this.key = key;
            return true;
        }

        public boolean endObjectEntry() throws ParseException, IOException {
            this.key = null;
            return true;
        }

        public boolean startArray() throws ParseException, IOException {
            return true;
        }

        public boolean endArray() throws ParseException, IOException {
            return true;
        }

        public boolean primitive(Object value)
                throws ParseException, IOException {
            return true;
        }
    }
}
