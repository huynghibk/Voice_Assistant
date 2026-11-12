package com.voiceassistant.presentation.navigation

import androidx.lifecycle.ViewModel
import com.voiceassistant.data.ingress.PendingTextIngress
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppNavViewModel @Inject constructor(
    val pendingTextIngress: PendingTextIngress
) : ViewModel()
