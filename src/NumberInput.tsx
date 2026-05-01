import { requireNativeViewManager } from 'expo-modules-core';
import React, { useCallback, useImperativeHandle, useRef } from 'react';
import type { ViewProps } from 'react-native';

import type {
  CurrencyValueResult,
  NumberInputProps,
  NumberInputRef,
  NumberValueResult,
} from './ExpoInputMask.types';

// The native view accepts the full prop surface (currency / mode / minorUnits
// in the event), but `<NumberInput />` only forwards the non-currency subset
// and strips `minorUnits` from the JS event before calling `onValueChange`.
// Use `<CurrencyInput />` for currency formatting.
interface NativeNumberInputProps extends ViewProps {
  placeholder?: string;
  editable?: boolean;
  textAlign?: string;
  keyboardType?: string;
  returnKeyType?: string;
  locale?: string;
  groupingSeparator?: string;
  decimalSeparator?: string;
  decimalPlaces?: number;
  min?: number;
  max?: number;
  value?: number | null;
  onValueChange?: (event: { nativeEvent: CurrencyValueResult }) => void;
  onFocusEvent?: () => void;
  onBlurEvent?: () => void;
}

// AsyncFunctions declared on the native view (`focus`, `blur`, `clear`) are
// attached to the view instance the ref receives, but `requireNativeViewManager`
// types its returned component without ref support. This is the minimal contract
// the wrapper relies on.
interface NativeNumberInputHandle {
  focus?: () => void;
  blur?: () => void;
  clear?: () => void;
}

const NativeNumberInput =
  requireNativeViewManager<NativeNumberInputProps>('ExpoInputMask');

/**
 * Native number input with live locale-aware formatting.
 *
 * Integer digits are capped at 15 to stay within JavaScript number precision
 * (2^53 is exact integer up to 16 digits but not for all values). Digits
 * typed or pasted past the cap are silently dropped. Note that this cap
 * applies before the `max` check, so a sufficiently large paste with leading
 * zeros can produce a post-cap value below `max` even if the original input
 * would have exceeded it.
 */

const NativeNumberInputWithRef = NativeNumberInput as React.ComponentType<
  NativeNumberInputProps & { ref?: React.Ref<NativeNumberInputHandle> }
>;

export const NumberInput = React.forwardRef<NumberInputRef, NumberInputProps>(
  ({ onChangeText, onValueChange, onFocus, onBlur, ...rest }, ref) => {
    const nativeRef = useRef<NativeNumberInputHandle | null>(null);

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

    // Stash the latest callbacks in a ref so `handleValueChange` is
    // permanently stable. If we depended on `[onChangeText, onValueChange]`
    // here, parents that inline their callbacks would rebuild the handler on
    // every render, forcing the native view to rebind the `onValueChange`
    // prop and burn cycles for no behavioral change.
    const callbacksRef = useRef({ onChangeText, onValueChange });
    callbacksRef.current = { onChangeText, onValueChange };

    const handleValueChange = useCallback(
      (event: { nativeEvent: CurrencyValueResult }) => {
        // Native always emits `minorUnits`; strip it for the non-currency surface.
        const { minorUnits: _minorUnits, ...numberValue } = event.nativeEvent;
        const result: NumberValueResult = numberValue;
        callbacksRef.current.onChangeText?.(result.formattedText);
        callbacksRef.current.onValueChange?.(result);
      },
      []
    );

    return (
      <NativeNumberInputWithRef
        ref={nativeRef}
        {...rest}
        onValueChange={handleValueChange}
        onFocusEvent={onFocus as (() => void) | undefined}
        onBlurEvent={onBlur as (() => void) | undefined}
      />
    );
  }
);

NumberInput.displayName = 'NumberInput';
