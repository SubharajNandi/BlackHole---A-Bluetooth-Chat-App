package com.example.bluetoothchat

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.bluetoothchat.databinding.ActivityMainBinding
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_ENABLE_BT = 3
    }

    // Name of the connected device
    private var connectedDeviceName: String? = null
    private var connectedDeviceAddress: String? = null

    // Local Bluetooth adapter
    private var bluetoothAdapter: BluetoothAdapter? = null

    // Message buffer and list adapter for the chat
    private lateinit var conversationArrayAdapter: ChatMessageAdapter
    private val messages = mutableListOf<ChatMessage>()

    private lateinit var binding: ActivityMainBinding
    private lateinit var chatService: BluetoothChatService

    // Handler that gets information back from the BluetoothChatService
    private val mHandler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                BluetoothChatService.MESSAGE_STATE_CHANGE -> {
                    when (msg.arg1) {
                        BluetoothChatService.STATE_CONNECTED -> {
                            setStatus(getString(R.string.title_connected_to, connectedDeviceName))
                            activityMainTitle.text = connectedDeviceName ?: "Connected"
                            binding.buttonSend.isEnabled = true
                        }
                        BluetoothChatService.STATE_CONNECTING -> {
                            setStatus(R.string.title_connecting)
                            binding.buttonSend.isEnabled = false
                        }
                        BluetoothChatService.STATE_LISTEN, BluetoothChatService.STATE_NONE -> {
                            setStatus(R.string.title_not_connected)
                            binding.buttonSend.isEnabled = false
                        }
                    }
                }
                BluetoothChatService.MESSAGE_WRITE -> {
                    val writeBuf = msg.obj as ByteArray
                    val writeMessage = String(writeBuf, StandardCharsets.UTF_8)
                    conversationArrayAdapter.add(ChatMessage(writeMessage, isMe = true))
                    binding.listMessages.setSelection(conversationArrayAdapter.count - 1)
                }
                BluetoothChatService.MESSAGE_READ -> {
                    val readBuf = msg.obj as ByteArray
                    val readMessage = String(readBuf, 0, msg.arg1, StandardCharsets.UTF_8)
                    conversationArrayAdapter.add(
                        ChatMessage(readMessage, isMe = false, senderName = connectedDeviceName ?: "Device")
                    )
                    binding.listMessages.setSelection(conversationArrayAdapter.count - 1)
                }
                BluetoothChatService.MESSAGE_DEVICE_NAME -> {
                    connectedDeviceName = msg.data.getString(BluetoothChatService.DEVICE_NAME)
                    connectedDeviceAddress = msg.data.getString(BluetoothChatService.DEVICE_ADDRESS)
                    if (connectedDeviceName != null) {
                        Toast.makeText(
                            applicationContext,
                            "Connected to $connectedDeviceName",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                BluetoothChatService.MESSAGE_TOAST -> {
                    Toast.makeText(
                        applicationContext,
                        msg.data.getString(BluetoothChatService.TOAST),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private lateinit var activityMainTitle: TextView

    // Permission launcher for Bluetooth permissions
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            setupChat()
        } else {
            Toast.makeText(this, "Bluetooth permission is required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // Result launcher for enabling Bluetooth
    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            setupChat()
        } else {
            Toast.makeText(this, "Bluetooth must be enabled to chat", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // Result launcher for choosing a device
    private val deviceListLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val address = result.data?.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS)
            address?.let {
                connectDevice(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                systemBars.left + 16,
                systemBars.top + 16,
                systemBars.right + 16,
                imeInsets.bottom.coerceAtLeast(systemBars.bottom) + 16
            )
            insets
        }

        activityMainTitle = binding.activityMainTitle ?: findViewById(R.id.activity_main_title)

        // Get local Bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // If the adapter is null, then Bluetooth is not supported
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        conversationArrayAdapter = ChatMessageAdapter(this, messages)
        binding.listMessages.adapter = conversationArrayAdapter
        binding.listMessages.transcriptMode = ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL

        if (!hasConnectPermission() || !hasAdvertisePermission() || !hasScanPermission()) {
            requestNeededPermissions()
        } else {
            setupChat()
        }
    }

    override fun onStart() {
        super.onStart()
        // If BT is not on, request that it be enabled.
        if (bluetoothAdapter?.isEnabled == false) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBtLauncher.launch(enableIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::chatService.isInitialized) {
            chatService.stop()
        }
    }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun setupChat() {
        // Initialize the BluetoothChatService to perform bluetooth connections
        chatService = BluetoothChatService(this, mHandler)

        // Initialize listeners for message input and send
        binding.buttonSend.setOnClickListener {
            sendMessage(binding.editTextMessage.text.toString())
        }
        binding.buttonScan.setOnClickListener {
            val serverIntent = Intent(this, DeviceListActivity::class.java)
            deviceListLauncher.launch(serverIntent)
        }
        binding.editTextMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage(binding.editTextMessage.text.toString())
                true
            } else {
                false
            }
        }
        binding.editTextMessage.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && conversationArrayAdapter.count > 0) {
                binding.listMessages.postDelayed({
                    binding.listMessages.setSelection(conversationArrayAdapter.count - 1)
                }, 200)
            }
        }

        // Initialize the BluetoothChatService to run as the server (waiting for connections)
        chatService.start()
    }

    /**
     * Sends a message.
     */
    private fun sendMessage(message: String) {
        // Check that we're actually connected before trying anything
        if (chatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show()
            return
        }

        // Check that there's actually something to send
        if (message.isNotEmpty()) {
            // Get the message bytes and tell the BluetoothChatService to write
            val send = message.toByteArray(StandardCharsets.UTF_8)
            chatService.write(send)
            binding.editTextMessage.setText("")
        }
    }

    /**
     * Establish a connection with a remote device.
     */
    private fun connectDevice(address: String) {
        val device = bluetoothAdapter?.getRemoteDevice(address)
        if (device != null && hasConnectPermission()) {
            chatService.connect(device)
        }
    }

    /**
     * Changes the status title at the top of the UI.
     */
    private fun setStatus(resId: Int) {
        activityMainTitle.text = resources.getString(resId)
    }

    private fun setStatus(subtitle: String) {
        activityMainTitle.text = subtitle
    }

    private fun hasConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasScanPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasAdvertisePermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
    }
}