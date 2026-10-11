package expo.modules.inputmask

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Typeface
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.util.TypedValue
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.facebook.react.common.ReactConstants
import com.facebook.react.views.text.ReactTypefaceUtils
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

  // Plain EditText — no opinionated padding. Consumers style via
  // `<NumberInput style={...} />` and any wrapping View as they would a
  // regular `<TextInput />`. `background = null` removes the platform
  // underline drawable so the visual matches iOS UITextField.
  val editText: EditText = EditText(context).apply {
    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    // Let both "." and "," through the OS input filter. TYPE_NUMBER_FLAG_DECIMAL
    // alone would reject whichever one isn't the device locale's decimal separator.
    keyListener = DigitsKeyListener.getInstance("0123456789.,")
    background = null
    // EditText keeps the theme's padding after its background is removed,
    // and adds font padding above and below the line. A field sized to its
    // line (as a `<TextInput />` laid out by Yoga is) then draws its text
    // pushed down and clipped to a sliver. UITextField has neither, so this
    // matches iOS: the text fills the view, and `style` padding is the
    // consumer's to add.
    setPadding(0, 0, 0, 0)
    includeFontPadding = false
  }

  // EditText's own style, restored when the text-style props are removed.
  private val defaultTypeface: Typeface? = editText.typeface
  private val defaultTextSize: Float = editText.textSize
  private val defaultTextColors: ColorStateList = editText.textColors
  private val defaultHintTextColors: ColorStateList? = editText.hintTextColors
  private val defaultHighlightColor: Int = editText.highlightColor

  var minValue: Double? = null
  var maxValue: Double? = null

  // The `<TextInput />` text-style props, forwarded by the JS wrapper from
  // `style` (font, color, letter spacing, font variant) and from the top level
  // (placeholder and caret colors, font scaling). Each marks the style dirty
  // when it changes; `applyTextStyle()` (from `OnViewDidUpdateProps`) rebuilds
  // once per prop batch. Until one is set, the EditText keeps its own style.
  var propFontFamily: String? = null
    set(value) { if (field != value) { field = value; styleDirty = true } }
  var propFontSize: Double? = null
    set(value) { if (field != value) { field = value; styleDirty = true } }
  var propFontWeight: String? = null
    set(value) { if (field != value) { field = value; styleDirty = true } }
  var propFontVariant: List<String>? = null
    set(value) { if (field != value) { field = value; styleDirty = true } }
  var propLetterSpacing: Double? = null
    set(value) { if (field != value) { field = value; styleDirty = true } }
  var propAllowFontScaling: Boolean? = null
    set(value) { if (field != value) { field = value; styleDirty = true } }
  var propColor: Int? = null
    set(value) { if (field != value) { field = value; styleDirty = true } }
  var propPlaceholderTextColor: Int? = null
    set(value) { if (field != value) { field = value; styleDirty = true } }
  var propSelectionColor: Int? = null
    set(value) { field = value; updateCaretColor() }
  var propCursorColor: Int? = null
    set(value) { field = value; updateCaretColor() }
  private var styleDirty: Boolean = false

  // Each formatting-config prop sets `formatterDirty` when it actually changes.
  // `OnViewDidUpdateProps` then skips the formatter rebuild on prop batches
  // that only echoed the controlled `value` back — the typical
  // `onValueChange` → `setState` → `value` cycle in controlled mode.
  var propLocale: String? = null
    set(value) { if (field != value) { field = value; formatterDirty = true } }
  var propCurrency: String? = null
    set(value) { if (field != value) { field = value; formatterDirty = true } }
  var propGroupingSeparator: String? = null
    set(value) { if (field != value) { field = value; formatterDirty = true } }
  var propDecimalSeparator: String? = null
    set(value) { if (field != value) { field = value; formatterDirty = true } }
  var propDecimalPlaces: Int? = null
    set(value) { if (field != value) { field = value; formatterDirty = true } }
  // "decimal" (default) or "cents"
  var propMode: String? = null
    set(value) { if (field != value) { field = value; formatterDirty = true } }
  // Last value the parent passed via `value`. Stashed so we can re-render it
  // after `updateFormatter()` resolves the final locale/currency on first mount.
  private var propValue: Double? = null
  private var hasPropValue: Boolean = false
  // Initial `true` so the first `updateFormatter()` after mount always rebuilds.
  private var formatterDirty: Boolean = true

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

  // MARK: - Text Style

  fun applyTextStyle() {
    if (!styleDirty) return
    styleDirty = false

    val fontSize = propFontSize
    if (fontSize != null) {
      val unit = if (propAllowFontScaling != false) TypedValue.COMPLEX_UNIT_SP else TypedValue.COMPLEX_UNIT_DIP
      editText.setTextSize(unit, fontSize.toFloat())
    } else {
      editText.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultTextSize)
    }

    val weight = ReactTypefaceUtils.parseFontWeight(propFontWeight)
    editText.typeface = if (propFontFamily == null && weight == ReactConstants.UNSET) {
      defaultTypeface
    } else {
      ReactTypefaceUtils.applyStyles(defaultTypeface, ReactConstants.UNSET, weight, propFontFamily, context.assets)
    }

    editText.fontFeatureSettings = fontFeatures(propFontVariant)

    // Android takes letter spacing in ems; React Native's is in points.
    val spacing = propLetterSpacing
    editText.letterSpacing = if (spacing != null) {
      val sizeInPoints = fontSize ?: (editText.textSize / resources.displayMetrics.density).toDouble()
      if (sizeInPoints > 0) (spacing / sizeInPoints).toFloat() else 0f
    } else {
      0f
    }

    val color = propColor
    if (color != null) editText.setTextColor(color) else editText.setTextColor(defaultTextColors)
    val hintColor = propPlaceholderTextColor
    if (hintColor != null) {
      editText.setHintTextColor(hintColor)
    } else if (defaultHintTextColors != null) {
      editText.setHintTextColor(defaultHintTextColors)
    }
  }

  // `selectionColor` tints the selection, and the caret unless `cursorColor`
  // is set. The caret is tinted on Android 10 (API 29) and later.
  private fun updateCaretColor() {
    val selection = propSelectionColor
    editText.highlightColor = selection ?: defaultHighlightColor
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val cursor = propCursorColor ?: selection
      val drawable = editText.textCursorDrawable ?: return
      if (cursor != null) {
        drawable.colorFilter = BlendModeColorFilter(cursor, BlendMode.SRC_IN)
      } else {
        drawable.clearColorFilter()
      }
      editText.textCursorDrawable = drawable
    }
  }

  private fun fontFeatures(variants: List<String>?): String? {
    val features = variants.orEmpty().mapNotNull { variant ->
      when (variant) {
        "tabular-nums" -> "'tnum'"
        "proportional-nums" -> "'pnum'"
        "lining-nums" -> "'lnum'"
        "oldstyle-nums" -> "'onum'"
        "small-caps" -> "'smcp'"
        else -> null
      }
    }
    return if (features.isEmpty()) null else features.joinToString(", ")
  }

  // MARK: - Imperative Methods (exposed as AsyncFunctions from the module)

  // `autoFocus`: focus once, when the view first reaches a window. A
  // `focus()` call from JS at mount can arrive before the view is attached
  // (Fabric resolves the view by tag, and it isn't registered yet), so the
  // native side does it at the moment it can.
  var autoFocus = false
  private var autoFocused = false

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    if (autoFocus && !autoFocused) {
      autoFocused = true
      editText.post { focusField() }
    }
  }

  fun focusField() {
    if (editText.requestFocus()) {
      // requestFocus() alone leaves the keyboard down; focus from code should
      // behave as a tap on the field does.
      val keyboard = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
      keyboard?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }
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
    fireValueChange("", "", null, null)
  }

  // MARK: - Formatter Configuration

  fun updateFormatter() {
    if (formatterDirty) {
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
      formatterDirty = false
    }

    // Always re-apply the controlled value: on a dirty rebuild this catches
    // the first-mount race (default en_US paint before locale/currency resolve);
    // on a clean batch this lets a `value`-only echo paint through (cheap —
    // `applyExternalValue` skips when text is already equal).
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
    fireValueChange(result.value, result.formattedText, numericValue, result.minorUnits)
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

  private fun fireValueChange(rawValue: String, formatted: String, value: Double?, minorUnits: Long?) {
    val payload = mutableMapOf<String, Any?>(
      "formattedText" to formatted,
      "rawValue" to rawValue,
      "value" to value,
      "minorUnits" to minorUnits,
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
