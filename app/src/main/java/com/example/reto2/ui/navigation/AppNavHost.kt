package com.example.reto2.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.reto2.ui.auth.AuthForm
import com.example.reto2.ui.auth.AuthViewModel
import com.example.reto2.ui.audit.AdminAuditScreen
import com.example.reto2.ui.audit.AdminAuditViewModel
import com.example.reto2.ui.audit.AuditFilter
import com.example.reto2.ui.exchange.ExchangeScreen
import com.example.reto2.ui.exchange.ExchangeViewModel
import com.example.reto2.ui.profile.ProfileViewModel
import com.example.reto2.ui.profile.ProfileScreen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

private const val LOGIN = "login"
private const val REGISTER = "register"
private const val SIGNED_IN = "signed_in"
private const val PROFILE = "profile"

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val viewModel: AuthViewModel = viewModel()
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val coroutineScope = rememberCoroutineScope()
    val googleSignIn: () -> Unit = {
        viewModel.googleSignInStarted()
        coroutineScope.launch {
            try {
                val googleOption = GetGoogleIdOption.Builder()
                    .setServerClientId(context.getString(com.example.reto2.R.string.default_web_client_id))
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .build()
                val request = GetCredentialRequest.Builder().addCredentialOption(googleOption).build()
                val response = credentialManager.getCredential(context, request)
                val credential = response.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    viewModel.googleSignIn(GoogleIdTokenCredential.createFrom(credential.data).idToken)
                } else {
                    viewModel.googleSignInFailed("Google no devolvió una credencial válida")
                }
            } catch (error: Exception) {
                viewModel.googleSignInFailed(
                    error.localizedMessage ?: "No se pudo iniciar sesión con Google"
                )
            }
        }
        Unit
    }
    NavHost(navController = navController, startDestination = if (state.user == null) LOGIN else SIGNED_IN,
        modifier = modifier) {
        composable(LOGIN) {
            LaunchedEffect(state.user) {
                if (state.user != null) navController.navigate(SIGNED_IN) {
                    popUpTo(LOGIN) { inclusive = true }
                }
            }
            AuthForm(state, false, viewModel::emailChanged, viewModel::passwordChanged,
                viewModel::confirmPasswordChanged,
                googleSignIn,
                { viewModel.submit(false) }, { navController.navigate(REGISTER) })
        }
        composable(REGISTER) {
            LaunchedEffect(state.user) {
                if (state.user != null) navController.navigate(SIGNED_IN) {
                    popUpTo(REGISTER) { inclusive = true }
                }
            }
            AuthForm(state, true, viewModel::emailChanged, viewModel::passwordChanged,
                viewModel::confirmPasswordChanged,
                googleSignIn,
                { viewModel.submit(true) }, { navController.popBackStack() })
        }
        composable(SIGNED_IN) {
            LaunchedEffect(state.user) {
                if (state.user == null) {
                    navController.navigate(LOGIN) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            when {
                state.user == null -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("Cerrando sesión", modifier = Modifier.padding(top = 12.dp))
                }
                state.roleLoading -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("Cargando permisos", modifier = Modifier.padding(top = 12.dp))
                }
                state.isAdmin -> {
                    val auditViewModel: AdminAuditViewModel = viewModel()
                    val audit = auditViewModel.state.collectAsStateWithLifecycle().value
                    AdminAuditScreen(audit, auditViewModel::filterChanged, auditViewModel::reload, viewModel::logout)
                }
                else -> {
                    val exchangeViewModel: ExchangeViewModel = viewModel()
                    val exchange = exchangeViewModel.state.collectAsStateWithLifecycle().value
                    ExchangeScreen(
                        state = exchange,
                        onAmountChanged = exchangeViewModel::amountChanged,
                        onCurrencyChanged = exchangeViewModel::currencyChanged,
                        onRefresh = exchangeViewModel::refreshRate,
                        onTrade = exchangeViewModel::trade,
                        onLogout = viewModel::logout,
                        onOpenProfile = { navController.navigate(PROFILE) }
                    )
                }
            }
        }
        composable(PROFILE) {
            val profileViewModel: ProfileViewModel = viewModel()
            val profile = profileViewModel.state.collectAsStateWithLifecycle().value
            val photoPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.PickVisualMedia()
            ) { uri -> uri?.let(profileViewModel::upload) }
            ProfileScreen(
                email = state.user?.email.orEmpty(),
                state = profile,
                onChoosePhoto = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onBack = { navController.popBackStack() },
                onPhotoCaptured = profileViewModel::uploadFile
            )
        }
    }
}
