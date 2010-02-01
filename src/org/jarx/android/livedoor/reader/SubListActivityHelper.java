package org.jarx.android.livedoor.reader;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class SubListActivityHelper extends ActivityHelper {

    public static interface SubListable {
        Activity getActivity();
        void initListAdapter();
        ReaderService getReaderService();
        Handler getHandler();
    }

    static final int REQUEST_PREFERENCES = 1;
    static final int DIALOG_SUBS_VIEW = 1;
    static final int DIALOG_SUBS_SORT = 2;
    static final int DIALOG_TOUCH_ALL_LOCAL = 3;

    static Dialog onCreateDialog(final SubListable listable, int id) {
        final Activity activity = listable.getActivity();
        final Context context = activity.getApplicationContext();
        switch (id) {
        case DIALOG_SUBS_VIEW:
            int subsViewWhich = ReaderPreferences.getSubsView(context) - 1;
            return new AlertDialog.Builder(activity)
                .setIcon(R.drawable.alert_dialog_icon)
                .setTitle(R.string.dialog_subs_view_title)
                .setSingleChoiceItems(R.array.dialog_subs_view_items, subsViewWhich,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            ReaderPreferences.setSubsView(context, which + 1);
                            startSubListActivity(activity, context);
                            activity.finish();
                            dialog.dismiss();
                        }
                    }
                ).create();
        case DIALOG_SUBS_SORT:
            int defaultWhich = ReaderPreferences.getSubsSort(context) - 1;
            return new AlertDialog.Builder(activity)
                .setIcon(R.drawable.alert_dialog_icon)
                .setTitle(R.string.dialog_subs_sort_title)
                .setSingleChoiceItems(R.array.dialog_subs_sort_items, defaultWhich,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            ReaderPreferences.setSubsSort(context, which + 1);
                            listable.initListAdapter();
                            dialog.dismiss();
                        }
                    }
                ).create();
        case DIALOG_TOUCH_ALL_LOCAL:
            return new AlertDialog.Builder(activity)
                .setTitle(R.string.txt_reads)
                .setMessage(R.string.msg_confirm_touch_all_local)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        touchAllLocal(listable);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                }).create();
        }
        return null;
    }

    static void startSubListActivity(Activity activity) {
        startSubListActivity(activity, activity.getApplicationContext());
    }

    static void startSubListActivity(Activity activity, Context context) {
        int subsView = ReaderPreferences.getSubsView(context);
        switch (subsView) {
        case 1:
            if (!(activity instanceof SubListActivity)) {
                activity.startActivity(
                    new Intent(activity, SubListActivity.class));
            }
            break;
        default:
            activity.startActivity(new Intent(activity, GroupSubListActivity.class));
        }
    }

    static void touchAllLocal(final SubListable listable) {
        final Activity activity = listable.getActivity();
        final ProgressDialog dialog = new ProgressDialog(activity);
        dialog.setIndeterminate(true);
        dialog.setMessage(activity.getText(R.string.msg_running));
        dialog.show();
        new Thread() {
            public void run() {
                ContentResolver cr = activity.getContentResolver();
                ContentValues values = new ContentValues();

                values.put(Item._UNREAD, 0);
                cr.update(Item.CONTENT_URI, values, Item._UNREAD + " = 1", null);

                values.clear();
                values.put(Subscription._UNREAD_COUNT, 0);
                cr.update(Subscription.CONTENT_URI, values,
                    Subscription._UNREAD_COUNT + " <> 0", null);

                listable.getHandler().post(new Runnable() {
                    public void run() {
                        listable.initListAdapter();
                        dialog.dismiss();
                    }
                });
            }
        }.start();
    }

    static boolean onCreateOptionsMenu(SubListable listable, Menu menu) {
        MenuInflater inflater = listable.getActivity().getMenuInflater();
        inflater.inflate(R.menu.sub_list, menu);
        return true;
    }

    static boolean onOptionsItemSelected(SubListable listable, MenuItem item) {
        final Activity activity = listable.getActivity();
        final Context context = activity.getApplicationContext();
        switch (item.getItemId()) {
        case R.id.menu_item_reload:
            if (listable.getReaderService().startSync()) {
                showToast(context, activity.getText(R.string.msg_sync_started));
            } else {
                showToast(context, activity.getText(R.string.msg_sync_running));
            }
            listable.initListAdapter();
            return true;
        case R.id.menu_item_subs_view:
            activity.showDialog(DIALOG_SUBS_VIEW);
            return true;
        case R.id.menu_item_subs_sort:
            activity.showDialog(DIALOG_SUBS_SORT);
            return true;
        case R.id.menu_item_touch_all_local:
            activity.showDialog(DIALOG_TOUCH_ALL_LOCAL);
            return true;
        case R.id.menu_item_pin:
            activity.startActivity(new Intent(activity, PinActivity.class));
            return true;
        case R.id.menu_item_setting:
            activity.startActivityForResult(new Intent(activity,
                ReaderPreferenceActivity.class), REQUEST_PREFERENCES);
            return true;
        }
        return false;
    }

    static void onActivityResult(SubListable listable, int requestCode,
            int resultCode, Intent data) {
        switch (requestCode) {
        case REQUEST_PREFERENCES:
            listable.initListAdapter();
            break;
        }
    }
}
