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

  val onChangeText by EventDispatcher()
  val onNumberResult by EventDispatcher<Map<String, Any?>>()
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
  var propFixedDecimalPlaces: Boolean? = null

  private var formatter: DecimalFormat = DecimalFormat.getInstance(Locale.US) as DecimalFormat
  private var symbols: DecimalFormatSymbols = DecimalFormatSymbols.getInstance(Locale.US)

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

  // MARK: - Formatter Configuration

  fun updateFormatter() {
    val locale: Locale = if (propLocale != null) {
      Locale.forLanguageTag(propLocale!!.replace("_", "-"))
    } else {
      Locale.getDefault()
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

    val maxFrac = propDecimalPlaces ?: 2
    f.maximumFractionDigits = maxFrac
    f.minimumFractionDigits = if (propFixedDecimalPlaces == true) maxFrac else 0

    formatter = f
    symbols = syms
  }

  // MARK: - Controlled Mode

  fun setExternalValue(value: String) {
    val parsed = value.toDoubleOrNull()
    val decimalPlaces = propDecimalPlaces ?: 2
    val isFixed = propFixedDecimalPlaces == true

    formatter.minimumFractionDigits = if (isFixed) decimalPlaces else 0
    formatter.maximumFractionDigits = decimalPlaces

    val formatted = if (parsed != null) formatter.format(parsed) else ""

    if (editText.text?.toString() != formatted) {
      updatingText = true
      editText.setText(formatted)
      if (!editText.isFocused) {
        editText.setSelection(formatted.length)
      }
      lastFormattedText = formatted
      lastCaret = editText.selectionStart
      updatingText = false
    }
  }

  // MARK: - Text Change Handler

  private fun handleTextChanged(candidate: String) {
    val normalized = normalizeInsertion(candidate)
    val caret = editText.selectionStart.coerceIn(0, normalized.length)
    val decimalPlaces = propDecimalPlaces ?: 2

    if (propFixedDecimalPlaces == true) {
      applyCentsMode(normalized, decimalPlaces)
    } else {
      applyDecimalMode(normalized, caret, decimalPlaces)
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
    fireEvents(rawValue, formatted, value)
  }

  private fun applyDecimalMode(candidate: String, caret: Int, decimalPlaces: Int) {
    val decSep = symbols.decimalSeparator
    val maxFrac = decimalPlaces

    val canonical = StringBuilder()
    var hasDecimal = false
    var fractionCount = 0
    var contentCharsBeforeCaret = 0

    for ((i, char) in candidate.withIndex()) {
      if (char.isDigit()) {
        if (hasDecimal && fractionCount >= maxFrac) continue
        if (hasDecimal) fractionCount++
        canonical.append(char)
        if (i < caret) contentCharsBeforeCaret++
      } else if (char == decSep && !hasDecimal && maxFrac > 0) {
        hasDecimal = true
        canonical.append('.')
        if (i < caret) contentCharsBeforeCaret++
      }
    }

    val canonicalStr = canonical.toString()
    val numericValue: Double? = if (canonicalStr.isEmpty()) null else canonicalStr.toDoubleOrNull()

    if (numericValue != null && maxValue != null && numericValue > maxValue!!) {
      restoreLast()
      return
    }

    formatter.minimumFractionDigits = if (hasDecimal) minOf(fractionCount, maxFrac) else 0
    formatter.maximumFractionDigits = maxFrac

    var formatted: String = numericValue?.let { formatter.format(it) } ?: ""

    // Trailing decimal separator: render with one fraction digit, strip it so the
    // currency suffix (e.g. " €") stays in the correct position.
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
    fireEvents(canonicalStr, formatted, numericValue)
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

  private fun fireEvents(rawValue: String, formatted: String, value: Double?) {
    onChangeText(mapOf("text" to rawValue))
    val payload = mutableMapOf<String, Any?>(
      "formattedText" to formatted,
      "value" to value,
      "complete" to isComplete(value)
    )
    onNumberResult(payload)
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
