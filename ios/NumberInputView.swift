import ExpoModulesCore
import UIKit

class NumberInputView: ExpoView, UITextFieldDelegate {
  // MARK: - Event Dispatchers

  let onValueChange = EventDispatcher()
  let onFocusEvent = EventDispatcher()
  let onBlurEvent = EventDispatcher()

  // MARK: - Subview

  // Plain UITextField — no opinionated padding. Consumers style via
  // `<NumberInput style={...} />` and any wrapping View as they would a
  // regular `<TextInput />`.
  private let textField = UITextField()

  // MARK: - Constraints

  var minValue: Double?
  var maxValue: Double?

  // MARK: - Stored Prop Values

  // Each formatting-config prop sets `formatterDirty` when it actually changes.
  // `OnViewDidUpdateProps` then skips the formatter rebuild on prop batches
  // that only echoed the controlled `value` back — the typical
  // `onValueChange` → `setState` → `value` cycle in controlled mode.
  var propLocale: String? { didSet { if oldValue != propLocale { formatterDirty = true } } }
  var propCurrency: String? { didSet { if oldValue != propCurrency { formatterDirty = true } } }
  var propGroupingSeparator: String? { didSet { if oldValue != propGroupingSeparator { formatterDirty = true } } }
  var propDecimalSeparator: String? { didSet { if oldValue != propDecimalSeparator { formatterDirty = true } } }
  var propDecimalPlaces: Int? { didSet { if oldValue != propDecimalPlaces { formatterDirty = true } } }
  // "decimal" (default) or "cents" — kept as a string so the native-JS type is simple.
  var propMode: String? { didSet { if oldValue != propMode { formatterDirty = true } } }
  // Last value the parent passed via `value`. Stashed so we can re-render it
  // after `updateFormatter()` resolves the final locale/currency on first mount.
  private var propValue: Double?
  private var hasPropValue: Bool = false
  // Initial `true` so the first `updateFormatter()` after mount always rebuilds.
  private var formatterDirty: Bool = true

  // MARK: - Derived state from props

  // Used to look up the active decimal separator for input normalization, and
  // for formatting the controlled `value` prop in `applyExternalValue`.
  private var formatter: NumberFormatter = {
    let f = NumberFormatter()
    f.numberStyle = .decimal
    f.locale = Locale(identifier: "en_US")
    f.minimumFractionDigits = 0
    f.maximumFractionDigits = 2
    f.roundingMode = .floor
    return f
  }()

  private var effectiveDecimalPlaces: Int = 2

  private var isCentsMode: Bool {
    return propMode == "cents"
  }

  // MARK: - Initializer

