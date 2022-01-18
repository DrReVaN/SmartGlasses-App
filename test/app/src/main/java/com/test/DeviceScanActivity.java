package com.test;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.test.Circles.CircleDistanceEquation;
import com.test.Circles.TwoCircleIntercestPoints;

import java.nio.Buffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;


/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
public class DeviceScanActivity extends ListActivity {
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private AlertDialog enableNotificationListenerAlertDialog;

    private boolean mScanning;
    private Handler mHandler;

    private static final int REQUEST_ENABLE_BT = 1;
    private double[] first_coordinate = new double[]{0,0};
    private double[] second_coordinate = new double[]{4,4};
    private double[] third_coordinate = new double[]{8,0};
    private double[] first_point = new double[2];
    private double[] second_point = new double[2];
    private double[] third_point = new double[2];
    private double[][] Last_point = new double[2][2];

    private static final String ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners";
    private static final String ACTION_NOTIFICATION_LISTENER_SETTINGS = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS";

    private double firstRange;
    private double secondRange;
    private double thirdRange;
    private HashMap<String, Double> calculatedDistance = new HashMap<String, Double>();
    private HashMap<String, Double> averageRssi = new HashMap<String, Double>();
    private HashMap<String, Double> currentRssi = new HashMap<String, Double>();
    private CircleDistanceEquation a;
    private CircleDistanceEquation b;
    private CircleDistanceEquation c;

