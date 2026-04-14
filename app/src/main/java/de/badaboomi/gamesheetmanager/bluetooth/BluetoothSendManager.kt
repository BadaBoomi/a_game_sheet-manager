package de.badaboomi.gamesheetmanager.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
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
            try {
                socket = device.createRfcommSocketToServiceRecord(BluetoothConstants.SERVICE_UUID)
                socket.connect()

                val imageFile = File(template.imagePath)
                if (!imageFile.exists()) {
                    throw IOException("Template image file not found: ${template.imagePath}")
                }

                val imageBytes = imageFile.readBytes()
                val totalSize = imageBytes.size
                val startTime = System.currentTimeMillis()

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
                    }
                    out.flush()
                }

                onSuccess()
            } catch (e: Exception) {
                onError(e)
            } finally {
                try {
                    socket?.close()
                } catch (_: IOException) {
                }
            }
        }.start()
    }
}
