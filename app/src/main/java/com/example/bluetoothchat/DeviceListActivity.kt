package com.example.bluetoothchat

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * This Activity appears as a dialog. It lists any paired devices and devices
 * detected in the area after discovery. When a device is chosen by the user,
 * the MAC address of the device is sent back to the parent Activity in the
 * result Intent.
 */
class DeviceListActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DEVICE_ADDRESS = "device_address"

        private const val TAG = "DeviceListActivity"
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private lateinit var newDevicesArrayAdapter: ArrayAdapter<String>
    private lateinit var pairedDevicesArrayAdapter: ArrayAdapter<String>

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Check if we got the necessary permissions
        if (permissions.values.all { it }) {
            doDiscovery()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                }

                if (device != null && hasConnectPermission()) {
                    try {
                        val deviceName = device.name
                        if (deviceName != null) {
                            // If it's already paired, skip it, it's already in the paired list
                            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                                newDevicesArrayAdapter.add("$deviceName\n${device.address}")
                            }
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "SecurityException accessing device properties", e)
                    }
                }
                // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == action) {
                setTitle(R.string.select_device)
                if (newDevicesArrayAdapter.count == 0) {
                    val noDevices = getString(R.string.none_found)
                    newDevicesArrayAdapter.add(noDevices)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_list)

        newDevicesArrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)
        pairedDevicesArrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)

        // Set result CANCELED in case the user backs out
        setResult(Activity.RESULT_CANCELED)

        // Initialize the button to perform device discovery
        val scanButton = findViewById<Button>(R.id.button_scan)
        scanButton.setOnClickListener {
            checkPermissionsAndScan()
        }

        // Initialize array adapters. One for already paired devices and one for newly discovered devices
        val pairedListView = findViewById<ListView>(R.id.paired_devices)
        pairedListView.adapter = pairedDevicesArrayAdapter
        pairedListView.setOnItemClickListener { _, _, position, _ ->
            val info = pairedDevicesArrayAdapter.getItem(position).toString()
            val address = info.substring(info.length - 17)
            finishWithAddress(address)
        }

        val newDevicesListView = findViewById<ListView>(R.id.new_devices)
        newDevicesListView.adapter = newDevicesArrayAdapter
        newDevicesListView.setOnItemClickListener { _, _, position, _ ->
            val info = newDevicesArrayAdapter.getItem(position).toString()
            val address = info.substring(info.length - 17)
            finishWithAddress(address)
        }

        // Get the local Bluetooth adapter and check if it's enabled
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            showMessage("Bluetooth is not available")
            finish()
            return
        }

        // Register for broadcasts when a device is discovered and discovery finished
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        // Get a set of currently paired devices
        pairedDevicesArrayAdapter.clear()
        val pairedDevices: Set<BluetoothDevice>? = if (hasConnectPermission()) {
            try {
                bluetoothAdapter?.bondedDevices
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException getting bonded devices", e)
                null
            }
        } else {
            emptySet()
        }
        if (pairedDevices != null && pairedDevices.isNotEmpty()) {
            findViewById<View>(R.id.title_paired_devices).visibility = View.VISIBLE
            for (device in pairedDevices) {
                try {
                    pairedDevicesArrayAdapter.add("${device.name}\n${device.address}")
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException accessing paired device", e)
                }
            }
        } else {
            findViewById<TextView>(R.id.no_devices).visibility = View.VISIBLE
            findViewById<View>(R.id.title_paired_devices).visibility = View.VISIBLE
        }

        if (hasScanPermission() && hasConnectPermission()) {
            doDiscovery()
        } else {
            checkPermissionsAndScan()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Make sure we're not doing discovery anymore
        if (bluetoothAdapter != null && hasScanPermission()) {
            bluetoothAdapter.cancelDiscovery()
        }
        // Unregister broadcast listeners
        unregisterReceiver(receiver)
    }

    private fun checkPermissionsAndScan() {
        val needsConnect = !hasConnectPermission()
        val needsScan = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasScanPermission()

        if (needsConnect || needsScan) {
            permissionLauncher.launch(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    )
                } else {
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            )
        } else {
            doDiscovery()
        }
    }

    private fun hasScanPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private fun doDiscovery() {
        if (!hasScanPermission()) {
            return
        }

        setTitle(R.string.scanning)

        // If we're already discovering, stop it
        bluetoothAdapter?.let {
            if (it.isDiscovering) {
                it.cancelDiscovery()
            }
            // Request discovery from BluetoothAdapter
            it.startDiscovery()
        }
    }

    private fun showMessage(message: String) {
        val msg = Intent()
        msg.putExtra("error", message)
        setResult(Activity.RESULT_CANCELED, msg)
        finish()
    }

    private fun finishWithAddress(address: String) {
        // Make sure we're not doing discovery anymore
        if (bluetoothAdapter != null && hasScanPermission() && bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }
        // Create the result Intent and include the MAC address
        val intent = Intent()
        intent.putExtra(EXTRA_DEVICE_ADDRESS, address)

        // Set result and finish this Activity
        setResult(Activity.RESULT_OK, intent)
        finish()
    }
}