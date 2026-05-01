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

  // Defensive: strip `currency` and `mode` from runtime options too, not just
  // from the type. A consumer who casts past TS to pass `currency: 'USD'`
  // through `applyNumberFormat` should still get plain decimal output, not
  // silently currency-formatted text. Use `applyCurrencyFormat` for currency.
  const sanitized = options as ApplyNumberFormatOptions & {
    currency?: unknown;
    mode?: unknown;
  };
  const { currency: _c, mode: _m, ...nonCurrencyOptions } = sanitized;

  // Native always emits `minorUnits` (used by the currency surface). Strip it
  // here so the non-currency `NumberFormatResult` shape is honest at runtime.
  const result = ExpoInputMaskNative.applyNumberFormat({
    ...nonCurrencyOptions,
    caretPosition,
  });
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
