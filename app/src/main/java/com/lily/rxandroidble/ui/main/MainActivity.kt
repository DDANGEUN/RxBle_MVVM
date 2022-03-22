package com.lily.rxandroidble.ui.main

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lily.rxandroidble.R
import com.lily.rxandroidble.databinding.ActivityMainBinding
import com.lily.rxandroidble.ui.adapter.BleListAdapter
import com.lily.rxandroidble.ui.dialog.WriteDialog
import com.lily.rxandroidble.util.Util
import com.lily.rxandroidble.viewmodel.BleViewModel
import com.polidea.rxandroidble2.exceptions.BleScanException
import com.polidea.rxandroidble2.scan.ScanResult
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : AppCompatActivity() {

    private val viewModel by viewModel<BleViewModel>()
    private var adapter: BleListAdapter? = null

    private var requestEnableBluetooth = false
    private var askGrant = false

    companion object {
        const val REQUEST_LOCATION_PERMISSION = 1
        val LOCATION_PERMISSION = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = DataBindingUtil.setContentView<ActivityMainBinding>(
            this,
            R.layout.activity_main
        )
        binding.viewModel = viewModel

        binding.rvBleList.setHasFixedSize(true)
        val layoutManager: RecyclerView.LayoutManager = LinearLayoutManager(this)
        binding.rvBleList.layoutManager = layoutManager


        adapter = BleListAdapter()
        binding.rvBleList.adapter = adapter
        adapter?.setItemClickListener(object : BleListAdapter.ItemClickListener {
            override fun onClick(view: View, scanResult: ScanResult?) {
                val device = scanResult?.bleDevice
                if (device != null) {
                    viewModel.connectDevice(device)
                }
            }
        })


        initObserver(binding)

        if (!hasPermissions(this, LOCATION_PERMISSION)) {
            requestPermissions(LOCATION_PERMISSION, REQUEST_LOCATION_PERMISSION)
        }

    }

    private fun initObserver(binding: ActivityMainBinding){
        viewModel.apply {
            bleException.observe(this@MainActivity, Observer {
                it.getContentIfNotHandled()?.let { reason ->
                    viewModel.stopScan()
                    bleThrowable(reason)
                }
            })

            listUpdate.observe(this@MainActivity, Observer {
                it.getContentIfNotHandled()?.let { scanResults ->
                    adapter?.setItem(scanResults)
                }
            })

            readTxt.observe(this@MainActivity,Observer{
                binding.txtRead.append("$it\n")
                if ((binding.txtRead.measuredHeight - binding.scroller.scrollY) <=
                    (binding.scroller.height + binding.txtRead.lineHeight)) {
                    binding.scroller.post {
                        binding.scroller.scrollTo(0, binding.txtRead.bottom)
                    }
                }
            })
        }

    }
    private fun bleThrowable(reason: Int) = when (reason) {
        BleScanException.BLUETOOTH_DISABLED -> {
            requestEnableBluetooth = true
            requestEnableBLE()
        }
        BleScanException.LOCATION_PERMISSION_MISSING->{
            requestPermissions(LOCATION_PERMISSION, REQUEST_LOCATION_PERMISSION)
        }
        else -> {
            Util.showNotification(bleScanExceptionReasonDescription(reason))
        }
    }

    private fun bleScanExceptionReasonDescription(reason: Int): String {
        return when (reason) {
            BleScanException.BLUETOOTH_CANNOT_START -> "Bluetooth cannot start"
            BleScanException.BLUETOOTH_DISABLED -> "Bluetooth disabled"
            BleScanException.BLUETOOTH_NOT_AVAILABLE -> "Bluetooth not available"
            BleScanException.LOCATION_SERVICES_DISABLED -> "Location Services disabled"
            BleScanException.SCAN_FAILED_ALREADY_STARTED -> "Scan failed because it has already started"
            BleScanException.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Scan failed because application registration failed"
            BleScanException.SCAN_FAILED_INTERNAL_ERROR -> "Scan failed because of an internal error"
            BleScanException.SCAN_FAILED_FEATURE_UNSUPPORTED -> "Scan failed because feature unsupported"
            BleScanException.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "Scan failed because out of hardware resources"
            BleScanException.UNDOCUMENTED_SCAN_THROTTLE -> "Undocumented scan throttle"
            BleScanException.UNKNOWN_ERROR_CODE -> "Unknown error"
            else -> "Unknown error"
        }
    }
    override fun onResume() {
        super.onResume()
        // finish app if the BLE is not supported
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            finish()
        }
    }

    fun onClickWrite(view: View){
        val writeDialog = WriteDialog(this@MainActivity, object : WriteDialog.WriteDialogListener {
            override fun onClickSend(data: String, type: String) {
                viewModel.writeData(data, type)
            }
        })
        writeDialog.show()
    }


    private val requestEnableBleResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            Util.showNotification("Bluetooth기능을 허용하였습니다.")
            viewModel.startScan()
        }
        else{
            Util.showNotification("Bluetooth기능을 켜주세요.")
            viewModel.stopScan()
        }
        requestEnableBluetooth = false
    }

    /**
     * Request BLE enable
     */
    private fun requestEnableBLE() {
        val bleEnableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        requestEnableBleResult.launch(bleEnableIntent)
    }

    private fun hasPermissions(context: Context?, permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (context?.let { ActivityCompat.checkSelfPermission(it, permission) }
                != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }
    // Permission check
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_LOCATION_PERMISSION -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show()
                } else {
                    requestPermissions(permissions, REQUEST_LOCATION_PERMISSION)
                    Toast.makeText(this, "Permissions must be granted!", Toast.LENGTH_SHORT).show()
                    askGrant = false
                }
            }
        }
    }


}