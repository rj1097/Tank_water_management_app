package com.example.uw;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.provider.AlarmClock;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.client.AWSStartupHandler;
import com.amazonaws.mobile.client.AWSStartupResult;
import com.amazonaws.mobile.client.Callback;
import com.amazonaws.mobile.client.UserStateDetails;
import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttLastWillAndTestament;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttNewMessageCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.iot.AWSIotClient;
import com.amazonaws.services.iot.model.AttachPrincipalPolicyRequest;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateRequest;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateResult;
import com.example.uw.aws.AWSLoginModel;
import com.amazonaws.mobile.auth.core.IdentityManager;
import com.google.gson.JsonObject;
//import com.amazonaws.android.auth.CognitoCredentialsProvider;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.security.KeyStore;
import java.util.UUID;

import me.itangqi.waveloadingview.WaveLoadingView;

public class MainActivity extends AppCompatActivity {

    static final String LOG_TAG = MainActivity.class.getCanonicalName();

    // --- Constants to modify per your configuration ---

    // IoT endpoint
    // AWS Iot CLI describe-endpoint call returns: XXXXXXXXXX.iot.<region>.amazonaws.com
    private static final String CUSTOMER_SPECIFIC_ENDPOINT = "a2zymexyuqj8oz-ats.iot.ap-south-1.amazonaws.com";
    // Name of the AWS IoT policy to attach to a newly created certificate
    private static final String AWS_IOT_POLICY_NAME = "AppPolicy";

    // Region of AWS IoT
    private static final Regions MY_REGION = Regions.AP_SOUTH_1;
    // Filename of KeyStore file on the filesystem
    private static final String KEYSTORE_NAME = "iot_keystore";
    // Password for the private key in the KeyStore
    private static final String KEYSTORE_PASSWORD = "password";
    // Certificate and key aliases in the KeyStore
    private static final String CERTIFICATE_ID = "default";

    EditText txtSubscribe;
    EditText txtTopic;

    TextView tvLastMessage;
    TextView tvClientId;
    TextView tvStatus;

    Button btnConnect;
    //Button alarm;
    AWSIotClient mIotAndroidClient;
    AWSIotMqttManager mqttManager;
    String clientId;
    String keystorePath;
    String keystoreName;
    String keystorePassword;

    KeyStore clientKeyStore = null;
    String certificateId;
    ProgressBar simpleProgressBar;
    JSONObject iotMessage;
    WaveLoadingView waveLoadingView;
    Button setdepth;
    SwipeRefreshLayout mySwipeRefreshLayout;
    int tank_depth;
    int intDistance;

    boolean print_to_subscribed_topic = false;
    SharedPreferences tank_data;
    //initializing editor
    SharedPreferences.Editor editor_tank_data;

