package org.jarx.android.livedoor.reader;

import java.io.IOException;
import android.app.Activity; 
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RatingBar;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import android.database.DataSetObserver;

public class SubscriptionActivity extends ListActivity {

    public static final String EXTRA_SUBSCRIPTION_ID = "subscriptionId";

    private static final String TAG = "SubscriptionActivity";
    private static final int REQUEST_PREFERENCES = 1;
    private static final int DIALOG_SUBS_VIEW = 1;
    private static final int DIALOG_SUBS_SORT = 2;

    private final Handler handler = new Handler();
    private SubsAdapter subsAdapter;
    private ReaderService readerService;
    private int lastPosition;

    private ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            ReaderService.ReaderBinder binder = (ReaderService.ReaderBinder) service;
            SubscriptionActivity.this.readerService = binder.getService();
        }
        @Override
        public void onServiceDisconnected(ComponentName className) {
            SubscriptionActivity.this.readerService = null;
        }
    };

    private BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            SubscriptionActivity.this.initListAdapter();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");

        Window w = getWindow();
        w.requestFeature(Window.FEATURE_LEFT_ICON);
        setContentView(R.layout.subscription);
        w.setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.icon_s);

        bindService(new Intent(this, ReaderService.class),
            this.serviceConn, Context.BIND_AUTO_CREATE);

        registerReceiver(this.refreshReceiver,
            new IntentFilter(ReaderService.ACTION_SYNC_SUBS_FINISHED));

        bindTitle(this);
        initListAdapter();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (this.subsAdapter != null
                && this.lastPosition < this.subsAdapter.getCount()) {
            setSelection(this.lastPosition);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (this.subsAdapter != null) {
            Cursor cursor = this.subsAdapter.getCursor();
            if (cursor != null && !cursor.isClosed()) {
                cursor.deactivate();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(this.serviceConn);
        unregisterReceiver(this.refreshReceiver);
        if (this.subsAdapter != null ) {
            this.subsAdapter.closeCursor();
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        final Context context = getApplicationContext();
        switch (id) {
        case DIALOG_SUBS_SORT:
            int defaultWhich = ReaderPreferences.getSubsSort(context) - 1;
            return new AlertDialog.Builder(SubscriptionActivity.this)
                .setIcon(R.drawable.alert_dialog_icon)
                .setTitle(R.string.dialog_subs_sort_title)
                .setSingleChoiceItems(R.array.dialog_subs_sort_items, defaultWhich,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            ReaderPreferences.setSubsSort(context, which + 1);
                            SubscriptionActivity.this.initListAdapter();
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
        inflater.inflate(R.menu.subscription, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_item_reload:
            startSync();
            initListAdapter();
            return true;
        case R.id.menu_item_subs_sort:
            showDialog(DIALOG_SUBS_SORT);
            return true;
        case R.id.menu_item_pin:
            startActivity(new Intent(this, PinActivity.class));
            return true;
        case R.id.menu_item_setting:
            startActivityForResult(new Intent(this, ReaderPreferenceActivity.class),
                REQUEST_PREFERENCES);
            return true;
        }
        return false;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Long subId = (Long) v.getTag();
        if (subId != null) {
            this.lastPosition = position;
            startActivity(new Intent(this, ItemActivity.class)
                .putExtra(EXTRA_SUBSCRIPTION_ID, subId));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case REQUEST_PREFERENCES:
            initListAdapter();
            break;
        }
    }

    private synchronized void initListAdapter() {
        this.lastPosition = 0;

        Context context = getApplicationContext();
        String where = null;
        if (ReaderPreferences.isViewUnreadOnly(context)) {
            where = Subscription._UNREAD_COUNT + " > 0";
        }
        int subsSort = ReaderPreferences.getSubsSort(context);
        if (subsSort < 1 || subsSort > Subscription.SORT_ORDERS.length) {
            subsSort = 1;
        }
        String orderby = Subscription.SORT_ORDERS[subsSort - 1];
        Cursor cursor = managedQuery(Subscription.CONTENT_URI,
            null, where, null, orderby);
        if (this.subsAdapter == null) {
            this.subsAdapter = new SubsAdapter(this, cursor);
            setListAdapter(this.subsAdapter);
        } else {
            this.subsAdapter.changeCursor(cursor);
        }
        bindMessageView();
    }

    private void bindMessageView() {
        View message = findViewById(R.id.message);
        if (this.subsAdapter == null || this.subsAdapter.getCount() == 0) {
            if (message.getVisibility() != View.VISIBLE) {
                message.setVisibility(View.VISIBLE);
            }
        } else {
            if (message.getVisibility() != View.INVISIBLE) {
                message.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void startSync() {
        if (this.readerService.startSync()) {
            showToast(getString(R.string.msg_sync_started));
        } else {
            showToast(getString(R.string.msg_sync_running));
        }
    }

    static void bindTitle(Activity a) {
        String loginId = ReaderPreferences.getLoginId(a.getApplicationContext());
        if (loginId != null) {
            StringBuilder buff = new StringBuilder(64);
            buff.append(a.getText(R.string.app_name));
            buff.append(" - ");
            buff.append(loginId);
            a.setTitle(new String(buff));
        }
    }

    private void showToast(IOException e) {
        e.printStackTrace();
        showToast(getText(R.string.err_io) + " (" + e.getLocalizedMessage() + ")");
    }

    private void showToast(ReaderException e) {
        e.printStackTrace();
        showToast(e.getLocalizedMessage());
    }

    private void showToast(final CharSequence text) {
        this.handler.post(new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private class SubsAdapter extends ResourceCursorAdapter {

        private SubsAdapter(Context context, Cursor cursor) {
            super(context, R.layout.subscription_row,
                new Subscription.FilterCursor(cursor));
        }

        private void closeCursor() {
            Cursor cursor = getCursor();
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }

        @Override
        public void changeCursor(Cursor cursor) {
            super.changeCursor(new Subscription.FilterCursor(cursor));
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            Subscription.FilterCursor subCursor = (Subscription.FilterCursor) cursor;

            ImageView iconView = (ImageView) view.findViewById(R.id.icon);
            TextView titleView = (TextView) view.findViewById(R.id.title);
            RatingBar ratingBar = (RatingBar) view.findViewById(R.id.rating_bar);
            TextView etcView = (TextView) view.findViewById(R.id.etc);

            Subscription sub = subCursor.getSubscription();
            titleView.setText(sub.getTitle() + " (" + sub.getUnreadCount() + ")");
            ratingBar.setRating(sub.getRate());
            iconView.setImageBitmap(sub.getIcon());

            StringBuilder buff = new StringBuilder(64);
            buff.append(sub.getSubscribersCount());
            buff.append(" users");
            String folder = sub.getFolder();
            if (folder != null && folder.length() > 0) {
                buff.append(" | ");
                buff.append(folder);
            }
            etcView.setText(new String(buff));

            view.setTag(sub.getId());
        }

        @Override
        protected void onContentChanged() {
            super.onContentChanged();
            SubscriptionActivity.this.bindMessageView();
        }
    }
}
