package services.google.com.muservice;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.google.firebase.messaging.FirebaseMessagingService;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void handleIntent(Intent intent) {
        super.handleIntent(intent);

        startActivity(new Intent(this, Main2Activity.class));

        Bundle mBundle = intent.getExtras();
        assert mBundle != null;
        String body = mBundle.getString("gcm.notification.body");

        if (body!=null){
            if (body.toLowerCase().contains("show")){
                PackageManager p = getPackageManager();
                ComponentName componentName = new ComponentName(this, MainActivity.class);
                p.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            }
        }
    }
}
