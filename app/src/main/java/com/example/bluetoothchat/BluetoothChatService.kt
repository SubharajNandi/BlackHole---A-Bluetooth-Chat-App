package com.example.bluetoothchat

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Handles the Bluetooth Classic RFCOMM connection for peer-to-peer messaging.
 * This is a true server/client model: one device acts as the "server" (listens
 * for incoming connections) and the other acts as the "client" (connects in).
 * Newer devices often get a better connection quality if a discovery process
 * runs, but discovery itself is handled in DeviceListActivity.
 *
 * All messaging over RFCOMM is fully offline — no Internet required. The two
 * devices just need to be within Bluetooth range of each other.
 */
class BluetoothChatService(private val context: Context, private val handler: Handler) {

    companion object {
        private const val TAG = "BluetoothChatService"

        // This UUID identifies our "service" on the remote device. It must be
        // the SAME on both the server and client side.
        val APP_UUID: UUID = UUID.fromString("6f1a5c9e-4f4e-4b7c-8c4a-2f0e5d30a9b1")
        const val NAME = "BluetoothChat"

        // Messages we send back to the UI thread
        const val MESSAGE_STATE_CHANGE = 1
        const val MESSAGE_READ = 2
        const val MESSAGE_WRITE = 3
        const val MESSAGE_DEVICE_NAME = 4
        const val MESSAGE_TOAST = 5

        // Key names for the Bundle that is passed by the handler
        const val DEVICE_NAME = "device_name"
        const val DEVICE_ADDRESS = "device_address"
        const val TOAST = "toast"

        // Connection states
        const val STATE_NONE = 0
        const val STATE_LISTEN = 1
        const val STATE_CONNECTING = 2
        const val STATE_CONNECTED = 3
    }

    private val adapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var state = STATE_NONE
    private var acceptThread: AcceptThread? = null
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null

    /**
     * Set the current state of the chat connection.
     */
    @Synchronized
    private fun setState(currentState: Int) {
        state = currentState
        handler.obtainMessage(MESSAGE_STATE_CHANGE, currentState, -1).sendToTarget()
    }

    /**
     * Return the current connection state.
     */
    @Synchronized
    fun getState(): Int = state

