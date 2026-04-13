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
