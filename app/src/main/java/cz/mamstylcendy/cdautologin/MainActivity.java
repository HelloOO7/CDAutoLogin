package cz.mamstylcendy.cdautologin;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;
import androidx.core.content.PackageManagerCompat;
import androidx.core.content.UnusedAppRestrictionsConstants;
import androidx.work.WorkInfo;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

    private static final String PK_HIBERNATION_DIALOG_SUPPRESS = "hibernation_dialog_suppress";

    private TextView tvStatus;
    private Button btnConnect;

    private ActivityResultLauncher<String> mNotificationPermissionLauncher;

    private UUID mShownWorkUUID = null;

    private SharedPreferences mPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeSupport.registerCompatInsetsFixups(this);
        EdgeToEdgeSupport.applyCompatStatusBarColor(this);
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT));

        mPreferences = getSharedPreferences(getPackageName(), MODE_PRIVATE);

        setContentView(R.layout.activity_main);
        tvStatus = findViewById(R.id.tvStatus);
        btnConnect = findViewById(R.id.btnConnect);

        mNotificationPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {

        });

        BackgroundLoginWorker.registerIfNotPresent(this);

        BackgroundLoginWorker.getWorkInfo(this).observe(this, this::updateWorkStatus);

        if (NetworkStackNotifierCompat.isUsed() || BackgroundLoginWorker.needsForegroundNotification()) {
            requestRuntimeNotificationPermission();
        }

        checkHibernationExemption();
    }

    private void checkHibernationExemption() {
        if (mPreferences.getBoolean(PK_HIBERNATION_DIALOG_SUPPRESS, false)) {
            return;
        }

        //google "zkus vymyslet nejhorsi asynchronni API challenge 2025"
        ListenableFuture<Integer> future = PackageManagerCompat.getUnusedAppRestrictionsStatus(this);
        future.addListener(() -> {
            //hehe, nenapadlo treba nekoho, ze by bylo tisickrat pohodlnejsi predat vysledek te future do toho callbacku??
            try {
                //jo aha, jasne, a jeste navic to hazi checked vyjimku
                int result = future.get();
                if (!Set.of(
                        UnusedAppRestrictionsConstants.DISABLED,
                        UnusedAppRestrictionsConstants.ERROR,
                        UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE
                ).contains(result)) {
                    requestHibernationExemption();
                }
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void requestHibernationExemption() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.hibernation_exemption_title)
                .setMessage(R.string.hibernation_exemption_message)
                .setPositiveButton(R.string.hibernation_exemption_button, (dialog, which) -> {
                    callHibernationExemptionActivity();
                })
                .setNeutralButton(R.string.hibernation_exemption_cancel, null)
                .setNegativeButton(R.string.hibernation_exemption_dont_show_again, (dialog, which) -> {
                    mPreferences.edit()
                            .putBoolean(PK_HIBERNATION_DIALOG_SUPPRESS, true)
                            .apply();
                })
                .show();
    }

    //documentation: "It is important that you call startActivityForResult(), not startActivity(), when sending this intent."
    //meanwhile startActivityForResult:
    @SuppressWarnings("deprecation")
    private void callHibernationExemptionActivity() {
        Intent intent = IntentCompat.createManageUnusedAppRestrictionsIntent(this, getPackageName());
        startActivityForResult(intent, 0);
    }

    private void requestRuntimeNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (
                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            && !shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)
            ) {
                mNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void updateWorkStatus(WorkInfo workInfo) {
        if (workInfo == null || workInfo.getState().isFinished()) {
            btnConnect.setEnabled(true);
            if (workInfo != null && mShownWorkUUID != null && mShownWorkUUID.equals(workInfo.getId())) {
                if (BackgroundLoginWorker.isSuccess(workInfo)) {
                    tvStatus.setText(R.string.status_success);
                } else {
                    tvStatus.setText(getErrorText(BackgroundLoginWorker.getError(workInfo)));
                }
            } else {
                tvStatus.setText(R.string.status_ready);
            }
        } else {
            BackgroundLoginWorker.ProgressCode progress = BackgroundLoginWorker.getProgress(workInfo);
            if (progress != BackgroundLoginWorker.ProgressCode.NOT_STARTED) {
                mShownWorkUUID = workInfo.getId();
                btnConnect.setEnabled(false);
                tvStatus.setText(getProgressText(progress));
            }
        }
    }

    private @StringRes int getErrorText(BackgroundLoginWorker.ErrorCode error) {
        switch (error) {
            case NO_CAPTIVE_PORTAL:
                return R.string.error_not_captive;
            case CAPTIVE_DETECTION_FAILED:
                return R.string.error_detection;
            case CAPTIVE_LOGIN_FAILED:
                return R.string.error_login;
            case CAPTIVE_NOT_CD_WIFI:
                return R.string.error_not_cd_wifi;
            default:
                return R.string.error_unknown;
        }
    }

    private @StringRes int getProgressText(BackgroundLoginWorker.ProgressCode progress) {
        if (progress != null) {
            switch (progress) {
                case DETECT:
                    return R.string.status_detecting;
                case LOGIN:
                    return R.string.status_logging_in;
            }
        }
        return R.string.status_connecting;
    }

    public void btnConnectClicked(View view) {
        view.setEnabled(false);
        tvStatus.setText(getProgressText(null));

        BackgroundLoginWorker.runNow(this);
    }
}