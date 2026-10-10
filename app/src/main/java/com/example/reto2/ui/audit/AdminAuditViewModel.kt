package com.example.reto2.ui.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.reto2.data.model.CurrencyTransaction
import com.example.reto2.data.repository.FinanceRepository
import com.example.reto2.data.repository.FinnhubFinanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AuditFilter { ALL, VALID, REVIEW }

data class AuditUiState(
    val transactions: List<CurrencyTransaction> = emptyList(),
    val filter: AuditFilter = AuditFilter.ALL,
    val loading: Boolean = true,
    val error: String? = null
)

class AdminAuditViewModel(
    private val repository: FinanceRepository = FinnhubFinanceRepository()
) : ViewModel() {
    private val _state = MutableStateFlow(AuditUiState())
    val state = _state.asStateFlow()

    init {
        reload()
    }

    fun filterChanged(filter: AuditFilter) = _state.update { it.copy(filter = filter) }

    fun reload() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = withContext(Dispatchers.IO) { repository.allTransactions() }
            result.fold(
                onSuccess = { items -> _state.update { it.copy(transactions = items, loading = false) } },
                onFailure = { e -> _state.update { it.copy(error = e.message ?: "No se pudieron cargar las operaciones", loading = false) } }
            )
        }
    }
}
