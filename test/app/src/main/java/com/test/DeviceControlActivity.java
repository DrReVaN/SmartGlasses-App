
package com.test;

import android.app.Activity;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.app.AlertDialog;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";



    private Handler clockHandler = new Handler();
    private BluetoothGattCharacteristic myPushCharacteristic;
    private BluetoothGattCharacteristic myTimeCharacteristic;
    private BluetoothGattCharacteristic myCharacteristic;
    private String tempTime = "7000";
    private String notifyTextOld;
    private Button button1;
    private Button button2;
    private EditText editText;
    private String notifyInfoText;

    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private NotifyBroadcastReceiver mNotifyBroadcastReceiver;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                System.out.println(BluetoothLeService.EXTRA_DATA.getClass().getName()+"");
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (mGattCharacteristics != null) {
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);

                        myCharacteristic = characteristic;

                        final int charaProp = characteristic.getProperties();
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
                                mBluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    characteristic, true);
                        }
                        return true;
                    }
                    return false;
                }
    };

    private void clearUI() {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mDataField.setText(R.string.no_data);
    }




    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_controll);

        final Intent intent = getIntent();


        mNotifyBroadcastReceiver = new NotifyBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.test.notifyListener");
        registerReceiver(mNotifyBroadcastReceiver,intentFilter);

        notifyTextOld = notifyInfoText;

        clockHandler.post(runnable);

        button1=(Button)findViewById(R.id.button);
        button2=(Button)findViewById(R.id.button2);
        editText=(EditText)findViewById(R.id.editText);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeCharacteristic(myCharacteristic, editText.getText().toString());
            }
        });


        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mBluetoothLeService != null) {
                    mBluetoothLeService.readCustomCharacteristic();
                }
            }
        });

        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

         // Sets up UI references.
//        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
        mGattServicesList.setOnChildClickListener(servicesListClickListner);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.editText1);

        mDataField.setText(mDeviceName);
        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
        unregisterReceiver(mNotifyBroadcastReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            String[] parts=data.split("\n");
            System.out.println(parts[0]);
            mDataField.setText(parts[0]);
        }
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            Log.v("Service",gattService.getUuid().toString()+" instance id"+ gattService.getInstanceId());
            Log.v("Service",gattService.getUuid().toString()+" type"+ gattService.getType());
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                if(myTimeCharacteristic == null && uuid.startsWith("00000021")) {
                    myTimeCharacteristic = gattCharacteristic;
                }
                if(myPushCharacteristic == null && uuid.startsWith("00000022")) {
                    myPushCharacteristic = gattCharacteristic;
                }
                Log.v("Characteristics",gattCharacteristic.getUuid().toString()+" write type"+ gattCharacteristic.getWriteType());
                Log.v("Characteristics",gattCharacteristic.getUuid().toString()+" properties"+ gattCharacteristic.getProperties());
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 },
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 }
        );
        mGattServicesList.setAdapter(gattServiceAdapter);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    //Threat that handles the Clock and Date Update
    final private Runnable runnable = new Runnable() {
        @Override
        public void run() {


            //Create Time String
            String currentTime = new SimpleDateFormat("HHmmss", Locale.getDefault()).format(new Date());
            //Create Date String
            String currentDate = new SimpleDateFormat("ddMMyyyy", Locale.getDefault()).format(new Date());

            String sendStringTimeDate = currentTime.substring(0,4) + currentDate.substring(0,4);
            if(myTimeCharacteristic != null && mConnected) {
                if(tempTime.charAt(5) == '5' || tempTime.charAt(5) == '0') {
                    writeTimeCharacteristic(myTimeCharacteristic, sendStringTimeDate);
                }
            }
            tempTime = currentTime;


            // Repeat every 2 seconds
            clockHandler.postDelayed(runnable, 1000);

        }
    };

    public boolean writeCharacteristic(BluetoothGattCharacteristic charac, String value){

        //check mBluetoothGatt is available
        BluetoothGatt mBluetoothGatt = mBluetoothLeService.getmBluetoothGatt();
        if (mBluetoothGatt == null) {
            Log.e(TAG, "lost connection");
            return false;
        }

        if (charac == null) {
            Log.e(TAG, "char not found!");
            return false;
        }

        byte[] byteString = value.getBytes(StandardCharsets.UTF_8);

        if(byteString.length<19) {
            byte[] tempStringByte = new byte[byteString.length+2];
            System.arraycopy(byteString, 0, tempStringByte, 2, byteString.length);
            tempStringByte[0] = 0x00;
            tempStringByte[1] = 0x01;
            charac.setValue(tempStringByte);
            return mBluetoothGatt.writeCharacteristic(charac);
        } else {
            byte[][] splitData = ArrayChunk(byteString, 18);
            boolean success = false;
            for(int i=0;i<splitData.length;i++) {
                byte[] tempStringByte = new byte[20];
                System.arraycopy(splitData[i], 0, tempStringByte, 2, splitData[i].length);
                tempStringByte[0] = (byte) i;
                tempStringByte[1] = (byte) splitData.length;
                success = false;
                charac.setValue(tempStringByte);
                while(!success) {
                    success = mBluetoothGatt.writeCharacteristic(charac);
                }

            }
            return success;
        }
    }

    public boolean writeTimeCharacteristic(BluetoothGattCharacteristic charac, String value){

        //check mBluetoothGatt is available
        BluetoothGatt mBluetoothGatt = mBluetoothLeService.getmBluetoothGatt();
        if (mBluetoothGatt == null) {
            Log.e(TAG, "lost connection");
            return false;
        }

        if (charac == null) {
            Log.e(TAG, "char not found!");
            return false;
        }

        charac.setValue(value);
        return mBluetoothGatt.writeCharacteristic(charac);
    }

    public static byte[][] ArrayChunk(byte[] array, int chunkSize) {
        int numOfChunks = (int) Math.ceil((double) array.length / chunkSize);
        byte[][] output = new byte[numOfChunks][];

        for (int i = 0; i < numOfChunks; i++) {
            int start = i * chunkSize;
            int length = Math.min(array.length - start, chunkSize);

            byte[] temp = new byte[length];
            System.arraycopy(array, start, temp, 0, length);
            output[i] = temp;
        }

        //
        return output;
    }

    public class NotifyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent notifyIntent) {
            notifyInfoText = notifyIntent.getStringExtra("Notification Info");
            writeCharacteristic(myPushCharacteristic, notifyInfoText.substring(0, Math.min(notifyInfoText.length(), 60)));
        }
    }


}
