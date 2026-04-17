import ExpoModulesCore
import UIKit

private class PaddedTextField: UITextField {
  var contentPadding = UIEdgeInsets(top: 0, left: 12, bottom: 0, right: 12)

  override func textRect(forBounds bounds: CGRect) -> CGRect {
    return bounds.inset(by: contentPadding)
  }

  override func editingRect(forBounds bounds: CGRect) -> CGRect {
    return bounds.inset(by: contentPadding)
  }

  override func placeholderRect(forBounds bounds: CGRect) -> CGRect {
    return bounds.inset(by: contentPadding)
  }
}

class NumberInputView: ExpoView, UITextFieldDelegate {
  // MARK: - Event Dispatchers

  let onChangeText = EventDispatcher()
  let onNumberResult = EventDispatcher()
  let onFocusEvent = EventDispatcher()
  let onBlurEvent = EventDispatcher()

  // MARK: - Subview

  private let textField = PaddedTextField()

  // MARK: - Constraints

  var minValue: Double?
  var maxValue: Double?

  // MARK: - Stored Prop Values

  var propLocale: String?
  var propCurrency: String?
  var propGroupingSeparator: String?
  var propDecimalSeparator: String?
  var propDecimalPlaces: Int?
  var propFixedDecimalPlaces: Bool?

  // MARK: - Formatter

  private var formatter: NumberFormatter = {
    let f = NumberFormatter()
    f.numberStyle = .decimal
    f.locale = Locale(identifier: "en_US")
    f.minimumFractionDigits = 0
    f.maximumFractionDigits = 2
    f.roundingMode = .floor
    return f
  }()

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

  // MARK: - Formatter Configuration

  func updateFormatter(
    locale: String?,
    currency: String?,
    groupingSeparator: String?,
    decimalSeparator: String?,
    decimalPlaces: Int?,
    fixedDecimalPlaces: Bool?
  ) {
    let f = NumberFormatter()
    f.roundingMode = .floor

    if let localeId = locale {
      f.locale = Locale(identifier: localeId)
    } else {
      f.locale = Locale(identifier: "en_US")
    }

    if let currencyCode = currency {
      f.numberStyle = .currency
      f.currencyCode = currencyCode
    } else {
      f.numberStyle = .decimal
    }

    if let gs = groupingSeparator {
      f.groupingSeparator = gs
      f.currencyGroupingSeparator = gs
    }

    if let ds = decimalSeparator {
      f.decimalSeparator = ds
      f.currencyDecimalSeparator = ds
    }

    let maxFrac = decimalPlaces ?? 2
    f.maximumFractionDigits = maxFrac
    f.minimumFractionDigits = fixedDecimalPlaces == true ? maxFrac : 0

    formatter = f
  }

  // MARK: - Controlled Mode

  func setExternalValue(_ value: String) {
    let parsed = Double(value)
    let decimalPlaces = propDecimalPlaces ?? 2
    let isFixed = propFixedDecimalPlaces == true

    formatter.minimumFractionDigits = isFixed ? decimalPlaces : 0
    formatter.maximumFractionDigits = decimalPlaces

    let formatted: String
    if let v = parsed {
      formatted = formatter.string(from: NSNumber(value: v)) ?? value
    } else {
      formatted = ""
    }

    if textField.text != formatted {
      textField.text = formatted
      if !textField.isFirstResponder {
        let end = textField.endOfDocument
        textField.selectedTextRange = textField.textRange(from: end, to: end)
      }
    }
  }

  // MARK: - UITextFieldDelegate

  func textField(
    _ textField: UITextField,
    shouldChangeCharactersIn range: NSRange,
    replacementString string: String
  ) -> Bool {
    // Normalize user-typed `.` / `,` to the formatter's decimal separator.
    // The iOS decimal-pad keyboard does not respect the app's configured locale
    // (it follows the system locale), so a de-DE NumberInput on an en-US device
    // shows only "." on-screen — convert it to the expected ",".
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

    let decimalPlaces = propDecimalPlaces ?? 2

    if propFixedDecimalPlaces == true {
      return applyCentsMode(candidate: candidate, decimalPlaces: decimalPlaces)
    } else {
      return applyDecimalMode(candidate: candidate, caret: caret, decimalPlaces: decimalPlaces)
    }
  }

  // MARK: - Cents Mode (fixedDecimalPlaces = true)

