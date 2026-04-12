package de.badaboomi.gamesheetmanager.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import de.badaboomi.gamesheetmanager.R
import de.badaboomi.gamesheetmanager.data.Template
import de.badaboomi.gamesheetmanager.databinding.ActivityBluetoothDeviceListBinding

/**
 * Activity for picking a paired or discovered Bluetooth device to send a template to.
 *
 * Expects [EXTRA_TEMPLATE_ID], [EXTRA_TEMPLATE_NAME] and [EXTRA_TEMPLATE_IMAGE_PATH] in the
 * starting intent.
 */
class BluetoothDeviceListActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TEMPLATE_ID = "template_id"
        const val EXTRA_TEMPLATE_NAME = "template_name"
        const val EXTRA_TEMPLATE_IMAGE_PATH = "template_image_path"
    }

    private lateinit var binding: ActivityBluetoothDeviceListBinding
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val devices = mutableListOf<BluetoothDevice>()
    private lateinit var deviceAdapter: DeviceAdapter
    private var templateToSend: Template? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            loadPairedDevices()
        } else {
            Toast.makeText(this, R.string.msg_bt_permission_denied, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    @Suppress("DEPRECATION")
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let {
                        if (!devices.contains(it)) {
                            devices.add(it)
                            deviceAdapter.notifyDataSetChanged()
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    binding.progressDiscovery.visibility = View.GONE
                    binding.btnDiscovery.isEnabled = true
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBluetoothDeviceListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_bt_device_picker)

        val templateId = intent.getLongExtra(EXTRA_TEMPLATE_ID, -1L)
        val templateName = intent.getStringExtra(EXTRA_TEMPLATE_NAME) ?: ""
        val templateImagePath = intent.getStringExtra(EXTRA_TEMPLATE_IMAGE_PATH) ?: ""

        if (templateId < 0 || templateImagePath.isEmpty()) {
            finish()
            return
        }
        templateToSend = Template(id = templateId, name = templateName, imagePath = templateImagePath)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, R.string.msg_bt_not_supported, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, R.string.msg_bt_not_enabled, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        deviceAdapter = DeviceAdapter()
        binding.listDevices.adapter = deviceAdapter
        binding.listDevices.setOnItemClickListener { _, _, position, _ ->
            sendToDevice(devices[position])
        }

        binding.btnDiscovery.setOnClickListener { startDiscovery() }

        checkPermissionsAndLoad()
    }

    private fun checkPermissionsAndLoad() {
        val required = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                required.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) {
                required.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }
        if (required.isEmpty()) {
            loadPairedDevices()
        } else {
            permissionLauncher.launch(required.toTypedArray())
        }
    }

    @Suppress("MissingPermission")
    private fun loadPairedDevices() {
        devices.clear()
        val paired = bluetoothAdapter?.bondedDevices ?: emptySet()
        devices.addAll(paired)
        deviceAdapter.notifyDataSetChanged()

        binding.tvEmptyDevices.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
        binding.listDevices.visibility = if (devices.isEmpty()) View.GONE else View.VISIBLE
    }

    @Suppress("MissingPermission")
    private fun startDiscovery() {
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter?.cancelDiscovery()
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(discoveryReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(discoveryReceiver, filter)
        }
        bluetoothAdapter?.startDiscovery()
        binding.progressDiscovery.visibility = View.VISIBLE
        binding.btnDiscovery.isEnabled = false
        binding.tvEmptyDevices.visibility = View.GONE
        binding.listDevices.visibility = View.VISIBLE
    }

    private fun sendToDevice(device: BluetoothDevice) {
        val template = templateToSend ?: return
        binding.progressSending.visibility = View.VISIBLE
        binding.listDevices.isEnabled = false
        binding.btnDiscovery.isEnabled = false

        @Suppress("MissingPermission")
        bluetoothAdapter?.cancelDiscovery()

        BluetoothSendManager.send(
            device = device,
            template = template,
            onSuccess = {
                runOnUiThread {
                    binding.progressSending.visibility = View.GONE
                    Toast.makeText(
                        this,
                        getString(R.string.msg_template_sent, template.name),
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            },
            onError = { e ->
                runOnUiThread {
                    binding.progressSending.visibility = View.GONE
                    binding.listDevices.isEnabled = true
                    binding.btnDiscovery.isEnabled = true
                    Toast.makeText(
                        this,
                        getString(R.string.msg_template_send_error, e.localizedMessage ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(discoveryReceiver)
        } catch (_: IllegalArgumentException) {
        }
        @Suppress("MissingPermission")
        bluetoothAdapter?.cancelDiscovery()
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // -------------------------------------------------------------------------
    // Inner adapter
    // -------------------------------------------------------------------------

    inner class DeviceAdapter : ArrayAdapter<BluetoothDevice>(
        this@BluetoothDeviceListActivity, 0, devices
    ) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val device = devices[position]
            val view = convertView ?: layoutInflater.inflate(
                android.R.layout.simple_list_item_2, parent, false
            )
            @Suppress("MissingPermission")
            view.findViewById<TextView>(android.R.id.text1).text =
                device.name ?: getString(R.string.label_unknown_device)
            view.findViewById<TextView>(android.R.id.text2).text = device.address
            return view
        }
    }
}