    private HashMap<String,double[]> rssiBuffer =new HashMap<String,double[]>();





    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000000;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);
        getActionBar().setTitle(R.string.title_devices);

        if(!isNotificationServiceEnabled()){
            enableNotificationListenerAlertDialog = buildNotificationServiceAlertDialog();
            enableNotificationListenerAlertDialog.show();
        }

        try {
            mHandler = new Handler();


            Timer timer = new Timer();
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Last_point[0][0] = Last_point[1][0];
                            Last_point[0][1] = Last_point[1][1];
                            mLeDeviceListAdapter.notifyDataSetChanged();
                        }
                    });
                }
            }, 10000, 10000);
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Bitmap mutableBitmap;
                            Paint paint = new Paint();
                            BitmapFactory.Options myOptions = new BitmapFactory.Options();
                            myOptions.inDither = true;
                            myOptions.inScaled = false;
                            myOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;// important
                            myOptions.inPurgeable = true;
                            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.grid5, myOptions);
                            paint.setAntiAlias(true);
                            paint.setColor(Color.BLUE);
                            Bitmap workingBitmap = Bitmap.createBitmap(bitmap);
                            mutableBitmap = workingBitmap.copy(Bitmap.Config.ARGB_8888, true);
                            ImageView imageView = (ImageView) findViewById(R.id.grid);
                            imageView.setAdjustViewBounds(true);
                            imageView.setImageBitmap(mutableBitmap);
                            Canvas canvas = new Canvas(mutableBitmap);
                            Last_point[0][0] += (Last_point[1][0] - Last_point[0][0]) / 10;
                            Last_point[0][1] += (Last_point[1][1] - Last_point[0][1]) / 10;
                            canvas.drawCircle((float) Last_point[0][0] * 125, 1000 - (float) Last_point[0][1] * 125, 25, paint);
                        }
                    });
                }
            }, 200, 200);


            // Use this check to determine whether BLE is supported on the device.  Then you can
            // selectively disable BLE-related features.
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
                finish();
            }

            // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
            // BluetoothAdapter through BluetoothManager.
            final BluetoothManager bluetoothManager =
                    (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = bluetoothManager.getAdapter();

            // Checks if Bluetooth is supported on the device.
            if (mBluetoothAdapter == null) {
                Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }


        }catch (Exception e){

        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.actionbar_indeterminate_progress);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                mLeDeviceListAdapter.clear();
                scanLeDevice(true);
                break;
            case R.id.menu_stop:
                scanLeDevice(false);
                break;
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }

        // Initializes list view adapter.
        mLeDeviceListAdapter = new LeDeviceListAdapter();
        setListAdapter( mLeDeviceListAdapter);
        scanLeDevice(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
        mLeDeviceListAdapter.clear();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        scanLeDevice(false);
        final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
        if (device == null) return;
        final Intent intent = new Intent(this, DeviceControlActivity.class);
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.getName());
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
        if (mScanning) {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            mScanning = false;
        }
        startActivity(intent);
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }

    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflator;
        private TextView twLocation;

        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<BluetoothDevice>();
            mInflator = DeviceScanActivity.this.getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {
            if(!mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }


        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }


        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.



            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceDistance = (TextView) view.findViewById(R.id.tw_of_distance);
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                twLocation=(TextView)findViewById(R.id.tw_location);

                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText(R.string.unknown_device);
                viewHolder.deviceAddress.setText("Address:      "+device.getAddress());




            // Circles:
    try {
        firstRange=(calculatedDistance.get("24:71:89:1D:B7:C3") == null) ? 0 : calculatedDistance.get("24:71:89:1D:B7:C3") <= 2 ? calculatedDistance.get("24:71:89:1D:B7:C3") : ((calculatedDistance.get("24:71:89:1D:B7:C3") > 2 && calculatedDistance.get("24:71:89:1D:B7:C3") <= 5) ? (calculatedDistance.get("24:71:89:1D:B7:C3")+4)/2 : 7 );
        secondRange=(calculatedDistance.get("24:71:89:1D:B4:FB") == null) ? 0 : calculatedDistance.get("24:71:89:1D:B4:FB") <= 2 ? calculatedDistance.get("24:71:89:1D:B4:FB") : ((calculatedDistance.get("24:71:89:1D:B4:FB") > 2 && calculatedDistance.get("24:71:89:1D:B4:FB") <= 5) ? (calculatedDistance.get("24:71:89:1D:B4:FB")+4)/2 : 7 );
        thirdRange=(calculatedDistance.get("24:71:89:1D:B2:C6") == null) ? 0 : calculatedDistance.get("24:71:89:1D:B2:C6") <= 2 ? calculatedDistance.get("24:71:89:1D:B2:C6") : ((calculatedDistance.get("24:71:89:1D:B2:C6") > 2 && calculatedDistance.get("24:71:89:1D:B2:C6") <= 5) ? (calculatedDistance.get("24:71:89:1D:B2:C6")+4)/2 : 7 );
            while (firstRange+secondRange < 8)  {firstRange++; secondRange++;}
            while (firstRange+thirdRange < 8)  {firstRange++; thirdRange++;}
            while (secondRange+thirdRange < 8)  {thirdRange++; secondRange++;}

                a = new CircleDistanceEquation(firstRange, first_coordinate[0], first_coordinate[1]);
                b = new CircleDistanceEquation(secondRange, second_coordinate[0],second_coordinate[1]);
                c = new CircleDistanceEquation(thirdRange, third_coordinate[0], third_coordinate[1]);

                    TwoCircleIntercestPoints firstEqu = new TwoCircleIntercestPoints(a, b);
                    firstEqu.compute();
                    TwoCircleIntercestPoints secondEqu = new TwoCircleIntercestPoints(a, c);
                    secondEqu.compute();
                    TwoCircleIntercestPoints thirdEqu = new TwoCircleIntercestPoints(b, c);
                    thirdEqu.compute();
                boolean isFar=true;
                if(calculatedDistance.get("24:71:89:1D:B7:C3")<1) {
                    Last_point[1][0] = first_coordinate[0] ; Last_point[1][1] = first_coordinate[1];
                    isFar = false;
                }
                    else if(calculatedDistance.get("24:71:89:1D:B4:FB")<1){
                    Last_point[1][0] = second_coordinate[0]; Last_point[1][1] = second_coordinate[1];
                    isFar=false;
                }
                        else if(calculatedDistance.get("24:71:89:1D:B2:C6")<1){
                    Last_point[1][0] = third_coordinate[0]; Last_point[1][1] = third_coordinate[1];
                    isFar=false;
                }


        if(isFar){

                    Last_point[1][0] = (firstEqu.x[0] + firstEqu.x[1] + secondEqu.x[0] + secondEqu.x[1] + thirdEqu.x[0] + thirdEqu.x[1]) / 6;
                    Last_point[1][1] = (firstEqu.y[0] + firstEqu.y[1] + secondEqu.y[0] + secondEqu.y[1] + thirdEqu.y[0] + thirdEqu.y[1]) / 6;
                }

                    twLocation.setText("Location  X: " + Math.floor(100 * Last_point[1][0]) / 100 + "  Y: " + Math.floor(Last_point[1][1] * 100) / 100);

    }catch (NullPointerException e){
            System.out.println("HAHA got u!");
            }
            viewHolder.deviceDistance.setText("Distance(m) : " + (Math.floor(100 * calculatedDistance.get(device.getAddress())) / 100) + "            RSSI: " + currentRssi.get(device.getAddress()) );
            return view;
        }


    }


    private AlertDialog buildNotificationServiceAlertDialog(){
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setTitle(R.string.notification_listener_service);
        alertDialogBuilder.setMessage(R.string.notification_listener_service_explanation);
        alertDialogBuilder.setPositiveButton(R.string.yes,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        startActivity(new Intent(ACTION_NOTIFICATION_LISTENER_SETTINGS));
                    }
                });
        alertDialogBuilder.setNegativeButton(R.string.no,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // If you choose to not enable the notification listener
                        // the app. will not work as expected
                    }
                });
        return(alertDialogBuilder.create());
    }


    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

                @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {

            runOnUiThread(new Runnable() {
                @Override
                public void run() {


                        if(rssiBuffer.get(device.getAddress())==null){
                            rssiBuffer.put(device.getAddress(),new double[40]);

                        }else {
                            currentRssi.put(device.getAddress(),(double)rssi);
                                for (int i = 1; i < rssiBuffer.get(device.getAddress()).length ; i++)
                                    rssiBuffer.get(device.getAddress())[i - 1] = rssiBuffer.get(device.getAddress())[i];


                                rssiBuffer.get(device.getAddress())[rssiBuffer.get(device.getAddress()).length-1]=  rssi;
                            //////Filtering:
                        if(rssiBuffer.get(device.getAddress())[rssiBuffer.get(device.getAddress()).length-1]- rssiBuffer.get(device.getAddress())[rssiBuffer.get(device.getAddress()).length-2] < rssiBuffer.get(device.getAddress())[rssiBuffer.get(device.getAddress()).length-2]*0.03
                                || rssiBuffer.get(device.getAddress())[rssiBuffer.get(device.getAddress()).length-2]- rssiBuffer.get(device.getAddress())[rssiBuffer.get(device.getAddress()).length-1] < rssiBuffer.get(device.getAddress())[rssiBuffer.get(device.getAddress()).length-2]*0.03){
                            rssiBuffer.get(device.getAddress())[rssiBuffer.get(device.getAddress()).length-1] = rssiBuffer.get(device.getAddress())[rssiBuffer.get(device.getAddress()).length-2];
                        }
                                    boolean isComeBack = true;
                                    for(int k = 2 ; k < 10 ; k++) {
                                        if (rssiBuffer.get(device.getAddress())[rssiBuffer.get(device.getAddress()).length - 1] != rssiBuffer.get(device.getAddress())[rssiBuffer.get(device.getAddress()).length - k])
                                            isComeBack = false;
                                    }
                                        if(isComeBack)
                                            rssiBuffer.get(device.getAddress())[rssiBuffer.get(device.getAddress()).length - 1] = (averageRssi.get(device.getAddress()) == null) ? rssi : (averageRssi.get(device.getAddress()) + rssi * 3) / 4;
                                        //////
                                double total = 0;
                                boolean isFull = true;

                            for(int i = 0; i < rssiBuffer.get(device.getAddress()).length ; i++){
                                if(rssiBuffer.get(device.getAddress())[i]==0)
                                    isFull = false;
                            }

                            if(isFull){

                                for(int i = 0; i < rssiBuffer.get(device.getAddress()).length ; i++)
                                    total += rssiBuffer.get(device.getAddress())[i]*(Math.pow(i+1,2));

                                total /= (rssiBuffer.get(device.getAddress()).length * (rssiBuffer.get(device.getAddress()).length+1) * (rssiBuffer.get(device.getAddress()).length*2+1) / 6);
                                averageRssi.put(device.getAddress(), total );

                                calculatedDistance.put(device.getAddress(),Math.pow(10,(-64 - averageRssi.get(device.getAddress()))/20));
                                mLeDeviceListAdapter.addDevice(device);
                            }
                    }
                }

            });
        }
    };

    private boolean isNotificationServiceEnabled(){
        String pkgName = getPackageName();
        final String flat = Settings.Secure.getString(getContentResolver(),
                ENABLED_NOTIFICATION_LISTENERS);
        if (!TextUtils.isEmpty(flat)) {
            final String[] names = flat.split(":");
            for (int i = 0; i < names.length; i++) {
                final ComponentName cn = ComponentName.unflattenFromString(names[i]);
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.getPackageName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }


    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
        TextView deviceDistance;

    }




}



