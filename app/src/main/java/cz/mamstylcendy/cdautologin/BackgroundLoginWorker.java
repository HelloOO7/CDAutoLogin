package cz.mamstylcendy.cdautologin;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ForegroundInfo;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class BackgroundLoginWorker extends Worker {

    private static final String TAG = "BGWorker";

    private static final String WORK_TAG = BackgroundLoginWorker.class.getName();

    private static final String DATA_PROGRESS = "progress";
    private static final String DATA_ERROR_CODE = "error_code";

    private static final String SERVICE_NOTIFICATION_CHANNEL_ID = "CDWALForegroundService";
    private static final int SERVICE_NOTIFICATION_ID = 2;

    private final ConnectivityManager connectivityManager;

    private Notification foregroundNotification;

    public BackgroundLoginWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (needsForegroundNotification()) {
            setupForegroundServiceNotification(context);
        }
    }

    public static boolean needsForegroundNotification() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S;
    }

    private void setupForegroundServiceNotification(Context context) {
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, SERVICE_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(context.getString(R.string.foreground_service_notification_title))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);

        foregroundNotification = notification.build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);

            NotificationChannel channel = new NotificationChannel(
                    SERVICE_NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.foreground_service_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setShowBadge(false);

            notificationManager.createNotificationChannel(channel);
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "Begin CDW login.");
        updateProgress(ProgressCode.DETECT);
        forceUseWlan();
        try {
            Context context = getApplicationContext();
            CDWiFiLoginImpl cdwifi = new CDWiFiLoginImpl(context);
            CaptivePortalInfo captiveInfo;
            try {
                captiveInfo = cdwifi.detectCaptivePortal();
            } catch (IOException ex) {
                logError(ex);
                return exitWithError(ErrorCode.CAPTIVE_DETECTION_FAILED, ex);
            }
            if (captiveInfo == CaptivePortalInfo.failure()) {
                return exitWithError(ErrorCode.CAPTIVE_DETECTION_FAILED);
            }
            Log.i(TAG, "CDW login captive result " + captiveInfo.type);
            if (captiveInfo.type.isCdWifi()) {
                updateProgress(ProgressCode.LOGIN);
                boolean loginSuccess;
                try {
                    Log.d(TAG, "Logging in to captive portal of type " + captiveInfo.type);
                    loginSuccess = cdwifi.cdWifiLogin(captiveInfo);
                } catch (IOException ex) {
                    logError(ex);
                    return exitWithError(ErrorCode.CAPTIVE_LOGIN_FAILED, ex);
                }

                Log.i(TAG, "LoginSuccess: " + loginSuccess);
                if (!isAppInForeground()) {
                    NetworkStackNotifierCompat notifier = new NetworkStackNotifierCompat(context);
                    if (loginSuccess) {
                        notifier.showLoggedInNotification();
                    } else {
                        notifier.showLoginFailedNotification();
                    }
                }

                if (loginSuccess) {
                    return exitSuccess();
                } else {
                    return exitWithError(ErrorCode.CAPTIVE_LOGIN_FAILED);
                }
            } else {
                if (captiveInfo.type == CDCaptiveType.NONE) {
                    return exitWithError(ErrorCode.NO_CAPTIVE_PORTAL);
                } else {
                    return exitWithError(ErrorCode.CAPTIVE_NOT_CD_WIFI);
                }
            }
        } catch (Throwable th) {
            logError(th);
        }
        return exitWithError(ErrorCode.UNKNOWN);
    }

    @NonNull
    @Override
    public ForegroundInfo getForegroundInfo() {
        return new ForegroundInfo(SERVICE_NOTIFICATION_ID, foregroundNotification);
    }

    private void forceUseWlan() {
        //if the wifi has a captive portal, a cellullar data connection may override the connectivity,
        //making it appear as though we have internet access, when, in fact, we only have it over cell
        Network captiveWlan = getCaptiveWlanNetwork();
        if (captiveWlan != null) {
            Log.i(TAG, "Forcing use of captive WLAN network: " + captiveWlan);
            connectivityManager.bindProcessToNetwork(captiveWlan);
        } else {
            Log.w(TAG, "No captive WLAN network found, process will use default connectivity.");
            connectivityManager.bindProcessToNetwork(null);
        }
    }

    @SuppressWarnings("deprecation")
    private Network getCaptiveWlanNetwork() {
        //getAllNetworks is deprecated without a good alternative, as network callbacks
        //are unreliable and could cause the worker to stall infinitely, as they are asynchronous
        //until the method is terminally deprecated or removed in newer versions, it is what we'll use
        for (Network net : connectivityManager.getAllNetworks()) {
            NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(net);
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) {
                return net;
            }
        }
        return null;
    }

    private void wakeUpConnectivityCheck() {
        connectivityManager.reportNetworkConnectivity(ConnectivityManagerCompat.getBoundOrActiveNetwork(connectivityManager), true);
    }

    private static boolean isAppInForeground() {
        ActivityManager.RunningAppProcessInfo state = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(state);
        return state.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND || state.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
    }

    private void resetWlanOverride() {
        connectivityManager.bindProcessToNetwork(null);
    }

    private Result exitSuccess() {
        wakeUpConnectivityCheck();
        resetWlanOverride();
        updateProgress(ProgressCode.DONE);
        rescheduleOnSuccess();
        return Result.success(new Data.Builder()
                .putString(DATA_PROGRESS, ProgressCode.DONE.name())
                .build());
    }

    private Result exitWithError(ErrorCode code) {
        return exitWithError(code, null);
    }

    private Result exitWithError(ErrorCode code, Throwable th) {
        resetWlanOverride();
        updateProgress(ProgressCode.DONE);
        rescheduleOnFailure(getFailureRescheduleTime(th));
        return Result.failure(new Data.Builder()
                .putString(DATA_ERROR_CODE, code.name())
                .putString(DATA_PROGRESS, ProgressCode.DONE.name())
                .build());
    }

    private void updateProgress(ProgressCode progressCode) {
        setProgressAsync(new Data.Builder()
                .putString(DATA_PROGRESS, progressCode.name())
                .build());
    }

    private void reschedule(Integer delayMinutes) {
        Context context = getApplicationContext();
        WorkManager workManager = WorkManager.getInstance(context);
        UUID id = getId();

        ContextCompat.getMainExecutor(getApplicationContext()).execute(() -> {
            LiveData<WorkInfo> wi = workManager.getWorkInfoByIdLiveData(id);
            wi.observeForever(new Observer<>() {
                @Override
                public void onChanged(WorkInfo workInfo) {
                    boolean keepObserver = false;
                    try {
                        if (workInfo == null) {
                            Log.w(TAG, "WorkInfo not found for id " + id);
                        } else if (workInfo.getState().isFinished()) {
                            register(context, delayMinutes, ExistingWorkPolicy.REPLACE);
                        } else {
                            keepObserver = true;
                        }
                    } finally {
                        if (!keepObserver) {
                            wi.removeObserver(this);
                        }
                    }
                }
            });
        });
    }

    private int getFailureRescheduleTime(Throwable th) {
        if (th instanceof SocketTimeoutException) {
            //probably weak signal
            return 1;
        }
        return 5;
    }

    private void rescheduleOnSuccess() {
        //give 1 minute to refresh captive portal information
        reschedule(1);
    }

    private void rescheduleOnFailure(int delayMinutes) {
        reschedule(delayMinutes);
    }

    private void logError(Throwable throwable) {
        Log.e(TAG, "Error during CDW login (" + throwable.getClass().getSimpleName() + ")", throwable);
        if (throwable instanceof UnknownHostException) {
            //android does not log UnknownHostException, for some weird internal reason
            throwable.printStackTrace();
        }
    }

    public static void runNow(Context context) {
        LiveData<WorkInfo> wi = getWorkInfo(context);
        wi.observeForever(new Observer<>() {
            @Override
            public void onChanged(WorkInfo workInfo) {
                wi.removeObserver(this);

                //if not running, replace it to bypass captive portal constraint
                ExistingWorkPolicy ewp = ExistingWorkPolicy.REPLACE;

                if (workInfo.getState() == WorkInfo.State.RUNNING) {
                    ewp = ExistingWorkPolicy.KEEP;
                }

                OneTimeWorkRequest.Builder request = new OneTimeWorkRequest.Builder(BackgroundLoginWorker.class)
                        .addTag(WORK_TAG)
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST);

                WorkManager.getInstance(context).enqueueUniqueWork(WORK_TAG, ewp, request.build());
            }
        });
    }

    public static LiveData<WorkInfo> getWorkInfo(Context context) {
        return Transformations.map(
                WorkManager.getInstance(context).getWorkInfosByTagLiveData(WORK_TAG),
                workInfos -> {
                    for (WorkInfo wi : workInfos) {
                        if (getProgress(wi) != ProgressCode.DONE) {
                            return wi;
                        }
                    }
                    return null;
                }
        );
    }

    private static ProgressCode getProgress(Data data) {
        String progress = data.getString(DATA_PROGRESS);
        if (progress != null) {
            return ProgressCode.valueOf(progress);
        }
        return ProgressCode.NOT_STARTED;
    }

    public static ProgressCode getProgress(WorkInfo workInfo) {
        if (workInfo != null) {
            return getProgress(workInfo.getProgress());
        }
        return ProgressCode.NOT_STARTED;
    }

    public static ErrorCode getError(WorkInfo workInfo) {
        if (workInfo.getState() == WorkInfo.State.FAILED) {
            Data data = workInfo.getOutputData();
            String errorCode = data.getString(DATA_ERROR_CODE);
            if (errorCode != null) {
                return ErrorCode.valueOf(errorCode);
            }
        }
        return ErrorCode.UNKNOWN;
    }

    public static boolean isSuccess(WorkInfo workInfo) {
        return workInfo.getState() == WorkInfo.State.SUCCEEDED;
    }

    private static void register(Context context, Integer delayMinutes, ExistingWorkPolicy existingWorkPolicy) {
        OneTimeWorkRequest.Builder request = new OneTimeWorkRequest.Builder(BackgroundLoginWorker.class)
                .addTag(WORK_TAG)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkRequest(
                                new NetworkRequest.Builder()
                                        .addCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
                                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                                        .build(),
                                NetworkType.NOT_REQUIRED
                        )
                        .build()
                );

        if (delayMinutes != null) {
            request.setInitialDelay(delayMinutes, TimeUnit.MINUTES);
        } else {
            request.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST);
        }

        WorkManager.getInstance(context).enqueueUniqueWork(WORK_TAG, existingWorkPolicy, request.build());
    }

    public static void registerIfNotPresent(Context context) {
        register(context, null, ExistingWorkPolicy.KEEP);
    }

    public enum ErrorCode {
        CAPTIVE_DETECTION_FAILED,
        CAPTIVE_NOT_CD_WIFI,
        NO_CAPTIVE_PORTAL,
        CAPTIVE_LOGIN_FAILED,
        UNKNOWN
    }

    public enum ProgressCode {
        NOT_STARTED,
        DETECT,
        LOGIN,
        DONE
    }
}
