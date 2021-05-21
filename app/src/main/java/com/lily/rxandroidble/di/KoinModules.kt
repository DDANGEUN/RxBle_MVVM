package com.lily.rxandroidble.di

import com.lily.rxandroidble.BleRepository
import com.lily.rxandroidble.viewmodel.BleViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel { BleViewModel(get()) }
}

val repositoryModule = module{
    single{
        BleRepository()
    }
}