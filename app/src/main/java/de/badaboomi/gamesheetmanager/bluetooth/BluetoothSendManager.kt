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
            var lastLoggedPercent = -1
            try {
                Log.d(TAG, "send start: template=${template.name}, device=${device.address}")
                socket = device.createRfcommSocketToServiceRecord(BluetoothConstants.SERVICE_UUID)
                Log.d(TAG, "connecting: device=${device.address}")
                socket.connect()
                Log.d(TAG, "connected: device=${device.address}")

                val imageFile = File(template.imagePath)
                if (!imageFile.exists()) {
                    throw IOException("Template image file not found: ${template.imagePath}")
                }

                val imageBytes = imageFile.readBytes()
                val totalSize = imageBytes.size
                val startTime = System.currentTimeMillis()
                Log.d(TAG, "transfer begin: bytes=$totalSize")

                DataOutputStream(socket.outputStream).use { out ->
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
                        if (percent == 100 || percent / 10 > lastLoggedPercent / 10) {
                            lastLoggedPercent = percent
                            Log.d(TAG, "progress: $percent%, eta=${secondsLeft}s")
                        }
                    }
                    out.flush()
                }
                Log.d(TAG, "transfer complete: template=${template.name}, device=${device.address}")
            } catch (e: Exception) {
                failure = e
                Log.e(TAG, "send failed: template=${template.name}, device=${device.address}", e)
            } finally {
                try {
                    socket?.close()
                    Log.d(TAG, "socket closed: device=${device.address}")
                } catch (_: IOException) {
                    Log.w(TAG, "socket close failed: device=${device.address}")
                }
            }

            if (failure == null) {
                onSuccess()
            } else {
                onError(failure!!)
            }
        }.start()
    }
}
