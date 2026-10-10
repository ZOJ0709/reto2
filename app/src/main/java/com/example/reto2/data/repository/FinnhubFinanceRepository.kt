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
            runCatching { frankfurterRate(base, quote) }.getOrElse { fallbackError ->
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

    private fun frankfurterRate(base: String, quote: String): FxRate {
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
            FxRate(base, quote, value, System.currentTimeMillis(), "Frankfurter")
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
        val balance = userBalance(userId).getOrThrow()
        val isUsdCop = quote == "COP"
        val inputCurrency = if (isUsdCop && side == TradeSide.BUY) "COP"
            else if (isUsdCop || side == TradeSide.BUY) "USD" else quote
        val inputAmount = if (isUsdCop && side == TradeSide.BUY) amount.multiply(rate.value) else amount
        val outputAmount = when {
            isUsdCop && side == TradeSide.BUY -> amount
            isUsdCop -> amount.multiply(rate.value)
            side == TradeSide.BUY -> amount.multiply(rate.value)
            else -> amount.divide(rate.value, 8, java.math.RoundingMode.HALF_UP)
        }
        val outputCurrency = when {
            isUsdCop && side == TradeSide.BUY -> "USD"
            isUsdCop -> "COP"
            side == TradeSide.BUY -> quote
            else -> "USD"
        }
        val usdBalanceDelta = when {
            isUsdCop && side == TradeSide.BUY -> amount
            isUsdCop -> amount.negate()
            side == TradeSide.BUY -> amount.negate()
            else -> outputAmount
        }
        val requiresUsdBalance = (isUsdCop && side == TradeSide.SELL) || (!isUsdCop && side == TradeSide.BUY)
        val usdDebit = if (isUsdCop && side == TradeSide.SELL) amount else if (!isUsdCop && side == TradeSide.BUY) amount else BigDecimal.ZERO
        val hasSufficientUsd = !requiresUsdBalance || usdDebit <= balance
        val appliedRate = rate.value
        val withinLimit = DeviationRule.isAllowed(appliedRate, rate.value)
        val successful = withinLimit && hasSufficientUsd
        val transaction = CurrencyTransaction(
            id = UUID.randomUUID().toString(),
            userId = userId,
            side = side,
            baseCurrency = base,
            quoteCurrency = quote,
            amount = if (isUsdCop || side == TradeSide.SELL) amount else outputAmount,
            referenceRate = rate.value,
            appliedRate = appliedRate,
            deviationPercent = DeviationRule.deviationPercent(appliedRate, rate.value),
            createdAt = System.currentTimeMillis(),
            status = if (successful) TradeStatus.SUCCESS else TradeStatus.REJECTED,
            reason = when {
                !withinLimit -> "La tasa superó el límite permitido de 2,5 %"
                !hasSufficientUsd -> "Saldo USD insuficiente para completar esa operación"
                else -> null
            },
            inputAmount = inputAmount,
            inputCurrency = inputCurrency,
            outputAmount = outputAmount,
            outputCurrency = outputCurrency,
            usdBalanceDelta = usdBalanceDelta
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
            "reason" to transaction.reason,
            "inputAmount" to transaction.inputAmount.toPlainString(),
            "inputAmountValue" to transaction.inputAmount.toDouble(),
            "inputCurrency" to transaction.inputCurrency,
            "outputAmount" to transaction.outputAmount.toPlainString(),
            "outputAmountValue" to transaction.outputAmount.toDouble(),
            "outputCurrency" to transaction.outputCurrency,
            "usdBalanceDelta" to transaction.usdBalanceDelta!!.toPlainString(),
            "usdBalanceDeltaValue" to transaction.usdBalanceDelta.toDouble()
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

    override suspend fun userBalance(userId: String): Result<BigDecimal> =
        userTransactions(userId).map { transactions ->
            transactions.asSequence()
                .filter { it.status == TradeStatus.SUCCESS }
                .fold(BigDecimal.ZERO) { balance, transaction ->
                    balance + (transaction.usdBalanceDelta ?: if (transaction.side == TradeSide.BUY) transaction.amount else transaction.amount.negate())
                }
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
                reason = getString("reason"),
                inputAmount = getString("inputAmount")?.let(::BigDecimal) ?: BigDecimal(getString("amount")!!),
                inputCurrency = getString("inputCurrency") ?: getString("baseCurrency")!!,
                outputAmount = getString("outputAmount")?.let(::BigDecimal) ?: BigDecimal(getString("amount")!!),
                outputCurrency = getString("outputCurrency") ?: getString("quoteCurrency")!!,
                usdBalanceDelta = getString("usdBalanceDelta")?.let(::BigDecimal)
            )
        }.getOrNull()
}
