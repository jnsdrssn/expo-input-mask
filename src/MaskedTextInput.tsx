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
import { applyMask } from './ExpoInputMaskModule';
import type { MaskedTextInputProps } from './ExpoInputMask.types';

export const MaskedTextInput = forwardRef<TextInput, MaskedTextInputProps>(
  (
    {
      mask,
      affinityMasks,
      affinityStrategy,
      autocomplete = true,
      autoskip = false,
      customNotations,
      onMaskResult,
      onChangeText,
      onSelectionChange,
      value,
      ...rest
    },
    ref
  ) => {
    const [formattedText, setFormattedText] = useState('');
    const [selection, setSelection] = useState<
      { start: number; end: number } | undefined
    >();
    const selectionRef = useRef({ start: 0, end: 0 });
    const previousTextRef = useRef('');
    const lastExtractedRef = useRef('');
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

    const runMask = useCallback(
      (text: string, caretPos: number, gravity: 'forward' | 'backward') => {
        return applyMask({
          primaryFormat: mask,
          text,
          caretPosition: caretPos,
          caretGravity: gravity,
          autocomplete,
          autoskip,
          affinityFormats: affinityMasks,
          affinityStrategy,
          customNotations,
        });
      },
      [mask, autocomplete, autoskip, affinityMasks, affinityStrategy, customNotations]
    );

    // After setting selection, release control so the user can freely move the cursor.
    // Without this, the controlled selection prop fights with native cursor positioning.
    useEffect(() => {
      if (selection !== undefined) {
        const id = requestAnimationFrame(() => setSelection(undefined));
        return () => cancelAnimationFrame(id);
      }
    }, [selection]);

    // Handle external value prop changes (value = extracted/unmasked value)
    useEffect(() => {
      if (value !== undefined && value !== lastExtractedRef.current) {
        const result = runMask(value, value.length, 'forward');
        setFormattedText(result.formattedText);
        previousTextRef.current = result.formattedText;
        lastExtractedRef.current = value;
        setSelection({
          start: result.caretPosition,
          end: result.caretPosition,
        });
      }
    }, [value, runMask]);

    const handleChangeText = useCallback(
      (text: string) => {
        const gravity: 'forward' | 'backward' =
          text.length < previousTextRef.current.length ? 'backward' : 'forward';
        const caretPos = Math.max(
          0,
          selectionRef.current.start +
            (text.length - previousTextRef.current.length)
        );

        const result = runMask(text, caretPos, gravity);

        setFormattedText(result.formattedText);
        previousTextRef.current = result.formattedText;
        lastExtractedRef.current = result.extractedValue;

        // Briefly set selection to position cursor after mask formatting.
        // The useEffect above clears it on the next frame so native regains control.
        setSelection({
          start: result.caretPosition,
          end: result.caretPosition,
        });

        onChangeText?.(result.extractedValue);
        onMaskResult?.({
          formattedText: result.formattedText,
          extractedValue: result.extractedValue,
          complete: result.complete,
        });
      },
      [runMask, onChangeText, onMaskResult]
    );

    const handleSelectionChange = useCallback(
      (event: NativeSyntheticEvent<TextInputSelectionChangeEventData>) => {
        // Only track in ref — never update selection state from native events.
        // This prevents the cursor from fighting between React and native.
        selectionRef.current = event.nativeEvent.selection;
        onSelectionChange?.(event);
      },
      [onSelectionChange]
    );

    return (
      <TextInput
        ref={setRefs}
        {...rest}
        value={formattedText}
        selection={selection}
        onChangeText={handleChangeText}
        onSelectionChange={handleSelectionChange}
      />
    );
  }
);

MaskedTextInput.displayName = 'MaskedTextInput';
