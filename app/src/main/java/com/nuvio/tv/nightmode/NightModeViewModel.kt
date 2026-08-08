package com.nuvio.tv.nightmode

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NightModeViewModel @Inject constructor(
    val nightModeManager: NightModeManager
) : ViewModel()
