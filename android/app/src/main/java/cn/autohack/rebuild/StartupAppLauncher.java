package cn.autohack.rebuild;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Lists and launches user-installed apps only after an explicit foreground action. */
public final class StartupAppLauncher {
    private StartupAppLauncher() {}

    public static boolean isEligible(boolean systemApp, boolean ownApp, boolean hasLaunchIntent) {
        return !systemApp && !ownApp && hasLaunchIntent;
    }

    public static List<Entry> listUserLaunchableApps(Context context) {
        PackageManager manager = context.getPackageManager();
        List<Entry> entries = new ArrayList<>();
        for (ApplicationInfo info : manager.getInstalledApplications(0)) {
            boolean system = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean own = info.packageName.equals(context.getPackageName());
            Intent launch = manager.getLaunchIntentForPackage(info.packageName);
            if (!isEligible(system, own, launch != null)) continue;
            CharSequence label = manager.getApplicationLabel(info);
            entries.add(new Entry(label == null ? info.packageName : label.toString(),
                    info.packageName));
        }
        final Collator collator = Collator.getInstance();
        Collections.sort(entries, new Comparator<Entry>() {
            @Override public int compare(Entry left, Entry right) {
                return collator.compare(left.label, right.label);
            }
        });
        return entries;
    }

    public static boolean launch(Context context, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return false;
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) return false;
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return true;
    }

    public static final class Entry {
        public final String label;
        public final String packageName;

        Entry(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }

        @Override public String toString() { return label; }
    }
}
