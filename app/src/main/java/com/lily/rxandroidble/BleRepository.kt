package com.lily.rxandroidble


import android.bluetooth.BluetoothGattService
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDevice
import com.polidea.rxandroidble2.RxBleDeviceServices
import io.reactivex.disposables.Disposable
import java.util.*


class BleRepository {



    var rxBleConnection: RxBleConnection? = null
    var rxBleDeviceServices: RxBleDeviceServices? = null
    var bleGattServices: List<BluetoothGattService>? = null

    /**
     * Connect & Discover Services
     * @Saved rxBleConnection, rxBleDeviceServices, bleGattServices
     */
    fun bleConnectObservable(device: RxBleDevice): Disposable =
        device.establishConnection(false) // <-- autoConnect flag
            .subscribe({ _rxBleConnection->
                // All GATT operations are done through the rxBleConnection.
                rxBleConnection = _rxBleConnection

                // Discover services
                _rxBleConnection.discoverServices().subscribe ({ _rxBleDeviceServices ->
                    rxBleDeviceServices = _rxBleDeviceServices
                    bleGattServices = _rxBleDeviceServices.bluetoothGattServices
                },{ discoverServicesThrowable->
                    discoverServicesThrowable.printStackTrace()
                })

            },{ connectionThrowable->
                connectionThrowable.printStackTrace()
            })


    /**
     * Notification
     */
    fun bleNotification() = rxBleConnection
        ?.setupNotification(UUID.fromString(CHARACTERISTIC_RESPONSE_STRING))
        ?.doOnNext { notificationObservable->
            // Notification has been set up
        }
        ?.flatMap { notificationObservable -> notificationObservable }


    /**
     * Write Data
     */
    fun writeData(sendByteData: ByteArray) = rxBleConnection?.writeCharacteristic(
        UUID.fromString(CHARACTERISTIC_COMMAND_STRING),
        sendByteData
    )




}