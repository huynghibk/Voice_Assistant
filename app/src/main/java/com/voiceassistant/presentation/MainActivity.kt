package com.voiceassistant.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.voiceassistant.engine.audio.AudioRoutingManager
import com.voiceassistant.presentation.navigation.AppNavGraph
import com.voiceassistant.presentation.theme.VoiceAssistantTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var audioRoutingManager: AudioRoutingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioRoutingManager.start()
        enableEdgeToEdge()
        setContent {
            VoiceAssistantTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavGraph()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        audioRoutingManager.start()
        audioRoutingManager.refresh()
    }

    override fun onStop() {
        // Keep discovery while activity is partially visible; stop only on destroy
        super.onStop()
    }

    override fun onDestroy() {
        audioRoutingManager.stop()
        super.onDestroy()
    }
}
