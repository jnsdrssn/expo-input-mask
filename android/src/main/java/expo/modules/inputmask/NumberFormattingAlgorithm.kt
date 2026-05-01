package expo.modules.inputmask

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Locale
import kotlin.math.pow

/**
 * Pure number-formatting algorithm shared by `applyNumberFormat` (the JS
 * bridge function) and `NumberInputView` (the native view's text watcher).
 *
 * Extracted so it can be unit-tested without instantiating the Expo module.
 * Given a raw input string + caret position and locale/currency configuration,
 * it walks the input (keeping only digits and the decimal separator), parses
 * the resulting canonical value, re-formats it via `DecimalFormat`, and
 * remaps the caret by counting content characters.
 */
object NumberFormattingAlgorithm {
  data class Result(
    val formattedText: String,
    val value: String,
    val complete: Boolean,
    val caretPosition: Int,
    val exceeded: Boolean,
    /**
     * Value expressed as an integer in the smallest unit (e.g. cents for
     * USD/EUR, ¥ for JPY, fils for BHD). Computed from `value` by string
     * concatenation — exact, no floating-point. `null` when `value` is empty.
     * Useful for payment APIs (Stripe, Adyen, ...) that take amounts as
     * integers in minor units.
     */
    val minorUnits: Long?
  )

  private const val INT_DIGIT_CAP = 15

  /**
   * Compute integer minor-units from a dot-canonical raw value and a
   * decimal-places scale, by string concatenation. `"1234.56" / 2 → 123456`,
   * `"1.5" / 2 → 150`, `"1234" / 2 → 123400`, `"" / * → null`.
   */
  private fun computeMinorUnits(rawValue: String, decimalPlaces: Int): Long? {
    if (rawValue.isEmpty()) return null
    val parts = rawValue.split(".", limit = 2)
    val intPart = parts[0]
    var fracPart = if (parts.size > 1) parts[1] else ""
    fracPart = when {
      fracPart.length < decimalPlaces -> fracPart.padEnd(decimalPlaces, '0')
      fracPart.length > decimalPlaces -> fracPart.substring(0, decimalPlaces)
      else -> fracPart
    }
    return (intPart + fracPart).toLongOrNull()
  }

  fun apply(
    text: String,
    caretPosition: Int,
    locale: String? = null,
    currency: String? = null,
    groupingSeparator: String? = null,
    decimalSeparator: String? = null,
    decimalPlaces: Int? = null,
    fixedDecimalPlaces: Boolean = false,
    min: Double? = null,
    max: Double? = null
  ): Result {
    val resolvedLocale: Locale = if (locale != null) {
      Locale.forLanguageTag(locale.replace("_", "-"))
    } else {
      Locale.getDefault()
    }

    val symbols = DecimalFormatSymbols.getInstance(resolvedLocale)
    val effectiveDecimalSeparator: Char = decimalSeparator?.first() ?: symbols.decimalSeparator

    // Resolve maximum fraction digits: explicit > currency default > 2.
    val maxFractionDigits: Int = when {
      decimalPlaces != null -> decimalPlaces
      currency != null -> try {
        Currency.getInstance(currency).defaultFractionDigits
      } catch (_: Exception) { 2 }
      else -> 2
    }

    // Walk the input keeping digits + one decimal separator. Integer digits
    // past `INT_DIGIT_CAP` are silently dropped (Double exact-integer limit).
    val digitsBuilder = StringBuilder()
    var hasDecimal = false
    var fractionCount = 0
    var integerCount = 0
    val clampedCaret = caretPosition.coerceIn(0, text.length)
    var contentCharsBeforeCaret = 0

    for ((i, char) in text.withIndex()) {
      if (char.isDigit()) {
        if (hasDecimal) {
          if (fractionCount >= maxFractionDigits) continue
          fractionCount++
        } else {
          if (integerCount >= INT_DIGIT_CAP) continue
          integerCount++
        }
        digitsBuilder.append(char)
        if (i < clampedCaret) contentCharsBeforeCaret++
      } else if (char == effectiveDecimalSeparator && !hasDecimal && maxFractionDigits > 0) {
        hasDecimal = true
        digitsBuilder.append('.')
        if (i < clampedCaret) contentCharsBeforeCaret++
      }
    }

    // Implicit leading zero so ".5" parses as 0.5 and renders as "0.5".
    var digits = digitsBuilder.toString()
    if (digits.startsWith('.')) {
      digits = "0$digits"
      contentCharsBeforeCaret++
    }

    val numericValue: Double? = if (digits.isEmpty()) null else digits.toDoubleOrNull()

    // Reject values above max — signal via `exceeded`, caller decides whether
    // to revert or display an error.
    if (numericValue != null && max != null && numericValue > max) {
      return Result(
        formattedText = "",
        value = "",
        complete = false,
        caretPosition = 0,
        exceeded = true,
        minorUnits = null
      )
    }

    // Apply separator overrides before building the formatter.
    if (groupingSeparator != null) {
      symbols.groupingSeparator = groupingSeparator.first()
      symbols.monetaryGroupingSeparator = groupingSeparator.first()
    }
    if (decimalSeparator != null) {
      symbols.decimalSeparator = decimalSeparator.first()
      symbols.monetaryDecimalSeparator = decimalSeparator.first()
    }

    val formatter: DecimalFormat = if (currency != null) {
      (DecimalFormat.getCurrencyInstance(resolvedLocale) as DecimalFormat).also {
        try { it.currency = Currency.getInstance(currency) } catch (_: Exception) {}
      }
    } else {
      DecimalFormat.getInstance(resolvedLocale) as DecimalFormat
    }
    formatter.decimalFormatSymbols = symbols
    formatter.isGroupingUsed = true
    formatter.minimumFractionDigits = when {
      fixedDecimalPlaces -> maxFractionDigits
      hasDecimal -> minOf(fractionCount, maxFractionDigits)
      else -> 0
    }
    formatter.maximumFractionDigits = maxFractionDigits

    var formattedText: String = if (numericValue != null) formatter.format(numericValue) else ""

    // Trailing decimal separator: render with one fraction digit, strip it so
    // the currency suffix (e.g. " €" in de-DE) stays in the correct place.
    if (hasDecimal && fractionCount == 0 && !fixedDecimalPlaces && numericValue != null && formattedText.isNotEmpty()) {
      formatter.minimumFractionDigits = 1
      formatter.maximumFractionDigits = 1
      val oneFrac = formatter.format(numericValue)
      val sepIndex = oneFrac.indexOf(symbols.decimalSeparator)
      formattedText = if (sepIndex >= 0 && sepIndex + 1 < oneFrac.length) {
        StringBuilder(oneFrac).deleteCharAt(sepIndex + 1).toString()
      } else {
        oneFrac
      }
    }

    // Caret remap: walk formatted, count digit-or-decimal-separator characters.
    val resolvedDecSep = symbols.decimalSeparator
    var newCaretPosition = formattedText.length
    var contentCount = 0
    for ((i, char) in formattedText.withIndex()) {
      if (char.isDigit() || char == resolvedDecSep) {
        contentCount++
      }
      if (contentCount == contentCharsBeforeCaret) {
        newCaretPosition = i + 1
        break
      }
    }

    val complete: Boolean = if (numericValue != null) {
      val aboveMin = min == null || numericValue >= min
      val belowMax = max == null || numericValue <= max
      aboveMin && belowMax
    } else {
      min == null || min <= 0.0
    }

    return Result(
      formattedText = formattedText,
      value = digits,
      complete = complete,
      caretPosition = newCaretPosition,
      exceeded = false,
      minorUnits = computeMinorUnits(digits, maxFractionDigits)
    )
  }

