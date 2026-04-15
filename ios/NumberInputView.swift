import ExpoModulesCore
import InputMask
import UIKit

class NumberInputView: UITextField {
  // MARK: - Event Dispatchers

  let onChangeText = EventDispatcher()
  let onNumberResult = EventDispatcher()
  let onFocusEvent = EventDispatcher()
  let onBlurEvent = EventDispatcher()

  // MARK: - Constraints

  var minValue: Double?
  var maxValue: Double?

  // Stored prop values for formatter configuration
  var propLocale: String?
  var propCurrency: String?
  var propGroupingSeparator: String?
  var propDecimalSeparator: String?
  var propDecimalPlaces: Int?
  var propFixedDecimalPlaces: Bool?

  // MARK: - Private State

  private var previousText: String = ""
  private var previousValue: String = ""

  // MARK: - NumberInputListener

  lazy var numberListener: NumberInputListener = {
    let listener = NumberInputListener(
      primaryFormat: "",
      autocomplete: false,
      autocompleteOnFocus: false,
      autoskip: false,
      rightToLeft: false,
      affineFormats: [],
      affinityCalculationStrategy: .wholeString,
      customNotations: [],
      onMaskedTextChangedCallback: { [weak self] textInput, value, complete, tailPlaceholder in
        self?.handleMaskedTextChanged(textInput: textInput, value: value, complete: complete)
      },
      allowSuggestions: true
    )
    listener.atomicCaretMovement = true
    return listener
  }()

  // MARK: - Initializers

  required init(appContext: AppContext? = nil) {
    super.init(frame: .zero)
    commonInit()
  }

  override init(frame: CGRect) {
    super.init(frame: frame)
    commonInit()
  }

  required init?(coder: NSCoder) {
    super.init(coder: coder)
    commonInit()
  }

  private func commonInit() {
    delegate = numberListener
    keyboardType = .decimalPad
  }

  // MARK: - Focus / Blur

  @discardableResult
  override func becomeFirstResponder() -> Bool {
    let result = super.becomeFirstResponder()
    if result {
      onFocusEvent([:])
    }
    return result
  }

  @discardableResult
  override func resignFirstResponder() -> Bool {
    let result = super.resignFirstResponder()
    if result {
      onBlurEvent([:])
    }
    return result
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
    let formatter = NumberFormatter()
    formatter.roundingMode = .floor

    if let localeId = locale {
      formatter.locale = Locale(identifier: localeId)
    } else {
      formatter.locale = Locale(identifier: "en_US")
    }

    if let currencyCode = currency {
      formatter.numberStyle = .currency
      formatter.currencyCode = currencyCode
    } else {
      formatter.numberStyle = .decimal
    }

    if let gs = groupingSeparator {
      formatter.groupingSeparator = gs
    }

    if let ds = decimalSeparator {
      formatter.decimalSeparator = ds
    } else {
      formatter.decimalSeparator = formatter.locale.decimalSeparator ?? "."
    }

    let maxFrac = decimalPlaces ?? 2
    formatter.maximumFractionDigits = maxFrac

    if fixedDecimalPlaces == true {
      formatter.minimumFractionDigits = maxFrac
    } else {
      formatter.minimumFractionDigits = 0
    }

    numberListener.formatter = formatter
  }

  // MARK: - Controlled Mode

  func setExternalValue(_ value: String) {
    numberListener.put(text: value, into: self, autocomplete: false)
  }

  // MARK: - Masked Text Changed Callback

  private func handleMaskedTextChanged(textInput: UITextInput, value: String, complete: Bool) {
    let formattedText = self.text ?? ""
    let numericValue = parseNumericValue(from: formattedText)

    // Enforce max: if value exceeds max, revert to previous text
    if let val = numericValue, let maxVal = maxValue, val > maxVal {
      // Revert the text field
      numberListener.put(text: previousValue, into: self, autocomplete: false)
      return
    }

    // Determine completeness factoring in min
    var isComplete = complete
    if let val = numericValue, let minVal = minValue {
      if val < minVal {
        isComplete = false
      }
    }

    // Store current state for potential revert
    previousText = formattedText
    previousValue = value

    // Fire events
    onChangeText(["text": value])

    let jsValue: Any = numericValue != nil ? numericValue! : NSNull()
    onNumberResult([
      "formattedText": formattedText,
      "value": jsValue,
      "complete": isComplete
    ])
  }

  // MARK: - Numeric Parsing

  private func parseNumericValue(from text: String) -> Double? {
    guard !text.isEmpty else { return nil }

    let decSep = numberListener.formatter?.decimalSeparator ?? "."
    var digits = ""

    for char in text {
      if char.isNumber {
        digits.append(char)
      } else if String(char) == decSep {
        digits.append(".")
      }
    }

    guard !digits.isEmpty else { return nil }
    return Double(digits)
  }
}
