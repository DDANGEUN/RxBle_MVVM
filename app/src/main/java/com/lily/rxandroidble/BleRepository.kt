package com.lily.rxandroidble

import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDevice
import io.reactivex.disposables.Disposable
import java.util.*


class BleRepository {

    var rxBleConnection: RxBleConnection? = null
    private var mConnectSubscription: Disposable? = null

    /**
     * Connect & Discover Services
     * @Saved rxBleConnection
     */
    fun connectDevice(device: RxBleDevice){
        mConnectSubscription = device.establishConnection(false) // <-- autoConnect flag
            .flatMapSingle{ _rxBleConnection->
                // All GATT operations are done through the rxBleConnection.
                rxBleConnection = _rxBleConnection
                // Discover services
                _rxBleConnection.discoverServices()
            }.subscribe({
                // Services
            },{

            })
    }
    fun disconnectDevice(){
        mConnectSubscription?.dispose()
    }



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
     * Read
     */
    fun bleRead() =
        rxBleConnection?.readCharacteristic(UUID.fromString(CHARACTERISTIC_RESPONSE_STRING))


    /**
     * Write Data
     */
    fun writeData(sendByteData: ByteArray) = rxBleConnection?.writeCharacteristic(
        UUID.fromString(CHARACTERISTIC_COMMAND_STRING),
        sendByteData
    )




}