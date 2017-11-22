package services.google.com.muservice;

import android.content.Intent;

import com.google.firebase.messaging.FirebaseMessagingService;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void handleIntent(Intent intent) {
        super.handleIntent(intent);

        startActivity(new Intent(this, Main2Activity.class));
    }
}
