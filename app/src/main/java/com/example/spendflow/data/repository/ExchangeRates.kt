package com.example.spendflow.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.math.BigDecimal
import java.net.URL
import java.util.Currency
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection

interface ExchangeRates {
    fun lastRate(currency: String, base: String): BigDecimal?
    fun remember(currency: String, base: String, rate: BigDecimal)
    suspend fun latest(currency: String, base: String): BigDecimal
}

@Singleton
class ApiExchangeRates @Inject constructor(@ApplicationContext context: Context) : ExchangeRates {
    private val preferences = context.getSharedPreferences("income_rates", Context.MODE_PRIVATE)
    private val cache = mutableMapOf<String, Pair<Long, BigDecimal>>()

    override fun lastRate(currency: String, base: String): BigDecimal? =
        preferences.getString("$currency/$base", null)?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

    override fun remember(currency: String, base: String, rate: BigDecimal) {
        preferences.edit().putString("$currency/$base", rate.toPlainString()).apply()
    }

    override suspend fun latest(currency: String, base: String): BigDecimal = withContext(Dispatchers.IO) {
        Currency.getInstance(currency)
        Currency.getInstance(base)
        if (currency == base) return@withContext BigDecimal.ONE
        val key = "$currency/$base"
        synchronized(cache) { cache[key] }?.let { (expires, rate) ->
            if (System.currentTimeMillis() < expires) return@withContext rate
        }
        val connection = URL("https://open.er-api.com/v6/latest/$currency").openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            check(connection.responseCode == 200) { "Exchange rate request failed" }
            val json = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            check(json.getString("result") == "success" && json.getString("base_code") == currency)
            val rate = BigDecimal(json.getJSONObject("rates").getString(base))
            require(rate.signum() > 0)
            val expires = json.getLong("time_next_update_unix") * 1000
            synchronized(cache) { cache[key] = expires to rate }
            rate
        } finally {
            connection.disconnect()
        }
    }
}