    public void connectClick()//final View view)
    {
        Log.d(LOG_TAG, "clientId = " + clientId);

        try {
            mqttManager.connect(clientKeyStore, new AWSIotMqttClientStatusCallback() {
                @Override
                public void onStatusChanged(final AWSIotMqttClientStatus status,
                                            final Throwable throwable) {
                    Log.d(LOG_TAG, "Status = " + String.valueOf(status));

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvStatus.setText(status.toString());
                            if (throwable != null) {
                                Log.e(LOG_TAG, "Connection error.", throwable);
                            }
                        }
                    });
                }
            });
        } catch (final Exception e) {
            Log.e(LOG_TAG, "Connection error.", e);
            tvStatus.setText("Error! " + e.getMessage());
        }

    }

    public void update_waveloading() {
        int progress = (int) ((((float) (tank_depth - intDistance)) / (float) tank_depth) * 100);
        waveLoadingView.setProgressValue(progress);
        editor_tank_data.putInt("tankSensorData", intDistance);
        editor_tank_data.apply();
        if (progress < 50) {
            waveLoadingView.setBottomTitle(String.format("%d%%", progress));
            waveLoadingView.setCenterTitle("");
            waveLoadingView.setTopTitle("");
        } else if (progress < 80) {
            waveLoadingView.setBottomTitle("");
            waveLoadingView.setCenterTitle(String.format("%d%%", progress));
            waveLoadingView.setTopTitle("");
        } else {
            waveLoadingView.setBottomTitle("");
            waveLoadingView.setCenterTitle("");
            waveLoadingView.setTopTitle(String.format("%d%%", progress));
        }
    }

    public void iotOn(final View view) {
        //final String topic = txtTopic.getText().toString();
        final String topic = ("home/iot_button/" + clientId);
        final String msgon = "{\"gpio\":{\"pin\":2,\"state\":0}}";
        try {
            mqttManager.publishString(msgon, topic, AWSIotMqttQos.QOS0);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Publish error.", e);
        }
    }

    public void iotOff(final View view) {
        //final String topic = txtTopic.getText().toString();
        final String topic = ("home/iot_button/" + clientId);
        final String msgoff = "{\"gpio\":{\"pin\": 2, \"state\": 1}}";
        try {
            mqttManager.publishString(msgoff, topic, AWSIotMqttQos.QOS0);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Publish error.", e);
        }
    }

    public void subscribeClick()//final View view)
    {
        final String topic = ("home/tanklevel/" + clientId);
        Log.d(LOG_TAG, "topic = " + topic);

        try {
            mqttManager.subscribeToTopic(topic, AWSIotMqttQos.QOS0,
                    new AWSIotMqttNewMessageCallback() {
                        @Override
                        public void onMessageArrived(final String topic, final byte[] data) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        String message = new String(data, "UTF-8");
                                        Log.d(LOG_TAG, "Message arrived:");
                                        Log.d(LOG_TAG, "   Topic: " + topic);
                                        Log.d(LOG_TAG, " Message: " + message);
                                        //{\"Device Parameter\":{\"distance\":%.2f, \"Id\":%s, \"Status\":%d}}
                                        JSONObject device_param = new JSONObject(message);
                                        intDistance = device_param.getInt("distance");
                                        Log.d(LOG_TAG, " Distance: " + intDistance);
                                        String deviceId = device_param.getString("Id");
                                        Log.d(LOG_TAG, " Device Id: " + deviceId);
                                        //String strDistance = device_param.getString("distance");


                                        //waveLoadingView.setProgressValue((int)percentlevel);

                                        //tvLastMessage.setText("Id: " + deviceId + "\nDistance: " + strDistance);//+"\nPercent: "+percentlevel);
                                        update_waveloading();
                                    } catch (UnsupportedEncodingException e) {
                                        Log.e(LOG_TAG, "Message encoding error.", e);
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                        }
                    });
        } catch (Exception e) {
            Log.e(LOG_TAG, "Subscription error.", e);
        }
        //setRefreshing(false);
    }

    public void disconnectClick()//final View view)
    {
        try {
            mqttManager.disconnect();

        } catch (Exception e) {
            Log.e(LOG_TAG, "Disconnect error.", e);
        }
        print_to_subscribed_topic = false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tank_data = getSharedPreferences("tank_data", MODE_PRIVATE);
        editor_tank_data = tank_data.edit();
        intDistance = tank_data.getInt("tankSensorData", 500);
        tank_depth = tank_data.getInt("tankDepth", 500);


        //simpleProgressBar = (ProgressBar) findViewById(R.id.simpleProgressBar);
        //setProgressValue(progress);
        /*alarm = (Button) findViewById(R.id.alarm);
        alarm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
                intent.putExtra(AlarmClock.EXTRA_HOUR, 10);
                intent.putExtra(AlarmClock.EXTRA_MINUTES, 20);
                startActivity(intent);
            }
        });*/

        //tvLastMessage = findViewById(R.id.tvLastMessage);
        tvClientId = findViewById(R.id.tvClientId);
        tvStatus = findViewById(R.id.tvStatus);
        //btnConnect = findViewById(R.id.btnConnect);
        //btnConnect.setEnabled(false);

        waveLoadingView = (WaveLoadingView) findViewById(R.id.wave_LoadingView);
        waveLoadingView.setProgressValue(0);
        // MQTT client IDs are required to be unique per AWS IoT account.
        // This UUID is "practically unique" but does not _guarantee_
        // uniqueness.
        //String  id = AWSLoginModel.getSavedUserName(MainActivity.this);

        //clientId = UUID.randomUUID().toString();
        clientId = AWSLoginModel.getSavedUserName(MainActivity.this);
        tvClientId.setText(clientId);

        // Create log out Button on click listener
        //Button clickButton = (Button) findViewById(R.id.signOutButton);

        // Initialize the AWS Cognito credentials provider
        /*AWSMobileClient.getInstance().initialize(this, new Callback<UserStateDetails>() {
            @Override
            public void onResult(UserStateDetails result) {
                initIoTClient();
            }

            @Override
            public void onError(Exception e) {
                Log.e(LOG_TAG,"onError: ", e);
            }
        });*/

        AWSMobileClient.getInstance().initialize(this, new AWSStartupHandler() {
            @Override
            public void onComplete(AWSStartupResult awsStartupResult) {
                Log.d("YourMainActivity", "AWSMobileClient is instantiated and you are connected to AWS!");
                initIoTClient();
                connectClick();
                Log.d("YourMainActivity", "Connected to Aws");
                update_waveloading();
                /*final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        subscribeClick();
                        //update_waveloading();
                        Log.d("YourMainActivity", "Subscribed to Aws Topic");
                    }
                }, 500);*/

            }

        }).execute();

    }

    /*private void setProgressValue(final int progress) {

        // set the progress
        simpleProgressBar.setProgress(progress);
        // thread is used to change the progress value
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                setProgressValue(progress + 10);
            }
        });
        thread.start();
    }*/

    public void set_depth() {
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View formElementsView = inflater.inflate(R.layout.prompts,
                null, false);

        final EditText nameEditText = (EditText) formElementsView
                .findViewById(R.id.tankDepth);

        // the alert dialog
        new AlertDialog.Builder(MainActivity.this).setView(formElementsView)
                .setTitle("Enter Tank Depth")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @TargetApi(11)
                    public void onClick(DialogInterface dialog, int id) {
                        if (nameEditText.getText().toString().trim().length() == 0) {
                            nameEditText.setError("Please enter a valid tank depth");

                        } else {
                            String value = nameEditText.getText().toString();
                            int temp = Integer.parseInt(value);
                            if (temp > 0) {
                                tank_depth = temp;

                                editor_tank_data.putInt("tankDepth", tank_depth);
                                editor_tank_data.apply();
                                update_waveloading();
                            }
                        }
                        tvStatus.setText("Tank Depth: " + tank_depth + "cm");
                        //String toastString = "";

                        /*
                         * Detecting whether the checkbox is checked or not.
                         */

                        //toastString += "Name is: " + nameEditText.getText()
                        //+ "!\n";
                        //tvStatus.setText(value);

                        dialog.cancel();
                    }

                })

                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        }).show();
    }

    public void set_alarm() {
        Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
        intent.putExtra(AlarmClock.EXTRA_HOUR, 10);
        intent.putExtra(AlarmClock.EXTRA_MINUTES, 20);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.dotmenu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int temp_id = item.getItemId();
        Context context = this;
        switch (temp_id) {
            case R.id.refresh:
                subscribeClick();
                update_waveloading();
                tvStatus.setText("Tank Depth: " + tank_depth + "cm");
                break;

            case R.id.depthOption:
                set_depth();

                break;

            case R.id.alarmOption:
                Toast.makeText(this, "Setting Alarm", Toast.LENGTH_SHORT).show();
                set_alarm();
                break;

            case R.id.connectOption:
                Toast.makeText(this, "Connecting", Toast.LENGTH_SHORT).show();
                connectClick();
                //subscribeClick();
                break;

            case R.id.disconnectOption:
                Toast.makeText(this, "Disconnecting", Toast.LENGTH_SHORT).show();
                disconnectClick();
                break;

            case R.id.signOutOption:
                Toast.makeText(this, "Signing Out", Toast.LENGTH_SHORT).show();
                Signout();
                break;
        }
        return true;
    }

    private void Signout() {
        IdentityManager.getDefaultIdentityManager().signOut();
        editor_tank_data.clear();
        editor_tank_data.apply();
        startActivity(new Intent(MainActivity.this, LoginActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }

    void initIoTClient() {
        Region region = Region.getRegion(MY_REGION);

        // MQTT Client
        mqttManager = new AWSIotMqttManager(clientId, CUSTOMER_SPECIFIC_ENDPOINT);

        // Set keepalive to 10 seconds.  Will recognize disconnects more quickly but will also send
        // MQTT pings every 10 seconds.
        mqttManager.setKeepAlive(10);

        // Set Last Will and Testament for MQTT.  On an unclean disconnect (loss of connection)
        // AWS IoT will publish this message to alert other clients.
        AWSIotMqttLastWillAndTestament lwt = new AWSIotMqttLastWillAndTestament("my/lwt/topic",
                "Android client lost connection", AWSIotMqttQos.QOS0);
        mqttManager.setMqttLastWillAndTestament(lwt);

        // IoT Client (for creation of certificate if needed)
        mIotAndroidClient = new AWSIotClient(AWSMobileClient.getInstance());
        mIotAndroidClient.setRegion(region);

        keystorePath = getFilesDir().getPath();
        keystoreName = KEYSTORE_NAME;
        keystorePassword = KEYSTORE_PASSWORD;
        certificateId = CERTIFICATE_ID;

        // To load cert/key from keystore on filesystem
        try {
            if (AWSIotKeystoreHelper.isKeystorePresent(keystorePath, keystoreName)) {
                if (AWSIotKeystoreHelper.keystoreContainsAlias(certificateId, keystorePath,
                        keystoreName, keystorePassword)) {
                    Log.i(LOG_TAG, "Certificate " + certificateId
                            + " found in keystore - using for MQTT.");
                    // load keystore from file into memory to pass on connection
                    clientKeyStore = AWSIotKeystoreHelper.getIotKeystore(certificateId,
                            keystorePath, keystoreName, keystorePassword);
                    /* initIoTClient is invoked from the callback passed during AWSMobileClient initialization.
                    The callback is executed on a background thread so UI update must be moved to run on UI Thread. */
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            btnConnect.setEnabled(true);
                        }
                    });
                } else {
                    Log.i(LOG_TAG, "Key/cert " + certificateId + " not found in keystore.");
                }
            } else {
                Log.i(LOG_TAG, "Keystore " + keystorePath + "/" + keystoreName + " not found.");
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "An error occurred retrieving cert/key from keystore.", e);
        }

        if (clientKeyStore == null) {
            Log.i(LOG_TAG, "Cert/key was not found in keystore - creating new key and certificate.");

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Create a new private key and certificate. This call
                        // creates both on the server and returns them to the
                        // device.
                        CreateKeysAndCertificateRequest createKeysAndCertificateRequest =
                                new CreateKeysAndCertificateRequest();
                        createKeysAndCertificateRequest.setSetAsActive(true);
                        final CreateKeysAndCertificateResult createKeysAndCertificateResult;
                        createKeysAndCertificateResult =
                                mIotAndroidClient.createKeysAndCertificate(createKeysAndCertificateRequest);
                        Log.i(LOG_TAG,
                                "Cert ID: " +
                                        createKeysAndCertificateResult.getCertificateId() +
                                        " created.");

                        // store in keystore for use in MQTT client
                        // saved as alias "default" so a new certificate isn't
                        // generated each run of this application
                        AWSIotKeystoreHelper.saveCertificateAndPrivateKey(certificateId,
                                createKeysAndCertificateResult.getCertificatePem(),
                                createKeysAndCertificateResult.getKeyPair().getPrivateKey(),
                                keystorePath, keystoreName, keystorePassword);

                        // load keystore from file into memory to pass on
                        // connection
                        clientKeyStore = AWSIotKeystoreHelper.getIotKeystore(certificateId,
                                keystorePath, keystoreName, keystorePassword);

                        // Attach a policy to the newly created certificate.
                        // This flow assumes the policy was already created in
                        // AWS IoT and we are now just attaching it to the
                        // certificate.
                        AttachPrincipalPolicyRequest policyAttachRequest =
                                new AttachPrincipalPolicyRequest();
                        policyAttachRequest.setPolicyName(AWS_IOT_POLICY_NAME);
                        policyAttachRequest.setPrincipal(createKeysAndCertificateResult
                                .getCertificateArn());
                        mIotAndroidClient.attachPrincipalPolicy(policyAttachRequest);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                btnConnect.setEnabled(true);
                            }
                        });
                    } catch (Exception e) {
                        Log.e(LOG_TAG,
                                "Exception occurred when generating new private key and certificate.",
                                e);
                    }
                }
            }).start();
        }
    }
}
