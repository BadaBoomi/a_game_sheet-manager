package de.badaboomi.gamesheetmanager.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import de.badaboomi.gamesheetmanager.data.Template
import java.io.DataOutputStream
import java.io.File
import java.io.IOException

/**
 * Handles sending a template to a remote Bluetooth device.
 *
 * Protocol:
 *  1. Connect via RFCOMM using [BluetoothConstants.SERVICE_UUID].
 *  2. Write header string (UTF-8, length-prefixed via [DataOutputStream.writeUTF]).
 *  3. Write template name (UTF-8).
 *  4. Write image file size as Long.
 *  5. Write image bytes.
 */
object BluetoothSendManager {

    private const val TAG = "BtSend"

    @Suppress("MissingPermission")
    private fun connectWithFallback(device: BluetoothDevice): BluetoothSocket {
        var lastError: Exception? = null

        repeat(2) { attempt ->
            var socket: BluetoothSocket? = null
            try {
                socket = device.createRfcommSocketToServiceRecord(BluetoothConstants.SERVICE_UUID)
                socket.connect()
                return socket
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "secure connect failed attempt ${attempt + 1}: device=${device.address}", e)
                try {
                    socket?.close()
                } catch (_: IOException) {
                }
            }
        }

        var insecureSocket: BluetoothSocket? = null
        try {
            insecureSocket = device.createInsecureRfcommSocketToServiceRecord(
                BluetoothConstants.SERVICE_UUID
            )
            insecureSocket.connect()
            return insecureSocket
        } catch (e: Exception) {
            try {
                insecureSocket?.close()
            } catch (_: IOException) {
            }
            Log.e(TAG, "insecure connect failed: device=${device.address}", e)
            throw lastError ?: e
        }
    }

    /**
     * Sends [template] to [device] on a background thread and reports results
     * via [onSuccess] / [onError] (called on the calling thread – post to UI handler if needed).
     */
    fun send(
        device: BluetoothDevice,
        template: Template,
        onProgress: (percent: Int, secondsLeft: Int) -> Unit = { _, _ -> },
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        Thread {
            var socket: BluetoothSocket? = null
            var failure: Exception? = null
            try {
                socket = connectWithFallback(device)

                val imageFile = File(template.imagePath)
                if (!imageFile.exists()) {
                    throw IOException("Template image file not found: ${template.imagePath}")
                }

                val imageBytes = imageFile.readBytes()
                val totalSize = imageBytes.size
                val startTime = System.currentTimeMillis()

                // Do NOT wrap with .use {} — closing DataOutputStream closes the Bluetooth socket
                // output stream immediately, causing the receiver to get EOF mid-read.
                // The socket is closed in the finally block below.
                val out = DataOutputStream(socket.outputStream)
                out.writeUTF(BluetoothConstants.PROTOCOL_HEADER)
                out.writeUTF(template.name)
                out.writeLong(totalSize.toLong())
                val bufferSize = 8192
                var written = 0
                while (written < totalSize) {
                    val chunkSize = minOf(bufferSize, totalSize - written)
                    out.write(imageBytes, written, chunkSize)
                    written += chunkSize
                    val percent = (written * 100L / totalSize).toInt()
                    val elapsed = System.currentTimeMillis() - startTime
                    val secondsLeft = if (written > 0 && elapsed > 500) {
                        val bytesPerMs = written.toDouble() / elapsed
                        ((totalSize - written) / bytesPerMs / 1000).toInt().coerceAtLeast(0)
                    } else -1
                    onProgress(percent, secondsLeft)
                }
                out.flush()
                // Wait for 1-byte ACK from receiver before closing the socket.
                // Closing immediately after flush() can discard bytes still queued
                // in the Bluetooth TX stack that the receiver hasn't consumed yet.
                try {
                    socket.inputStream.read()
                } catch (e: IOException) {
                    Log.w(TAG, "ack wait failed (transfer may still be ok): ${e.message}")
                }
            } catch (e: Exception) {
                failure = e
                Log.e(TAG, "send failed: template=${template.name}, device=${device.address}", e)
            } finally {
                try {
                    socket?.close()
                } catch (_: IOException) {
                    Log.w(TAG, "socket close failed: device=${device.address}")
                }
            }

            if (failure == null) {
                onSuccess()
            } else {
                onError(failure)
            }
        }.start()
    }
}
