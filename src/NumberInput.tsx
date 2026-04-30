import { requireNativeViewManager } from 'expo-modules-core';
import React, { useCallback, useImperativeHandle, useRef } from 'react';
import type { ViewProps } from 'react-native';

import type {
  NumberInputProps,
  NumberInputRef,
  NumberValueResult,
} from './ExpoInputMask.types';

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
