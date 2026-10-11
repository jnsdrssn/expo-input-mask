import CoreText
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

  // MARK: - Text Style Props

  // The `<TextInput />` text-style props, forwarded by the JS wrapper from
  // `style` (font, color, letter spacing, font variant) and from the top level
  // (placeholder and caret colors, font scaling). Each marks the style dirty
  // when it changes; `applyTextStyle()` (from `OnViewDidUpdateProps`) rebuilds
  // once per prop batch. Until one is set, the field keeps UITextField's own
  // font and colors.
  var propFontFamily: String? { didSet { if oldValue != propFontFamily { styleDirty = true } } }
  var propFontSize: Double? { didSet { if oldValue != propFontSize { styleDirty = true } } }
  var propFontWeight: String? { didSet { if oldValue != propFontWeight { styleDirty = true } } }
  var propFontVariant: [String]? { didSet { if oldValue != propFontVariant { styleDirty = true } } }
  var propLetterSpacing: Double? { didSet { if oldValue != propLetterSpacing { styleDirty = true } } }
  var propAllowFontScaling: Bool? { didSet { if oldValue != propAllowFontScaling { styleDirty = true } } }
  var propColor: UIColor? { didSet { if oldValue != propColor { styleDirty = true } } }
  var propPlaceholderTextColor: UIColor? { didSet { if oldValue != propPlaceholderTextColor { styleDirty = true } } }
  var propSelectionColor: UIColor? { didSet { updateCaretColor() } }
  var propCursorColor: UIColor? { didSet { updateCaretColor() } }
  private var styleDirty: Bool = false
  private var placeholderText: String?
  // UITextField's own font and color, restored when the style props are removed.
  private var defaultFont: UIFont?
  private var defaultTextColor: UIColor?

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
    defaultFont = textField.font
    defaultTextColor = textField.textColor
  }

  // MARK: - Forwarded Props

  var placeholder: String? {
    get { placeholderText }
    set {
      placeholderText = newValue
      updatePlaceholder()
    }
  }

  // Set on the text field itself, so VoiceOver focuses the field and reads
  // this label instead of the placeholder.
  var fieldAccessibilityLabel: String? {
    get { textField.accessibilityLabel }
    set { textField.accessibilityLabel = newValue }
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

  // MARK: - Text Style

  func applyTextStyle() {
    if !styleDirty { return }
    styleDirty = false

    let font = resolveFont()
    textField.font = font
    textField.adjustsFontForContentSizeCategory = hasFontProps && (propAllowFontScaling ?? true)
    textField.textColor = propColor ?? defaultTextColor

    var attributes = textField.defaultTextAttributes
    if let spacing = propLetterSpacing {
      attributes[.kern] = CGFloat(spacing)
    } else {
      attributes.removeValue(forKey: .kern)
    }
    textField.defaultTextAttributes = attributes

    updatePlaceholder()
  }

  private var hasFontProps: Bool {
    return propFontFamily != nil
      || propFontSize != nil
      || propFontWeight != nil
      || !(propFontVariant ?? []).isEmpty
  }

  private func updatePlaceholder() {
    guard let color = propPlaceholderTextColor, let text = placeholderText else {
      textField.placeholder = placeholderText
      return
    }
    var attributes: [NSAttributedString.Key: Any] = [.foregroundColor: color]
    if let font = textField.font {
      attributes[.font] = font
    }
    textField.attributedPlaceholder = NSAttributedString(string: text, attributes: attributes)
  }

  // iOS draws the caret and selection in the tint color; `cursorColor` wins
  // over `selectionColor` when both are set. `nil` restores the inherited tint.
  private func updateCaretColor() {
    textField.tintColor = propCursorColor ?? propSelectionColor
  }

  private func resolveFont() -> UIFont? {
    if !hasFontProps { return defaultFont }

    let size = CGFloat(propFontSize ?? Double(defaultFont?.pointSize ?? 17))
    let weight = NumberInputView.fontWeight(propFontWeight) ?? .regular

    var font = propFontFamily.flatMap {
      NumberInputView.customFont(family: $0, size: size, weight: weight)
    } ?? UIFont.systemFont(ofSize: size, weight: weight)

    let features = NumberInputView.fontFeatures(propFontVariant ?? [])
    if !features.isEmpty {
      let descriptor = font.fontDescriptor.addingAttributes([.featureSettings: features])
      font = UIFont(descriptor: descriptor, size: size)
    }

    if propAllowFontScaling ?? true {
      font = UIFontMetrics.default.scaledFont(for: font)
    }
    return font
  }

  /// A font by PostScript name, else by family (including expo-font aliases,
  /// which `UIFont.fontNames(forFamilyName:)` resolves), picking the face
  /// whose weight is nearest. `nil` when nothing by that name is registered.
  private static func customFont(family: String, size: CGFloat, weight: UIFont.Weight) -> UIFont? {
    if let font = UIFont(name: family, size: size) {
      return font
    }
    let fonts = UIFont.fontNames(forFamilyName: family).compactMap { UIFont(name: $0, size: size) }
    if fonts.count <= 1 {
      return fonts.first
    }
    return fonts.min { a, b in
      abs(faceWeight(a) - weight.rawValue) < abs(faceWeight(b) - weight.rawValue)
    }
  }

  private static func faceWeight(_ font: UIFont) -> CGFloat {
    let traits = font.fontDescriptor.object(forKey: .traits) as? [UIFontDescriptor.TraitKey: Any]
    return (traits?[.weight] as? CGFloat) ?? UIFont.Weight.regular.rawValue
  }

  private static func fontWeight(_ value: String?) -> UIFont.Weight? {
    switch value {
    case "100": return .ultraLight
    case "200": return .thin
    case "300": return .light
    case "400", "normal": return .regular
    case "500": return .medium
    case "600": return .semibold
    case "700", "bold": return .bold
    case "800": return .heavy
    case "900": return .black
    default: return nil
    }
  }

  private static func fontFeatures(_ variants: [String]) -> [[UIFontDescriptor.FeatureKey: Int]] {
    return variants.compactMap { variant -> [UIFontDescriptor.FeatureKey: Int]? in
      switch variant {
      case "tabular-nums":
        return [.type: kNumberSpacingType, .selector: kMonospacedNumbersSelector]
      case "proportional-nums":
        return [.type: kNumberSpacingType, .selector: kProportionalNumbersSelector]
      case "lining-nums":
        return [.type: kNumberCaseType, .selector: kUpperCaseNumbersSelector]
      case "oldstyle-nums":
        return [.type: kNumberCaseType, .selector: kLowerCaseNumbersSelector]
      case "small-caps":
        return [.type: kLowerCaseType, .selector: kLowerCaseSmallCapsSelector]
      default:
        return nil
      }
    }
  }

  // MARK: - Focus / Blur

  @objc private func handleEditingDidBegin() {
    onFocusEvent([:])
  }

  @objc private func handleEditingDidEnd() {
    onBlurEvent([:])
  }

  // MARK: - Imperative Methods (exposed as AsyncFunctions from the module)

  // `autoFocus`: focus once, when the view first reaches a window. A
  // `focus()` call from JS at mount can arrive before the view exists
  // natively, so the native side does it at the moment it can.
  var autoFocus = false
  private var autoFocused = false

  override func didMoveToWindow() {
    super.didMoveToWindow()
    if window != nil && autoFocus && !autoFocused {
      autoFocused = true
      DispatchQueue.main.async { [weak self] in self?.focusField() }
    }
  }

  func focusField() {
    textField.becomeFirstResponder()
  }

  func blurField() {
    textField.resignFirstResponder()
  }

  func clearField() {
    textField.text = ""
    fireValueChange(rawValue: "", formatted: "", value: nil, minorUnits: nil)
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
    fireValueChange(rawValue: result.value, formatted: result.formattedText, value: numericValue, minorUnits: result.minorUnits)
    return false
  }

  // MARK: - Event Dispatch

  private func fireValueChange(rawValue: String, formatted: String, value: Double?, minorUnits: Int64?) {
    let jsValue: Any = value != nil ? value! : NSNull()
    let jsMinor: Any = minorUnits.map { NSNumber(value: $0) } ?? NSNull()
    onValueChange([
      "formattedText": formatted,
      "rawValue": rawValue,
      "value": jsValue,
      "minorUnits": jsMinor,
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
