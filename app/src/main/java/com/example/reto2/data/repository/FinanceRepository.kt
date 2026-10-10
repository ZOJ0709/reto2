package com.example.reto2.data.repository

import com.example.reto2.data.model.CurrencyTransaction
import com.example.reto2.data.model.TradeSide
import java.math.BigDecimal

data class FxRate(val base: String, val quote: String, val value: BigDecimal, val observedAt: Long)

interface FinanceRepository {
    suspend fun latestRate(base: String, quote: String): Result<FxRate>
    suspend fun executeTrade(
        userId: String,
        side: TradeSide,
        base: String,
        quote: String,
        amount: BigDecimal,
        rate: FxRate
    ): Result<CurrencyTransaction>
    suspend fun userTransactions(userId: String): Result<List<CurrencyTransaction>>
    suspend fun allTransactions(): Result<List<CurrencyTransaction>>
}
