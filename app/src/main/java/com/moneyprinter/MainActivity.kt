package com.moneyprinter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.moneyprinter.ui.GameScreen
import com.moneyprinter.ui.theme.MoneyPrinterTheme

class MainActivity : ComponentActivity() {
    private lateinit var vm: GameViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm = ViewModelProvider(this, GameViewModelFactory(this))[GameViewModel::class.java]

        setContent {
            MoneyPrinterTheme {
                GameScreen(vm)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        vm.save()
    }
}
