package cz.mamstylcendy.cdautologin;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.os.Build;

import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;

/**
 * Before Android 11, Android does not show a "logged in" notification after passing a captive
 * portal. This class brings this behavior to these versions, while on Android 11+, it is simply
 * a no-op.
 */
public class NetworkStackNotifierCompat {

    private static final String CHANNEL_ID = "CDWALNotifyChannel";
    private static final int NOTIFICATION_ID = 1;

    private static final boolean ENABLED = Build.VERSION.SDK_INT < Build.VERSION_CODES.R;

    private final Context mContext;

    public NetworkStackNotifierCompat(Context context) {
        mContext = context;
    }

    public static boolean isUsed() {
        return ENABLED;
    }

    public void showLoggedInNotification() {
        if (ENABLED) {
            showConnectionStateNotification(R.string.notification_login_success);
        }
    }

    public void showLoginFailedNotification() {
        if (ENABLED) {
            showConnectionStateNotification(R.string.notification_login_failed);
        }
    }

    private void showConnectionStateNotification(@StringRes int textId) {
        Intent notIntent = new Intent(mContext, MainActivity.class);
        notIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        showNotification(
                mContext.getString(R.string.notification_login_title),
                mContext.getString(textId),
                notIntent,
                0,
                NOTIFICATION_ID
        );
    }

    private void showNotification(String title, String message, Intent intent, int reqCode, int uniqueId) {
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, reqCode, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_MUTABLE);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(mContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setContentIntent(pendingIntent);
        NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, mContext.getString(R.string.app_name), importance);
            notificationManager.createNotificationChannel(channel);
        }
        notificationManager.notify(uniqueId, notificationBuilder.build());
    }
}
