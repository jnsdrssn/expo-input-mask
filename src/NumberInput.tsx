import React, { useCallback, useImperativeHandle, useRef } from 'react';
import { requireNativeViewManager } from 'expo-modules-core';
import type {
  NumberInputProps,
  NumberInputRef,
  NumberValueResult,
} from './ExpoInputMask.types';
import type { ViewProps } from 'react-native';

interface NativeNumberInputProps extends ViewProps {
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
  onValueChange?: (event: { nativeEvent: NumberValueResult }) => void;
  onFocusEvent?: () => void;
  onBlurEvent?: () => void;
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

// requireNativeViewManager returns a ComponentType that accepts a ref at
// runtime (the ref receives a native view instance with any AsyncFunctions
// declared in the module attached as methods), but the type doesn't expose it.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const NativeNumberInputWithRef = NativeNumberInput as React.ComponentType<
  NativeNumberInputProps & { ref?: React.Ref<any> }
>;

export const NumberInput = React.forwardRef<NumberInputRef, NumberInputProps>(
  ({ onChangeText, onValueChange, onFocus, onBlur, ...rest }, ref) => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const nativeRef = useRef<any>(null);

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

    const handleValueChange = useCallback(
      (event: { nativeEvent: NumberValueResult }) => {
        onChangeText?.(event.nativeEvent.formattedText);
        onValueChange?.(event.nativeEvent);
      },
      [onChangeText, onValueChange]
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
