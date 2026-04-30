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
  // Last value the parent passed via `value`. Stashed so we can re-render it
  // after `updateFormatter()` resolves the final locale/currency on first mount.
  private var propValue: Double? = null
  private var hasPropValue: Boolean = false

  // Used only for `applyExternalValue` and decimal-separator lookup during
  // input normalization. The typing path delegates to NumberFormattingAlgorithm.
  private var formatter: DecimalFormat = DecimalFormat.getInstance(Locale.US) as DecimalFormat
  private var symbols: DecimalFormatSymbols = DecimalFormatSymbols.getInstance(Locale.US)

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

    // Re-apply the controlled value with the freshly-resolved formatter so that
    // first-mount renders (`<NumberInput currency="EUR" locale="de-DE" value={1.5} />`)
    // don't briefly paint with the default en_US formatter before the prop
    // batch finishes.
    applyExternalValue()
  }

  // MARK: - Controlled Mode

  /**
   * Stash the externally-provided value, then re-render. The actual paint is
   * deferred to `applyExternalValue` so it can run again from `updateFormatter`
   * after the rest of the prop batch has resolved.
   */
  fun setExternalValue(value: Double?) {
    propValue = value
    hasPropValue = true
    applyExternalValue()
  }

  /**
   * Renders `propValue` into the EditText. No-op while the field is focused so
   * we don't race with active typing (the parent typically echoes every
   * `onValueChange` back via `value={state}`, and mid-typing values like "1."
   * would otherwise be overwritten by the re-format of 1.0 → "1").
   */
  private fun applyExternalValue() {
    if (!hasPropValue) return
    if (editText.isFocused) return

    formatter.minimumFractionDigits = if (isCentsMode) effectiveDecimalPlaces else 0
    formatter.maximumFractionDigits = effectiveDecimalPlaces

    val formatted = if (propValue != null) formatter.format(propValue) else ""

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

    val result: NumberFormattingAlgorithm.Result = if (isCentsMode) {
      NumberFormattingAlgorithm.applyCents(
        text = normalized,
        decimalPlaces = effectiveDecimalPlaces,
        locale = propLocale,
        currency = propCurrency,
        groupingSeparator = propGroupingSeparator,
        decimalSeparator = propDecimalSeparator,
        min = minValue,
        max = maxValue
      )
    } else {
      NumberFormattingAlgorithm.apply(
        text = normalized,
        caretPosition = caret,
        locale = propLocale,
        currency = propCurrency,
        groupingSeparator = propGroupingSeparator,
        decimalSeparator = propDecimalSeparator,
        decimalPlaces = propDecimalPlaces,
        fixedDecimalPlaces = false,
        min = minValue,
        max = maxValue
      )
    }

    if (result.exceeded) {
      restoreLast()
      return
    }

    setTextPreservingCaret(result.formattedText, result.caretPosition)
    val numericValue: Double? = if (result.value.isEmpty()) null else result.value.toDoubleOrNull()
    fireValueChange(result.value, result.formattedText, numericValue)
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
