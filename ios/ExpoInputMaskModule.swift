import ExpoModulesCore
import InputMask

struct CustomNotationRecord: Record {
  @Field var character: String = ""
  @Field var characterSet: String = ""
  @Field var isOptional: Bool = false
}

struct ApplyMaskOptions: Record {
  @Field var primaryFormat: String = ""
  @Field var text: String = ""
  @Field var caretPosition: Int = 0
  @Field var caretGravity: String = "forward"
  @Field var autocomplete: Bool = true
  @Field var autoskip: Bool = false
  @Field var affinityFormats: [String]? = nil
  @Field var affinityStrategy: String? = nil
  @Field var customNotations: [CustomNotationRecord]? = nil
}

struct ApplyNumberFormatOptions: Record {
  @Field var text: String = ""
  @Field var caretPosition: Int = 0
  @Field var caretGravity: String = "forward"
  @Field var locale: String? = nil
  @Field var currency: String? = nil
  @Field var groupingSeparator: String? = nil
  @Field var decimalSeparator: String? = nil
  @Field var decimalPlaces: Int? = nil
  @Field var min: Double? = nil
  @Field var max: Double? = nil
  @Field var fixedDecimalPlaces: Bool? = nil
}

public class ExpoInputMaskModule: Module {
  public func definition() -> ModuleDefinition {
    Name("ExpoInputMask")

    Function("applyMask") { (options: ApplyMaskOptions) -> [String: Any] in
      let notations = try (options.customNotations ?? []).map { record -> Notation in
        guard let char = record.character.first else {
          throw Exception(name: "ERR_INVALID_NOTATION", description: "customNotation.character must be a single character")
        }
        return Notation(
          character: char,
          characterSet: CharacterSet(charactersIn: record.characterSet),
          isOptional: record.isOptional
        )
      }

      let primaryMask: Mask
      do {
        primaryMask = try Mask.getOrCreate(
          withFormat: options.primaryFormat,
          customNotations: notations
        )
      } catch {
        throw Exception(name: "ERR_INVALID_MASK", description: "Invalid mask format '\(options.primaryFormat)': \(error.localizedDescription)")
      }

      let gravity: CaretString.CaretGravity = options.caretGravity == "backward"
        ? .backward(autoskip: options.autoskip)
        : .forward(autocomplete: options.autocomplete)

      let clampedPos = max(0, min(options.caretPosition, options.text.count))
      let caretIndex = options.text.index(
        options.text.startIndex,
        offsetBy: clampedPos
      )

      let caretString = CaretString(
        string: options.text,
        caretPosition: caretIndex,
        caretGravity: gravity
      )

      if let affinityFormats = options.affinityFormats, !affinityFormats.isEmpty {
        let strategy = self.parseStrategy(options.affinityStrategy)
        let primaryResult = primaryMask.apply(toText: caretString)
        let primaryAffinity = strategy.calculateAffinity(
          ofMask: primaryMask,
          forText: caretString,
          autocomplete: options.autocomplete
        )

        var bestResult = primaryResult
        var bestAffinity = primaryAffinity

        for format in affinityFormats {
          guard let mask = try? Mask.getOrCreate(
            withFormat: format,
            customNotations: notations
          ) else { continue }

          let result = mask.apply(toText: caretString)
          let affinity = strategy.calculateAffinity(
            ofMask: mask,
            forText: caretString,
            autocomplete: options.autocomplete
          )
          if affinity > bestAffinity {
            bestAffinity = affinity
            bestResult = result
          }
        }

        return [
          "formattedText": bestResult.formattedText.string,
          "extractedValue": bestResult.extractedValue,
          "complete": bestResult.complete,
          "caretPosition": bestResult.formattedText.string.distance(
            from: bestResult.formattedText.string.startIndex,
            to: bestResult.formattedText.caretPosition
          ),
          "affinityOfPrimaryFormat": primaryAffinity
        ]
      }

      let result = primaryMask.apply(toText: caretString)

      return [
        "formattedText": result.formattedText.string,
        "extractedValue": result.extractedValue,
        "complete": result.complete,
        "caretPosition": result.formattedText.string.distance(
          from: result.formattedText.string.startIndex,
          to: result.formattedText.caretPosition
        )
      ]
    }

    Function("applyNumberFormat") { (options: ApplyNumberFormatOptions) -> [String: Any] in
      let resolvedLocale: Locale
      if let localeId = options.locale {
        resolvedLocale = Locale(identifier: localeId)
      } else {
        resolvedLocale = Locale.current
      }

      // Build the formatter once and read settings from it
      let formatter = NumberFormatter()
      formatter.locale = resolvedLocale
      if let currencyCode = options.currency {
        formatter.numberStyle = .currency
        formatter.currencyCode = currencyCode
      } else {
        formatter.numberStyle = .decimal
      }

      let effectiveDecimalSeparator = options.decimalSeparator ?? formatter.decimalSeparator ?? "."
      let maxFractionDigits = options.decimalPlaces ?? formatter.maximumFractionDigits

      // Strip input to digits and decimal separator only
      let inputText = options.text
      var digits = ""
      var hasDecimal = false
      var fractionCount = 0
      let clampedCaret = max(0, min(options.caretPosition, inputText.count))
      var contentCharsBeforeCaret = 0

      for (i, char) in inputText.enumerated() {
        if char.isNumber {
          if hasDecimal {
            if fractionCount >= maxFractionDigits {
              continue
            }
            fractionCount += 1
          }
          digits.append(char)
          if i < clampedCaret {
            contentCharsBeforeCaret += 1
          }
        } else if String(char) == effectiveDecimalSeparator && !hasDecimal && maxFractionDigits > 0 {
          hasDecimal = true
          digits.append(".")
          if i < clampedCaret {
            contentCharsBeforeCaret += 1
          }
        }
      }

      // Parse the numeric value
      let numericValue: Double?
      if digits.isEmpty {
        numericValue = nil
      } else {
        numericValue = Double(digits)
      }

      // Enforce max constraint: reject input if value exceeds max
      if let val = numericValue, let maxVal = options.max, val > maxVal {
        return [
          "formattedText": "",
          "value": "",
          "complete": false,
          "caretPosition": 0,
          "exceeded": true
        ]
      }

      // Apply overrides and fraction settings
      if let gs = options.groupingSeparator {
        formatter.groupingSeparator = gs
      }
      if let ds = options.decimalSeparator {
        formatter.decimalSeparator = ds
      }
      let isFixedDecimal = options.fixedDecimalPlaces == true
      if isFixedDecimal {
        formatter.minimumFractionDigits = maxFractionDigits
      } else {
        formatter.minimumFractionDigits = hasDecimal ? min(fractionCount, maxFractionDigits) : 0
      }
      formatter.maximumFractionDigits = maxFractionDigits

      // Format the number
      var formattedText: String
      if let val = numericValue {
        formattedText = formatter.string(from: NSNumber(value: val)) ?? digits
      } else {
        formattedText = ""
      }

      // When user typed a decimal point but no fraction digits yet, append the separator
      // so they can see it (NumberFormatter drops trailing decimal points)
      if hasDecimal && fractionCount == 0 && !isFixedDecimal && !formattedText.isEmpty {
        formattedText += (options.decimalSeparator ?? formatter.decimalSeparator ?? ".")
      }

      // Caret repositioning: walk the formatted string counting content chars
      let resolvedDecSep = options.decimalSeparator ?? formatter.decimalSeparator ?? "."
      var newCaretPosition = formattedText.count
      var contentCount = 0
      for (i, char) in formattedText.enumerated() {
        if char.isNumber || String(char) == resolvedDecSep {
          contentCount += 1
        }
        if contentCount == contentCharsBeforeCaret {
          newCaretPosition = i + 1
          break
        }
      }

      // Determine completeness based on min/max
      let complete: Bool
      if let val = numericValue {
        let aboveMin = options.min == nil || val >= options.min!
        let belowMax = options.max == nil || val <= options.max!
        complete = aboveMin && belowMax
      } else {
        complete = options.min == nil || options.min! <= 0
      }

      return [
        "formattedText": formattedText,
        "value": digits,
        "complete": complete,
        "caretPosition": newCaretPosition,
        "exceeded": false
      ]
    }

    View(NumberInputView.self) {
      // Note: events are registered under `onFocusEvent` / `onBlurEvent` rather
      // than `onFocus` / `onBlur` because Fabric reserves the latter names for
      // its own focus-handling pipeline. The JS wrapper (NumberInput.tsx)
      // transparently re-exposes them as `onFocus` / `onBlur` to users.
      Events(
        "onValueChange",
        "onFocusEvent",
        "onBlurEvent"
      )

      AsyncFunction("focus") { (view: NumberInputView) in
        view.focusField()
      }

      AsyncFunction("blur") { (view: NumberInputView) in
        view.blurField()
      }

      AsyncFunction("clear") { (view: NumberInputView) in
        view.clearField()
      }

      Prop("placeholder") { (view: NumberInputView, value: String?) in
        view.placeholder = value
      }

      Prop("editable") { (view: NumberInputView, value: Bool?) in
        view.isEditable = value ?? true
      }

      Prop("textAlign") { (view: NumberInputView, value: String?) in
        switch value {
        case "center":
          view.textAlignment = .center
        case "right":
          view.textAlignment = .right
        case "left":
          view.textAlignment = .left
        default:
          view.textAlignment = .natural
        }
      }

      Prop("keyboardType") { (view: NumberInputView, value: String?) in
        switch value {
        case "numeric", "number-pad":
          view.keyboardType = .numberPad
        case "decimal-pad":
          view.keyboardType = .decimalPad
        default:
          view.keyboardType = .decimalPad
        }
      }

      Prop("returnKeyType") { (view: NumberInputView, value: String?) in
        switch value {
        case "go": view.returnKeyType = .go
        case "next": view.returnKeyType = .next
        case "search": view.returnKeyType = .search
        case "send": view.returnKeyType = .send
        case "done": view.returnKeyType = .done
        default: view.returnKeyType = .default
        }
      }

      Prop("value") { (view: NumberInputView, value: Double?) in
        view.setExternalValue(value)
      }

      Prop("min") { (view: NumberInputView, value: Double?) in
        view.minValue = value
      }

      Prop("max") { (view: NumberInputView, value: Double?) in
        view.maxValue = value
      }

      Prop("locale") { (view: NumberInputView, value: String?) in
        view.propLocale = value
      }

      Prop("currency") { (view: NumberInputView, value: String?) in
        view.propCurrency = value
      }

      Prop("groupingSeparator") { (view: NumberInputView, value: String?) in
        view.propGroupingSeparator = value
      }

      Prop("decimalSeparator") { (view: NumberInputView, value: String?) in
        view.propDecimalSeparator = value
      }

      Prop("decimalPlaces") { (view: NumberInputView, value: Int?) in
        view.propDecimalPlaces = value
      }

      Prop("mode") { (view: NumberInputView, value: String?) in
        view.propMode = value
      }

      OnViewDidUpdateProps { (view: NumberInputView) in
        view.updateFormatter()
      }
    }
  }

  private func parseStrategy(_ strategy: String?) -> AffinityCalculationStrategy {
    switch strategy {
    case "prefix":
      return .prefix
    case "capacity":
      return .capacity
    case "extracted_value_capacity":
      return .extractedValueCapacity
    default:
      return .wholeString
    }
  }
}
