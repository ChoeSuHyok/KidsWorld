package com.kidsworld;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.kidsworld.ble.RBLGattAttributes;
import com.kidsworld.ble.RBLService;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    private Context _context = null;

    @BindView(R.id.btn_main_action)
    Button ui_btnAction;

    @BindView(R.id.txv_main_state)
    TextView ui_txvState;

    @BindView(R.id.txv_main_communication_data)
    TextView ui_TxvData;

    @BindView(R.id.lly_main_communication_bg)
    LinearLayout ui_llyComBg;

    @BindView(R.id.edt_main_character_uuid)
    EditText ui_edtCharacteristicUuid;

    @BindView(R.id.edt_main_service_uuid)
    EditText ui_edtServiceUuid;

    private static final String TAG = "KidsWorld";

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_SELECT_DEVICE = 2;

    private ProgressDialog progressDialog;

    public String address = null;
    public String deviceName = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        _context = this;

        ButterKnife.bind(this);

        InitLayout();

        CheckBluetooth();
    }

    private void CheckBluetooth() {

        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(_context, "Cannot Run!",
                    Toast.LENGTH_LONG).show();
            finish();
        } else {
            if (!mBluetoothAdapter.isEnabled()) {

                ui_btnAction.setEnabled(false);

                Intent intentBtEnabled = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(intentBtEnabled, REQUEST_ENABLE_BT);
            } else {
                InitBlueTooth();
            }
        }
    }

    private void InitLayout() {

        ui_btnAction.setText("Scan");
        ui_llyComBg.setVisibility(View.GONE);
        ui_TxvData.setText("");
        ui_txvState.setText("NONE");

        ui_edtServiceUuid.setText(RBLGattAttributes.BLE_SHIELD_SERVICE);
        ui_edtCharacteristicUuid.setText(RBLGattAttributes.BLE_SHIELD_RX2);
    }

    private void InitBlueTooth() {

        final BluetoothManager mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
    }

    static BluetoothGattCharacteristic characteristicRx = null; // for write
    static BluetoothGattCharacteristic characteristicRx2 = null; // for read

    private void getGattService(BluetoothGattService gattService) {

        if (gattService == null)
            return;

//        characteristicRx = gattService
//                .getCharacteristic(RBLService.UUID_BLE_SHIELD_RX);

        characteristicRx2 = gattService
                .getCharacteristic(RBLService.UUID_BLE_SHIELD_RX2);

        mBluetoothLeService.setCharacteristicNotification(characteristicRx2,
                true);

        mBluetoothLeService.readCharacteristic(characteristicRx2);
    }

    public void displayData(byte[] byteArray) {

        Log.d("kris_log", "kris_log1 - byteArray : " + byteArray.length);

        ui_TxvData.setText(new String(byteArray));
    }

    private BluetoothAdapter mBluetoothAdapter = null;

    private static final long SCAN_PERIOD = 3000;
    public static List<BluetoothDevice> mDevices = new ArrayList<BluetoothDevice>();

    private void scanLeDevice() {
        new Thread() {

            @Override
            public void run() {

                mBluetoothAdapter.startLeScan(mLeScanCallback);

                try {
                    Thread.sleep(SCAN_PERIOD);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }
        }.start();
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi,
                             byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (device != null) {
                        if (mDevices.indexOf(device) == -1)
                            mDevices.add(device);
                    }
                }
            });
        }
    };

    public static RBLService mBluetoothLeService;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName,
                                       IBinder service) {
            mBluetoothLeService = ((RBLService.LocalBinder) service)
                    .getService();

            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up
            // initialization.
            mBluetoothLeService.connect(address);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    static boolean isConnected = false;

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (RBLService.ACTION_GATT_DISCONNECTED.equals(action)) {
                isConnected = false;
                ui_txvState.setText("DISCONNECTED");
                ui_llyComBg.setVisibility(View.GONE);
            } else if (RBLService.ACTION_GATT_SERVICES_DISCOVERED
                    .equals(action)) {
                isConnected = true;
                getGattService(mBluetoothLeService.getSupportedGattService());
                ui_txvState.setText("CONNECTED");
                ui_llyComBg.setVisibility(View.VISIBLE);
            } else if (RBLService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getByteArrayExtra(RBLService.EXTRA_DATA));
            }
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(RBLService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(RBLService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(RBLService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(RBLService.ACTION_DATA_AVAILABLE);

        return intentFilter;
    }

    @OnClick(R.id.btn_main_action)
    public void OnClickAction() {

        ui_btnAction.setEnabled(false);

        progressDialog = ProgressDialog.show(this,
                "", "Searching...", false);
        progressDialog.setCancelable(true);

        scanLeDevice();

        ui_btnAction.setEnabled(true);

        Timer mTimer = new Timer();
        mTimer.schedule(new TimerTask() {

            @Override
            public void run() {
                progressDialog.dismiss();
                Intent serverIntent = new Intent(
                        _context, DevicesActivity.class);
                startActivityForResult(serverIntent,
                        REQUEST_SELECT_DEVICE);
            }
        }, SCAN_PERIOD);
    }

    // 블루투스 커넥션을 처리하기 위한 함수(자동호출)
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {

                ui_btnAction.setEnabled(true);

                InitBlueTooth();

            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(_context, "Canceled",
                        Toast.LENGTH_LONG).show();
                finish();
            }
        } else if (requestCode == REQUEST_SELECT_DEVICE) {
            if (resultCode == RESULT_OK) {
                // Get the device MAC address

                address = data.getExtras().getString(
                        DevicesActivity.EXTRA_DEVICE_ADDRESS);
                deviceName = data.getExtras().getString(
                        DevicesActivity.EXTRA_DEVICE_NAME);

                Intent gattServiceIntent = new Intent(this, RBLService.class);
                bindService(gattServiceIntent, mServiceConnection,
                        BIND_AUTO_CREATE);

                registerReceiver(mGattUpdateReceiver,
                        makeGattUpdateIntentFilter());
            }
        }
    }
}