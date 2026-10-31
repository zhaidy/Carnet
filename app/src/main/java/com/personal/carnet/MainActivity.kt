package com.personal.carnet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.carnet.ui.CarnetRoot
import com.personal.carnet.ui.MainViewModel
import com.personal.carnet.ui.glass.Palette

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as CarnetApp).repository
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Palette.Violet, onSurface = Palette.Ink)) {
                val vm: MainViewModel = viewModel { MainViewModel(repository) }
                CarnetRoot(vm)
            }
        }
    }
}
