package com.laplog.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.laplog.app.data.PreferencesManager
import com.laplog.app.data.database.AppDatabase
import com.laplog.app.ui.StopwatchScreen
import com.laplog.app.ui.theme.StopwatchTheme

class MainActivity : ComponentActivity() {
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferencesManager = PreferencesManager(applicationContext)
        database = AppDatabase.getDatabase(applicationContext)

        setContent {
            StopwatchTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StopwatchScreen(
                        preferencesManager = preferencesManager,
                        sessionDao = database.sessionDao(),
                        onKeepScreenOn = { keepOn ->
                            if (keepOn) {
                                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            } else {
                                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                        }
                    )
                }
            }
        }
    }
}
