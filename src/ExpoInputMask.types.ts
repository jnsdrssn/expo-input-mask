import type { TextInputProps } from 'react-native';

export interface CustomNotation {
  character: string;
  characterSet: string;
  isOptional?: boolean;
}

export interface ApplyMaskOptions {
  primaryFormat: string;
  text: string;
  caretPosition: number;
  caretGravity?: 'forward' | 'backward';
  autocomplete?: boolean;
  autoskip?: boolean;
  affinityFormats?: string[];
  affinityStrategy?:
    | 'whole_string'
    | 'prefix'
    | 'capacity'
    | 'extracted_value_capacity';
  customNotations?: CustomNotation[];
}

export interface MaskResult {
  formattedText: string;
  extractedValue: string;
  complete: boolean;
  caretPosition: number;
  affinityOfPrimaryFormat?: number;
}

export interface MaskedTextInputProps extends TextInputProps {
  mask: string;
  affinityMasks?: string[];
  affinityStrategy?:
    | 'whole_string'
    | 'prefix'
    | 'capacity'
    | 'extracted_value_capacity';
  autocomplete?: boolean;
  autoskip?: boolean;
  customNotations?: CustomNotation[];
  onMaskResult?: (result: {
    formattedText: string;
    extractedValue: string;
    complete: boolean;
  }) => void;
}

export interface ApplyNumberFormatOptions {
  text: string;
  caretPosition: number;
  locale?: string;
  currency?: string;
  groupingSeparator?: string;
  decimalSeparator?: string;
  decimalPlaces?: number;
  fixedDecimalPlaces?: boolean;
  min?: number;
  max?: number;
}

export interface NumberFormatResult {
  formattedText: string;
  value: string;
  complete: boolean;
  caretPosition: number;
  /**
   * Value expressed as an integer in the smallest unit (cents for USD/EUR,
   * ¥ for JPY, fils for BHD). Computed natively by string concatenation —
   * exact, no floating-point. `null` when `value` is empty.
   */
  minorUnits: number | null;
}

export interface NumberValueResult {
  value: number | null;
  formattedText: string;
  rawValue: string;
  /**
   * Value expressed as an integer in the smallest unit (cents for USD/EUR,
   * ¥ for JPY, fils for BHD). Computed natively by string concatenation —
   * exact, no floating-point. `null` when the field is empty. Useful for
   * payment APIs (Stripe, Adyen, ...) that take amounts in minor units.
   */
  minorUnits: number | null;
  complete: boolean;
}

export interface NumberInputProps extends Omit<
  TextInputProps,
  'value' | 'onChangeText' | 'onChange' | 'keyboardType'
> {
  // Number formatting
  locale?: string;
  currency?: string;
  groupingSeparator?: string;
  decimalSeparator?: string;
  /** Max fractional digits. Defaults to the currency's default if `currency` is set, otherwise 2. */
  decimalPlaces?: number;
  /**
   * `'decimal'` (default): user types digits and a decimal separator; display matches input.
   * `'cents'`: append-only digit mode — the last `decimalPlaces` digits are always the fraction
   * (typing `123` with `decimalPlaces: 2` → `1.23`). The decimal separator is ignored on input.
   */
  mode?: 'decimal' | 'cents';

  // Constraints
  min?: number;
  max?: number;

  /**
   * Controlled value. Pass `null` to clear; passing `undefined` (or omitting
   * the prop) leaves whatever the field currently shows untouched. Updates
   * while the field is focused are ignored to avoid races with active typing.
   */
  value?: number | null;

  /**
   * Fires with the **display-formatted** text on every change (e.g. `"1,234.56"`,
   * `"1.234,56 €"`). Matches the `<TextInput />` `onChangeText` convention.
   * For the dot-canonical raw string or the parsed `number`, use `onValueChange`.
   */
  onChangeText?: (formattedText: string) => void;
  /** Fires on every change with parsed number, formatted text, dot-canonical raw string, and min/max completeness. */
  onValueChange?: (result: NumberValueResult) => void;

  /** Narrowed for numeric input. Other `TextInput` keyboard types don't make sense here. */
  keyboardType?: 'decimal-pad' | 'numeric' | 'number-pad';
}

export interface NumberInputRef {
  focus: () => void;
  blur: () => void;
  clear: () => void;
}