  private func applyCentsMode(candidate: String, decimalPlaces: Int) -> Bool {
    var digits = ""
    for char in candidate where char.isNumber {
      digits.append(char)
    }
    digits = String(digits.prefix(15))  // guard Double precision

    let value: Double?
    if digits.isEmpty {
      value = nil
    } else {
      let intPart = Double(digits) ?? 0
      value = intPart / pow(10.0, Double(decimalPlaces))
    }

    if let v = value, let maxVal = maxValue, v > maxVal {
      return false
    }

    formatter.minimumFractionDigits = decimalPlaces
    formatter.maximumFractionDigits = decimalPlaces

    let formatted: String
    if let v = value {
      formatted = formatter.string(from: NSNumber(value: v)) ?? digits
    } else {
      formatted = ""
    }

    textField.text = formatted
    let end = textField.endOfDocument
    textField.selectedTextRange = textField.textRange(from: end, to: end)

    let rawValue: String
    if let v = value {
      rawValue = String(format: "%.\(decimalPlaces)f", v)
    } else {
      rawValue = ""
    }

    fireEvents(rawValue: rawValue, formatted: formatted, value: value)
    return false
  }

  // MARK: - Decimal Mode

  private func applyDecimalMode(candidate: String, caret: Int, decimalPlaces: Int) -> Bool {
    let decSep = formatter.decimalSeparator ?? "."
    let maxFrac = decimalPlaces

    var canonical = ""
    var hasDecimal = false
    var fractionCount = 0
    var contentCharsBeforeCaret = 0
    let clampedCaret = max(0, min(caret, candidate.count))

    for (i, char) in candidate.enumerated() {
      if char.isNumber {
        if hasDecimal && fractionCount >= maxFrac {
          continue
        }
        if hasDecimal {
          fractionCount += 1
        }
        canonical.append(char)
        if i < clampedCaret {
          contentCharsBeforeCaret += 1
        }
      } else if String(char) == decSep && !hasDecimal && maxFrac > 0 {
        hasDecimal = true
        canonical.append(".")
        if i < clampedCaret {
          contentCharsBeforeCaret += 1
        }
      }
    }

    let numericValue = canonical.isEmpty ? nil : Double(canonical)

    if let v = numericValue, let maxVal = maxValue, v > maxVal {
      return false
    }

    formatter.minimumFractionDigits = hasDecimal ? min(fractionCount, maxFrac) : 0
    formatter.maximumFractionDigits = maxFrac

    var formatted: String
    if let v = numericValue {
      formatted = formatter.string(from: NSNumber(value: v)) ?? canonical
    } else {
      formatted = ""
    }

    // Trailing decimal separator: user typed separator but no fraction digits yet.
    // Render the number with one decimal digit, then strip that placeholder digit —
    // this keeps the currency suffix (e.g. " €") in the correct position.
    if hasDecimal && fractionCount == 0, let v = numericValue, !formatted.isEmpty {
      formatter.minimumFractionDigits = 1
      formatter.maximumFractionDigits = 1
      if let oneFrac = formatter.string(from: NSNumber(value: v)),
         let sepRange = oneFrac.range(of: decSep) {
        let afterSep = oneFrac.index(after: sepRange.lowerBound)
        if afterSep < oneFrac.endIndex {
          var mutable = oneFrac
          mutable.remove(at: afterSep)
          formatted = mutable
        } else {
          formatted = oneFrac
        }
      }
    }

    // Caret mapping: walk formatted, count chars matching digit or decSep
    var newCaret = formatted.count
    var contentCount = 0
    for (i, char) in formatted.enumerated() {
      if char.isNumber || String(char) == decSep {
        contentCount += 1
      }
      if contentCount == contentCharsBeforeCaret {
        newCaret = i + 1
        break
      }
    }

    textField.text = formatted
    if let pos = textField.position(from: textField.beginningOfDocument, offset: newCaret) {
      textField.selectedTextRange = textField.textRange(from: pos, to: pos)
    }

    fireEvents(rawValue: canonical, formatted: formatted, value: numericValue)
    return false
  }

  // MARK: - Event Dispatch

  private func fireEvents(rawValue: String, formatted: String, value: Double?) {
    onChangeText(["text": rawValue])
    let jsValue: Any = value != nil ? value! : NSNull()
    onNumberResult([
      "formattedText": formatted,
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
