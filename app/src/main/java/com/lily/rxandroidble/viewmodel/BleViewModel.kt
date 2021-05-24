package com.lily.rxandroidble.viewmodel


import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lily.rxandroidble.BleRepository
import com.lily.rxandroidble.MyApplication
import com.lily.rxandroidble.SERVICE_STRING
import com.lily.rxandroidble.util.Event
import com.lily.rxandroidble.util.Util
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDevice
import com.polidea.rxandroidble2.exceptions.BleScanException
import com.polidea.rxandroidble2.scan.ScanFilter
import com.polidea.rxandroidble2.scan.ScanResult
import com.polidea.rxandroidble2.scan.ScanSettings
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.launch
import java.nio.charset.Charset
import java.util.*
import kotlin.concurrent.schedule


class BleViewModel(private val repository: BleRepository) : ViewModel() {

    private lateinit var mScanSubscription: Disposable
    private var mConnectSubscription: Disposable? = null
    private var mNotificationSubscription: Disposable? = null
    private var mWriteSubscription: Disposable? = null
    private lateinit var connectionStateDisposable: Disposable

    val TAG = "BleViewModel"

    // View Databinding
    var statusTxt = ObservableField("Press the Scan button to start Ble Scan.")
    var scanVisible = ObservableBoolean(true)
    var readTxt = MutableLiveData("")
    var connectedTxt = ObservableField("")
    var isScanning = ObservableBoolean(false)
    var isConnecting = ObservableBoolean(false)
    var isConnect = ObservableBoolean(false)

    var isRead = false


    private val _requestEnableBLE = MutableLiveData<Event<Int>>()
    val requestEnableBLE: LiveData<Event<Int>>
        get() = _requestEnableBLE

    private val _listUpdate = MutableLiveData<Event<ArrayList<ScanResult>?>>()
    val listUpdate: LiveData<Event<ArrayList<ScanResult>?>>
        get() = _listUpdate



    // scan results
    private var scanResults: ArrayList<ScanResult>? = ArrayList()
    private val rxBleClient: RxBleClient = RxBleClient.create(MyApplication.applicationContext())



    /**
     *  Start BLE Scan
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun onClickScan() {
        startScan()
    }

    private fun startScan() {
        scanVisible.set(true)
        //scan filter
        val scanFilter: ScanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(UUID.fromString(SERVICE_STRING)))
            //.setDeviceName("")
            .build()
        // scan settings
        // set low power scan mode
        val settings: ScanSettings = ScanSettings.Builder()
            //.setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()


        scanResults = ArrayList() //list 초기화


        mScanSubscription = rxBleClient.scanBleDevices(settings, scanFilter)
            .subscribe({ scanResult ->
                addScanResult(scanResult)
            }, { throwable ->
                if (throwable is BleScanException) {
                    _requestEnableBLE.postValue(Event(throwable.reason))
                } else {
                    Util.showNotification("UNKNOWN ERROR")
                }

            })


        isScanning.set(true)

        Timer("SettingUp", false).schedule(4000) { stopScan() }

    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun stopScan() {
        mScanSubscription.dispose()
        isScanning.set(false)
        statusTxt.set("Scan finished. Click on the name to connect to the device.")

        scanResults = ArrayList() //list 초기화
    }

    /**
     * Add scan result
     */
    private fun addScanResult(result: ScanResult) {
        // get scanned device
        val device = result.bleDevice
        // get scanned device MAC address
        val deviceAddress = device.macAddress
        // add the device to the result list
        for (dev in scanResults!!) {
            if (dev.bleDevice.macAddress == deviceAddress) return
        }
        scanResults?.add(result)
        // log
        statusTxt.set("add scanned device: $deviceAddress")
        _listUpdate.postValue(Event(scanResults))
    }


    fun onClickDisconnect() {
        mConnectSubscription?.dispose()
    }



    fun connectDevice(device: RxBleDevice) {
        // register connectionStateListener
        connectionStateDisposable = device.observeConnectionStateChanges()
            .subscribe(
                { connectionState ->
                    connectionStateListener(device, connectionState)
                }
            ) { throwable ->
                throwable.printStackTrace()
            }

        mConnectSubscription = repository.bleConnectObservable(device)
    }


    private fun connectionStateListener(
        device: RxBleDevice,
        connectionState: RxBleConnection.RxBleConnectionState
    ){
        when(connectionState){
            RxBleConnection.RxBleConnectionState.CONNECTED -> {
                isConnect.set(true)
                isConnecting.set(false)
                scanVisible.set(false)
                connectedTxt.set("${device.macAddress} Connected.")
            }
            RxBleConnection.RxBleConnectionState.CONNECTING -> {
                isConnecting.set(true)
            }
            RxBleConnection.RxBleConnectionState.DISCONNECTED -> {
                isConnect.set(false)
                isConnecting.set(false)
                scanVisible.set(true)
                scanResults = ArrayList()
                _listUpdate.postValue(Event(scanResults))
            }
            RxBleConnection.RxBleConnectionState.DISCONNECTING -> {

            }
        }
    }


    fun onClickRead() {
        if(!isRead) {
            mNotificationSubscription = repository.bleNotification()
                ?.subscribe({ bytes ->
                    // Given characteristic has been changes, here is the value.
                    readTxt.postValue(byteArrayToHex(bytes))
                    isRead = true

                }, { throwable ->
                    // Handle an error here
                    throwable.printStackTrace()
                    mConnectSubscription?.dispose()
                    isRead = false
                })
        }else{
            isRead = false
            mNotificationSubscription?.dispose()
        }

    }
    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4)
                    + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }


    fun byteArrayToHex(a: ByteArray): String {
        val sb = java.lang.StringBuilder(a.size * 2)
        for (b in a) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    fun writeData(data: String, type: String) {

        var sendByteData: ByteArray? = null
        when(type){
            "string" -> {
                sendByteData = data.toByteArray(Charset.defaultCharset())
            }
            "byte" -> {
                if (data.length % 2 != 0) {
                    Util.showNotification("Byte Size Error")
                    return
                }
                sendByteData = hexStringToByteArray(data)
            }
        }
        if (sendByteData != null) {
            mWriteSubscription = repository.writeData(sendByteData)?.subscribe({ writeBytes ->
                // Written data.
                val str: String = byteArrayToHex(writeBytes)
                Log.d("writtenBytes", str)
                viewModelScope.launch{
                    Util.showNotification("`$str` is written.")
                }
            }, { throwable ->
                // Handle an error here.
                throwable.printStackTrace()
            })
        }


    }

    override fun onCleared() {
        super.onCleared()
        mScanSubscription.dispose()
        mConnectSubscription?.dispose()
        mWriteSubscription?.dispose()
        connectionStateDisposable.dispose()
    }

}