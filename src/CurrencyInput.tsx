import { requireNativeViewManager } from 'expo-modules-core';
import React, { useCallback, useImperativeHandle, useRef } from 'react';
import type { ViewProps } from 'react-native';

import type {
  CurrencyInputProps,
  CurrencyValueResult,
  NumberInputRef,
} from './ExpoInputMask.types';

// Same native primitive as `<NumberInput />`. The currency-specific surface
// (required `currency`, optional `mode`) is enforced at the JS type layer;
// the event payload includes `minorUnits` (stripped by `<NumberInput />`).
interface NativeCurrencyInputProps extends ViewProps {
  placeholder?: string;
  editable?: boolean;
  textAlign?: string;
  keyboardType?: string;
  returnKeyType?: string;
  locale?: string;
  currency?: string;
  groupingSeparator?: string;
  decimalSeparator?: string;
  decimalPlaces?: number;
  mode?: 'decimal' | 'cents';
  min?: number;
  max?: number;
  value?: number | null;
  onValueChange?: (event: { nativeEvent: CurrencyValueResult }) => void;
  onFocusEvent?: () => void;
  onBlurEvent?: () => void;
}

interface NativeCurrencyInputHandle {
  focus?: () => void;
  blur?: () => void;
  clear?: () => void;
}

const NativeCurrencyInput =
  requireNativeViewManager<NativeCurrencyInputProps>('ExpoInputMask');

/**
 * Native currency input with live locale + currency formatting.
 *
 * Required `currency` (ISO-4217). `decimalPlaces` defaults to the currency's
 * default (USD = 2, JPY = 0, BHD = 3) unless overridden. Optional `mode`:
 * `'decimal'` (default) for free-form entry, `'cents'` for append-only digit
 * entry where the last `decimalPlaces` digits are always the fraction.
 *
 * Integer digits are capped at 15 to stay within JavaScript number precision.
 *
 * The `onValueChange` payload includes `minorUnits` — the value as an integer
 * in the smallest currency unit (cents for USD/EUR, ¥ for JPY, fils for BHD).
 * Computed natively by string concatenation, no floating-point error. Pass it
 * directly to payment APIs (Stripe, Adyen, ...) that take amounts in minor units.
 */

const NativeCurrencyInputWithRef = NativeCurrencyInput as React.ComponentType<
  NativeCurrencyInputProps & { ref?: React.Ref<NativeCurrencyInputHandle> }
>;

export const CurrencyInput = React.forwardRef<NumberInputRef, CurrencyInputProps>(
  ({ onChangeText, onValueChange, onFocus, onBlur, ...rest }, ref) => {
    const nativeRef = useRef<NativeCurrencyInputHandle | null>(null);

    useImperativeHandle(
      ref,
      () => ({
        focus: () => {
          nativeRef.current?.focus?.();
        },
        blur: () => {
          nativeRef.current?.blur?.();
        },
        clear: () => {
          nativeRef.current?.clear?.();
        },
      }),
      []
    );

    const callbacksRef = useRef({ onChangeText, onValueChange });
    callbacksRef.current = { onChangeText, onValueChange };

    const handleValueChange = useCallback(
      (event: { nativeEvent: CurrencyValueResult }) => {
        callbacksRef.current.onChangeText?.(event.nativeEvent.formattedText);
        callbacksRef.current.onValueChange?.(event.nativeEvent);
      },
      []
    );

    return (
      <NativeCurrencyInputWithRef
        ref={nativeRef}
        {...rest}
        onValueChange={handleValueChange}
        onFocusEvent={onFocus as (() => void) | undefined}
        onBlurEvent={onBlur as (() => void) | undefined}
      />
    );
  }
);

CurrencyInput.displayName = 'CurrencyInput';
