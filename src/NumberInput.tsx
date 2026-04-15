import React, { useCallback } from 'react';
import { requireNativeViewManager } from 'expo-modules-core';
import type { NumberInputProps } from './ExpoInputMask.types';
import type { View, ViewProps } from 'react-native';

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
  fixedDecimalPlaces?: boolean;
  min?: number;
  max?: number;
  value?: string;
  onChangeText?: (event: { nativeEvent: { text: string } }) => void;
  onNumberResult?: (event: {
    nativeEvent: { formattedText: string; value: number | null; complete: boolean };
  }) => void;
  onFocusEvent?: () => void;
  onBlurEvent?: () => void;
}

const NativeNumberInput =
  requireNativeViewManager<NativeNumberInputProps>('ExpoInputMask');

// Cast to allow ref forwarding; requireNativeViewManager returns ComponentType
// which accepts a ref at runtime even though the type doesn't declare it.
const NativeNumberInputWithRef = NativeNumberInput as React.ComponentType<
  NativeNumberInputProps & { ref?: React.Ref<View> }
>;

export const NumberInput = React.forwardRef<View, NumberInputProps>(
  (
    {
      onChangeText,
      onNumberResult,
      onFocus,
      onBlur,
      ...rest
    },
    ref
  ) => {
    const handleChangeText = useCallback(
      (event: { nativeEvent: { text: string } }) => {
        onChangeText?.(event.nativeEvent.text);
      },
      [onChangeText]
    );

    const handleNumberResult = useCallback(
      (event: {
        nativeEvent: {
          formattedText: string;
          value: number | null;
          complete: boolean;
        };
      }) => {
        onNumberResult?.(event.nativeEvent);
      },
      [onNumberResult]
    );

    return (
      <NativeNumberInputWithRef
        ref={ref}
        {...rest}
        onChangeText={handleChangeText}
        onNumberResult={handleNumberResult}
        onFocusEvent={onFocus}
        onBlurEvent={onBlur}
      />
    );
  }
);

NumberInput.displayName = 'NumberInput';
