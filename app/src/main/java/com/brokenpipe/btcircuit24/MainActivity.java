package com.brokenpipe.btcircuit24;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsoluteLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.os.Handler;
import android.util.Log;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.widget.Toast;
import android.content.ClipData;

public class MainActivity extends ActionBarActivity {

    private static final String TAG = "BTcircuit24";
    private static final boolean D = true;

    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    private static final int SETTINGS_CHANGED = 3;

    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_INFO = 4;
    public static final int MESSAGE_TOAST = 5;

    public static final int MESSAGE_POWER_CHANGE = 6;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String DEVICE_ADDRESS = "device_address";
    public static final String APAD_DELTAY = "apad_delta_y";
    public static final String APAD_DELTAX = "apad_delta_x";
    public static final String TOAST = "toast";

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothSerialService mBluetoothService = null;
    private String mConnectedDeviceName = null;
    private String mConnectedDeviceAddress = null;

    private final Handler mHandler;

    private Resources res;
    Menu menu;

    private ImageView apadButton;
    private ImageView apadBackground;
    private ImageView crossView;
    private ImageView xAxis;
    private ImageView yAxis;
    private ImageView pixelView;

    int windowwidth;
    int windowheight;
    float xAxisZero = 0;
    float yAxisZero = 0;
    int apadThumbWidth;
    int apadThumbHeight;
    int apadBackgroundWidth;
    int apadBackgroundHeight;
    int apadMaxDeltaY;
    int apadMaxDeltaX;
    int dragStartX;
    int dragStartY;
    int w,h,w2,h2;

    // Create a string for the ImageView label
    private static final String IMAGEVIEW_TAG = "icon bitmap";