  required init(appContext: AppContext? = nil) {
    super.init(appContext: appContext)
    textField.frame = bounds
    textField.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    textField.keyboardType = .decimalPad
    textField.delegate = self
    textField.addTarget(self, action: #selector(handleEditingDidBegin), for: .editingDidBegin)
    textField.addTarget(self, action: #selector(handleEditingDidEnd), for: .editingDidEnd)
    addSubview(textField)
  }

  // MARK: - Forwarded Props

  var placeholder: String? {
    get { textField.placeholder }
    set { textField.placeholder = newValue }
  }

  var textAlignment: NSTextAlignment {
    get { textField.textAlignment }
    set { textField.textAlignment = newValue }
  }

  var keyboardType: UIKeyboardType {
    get { textField.keyboardType }
    set { textField.keyboardType = newValue }
  }

  var returnKeyType: UIReturnKeyType {
    get { textField.returnKeyType }
    set { textField.returnKeyType = newValue }
  }

  var isEditable: Bool {
    get { textField.isUserInteractionEnabled }
    set { textField.isUserInteractionEnabled = newValue }
  }

  // MARK: - Focus / Blur

  @objc private func handleEditingDidBegin() {
    onFocusEvent([:])
  }

  @objc private func handleEditingDidEnd() {
    onBlurEvent([:])
  }

  // MARK: - Imperative Methods (exposed as AsyncFunctions from the module)

  func focusField() {
    textField.becomeFirstResponder()
  }

  func blurField() {
    textField.resignFirstResponder()
  }

  func clearField() {
    textField.text = ""
    fireValueChange(rawValue: "", formatted: "", value: nil)
  }

  // MARK: - Formatter Configuration

  func updateFormatter() {
    if formatterDirty {
      // Resolve decimal places: explicit > currency default > 2.
      if let explicit = propDecimalPlaces {
        effectiveDecimalPlaces = explicit
      } else if let currencyCode = propCurrency {
        let probe = NumberFormatter()
        probe.numberStyle = .currency
        probe.currencyCode = currencyCode
        effectiveDecimalPlaces = probe.maximumFractionDigits
      } else {
        effectiveDecimalPlaces = 2
      }

      let f = NumberFormatter()
      f.roundingMode = .floor

      if let localeId = propLocale {
        f.locale = Locale(identifier: localeId)
      } else {
        f.locale = Locale(identifier: "en_US")
      }

      if let currencyCode = propCurrency {
        f.numberStyle = .currency
        f.currencyCode = currencyCode
      } else {
        f.numberStyle = .decimal
      }

      if let gs = propGroupingSeparator {
        f.groupingSeparator = gs
        f.currencyGroupingSeparator = gs
      }

      if let ds = propDecimalSeparator {
        f.decimalSeparator = ds
        f.currencyDecimalSeparator = ds
      }

      f.maximumFractionDigits = effectiveDecimalPlaces
      f.minimumFractionDigits = isCentsMode ? effectiveDecimalPlaces : 0

      formatter = f
      formatterDirty = false
    }

    // Always re-apply the controlled value: on a dirty rebuild this catches
    // the first-mount race (default en_US paint before locale/currency resolve);
    // on a clean batch this lets a `value`-only echo paint through (cheap —
    // `applyExternalValue` skips when text is already equal).
    applyExternalValue()
  }

  // MARK: - Controlled Mode

  /// Stash the externally-provided value, then re-render. The actual paint is
  /// deferred to `applyExternalValue` so it can run again from `updateFormatter`
  /// after the rest of the prop batch has resolved.
  func setExternalValue(_ value: Double?) {
    propValue = value
    hasPropValue = true
    applyExternalValue()
  }

  /// Renders `propValue` into the text field. No-op while the field is focused
  /// so we don't race with active typing (the parent typically echoes every
  /// `onValueChange` back via `value={state}`, and mid-typing values like
  /// "1." would otherwise be overwritten by the re-format of 1.0 → "1").
  private func applyExternalValue() {
    if !hasPropValue { return }
    if textField.isFirstResponder { return }

    formatter.minimumFractionDigits = isCentsMode ? effectiveDecimalPlaces : 0
    formatter.maximumFractionDigits = effectiveDecimalPlaces

    let formatted: String
    if let v = propValue {
      formatted = formatter.string(from: NSNumber(value: v)) ?? ""
    } else {
      formatted = ""
    }

    if textField.text != formatted {
      textField.text = formatted
      let end = textField.endOfDocument
      textField.selectedTextRange = textField.textRange(from: end, to: end)
    }
  }

  // MARK: - UITextFieldDelegate

  func textField(
    _ textField: UITextField,
    shouldChangeCharactersIn range: NSRange,
    replacementString string: String
  ) -> Bool {
    // Normalize user-typed `.` / `,` to the formatter's decimal separator.
    // iOS decimal-pad follows the system locale, not the app's — so a de-DE
    // NumberInput on an en-US device shows only "." on-screen; convert it.
    let decSep = formatter.decimalSeparator ?? "."
    var normalizedString = ""
    for ch in string {
      let s = String(ch)
      if (s == "." || s == ",") && s != decSep {
        normalizedString += decSep
      } else {
        normalizedString.append(ch)
      }
    }

    let current = (textField.text ?? "") as NSString
    let candidate = current.replacingCharacters(in: range, with: normalizedString)
    let caret = range.location + (normalizedString as NSString).length

    let result: NumberFormattingAlgorithm.Result
    if isCentsMode {
      result = NumberFormattingAlgorithm.applyCents(
        text: candidate,
        decimalPlaces: effectiveDecimalPlaces,
        locale: propLocale,
        currency: propCurrency,
        groupingSeparator: propGroupingSeparator,
        decimalSeparator: propDecimalSeparator,
        min: minValue,
        max: maxValue
      )
    } else {
      result = NumberFormattingAlgorithm.apply(
        text: candidate,
        caretPosition: caret,
        locale: propLocale,
        currency: propCurrency,
        groupingSeparator: propGroupingSeparator,
        decimalSeparator: propDecimalSeparator,
        decimalPlaces: propDecimalPlaces,
        fixedDecimalPlaces: false,
        min: minValue,
        max: maxValue
      )
    }

    if result.exceeded {
      // Returning false leaves the existing text in place — the new keypress
      // is silently rejected.
      return false
    }

    textField.text = result.formattedText
    if let pos = textField.position(from: textField.beginningOfDocument, offset: result.caretPosition) {
      textField.selectedTextRange = textField.textRange(from: pos, to: pos)
    }

    let numericValue: Double? = result.value.isEmpty ? nil : Double(result.value)
    fireValueChange(rawValue: result.value, formatted: result.formattedText, value: numericValue)
    return false
  }

  // MARK: - Event Dispatch

  private func fireValueChange(rawValue: String, formatted: String, value: Double?) {
    let jsValue: Any = value != nil ? value! : NSNull()
    onValueChange([
      "formattedText": formatted,
      "rawValue": rawValue,
      "value": jsValue,
      "complete": isComplete(value: value)
    ])
  }

  private func isComplete(value: Double?) -> Bool {
    if let v = value {
      let aboveMin = minValue == nil || v >= minValue!
      let belowMax = maxValue == nil || v <= maxValue!
      return aboveMin && belowMax
    }
    return minValue == nil || minValue! <= 0
  }
}
