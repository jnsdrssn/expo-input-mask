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

public class ExpoInputMaskModule: Module {
  public func definition() -> ModuleDefinition {
    Name("ExpoInputMask")

    Function("applyMask") { (options: ApplyMaskOptions) -> [String: Any] in
      let notations = (options.customNotations ?? []).map { record in
        Notation(
          character: Character(record.character),
          characterSet: CharacterSet(charactersIn: record.characterSet),
          isOptional: record.isOptional
        )
      }

      let primaryMask = try Mask.getOrCreate(
        withFormat: options.primaryFormat,
        customNotations: notations
      )

      let gravity: CaretString.CaretGravity = options.caretGravity == "backward"
        ? .backward(autoskip: options.autoskip)
        : .forward(autocomplete: options.autocomplete)

      let clampedPos = min(options.caretPosition, options.text.count)
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
