package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import com.example.ui.ExpenseViewModel
import com.example.ui.MainScreen
import com.example.ui.screens.GoogleAuthScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : FragmentActivity() {

    private val viewModel: ExpenseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configure App Check before UI or Firebase operations
        com.example.util.configureAppCheck(this, intent)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            MyApplicationTheme(themeMode = uiState.themeMode) {
                if (!uiState.isGoogleSignedIn) {
                    GoogleAuthScreen(
                        isSessionExpired = uiState.isSessionExpired,
                        errorMessage = uiState.authErrorMessage,
                        onSignInSuccess = {
                            viewModel.onGoogleSignInSuccess()
                        },
                        onError = { err ->
                            viewModel.setAuthErrorMessage(err)
                        }
                    )
                } else {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkSessionAndRefreshActivity()
    }

    override fun onPause() {
        super.onPause()
        // If biometric lock is enabled, lock when the app goes into background
        viewModel.lockApp()
    }
}
