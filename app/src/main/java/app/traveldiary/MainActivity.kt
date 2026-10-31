package app.traveldiary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.traveldiary.ui.navigation.TravelDiaryNavHost
import app.traveldiary.ui.theme.TravelDiaryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as TravelDiaryApp).container
        setContent {
            val rangeColor by container.settings.rangeColor.collectAsStateWithLifecycle()
            TravelDiaryTheme(rangeColor = rangeColor) {
                TravelDiaryNavHost(container)
            }
        }
    }
}
