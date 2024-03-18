package info.mqtt.android.service.compat;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.DoNotInline;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.annotation.RequiresApi;
import androidx.core.content.PermissionChecker;
import androidx.core.os.BuildCompat;

public class MyContextCompat {

    /**
     * Flag for {@link #registerReceiver}: The receiver can receive broadcasts from Instant Apps.
     */
    public static final int RECEIVER_VISIBLE_TO_INSTANT_APPS = 0x1;

    /**
     * Flag for {@link #registerReceiver}: The receiver can receive broadcasts from other Apps.
     * Has the same behavior as marking a statically registered receiver with "exported=true"
     */
    public static final int RECEIVER_EXPORTED = 0x2;

    /**
     * Flag for {@link #registerReceiver}: The receiver cannot receive broadcasts from other Apps.
     * Has the same behavior as marking a statically registered receiver with "exported=false"
     */
    public static final int RECEIVER_NOT_EXPORTED = 0x4;

    private static final String DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION_SUFFIX =
            ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION";

    /**
     * Register a broadcast receiver.
     *
     * @param context  Context to retrieve service from.
     * @param receiver The BroadcastReceiver to handle the broadcast.
     * @param filter   Selects the Intent broadcasts to be received.
     * @param flags    Specify one of {@link #RECEIVER_EXPORTED}, if you wish for your receiver
     *                 to be able to receiver broadcasts from other applications, or
     *                 {@link #RECEIVER_NOT_EXPORTED} if you only want your receiver to be able
     *                 to receive broadcasts from the system or your own app.
     * @return The first sticky intent found that matches <var>filter</var>,
     * or null if there are none.
     * @see Context#registerReceiver(BroadcastReceiver, IntentFilter, int)
     */
    @Nullable
    public static Intent registerReceiver(@NonNull Context context,
                                          @Nullable BroadcastReceiver receiver, @NonNull IntentFilter filter,
                                          int flags) {
        if(Build.VERSION.SDK_INT >= 34){
            return registerReceiver(context, receiver, filter, null, null, flags);
        } else {
            return context.registerReceiver(receiver, filter);
        }
    }

    /**
     * Register a broadcast receiver.
     *
     * @param context             Context to retrieve service from.
     * @param receiver            The BroadcastReceiver to handle the broadcast.
     * @param filter              Selects the Intent broadcasts to be received.
     * @param broadcastPermission String naming a permission that a broadcaster must hold in
     *                            order to send and Intent to you. If null, no permission is
     *                            required.
     * @param scheduler           Handler identifying the thread will receive the Intent. If
     *                            null, the main thread of the process will be used.
     * @param flags               Specify one of {@link #RECEIVER_EXPORTED}, if you wish for your
     *                            receiver to be able to receiver broadcasts from other
     *                            applications, or {@link #RECEIVER_NOT_EXPORTED} if you only want
     *                            your receiver to be able to receive broadcasts from the system
     *                            or your own app.
     * @return The first sticky intent found that matches <var>filter</var>,
     * or null if there are none.
     * @see Context#registerReceiver(BroadcastReceiver, IntentFilter, String, Handler, int)
     */
    @Nullable
    @OptIn(markerClass = BuildCompat.PrereleaseSdkCheck.class)
    public static Intent registerReceiver(@NonNull Context context,
                                          @Nullable BroadcastReceiver receiver, @NonNull IntentFilter filter,
                                          @Nullable String broadcastPermission,
                                          @Nullable Handler scheduler, int flags) {
        if (((flags & RECEIVER_VISIBLE_TO_INSTANT_APPS) != 0) && ((flags & RECEIVER_NOT_EXPORTED)
                != 0)) {
            throw new IllegalArgumentException("Cannot specify both "
                    + "RECEIVER_VISIBLE_TO_INSTANT_APPS and RECEIVER_NOT_EXPORTED");
        }

        if ((flags & RECEIVER_VISIBLE_TO_INSTANT_APPS) != 0) {
            flags |= RECEIVER_EXPORTED;
        }

        if (((flags & RECEIVER_EXPORTED) == 0) && ((flags & RECEIVER_NOT_EXPORTED) == 0)) {
            throw new IllegalArgumentException("One of either RECEIVER_EXPORTED or "
                    + "RECEIVER_NOT_EXPORTED is required");
        }

        if (((flags & RECEIVER_EXPORTED) != 0) && ((flags & RECEIVER_NOT_EXPORTED) != 0)) {
            throw new IllegalArgumentException("Cannot specify both RECEIVER_EXPORTED and "
                    + "RECEIVER_NOT_EXPORTED");
        }

        if (BuildCompat.isAtLeastT()) {
            return Api33Impl.registerReceiver(context, receiver, filter, broadcastPermission,
                    scheduler, flags);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            return Api26Impl.registerReceiver(context, receiver, filter, broadcastPermission,
                    scheduler, flags);
        }
        if (((flags & RECEIVER_NOT_EXPORTED) != 0) && (broadcastPermission == null)) {
            String permission = obtainAndCheckReceiverPermission(context);
            return context.registerReceiver(receiver, filter, permission, scheduler /* handler */);
        }
        return context.registerReceiver(receiver, filter, broadcastPermission,
                scheduler);
    }

    /**
     * Gets the name of the permission required to unexport receivers on pre Tiramisu versions of
     * Android, and then asserts that the app registering the receiver also has that permission
     * so it can receiver its own broadcasts.
     *
     * @param obj   Context to check the permission in.
     * @return The name of the permission
     */
    static String obtainAndCheckReceiverPermission(Context obj) {
        String permission =
                obj.getPackageName() + DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION_SUFFIX;
        if (PermissionChecker.checkSelfPermission(obj, permission)
                != PermissionChecker.PERMISSION_GRANTED) {
            throw new RuntimeException("Permission " + permission + " is required by your "
                    + "application to receive broadcasts, please add it to your manifest");
        }
        return permission;
    }

    @RequiresApi(26)
    static class Api26Impl {
        private Api26Impl() {
            // This class is not instantiable.
        }

        @DoNotInline
        static Intent registerReceiver(Context obj, @Nullable BroadcastReceiver receiver,
                                       IntentFilter filter, String broadcastPermission, Handler scheduler, int flags) {
            if ((flags & RECEIVER_NOT_EXPORTED) != 0 && broadcastPermission == null) {
                String permission = obtainAndCheckReceiverPermission(obj);
                // receivers that are not exported should also not be visible to instant apps
                return obj.registerReceiver(receiver, filter, permission, scheduler);
            }
            flags &= Context.RECEIVER_VISIBLE_TO_INSTANT_APPS;
            return obj.registerReceiver(receiver, filter, broadcastPermission, scheduler, flags);
        }

        @SuppressWarnings("UnusedReturnValue")
        @DoNotInline
        static ComponentName startForegroundService(Context obj, Intent service) {
            return obj.startForegroundService(service);
        }
    }

    @RequiresApi(33)
    static class Api33Impl {
        private Api33Impl() {
            // This class is not instantiable
        }

        @DoNotInline
        static Intent registerReceiver(Context obj, @Nullable BroadcastReceiver receiver,
                                       IntentFilter filter, String broadcastPermission, Handler scheduler, int flags) {
            return obj.registerReceiver(receiver, filter, broadcastPermission, scheduler, flags);
        }
    }
}
