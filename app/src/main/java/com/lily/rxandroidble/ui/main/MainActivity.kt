package com.lily.rxandroidble.ui.main

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
import androidx.lifecycle.observe
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lily.rxandroidble.PERMISSIONS
import com.lily.rxandroidble.R
import com.lily.rxandroidble.REQUEST_ALL_PERMISSION
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

        if (!hasPermissions(this, PERMISSIONS)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(PERMISSIONS, REQUEST_ALL_PERMISSION)
            }
        }

    }

    private fun initObserver(binding: ActivityMainBinding){
        viewModel.apply {
            requestEnableBLE.observe(this@MainActivity, Observer {
                it.getContentIfNotHandled()?.let { reason ->
                    viewModel.stopScan()
                    when (reason) {
                        BleScanException.BLUETOOTH_CANNOT_START -> Util.showNotification("BLUETOOTH CANNOT START")
                        BleScanException.BLUETOOTH_DISABLED -> {
                            requestEnableBluetooth = true
                            requestEnableBLE()
                        }
                        BleScanException.BLUETOOTH_NOT_AVAILABLE -> Util.showNotification("블루투스 지원하지 않는 기기입니다.")
                        BleScanException.LOCATION_PERMISSION_MISSING, BleScanException.LOCATION_SERVICES_DISABLED -> {

                            if (!askGrant) {
                                askGrant = true
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    requestPermissions(PERMISSIONS, REQUEST_ALL_PERMISSION)
                                }
                            }

                        }
                        BleScanException.SCAN_FAILED_ALREADY_STARTED -> Util.showNotification("SCAN FAILED ALREADY STARTED")
                        BleScanException.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> Util.showNotification(
                            "SCAN FAILED APPLICATION REGISTRATION FAILED"
                        )
                        BleScanException.SCAN_FAILED_INTERNAL_ERROR -> Util.showNotification("SCAN FAILED INTERNAL ERROR")
                        BleScanException.SCAN_FAILED_FEATURE_UNSUPPORTED -> Util.showNotification("SCAN FAILED FEATURE UNSUPPORTED")
                        BleScanException.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> Util.showNotification(
                            "SCAN FAILED OUT OF HARDWARE RESOURCES"
                        )
                        BleScanException.UNDOCUMENTED_SCAN_THROTTLE -> Util.showNotification("UNDOCUMENTED SCAN THROTTLE")
                        else -> Util.showNotification("UNKNOWN ERROR CODE")
                    }
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
            val intent = result.data
            Util.showNotification("Bluetooth기능을 허용하였습니다.")

        }
        else{
            Util.showNotification("Bluetooth기능을 켜주세요.")
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null) {
            for (permission in permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                    return false
                }
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
            REQUEST_ALL_PERMISSION -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show()
                } else {
                    requestPermissions(permissions, REQUEST_ALL_PERMISSION)
                    Toast.makeText(this, "Permissions must be granted", Toast.LENGTH_SHORT).show()
                    askGrant = false
                }
            }
        }
    }


}