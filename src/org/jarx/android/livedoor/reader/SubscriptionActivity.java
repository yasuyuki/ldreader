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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.ArrayAdapter; 
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

public class SubscriptionActivity extends ListActivity {

    public static final String EXTRA_SUBSCRIPTION_ID = "subscriptionId";

    private static final String TAG = "SubscriptionActivity";
    private static final int REQUEST_PREFERENCES = 1;
    private static final int DIALOG_SUBS_VIEW = 1;
    private static final int DIALOG_SUBS_SORT = 2;
    private final Handler handler = new Handler();
    private SubsAdapter subsAdapter;
    private ReaderService readerService;

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

        Intent intent = new Intent(this, ReaderService.class);
        startService(intent);
        bindService(intent, this.serviceConn, Context.BIND_AUTO_CREATE);

        registerReceiver(this.refreshReceiver,
            new IntentFilter(ReaderService.ACTION_SYNC_SUBS_FINISHED));

        bindTitle(this);
        initListAdapter();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (this.subsAdapter != null && !this.subsAdapter.cursor.isClosed()) {
            this.subsAdapter.cursor.deactivate();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(this.serviceConn);
        unregisterReceiver(this.refreshReceiver);
        if (this.subsAdapter != null && !this.subsAdapter.cursor.isClosed()) {
            this.subsAdapter.cursor.close();
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

    private void initListAdapter() {
        if (this.subsAdapter != null && !this.subsAdapter.cursor.isClosed()) {
            this.subsAdapter.cursor.close();
        }
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
        this.subsAdapter = new SubsAdapter(this, managedQuery(
            Subscription.CONTENT_URI, null, where, null, orderby));
        setListAdapter(this.subsAdapter);

        View message = findViewById(R.id.message);
        if (this.subsAdapter.cursor.getCount() == 0) {
            message.setVisibility(View.VISIBLE);
        } else {
            message.setVisibility(View.INVISIBLE);
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

    private class SubsAdapter extends BaseAdapter {

        private Subscription.FilterCursor cursor;
        private LayoutInflater inflater;

        public SubsAdapter(Context context, Cursor cursor) {
            this.cursor = new Subscription.FilterCursor(cursor);
            this.inflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            return this.cursor.getCount();
        }

        public Object getItem(int position) {
            return position;
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View view;
            if (convertView == null) {
                view = this.inflater.inflate(R.layout.subscription_row, parent, false);
            } else {
                view = convertView;
            }
            ImageView iconView = (ImageView) view.findViewById(R.id.icon);
            TextView titleView = (TextView) view.findViewById(R.id.title);
            RatingBar ratingBar = (RatingBar) view.findViewById(R.id.rating_bar);
            TextView etcView = (TextView) view.findViewById(R.id.etc);

            if (this.cursor.moveToPosition(position)) {
                Subscription sub = this.cursor.getSubscription();
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
            } else {
                titleView.setText("(Subscription Error)");
                view.setTag(null);
            }
            return view;
        }
    }
}
