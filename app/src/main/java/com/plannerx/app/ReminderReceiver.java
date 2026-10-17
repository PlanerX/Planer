package com.plannerx.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Planner X reminders.
 * - JS (index.html) sends the list of upcoming reminders through PlannerBridge.syncReminders().
 * - schedule() saves that list and sets one Android alarm per reminder.
 * - When an alarm fires, onReceive() shows a notification.
 * - BootReceiver sets the alarms again after the phone restarts or the app is updated.
 */
public class ReminderReceiver extends BroadcastReceiver {

    static final String CHANNEL_ID = "planner_x_reminders";
    static final String ACTION_FIRE = "com.plannerx.app.REMINDER_FIRE";
    private static final String PREFS = "planner_x_reminders";
    private static final String KEY_LIST = "list";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (ACTION_FIRE.equals(intent.getAction())) {
            String title = intent.getStringExtra("title");
            String body = intent.getStringExtra("body");
            int nid = intent.getIntExtra("nid", 1);
            show(context, title == null ? "Planner X" : title, body == null ? "" : body, nid);
        }
    }

    /** Runs after phone restart / app update and sets the saved alarms again. */
    public static class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                    || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
                rescheduleSaved(context);
            }
        }
    }

    static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID, "Planner reminders", NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription("Task reminders from Planner X");
                channel.enableVibration(true);
                nm.createNotificationChannel(channel);
            }
        }
    }

    static void show(Context context, String title, String body, int id) {
        try {
            ensureChannel(context);
            NotificationManager nm =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            Intent open = new Intent(context, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent content = PendingIntent.getActivity(
                    context, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= 26) {
                builder = new Notification.Builder(context, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(context);
            }
            builder.setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setContentIntent(content)
                    .setAutoCancel(true)
                    .setWhen(System.currentTimeMillis());
            if (Build.VERSION.SDK_INT < 26) {
                builder.setPriority(Notification.PRIORITY_HIGH);
                builder.setDefaults(Notification.DEFAULT_ALL);
            }
            builder.setCategory(Notification.CATEGORY_REMINDER);
            try {
                Bitmap large = BitmapFactory.decodeResource(
                        context.getResources(), context.getApplicationInfo().icon);
                if (large != null) builder.setLargeIcon(large);
            } catch (Throwable ignored) {
            }
            nm.notify(id, builder.build());
        } catch (Throwable ignored) {
        }
    }

    /** Called from JS: saves the new list and sets the alarms. */
    static void schedule(Context context, String json) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        cancelAll(context, sp.getString(KEY_LIST, "[]"));
        if (json == null || json.length() == 0) json = "[]";
        sp.edit().putString(KEY_LIST, json).apply();
        setAll(context, json);
    }

    static void rescheduleSaved(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        setAll(context, sp.getString(KEY_LIST, "[]"));
    }

    private static String keyOf(JSONObject o, int index) {
        return o.optString("k", "k" + index);
    }

    private static PendingIntent buildPending(Context context, String key,
                                              String title, String body, int flag) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_FIRE);
        int code = key.hashCode();
        if (title != null) {
            intent.putExtra("title", title);
            intent.putExtra("body", body);
            intent.putExtra("nid", code);
        }
        return PendingIntent.getBroadcast(context, code, intent, flag | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void cancelAll(Context context, String json) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        try {
            JSONArray list = new JSONArray(json);
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.getJSONObject(i);
                PendingIntent pi = buildPending(context, keyOf(o, i), null, null,
                        PendingIntent.FLAG_NO_CREATE);
                if (pi != null) {
                    am.cancel(pi);
                    pi.cancel();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void setAll(Context context, String json) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long now = System.currentTimeMillis();
        try {
            JSONArray list = new JSONArray(json);
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.getJSONObject(i);
                long at = o.optLong("at", 0L);
                if (at <= now) continue;
                PendingIntent pi = buildPending(context, keyOf(o, i),
                        o.optString("title", "Planner X"), o.optString("body", ""),
                        PendingIntent.FLAG_UPDATE_CURRENT);
                setAlarm(am, at, pi);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void setAlarm(AlarmManager am, long at, PendingIntent pi) {
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (Throwable t) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } catch (Throwable ignored) {
            }
        }
    }
  }
