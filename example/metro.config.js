const { getDefaultConfig } = require('expo/metro-config');
const path = require('path');

const projectRoot = __dirname;
const monorepoRoot = path.resolve(projectRoot, '..');

const config = getDefaultConfig(projectRoot);

// Watch the parent directory for expo-input-mask source changes
config.watchFolders = [monorepoRoot];

// Resolve modules from example's node_modules first, then root's
config.resolver.nodeModulesPaths = [
  path.resolve(projectRoot, 'node_modules'),
  path.resolve(monorepoRoot, 'node_modules'),
];

// Force core packages to resolve from example's node_modules
// (root has different versions as transitive deps of expo devDependency)
config.resolver.extraNodeModules = {
  'react': path.resolve(projectRoot, 'node_modules/react'),
  'react-native': path.resolve(projectRoot, 'node_modules/react-native'),
  'expo': path.resolve(projectRoot, 'node_modules/expo'),
  'expo-modules-core': path.resolve(projectRoot, 'node_modules/expo-modules-core'),
};

module.exports = config;
