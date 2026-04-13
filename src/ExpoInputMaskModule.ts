import { requireNativeModule } from 'expo-modules-core';
import type { ApplyMaskOptions, MaskResult } from './ExpoInputMask.types';

const ExpoInputMaskNative = requireNativeModule('ExpoInputMask');

export function applyMask(options: ApplyMaskOptions): MaskResult {
  return ExpoInputMaskNative.applyMask(options);
}
