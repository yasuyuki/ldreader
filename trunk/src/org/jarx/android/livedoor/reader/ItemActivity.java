package org.jarx.android.livedoor.reader;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class ItemActivity extends Activity {

    private static final String TAG = "ItemActivity";

    private Subscription sub;
    private Item currentItem;
    private Uri subUri;
    private Item.FilterCursor itemsCursor;
    private int itemsCount;
    private int itemsIndex;
    private boolean unreadOnly = true;
    private Set<Long> readItemIds = new HashSet<Long>();
    private ImageView pinView;
    private boolean pinOn;
    private ReaderService readerService;
    private ReaderManager readerManager;

    private ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            ReaderService.ReaderBinder binder = (ReaderService.ReaderBinder) service;
            ItemActivity.this.readerService = binder.getService();
            ItemActivity.this.readerManager = binder.getManager();
            if (ItemActivity.this.itemsCount == 0) {
                ItemActivity.this.progressSyncItems();
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName className) {
            ItemActivity.this.readerService = null;
            ItemActivity.this.readerManager = null;
        }
    };

    private final Handler handler = new Handler();
    private final Runnable hideTouchControlViewsRunner = new Runnable() {
        public void run() {
            hideTouchControlViews();
        }
    };

    private final Animation previousHideAnimation = new AlphaAnimation(1F, 0F);
    private final Animation nextHideAnimation = new AlphaAnimation(1F, 0F);
    private final Animation zoomControllsHideAnimation = new AlphaAnimation(1F, 0F);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        bindService(new Intent(this, ReaderService.class), this.serviceConn,
            Context.BIND_AUTO_CREATE);

        Window w = getWindow();
        w.requestFeature(Window.FEATURE_LEFT_ICON);
        setContentView(R.layout.item);
        w.setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.icon_s);

        SubListActivity.bindTitle(this);

        Intent intent = getIntent();
        long subId = intent.getLongExtra(SubListActivity.EXTRA_SUBSCRIPTION_ID, 0);

        this.subUri = ContentUris.withAppendedId(Subscription.CONTENT_URI, subId);
        Cursor cursor = managedQuery(subUri, null, null, null, null);
        cursor.moveToFirst();
        this.sub = new Subscription.FilterCursor(cursor).getSubscription();
        cursor.close();

        ImageView iconView = (ImageView) findViewById(R.id.sub_icon);
        iconView.setImageBitmap(sub.getIcon());

        final WebView bodyView = (WebView) findViewById(R.id.item_body);
        bodyView.setOnTouchListener(new BodyWebViewTouchListener());
        bodyView.setWebViewClient(new BodyWebViewClient());
        WebSettings settings = bodyView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setBuiltInZoomControls(false);
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);

        final View previous = findViewById(R.id.previous);
        previous.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ItemActivity.this.previousItem();
            }
        });
        final View next = findViewById(R.id.next);
        next.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ItemActivity.this.nextItem();
            }
        });
        final View zoomIn = findViewById(R.id.zoom_in);
        zoomIn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                bodyView.zoomIn();
            }
        });
        final View zoomOut = findViewById(R.id.zoom_out);
        zoomOut.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                bodyView.zoomOut();
            }
        });
        this.pinView = (ImageView) findViewById(R.id.pin);
        this.pinView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                progressPin();
            }
        });

        initItems();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.item, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_item_reload:
            progressSyncItems();
            return true;
        case R.id.menu_item_touch_feed_local:
            destroyItems(true);
            initItems();
            showToast(getText(R.string.msg_touch_feed_local));
            return true;
        case R.id.menu_item_move_to_last_read:
            initItems(true);
            return true;
        case R.id.menu_item_pin:
            progressPin();
            return true;
        case R.id.menu_item_pin_list:
            startActivity(new Intent(this, PinActivity.class));
            return true;
        case R.id.menu_item_setting:
            startActivity(new Intent(this, ReaderPreferenceActivity.class));
            return true;
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        final WebView bodyView = (WebView) findViewById(R.id.item_body);
        WebSettings settings = bodyView.getSettings();
        settings.setDefaultFontSize(ReaderPreferences.getItemBodyFontSize(
            getApplicationContext()));
    }

    @Override
    public void onPause() {
        super.onPause();
        saveReadItemId();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        destroyItems(false);
        unbindService(this.serviceConn);
    }

    private void progressSyncItems() {
        final long subId = this.sub.getId();
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setIndeterminate(true);
        dialog.setMessage(getText(R.string.msg_sync_running));
        dialog.show();
        new Thread() {
            public void run() {
                ReaderManager rm = ItemActivity.this.readerManager;
                try {
                    rm.syncItems(subId, false);
                } catch (IOException e) {
                    showToast(e);
                } catch (Throwable e) {
                    showToast(e);
                }
                handler.post(new Runnable() {
                    public void run() {
                        ItemActivity.this.initItems();
                        dialog.dismiss();
                    }
                });
            }
        }.start();
    }

    private void progressPin() {
        final Item item = this.currentItem;
        if (item == null) {
            return;
        }
        final boolean add = !this.pinOn;
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setIndeterminate(true);
        if (add) {
            dialog.setMessage(getText(R.string.msg_pin_add_running));
        } else {
            dialog.setMessage(getText(R.string.msg_pin_remove_running));
        }
        dialog.show();
        new Thread() {
            public void run() {
                ReaderManager rm = ItemActivity.this.readerManager;
                try {
                    if (add) {
                        rm.pinAdd(item.getUri(), item.getTitle(), true);
                    } else {
                        rm.pinRemove(item.getUri(), true);
                    }
                } catch (Throwable e) {
                    // NOTE: ignore
                    // showToast(e);
                }
                handler.post(new Runnable() {
                    public void run() {
                        ItemActivity.this.bindPinView();
                        dialog.dismiss();
                    }
                });
            }
        }.start();
    }

    private void initItems() {
        initItems(false);
    }

    private void initItems(boolean moveToLastItem) {
        if (this.itemsCursor != null && !this.itemsCursor.isClosed()) {
            this.itemsCursor.close();
        }

        long lastItemId = this.sub.getReadItemId();
        if (moveToLastItem && lastItemId > 0) {
            Uri itemUri = ContentUris.withAppendedId(Item.CONTENT_URI, lastItemId);
            Cursor c = managedQuery(itemUri, null, null, null, null);
            if (c.moveToFirst()) {
                Item lastItem = new Item.FilterCursor(c).getItem();
                this.unreadOnly = lastItem.isUnread();
            } else {
                lastItemId = 0;
            }
            c.close();
        }

        String orderby = Item._ID + " desc";
        String whereBase = Item._SUBSCRIPTION_ID + " = " + this.sub.getId();
        StringBuilder where = new StringBuilder(whereBase);
        if (this.unreadOnly) {
            where.append(" and ");
            where.append(Item._UNREAD).append(" = 1");
        }

        this.itemsIndex = 0;
        this.itemsCursor = new Item.FilterCursor(managedQuery(
            Item.CONTENT_URI, null, new String(where), null, orderby));
        this.itemsCount = this.itemsCursor.getCount();

        if (this.itemsCount == 0 && this.unreadOnly) {
            where.setLength(whereBase.length());
            this.itemsCursor.close();
            this.itemsCursor = new Item.FilterCursor(managedQuery(
                Item.CONTENT_URI, null, new String(where), null, orderby));
            this.itemsCount = this.itemsCursor.getCount();
            this.unreadOnly = false;
        }

        if (moveToLastItem && lastItemId > 0 && this.itemsCount > 0) {
            where.append(" and ");
            where.append(Item._ID).append(" > ").append(lastItemId);
            Cursor c = managedQuery(Item.CONTENT_URI, null,
                new String(where), null, null);
            this.itemsIndex = c.getCount();
            c.close();
        }

        bindSubTitleView();
        bindItemView(ReaderPreferences.isShowItemControlls(
            getApplicationContext()));
    }

    private void destroyItems(boolean touchAllLocal) {
        if (this.itemsCursor != null && !this.itemsCursor.isClosed()) {
            this.itemsCursor.close();
        }
        this.itemsCursor = null;
        this.itemsCount = 0;
        this.itemsIndex = 0;

        if (!touchAllLocal && this.readItemIds.size() == 0) {
            return;
        }

        int buffSize = 128 + (this.readItemIds.size() * 3);
        StringBuilder where = new StringBuilder(buffSize);
        where.append(Item._UNREAD + " = 1");
        where.append(" and ");
        where.append(Item._SUBSCRIPTION_ID + " = " + this.sub.getId());
        if (!touchAllLocal) {
            where.append(" and ");
            where.append(Item._ID + " in (");
            boolean first = true;
            for (long itemId: this.readItemIds) {
                if (first) {
                    first = false;
                } else {
                    where.append(",");
                }
                where.append(itemId);
            }
            where.append(")");
        }
        if (!isFinishing()) {
            where.append(" and ");
            where.append(Item._ID + " <> " + this.sub.getReadItemId());
        }

        ContentValues values = new ContentValues();
        values.put(Item._UNREAD, 0);

        ContentResolver cr = getContentResolver();
        if (cr.update(Item.CONTENT_URI, values, new String(where), null) > 0) {
            where.setLength(0);
            where.append(Item._SUBSCRIPTION_ID + " = ").append(this.sub.getId());
            where.append(" and ");
            where.append(Item._UNREAD + " = 1");
            Cursor cursor = cr.query(Item.CONTENT_URI, null, new String(where),
                null, null);
            int unreadCount = cursor.getCount();
            cursor.close();

            ContentValues subValues = new ContentValues();
            subValues.put(Subscription._UNREAD_COUNT, unreadCount);
            cr.update(this.subUri, subValues, null, null);

            getApplicationContext().sendBroadcast(
                new Intent(ReaderService.ACTION_UNREAD_MODIFIED));
        }
        this.readItemIds.clear();
    }

    private void saveReadItemId() {
        if (this.currentItem == null) {
            return;
        }
        this.sub.setReadItemId(this.currentItem.getId());
        ContentResolver cr = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(Subscription._READ_ITEM_ID, this.sub.getReadItemId());
        cr.update(this.subUri, values, null, null);
    }

    private void nextItem() {
        scheduleHideTouchControlViews();
        if (this.itemsCount > 0) {
            if (++this.itemsIndex >= this.itemsCount) {
                this.itemsIndex--;
            }
            bindSubTitleView();
            bindItemView(true);
        }
    }

    private void previousItem() {
        scheduleHideTouchControlViews();
        if (this.itemsCount > 0) {
            if (--this.itemsIndex < 0) {
                this.itemsIndex = 0;
            }
            bindSubTitleView();
            bindItemView(true);
        }
    }

    private void bindTouchControlViews(boolean visible) {
        if (ReaderPreferences.isShowItemControlls(
                getApplicationContext())) {
            visible = true;
        }

        boolean hasPrevious = (this.itemsIndex > 0);
        boolean hasNext = (this.itemsIndex < (this.itemsCount - 1));

        final View previous = findViewById(R.id.previous);
        final View next = findViewById(R.id.next);
        final View zoomControlls = findViewById(R.id.zoom_controlls);

        boolean previousVisible = (previous.getVisibility() == View.VISIBLE);
        boolean nextVisible = (next.getVisibility() == View.VISIBLE);

        if (hasPrevious && visible && !previousVisible) {
            previous.setVisibility(View.VISIBLE);
        } else if (!hasPrevious && previousVisible) {
            previous.setVisibility(View.INVISIBLE);
        }

        if (hasNext && visible && !nextVisible) {
            next.setVisibility(View.VISIBLE);
        } else if (!hasNext && nextVisible) {
            next.setVisibility(View.INVISIBLE);
        }

        if (zoomControlls.getVisibility() != View.VISIBLE) {
            zoomControlls.setVisibility(View.VISIBLE);
        }
    }

    private void hideTouchControlViews() {
        if (ReaderPreferences.isShowItemControlls(
                getApplicationContext())) {
            return;
        }

        final View previous = findViewById(R.id.previous);
        final View next = findViewById(R.id.next);
        final View zoomControlls = findViewById(R.id.zoom_controlls);

        if (previous.getVisibility() == View.VISIBLE) {
            Animation a = this.previousHideAnimation;
            a.setDuration(500);
            previous.startAnimation(a);
            previous.setVisibility(View.INVISIBLE);
        }

        if (next.getVisibility() == View.VISIBLE) {
            Animation a = this.nextHideAnimation;
            a.setDuration(500);
            next.startAnimation(a);
            next.setVisibility(View.INVISIBLE);
        }

        if (zoomControlls.getVisibility() == View.VISIBLE) {
            Animation a = this.zoomControllsHideAnimation;
            a.setDuration(500);
            zoomControlls.startAnimation(a);
            zoomControlls.setVisibility(View.INVISIBLE);
        }
    }

    private void scheduleHideTouchControlViews() {
        this.handler.removeCallbacks(hideTouchControlViewsRunner);
        this.handler.postDelayed(hideTouchControlViewsRunner, 2000);
    }

    private void bindSubTitleView() {
        TextView subTitleView = (TextView) findViewById(R.id.sub_title);
        StringBuilder buff = new StringBuilder(64);
        buff.append(this.sub.getTitle());
        if (this.itemsCount > 0) {
            buff.append(" (");
            if (this.unreadOnly) {
                buff.append(getText(R.string.txt_unreads));
            } else {
                buff.append(getText(R.string.txt_reads));
            }
            buff.append(":");
            buff.append(this.itemsIndex + 1);
            buff.append("/");
            buff.append(this.itemsCount);
            buff.append(")");
        }
        subTitleView.setText(new String(buff));
    }

    private void bindItemView(boolean bindTouchControlViews) {
        TextView titleView = (TextView) findViewById(R.id.item_title);
        WebView bodyView = (WebView) findViewById(R.id.item_body);
        if (this.itemsCount > 0
                && this.itemsCursor.moveToPosition(this.itemsIndex)) {
            Item item = this.itemsCursor.getItem();
            titleView.setText(item.getTitle());
            bodyView.loadDataWithBaseURL(ApiClient.URL_READER,
                createBodyHtml(item), "text/html", "UTF-8", "about:blank");
            if (bindTouchControlViews) {
                bindTouchControlViews(true);
            }
            this.currentItem = item;
            this.readItemIds.add(item.getId());
        } else {
            titleView.setText(getText(R.string.msg_no_item_for_title));
            bodyView.clearView();
        }
        bindPinView();
    }

    private boolean pinExists() {
        if (this.currentItem == null) {
            return false;
        }
        Cursor cursor = managedQuery(Pin.CONTENT_URI, null,
            Pin._URI + " = ? and " + Pin._ACTION + " <> " + Pin.ACTION_REMOVE,
            new String[]{this.currentItem.getUri()}, null);
        try {
            return (cursor.getCount() > 0);
        } finally {
            cursor.close();
        }
    }

    private void bindPinView() {
        if (pinExists()) {
            this.pinView.setImageResource(R.drawable.pin_on);
            this.pinOn = true;
        } else {
            this.pinView.setImageResource(R.drawable.pin_off);
            this.pinOn = false;
        }
    }

    private String createBodyHtml(Item item) {
        String body = item.getBody();
        if (body == null) {
            body = "";
        }
        long time = item.getCreatedOrModifiedTime();
        String author = item.getAuthor();
        StringBuilder buff = new StringBuilder(body.length() + 256);
        buff.append("<div class=\"item_info\">");
        buff.append("<a href=\"");
        buff.append(item.getUri());
        buff.append("\">Permalink</a>");
        if (time > 0 || (author != null && author.length() > 0)) {
            buff.append(" |");
            if (time > 0) {
                buff.append(" <small class=\"rel\">");
                buff.append(Utils.formatTimeAgo(time));
                buff.append("</small>");
            }
            if (author != null && author.length() > 0) {
                buff.append(" <small class=\"author\">by ");
                buff.append(Utils.htmlEscape(item.getAuthor()));
                buff.append("</small>");
            }
        }
        buff.append("</div>");
        buff.append("<div class=\"item_body\" style=\"margin:8px 0 50px 0; word-wrap:break-word; overflow:auto;\">");
        buff.append(body);
        buff.append("</div>");
        return new String(buff);
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

    private class BodyWebViewTouchListener implements View.OnTouchListener {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                bindTouchControlViews(true);
                break;
            case MotionEvent.ACTION_UP:
                scheduleHideTouchControlViews();
                break;
            case MotionEvent.ACTION_MOVE:
                break;
            }
            return false;
        }
    }

    private class BodyWebViewClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, final String url) {
            if (ReaderPreferences.isDisableItemLinks(getApplicationContext())) {
                return true;
            }
            new AlertDialog.Builder(ItemActivity.this)
                .setTitle(R.string.msg_confirm_browse)
                .setMessage(url)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                }).show();
            return true;
        }
    }
}
