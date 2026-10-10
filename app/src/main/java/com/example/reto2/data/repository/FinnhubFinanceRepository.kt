package com.example.reto2.data.repository

import com.example.reto2.BuildConfig
import com.example.reto2.data.model.CurrencyTransaction
import com.example.reto2.data.model.DeviationRule
import com.example.reto2.data.model.TradeSide
import com.example.reto2.data.model.TradeStatus
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class FinnhubFinanceRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : FinanceRepository {

    override suspend fun latestRate(base: String, quote: String): Result<FxRate> = runCatching {
        runCatching { finnhubRate(base, quote) }.getOrElse { primaryError ->
            runCatching { frankfurterRate(base, quote, primaryError) }.getOrElse { fallbackError ->
                throw IllegalStateException(
                    "No hay cotización. Finnhub: ${primaryError.message ?: "error de conexión"}. " +
                        "Frankfurter: ${fallbackError.message ?: "error de conexión"}"
                )
            }
        }
    }

    private fun finnhubRate(base: String, quote: String): FxRate {
        require(BuildConfig.FINNHUB_API_KEY.isNotBlank()) { "Configura FINNHUB_API_KEY en .env" }
        val url = URL("https://finnhub.io/api/v1/forex/rates?base=$base")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("X-Finnhub-Token", BuildConfig.FINNHUB_API_KEY)
        }
        return try {
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                .bufferedReader().use { it.readText() }
            check(status in 200..299) { "Finnhub respondió HTTP $status" }
            val value = BigDecimal(JSONObject(body).getJSONObject("quote").get(quote).toString())
            require(value.signum() > 0) { "Finnhub devolvió una tasa no válida" }
            FxRate(base, quote, value, System.currentTimeMillis(), "Finnhub")
        } finally {
            connection.disconnect()
        }
    }

    private fun frankfurterRate(base: String, quote: String, primaryError: Throwable): FxRate {
        val url = URL("https://api.frankfurter.dev/v2/rate/$base/$quote")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        return try {
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                .bufferedReader().use { it.readText() }
            check(status in 200..299) { "Frankfurter respondió HTTP $status" }
            val json = JSONObject(body)
            val value = BigDecimal(json.get("rate").toString())
            require(value.signum() > 0) { "Frankfurter devolvió una tasa no válida" }
            val reason = primaryError.message?.substringBefore('.') ?: "sin acceso"
            FxRate(base, quote, value, System.currentTimeMillis(), "Frankfurter · respaldo de $reason")
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun executeTrade(
        userId: String,
        side: TradeSide,
        base: String,
        quote: String,
        amount: BigDecimal,
        rate: FxRate
    ): Result<CurrencyTransaction> = runCatching {
        require(amount.signum() > 0) { "Ingresa una cantidad mayor que cero" }
        val appliedRate = rate.value
        val withinLimit = DeviationRule.isAllowed(appliedRate, rate.value)
        val transaction = CurrencyTransaction(
            id = UUID.randomUUID().toString(),
            userId = userId,
            side = side,
            baseCurrency = base,
            quoteCurrency = quote,
            amount = amount,
            referenceRate = rate.value,
            appliedRate = appliedRate,
            deviationPercent = DeviationRule.deviationPercent(appliedRate, rate.value),
            createdAt = System.currentTimeMillis(),
            status = if (withinLimit) TradeStatus.SUCCESS else TradeStatus.REJECTED,
            reason = if (withinLimit) null else "La tasa superó el límite permitido de 2,5 %"
        )
        val data = mapOf(
            "userId" to transaction.userId,
            "side" to transaction.side.name,
            "baseCurrency" to transaction.baseCurrency,
            "quoteCurrency" to transaction.quoteCurrency,
            "amount" to transaction.amount.toPlainString(),
            "amountValue" to transaction.amount.toDouble(),
            "referenceRate" to transaction.referenceRate.toPlainString(),
            "referenceRateValue" to transaction.referenceRate.toDouble(),
            "appliedRate" to transaction.appliedRate.toPlainString(),
            "appliedRateValue" to transaction.appliedRate.toDouble(),
            "deviationPercent" to transaction.deviationPercent.toPlainString(),
            "deviationPercentValue" to transaction.deviationPercent.toDouble(),
            "createdAt" to transaction.createdAt,
            "status" to transaction.status.name,
            "reason" to transaction.reason
        )
        firestore.collection("transactions").document(transaction.id).set(data).await()
        transaction
    }

    override suspend fun userTransactions(userId: String): Result<List<CurrencyTransaction>> = runCatching {
        firestore.collection("transactions")
            .whereEqualTo("userId", userId)
            .get().await().documents.mapNotNull { it.toTransaction() }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun allTransactions(): Result<List<CurrencyTransaction>> = runCatching {
        firestore.collection("transactions").get().await().documents
            .mapNotNull { it.toTransaction() }
            .sortedByDescending { it.createdAt }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toTransaction(): CurrencyTransaction? =
        runCatching {
            CurrencyTransaction(
                id = id,
                userId = getString("userId")!!,
                side = TradeSide.valueOf(getString("side")!!),
                baseCurrency = getString("baseCurrency")!!,
                quoteCurrency = getString("quoteCurrency")!!,
                amount = BigDecimal(getString("amount")!!),
                referenceRate = BigDecimal(getString("referenceRate")!!),
                appliedRate = BigDecimal(getString("appliedRate")!!),
                deviationPercent = BigDecimal(getString("deviationPercent")!!),
                createdAt = getLong("createdAt")!!,
                status = TradeStatus.valueOf(getString("status")!!),
                reason = getString("reason")
            )
        }.getOrNull()
}
