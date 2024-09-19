package com.example.ble;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_LOCATION_PERMISSION = 2;
    private Button buttonScan, buttonStopScan, buttonReset;
    private BluetoothAdapter bluetoothAdapter;
    private DeviceAdapter deviceAdapter;
    private BluetoothLeScanner bluetoothLeScanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonScan = findViewById(R.id.button_scan);
        buttonStopScan = findViewById(R.id.button_stop_scan);
        buttonReset = findViewById(R.id.button_reset);
        RecyclerView recyclerViewDevices = findViewById(R.id.recycler_view_devices);

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();

        deviceAdapter = new DeviceAdapter(new ArrayList<>());
        recyclerViewDevices.setAdapter(deviceAdapter);
        recyclerViewDevices.setLayoutManager(new LinearLayoutManager(this));

        buttonScan.setOnClickListener(v -> startScan());
        buttonStopScan.setOnClickListener(v -> stopScan());
        buttonReset.setOnClickListener(v -> resetDeviceList());
        updateButtonStates(false);
        checkPermissions();
    }

    private void checkPermissions() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
        }
    }

    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            runOnUiThread(() -> deviceAdapter.addDevice(device));
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                BluetoothDevice device = result.getDevice();
                runOnUiThread(() -> deviceAdapter.addDevice(device));
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            // Handle scan error
        }
    };

    public void startScan() {
        bluetoothLeScanner.startScan(leScanCallback);
        updateButtonStates(true);
    }

    public void stopScan() {
        bluetoothLeScanner.stopScan(leScanCallback);
        updateButtonStates(false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_CANCELED) {
            Toast.makeText(this, "Bluetooth must be enabled to use this app", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION && grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission is required to scan for BLE devices", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    private void resetDeviceList() {
        deviceAdapter.clear(); // Clear the device list in the adapter
    }
    private void updateButtonStates(boolean isScanning) {
        buttonScan.setEnabled(!isScanning);
        buttonStopScan.setEnabled(isScanning);
        buttonScan.setBackgroundColor(isScanning ? Color.GRAY : Color.GREEN);
        buttonStopScan.setBackgroundColor(isScanning ? Color.RED : Color.GRAY);
    }

}