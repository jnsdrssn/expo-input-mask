/**
 * JS-layer tests for the wrappers around the native primitive.
 *
 * The native algorithm itself is covered by the platform-native suites
 * (`android/src/test/.../NumberFormattingAlgorithmTest.kt` and
 * `ios/Tests/NumberFormattingAlgorithmTests.swift`, 55 tests each). This
 * file pins the JS wrappers' contract — specifically:
 *
 *  1. `applyNumberFormat()` strips `minorUnits` from the native return
 *     and `currency` / `mode` from the runtime options.
 *  2. `applyCurrencyFormat()` forwards `minorUnits` and `currency`.
 *  3. `<NumberInput />`'s `onValueChange` is invoked with a
 *     `NumberValueResult` shape — no `minorUnits`, even when the native
 *     event includes one.
 *  4. `<CurrencyInput />`'s `onValueChange` is invoked with the full
 *     `CurrencyValueResult` shape.
 *
 * A regression where someone forgets the destructure in either wrapper
 * would compile cleanly but leak the field at runtime; these tests catch
 * that. They were called out as a follow-up in the 0.3.0 code review.
 */

// Imports come first; `jest.mock(...)` below is hoisted by Jest above them at
// runtime. Importing from individual modules (not `../index`) bypasses
// `MaskedTextInput`'s react-native runtime imports — this test only covers
// the number/currency surface and runs under a node test environment.
import * as ExpoModulesCore from 'expo-modules-core';
import * as React from 'react';
import { act, create } from 'react-test-renderer';

import { CurrencyInput } from '../CurrencyInput';
import { applyCurrencyFormat, applyNumberFormat } from '../ExpoInputMaskModule';
import { NumberInput } from '../NumberInput';

// State lives inside the factory closure and is exposed through getters so
// individual tests can read/reset it.
jest.mock('expo-modules-core', () => {
  const ReactInner = jest.requireActual('react');
  let lastViewProps: Record<string, unknown> | null = null;
  const mockNativeApplyNumberFormat = jest.fn();
  const NativeViewMock = (
    props: Record<string, unknown>
  ): React.ReactElement => {
    lastViewProps = props;
    return ReactInner.createElement('NativeNumberInputStub', null);
  };
  return {
    requireNativeViewManager: () => NativeViewMock,
    requireNativeModule: () => ({
      applyMask: jest.fn(),
      applyNumberFormat: mockNativeApplyNumberFormat,
    }),
    __getLastViewProps: () => lastViewProps,
    __getMockNativeApplyNumberFormat: () => mockNativeApplyNumberFormat,
  };
});

const getLastViewProps = (
  ExpoModulesCore as unknown as {
    __getLastViewProps: () => Record<string, unknown> | null;
  }
).__getLastViewProps;
const mockNativeApplyNumberFormat = (
  ExpoModulesCore as unknown as {
    __getMockNativeApplyNumberFormat: () => jest.Mock;
  }
).__getMockNativeApplyNumberFormat();

describe('applyNumberFormat (JS layer)', () => {
  beforeEach(() => {
    mockNativeApplyNumberFormat.mockReset();
  });

  it('strips minorUnits from the native result', () => {
    mockNativeApplyNumberFormat.mockReturnValue({
      formattedText: '1,234',
      value: '1234',
      complete: true,
      caretPosition: 5,
      exceeded: false,
      minorUnits: 123400,
    });

    const result = applyNumberFormat({
      text: '1234',
      caretPosition: 4,
      locale: 'en-US',
    });

    expect(result).not.toHaveProperty('minorUnits');
    expect(result).toEqual({
      formattedText: '1,234',
      value: '1234',
      complete: true,
      caretPosition: 5,
      exceeded: false,
    });
  });

  it('defensively strips currency and mode from runtime options', () => {
    mockNativeApplyNumberFormat.mockReturnValue({
      formattedText: '1,234',
      value: '1234',
      complete: true,
      caretPosition: 5,
      exceeded: false,
      minorUnits: 123400,
    });

    applyNumberFormat({
      text: '1234',
      caretPosition: 4,
      // @ts-expect-error — testing the runtime escape hatch
      currency: 'USD',
      // @ts-expect-error — testing the runtime escape hatch
      mode: 'cents',
    });

    const callArg = mockNativeApplyNumberFormat.mock.calls[0][0];
    expect(callArg).not.toHaveProperty('currency');
    expect(callArg).not.toHaveProperty('mode');
  });

  it('passes locale + decimalPlaces through to the native call', () => {
    mockNativeApplyNumberFormat.mockReturnValue({
      formattedText: '1,234.5678',
      value: '1234.5678',
      complete: true,
      caretPosition: 10,
      exceeded: false,
      minorUnits: 12345678,
    });

    applyNumberFormat({
      text: '1234.5678',
      caretPosition: 9,
      locale: 'en-US',
      decimalPlaces: 4,
    });

    expect(mockNativeApplyNumberFormat).toHaveBeenCalledWith(
      expect.objectContaining({
        locale: 'en-US',
        decimalPlaces: 4,
      })
    );
  });

  it('forwards exceeded=true with empty fields when input is rejected', () => {
    mockNativeApplyNumberFormat.mockReturnValue({
      formattedText: '',
      value: '',
      complete: false,
      caretPosition: 0,
      exceeded: true,
      minorUnits: null,
    });

    const result = applyNumberFormat({
      text: '99999',
      caretPosition: 5,
      max: 100,
    });

    expect(result.exceeded).toBe(true);
    expect(result.formattedText).toBe('');
    expect(result.value).toBe('');
    expect(result).not.toHaveProperty('minorUnits');
  });
});

