package com.example.util

import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

data class CurrencyItem(
    val code: String,
    val symbol: String,
    val name: String,
    val useIndianNumbering: Boolean = false
)

object CurrencyFormatter {

    val supportedCurrencies = listOf(
        CurrencyItem("INR", "₹", "Indian Rupee", useIndianNumbering = true),
        CurrencyItem("USD", "$", "US Dollar", useIndianNumbering = false),
        CurrencyItem("EUR", "€", "Euro", useIndianNumbering = false),
        CurrencyItem("GBP", "£", "British Pound", useIndianNumbering = false),
        CurrencyItem("AED", "AED ", "UAE Dirham", useIndianNumbering = false),
        CurrencyItem("SGD", "S$", "Singapore Dollar", useIndianNumbering = false),
        CurrencyItem("JPY", "¥", "Japanese Yen", useIndianNumbering = false),
        CurrencyItem("SAR", "SAR ", "Saudi Riyal", useIndianNumbering = false),
        CurrencyItem("CAD", "C$", "Canadian Dollar", useIndianNumbering = false),
        CurrencyItem("AUD", "A$", "Australian Dollar", useIndianNumbering = false)
    )

    var activeCurrency: CurrencyItem = supportedCurrencies[0]

    private val indianFormat: DecimalFormat by lazy {
        val format = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()) as DecimalFormat
        val symbols = format.decimalFormatSymbols
        symbols.currencySymbol = "₹"
        format.decimalFormatSymbols = symbols
        format.maximumFractionDigits = 2
        format.minimumFractionDigits = 2
        format
    }

    private val indianIntegerFormat: DecimalFormat by lazy {
        val format = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()) as DecimalFormat
        val symbols = format.decimalFormatSymbols
        symbols.currencySymbol = "₹"
        format.decimalFormatSymbols = symbols
        format.maximumFractionDigits = 0
        format.minimumFractionDigits = 0
        format
    }

    private val standardFormat: DecimalFormat by lazy {
        val format = NumberFormat.getNumberInstance(Locale.US) as DecimalFormat
        format.maximumFractionDigits = 2
        format.minimumFractionDigits = 2
        format
    }

    private val standardIntegerFormat: DecimalFormat by lazy {
        val format = NumberFormat.getNumberInstance(Locale.US) as DecimalFormat
        format.maximumFractionDigits = 0
        format.minimumFractionDigits = 0
        format
    }

    fun formatInr(amount: Double, includeDecimals: Boolean = true): String {
        return formatCurrency(amount, activeCurrency, includeDecimals)
    }

    fun formatCurrency(amount: Double, currency: CurrencyItem = activeCurrency, includeDecimals: Boolean = true): String {
        return try {
            val hasFraction = (amount % 1.0 != 0.0) && includeDecimals
            if (currency.useIndianNumbering) {
                if (hasFraction) {
                    val raw = indianFormat.format(amount)
                    if (currency.symbol != "₹") raw.replace("₹", currency.symbol) else raw
                } else {
                    val raw = indianIntegerFormat.format(amount)
                    if (currency.symbol != "₹") raw.replace("₹", currency.symbol) else raw
                }
            } else {
                val numStr = if (hasFraction) standardFormat.format(amount) else standardIntegerFormat.format(amount)
                "${currency.symbol}$numStr"
            }
        } catch (e: Exception) {
            String.format(Locale.getDefault(), "%s%.2f", currency.symbol, amount)
        }
    }

    fun formatCompact(amount: Double, currency: CurrencyItem = activeCurrency): String {
        val absVal = Math.abs(amount)
        val sign = if (amount < 0) "-" else ""
        if (currency.useIndianNumbering) {
            return when {
                absVal >= 10_000_000 -> String.format(Locale.getDefault(), "%s%s%.2f Cr", sign, currency.symbol, absVal / 10_000_000.0)
                absVal >= 100_000 -> String.format(Locale.getDefault(), "%s%s%.2f L", sign, currency.symbol, absVal / 100_000.0)
                absVal >= 1_000 -> String.format(Locale.getDefault(), "%s%s%.1f k", sign, currency.symbol, absVal / 1_000.0)
                else -> formatCurrency(amount, currency, false)
            }
        } else {
            return when {
                absVal >= 1_000_000_000 -> String.format(Locale.getDefault(), "%s%s%.2f B", sign, currency.symbol, absVal / 1_000_000_000.0)
                absVal >= 1_000_000 -> String.format(Locale.getDefault(), "%s%s%.2f M", sign, currency.symbol, absVal / 1_000_000.0)
                absVal >= 1_000 -> String.format(Locale.getDefault(), "%s%s%.1f k", sign, currency.symbol, absVal / 1_000.0)
                else -> formatCurrency(amount, currency, false)
            }
        }
    }
}
