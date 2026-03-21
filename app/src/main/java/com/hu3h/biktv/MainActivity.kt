package com.hu3h.biktv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.hu3h.biktv.server.KtorServer
import com.hu3h.biktv.ui.KtvMainScreen
import com.hu3h.biktv.ui.theme.BiKTVTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KtorServer.start(this, 8889)
        setContent {
            BiKTVTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    KtvMainScreen(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        KtorServer.stop()
    }
}