  /**
   * Cents mode: append-only digit accumulation. Strips non-digits, treats the
   * last `decimalPlaces` digits as the fractional part. The caret is always
   * parked at end-of-text (no in-place editing in this mode).
   */
  fun applyCents(
    text: String,
    decimalPlaces: Int,
    locale: String? = null,
    currency: String? = null,
    groupingSeparator: String? = null,
    decimalSeparator: String? = null,
    min: Double? = null,
    max: Double? = null
  ): Result {
    val digitsBuilder = StringBuilder()
    for (c in text) {
      if (c.isDigit()) digitsBuilder.append(c)
    }
    val digits = digitsBuilder.toString().take(INT_DIGIT_CAP)

    val numericValue: Double? = if (digits.isEmpty()) {
      null
    } else {
      val intPart = digits.toDoubleOrNull() ?: 0.0
      intPart / 10.0.pow(decimalPlaces.toDouble())
    }

    if (numericValue != null && max != null && numericValue > max) {
      return Result(
        formattedText = "",
        value = "",
        complete = false,
        caretPosition = 0,
        exceeded = true,
        minorUnits = null
      )
    }

    val resolvedLocale: Locale = if (locale != null) {
      Locale.forLanguageTag(locale.replace("_", "-"))
    } else {
      Locale.getDefault()
    }
    val symbols = DecimalFormatSymbols.getInstance(resolvedLocale)
    if (groupingSeparator != null) {
      symbols.groupingSeparator = groupingSeparator.first()
      symbols.monetaryGroupingSeparator = groupingSeparator.first()
    }
    if (decimalSeparator != null) {
      symbols.decimalSeparator = decimalSeparator.first()
      symbols.monetaryDecimalSeparator = decimalSeparator.first()
    }

    val formatter: DecimalFormat = if (currency != null) {
      (DecimalFormat.getCurrencyInstance(resolvedLocale) as DecimalFormat).also {
        try { it.currency = Currency.getInstance(currency) } catch (_: Exception) {}
      }
    } else {
      DecimalFormat.getInstance(resolvedLocale) as DecimalFormat
    }
    formatter.decimalFormatSymbols = symbols
    formatter.isGroupingUsed = true
    formatter.minimumFractionDigits = decimalPlaces
    formatter.maximumFractionDigits = decimalPlaces

    val formattedText: String = if (numericValue != null) formatter.format(numericValue) else ""
    val rawValue: String = if (numericValue != null) String.format(Locale.US, "%.${decimalPlaces}f", numericValue) else ""

    val complete: Boolean = if (numericValue != null) {
      val aboveMin = min == null || numericValue >= min
      val belowMax = max == null || numericValue <= max
      aboveMin && belowMax
    } else {
      min == null || min <= 0.0
    }

    return Result(
      formattedText = formattedText,
      value = rawValue,
      complete = complete,
      caretPosition = formattedText.length,
      exceeded = false,
      minorUnits = computeMinorUnits(rawValue, decimalPlaces)
    )
  }
}
