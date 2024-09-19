package com.example.ble;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> {

    private final List<BluetoothDevice> devices;

    public DeviceAdapter(List<BluetoothDevice> devices) {
        this.devices = devices;
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.device_item, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        BluetoothDevice device = devices.get(position);
        String deviceName = device.getName();
        if (deviceName == null || deviceName.isEmpty()) {
            deviceName = "Unknown Device";
        }

        holder.textDeviceName.setText(deviceName);
        holder.textDeviceAddress.setText(device.getAddress());

        // Check for specific company ID in the scan record (assuming it's available)
        // For the purpose of this example, we will make the connect button always visible
        holder.buttonConnect.setVisibility(View.VISIBLE);

        holder.buttonConnect.setOnClickListener(v -> {
            Intent intent = new Intent(holder.itemView.getContext(), SecondActivity.class);
            intent.putExtra("device", device);
            holder.itemView.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    public void addDevice(BluetoothDevice device) {
        if (!devices.contains(device)) {
            devices.add(device);
            notifyItemInserted(devices.size() - 1);
        }
    }
    public void clear() {
        devices.clear();
        notifyDataSetChanged();
    }
    static class DeviceViewHolder extends RecyclerView.ViewHolder {

        TextView textDeviceName;
        TextView textDeviceAddress;
        Button buttonConnect;

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            textDeviceName = itemView.findViewById(R.id.device_name);
            textDeviceAddress = itemView.findViewById(R.id.device_address);
            buttonConnect = itemView.findViewById(R.id.button_connect);
            textDeviceName.setTextColor(Color.WHITE);
        }
    }
}
