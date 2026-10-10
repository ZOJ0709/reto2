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
        require(BuildConfig.FINNHUB_API_KEY.isNotBlank()) { "Configura FINNHUB_API_KEY en .env" }
        val url = URL("https://finnhub.io/api/v1/forex/rates?base=$base")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("X-Finnhub-Token", BuildConfig.FINNHUB_API_KEY)
        }
        try {
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                .bufferedReader().use { it.readText() }
            check(status in 200..299) { "Finnhub respondió HTTP $status" }
            val rates = JSONObject(body).getJSONObject("quote")
            val value = BigDecimal(rates.get(quote).toString())
            require(value.signum() > 0) { "Finnhub devolvió una tasa no válida" }
            FxRate(base, quote, value, System.currentTimeMillis())
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
            .get().await().documents.mapNotNull { document ->
                runCatching {
                    CurrencyTransaction(
                        id = document.id,
                        userId = document.getString("userId")!!,
                        side = TradeSide.valueOf(document.getString("side")!!),
                        baseCurrency = document.getString("baseCurrency")!!,
                        quoteCurrency = document.getString("quoteCurrency")!!,
                        amount = BigDecimal(document.getString("amount")!!),
                        referenceRate = BigDecimal(document.getString("referenceRate")!!),
                        appliedRate = BigDecimal(document.getString("appliedRate")!!),
                        deviationPercent = BigDecimal(document.getString("deviationPercent")!!),
                        createdAt = document.getLong("createdAt")!!,
                        status = TradeStatus.valueOf(document.getString("status")!!),
                        reason = document.getString("reason")
                    )
                }.getOrNull()
            }.sortedByDescending { it.createdAt }
    }
}
