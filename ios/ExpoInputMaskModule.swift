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
      let r = NumberFormattingAlgorithm.apply(
        text: options.text,
        caretPosition: options.caretPosition,
        locale: options.locale,
        currency: options.currency,
        groupingSeparator: options.groupingSeparator,
        decimalSeparator: options.decimalSeparator,
        decimalPlaces: options.decimalPlaces,
        fixedDecimalPlaces: options.fixedDecimalPlaces ?? false,
        min: options.min,
        max: options.max
      )
      return [
        "formattedText": r.formattedText,
        "value": r.value,
        "complete": r.complete,
        "caretPosition": r.caretPosition,
        "exceeded": r.exceeded,
        "minorUnits": r.minorUnits.map { NSNumber(value: $0) } ?? NSNull()
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
