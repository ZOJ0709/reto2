package com.example.reto2.ui.exchange

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.reto2.data.model.CurrencyTransaction
import com.example.reto2.data.model.TradeSide
import java.text.DateFormat
import java.util.Date

@Composable
fun ExchangeScreen(
    state: ExchangeUiState,
    onAmountChanged: (String) -> Unit,
    onCurrencyChanged: (String) -> Unit,
    onRefresh: () -> Unit,
    onTrade: (TradeSide) -> Unit,
    onLogout: () -> Unit,
    onOpenProfile: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Reto FX", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Compra y vende divisas", color = MaterialTheme.colorScheme.tertiary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpenProfile) { Text("Perfil") }
                OutlinedButton(onClick = onLogout) { Text("Salir") }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Operaciones simuladas", color = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("COP", "EUR", "GBP", "JPY").forEach { currency ->
                if (currency == state.quoteCurrency) Button(onClick = {}) { Text(currency) }
                else OutlinedButton(onClick = { onCurrencyChanged(currency) }) { Text(currency) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Tasa de referencia · USD/${state.quoteCurrency}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                when {
                    state.loadingRate -> CircularProgressIndicator()
                    state.rate != null -> Text(
                        state.rate.value.stripTrailingZeros().toPlainString(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    else -> Text("Sin cotización disponible", color = MaterialTheme.colorScheme.error)
                }
                state.rate?.let {
                    Text("Fuente: ${it.source}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary)
                }
                TextButtonCompat(onClick = onRefresh, label = "Actualizar")
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.amount,
            onValueChange = onAmountChanged,
            label = { Text("Cantidad en USD") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        state.rate?.let { rate ->
            val total = state.amount.toBigDecimalOrNull()?.multiply(rate.value)
            if (total != null) Text("Recibirás aprox. ${total.stripTrailingZeros().toPlainString()} ${rate.quote}")
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { onTrade(TradeSide.BUY) }, enabled = !state.submitting && state.rate != null,
                modifier = Modifier.weight(1f)) { Text("Comprar USD") }
            OutlinedButton(onClick = { onTrade(TradeSide.SELL) }, enabled = !state.submitting && state.rate != null,
                modifier = Modifier.weight(1f)) { Text("Vender USD") }
        }
        if (state.submitting) CircularProgressIndicator(Modifier.padding(top = 8.dp))
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(18.dp))
        Text("Mis operaciones", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.transactions, key = { it.id }) { transaction -> TransactionRow(transaction) }
        }
    }
}

@Composable
private fun TextButtonCompat(onClick: () -> Unit, label: String) {
    androidx.compose.material3.TextButton(onClick = onClick) { Text(label) }
}

@Composable
private fun TransactionRow(transaction: CurrencyTransaction) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("${if (transaction.side == TradeSide.BUY) "Compra" else "Venta"} ${transaction.amount} ${transaction.baseCurrency}",
                fontWeight = FontWeight.SemiBold)
            Text("${transaction.quoteCurrency}: ${transaction.appliedRate} · desviación ${transaction.deviationPercent}%")
            Text("${transaction.status} · ${DateFormat.getDateTimeInstance().format(Date(transaction.createdAt))}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}
