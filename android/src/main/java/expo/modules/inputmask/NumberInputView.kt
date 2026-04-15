package expo.modules.inputmask

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.kotlin.views.ExpoView
import com.redmadrobot.inputmask.MaskedTextChangedListener
import com.redmadrobot.inputmask.NumberInputListener

class NumberInputView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {

  val onChangeText by EventDispatcher()
  val onNumberResult by EventDispatcher()
  val onFocusEvent by EventDispatcher()
  val onBlurEvent by EventDispatcher()

  val editText: EditText = EditText(context).apply {
    layoutParams = LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.MATCH_PARENT
    )
    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    background = null
    setPadding(0, 0, 0, 0)
  }

  var minValue: Double? = null
  var maxValue: Double? = null

  // Stored prop values for formatter configuration
  var propLocale: String? = null
  var propCurrency: String? = null
  var propGroupingSeparator: String? = null
  var propDecimalSeparator: String? = null
  var propDecimalPlaces: Int? = null
  var propFixedDecimalPlaces: Boolean? = null

  // Decimal separator resolved during updateFormatter, used for numeric parsing
  private var resolvedDecimalSeparator: Char = '.'

  private var numberListener: NumberInputListener
  private var previousFormattedText: String = ""
  private var previousExtractedValue: String = ""

  init {
    addView(editText)

    val valueListener = object : MaskedTextChangedListener.ValueListener {
      override fun onTextChanged(
        maskFilled: Boolean,
        extractedValue: String,
        formattedValue: String,
        tailPlaceholder: String
      ) {
        handleTextChanged(maskFilled, extractedValue, formattedValue)
      }
    }

    numberListener = NumberInputListener(
      false, // autocomplete
      false, // autoskip
      editText,
      null,  // additional TextWatcher
      valueListener
    )
    editText.addTextChangedListener(numberListener)

    editText.setOnFocusChangeListener { _, hasFocus ->
      if (hasFocus) {
        onFocusEvent(mapOf<String, Any>())
      } else {
        onBlurEvent(mapOf<String, Any>())
      }
    }
  }

  private fun handleTextChanged(complete: Boolean, extractedValue: String, formattedText: String) {
    val numericValue = parseNumericValue(formattedText)

    // Enforce max constraint
    if (numericValue != null && maxValue != null && numericValue > maxValue!!) {
      numberListener.setText(previousExtractedValue, false)
      return
    }

    // Determine completeness factoring in min
    var isComplete = complete
    if (numericValue != null && minValue != null && numericValue < minValue!!) {
      isComplete = false
    }

    previousFormattedText = formattedText
    previousExtractedValue = extractedValue

    onChangeText(mapOf("text" to extractedValue))

    val jsValue: Any = numericValue ?: "null"
    onNumberResult(mapOf(
      "formattedText" to formattedText,
      "value" to jsValue,
      "complete" to isComplete
    ))
  }

  private fun parseNumericValue(formattedText: String): Double? {
    if (formattedText.isEmpty()) return null
    val decSep = resolvedDecimalSeparator
    val filtered = formattedText.filter { it.isDigit() || it == decSep }
    val normalized = filtered.replace(decSep.toString(), ".")
    return normalized.toDoubleOrNull()
  }

  fun updateFormatter() {
    val locale = if (propLocale != null) {
      android.icu.util.ULocale.forLanguageTag(propLocale!!.replace("_", "-"))
    } else {
      android.icu.util.ULocale.getDefault()
    }

    // Resolve the decimal separator for numeric parsing
    val javaLocale = locale.toLocale()
    resolvedDecimalSeparator = if (propDecimalSeparator != null) {
      propDecimalSeparator!!.first()
    } else {
      java.text.DecimalFormatSymbols.getInstance(javaLocale).decimalSeparator
    }

    var formatter = android.icu.number.NumberFormatter
      .withLocale(locale)
      .grouping(android.icu.number.NumberFormatter.GroupingStrategy.AUTO)

    if (propCurrency != null) {
      formatter = formatter.unit(android.icu.util.Currency.getInstance(propCurrency))
    }

    val maxFrac = propDecimalPlaces ?: 2
    val minFrac = if (propFixedDecimalPlaces == true) maxFrac else 0
    formatter = formatter.precision(android.icu.number.Precision.minMaxFraction(minFrac, maxFrac))

    numberListener.formatter = formatter
  }

  fun setExternalValue(value: String) {
    numberListener.setText(value, false)
  }
}
