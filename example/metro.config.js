const { getDefaultConfig } = require('expo/metro-config');
const path = require('path');

const projectRoot = __dirname;
const monorepoRoot = path.resolve(projectRoot, '..');

const config = getDefaultConfig(projectRoot);

// Only watch the parent's src/ directory (not root node_modules which has
// react-native@0.85.0 that uses unsupported match syntax)
config.watchFolders = [path.resolve(monorepoRoot, 'src')];

// Only resolve from the example's own node_modules
config.resolver.nodeModulesPaths = [
  path.resolve(projectRoot, 'node_modules'),
];

// Map the library's bare import to the parent src directory
config.resolver.extraNodeModules = {
  'expo-input-mask': path.resolve(monorepoRoot, 'src'),
};

module.exports = config;
