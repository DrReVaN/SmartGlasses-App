package com.test;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import java.util.ArrayList;
import java.util.Collections;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private volatile int mConnectionState = STATE_DISCONNECTED;


    private final Handler eventLoop = new Handler(Looper.getMainLooper());
    private boolean servicesReady;
    public static final String ACTION_TRANSFER_FAILED = "com.test.TRANSFER_FAILED";
    private final GattQueue transfer = new GattQueue(new GattQueue.Driver() {
        public long now() { return SystemClock.elapsedRealtime(); }
        public boolean ready() {
            return servicesReady && mBluetoothGatt != null &&
                mBluetoothGatt.getDevice().getBondState() == BluetoothDevice.BOND_BONDED;
        }
        public boolean start(GattQueue.Operation operation) {
            if (mBluetoothGatt == null) return false;
            for (BluetoothGattService service : mBluetoothGatt.getServices()) {
                BluetoothGattCharacteristic c = service.getCharacteristic(operation.uuid);
                if (c != null) {
                    if (operation.data == null) return mBluetoothGatt.readCharacteristic(c);
                    c.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                    c.setValue(operation.data.clone());
                    return mBluetoothGatt.writeCharacteristic(c);
                }
            }
            return false;
        }
        public void later(Runnable task, long delay) { eventLoop.postDelayed(task, delay); }
        public void failed(String reason) {
            Log.w(TAG, reason); servicesReady = false;
            Intent failure = new Intent(ACTION_TRANSFER_FAILED);
            failure.putExtra(EXTRA_DATA, reason); sendBroadcast(failure);
            if (mBluetoothGatt != null) mBluetoothGatt.disconnect();
        }
    });
    private final BroadcastReceiver bondReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (mBluetoothGatt == null || device == null ||
                !device.getAddress().equals(mBluetoothGatt.getDevice().getAddress())) return;
            if (device.getBondState() == BluetoothDevice.BOND_BONDED) transfer.wake();
            else if (device.getBondState() == BluetoothDevice.BOND_NONE) {
                transfer.clear(); servicesReady = false;
                broadcastUpdate(ACTION_TRANSFER_FAILED);
            }
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        registerReceiver(bondReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
    }
    @Override public void onDestroy() {
        close(); unregisterReceiver(bondReceiver); eventLoop.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
    public boolean enqueuePackets(BluetoothGattCharacteristic characteristic, List<byte[]> packets) {
        if (characteristic == null || packets.isEmpty() || mConnectionState != STATE_CONNECTED) return false;
        ArrayList<GattQueue.Operation> batch = new ArrayList<>();
        for (byte[] packet : packets) batch.add(new GattQueue.Operation(characteristic.getUuid(), packet));
        if (Looper.myLooper() != Looper.getMainLooper()) {
            eventLoop.post(() -> { if (!transfer.enqueue(batch)) broadcastUpdate(ACTION_TRANSFER_FAILED); });
            return true;
        }
        return transfer.enqueue(batch);
    }
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";

    public final static UUID UUID_HEART_RATE_MEASUREMENT =
            UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            eventLoop.post(() -> {
                if (gatt != mBluetoothGatt) return;
                servicesReady = false; transfer.clear();
                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    mConnectionState = STATE_CONNECTED; broadcastUpdate(ACTION_GATT_CONNECTED);
                    if (!gatt.discoverServices()) gatt.disconnect();
                } else {
                    mConnectionState = STATE_DISCONNECTED; broadcastUpdate(ACTION_GATT_DISCONNECTED);
                }
            });
        }
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            eventLoop.post(() -> {
                if (gatt != mBluetoothGatt) return;
                if (status != BluetoothGatt.GATT_SUCCESS) { gatt.disconnect(); return; }
                servicesReady = true;
                BluetoothDevice device = gatt.getDevice();
                if (device.getBondState() == BluetoothDevice.BOND_NONE && !device.createBond()) {
                    servicesReady = false; broadcastUpdate(ACTION_TRANSFER_FAILED); gatt.disconnect(); return;
                }
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED); transfer.wake();
            });
        }
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic c, int status) {
            eventLoop.post(() -> {
                if (gatt == mBluetoothGatt) transfer.complete(c.getUuid(), status == BluetoothGatt.GATT_SUCCESS);
            });
        }
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic c, int status) {
            eventLoop.post(() -> {
                if (gatt != mBluetoothGatt) return;
                if (status == BluetoothGatt.GATT_SUCCESS) broadcastUpdate(ACTION_DATA_AVAILABLE, c);
                transfer.complete(c.getUuid(), status == BluetoothGatt.GATT_SUCCESS);
            });
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        // This is special handling for the Heart Rate Measurement profile.  Data parsing is
        // carried out as per profile specifications:
        // http://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            int flag = characteristic.getProperties();
            int format = -1;
            if ((flag & 0x01) != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
                Log.d(TAG, "Heart rate format UINT16.");
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
                Log.d(TAG, "Heart rate format UINT8.");
            }
            final int heartRate = characteristic.getIntValue(format, 1);
            Log.d(TAG, String.format("Received heart rate: %d", heartRate));
            intent.putExtra(EXTRA_DATA, String.valueOf(heartRate));
        } else {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
            }
        }
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        servicesReady = false; transfer.clear();
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        servicesReady = false; transfer.clear();
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        transfer.enqueue(Collections.singletonList(new GattQueue.Operation(characteristic.getUuid(), null)));
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // This is specific to Heart Rate Measurement.
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }
    public void readCustomCharacteristic() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        /*check if the service is available on the device*/
        BluetoothGattService mCustomService = mBluetoothGatt.getService(UUID.fromString("00001110-0000-1000-8000-00805f9b34fb"));
        if(mCustomService == null){
            Log.w(TAG, "Custom BLE Service not found");
            Toast.makeText(this, "Custom BLE Service not found", Toast.LENGTH_SHORT).show();
            return;
        }
        /*get the read characteristic from the service*/
        BluetoothGattCharacteristic mReadCharacteristic = mCustomService.getCharacteristic(UUID.fromString("00000002-0000-1000-8000-00805f9b34fb"));
        if(mReadCharacteristic == null){
            Log.w(TAG, "Failed to read characteristic");
            Toast.makeText(this, "Failed to read characteristic", Toast.LENGTH_SHORT).show();
        } else { readCharacteristic(mReadCharacteristic); }
    }

    public void writeCustomCharacteristic(int value) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        /*check if the service is available on the device*/
        BluetoothGattService mCustomService = mBluetoothGatt.getService(UUID.fromString("00001110-0000-1000-8000-00805f9b34fb"));
        if(mCustomService == null){
            Log.w(TAG, "Custom BLE Service not found");
            Toast.makeText(this, "Custom BLE Service not found", Toast.LENGTH_SHORT).show();
            return;
        }
        /*get the read characteristic from the service*/
        BluetoothGattCharacteristic mWriteCharacteristic = mCustomService.getCharacteristic(UUID.fromString("00000001-0000-1000-8000-00805f9b34fb"));
        mWriteCharacteristic.setValue(value, BluetoothGattCharacteristic.FORMAT_UINT32,0);
        if(!enqueuePackets(mWriteCharacteristic, Collections.singletonList(new byte[] {(byte)value,0,0,0}))){
            Toast.makeText(this, "Failed to write characteristic", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Failed to write characteristic");
        }
    }

    public BluetoothGatt getmBluetoothGatt(){
        return mBluetoothGatt;
    }
}
