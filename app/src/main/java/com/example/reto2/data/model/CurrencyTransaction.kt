package com.example.reto2.data.model

import java.math.BigDecimal

enum class TradeSide { BUY, SELL }
enum class TradeStatus { SUCCESS, REJECTED }

data class CurrencyTransaction(
    val id: String,
    val userId: String,
    val side: TradeSide,
    val baseCurrency: String,
    val quoteCurrency: String,
    val amount: BigDecimal,
    val referenceRate: BigDecimal,
    val appliedRate: BigDecimal,
    val deviationPercent: BigDecimal,
    val createdAt: Long,
    val status: TradeStatus,
    val reason: String? = null,
    val inputAmount: BigDecimal = amount,
    val inputCurrency: String = baseCurrency,
    val outputAmount: BigDecimal = amount,
    val outputCurrency: String = quoteCurrency,
    val usdBalanceDelta: BigDecimal? = null
)

object DeviationRule {
    private val limitPercent = BigDecimal("2.5")

    fun deviationPercent(applied: BigDecimal, reference: BigDecimal): BigDecimal {
        require(reference.signum() > 0) { "La tasa de referencia debe ser positiva" }
        return applied.subtract(reference).abs()
            .multiply(BigDecimal("100"))
            .divide(reference, 6, java.math.RoundingMode.HALF_UP)
    }

    fun isAllowed(applied: BigDecimal, reference: BigDecimal): Boolean {
        require(reference.signum() > 0) { "La tasa de referencia debe ser positiva" }
        return applied.subtract(reference).abs().multiply(BigDecimal("100")) <=
            limitPercent.multiply(reference)
    }
}