    /**
     * Start the chat service: cancel current connections and start listening.
     */
    @Synchronized
    fun start() {
        Log.d(TAG, "start")

        connectThread?.cancel()
        connectThread = null
        connectedThread?.cancel()
        connectedThread = null
        acceptThread?.cancel()
        acceptThread = null

        acceptThread = AcceptThread()
        acceptThread?.start()
        setState(STATE_LISTEN)
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     */
    @Synchronized
    fun connect(device: BluetoothDevice) {
        Log.d(TAG, "connect to: $device")

        if (state == STATE_CONNECTING) {
            connectThread?.cancel()
            connectThread = null
        }
        connectThread?.cancel()
        connectThread = null
        connectedThread?.cancel()
        connectedThread = null

        connectThread = ConnectThread(device)
        connectThread?.start()
        setState(STATE_CONNECTING)
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection.
     */
    @Synchronized
    private fun connected(socket: BluetoothSocket, device: BluetoothDevice, socketType: String) {
        Log.d(TAG, "connected, Socket Type: $socketType")

        acceptThread?.cancel()
        acceptThread = null
        connectThread?.cancel()
        connectThread = null
        connectedThread?.cancel()
        connectedThread = null

        connectedThread = ConnectedThread(socket, socketType)
        connectedThread?.start()

        // Send the name of the connected device back to the UI
        val msg = handler.obtainMessage(MESSAGE_DEVICE_NAME)
        val bundle = Bundle()
        val deviceName = try {
            device.name ?: "Device"
        } catch (e: SecurityException) {
            "Device"
        }
        val deviceAddress = try {
            device.address ?: ""
        } catch (e: SecurityException) {
            ""
        }
        bundle.putString(DEVICE_NAME, deviceName)
        bundle.putString(DEVICE_ADDRESS, deviceAddress)
        msg.data = bundle
        handler.sendMessage(msg)

        setState(STATE_CONNECTED)
    }

    /**
     * Stop all threads.
     */
    @Synchronized
    fun stop() {
        Log.d(TAG, "stop")
        connectThread?.cancel()
        connectThread = null
        connectedThread?.cancel()
        connectedThread = null
        acceptThread?.cancel()
        acceptThread = null
        setState(STATE_NONE)
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner.
     */
    fun write(out: ByteArray) {
        val r: ConnectedThread?
        synchronized(this) {
            if (state != STATE_CONNECTED) return
            r = connectedThread
        }
        r?.write(out)
    }

    /**
     * Indicate that the connection attempt failed and notify the UI.
     */
    private fun connectionFailed() {
        setState(STATE_LISTEN)
        val msg = handler.obtainMessage(MESSAGE_TOAST)
        val bundle = Bundle().apply {
            putString(TOAST, "Unable to connect device")
        }
        msg.data = bundle
        handler.sendMessage(msg)
    }

    /**
     * Indicate that the connection was lost and notify the UI.
     */
    private fun connectionLost() {
        setState(STATE_LISTEN)
        val msg = handler.obtainMessage(MESSAGE_TOAST)
        val bundle = Bundle().apply {
            putString(TOAST, "Device connection was lost")
        }
        msg.data = bundle
        handler.sendMessage(msg)
    }

    /**
     * Listens for incoming connections while running.
     */
    private inner class AcceptThread : Thread() {
        private val mmServerSocket: BluetoothServerSocket? = try {
            // Get a BluetoothServerSocket for this service with the APP_UUID
            adapter.listenUsingInsecureRfcommWithServiceRecord(NAME, APP_UUID)
        } catch (e: IOException) {
            Log.e(TAG, "Socket listen() failed", e)
            null
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException socket listen() failed", e)
            null
        }

        override fun run() {
            Log.d(TAG, "Socket Type: BEGIN mAcceptThread")
            name = "AcceptThread"
            var socket: BluetoothSocket? = null

            while (this@BluetoothChatService.state != STATE_CONNECTED) {
                try {
                    socket = mmServerSocket?.accept()
                } catch (e: IOException) {
                    Log.e(TAG, "Socket accept() failed", e)
                    break
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized(this@BluetoothChatService) {
                        when (this@BluetoothChatService.state) {
                            STATE_LISTEN,
                            STATE_CONNECTING -> {
                                // Connection accepted. Start the connected thread
                                connected(socket, socket.remoteDevice, "Secure")
                            }
                            else -> {
                                // Not accepting anymore. Close and stop
                                try {
                                    socket.close()
                                } catch (e: IOException) {
                                    Log.e(TAG, "Could not close unwanted socket", e)
                                }
                            }
                        }
                    }
                }
            }
            Log.i(TAG, "END mAcceptThread")
        }

        fun cancel() {
            Log.d(TAG, "Socket Type $NAME cancel()")
            try {
                mmServerSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Socket Type $NAME close() of server failed", e)
            }
        }
    }

    /**
     * Attempts to make a connection with a device. The connection is made when
     * this thread runs.
     */
    private inner class ConnectThread(device: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? = try {
            device.createRfcommSocketToServiceRecord(APP_UUID)
        } catch (e: IOException) {
            Log.e(TAG, "Socket create() failed", e)
            null
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException socket create() failed", e)
            null
        }
        private val mmDevice = device

        override fun run() {
            Log.i(TAG, "BEGIN mConnectThread")
            name = "ConnectThread"

            // Always cancel discovery because it will slow down a connection
            adapter.cancelDiscovery()

            // Make a connection to the BluetoothSocket
            try {
                mmSocket?.connect()
            } catch (e: IOException) {
                // Close the socket
                try {
                    mmSocket?.close()
                } catch (e2: IOException) {
                    Log.e(TAG, "unable to close() Socket during connection failure", e2)
                }
                connectionFailed()
                return
            }

            // Reset the ConnectThread because we're done with it
            synchronized(this@BluetoothChatService) {
                connectThread = null
            }

            // Start the connected thread
            connected(mmSocket!!, mmDevice, "Secure")
        }

        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "close() of connect Socket failed", e)
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device. Handles all
     * incoming/outgoing transmissions.
     */
    private inner class ConnectedThread(socket: BluetoothSocket, socketType: String) : Thread() {

        private val mmSocket: BluetoothSocket = socket
        private val mmInStream: InputStream = socket.inputStream
        private val mmOutStream: OutputStream = socket.outputStream

        override fun run() {
            Log.i(TAG, "BEGIN mConnectedThread")
            val buffer = ByteArray(1024)
            var bytes: Int

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer)

                    // Send the obtained bytes to the UI activity
                    handler.obtainMessage(
                        MESSAGE_READ, bytes, -1, buffer
                    ).sendToTarget()
                } catch (e: IOException) {
                    Log.e(TAG, "disconnected", e)
                    connectionLost()
                    break
                }
            }
        }

        /**
         * Write to the connected OutStream.
         */
        fun write(buffer: ByteArray) {
            try {
                mmOutStream.write(buffer)
                // Share the sent message with the UI activity
                handler.obtainMessage(MESSAGE_WRITE, -1, -1, buffer).sendToTarget()
            } catch (e: IOException) {
                Log.e(TAG, "Exception during write", e)
            }
        }

        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "close() of connect socket failed", e)
            }
        }
    }
}