    public MainActivity() {
        // The Handler that gets information back from the BluetoothChatService
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                MenuItem connectButton;

                switch (msg.what) {
                    case MESSAGE_STATE_CHANGE:
                        switch (msg.arg1) {
                            case BluetoothSerialService.STATE_CONNECTED:
                                setProgressBarIndeterminateVisibility(false);
                                setTitle(String.format(res.getString(R.string.CONNECTED), mConnectedDeviceName));
                                connectButton = menu.findItem(R.id.action_connect).setIcon(R.drawable.led_green);
                                break;
                            case BluetoothSerialService.STATE_CONNECTING:
                                setTitle(R.string.CONNECTING);
                                break;
                            case BluetoothSerialService.STATE_LISTEN:
                            case BluetoothSerialService.STATE_NONE:
                                setTitle(R.string.NOT_CONNECTED);
                                connectButton = menu.findItem(R.id.action_connect).setIcon(R.drawable.led_red);
                                break;
                        }
                        break;
                    case MESSAGE_POWER_CHANGE:
                        float deltaY = msg.getData().getFloat(APAD_DELTAY);
                        float percent = (deltaY * 100) / apadMaxDeltaY;

                        // TODO: Better algorythm to control the speed (less linear)
                        if (percent < 0) {
                         percent = 0;
                        } else if (percent > 99) {
                            percent = 99;
                        }

                        char ch = (char) (percent + 49); // ascii values sent must be between 49 and 149
                        sendAscii(Character.toString(ch));
                        break;
                    case MESSAGE_WRITE:
                        break;
                    case MESSAGE_READ:
                        break;
                    case MESSAGE_DEVICE_INFO:
                        // save the connected device's name
                        mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                        mConnectedDeviceAddress = msg.getData().getString(DEVICE_ADDRESS);
                        break;
                    case MESSAGE_TOAST:
                        Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                                Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        setTitle(R.string.NOT_CONNECTED);

        res = getResources();


        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.NO_BLUETOOTH_ADAPTER, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        apadButton = (ImageView) findViewById(R.id.apadThumb) ;
        apadBackground = (ImageView) findViewById(R.id.apadBackground) ;

        pixelView = (ImageView) findViewById(R.id.pixelView) ;

        crossView = (ImageView) findViewById(R.id.crossView) ;
        xAxis = (ImageView) findViewById(R.id.xAxis) ;
        yAxis = (ImageView) findViewById(R.id.yAxis) ;

        windowwidth = getWindowManager().getDefaultDisplay().getWidth();
        windowheight = getWindowManager().getDefaultDisplay().getHeight();

        //SurfaceView sView = (SurfaceView) findViewById(R.id.surfaceView);

        apadThumbWidth = apadButton.getLayoutParams().width;
        apadThumbHeight = apadButton.getLayoutParams().height;

        apadBackgroundWidth = apadBackground.getLayoutParams().width;
        apadBackgroundHeight = apadBackground.getLayoutParams().height;


        apadMaxDeltaX = apadBackgroundWidth / 2 - 15;
        apadMaxDeltaY = apadBackgroundHeight / 2 - 15;

        w = crossView.getLayoutParams().width;
        h = crossView.getLayoutParams().height;

        w2 = xAxis.getLayoutParams().width;
        h2 = yAxis.getLayoutParams().height;


        Log.v(TAG, String.valueOf(w));
        Log.v(TAG, String.valueOf(h));

        apadButton.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Message msg;
                Bundle bundle;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (xAxisZero == 0 && yAxisZero ==0) {
                            xAxisZero = apadButton.getX() + apadThumbWidth / 2;
                            yAxisZero = apadButton.getY() + apadThumbHeight / 2;


                            Log.v(TAG, "Xzero " + String.valueOf(xAxisZero));
                            Log.v(TAG, "Yzero " + String.valueOf(yAxisZero));

                            pixelView.setX(xAxisZero);
                            pixelView.setY(yAxisZero);

                            xAxis.setX(xAxisZero);
                            xAxis.setY(yAxisZero);
                            yAxis.setX(xAxisZero);
                            yAxis.setY(yAxisZero);

                            crossView.setX(xAxisZero - w / 2);
                            crossView.setY(yAxisZero - h / 2);

                        }

                        dragStartX = (int) event.getRawX();
                        dragStartY = (int) event.getRawY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float ptx = event.getRawX();
                        float pty = event.getRawY();

                        int deltaX = dragStartX - (int) ptx;
                        int deltaY = dragStartY - (int) pty;

                        Log.v(TAG, String.valueOf(xAxisZero) + "/" + String.valueOf(ptx) + " , " +  String.valueOf(yAxisZero) + "/" + String.valueOf(pty));

                        if (!inCircle(xAxisZero, yAxisZero, apadMaxDeltaY, xAxisZero - deltaX, yAxisZero - deltaY )) {
                            Point pt = getPointOnTheCircle(xAxisZero, yAxisZero, apadMaxDeltaY, ptx, pty);
                            //Log.v(TAG, "Out");
                            //deltaX = dragStartX - pt.x;
                            //deltaY = dragStartY - pt.y;

                            crossView.setX(pt.x - w / 2);
                            crossView.setY(pt.y - h / 2);
                            //deltaX = 0;
                            //deltaY = 0;
                        } else {
                            crossView.setX(xAxisZero - deltaX - w / 2);
                            crossView.setY(yAxisZero - deltaY - h / 2);
                        }

                        xAxis.setX(xAxisZero - deltaX);
                        yAxis.setY(yAxisZero - deltaY);

                        apadButton.setX(xAxisZero - deltaX - apadThumbWidth / 2);
                        apadButton.setY(yAxisZero - deltaY - apadThumbHeight / 2);

                        // Send message to the activity to update the motor progress
                        msg = mHandler.obtainMessage(MainActivity.MESSAGE_POWER_CHANGE);
                        bundle = new Bundle();
                        bundle.putFloat(MainActivity.APAD_DELTAY, deltaY);
                        bundle.putFloat(MainActivity.APAD_DELTAX, deltaX);
                        msg.setData(bundle);
                        mHandler.sendMessage(msg);
                        break;
                    case MotionEvent.ACTION_UP:
                        // Reset the dpap position
                        apadButton.setY(yAxisZero - apadThumbWidth / 2 );
                        apadButton.setX(xAxisZero - apadThumbHeight / 2);

                        xAxis.setX(xAxisZero);
                        xAxis.setY(yAxisZero);

                        yAxis.setX(xAxisZero);
                        yAxis.setY(yAxisZero);

                        crossView.setX(xAxisZero - w / 2);
                        crossView.setY(yAxisZero - h / 2);


                        msg = mHandler.obtainMessage(MainActivity.MESSAGE_POWER_CHANGE);
                        bundle = new Bundle();
                        bundle.putFloat(MainActivity.APAD_DELTAY, 0);
                        bundle.putFloat(MainActivity.APAD_DELTAX, 0);
                        msg.setData(bundle);
                        mHandler.sendMessage(msg);
                        break;
                    default:
                        break;
                }
                return true;
            }
        });

        // Get boost button from ressource id
        ImageButton boostButton = (ImageButton) findViewById(R.id.boostButton);

        // Assign click handler
        boostButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
            }

        });
    }

    @Override
    public void onStart() {
        super.onStart();

        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            mBluetoothService = new BluetoothSerialService(this, mHandler);
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();

        Log.v(TAG, "onResume");
        if (mBluetoothService != null) {

            if (mBluetoothService.getState() == BluetoothSerialService.STATE_NONE) {
                Log.v(TAG, "STATE_NONE");

                if (mConnectedDeviceAddress != null && mConnectedDeviceName != null) {
                    connectToDevice(mConnectedDeviceAddress);
                } else {
                    Log.v(TAG, "No Reconnection Info");
                }

            } else if (mBluetoothService.getState() == BluetoothSerialService.STATE_CONNECTING) {
                Log.v(TAG, "STATE_CONNECTING");
            } else if (mBluetoothService.getState() == BluetoothSerialService.STATE_CONNECTED) {
                Log.v(TAG, "STATE_CONNECTED");
            }
        }

    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        Log.v(TAG, "onPause");
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.v(TAG, "onStop");
        if (mBluetoothService != null) mBluetoothService.stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy");
        if (mBluetoothService != null) mBluetoothService.stop();
    }


    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    // Get the device MAC address
                    String address = data.getExtras()
                            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    // Get the BLuetoothDevice object
                    connectToDevice(address);
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
                break;
            case SETTINGS_CHANGED:
                // refresh settings
        }
    }

    private void connectToDevice(String address) {
        if (mBluetoothService != null && mBluetoothAdapter != null) {
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
            // Attempt to connect to the device
            mBluetoothService.connect(device);
        }
    }

    /**
     * Sends a message.
     * @param message  A string of text to send.
     */
    private void sendAscii(String message) {
        // Check that we're actually connected before trying anything
        if (mBluetoothService.getState() != BluetoothSerialService.STATE_CONNECTED) {
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mBluetoothService.write(send);
        }
    }

    public boolean inCircle(float center_x, float center_y, float radius, float x, float y) {
        double square_dist = Math.pow(center_x - x,2) + Math.pow(center_y - y, 2);
        return square_dist <= Math.pow(radius, 2);
    }

    public Point getPointOnTheCircle(float cx, float cy, float r, float ptx, float pty) {
        double x = 0, y = 0;
        double a = 0;
        double ntd = Math.toRadians(90);

       //if (ptx < cx && pty < cy) {

            //h = Math.sqrt( (double) (Math.pow(ptx-cx, 2) + Math.pow(pty-cy, 2)));
            //sina = (cy-pty) / h;
            //cosa = (cx-ptx) / h;
        if (ptx == cx && pty > cy) {
            x = cx;
            y = cy + r;
        } else if (pty == cy && ptx > cx) {
            x = cx + r;
            y = cy;
        } else if (ptx == cx && pty < cy) {
            x = cx;
            y = cy - r;
        } else if (pty == cy && ptx < cx) {
            x = cx - r;
            y = cy;
        } else if (ptx < cx && pty > cy) {
            a = Math.atan2(pty - cy , cx - ptx);
            x = cx - r * Math.cos(a);
            y = cy + r * Math.sin(a);
             Log.v(TAG, "Q " + String.valueOf(cx) + " / " + String.valueOf(x) + " : " + String.valueOf(cy) + " / " + String.valueOf(y));
        } else if (ptx > cx && pty > cy) {
            a = Math.atan2(pty - cy , ptx - cx);
            x = cx + r * Math.cos(a);
            y = cy + r * Math.sin(a);
            Log.v(TAG, "Q1 "+ String.valueOf(Math.toDegrees(a)));
        } else if (ptx > cx && pty < cy) {
            a = ntd + Math.atan2(Math.abs(cy - pty) , Math.abs(ptx - cx));
            x = cx + r * Math.cos(a);
            y = cy + r * Math.sin(a);
            Log.v(TAG, "Q "+ String.valueOf(Math.toDegrees(a)));
        } else if (ptx < cx && pty < cy) {
            a = ntd + Math.atan2(Math.abs(cy - pty) , Math.abs(ptx - cx));
            x = cx + r * Math.cos(a);
            y = cy + r * Math.sin(a);
            Log.v(TAG, "Q "+ String.valueOf(Math.toDegrees(a)));
        }

        //xx
        //a += Math.atan2(Math.abs(cy - pty) , Math.abs(ptx - cx));



        //Log.v(TAG, "a = " + String.valueOf(Math.toDegrees(a)));

        //Log.v(TAG, "SIN(a) " + String.valueOf(sina));
        //Log.v(TAG, "COS(a) " + String.valueOf(cosa));
        /*Log.v(TAG, "x " + x);
        Log.v(TAG, "y " + y);*/

        return new Point((int) Math.round(x), (int)Math.round(y));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        this.menu = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        Intent serverIntent;

        switch (id) {

            case R.id.action_settings :
                // Launch the settings activity
                serverIntent = new Intent(this, SettingsActivity.class);
                startActivityForResult(serverIntent, 0);
                return true;
            case R.id.action_connect :
                if (mBluetoothService != null) {
                    // If not connected to a remote, then launch the DeviceListActivity to see devices and do scan
                    if (mBluetoothService.getState() != BluetoothSerialService.STATE_CONNECTED) {
                        setProgressBarIndeterminateVisibility(true); // not working
                        serverIntent = new Intent(this, DeviceListActivity.class);
                        startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
                    } else {
                        // Otherwise close the existing connection
                        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mConnectedDeviceAddress);
                        mBluetoothService.stop();
                    }
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}

