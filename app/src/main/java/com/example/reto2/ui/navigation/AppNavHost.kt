package com.example.reto2.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
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

private const val LOGIN = "login"
private const val REGISTER = "register"
private const val SIGNED_IN = "signed_in"

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val viewModel: AuthViewModel = viewModel()
    val state = viewModel.state.collectAsStateWithLifecycle().value
    NavHost(navController = navController, startDestination = if (state.user == null) LOGIN else SIGNED_IN,
        modifier = modifier) {
        composable(LOGIN) {
            LaunchedEffect(state.user) {
                if (state.user != null) navController.navigate(SIGNED_IN) {
                    popUpTo(LOGIN) { inclusive = true }
                }
            }
            AuthForm(state, false, viewModel::emailChanged, viewModel::passwordChanged,
                { viewModel.submit(false) }, { navController.navigate(REGISTER) })
        }
        composable(REGISTER) {
            LaunchedEffect(state.user) {
                if (state.user != null) navController.navigate(SIGNED_IN) {
                    popUpTo(REGISTER) { inclusive = true }
                }
            }
            AuthForm(state, true, viewModel::emailChanged, viewModel::passwordChanged,
                { viewModel.submit(true) }, { navController.popBackStack() })
        }
        composable(SIGNED_IN) {
            when {
                state.roleLoading -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("Cargando permisos", modifier = Modifier.padding(top = 12.dp))
                }
                state.isAdmin -> {
                    val auditViewModel: AdminAuditViewModel = viewModel()
                    val audit = auditViewModel.state.collectAsStateWithLifecycle().value
                    AdminAuditScreen(audit, auditViewModel::filterChanged, auditViewModel::reload, {
                        viewModel.logout()
                        navController.navigate(LOGIN) { popUpTo(SIGNED_IN) { inclusive = true } }
                    })
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
                        onLogout = {
                            viewModel.logout()
                            navController.navigate(LOGIN) { popUpTo(SIGNED_IN) { inclusive = true } }
                        }
                    )
                }
            }
        }
    }
}
