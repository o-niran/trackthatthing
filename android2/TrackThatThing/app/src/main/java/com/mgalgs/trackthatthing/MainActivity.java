package com.mgalgs.trackthatthing;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;


public class MainActivity extends Activity {
    public static final int ACTIVITY_RESULT_GET_SECRET = 0;
    /*
     * Define a request code to send to Google Play services
     * This code is returned in Activity.onActivityResult
     */
    private static final String DIALOG_ERROR = "dialog_error";
    private boolean mResolvingError = false;
    private static final String STATE_RESOLVING_ERROR = "resolving_error";
    private static final String STATE_TRACKING = "tracking";
    private static final int REQUEST_RESOLVE_ERROR = 1001;


    Handler mHandler = new Handler();

    private NotTrackingFragment mNotTrackingFragment = new NotTrackingFragment();
    private YesTrackingFragment mYesTrackingFragment = new YesTrackingFragment();


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_RESOLVING_ERROR, mResolvingError);
        outState.putBoolean(STATE_TRACKING, mTracking);
    }

    private boolean mTracking;
    private String mSecretCode;

    public static class NotTrackingFragment extends Fragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            // Inflate the layout for this fragment
            return inflater.inflate(R.layout.fragment_layout_not_tracking, container, false);
        }
    }

    public static class YesTrackingFragment extends Fragment {
        private View mView;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            // Inflate the layout for this fragment
            mView = inflater.inflate(R.layout.fragment_layout_yes_tracking, container, false);

            MainActivity mainActivity = (MainActivity) getActivity();
            updateSecretCode(mainActivity.mSecretCode);
            updateLastLoc(mainActivity.getApplicationContext());

            return mView;
        }

        public void updateSecretCode(String secretCode) {
            // update the secret code text view
            TextView tv = (TextView) mView.findViewById(R.id.tv_with_code);
            tv.setText(getString(R.string.with_code) + " " + secretCode);
        }

        public void updateLastLoc(Context context) {
            SharedPreferences settings = context.getSharedPreferences(TrackThatThing.PREFS_NAME,
                    android.content.Context.MODE_PRIVATE);
            String last = settings.getString(TrackThatThing.PREF_LAST_LOC_TIME, "a long time ago...");
            TextView tv = (TextView) mView.findViewById(R.id.tv_last_update);
            if (tv != null)
                tv.setText(context.getString(R.string.last_update) + " " + last);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_tracking);

        mTracking = savedInstanceState != null
            && savedInstanceState.getBoolean(STATE_TRACKING, false);
        if (mTracking)
            startTracking();
        else
            stopTracking();
    }

    /**
     * Verify that Google Play services is available before making a request.
     *
     * @return true if Google Play services is available, otherwise false
     */
    private boolean servicesConnected() {

        // Check that Google Play services is available
        int resultCode =
                GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);

        // If Google Play services is available
        if (ConnectionResult.SUCCESS == resultCode) {
            // Continue
            Log.d(TrackThatThing.TAG, "Have google play services!");
            return true;
            // Google Play services was not available for some reason
        } else {
            // Display an error dialog
            Dialog dialog = GooglePlayServicesUtil.getErrorDialog(resultCode, this, 0);
            if (dialog != null) {
                showErrorDialog(resultCode);
            }
            return false;
        }
    }

    /* Creates a dialog for an error message */
    private void showErrorDialog(int errorCode) {
        // Create a fragment for the error dialog
        ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
        // Pass the error that should be displayed
        Bundle args = new Bundle();
        args.putInt(DIALOG_ERROR, errorCode);
        dialogFragment.setArguments(args);
        dialogFragment.show(getFragmentManager(), "errordialog");
    }

    /**
     * Define a DialogFragment to display the error dialog generated in
     * showErrorDialog.
     */
    public static class ErrorDialogFragment extends DialogFragment {
        public ErrorDialogFragment() {
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Get the error code and retrieve the appropriate dialog
            int errorCode = this.getArguments().getInt(DIALOG_ERROR);
            return GooglePlayServicesUtil.getErrorDialog(errorCode,
                    this.getActivity(), REQUEST_RESOLVE_ERROR);
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            ((MainActivity) getActivity()).onDialogDismissed();
        }
    }


    private void UI_notTracking() {
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.tracking_fragment_container, mNotTrackingFragment);
        fragmentTransaction.commit();
    }

    private void UI_yesTracking() {
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.tracking_fragment_container, mYesTrackingFragment);
        fragmentTransaction.commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        switch (item.getItemId()) {
            case R.id.action_track:
                toggleTracking();
                return true;
            case R.id.action_secret:
                launchSecretGetter();
                return true;
            case R.id.action_share:
                share();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void share()
    {
        String subject = "See where I'm at in real-time!";
        String bodyText = "http://www.trackthatthing.com";
        try {
            bodyText = String
                    .format("Hey! I'm using TrackThatThing "
                                    + "to track my location. Check out the real-time map of my location "
                                    + "here: http://www.trackthatthing.com/live?secret=%s",
                            URLEncoder.encode(mSecretCode, "ascii"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        final Intent theIntent = new Intent(android.content.Intent.ACTION_SEND);
        theIntent.setType("text/plain");
        theIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, subject);
        theIntent.putExtra(android.content.Intent.EXTRA_TEXT, bodyText);
        startActivity(Intent.createChooser(theIntent, "Send Location"));
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case ACTIVITY_RESULT_GET_SECRET:
                startTracking();
                break;
        }
    }

    private MyLocationService mLocationService;

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mLocationService = ((MyLocationService.LocalBinder)service).getService();

            Log.d(TrackThatThing.TAG, "Service connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mLocationService = null;
        }
    };


    public void launchSecretGetter() {
        Intent i = new Intent(this, TheSecretGetter.class);
        startActivityForResult(i, ACTIVITY_RESULT_GET_SECRET);
    }

    private void toggleTracking() {
        if (mTracking)
            stopTracking();
        else
            startTracking();
    }

    private Intent mLocationServiceIntent;

    private void startTracking() {

        SharedPreferences settings = getSharedPreferences(TrackThatThing.PREFS_NAME, MODE_PRIVATE);
        mSecretCode = settings.getString(TrackThatThing.PREF_SECRET_CODE, null);

        if (mSecretCode == null) {
            launchSecretGetter();
            return;
        }

        mLocationServiceIntent = new Intent(MainActivity.this, MyLocationService.class);
        MainActivity.this.startService(mLocationServiceIntent);

        mTracking = true;
        UI_yesTracking();
    }

    private void stopTracking() {
        stopService(mLocationServiceIntent);
        mTracking = false;
        UI_notTracking();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    /* Called from ErrorDialogFragment when the dialog is dismissed. */
    public void onDialogDismissed() {
        mResolvingError = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mLocUpdateReceiver, new IntentFilter(TrackThatThing.IF_LOC_UPDATE));
    }

    @Override
    protected void onPause() {
        unregisterReceiver(mLocUpdateReceiver);
        super.onPause();
    }

    private BroadcastReceiver mLocUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mYesTrackingFragment.updateLastLoc(context);
            this.setResultCode(Activity.RESULT_OK);
        }
    };
}