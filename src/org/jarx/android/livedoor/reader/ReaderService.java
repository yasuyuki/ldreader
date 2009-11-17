package org.jarx.android.livedoor.reader;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Timer;
import java.util.TimerTask;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class ReaderService extends Service {

    public static final String ACTION_SYNC_SUBS_FINISHED
        = "ReaderService.action.syncSubsFinished";
    private static final String TAG = "ReaderService";

    class ReaderBinder extends Binder {

        ReaderService getService() {
            return ReaderService.this;
        }

        ReaderManager getManager() {
            return ReaderService.this.getSharedReaderManager();
        }
    }

    private ReaderManager rman;
    private NotificationManager nman;
    private Timer timer;
    private boolean syncRunning;
    private boolean started;
    private MessageFormat syncFinishedFormat;

    @Override
    public void onCreate() {
        super.onCreate();

        this.nman = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        this.syncFinishedFormat = new MessageFormat(
            getText(R.string.msg_sync_finished).toString());

        long interval = ReaderPreferences.getSyncInterval(getApplicationContext());
        if (interval > 0) {
            startSyncTimer(500, interval);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cancelSyncTimer();
    }

    @Override
    public IBinder onBind(Intent intent) {
        this.rman = new ReaderManager(getApplicationContext());
        return new ReaderBinder();
    }

    @Override
    public void onRebind(Intent intent) {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        ReaderManager rm = this.rman;
        if (rm != null) {
            try {
                rm.logout();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.rman = null;
        return true;
    }

    public ReaderManager getSharedReaderManager() {
        ReaderManager rm = this.rman;
        if (rm != null) {
            return rm;
        }
        return new ReaderManager(getApplicationContext());
    }

    public boolean startSync() {
        long interval = ReaderPreferences.getSyncInterval(getApplicationContext());
        return startSyncTimer(0, interval);
    }

    public synchronized boolean startSyncTimer(long delay, long interval) {
        Log.d(TAG, "startSyncTimer(" + delay + ", " + interval + ")");
        if (this.syncRunning) {
            Log.d(TAG, "  syncRunning");
            return false;
        }
        if (this.timer != null) {
            this.timer.cancel();
        }
        this.timer = new Timer();

        TimerTask timerTask = new TimerTask() {
            public void run() {
                Context context = getApplicationContext();
                ReaderManager rm = ReaderManager.newInstance(context);
                ReaderService.this.setSyncRunning(true);
                try {
                    if (rm.login()) {
                        ReaderService.this.notifySyncStarted();
                        int syncCount = rm.sync();
                        ReaderService.this.notifySyncFinished(syncCount);
                        rm.logout();
                    }
                } catch (IOException e) {
                    ReaderService.this.notifySyncError(e);
                } catch (ReaderException e) {
                    ReaderService.this.notifySyncError(e);
                } catch (Throwable e) {
                    ReaderService.this.notifySyncError(e);
                } finally {
                    ReaderService.this.setSyncRunning(false);
                }
            }
        };
        if (interval == 0) {
            this.timer.schedule(timerTask, delay);
        } else {
            this.timer.schedule(timerTask, delay, interval);
        }
        return true;
    }

    public synchronized void cancelSyncTimer() {
        if (this.timer != null) {
            this.timer.cancel();
            this.timer = null;
        }
    }

    private void setSyncRunning(boolean syncRunning) {
        this.syncRunning = syncRunning;
    }

    private void notifySyncStarted() {
        Log.d(TAG, "notifySyncStarted");
        sendNotify(android.R.drawable.stat_notify_sync,
            getText(R.string.msg_sync_started));
    }

    private void notifySyncFinished(int syncCount) {
        Log.d(TAG, "notifySyncFinished(" + syncCount + ")");

        Context context = getApplicationContext();
        ReaderManager rm = ReaderManager.newInstance(context);
        int unreadCount = rm.countUnread();
        String msg = this.syncFinishedFormat.format(new Integer[]{syncCount, unreadCount});
        sendNotify(android.R.drawable.stat_notify_sync, msg);

        context.sendBroadcast(new Intent(ACTION_SYNC_SUBS_FINISHED));
    }

    private void notifySyncError(IOException e) {
        e.printStackTrace();
        sendNotify(R.drawable.stat_notify_sync_error,
            getText(R.string.err_io) + "(" + e.getLocalizedMessage() + ")");
    }

    private void notifySyncError(Throwable e) {
        e.printStackTrace();
        sendNotify(R.drawable.stat_notify_sync_error, e.getLocalizedMessage());
    }

    private void sendNotify(int icon, CharSequence message) {
        CharSequence title = getText(R.string.app_name);
        Notification notification = new Notification(
            icon, message, System.currentTimeMillis());
        PendingIntent intent = PendingIntent.getActivity(this, 0,
                new Intent(this, SubscriptionActivity.class), 0);
        notification.setLatestEventInfo(this, title, message, intent);
        this.nman.notify(R.layout.subscription, notification);
    }
}
