package com.example.reto2.ui.exchange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.reto2.data.model.CurrencyTransaction
import com.example.reto2.data.model.TradeSide
import com.example.reto2.data.model.TradeStatus
import com.example.reto2.data.repository.FinanceRepository
import com.example.reto2.data.repository.FinnhubFinanceRepository
import com.example.reto2.data.repository.FxRate
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal

data class ExchangeUiState(
    val quoteCurrency: String = "COP",
    val amount: String = "",
    val rate: FxRate? = null,
    val transactions: List<CurrencyTransaction> = emptyList(),
    val loadingRate: Boolean = false,
    val submitting: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

class ExchangeViewModel(
    private val repository: FinanceRepository = FinnhubFinanceRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {
    private val _state = MutableStateFlow(ExchangeUiState())
    val state = _state.asStateFlow()

    init {
        refreshRate()
        loadTransactions()
    }

    fun amountChanged(value: String) {
        if (value.isEmpty() || value.matches(Regex("^\\d*(\\.\\d*)?$"))) {
            _state.update { it.copy(amount = value, error = null, message = null) }
        }
    }

    fun currencyChanged(value: String) {
        _state.update { it.copy(quoteCurrency = value, rate = null) }
        refreshRate()
    }

    fun refreshRate() {
        val currency = _state.value.quoteCurrency
        viewModelScope.launch {
            _state.update { it.copy(loadingRate = true, error = null) }
            val result = withContext(Dispatchers.IO) { repository.latestRate("USD", currency) }
            result.fold(
                onSuccess = { rate -> _state.update { it.copy(rate = rate, loadingRate = false) } },
                onFailure = { e -> _state.update { it.copy(error = e.message ?: "No se pudo cargar la tasa", loadingRate = false) } }
            )
        }
    }

    fun trade(side: TradeSide) {
        val userId = auth.currentUser?.uid
        val amount = _state.value.amount.toBigDecimalOrNull()
        if (userId == null) {
            _state.update { it.copy(error = "Tu sesión expiró. Inicia sesión nuevamente") }
            return
        }
        if (amount == null || amount.signum() <= 0) {
            _state.update { it.copy(error = "Ingresa una cantidad válida en USD") }
            return
        }
        val currency = _state.value.quoteCurrency
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, message = null, error = null) }
            val freshRate = withContext(Dispatchers.IO) { repository.latestRate("USD", currency) }
            if (freshRate.isFailure) {
                _state.update { it.copy(error = freshRate.exceptionOrNull()?.message ?: "No se obtuvo una tasa reciente", submitting = false) }
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                repository.executeTrade(userId, side, "USD", currency, amount, freshRate.getOrThrow())
            }
            result.fold(
                onSuccess = { transaction ->
                    val history = withContext(Dispatchers.IO) { repository.userTransactions(userId).getOrDefault(emptyList()) }
                    _state.update {
                        it.copy(
                            rate = freshRate.getOrThrow(),
                            transactions = history,
                            amount = "",
                            submitting = false,
                            message = if (transaction.status == TradeStatus.SUCCESS) "Operación simulada guardada" else transaction.reason
                        )
                    }
                },
                onFailure = { e -> _state.update { it.copy(error = e.message ?: "No se pudo guardar la operación", submitting = false) } }
            )
        }
    }

    private fun loadTransactions() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.userTransactions(userId) }
            result.onSuccess { items -> _state.update { it.copy(transactions = items) } }
        }
    }
}
