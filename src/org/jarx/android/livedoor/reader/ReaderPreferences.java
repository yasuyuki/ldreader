package org.jarx.android.livedoor.reader;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class ReaderPreferences {

    public static final String PREFS_KEY_LOGIN_ID = "login_id";
    public static final String PREFS_KEY_PASSWORD = "password";
    public static final String PREFS_KEY_SUBS_VIEW = "subs_view";
    public static final String PREFS_KEY_SUBS_SORT = "subs_sort";
    public static final String PREFS_KEY_SYNC_INTERVAL_HOURS = "sync_interval_hours";
    public static final String PREFS_KEY_SYNC_UNREAD_ONLY = "sync_unread_only";
    public static final String PREFS_KEY_AUTO_TOUCH_ALL = "auto_touch_all";
    public static final String PREFS_KEY_VIEW_UNREAD_ONLY = "view_unread_only";

    public static final int SUBS_VIEW_FLAT = 1;
    public static final int SUBS_VIEW_FOLDER = 2;
    public static final int SUBS_VIEW_RATE = 3;
    public static final int SUBS_VIEW_SUBS = 4;

    public static final int SUBS_SORT_MODIFIED_DESC = 1;
    public static final int SUBS_SORT_MODIFIED_ASC = 2;
    public static final int SUBS_SORT_UNREAD_DESC = 3;
    public static final int SUBS_SORT_UNREAD_ASC = 4;
    public static final int SUBS_SORT_TITLE_ASC = 5;
    public static final int SUBS_SORT_RATE_DESC = 6;
    public static final int SUBS_SORT_SUBS_DESC = 7;
    public static final int SUBS_SORT_SUBS_ASC = 8;

    public static SharedPreferences getPreferences(Context c) {
        return PreferenceManager.getDefaultSharedPreferences(c);
    }

    public static String getString(Context c, String name) {
        return getPreferences(c).getString(name, null);
    }

    public static int getInt(Context c, String name, int def) {
        return getPreferences(c).getInt(name, def);
    }

    public static long getLong(Context c, String name, long def) {
        return getPreferences(c).getLong(name, def);
    }

    public static boolean getBoolean(Context c, String name, boolean def) {
        return getPreferences(c).getBoolean(name, def);
    }

    public static String getLoginId(Context c) {
        return getString(c, PREFS_KEY_LOGIN_ID);
    }

    public static String getPassword(Context c) {
        return getString(c, PREFS_KEY_PASSWORD);
    }

    public static void setLoginIdPassword(Context c, String loginId,
            String password) {
        SharedPreferences sp = getPreferences(c);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(PREFS_KEY_LOGIN_ID, loginId);
        editor.putString(PREFS_KEY_PASSWORD, password);
        editor.commit();
    }

    public static int getSubsSort(Context c) {
        return getInt(c, PREFS_KEY_SUBS_SORT, SUBS_SORT_MODIFIED_DESC);
    }

    public static void setSubsSort(Context c, int subsSort) {
        SharedPreferences sp = getPreferences(c);
        SharedPreferences.Editor editor = sp.edit();
        editor.putInt(PREFS_KEY_SUBS_SORT, subsSort);
        editor.commit();
    }

    public static long getSyncInterval(Context c) {
        String h = getString(c, PREFS_KEY_SYNC_INTERVAL_HOURS);
        int hour = 3;
        if (h != null && h.length() != 0) {
            hour = Integer.parseInt(h);
        }
        return (hour * 60 * 60 * 1000);
    }

    public static boolean isSyncUnreadOnly(Context c) {
        return getBoolean(c, PREFS_KEY_SYNC_UNREAD_ONLY, true);
    }

    public static boolean isAutoTouchAll(Context c) {
        return getBoolean(c, PREFS_KEY_AUTO_TOUCH_ALL, false);
    }

    public static boolean isViewUnreadOnly(Context c) {
        return getBoolean(c, PREFS_KEY_VIEW_UNREAD_ONLY, false);
    }
}
