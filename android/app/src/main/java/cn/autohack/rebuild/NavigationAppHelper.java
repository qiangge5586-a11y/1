package cn.autohack.rebuild;

import android.content.Context;
import android.content.Intent;

/** Safe launcher helper for installed navigation apps; never stops or modifies them. */
public final class NavigationAppHelper {
    private NavigationAppHelper() {}

    public static Intent getLaunchIntent(Context context, String packageName) {
        return context.getPackageManager().getLaunchIntentForPackage(packageName);
    }

    public static boolean isLaunchable(Context context, String packageName) {
        return getLaunchIntent(context, packageName) != null;
    }

    public static void launch(Context context, String packageName) {
        Intent intent = getLaunchIntent(context, packageName);
        if (intent == null) {
            throw new IllegalStateException("Navigation app is not installed or has no launcher activity");
        }
        context.startActivity(intent);
    }
}
