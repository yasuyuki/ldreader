package org.jarx.android.livedoor.reader;

import java.util.HashMap;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

public class ReaderProvider extends ContentProvider {

    public static final String AUTHORITY = "org.jarx.android.livedoor.reader";

    private static final String DATABASE_NAME = "reader.db";
    private static final int DATABASE_VERSION = 2;

    private static final UriMatcher uriMatcher;
    private static final int URI_SUBSCRIPTION_ID = 10;
    private static final int URI_SUBSCRIPTIONS = 11;
    private static final int URI_ITEM_ID = 20;
    private static final int URI_ITEMS = 21;

    private static final String CONTENT_TYPE_ITEM
        = "vnd.android.cursor.item/vnd." + AUTHORITY;
    private static final String CONTENT_TYPE_DIR
        = "vnd.android.cursor.dir/vnd." + AUTHORITY;

    static {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(AUTHORITY, Subscription.TABLE_NAME + "/#", URI_SUBSCRIPTION_ID);
        uriMatcher.addURI(AUTHORITY, Subscription.TABLE_NAME, URI_SUBSCRIPTIONS);
        uriMatcher.addURI(AUTHORITY, Item.TABLE_NAME + "/#", URI_ITEM_ID);
        uriMatcher.addURI(AUTHORITY, Item.TABLE_NAME, URI_ITEMS);
    }

    private static class ReaderOpenHelper extends SQLiteOpenHelper {

        private ReaderOpenHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(Subscription.SQL_CREATE_TABLE);
            db.execSQL(Item.SQL_CREATE_TABLE);
            for (String column: Subscription.INDEX_COLUMNS) {
                db.execSQL(createIndex(Subscription.TABLE_NAME, column));
            }
            for (String column: Item.INDEX_COLUMNS) {
                db.execSQL(createIndex(Item.TABLE_NAME, column));
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("drop table if exists " + Subscription.TABLE_NAME);
            db.execSQL("drop table if exists " + Item.TABLE_NAME);
            onCreate(db);
        }

        private String createIndex(String tableName, String columnName) {
            StringBuilder buff = new StringBuilder(128);
            buff.append("create index idx_");
            buff.append(tableName);
            buff.append("_");
            buff.append(columnName);
            buff.append(" on ");
            buff.append(tableName);
            buff.append("(");
            buff.append(columnName);
            buff.append(")");
            return new String(buff);
        }
    }

    private ReaderOpenHelper openHelper;

    @Override
    public boolean onCreate() {
        this.openHelper = new ReaderOpenHelper(getContext());
        return true;
    }

    @Override
    public String getType(Uri uri) {
        switch (uriMatcher.match(uri)) {
        case URI_SUBSCRIPTION_ID:
        case URI_ITEM_ID:
            return CONTENT_TYPE_ITEM;
        case URI_SUBSCRIPTIONS:
        case URI_ITEMS:
            return CONTENT_TYPE_DIR;
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        switch (uriMatcher.match(uri)) {
        case URI_SUBSCRIPTION_ID:
            qb.setTables(Subscription.TABLE_NAME);
            qb.appendWhere(Subscription._ID + " = " + uri.getPathSegments().get(1));
            break;
        case URI_SUBSCRIPTIONS:
            qb.setTables(Subscription.TABLE_NAME);
            break;
        case URI_ITEM_ID:
            qb.setTables(Item.TABLE_NAME);
            qb.appendWhere(Item._ID + " = " + uri.getPathSegments().get(1));
            break;
        case URI_ITEMS:
            qb.setTables(Item.TABLE_NAME);
            break;
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        SQLiteDatabase db = this.openHelper.getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        String tableName;
        Uri contentUri;
        switch (uriMatcher.match(uri)) {
        case URI_SUBSCRIPTIONS:
            tableName = Subscription.TABLE_NAME;
            contentUri = Subscription.CONTENT_URI;
            break;
        case URI_ITEMS:
            tableName = Item.TABLE_NAME;
            contentUri = Item.CONTENT_URI;
            break;
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        SQLiteDatabase db = openHelper.getWritableDatabase();
        long rowId = db.insert(tableName, tableName, values);
        if (rowId > 0) {
            Uri insertedUri = ContentUris.withAppendedId(contentUri, rowId);
            getContext().getContentResolver().notifyChange(insertedUri, null);
            return insertedUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        SQLiteDatabase db = this.openHelper.getWritableDatabase();
        int count;
        switch (uriMatcher.match(uri)) {
        case URI_SUBSCRIPTION_ID:
            {
                String id = uri.getPathSegments().get(1);
                StringBuilder buff = new StringBuilder(128);
                buff.append(Subscription._ID);
                buff.append(" = ");
                buff.append(id);
                if (!TextUtils.isEmpty(where)) {
                    buff.append(" and ");
                    buff.append(where);
                }
                count = db.update(Subscription.TABLE_NAME, values, new String(buff), whereArgs);
            }
            break;
        case URI_SUBSCRIPTIONS:
            count = db.update(Subscription.TABLE_NAME, values, where, whereArgs);
            break;
        case URI_ITEM_ID:
            {
                String id = uri.getPathSegments().get(1);
                StringBuilder buff = new StringBuilder(128);
                buff.append(Item._ID);
                buff.append(" = ");
                buff.append(id);
                if (!TextUtils.isEmpty(where)) {
                    buff.append(" and ");
                    buff.append(where);
                }
                count = db.update(Item.TABLE_NAME, values, new String(buff), whereArgs);
            }
            break;
        case URI_ITEMS:
            count = db.update(Item.TABLE_NAME, values, where, whereArgs);
            break;
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
}
