package com.example.reto2.ui.audit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.reto2.data.model.CurrencyTransaction
import com.example.reto2.data.model.DeviationRule
import com.example.reto2.data.model.TradeStatus
import java.text.DateFormat
import java.util.Date

@Composable
fun AdminAuditScreen(
    state: AuditUiState,
    onFilter: (AuditFilter) -> Unit,
    onReload: () -> Unit,
    onLogout: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Auditoría", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Operaciones simuladas · control del 2,5 %", color = MaterialTheme.colorScheme.tertiary)
            }
            OutlinedButton(onClick = onLogout) { Text("Salir") }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AuditFilter.entries.forEach { filter ->
                if (filter == state.filter) Button(onClick = { onFilter(filter) }) {
                    Text(filter.label())
                } else OutlinedButton(onClick = { onFilter(filter) }) {
                    Text(filter.label())
                }
            }
            OutlinedButton(onClick = onReload) { Text("Actualizar") }
        }
        Spacer(Modifier.height(10.dp))
        when {
            state.loading -> CircularProgressIndicator()
            state.error != null -> Text(state.error, color = MaterialTheme.colorScheme.error)
            else -> {
                val filtered = state.transactions.filter { transaction ->
                    val valid = transaction.status == TradeStatus.SUCCESS &&
                        DeviationRule.isAllowed(transaction.appliedRate, transaction.referenceRate)
                    when (state.filter) {
                        AuditFilter.ALL -> true
                        AuditFilter.VALID -> valid
                        AuditFilter.REVIEW -> !valid
                    }
                }
                Text("${filtered.size} operaciones", style = MaterialTheme.typography.titleMedium)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.id }) { transaction -> AuditTransactionCard(transaction) }
                }
            }
        }
    }
}

@Composable
private fun AuditTransactionCard(transaction: CurrencyTransaction) {
    val valid = transaction.status == TradeStatus.SUCCESS &&
        DeviationRule.isAllowed(transaction.appliedRate, transaction.referenceRate)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "${if (transaction.side.name == "BUY") "Compra" else "Venta"} · ${transaction.amount} ${transaction.baseCurrency}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text("Usuario: ${transaction.userId}", style = MaterialTheme.typography.bodySmall)
            Text("Referencia: ${transaction.referenceRate} ${transaction.quoteCurrency}")
            val calculatedDeviation = DeviationRule.deviationPercent(
                transaction.appliedRate, transaction.referenceRate
            )
            Text("Aplicada: ${transaction.appliedRate} · diferencia ${calculatedDeviation}%")
            Text(
                if (valid) "Dentro del límite · operación exitosa" else "Revisar · ${transaction.reason ?: transaction.status}",
                color = if (valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )
            Text(DateFormat.getDateTimeInstance().format(Date(transaction.createdAt)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

private fun AuditFilter.label() = when (this) {
    AuditFilter.ALL -> "Todas"
    AuditFilter.VALID -> "Correctas"
    AuditFilter.REVIEW -> "Revisar"
}
