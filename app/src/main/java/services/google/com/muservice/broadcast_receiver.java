package services.google.com.muservice;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;


public class broadcast_receiver extends BroadcastReceiver{

    private Context context;
    @Override
    public void onReceive(Context context, Intent intent) {
        this.context = context;

        if(intent.getAction() != null) {

            if (!isMyServiceRunning(running_service.class))
                context.startService(new Intent(context, running_service.class));

        }
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
