import React, {
  useState,
  useRef,
  useCallback,
  useEffect,
  forwardRef,
} from 'react';
import {
  TextInput,
  NativeSyntheticEvent,
  TextInputSelectionChangeEventData,
} from 'react-native';
import { applyNumberFormat } from './ExpoInputMaskModule';
import type { NumberInputProps } from './ExpoInputMask.types';

export const NumberInput = forwardRef<TextInput, NumberInputProps>(
  (
    {
      locale,
      currency,
      groupingSeparator,
      decimalSeparator,
      decimalPlaces,
      min,
      max,
      onNumberResult,
      onChangeText,
      onSelectionChange,
      value,
      ...rest
    },
    ref
  ) => {
    const [formattedText, setFormattedText] = useState('');
    const selectionRef = useRef({ start: 0, end: 0 });
    const previousTextRef = useRef('');
    const lastRawValueRef = useRef('');
    const innerRef = useRef<TextInput>(null);

    const setRefs = useCallback(
      (instance: TextInput | null) => {
        innerRef.current = instance;
        if (typeof ref === 'function') {
          ref(instance);
        } else if (ref) {
          (ref as React.MutableRefObject<TextInput | null>).current = instance;
        }
      },
      [ref]
    );

    const runFormat = useCallback(
      (text: string, caretPos: number, gravity: 'forward' | 'backward') => {
        return applyNumberFormat({
          text,
          caretPosition: caretPos,
          caretGravity: gravity,
          locale,
          currency,
          groupingSeparator,
          decimalSeparator,
          decimalPlaces,
          min,
          max,
        });
      },
      [locale, currency, groupingSeparator, decimalSeparator, decimalPlaces, min, max]
    );

    // Handle external value prop changes (value = raw numeric string)
    useEffect(() => {
      if (value !== undefined && value !== lastRawValueRef.current) {
        const result = runFormat(value, value.length, 'forward');
        if (!result.exceeded) {
          setFormattedText(result.formattedText);
          previousTextRef.current = result.formattedText;
          lastRawValueRef.current = result.value;
        }
      }
    }, [value, runFormat]);

    const handleChangeText = useCallback(
      (text: string) => {
        const gravity: 'forward' | 'backward' =
          text.length < previousTextRef.current.length ? 'backward' : 'forward';
        const caretPos = Math.max(
          0,
          selectionRef.current.start +
            (text.length - previousTextRef.current.length)
        );

        const result = runFormat(text, caretPos, gravity);

        // If max was exceeded, revert to previous state
        if (result.exceeded) {
          setFormattedText(previousTextRef.current);
          return;
        }

        setFormattedText(result.formattedText);
        previousTextRef.current = result.formattedText;
        lastRawValueRef.current = result.value;

        onChangeText?.(result.value);
        onNumberResult?.({
          formattedText: result.formattedText,
          value: result.value ? parseFloat(result.value) : null,
          complete: result.complete,
        });
      },
      [runFormat, onChangeText, onNumberResult]
    );

    const handleSelectionChange = useCallback(
      (event: NativeSyntheticEvent<TextInputSelectionChangeEventData>) => {
        selectionRef.current = event.nativeEvent.selection;
        onSelectionChange?.(event);
      },
      [onSelectionChange]
    );

    return (
      <TextInput
        ref={setRefs}
        {...rest}
        keyboardType={rest.keyboardType ?? 'decimal-pad'}
        value={formattedText}
        onChangeText={handleChangeText}
        onSelectionChange={handleSelectionChange}
      />
    );
  }
);

NumberInput.displayName = 'NumberInput';
