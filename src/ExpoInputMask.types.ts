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
  caretGravity?: 'forward' | 'backward';
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
}

export interface NumberInputProps {
  // Text field props
  placeholder?: string;
  editable?: boolean;
  textAlign?: 'left' | 'center' | 'right';
  keyboardType?: 'default' | 'numeric' | 'decimal-pad' | 'number-pad';
  returnKeyType?: 'done' | 'go' | 'next' | 'search' | 'send' | 'default';

  // Number formatting
  locale?: string;
  currency?: string;
  groupingSeparator?: string;
  decimalSeparator?: string;
  decimalPlaces?: number;
  fixedDecimalPlaces?: boolean;

  // Constraints
  min?: number;
  max?: number;

  // Value (controlled mode)
  value?: string;

  // Callbacks
  onChangeText?: (value: string) => void;
  onNumberResult?: (result: {
    formattedText: string;
    value: number | null;
    complete: boolean;
  }) => void;
  onFocus?: () => void;
  onBlur?: () => void;

  // Layout
  style?: any;
}