describe('applyCurrencyFormat (JS layer)', () => {
  beforeEach(() => {
    mockNativeApplyNumberFormat.mockReset();
  });

  it('preserves minorUnits in the result', () => {
    mockNativeApplyNumberFormat.mockReturnValue({
      formattedText: '$12.34',
      value: '12.34',
      complete: true,
      caretPosition: 6,
      exceeded: false,
      minorUnits: 1234,
    });

    const result = applyCurrencyFormat({
      text: '12.34',
      caretPosition: 5,
      currency: 'USD',
    });

    expect(result.minorUnits).toBe(1234);
    expect(result).toEqual({
      formattedText: '$12.34',
      value: '12.34',
      complete: true,
      caretPosition: 6,
      exceeded: false,
      minorUnits: 1234,
    });
  });

  it('forwards currency to the native function', () => {
    mockNativeApplyNumberFormat.mockReturnValue({
      formattedText: '',
      value: '',
      complete: true,
      caretPosition: 0,
      exceeded: false,
      minorUnits: null,
    });

    applyCurrencyFormat({
      text: '',
      caretPosition: 0,
      currency: 'USD',
    });

    expect(mockNativeApplyNumberFormat).toHaveBeenCalledWith(
      expect.objectContaining({ currency: 'USD' })
    );
  });
});

describe('<NumberInput /> JS wrapper', () => {
  it('calls onValueChange without minorUnits, even when native emits one', () => {
    const onValueChange = jest.fn();
    act(() => {
      create(<NumberInput onValueChange={onValueChange} />);
    });

    const viewProps = getLastViewProps();
    expect(viewProps).not.toBeNull();
    const handleValueChange = viewProps!.onValueChange as (event: {
      nativeEvent: Record<string, unknown>;
    }) => void;

    handleValueChange({
      nativeEvent: {
        formattedText: '1,234',
        value: 1234,
        rawValue: '1234',
        complete: true,
        minorUnits: 123400,
      },
    });

    expect(onValueChange).toHaveBeenCalledTimes(1);
    const passed = onValueChange.mock.calls[0][0];
    expect(passed).not.toHaveProperty('minorUnits');
    expect(passed).toEqual({
      formattedText: '1,234',
      value: 1234,
      rawValue: '1234',
      complete: true,
    });
  });

  it('calls onChangeText with the formatted text', () => {
    const onChangeText = jest.fn();
    act(() => {
      create(<NumberInput onChangeText={onChangeText} />);
    });

    const viewProps = getLastViewProps();
    const handleValueChange = viewProps!.onValueChange as (event: {
      nativeEvent: Record<string, unknown>;
    }) => void;

    handleValueChange({
      nativeEvent: {
        formattedText: '1,234',
        value: 1234,
        rawValue: '1234',
        complete: true,
        minorUnits: 123400,
      },
    });

    expect(onChangeText).toHaveBeenCalledWith('1,234');
  });
});

describe('<CurrencyInput /> JS wrapper', () => {
  it('calls onValueChange WITH minorUnits', () => {
    const onValueChange = jest.fn();
    act(() => {
      create(<CurrencyInput currency="USD" onValueChange={onValueChange} />);
    });

    const viewProps = getLastViewProps();
    expect(viewProps).not.toBeNull();
    const handleValueChange = viewProps!.onValueChange as (event: {
      nativeEvent: Record<string, unknown>;
    }) => void;

    handleValueChange({
      nativeEvent: {
        formattedText: '$12.34',
        value: 12.34,
        rawValue: '12.34',
        complete: true,
        minorUnits: 1234,
      },
    });

    expect(onValueChange).toHaveBeenCalledTimes(1);
    expect(onValueChange.mock.calls[0][0]).toEqual({
      formattedText: '$12.34',
      value: 12.34,
      rawValue: '12.34',
      complete: true,
      minorUnits: 1234,
    });
  });

  it('forwards currency through to the native view as a prop', () => {
    act(() => {
      create(<CurrencyInput currency="EUR" locale="de-DE" />);
    });

    const viewProps = getLastViewProps();
    expect(viewProps).toEqual(
      expect.objectContaining({ currency: 'EUR', locale: 'de-DE' })
    );
  });
});
