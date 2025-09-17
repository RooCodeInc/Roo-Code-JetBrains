#!/bin/bash

# Default script to run IDE with Roo Code extension
# This ensures all files are in the right place for development

echo "🚀 Starting IDE with Roo Code extension..."

# Get the project root directory
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# Build Extension Host first (required for RPC communication)
echo "🔨 Building Extension Host..."
cd "$PROJECT_ROOT/extension_host"

# Check if node_modules exists, if not install dependencies
if [ ! -d "node_modules" ]; then
    echo "📦 Installing Extension Host dependencies..."
    npm install
fi

# Build the extension host
echo "🔧 Compiling Extension Host..."
npm run build:extension

# Check if build was successful
if [ ! -d "dist" ]; then
    echo "❌ Extension Host build failed!"
    exit 1
fi

echo "✅ Extension Host built successfully!"

# Now build and run the IDE plugin
cd "$PROJECT_ROOT/jetbrains_plugin"

# Build and run in release mode with Roo Code (default)
echo "📦 Building plugin with Roo Code..."
./gradlew clean buildPlugin -PdebugMode=release -PvscodePlugin=roo-code

echo "🏃 Starting IDE..."
./gradlew runIde -PdebugMode=release -PvscodePlugin=roo-code

echo "✅ Done!"