package de.badaboomi.gamesheetmanager.bluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import de.badaboomi.gamesheetmanager.R
import de.badaboomi.gamesheetmanager.data.Template
import de.badaboomi.gamesheetmanager.repository.TemplateRepository
import de.badaboomi.gamesheetmanager.utils.FileUtils
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Foreground service that listens for incoming Bluetooth template transfers.
 *
 * Starts an RFCOMM server socket on [BluetoothConstants.SERVICE_UUID] and
 * saves each received template to the local database.
 */
class TemplateReceiveService : Service() {
    companion object {
        private const val TAG = "BtReceive"
        const val CHANNEL_ID = "bluetooth_receive_channel"
        const val NOTIFICATION_ID = 1001

        /** Broadcast action sent when a template has been received and saved. */
        const val ACTION_TEMPLATE_RECEIVED =
            "de.badaboomi.gamesheetmanager.ACTION_TEMPLATE_RECEIVED"

        /** Broadcast action sent periodically during receive to report progress. */
        const val ACTION_RECEIVE_PROGRESS =
            "de.badaboomi.gamesheetmanager.ACTION_RECEIVE_PROGRESS"
        const val EXTRA_PROGRESS_PERCENT = "progress_percent"
        const val EXTRA_SECONDS_LEFT = "seconds_left"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, TemplateReceiveService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TemplateReceiveService::class.java))
        }
    }

    private var serverSocket: BluetoothServerSocket? = null
    private var acceptThread: Thread? = null
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAccepting()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }
        acceptThread?.interrupt()
        super.onDestroy()
    }

    private fun startAccepting() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "startAccepting aborted: adapter missing or disabled")
            stopSelf()
            return
        }

        try {
            @Suppress("MissingPermission")
            serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                BluetoothConstants.SERVICE_NAME,
                BluetoothConstants.SERVICE_UUID
            )
        } catch (e: IOException) {
            Log.e(TAG, "listenUsingRfcommWithServiceRecord failed", e)
            stopSelf()
            return
        }

        running = true
        acceptThread = Thread {
            while (running) {
                val socket: BluetoothSocket = try {
                    serverSocket?.accept() ?: break
                } catch (e: IOException) {
                    Log.w(TAG, "accept failed while running=$running", e)
                    if (running) continue else break
                }
                handleIncoming(socket)
            }
        }
        acceptThread?.start()
    }

    private fun handleIncoming(socket: BluetoothSocket) {
        try {
            // Do NOT use DataInputStream.use {} — closing it closes the Bluetooth socket
            // before we can send the ACK byte back to the sender.
            val input = DataInputStream(socket.inputStream)

            val header = input.readUTF()
            if (header != BluetoothConstants.PROTOCOL_HEADER) {
                Log.w(TAG, "invalid protocol header: $header")
                return
            }

            val name = input.readUTF()
            val imageSize = input.readLong()

            if (imageSize <= 0 || imageSize > 50 * 1024 * 1024L) {
                Log.w(TAG, "invalid image size: $imageSize")
                return
            }

            val imageBytes = ByteArray(imageSize.toInt())
            val startTime = System.currentTimeMillis()
            var offset = 0
            while (offset < imageBytes.size) {
                val read = input.read(imageBytes, offset, imageBytes.size - offset)
                if (read < 0) break
                offset += read
                val percent = (offset * 100L / imageBytes.size).toInt()
                val elapsed = System.currentTimeMillis() - startTime
                val secondsLeft = if (offset > 0 && elapsed > 500) {
                    val bytesPerMs = offset.toDouble() / elapsed
                    ((imageBytes.size - offset) / bytesPerMs / 1000).toInt().coerceAtLeast(0)
                } else -1
                sendBroadcast(
                    Intent(ACTION_RECEIVE_PROGRESS).apply {
                        `package` = packageName
                        putExtra(EXTRA_PROGRESS_PERCENT, percent)
                        putExtra(EXTRA_SECONDS_LEFT, secondsLeft)
                    }
                )
            }

            if (offset != imageBytes.size) {
                Log.w(TAG, "incomplete receive: read=$offset expected=${imageBytes.size}")
                return
            }

            val destFile = FileUtils.createTemplateImageFile(this)
            FileOutputStream(destFile).use { it.write(imageBytes) }

            val template = Template(name = name, imagePath = destFile.absolutePath)
            TemplateRepository(this).insertTemplate(template)

            // Send 1-byte ACK so the sender knows all data was consumed and it
            // is safe to close the socket without discarding buffered bytes.
            try {
                socket.outputStream.write(1)
                socket.outputStream.flush()
            } catch (e: IOException) {
                Log.w(TAG, "failed to send ack", e)
            }

            sendBroadcast(
                Intent(ACTION_TEMPLATE_RECEIVED).apply { `package` = packageName }
            )

            mainThread {
                Toast.makeText(
                    this,
                    getString(R.string.msg_template_received, name),
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: IOException) {
            Log.e(TAG, "handleIncoming failed", e)
        } finally {
            try {
                socket.close()
            } catch (_: IOException) {
                Log.w(TAG, "incoming socket close failed")
            }
        }
    }

    private fun mainThread(action: () -> Unit) {
        android.os.Handler(mainLooper).post(action)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_bt_receive),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_bt_receive_title))
            .setContentText(getString(R.string.notification_bt_receive_text))
            .setSmallIcon(R.drawable.ic_bluetooth_send)
            .build()
    }
}
