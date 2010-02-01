package org.jarx.android.livedoor.reader;

import java.io.IOException;
import android.app.Activity; 
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RatingBar;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class ItemListActivity extends ListActivity {

    private static final String TAG = "ItemListActivity";
    private static final int DIALOG_MOVE = 1;
    private static final int REQ_ITEM_ID = 1;

    private final Handler handler = new Handler();
    private Uri subUri;
    private Subscription sub;
    private long lastItemId;
    private ItemsAdapter itemsAdapter;
    private ReaderService readerService;
    private ReaderManager readerManager;
    private String keyword;
    private boolean unreadOnly;

    private ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            ReaderService.ReaderBinder binder = (ReaderService.ReaderBinder) service;
            ItemListActivity.this.readerService = binder.getService();
            ItemListActivity.this.readerManager = binder.getManager();
            if (ItemListActivity.this.itemsAdapter != null
                    && ItemListActivity.this.itemsAdapter.getCount() == 0) {
                ItemListActivity.this.progressSyncItems();
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName className) {
            ItemListActivity.this.readerService = null;
            ItemListActivity.this.readerManager = null;
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        bindService(new Intent(this, ReaderService.class), this.serviceConn,
            Context.BIND_AUTO_CREATE);

        Window w = getWindow();
        w.requestFeature(Window.FEATURE_LEFT_ICON);
        setContentView(R.layout.item_list);
        w.setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.icon_s);

        ActivityHelper.bindTitle(this);

        Intent intent = getIntent();
        long subId = intent.getLongExtra(ActivityHelper.EXTRA_SUB_ID, 0);
        this.subUri = ContentUris.withAppendedId(Subscription.CONTENT_URI, subId);
        bindSubTitleView(true);
        ImageView iconView = (ImageView) findViewById(R.id.sub_icon);
        iconView.setImageBitmap(sub.getIcon());

        final TextView keywordEdit = (TextView) findViewById(R.id.edit_keyword);
        keywordEdit.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN
                        && keyCode == KeyEvent.KEYCODE_ENTER) {
                    handleSearch(v, keywordEdit);
                    return true;
                }
                return false;
            }
        });
        View search = findViewById(R.id.btn_search);
        search.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                handleSearch(v, keywordEdit);
            }
        });

        initListAdapter();
    }

    @Override
    public void onResume() {
        super.onResume();
        moveToItemId(this.lastItemId);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(this.serviceConn);
        if (this.itemsAdapter != null) {
            this.itemsAdapter.closeCursor();
            this.itemsAdapter = null;
        }
    }

    private void bindSubTitleView(boolean reloadSub) {
        if (this.subUri == null) {
            return;
        }
        if (reloadSub) {
            ContentResolver cr = getContentResolver();
            Cursor cursor = cr.query(this.subUri, null, null, null, null);
            cursor.moveToFirst();
            this.sub = new Subscription.FilterCursor(cursor).getSubscription();
            cursor.close();
        }
        TextView subTitleView = (TextView) findViewById(R.id.sub_title);
        subTitleView.setText(this.sub.getTitle() + " (" + this.sub.getUnreadCount() + ")");
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case DIALOG_MOVE:
            return new AlertDialog.Builder(this)
                .setIcon(R.drawable.alert_dialog_icon)
                .setTitle(R.string.dialog_item_list_move_title)
                .setSingleChoiceItems(R.array.dialog_litem_list_move, 0,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int i) {
                            switch (i) {
                            case 0:
                                moveToLastRead();
                                break;
                            case 1:
                                moveToNewUnread();
                                break;
                            case 2:
                                moveToOldUnread();
                                break;
                            }
                            dialog.dismiss();
                        }
                    }
                ).create();
        }
        return null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.item_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
        case R.id.menu_item_reload:
            progressSyncItems();
            return true;
        case R.id.menu_item_touch_feed_local:
            progressTouchFeedLocal();
            return true;
        case R.id.menu_item_move:
            showDialog(DIALOG_MOVE);
            return true;
        case R.id.menu_unreads:
            toggleUnreadOnly(menuItem);
            return true;
        case R.id.menu_search:
            toggleSearchBar();
            return true;
        case R.id.menu_item_setting:
            startActivity(new Intent(this, ReaderPreferenceActivity.class));
            return true;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_ITEM_ID && data != null) {
            this.lastItemId = data.getLongExtra(ActivityHelper.EXTRA_ITEM_ID, 0);
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Long itemId = (Long) v.getTag();
        if (itemId != null) {
            this.lastItemId = itemId;
            Intent intent = new Intent(this, ItemActivity.class)
                .putExtra(ActivityHelper.EXTRA_SUB_ID, this.sub.getId())
                .putExtra(ActivityHelper.EXTRA_ITEM_ID, itemId)
                .putExtra(ActivityHelper.EXTRA_WHERE, createBaseWhere());
            startActivityForResult(intent, REQ_ITEM_ID);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_SEARCH:
            toggleSearchBar();
            return true;
        case KeyEvent.KEYCODE_BACK:
            View searchBar = findViewById(R.id.search_bar);
            if (searchBar.getVisibility() == View.VISIBLE) {
                toggleSearchBar();
                return true;
            }
            break;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void toggleUnreadOnly(MenuItem menuItem) {
        this.unreadOnly = !this.unreadOnly;
        initListAdapter();
        if (this.unreadOnly) {
            menuItem.setTitle("Show unread");
        } else {
            menuItem.setTitle("Hide unread");
        }
    }

    private void toggleSearchBar() {
        View searchBar = findViewById(R.id.search_bar);
        if (searchBar.getVisibility() == View.VISIBLE) {
            searchBar.setVisibility(View.GONE);
            if (this.keyword != null) {
                this.keyword = null;
                initListAdapter();
            }
        } else {
            searchBar.setVisibility(View.VISIBLE);
        }
    }

    private void handleSearch(View v, TextView keywordEdit) {
        CharSequence keywordChars = keywordEdit.getText();
        if (keywordChars != null) {
            this.keyword = keywordChars.toString();
            initListAdapter();
        }
        InputMethodManager imm = (InputMethodManager)
            getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromInputMethod(v.getWindowToken(), 0);
        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    private ActivityHelper.Where createBaseWhere() {
        String keyword = this.keyword;
        String[] args = null;
        StringBuilder buff = new StringBuilder(
            (keyword == null) ? 64: 128 + keyword.length());
        buff.append(Item._SUBSCRIPTION_ID).append(" = ").append(this.sub.getId());
        if (keyword != null && keyword.length() > 0) {
            buff.append(" and (");
            buff.append(Item._TITLE).append(" like ? escape '\\'");
            buff.append(" or ");
            buff.append(Item._BODY).append(" like ? escape '\\'");
            buff.append(")");
            keyword = keyword.replaceAll("\\\\", "\\\\\\\\");
            keyword = keyword.replaceAll("%", "\\%");
            keyword = keyword.replaceAll("_", "\\_");
            keyword = "%" + keyword + "%";
            args = new String[]{keyword, keyword};
        }
        if (this.unreadOnly) {
            buff.append(" and ");
            buff.append(Item._UNREAD).append(" = 1");
        }
        return new ActivityHelper.Where(buff, args);
    }

    private void initListAdapter() {
        ActivityHelper.Where where = createBaseWhere();
        String orderby = Item._ID + " desc";
        Cursor cursor = managedQuery(Item.CONTENT_URI, null,
            new String(where.buff), where.args, orderby);;
        if (this.itemsAdapter == null) {
            this.itemsAdapter = new ItemsAdapter(this, cursor);
            setListAdapter(this.itemsAdapter);
        } else {
            this.itemsAdapter.changeCursor(cursor);
        }
    }

    private void moveToItemId(long itemId) {
        if (itemId <= 0) {
            return;
        }
        ActivityHelper.Where where = createBaseWhere();
        where.buff.append(" and ");
        where.buff.append(Item._ID).append(" > ").append(itemId);
        ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(Item.CONTENT_URI, Item.SELECT_COUNT,
                new String(where.buff), where.args, null);
        cursor.moveToNext();
        int pos = cursor.getInt(0);
        cursor.close();
        getListView().setSelectionFromTop(pos, 48);
    }

    private void moveToLastRead() {
        if (this.lastItemId == 0) {
            this.lastItemId = this.sub.getReadItemId();
        }
        moveToItemId(this.lastItemId);
    }

    private void moveToNewUnread() {
        if (this.itemsAdapter == null) {
            return;
        }
        Item.FilterCursor cursor = this.itemsAdapter.getItemCursor();
        int pos = cursor.getPosition();
        cursor.moveToFirst();
        while (cursor.moveToNext()) {
            if (cursor.isUnread()) {
                getListView().setSelectionFromTop(cursor.getPosition(), 48);
                return;
            }
        }
        cursor.moveToPosition(pos);
    }

    private void moveToOldUnread() {
        if (this.itemsAdapter == null) {
            return;
        }
        if (this.itemsAdapter == null) {
            return;
        }
        Item.FilterCursor cursor = this.itemsAdapter.getItemCursor();
        int pos = cursor.getPosition();
        cursor.moveToLast();
        while (cursor.moveToPrevious()) {
            if (cursor.isUnread()) {
                getListView().setSelectionFromTop(cursor.getPosition(), 48);
                return;
            }
        }
        cursor.moveToPosition(pos);
    }

    private void progressSyncItems() {
        final long subId = this.sub.getId();
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setIndeterminate(true);
        dialog.setMessage(getText(R.string.msg_sync_running));
        dialog.show();
        new Thread() {
            public void run() {
                ReaderManager rm = ItemListActivity.this.readerManager;
                try {
                    rm.syncItems(subId, false);
                } catch (IOException e) {
                    showToast(e);
                } catch (Throwable e) {
                    showToast(e);
                }
                handler.post(new Runnable() {
                    public void run() {
                        initListAdapter();
                        dialog.dismiss();
                    }
                });
            }
        }.start();
    }

    private void progressTouchFeedLocal() {
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setIndeterminate(true);
        dialog.setMessage(getText(R.string.msg_touch_running));
        dialog.show();
        final long subId = this.sub.getId();
        new Thread() {
            public void run() {
                ContentResolver cr = getContentResolver();
                ContentValues values = new ContentValues();

                StringBuilder where = new StringBuilder(64);
                where.append(Item._UNREAD + " = 1");
                where.append(" and ");
                where.append(Item._SUBSCRIPTION_ID + " = " + subId);
                values.put(Item._UNREAD, 0);
                cr.update(Item.CONTENT_URI, values, new String(where), null);

                values.clear();
                values.put(Subscription._UNREAD_COUNT, 0);
                cr.update(ItemListActivity.this.subUri, values, null, null);

                handler.post(new Runnable() {
                    public void run() {
                        initListAdapter();
                        showToast(getText(R.string.msg_touch_feed_local));
                        dialog.dismiss();
                    }
                });
            }
        }.start();
    }

    private void showToast(IOException e) {
        e.printStackTrace();
        showToast(getText(R.string.err_io) + " (" + e.getLocalizedMessage() + ")");
    }

    private void showToast(Throwable e) {
        e.printStackTrace();
        showToast(e.getLocalizedMessage());
    }

    private void showToast(final CharSequence text) {
        this.handler.post(new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(),
                    text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private class ItemsAdapter extends ResourceCursorAdapter {

        private ItemsAdapter(Context context, Cursor cursor) {
            super(context, R.layout.item_list_row,
                new Item.FilterCursor(cursor));
        }

        private Item.FilterCursor getItemCursor() {
            return (Item.FilterCursor) getCursor();
        }

        private void closeCursor() {
            Cursor cursor = getCursor();
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }

        @Override
        public void changeCursor(Cursor cursor) {
            super.changeCursor(new Item.FilterCursor(cursor));
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            Item.FilterCursor itemCursor = (Item.FilterCursor) cursor;

            ImageView iconView = (ImageView) view.findViewById(R.id.icon_read_unread);
            TextView titleView = (TextView) view.findViewById(R.id.title);
            TextView summaryView = (TextView) view.findViewById(R.id.summary);

            Item item = itemCursor.getItem();
            iconView.setImageResource(item.isUnread()
                ? R.drawable.item_unread: R.drawable.item_read);
            titleView.setText(item.getTitle());
            summaryView.setText(item.getSummary());

            view.setTag(item.getId());
        }

        @Override
        public void onContentChanged() {
            super.onContentChanged();
            ItemListActivity.this.bindSubTitleView(true);
        }
    }
}
