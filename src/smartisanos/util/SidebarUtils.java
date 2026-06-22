package smartisanos.util;

import android.content.Context;
import android.view.View;

public final class SidebarUtils {
    private SidebarUtils() {
    }

    public static boolean isSidebarShowing(Context context) {
        return false;
    }

    public static void dragText(View source, Context context, String text) {
        // No sidebar integration in the standalone build.
    }
}
