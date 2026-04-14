import { requireNativeModule } from 'expo-modules-core';
import type {
  ApplyMaskOptions,
  MaskResult,
  ApplyNumberFormatOptions,
  NumberFormatResult,
} from './ExpoInputMask.types';

const ExpoInputMaskNative = requireNativeModule('ExpoInputMask');

export function applyMask(options: ApplyMaskOptions): MaskResult {
  // Clamp caretPosition to valid range before crossing the bridge
  const caretPosition = Math.max(
    0,
    Math.min(options.caretPosition, options.text.length)
  );

  // Validate custom notations
  if (options.customNotations) {
    for (const notation of options.customNotations) {
      if (notation.character.length !== 1) {
        throw new Error(
          `customNotation.character must be exactly one character, got "${notation.character}"`
        );
      }
    }
  }

  return ExpoInputMaskNative.applyMask({ ...options, caretPosition });
}

export function applyNumberFormat(
  options: ApplyNumberFormatOptions
): NumberFormatResult & { exceeded: boolean } {
  const caretPosition = Math.max(
    0,
    Math.min(options.caretPosition, options.text.length)
  );

  return ExpoInputMaskNative.applyNumberFormat({ ...options, caretPosition });
}
