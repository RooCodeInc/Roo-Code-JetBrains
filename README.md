# Roo Code for JetBrains

Run Roo Code AI Assistant in JetBrains IDEs.

## Prerequisites

- Node.js 16+ and npm
- JDK 17+
- Git
- curl or wget
- unzip

## Build from Source

### Quick Build

```bash
# Clone the repository
git clone https://github.com/RooCodeInc/Roo-Code-JetBrains.git
cd Roo-Code-JetBrains

# Run setup (initializes submodules, installs dependencies, applies patches)
./scripts/setup.sh

# Build the plugin
./scripts/build.sh
```

The setup script handles:
- Git LFS initialization
- Git submodule initialization and updates
- Dependency installation
- Patch application
- Development environment setup

The build script then:
- Downloads the latest Roo Code extension from VSCode marketplace
- Builds the Extension Host runtime
- Creates the JetBrains plugin with Roo Code integrated
- Outputs a ready-to-install plugin in the `dist/` directory

### Build Options

```bash
# Release build (default)
./scripts/build.sh

# Debug build with source maps
./scripts/build.sh --mode debug

# Custom output directory
./scripts/build.sh --output ./my-build

# Clean build
./scripts/build.sh --clean

# Skip specific components
./scripts/build.sh --skip-vscode    # Skip VSCode extension build
./scripts/build.sh --skip-host      # Skip Extension Host build
./scripts/build.sh --skip-idea      # Skip IDEA plugin build

# Verbose output
./scripts/build.sh --verbose

# See all options
./scripts/build.sh --help
```

### Build Output

After building, you'll find:
- `dist/RooCode-*.zip` - The JetBrains plugin file
- `dist/extension_host/` - The Extension Host runtime
- `dist/debug-resources/` - Debug resources (debug mode only)
- `dist/README.md` - Installation instructions

## Manual Build Steps

If you prefer to build components individually:

### 1. Download and Prepare Roo Code Extension

```bash
# The build script automatically downloads the latest version
# Manual placement: extract VSIX to jetbrains_plugin/plugins/roo-code/extension/
```

### 2. Build Extension Host

```bash
cd extension_host
npm install
npm run build:extension  # Production build
# or
npm run build           # Development build
```

### 3. Build IDEA Plugin

```bash
cd jetbrains_plugin

# Production build with Roo Code
./gradlew -PdebugMode=release -PvscodePlugin=roo-code buildPlugin

# Debug build
./gradlew -PdebugMode=idea -PvscodePlugin=roo-code buildPlugin
```

## Development

### Development Setup

```bash
# Clone and setup
git clone https://github.com/RooCodeInc/Roo-Code-JetBrains.git
cd Roo-Code-JetBrains

# Run setup (handles submodules, dependencies, patches)
./scripts/setup.sh

# Run Extension Host in development mode
cd extension_host
npm run dev
```

### Running the Plugin in Development

```bash
cd jetbrains_plugin
./gradlew runIde
```

## Installation

### Install from Built Plugin

1. Build the plugin using the instructions above
2. Open your JetBrains IDE
3. Go to Settings/Preferences → Plugins
4. Click the gear icon → Install Plugin from Disk...
5. Select the `.zip` file from `dist/` directory
6. Restart your IDE

## Troubleshooting

### Build Fails

```bash
# Re-run setup to ensure everything is initialized
./scripts/setup.sh --force

# Check prerequisites
node --version  # Should be 16+
java --version  # Should be 17+

# Make scripts executable
chmod +x scripts/*.sh
chmod +x scripts/lib/*.sh

# Clean build
./scripts/build.sh --clean
```

### Permission Issues

```bash
# Make gradlew executable
cd jetbrains_plugin
chmod +x gradlew
```

### Network Errors Downloading Roo Code

```bash
# Skip download and manually place extension
./scripts/build.sh --skip-download
# Download VSIX manually from: https://marketplace.visualstudio.com/items?itemName=RooVeterinaryInc.roo-cline
# Extract to: jetbrains_plugin/plugins/roo-code/extension/
```

## Architecture

The plugin consists of three components:

1. **JetBrains Plugin** (Kotlin) - IDE integration
2. **Extension Host** (Node.js) - Runs the Roo Code extension
3. **Roo Code Extension** - The AI assistant

Communication uses RPC over Unix sockets (macOS/Linux) or named pipes (Windows).

## Environment Variables

For CI/CD or automated builds:

```bash
export BUILD_MODE=release
export VSCODE_PLUGIN_NAME=roo-code
export SKIP_VSCODE_BUILD=true
export SKIP_IDEA_BUILD=false
./scripts/build.sh
```

## License

Apache License 2.0
