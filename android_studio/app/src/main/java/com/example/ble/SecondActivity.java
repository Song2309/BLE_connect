package com.example.ble;

import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SecondActivity extends AppCompatActivity {

    private static final String TAG = SecondActivity.class.getSimpleName();

    private BluetoothGatt bluetoothGatt;
    private TextView textDeviceName;
    private LinearLayout layoutServices;
    private Map<UUID, TextView> hexTextViews = new HashMap<>();
    private Map<UUID, TextView> asciiTextViews = new HashMap<>();
    private Map<UUID, TextView> decimalTextViews = new HashMap<>();
    private SharedPreferences sharedPreferences;
    private boolean enable_Notify = false;
    private Map<UUID, Boolean> notificationStatusMap = new HashMap<>();


    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        textDeviceName = findViewById(R.id.text_device_name);
        layoutServices = findViewById(R.id.layout_services);
        Button buttonDisconnect = findViewById(R.id.button_disconnect);
        sharedPreferences = getSharedPreferences("BLE_Preferences", Context.MODE_PRIVATE);
        BluetoothDevice device = getIntent().getParcelableExtra("device");
        if (device != null) {
            String deviceName = device.getName();
            if (deviceName == null || deviceName.isEmpty()) {
                deviceName = "Unknown Device";
            }
            textDeviceName.setText(deviceName);
            bluetoothGatt = device.connectGatt(this, false, gattCallback);
        } else {
            textDeviceName.setText("Device not found");
        }

        buttonDisconnect.setOnClickListener(v -> {
            disconnectGatt();
            finish();  // Close this activity and return to MainActivity
        });
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnectGatt();
                runOnUiThread(() -> {
                    Toast.makeText(SecondActivity.this, "Disconnected from device", Toast.LENGTH_SHORT).show();
                });
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread(() -> displayGattServices(gatt.getServices()));
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                runOnUiThread(() -> {
                    String decimalValue = bytesToDecimalString(characteristic.getValue());
                    String hexValue = decimalToHex(decimalValue);
                    String asciiValue = decimalToAscii(decimalValue);

                    updateCharacteristicValueViews(characteristic.getUuid(), decimalValue, hexValue, asciiValue);
                    Toast.makeText(SecondActivity.this, "Read successful: " + decimalValue, Toast.LENGTH_SHORT).show();
                });
                Log.d(TAG, "Characteristic read successful: " + characteristic.getUuid());
            } else {
                Log.w(TAG, "Characteristic read failed for: " + characteristic.getUuid() + ", status: " + status);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            runOnUiThread(() -> {
                if (status == BluetoothGatt.GATT_SUCCESS ) {
                    Toast.makeText(SecondActivity.this, "Write successful", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(SecondActivity.this, "Write failed", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            runOnUiThread(() -> {
                byte[] value = characteristic.getValue();
                String decimalValue = bytesToDecimalString(value);
                String hexValue = decimalToHex(decimalValue);
                String asciiValue = decimalToAscii(decimalValue);

                updateCharacteristicValueViews(characteristic.getUuid(), decimalValue, hexValue, asciiValue);
                Toast.makeText(SecondActivity.this, "Characteristic changed: " + decimalValue, Toast.LENGTH_SHORT).show();
            });
        }
    };

    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;

        for (BluetoothGattService gattService : gattServices) {
            UUID serviceUuid = gattService.getUuid();
            if (serviceUuid.toString().endsWith("00805f9b34fb")) {
                continue;
            }
            // Inflate service layout
            View serviceView = getLayoutInflater().inflate(R.layout.service_layout, null);
            TextView serviceName = serviceView.findViewById(R.id.service_name);
            TextView serviceUuidTextView = serviceView.findViewById(R.id.service_uuid);
            Button renameButton = serviceView.findViewById(R.id.rename_button);

            String serviceKey = "service_" + serviceUuid.toString();
            String savedServiceName = sharedPreferences.getString(serviceKey, "Service");
            serviceName.setText(savedServiceName);
            serviceUuidTextView.setText(serviceUuid.toString());
            renameButton.setOnClickListener(v -> showRenameDialog(serviceName, serviceKey));

            layoutServices.addView(serviceView);

            List<BluetoothGattCharacteristic> characteristics = gattService.getCharacteristics();
            for (BluetoothGattCharacteristic characteristic : characteristics) {
                UUID characteristicUuid = characteristic.getUuid();

                // Inflate characteristic layout
                View characteristicView = getLayoutInflater().inflate(R.layout.characteristic_layout, null);
                TextView characteristicUuidTextView = characteristicView.findViewById(R.id.characteristic_uuid);
                TextView characteristicNameTextView = characteristicView.findViewById(R.id.characteristic_name);
                Button renameButton2 = characteristicView.findViewById(R.id.rename_button);
                TextView hexTextView = characteristicView.findViewById(R.id.value_hex);
                TextView asciiTextView = characteristicView.findViewById(R.id.value_ascii);
                TextView decimalTextView = characteristicView.findViewById(R.id.value_decimal);

                String characteristicKey = "characteristic_" + characteristicUuid.toString();
                String savedCharacteristicName = sharedPreferences.getString(characteristicKey, "Characteristic");

                // Store references to TextViews in the maps
                hexTextViews.put(characteristicUuid, hexTextView);
                asciiTextViews.put(characteristicUuid, asciiTextView);
                decimalTextViews.put(characteristicUuid, decimalTextView);

                characteristicUuidTextView.setText(characteristicUuid.toString());
                characteristicNameTextView.setText(savedCharacteristicName);
                renameButton2.setOnClickListener(v -> showRenameDialog(characteristicNameTextView, characteristicKey));

                Button readButton = characteristicView.findViewById(R.id.button_read);
                Button writeButton = characteristicView.findViewById(R.id.button_write);
                Button notifyButton = characteristicView.findViewById(R.id.button_notify);

                // Check characteristic properties and set button visibility
                int properties = characteristic.getProperties();
                if ((properties & BluetoothGattCharacteristic.PROPERTY_READ) == 0) {
                    readButton.setVisibility(View.GONE);
                } else {
                    readButton.setOnClickListener(v -> readCharacteristic(characteristic));
                }
                if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) == 0 &&
                        (properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0) {
                    writeButton.setVisibility(View.GONE);
                } else {
                    writeButton.setOnClickListener(v -> showWriteDialog(characteristic));
                }
                if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) {
                    notifyButton.setVisibility(View.GONE);
                } else {
                    notifyButton.setOnClickListener(v -> {
                        setCharacteristicNotification(characteristic, !notificationStatusMap.getOrDefault(characteristicUuid, false));
                        notifyButton.setText(notificationStatusMap.getOrDefault(characteristicUuid, false) ? "Disable Notify" : "Enable Notify");
                    });
                }

                // Add characteristicView to layoutServices
                layoutServices.addView(characteristicView);
            }
        }
    }
    private void updateCharacteristicValueViews(UUID characteristicUuid, String decimalValue, String hexValue, String asciiValue) {
        TextView hexTextView = hexTextViews.get(characteristicUuid);
        if (hexTextView != null) {
            hexTextView.setText(hexValue);
        }

        TextView asciiTextView = asciiTextViews.get(characteristicUuid);
        if (asciiTextView != null) {
            asciiTextView.setText(asciiValue);
        }

        TextView decimalTextView = decimalTextViews.get(characteristicUuid);
        if (decimalTextView != null) {
            decimalTextView.setText(decimalValue);
        }
    }

    private void showRenameDialog(TextView textView, String key) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rename");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        builder.setPositiveButton("OK", (dialog, which) -> {
            String newName = input.getText().toString();
            if (!newName.isEmpty()) {
                textView.setText(newName);
                sharedPreferences.edit().putString(key, newName).apply();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }


    private void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (bluetoothGatt != null) {
            bluetoothGatt.readCharacteristic(characteristic);
        }
    }

    private void showWriteDialog(BluetoothGattCharacteristic characteristic) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Write Value");

        final EditText input = new EditText(SecondActivity.this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String value = input.getText().toString();
            // Assuming you want to write a numeric value as a string to the characteristic
            double doubValue = Double.parseDouble(value);
            // Convert the integer to a byte array
            byte[] byteValue = new byte[] { (byte) doubValue };
            characteristic.setValue(byteValue);
            if (bluetoothGatt != null) {
                String decimalValue = bytesToDecimalString(characteristic.getValue());
                String hexValue = decimalToHex(decimalValue);
                String asciiValue = decimalToAscii(decimalValue);

                updateCharacteristicValueViews(characteristic.getUuid(), decimalValue, hexValue, asciiValue);
                bluetoothGatt.writeCharacteristic(characteristic);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }
    private void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
        if (bluetoothGatt == null) {
            Log.w(TAG, "BluetoothGatt is null, cannot set notification");
            return;
        }

        boolean success = bluetoothGatt.setCharacteristicNotification(characteristic, enabled);
        if (!success) {
            Log.w(TAG, "Failed to set characteristic notification for " + characteristic.getUuid());
            return;
        }

        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
        if (descriptor != null) {
            descriptor.setValue(enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            bluetoothGatt.writeDescriptor(descriptor);
            notificationStatusMap.put(characteristic.getUuid(), enabled); // Update notification status
        }
    }


    private String decimalToHex(String decimalString) {
        StringBuilder hexString = new StringBuilder();
        String[] decimalValues = decimalString.split(" ");
        for (String decimalValue : decimalValues) {
            try {
                int value = Integer.parseInt(decimalValue);
                hexString.append(String.format("%02X ", value));
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        return hexString.toString().trim();
    }
    private String decimalToAscii(String decimalString) {
        StringBuilder asciiString = new StringBuilder();
        String[] decimalValues = decimalString.split(" ");
        for (String decimalValue : decimalValues) {
            try {
                int value = Integer.parseInt(decimalValue);
                asciiString.append((char) value);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        return asciiString.toString();
    }
    private String bytesToDecimalString(byte[] bytes) {
        StringBuilder decimalString = new StringBuilder();
        for (byte b : bytes) {
            decimalString.append(b & 0xFF).append(" ");
        }
        return decimalString.toString().trim();
    }
    private void disconnectGatt() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }
}
