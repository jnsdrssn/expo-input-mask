package expo.modules.inputmask

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.records.Field
import expo.modules.kotlin.records.Record
import com.redmadrobot.inputmask.helper.Mask
import com.redmadrobot.inputmask.model.CaretString
import com.redmadrobot.inputmask.model.Notation
import com.redmadrobot.inputmask.helper.AffinityCalculationStrategy

class CustomNotationRecord : Record {
  @Field val character: String = ""
  @Field val characterSet: String = ""
  @Field val isOptional: Boolean = false
}

class ApplyMaskOptions : Record {
  @Field val primaryFormat: String = ""
  @Field val text: String = ""
  @Field val caretPosition: Int = 0
  @Field val caretGravity: String = "forward"
  @Field val autocomplete: Boolean = true
  @Field val autoskip: Boolean = false
  @Field val affinityFormats: List<String>? = null
  @Field val affinityStrategy: String? = null
  @Field val customNotations: List<CustomNotationRecord>? = null
}

class ExpoInputMaskModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("ExpoInputMask")

    Function("applyMask") { options: ApplyMaskOptions ->
      val notations = (options.customNotations ?: emptyList()).map { record ->
        require(record.character.isNotEmpty()) { "customNotation.character must be a single character" }
        val char = record.character[0]
        Notation(character = char, characterSet = record.characterSet, isOptional = record.isOptional)
      }

      val primaryMask = Mask.getOrCreate(options.primaryFormat, notations)

      val gravity: CaretString.CaretGravity =
        if (options.caretGravity == "backward") {
          CaretString.CaretGravity.BACKWARD(options.autoskip)
        } else {
          CaretString.CaretGravity.FORWARD(options.autocomplete)
        }

      val caretPos = options.caretPosition.coerceIn(0, options.text.length)
      val caretString = CaretString(options.text, caretPos, gravity)

      if (options.affinityFormats != null && options.affinityFormats!!.isNotEmpty()) {
        val strategy = parseStrategy(options.affinityStrategy)
        val primaryResult = primaryMask.apply(caretString)
        val primaryAffinity = strategy.calculateAffinityOfMask(primaryMask, caretString)

        var bestResult = primaryResult
        var bestAffinity = primaryAffinity

        for (format in options.affinityFormats!!) {
          val mask = Mask.getOrCreate(format, notations)
          val result = mask.apply(caretString)
          val affinity = strategy.calculateAffinityOfMask(mask, caretString)
          if (affinity > bestAffinity) {
            bestAffinity = affinity
            bestResult = result
          }
        }

        return@Function mapOf(
          "formattedText" to bestResult.formattedText.string,
          "extractedValue" to bestResult.extractedValue,
          "complete" to bestResult.complete,
          "caretPosition" to bestResult.formattedText.caretPosition,
          "affinityOfPrimaryFormat" to primaryAffinity
        )
      }

      val result = primaryMask.apply(caretString)

      mapOf(
        "formattedText" to result.formattedText.string,
        "extractedValue" to result.extractedValue,
        "complete" to result.complete,
        "caretPosition" to result.formattedText.caretPosition
      )
    }
  }

  private fun parseStrategy(strategy: String?): AffinityCalculationStrategy {
    return when (strategy) {
      "prefix" -> AffinityCalculationStrategy.PREFIX
      "capacity" -> AffinityCalculationStrategy.CAPACITY
      "extracted_value_capacity" -> AffinityCalculationStrategy.EXTRACTED_VALUE_CAPACITY
      else -> AffinityCalculationStrategy.WHOLE_STRING
    }
  }
}
