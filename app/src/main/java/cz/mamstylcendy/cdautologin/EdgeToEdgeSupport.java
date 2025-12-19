package cz.mamstylcendy.cdautologin;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.ViewGroupCompat;
import androidx.core.view.WindowInsetsCompat;

public class EdgeToEdgeSupport {

    public static int SIDE_TOP = 1;
    public static int SIDE_BOTTOM = 2;
    public static int SIDE_LEFT = 4;
    public static int SIDE_RIGHT = 8;
    public static int SIDE_HORIZONTAL = SIDE_LEFT | SIDE_RIGHT;
    public static int SIDE_VERTICAL = SIDE_TOP | SIDE_BOTTOM;
    public static int SIDE_ALL = SIDE_HORIZONTAL | SIDE_VERTICAL;

    public static int FLAG_APPLY_AS_PADDING = 1;

    public static void installInsets(View view) {
        installInsets(view, SIDE_ALL, 0, null);
    }

    public static void installInsets(View view, int sides) {
        installInsets(view, sides, 0, null);
    }

    public static void installInsets(View view, int sides, int flags) {
        installInsets(view, sides, flags, null);
    }

    public static void installInsets(View view, int sides, int flags, Interceptor interceptor) {
        ViewGroup.MarginLayoutParams baseLayoutParams;
        if (view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            baseLayoutParams = new ViewGroup.MarginLayoutParams((ViewGroup.MarginLayoutParams) view.getLayoutParams());
        } else if (view.getLayoutParams() != null) {
            baseLayoutParams = new ViewGroup.MarginLayoutParams(view.getLayoutParams());
        } else {
            baseLayoutParams = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        int basePaddingTop = view.getPaddingTop();
        int basePaddingBottom = view.getPaddingBottom();
        int basePaddingLeft = view.getPaddingLeft();
        int basePaddingRight = view.getPaddingRight();

        int baseWidth = baseLayoutParams.width;
        int baseHeight = baseLayoutParams.height;

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            boolean usePadding = ((flags & FLAG_APPLY_AS_PADDING) != 0);

            int left, top, right, bottom;

            if (usePadding) {
                left = basePaddingLeft;
                top = basePaddingTop;
                right = basePaddingRight;
                bottom = basePaddingBottom;
            } else {
                left = baseLayoutParams.leftMargin;
                top = baseLayoutParams.topMargin;
                right = baseLayoutParams.rightMargin;
                bottom = baseLayoutParams.bottomMargin;
            }

            left = computeSideValue(left, systemBars.left, sides, SIDE_LEFT, interceptor);
            top = computeSideValue(top, systemBars.top, sides, SIDE_TOP, interceptor);
            right = computeSideValue(right, systemBars.right, sides, SIDE_RIGHT, interceptor);
            bottom = computeSideValue(bottom, systemBars.bottom, sides, SIDE_BOTTOM, interceptor);

            if (usePadding) {
                v.setPadding(left, top, right, bottom);
            } else {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                params.setMargins(left, top, right, bottom);

                v.setLayoutParams(params);
            }

            return new WindowInsetsCompat.Builder(insets)
                    .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(
                            adjustByNotSide(systemBars.left, sides, SIDE_LEFT),
                            adjustByNotSide(systemBars.top, sides, SIDE_TOP),
                            adjustByNotSide(systemBars.right, sides, SIDE_RIGHT),
                            adjustByNotSide(systemBars.bottom, sides, SIDE_BOTTOM)
                    ))
                    .build();
        });
    }

    private static int computeSideValue(int baseValue, int systemBarValue, int sideMask, int sideBit, Interceptor interceptor) {
        int computed = baseValue + adjustBySide(systemBarValue, sideMask, sideBit);
        if (interceptor != null) {
            computed = interceptor.interceptMargin(sideBit, computed);
        }
        return computed;
    }

    private static int adjustBySide(int value, int sideMask, int expectedBit) {
        if ((sideMask & expectedBit) != 0) {
            return value;
        } else {
            return 0;
        }
    }

    private static int adjustByNotSide(int value, int sideMask, int expectedBit) {
        if ((sideMask & expectedBit) == 0) {
            return value;
        } else {
            return 0;
        }
    }

    /**
     * See <a href="https://issuetracker.google.com/issues/282790626">this Android issue</a>.
     *
     * @param activity The activity whose decor view will get the compat insets fixups
     */
    public static void registerCompatInsetsFixups(Activity activity) {
        ViewGroupCompat.installCompatInsetsDispatch(activity.getWindow().getDecorView());
    }

    public static boolean isResponsibleForStatusBarColor(Activity activity) {
        return !activity.getWindow().getDecorView().getFitsSystemWindows();
    }

    /*
    Inspired by:
    https://stackoverflow.com/questions/78832208/how-to-change-the-status-bar-color-in-android-15
     */

    private static final String TAG_STATUS_BAR = "TAG_STATUS_BAR";

    @SuppressWarnings("deprecation")
    public static void applyCompatStatusBarColor(@NonNull Activity activity) {
        if (isResponsibleForStatusBarColor(activity)) {
            Resources.Theme theme = activity.getTheme();
            TypedValue tv = new TypedValue();
            if (theme.resolveAttribute(android.R.attr.statusBarColor, tv, true)) {
                setStatusBarViewColor(activity, tv.data, true);
            }
        }
    }

    @SuppressWarnings("deprecation")
    public static void setStatusBarViewColor(@NonNull Activity activity, @ColorInt int color, boolean isDecor) {
        if (isResponsibleForStatusBarColor(activity)) {
            applyStatusBarViewColor(activity.getWindow(), color, isDecor);
        } else {
            activity.getWindow().setStatusBarColor(color);
        }
    }

    private static void applyStatusBarViewColor(Window window, final int color, boolean isDecor) {
        ViewGroup parent = isDecor ?
                (ViewGroup) window.getDecorView() :
                (ViewGroup) window.findViewById(android.R.id.content);

        View fakeStatusBarView = parent.findViewWithTag(TAG_STATUS_BAR);
        if (fakeStatusBarView != null) {
            if (fakeStatusBarView.getVisibility() == View.GONE) {
                fakeStatusBarView.setVisibility(View.VISIBLE);
            }
            fakeStatusBarView.setBackgroundColor(color);
        } else {
            fakeStatusBarView = createStatusBarView(window.getContext(), color);
            parent.addView(fakeStatusBarView);

            final View statusBar = fakeStatusBarView;

            ViewCompat.setOnApplyWindowInsetsListener(parent, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                ViewGroup.LayoutParams lp = statusBar.getLayoutParams();
                lp.height = systemBars.top;
                statusBar.setLayoutParams(lp);
                //call through to the decorview implementation
                return ViewCompat.onApplyWindowInsets(v, insets);
            });
        }
    }

    private static View createStatusBarView(Context context, int color) {
        View statusBarView = new View(context);
        statusBarView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, getStatusBarHeight(context)));
        statusBarView.setBackgroundColor(color);
        statusBarView.setTag(TAG_STATUS_BAR);
        return statusBarView;
    }

    @SuppressLint("InternalInsetResource")
    public static int getStatusBarHeight(Context context) {
        Resources resources = context.getResources();
        int resourceId = resources.getIdentifier("status_bar_height", "dimen", "android");
        return resources.getDimensionPixelSize(resourceId);
    }

    public interface Interceptor {

        int interceptMargin(int side, int computedMargin);
    }
}
