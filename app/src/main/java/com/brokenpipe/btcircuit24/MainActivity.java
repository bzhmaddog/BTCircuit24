package com.brokenpipe.btcircuit24;

//import android.bluetooth.*;
import android.app.Activity;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.os.Handler;
import android.util.Log;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public class MainActivity extends ActionBarActivity {

    private static final String TAG = "BTcircuit24";
    private static final boolean D = true;

    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    public static final int MESSAGE_POWER_CHANGE = 6;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothSerialService mBluetoothService = null;
    private String mConnectedDeviceName = null;

    private SeekBar powerBar;
    private int progress = 0;
    private int lastProgress = 0;
    private int currentTime = 0;
    private final Handler rHandler = new Handler();

    private TextView mTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Get seekBar component
        powerBar = (SeekBar) findViewById(R.id.powerBar);

        // set the value to 0
        powerBar.setProgress(0);
        powerBar.setMax(99);

        // Establish a listener
        powerBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progressValue, boolean fromUser) {
                progress = progressValue;

                char ch = (char) (progress + 49); // ascii values sent must be between 49 and 149

                Log.v(TAG, "ascii : " + (progress +49) + " / " + ch);

                sendMessage(Character.toString(ch));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                rHandler.removeCallbacks(decreasePower);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                sendMessage("0"); //48

                lastProgress = progress;
                //rHandler.postDelayed(decreasePower, 50);
                progress = 0;
                powerBar.setProgress(0);
            }
        });

        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            //mBluetoothService = new BluetoothSerialService(this, mHandler);
        }
        mBluetoothService = new BluetoothSerialService(this, mHandler);

    }

    @Override
    public void onStart() {
        super.onStart();

    }

    // The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothSerialService.STATE_CONNECTED:
                            Toast.makeText(getApplicationContext(), "Connected To" + mConnectedDeviceName,
                                    Toast.LENGTH_SHORT).show();
                            break;
                        case BluetoothSerialService.STATE_CONNECTING:
                            Toast.makeText(getApplicationContext(), "Connecting",
                                    Toast.LENGTH_SHORT).show();
                            break;
                        case BluetoothSerialService.STATE_LISTEN:
                        case BluetoothSerialService.STATE_NONE:
                            //mTitle.setText("Not Connected");
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    break;
                case MESSAGE_READ:
                    break;
                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    // Get the device MAC address
                    String address = data.getExtras()
                            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    // Get the BLuetoothDevice object
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                    // Attempt to connect to the device
                    mBluetoothService.connect(device);
                    Toast.makeText(getApplicationContext(),device.getAddress(),
                            Toast.LENGTH_SHORT).show();
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    //setupChat();
                } else {
                    // User did not enable Bluetooth or an error occured
                    Toast.makeText(this, "Leaving", Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }

    /**
     * Sends a message.
     * @param message  A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mBluetoothService.getState() != BluetoothSerialService.STATE_CONNECTED) {
            Toast.makeText(this, "You are not connected to the device", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mBluetoothService.write(send);
        }
    }

    private double ease(int t, int b, int c, int d) {
        Log.v(TAG, "Ease = " + String.valueOf(t) + ' ' + String.valueOf(b) + ' ' + String.valueOf(c) + ' ' + String.valueOf(d));
        //double v = 1.70158;
        //return Math.round(c * ((t = t / d - 1) * t * ((v + 1) * t + v) + 1) + b);
        return c * (Math.pow(t / d - 1, 3) + 1) + b;
    }

    private Runnable decreasePower = new Runnable() {
        public void run() {
            progress = (int) Math.floor(lastProgress + (-lastProgress * ease(currentTime, 0 , 1, 100)));
            powerBar.setProgress(progress);
            currentTime++;
            Log.v("BTCircuit24", "CurrentTime = " + String.valueOf(currentTime));
            if (progress > 0 && currentTime < 100) {
                rHandler.postDelayed(this, 1);
            }
            //textView.setText("Covered: " + progress + "/" + powerBar.getMax());
        }
    };


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
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            // Launch the DeviceListActivity to see devices and do scan
            Intent serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}

