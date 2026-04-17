package expo.modules.inputmask

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.util.TypedValue
import android.widget.EditText
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.kotlin.views.ExpoView
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Locale
import kotlin.math.pow

class NumberInputView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {

  val onValueChange by EventDispatcher<Map<String, Any?>>()
  val onFocusEvent by EventDispatcher()
  val onBlurEvent by EventDispatcher()

  val editText: EditText = EditText(context).apply {
    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    // Let both "." and "," through the OS input filter. TYPE_NUMBER_FLAG_DECIMAL
    // alone would reject whichever one isn't the device locale's decimal separator.
    keyListener = DigitsKeyListener.getInstance("0123456789.,")
    background = null
    val hPad = TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics
    ).toInt()
    setPadding(hPad, paddingTop, hPad, paddingBottom)
  }

  var minValue: Double? = null
  var maxValue: Double? = null

  var propLocale: String? = null
  var propCurrency: String? = null
  var propGroupingSeparator: String? = null
  var propDecimalSeparator: String? = null
  var propDecimalPlaces: Int? = null
  // "decimal" (default) or "cents"
  var propMode: String? = null

  private var formatter: DecimalFormat = DecimalFormat.getInstance(Locale.US) as DecimalFormat
  private var symbols: DecimalFormatSymbols = DecimalFormatSymbols.getInstance(Locale.US)

  // Resolved decimal places: explicit prop > currency default > 2
  private var effectiveDecimalPlaces: Int = 2

  private val isCentsMode: Boolean
    get() = propMode == "cents"

  private var updatingText: Boolean = false
  private var lastFormattedText: String = ""
  private var lastCaret: Int = 0

  // Last user insertion, tracked so we can normalize just-typed decimal-like chars
  // (".", ",") without disturbing pre-existing grouping separators.
  private var insertionStart: Int = -1
  private var insertionCount: Int = 0

  init {
    addView(editText)

    editText.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        if (!updatingText) {
          insertionStart = start
          insertionCount = count
        }
      }
      override fun afterTextChanged(s: Editable?) {
        if (updatingText) return
        handleTextChanged(s?.toString() ?: "")
      }
    })

    editText.setOnFocusChangeListener { _, hasFocus ->
      if (hasFocus) {
        onFocusEvent(mapOf<String, Any>())
      } else {
        onBlurEvent(mapOf<String, Any>())
      }
    }
  }

  // MARK: - Imperative Methods (exposed as AsyncFunctions from the module)

  fun focusField() {
    editText.requestFocus()
  }

  fun blurField() {
    editText.clearFocus()
  }

  fun clearField() {
    updatingText = true
    editText.setText("")
    lastFormattedText = ""
    lastCaret = 0
    updatingText = false
    fireValueChange("", "", null)
  }

  // MARK: - Formatter Configuration

  fun updateFormatter() {
    val locale: Locale = if (propLocale != null) {
      Locale.forLanguageTag(propLocale!!.replace("_", "-"))
    } else {
      Locale.getDefault()
    }

    // Resolve decimal places
    effectiveDecimalPlaces = when {
      propDecimalPlaces != null -> propDecimalPlaces!!
      propCurrency != null -> try {
        Currency.getInstance(propCurrency).defaultFractionDigits
      } catch (_: Exception) { 2 }
      else -> 2
    }

    val syms = DecimalFormatSymbols.getInstance(locale)
    if (propGroupingSeparator != null) {
      syms.groupingSeparator = propGroupingSeparator!!.first()
      syms.monetaryGroupingSeparator = propGroupingSeparator!!.first()
    }
    if (propDecimalSeparator != null) {
      syms.decimalSeparator = propDecimalSeparator!!.first()
      syms.monetaryDecimalSeparator = propDecimalSeparator!!.first()
    }

    val f: DecimalFormat = if (propCurrency != null) {
      (DecimalFormat.getCurrencyInstance(locale) as DecimalFormat).also {
        try { it.currency = Currency.getInstance(propCurrency) } catch (_: Exception) {}
      }
    } else {
      DecimalFormat.getInstance(locale) as DecimalFormat
    }
    f.decimalFormatSymbols = syms
    f.isGroupingUsed = true

    f.maximumFractionDigits = effectiveDecimalPlaces
    f.minimumFractionDigits = if (isCentsMode) effectiveDecimalPlaces else 0

    formatter = f
    symbols = syms
  }

  // MARK: - Controlled Mode

  /**
   * Applies an externally-provided value. No-op while the field is focused
   * so we don't race with active typing (the parent typically echoes every
   * `onValueChange` back via `value={state}`, and mid-typing values like
   * "1." would otherwise be overwritten by the re-format of 1.0 → "1").
   */
  fun setExternalValue(value: Double?) {
    if (editText.isFocused) {
      return
    }

    formatter.minimumFractionDigits = if (isCentsMode) effectiveDecimalPlaces else 0
    formatter.maximumFractionDigits = effectiveDecimalPlaces

    val formatted = if (value != null) formatter.format(value) else ""

    if (editText.text?.toString() != formatted) {
      updatingText = true
      editText.setText(formatted)
      editText.setSelection(formatted.length)
      lastFormattedText = formatted
      lastCaret = formatted.length
      updatingText = false
    }
  }

  // MARK: - Text Change Handler

  private fun handleTextChanged(candidate: String) {
    val normalized = normalizeInsertion(candidate)
    val caret = editText.selectionStart.coerceIn(0, normalized.length)

    if (isCentsMode) {
      applyCentsMode(normalized, effectiveDecimalPlaces)
    } else {
      applyDecimalMode(normalized, caret, effectiveDecimalPlaces)
    }
  }

  private fun normalizeInsertion(candidate: String): String {
    if (insertionStart < 0 || insertionCount <= 0 || insertionStart >= candidate.length) {
      return candidate
    }
    val decSep = symbols.decimalSeparator
    val end = minOf(insertionStart + insertionCount, candidate.length)
    val sb = StringBuilder(candidate)
    for (i in insertionStart until end) {
      val c = sb[i]
      if ((c == '.' || c == ',') && c != decSep) {
        sb.setCharAt(i, decSep)
      }
    }
    return sb.toString()
  }

  private fun applyCentsMode(candidate: String, decimalPlaces: Int) {
    val digitsBuilder = StringBuilder()
    for (c in candidate) {
      if (c.isDigit()) digitsBuilder.append(c)
    }
    val digits = digitsBuilder.toString().take(15)

    val value: Double? = if (digits.isEmpty()) {
      null
    } else {
      val intPart = digits.toDoubleOrNull() ?: 0.0
      intPart / 10.0.pow(decimalPlaces.toDouble())
    }

    if (value != null && maxValue != null && value > maxValue!!) {
      restoreLast()
      return
    }

    formatter.minimumFractionDigits = decimalPlaces
    formatter.maximumFractionDigits = decimalPlaces

    val formatted = value?.let { formatter.format(it) } ?: ""

    setTextPreservingCaret(formatted, formatted.length)

    val rawValue = value?.let { String.format(Locale.US, "%.${decimalPlaces}f", it) } ?: ""
    fireValueChange(rawValue, formatted, value)
  }

  private fun applyDecimalMode(candidate: String, caret: Int, decimalPlaces: Int) {
    val decSep = symbols.decimalSeparator
    val maxFrac = decimalPlaces
    // Cap integer digits at 15 to stay within Double's exact-integer precision.
    // Excess digits past the cap are silently dropped.
    val intCap = 15

    val canonical = StringBuilder()
    var hasDecimal = false
    var fractionCount = 0
    var integerCount = 0
    var contentCharsBeforeCaret = 0

    for ((i, char) in candidate.withIndex()) {
      if (char.isDigit()) {
        if (hasDecimal) {
          if (fractionCount >= maxFrac) continue
          fractionCount++
        } else {
          if (integerCount >= intCap) continue
          integerCount++
        }
        canonical.append(char)
        if (i < caret) contentCharsBeforeCaret++
      } else if (char == decSep && !hasDecimal && maxFrac > 0) {
        hasDecimal = true
        canonical.append('.')
        if (i < caret) contentCharsBeforeCaret++
      }
    }

    // Implicit leading zero: ".5" → "0.5" so toDoubleOrNull() accepts it
    // and the formatter has a number to render.
    var canonicalStr = canonical.toString()
    if (canonicalStr.startsWith('.')) {
      canonicalStr = "0$canonicalStr"
      contentCharsBeforeCaret++
    }
    val numericValue: Double? = if (canonicalStr.isEmpty()) null else canonicalStr.toDoubleOrNull()

    if (numericValue != null && maxValue != null && numericValue > maxValue!!) {
      restoreLast()
      return
    }

    formatter.minimumFractionDigits = if (hasDecimal) minOf(fractionCount, maxFrac) else 0
    formatter.maximumFractionDigits = maxFrac

    var formatted: String = numericValue?.let { formatter.format(it) } ?: ""

    // Trailing decimal separator: render with one fraction digit, strip it so
    // the currency suffix (e.g. " €") stays in place.
    if (hasDecimal && fractionCount == 0 && numericValue != null && formatted.isNotEmpty()) {
      formatter.minimumFractionDigits = 1
      formatter.maximumFractionDigits = 1
      val oneFrac = formatter.format(numericValue)
      val sepIndex = oneFrac.indexOf(decSep)
      formatted = if (sepIndex >= 0 && sepIndex + 1 < oneFrac.length) {
        StringBuilder(oneFrac).deleteCharAt(sepIndex + 1).toString()
      } else {
        oneFrac
      }
    }

    // Caret mapping
    var newCaret = formatted.length
    var contentCount = 0
    for ((i, char) in formatted.withIndex()) {
      if (char.isDigit() || char == decSep) {
        contentCount++
      }
      if (contentCount == contentCharsBeforeCaret) {
        newCaret = i + 1
        break
      }
    }

    setTextPreservingCaret(formatted, newCaret)
    fireValueChange(canonicalStr, formatted, numericValue)
  }

  private fun setTextPreservingCaret(formatted: String, caret: Int) {
    updatingText = true
    editText.setText(formatted)
    val pos = caret.coerceIn(0, formatted.length)
    editText.setSelection(pos)
    lastFormattedText = formatted
    lastCaret = pos
    updatingText = false
  }

  private fun restoreLast() {
    updatingText = true
    editText.setText(lastFormattedText)
    editText.setSelection(lastCaret.coerceIn(0, lastFormattedText.length))
    updatingText = false
  }

  // MARK: - Event Dispatch

  private fun fireValueChange(rawValue: String, formatted: String, value: Double?) {
    val payload = mutableMapOf<String, Any?>(
      "formattedText" to formatted,
      "rawValue" to rawValue,
      "value" to value,
      "complete" to isComplete(value)
    )
    onValueChange(payload)
  }

  private fun isComplete(value: Double?): Boolean {
    return if (value != null) {
      val aboveMin = minValue == null || value >= minValue!!
      val belowMax = maxValue == null || value <= maxValue!!
      aboveMin && belowMax
    } else {
      minValue == null || minValue!! <= 0.0
    }
  }
}
