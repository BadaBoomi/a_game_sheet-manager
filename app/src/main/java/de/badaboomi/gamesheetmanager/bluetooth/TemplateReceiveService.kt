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
        const val CHANNEL_ID = "bluetooth_receive_channel"
        const val NOTIFICATION_ID = 1001

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
            stopSelf()
            return
        }

        running = true
        acceptThread = Thread {
            while (running) {
                val socket: BluetoothSocket = try {
                    serverSocket?.accept() ?: break
                } catch (e: IOException) {
                    break
                }
                handleIncoming(socket)
            }
        }
        acceptThread?.start()
    }

    private fun handleIncoming(socket: BluetoothSocket) {
        try {
            DataInputStream(socket.inputStream).use { input ->
                val header = input.readUTF()
                if (header != BluetoothConstants.PROTOCOL_HEADER) return

                val name = input.readUTF()
                val imageSize = input.readLong()

                if (imageSize <= 0 || imageSize > 50 * 1024 * 1024L) return

                val imageBytes = ByteArray(imageSize.toInt())
                var offset = 0
                while (offset < imageBytes.size) {
                    val read = input.read(imageBytes, offset, imageBytes.size - offset)
                    if (read < 0) break
                    offset += read
                }

                if (offset != imageBytes.size) return

                val destFile = FileUtils.createTemplateImageFile(this)
                FileOutputStream(destFile).use { it.write(imageBytes) }

                val template = Template(name = name, imagePath = destFile.absolutePath)
                TemplateRepository(this).insertTemplate(template)

                mainThread {
                    Toast.makeText(
                        this,
                        getString(R.string.msg_template_received, name),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (_: IOException) {
        } finally {
            try {
                socket.close()
            } catch (_: IOException) {
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
