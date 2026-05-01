import { requireNativeModule } from 'expo-modules-core';

import type {
  ApplyCurrencyFormatOptions,
  ApplyMaskOptions,
  ApplyNumberFormatOptions,
  CurrencyFormatResult,
  MaskResult,
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

  // Native always emits `minorUnits` (used by the currency surface). Strip it
  // here so the non-currency `NumberFormatResult` shape is honest.
  const result = ExpoInputMaskNative.applyNumberFormat({
    ...options,
    caretPosition,
  });
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const { minorUnits: _minorUnits, ...withoutMinor } = result;
  return withoutMinor;
}

export function applyCurrencyFormat(
  options: ApplyCurrencyFormatOptions
): CurrencyFormatResult & { exceeded: boolean } {
  const caretPosition = Math.max(
    0,
    Math.min(options.caretPosition, options.text.length)
  );

  return ExpoInputMaskNative.applyNumberFormat({ ...options, caretPosition });
}
