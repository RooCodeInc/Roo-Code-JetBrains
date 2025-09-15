#!/bin/bash

# Build script with pre-packaged Roo Code extension
# This script downloads, integrates, and builds the project with Roo Code pre-installed

set -euo pipefail

# Source common utilities
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"
source "$SCRIPT_DIR/lib/build.sh"

# Script configuration
readonly SCRIPT_NAME="build-with-roo.sh"
readonly SCRIPT_VERSION="1.0.0"
readonly ROO_EXTENSION_URL="https://marketplace.visualstudio.com/_apis/public/gallery/publishers/RooVeterinaryInc/vsextensions/roo-cline/3.25.6/vspackage"
readonly ROO_EXTENSION_VERSION="3.25.6"
readonly ROO_EXTENSION_NAME="roo-cline"

# Build configuration
BUILD_MODE="${BUILD_MODE:-release}"
DOWNLOAD_DIR=""
EXTENSION_OUTPUT_DIR=""
SKIP_DOWNLOAD=false
SKIP_VSCODE_BUILD=false
SKIP_EXTENSION_HOST_BUILD=false
SKIP_IDEA_BUILD=false
CLEAN_BUILD=false

# Show help for this script
show_help() {
    cat << EOF
$SCRIPT_NAME - Build RunVSAgent with pre-packaged Roo Code extension

USAGE:
    $SCRIPT_NAME [OPTIONS]

DESCRIPTION:
    This script automates the entire build process with Roo Code extension
    pre-packaged and ready to use. It:
    - Downloads the Roo Code extension from VSCode marketplace
    - Extracts and integrates it into the project
    - Builds all components (VSCode, Extension Host, IDEA plugin)
    - Outputs a complete package with Roo Code pre-installed

OPTIONS:
    -m, --mode MODE         Build mode: release (default) or debug
    -o, --output DIR        Output directory for final build
    -c, --clean             Clean build (remove all artifacts before building)
    --skip-download         Skip downloading Roo Code (use existing)
    --skip-vscode           Skip VSCode extension build
    --skip-host             Skip Extension Host build
    --skip-idea             Skip IDEA plugin build
    -v, --verbose           Enable verbose output
    -n, --dry-run           Show what would be done without executing
    -h, --help              Show this help message

EXAMPLES:
    $SCRIPT_NAME                        # Full build with Roo Code
    $SCRIPT_NAME --mode debug           # Debug build
    $SCRIPT_NAME --output ./dist        # Custom output directory
    $SCRIPT_NAME --clean                # Clean build from scratch

OUTPUT:
    The script creates a complete build in the output directory with:
    - IDEA plugin (.zip) with Roo Code pre-integrated
    - Extension Host runtime
    - Debug resources (if debug mode)
    - Ready-to-use configuration

REQUIREMENTS:
    - Node.js 16+ and npm
    - JDK 17+ (for IDEA plugin)
    - Git with submodules initialized
    - Internet connection (for downloading Roo Code)
    - curl or wget for downloading

EOF
}

# Parse command line arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -m|--mode)
                if [[ -z "${2:-}" ]]; then
                    log_error "Build mode requires a value"
                    exit 3
                fi
                BUILD_MODE="$2"
                shift 2
                ;;
            -o|--output)
                if [[ -z "${2:-}" ]]; then
                    log_error "Output directory requires a value"
                    exit 3
                fi
                EXTENSION_OUTPUT_DIR="$2"
                shift 2
                ;;
            -c|--clean)
                CLEAN_BUILD=true
                shift
                ;;
            --skip-download)
                SKIP_DOWNLOAD=true
                shift
                ;;
            --skip-vscode)
                SKIP_VSCODE_BUILD=true
                shift
                ;;
            --skip-host)
                SKIP_EXTENSION_HOST_BUILD=true
                shift
                ;;
            --skip-idea)
                SKIP_IDEA_BUILD=true
                shift
                ;;
            -v|--verbose)
                VERBOSE=true
                shift
                ;;
            -n|--dry-run)
                DRY_RUN=true
                shift
                ;;
            -h|--help)
                show_help
                exit 0
                ;;
            -*)
                log_error "Unknown option: $1"
                log_info "Use --help for usage information"
                exit 3
                ;;
            *)
                log_error "Unexpected argument: $1"
                log_info "Use --help for usage information"
                exit 3
                ;;
        esac
    done
    
    # Validate build mode
    if [[ "$BUILD_MODE" != "release" && "$BUILD_MODE" != "debug" ]]; then
        log_error "Invalid build mode: $BUILD_MODE"
        log_info "Valid modes: release, debug"
        exit 3
    fi
}

# Initialize build environment
init_roo_build_env() {
    log_step "Initializing Roo Code build environment..."
    
    # Set up directories
    DOWNLOAD_DIR="$PROJECT_ROOT/.roo-build"
    if [[ -z "$EXTENSION_OUTPUT_DIR" ]]; then
        EXTENSION_OUTPUT_DIR="$PROJECT_ROOT/dist-with-roo"
    fi
    
    # Make output directory absolute
    EXTENSION_OUTPUT_DIR="$(cd "$PROJECT_ROOT" && mkdir -p "$EXTENSION_OUTPUT_DIR" && cd "$EXTENSION_OUTPUT_DIR" && pwd)"
    
    # Ensure directories exist
    ensure_dir "$DOWNLOAD_DIR"
    ensure_dir "$EXTENSION_OUTPUT_DIR"
    
    # Initialize base build environment
    init_build_env
    
    log_success "Build environment initialized"
    log_info "Download directory: $DOWNLOAD_DIR"
    log_info "Output directory: $EXTENSION_OUTPUT_DIR"
}

# Check required tools
check_requirements() {
    log_step "Checking requirements..."
    
    # Check Node.js
    if ! command_exists "node"; then
        log_error "Node.js is required but not installed"
        exit 4
    fi
    
    # Check npm
    if ! command_exists "npm"; then
        log_error "npm is required but not installed"
        exit 4
    fi
    
    # Check JDK for IDEA plugin
    if [[ "$SKIP_IDEA_BUILD" != "true" ]]; then
        if ! command_exists "java"; then
            log_error "Java is required for IDEA plugin build but not installed"
            exit 4
        fi
    fi
    
    # Check download tool
    if ! command_exists "curl" && ! command_exists "wget"; then
        log_error "Either curl or wget is required for downloading"
        exit 4
    fi
    
    # Check unzip
    if ! command_exists "unzip"; then
        log_error "unzip is required but not installed"
        exit 4
    fi
    
    # Check git submodules
    if [[ ! -d "$PROJECT_ROOT/deps/vscode" ]] || [[ ! "$(ls -A "$PROJECT_ROOT/deps/vscode" 2>/dev/null)" ]]; then
        log_error "VSCode submodule not initialized. Run './scripts/setup.sh' first."
        exit 4
    fi
    
    if [[ ! -d "$PROJECT_ROOT/deps/roo-code" ]] || [[ ! "$(ls -A "$PROJECT_ROOT/deps/roo-code" 2>/dev/null)" ]]; then
        log_warn "Roo-Code submodule not initialized. Will use marketplace version."
    fi
    
    log_success "All requirements met"
}

# Clean build artifacts
clean_build_artifacts() {
    if [[ "$CLEAN_BUILD" != "true" ]]; then
        return 0
    fi
    
    log_step "Cleaning build artifacts..."
    
    # Clean download directory
    if [[ -d "$DOWNLOAD_DIR" ]]; then
        remove_dir "$DOWNLOAD_DIR"
        ensure_dir "$DOWNLOAD_DIR"
    fi
    
    # Clean output directory
    if [[ -d "$EXTENSION_OUTPUT_DIR" ]]; then
        remove_dir "$EXTENSION_OUTPUT_DIR"
        ensure_dir "$EXTENSION_OUTPUT_DIR"
    fi
    
    # Clean standard build artifacts
    clean_build
    
    log_success "Build artifacts cleaned"
}

# Download Roo Code extension
download_roo_extension() {
    if [[ "$SKIP_DOWNLOAD" == "true" ]]; then
        log_info "Skipping Roo Code download"
        return 0
    fi
    
    log_step "Downloading Roo Code extension..."
    
    local vsix_file="$DOWNLOAD_DIR/${ROO_EXTENSION_NAME}-${ROO_EXTENSION_VERSION}.vsix"
    
    # Check if already downloaded
    if [[ -f "$vsix_file" ]]; then
        log_info "Roo Code extension already downloaded"
        return 0
    fi
    
    # Download using curl or wget
    if command_exists "curl"; then
        log_info "Downloading from: $ROO_EXTENSION_URL"
        execute_cmd "curl -L -o '$vsix_file' '$ROO_EXTENSION_URL'" "download Roo Code extension"
    elif command_exists "wget"; then
        log_info "Downloading from: $ROO_EXTENSION_URL"
        execute_cmd "wget -O '$vsix_file' '$ROO_EXTENSION_URL'" "download Roo Code extension"
    else
        log_error "No download tool available (curl or wget)"
        exit 4
    fi
    
    # Verify download
    if [[ ! -f "$vsix_file" ]]; then
        log_error "Failed to download Roo Code extension"
        exit 2
    fi
    
    log_success "Roo Code extension downloaded: $vsix_file"
}

# Extract and prepare Roo Code extension
prepare_roo_extension() {
    log_step "Preparing Roo Code extension..."
    
    local vsix_file="$DOWNLOAD_DIR/${ROO_EXTENSION_NAME}-${ROO_EXTENSION_VERSION}.vsix"
    local extract_dir="$DOWNLOAD_DIR/roo-code-extracted"
    # IMPORTANT: The prepareSandbox task expects files in plugins/roo-code/extension
    local target_dir="$IDEA_BUILD_DIR/plugins/roo-code/extension"
    
    # Clean extraction directory
    remove_dir "$extract_dir"
    ensure_dir "$extract_dir"
    
    # Check if file is gzipped (marketplace sometimes returns gzipped files)
    local file_type=$(file -b "$vsix_file")
    if [[ "$file_type" == *"gzip"* ]]; then
        log_info "Decompressing gzipped VSIX file..."
        local uncompressed_file="${vsix_file}.uncompressed"
        execute_cmd "gunzip -c '$vsix_file' > '$uncompressed_file'" "decompress VSIX"
        vsix_file="$uncompressed_file"
    fi
    
    # Extract VSIX
    log_info "Extracting Roo Code extension..."
    execute_cmd "unzip -q '$vsix_file' -d '$extract_dir'" "extract Roo Code extension"
    
    # Prepare target directory (note: we clear the parent, then create extension subdir)
    remove_dir "$IDEA_BUILD_DIR/plugins/roo-code"
    ensure_dir "$target_dir"
    
    # Copy extension files
    if [[ -d "$extract_dir/extension" ]]; then
        log_info "Copying Roo Code extension files..."
        cp -r "$extract_dir/extension"/* "$target_dir/" 2>/dev/null || {
            # Fallback if glob fails
            cp -r "$extract_dir/extension/." "$target_dir/"
        }
        log_success "Roo Code extension files copied"
    else
        log_error "Extension directory not found in VSIX"
        exit 2
    fi
    
    # Modify package.json if needed for compatibility
    local package_json="$target_dir/package.json"
    if [[ -f "$package_json" ]]; then
        log_info "Adjusting package.json for compatibility..."
        if [[ "$DRY_RUN" != "true" ]]; then
            # Remove type field for CommonJS compatibility if needed
            node -e "
                const fs = require('fs');
                const pkgPath = process.argv[1];
                const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf8'));
                // Ensure compatibility settings
                if (pkg.type === 'module') {
                    delete pkg.type;
                }
                // Add activation events if missing
                if (!pkg.activationEvents || pkg.activationEvents.length === 0) {
                    pkg.activationEvents = ['onStartupFinished'];
                }
                fs.writeFileSync(pkgPath, JSON.stringify(pkg, null, 2));
                console.log('Adjusted package.json for compatibility');
            " "$package_json" || log_warn "Failed to adjust package.json"
        fi
    fi
    
    log_success "Roo Code extension prepared at: $target_dir"
}

# Build VSCode extension (if using submodule)
build_vscode_if_needed() {
    if [[ "$SKIP_VSCODE_BUILD" == "true" ]]; then
        log_info "Skipping VSCode extension build"
        return 0
    fi
    
    # Check if we should build from Roo-Code submodule
    if [[ -d "$PROJECT_ROOT/deps/roo-code" ]] && [[ "$(ls -A "$PROJECT_ROOT/deps/roo-code" 2>/dev/null)" ]]; then
        log_step "Building Roo Code from submodule..."
        
        cd "$PROJECT_ROOT/deps/roo-code"
        
        # Install dependencies
        local pkg_manager="npm"
        if command_exists "pnpm" && [[ -f "pnpm-lock.yaml" ]]; then
            pkg_manager="pnpm"
        fi
        
        log_info "Installing dependencies with $pkg_manager..."
        execute_cmd "$pkg_manager install" "dependency installation"
        
        # Build extension
        if [[ "$BUILD_MODE" == "debug" ]]; then
            log_info "Building in debug mode..."
            execute_cmd "$pkg_manager run build:dev" "Roo Code build (debug)" || \
                execute_cmd "$pkg_manager run build" "Roo Code build (debug)"
        else
            log_info "Building in release mode..."
            execute_cmd "$pkg_manager run build" "Roo Code build (release)"
        fi
        
        # Package as VSIX if vsce is available
        if command_exists "vsce" || npx vsce --help >/dev/null 2>&1; then
            log_info "Creating VSIX package..."
            # Change to src directory where the extension package.json is
            cd "$PROJECT_ROOT/deps/roo-code/src"
            # Try to package, but don't fail if it doesn't work
            if npx vsce package --no-dependencies --out ../bin/roo-code-custom.vsix >/dev/null 2>&1; then
                log_success "VSIX package created: roo-code-custom.vsix"
                # Use this custom built VSIX instead of the downloaded one
                local custom_vsix="$PROJECT_ROOT/deps/roo-code/bin/roo-code-custom.vsix"
                if [[ -f "$custom_vsix" ]]; then
                    log_info "Using custom-built VSIX for integration"
                    # Copy to download directory for consistency
                    cp "$custom_vsix" "$DOWNLOAD_DIR/roo-code-custom.vsix"
                fi
            else
                log_warn "VSIX packaging failed, continuing with built files from source"
            fi
            cd "$PROJECT_ROOT/deps/roo-code"
        fi
        
        log_success "Roo Code built from submodule"
    else
        log_info "Using downloaded Roo Code extension"
    fi
}

# Build Extension Host
build_extension_host_component() {
    if [[ "$SKIP_EXTENSION_HOST_BUILD" == "true" ]]; then
        log_info "Skipping Extension Host build"
        return 0
    fi
    
    log_step "Building Extension Host..."
    
    cd "$PROJECT_ROOT/extension_host"
    
    # Install dependencies
    log_info "Installing Extension Host dependencies..."
    execute_cmd "npm install" "Extension Host dependency installation"
    
    # Build
    if [[ "$BUILD_MODE" == "debug" ]]; then
        execute_cmd "npm run build" "Extension Host build (debug)"
    else
        execute_cmd "npm run build:extension" "Extension Host build (release)"
    fi
    
    # Copy to output
    local host_output="$EXTENSION_OUTPUT_DIR/extension_host"
    ensure_dir "$host_output"
    
    copy_files "$PROJECT_ROOT/extension_host/dist" "$host_output/" "Extension Host dist"
    copy_files "$PROJECT_ROOT/extension_host/package.json" "$host_output/" "Extension Host package.json"
    
    # Copy node_modules for runtime
    if [[ -d "$PROJECT_ROOT/extension_host/node_modules" ]]; then
        log_info "Copying Extension Host dependencies..."
        copy_files "$PROJECT_ROOT/extension_host/node_modules" "$host_output/" "Extension Host node_modules"
    fi
    
    log_success "Extension Host built"
}

# Build IDEA plugin with Roo Code
build_idea_with_roo() {
    if [[ "$SKIP_IDEA_BUILD" == "true" ]]; then
        log_info "Skipping IDEA plugin build"
        return 0
    fi
    
    log_step "Building IDEA plugin with Roo Code..."
    
    cd "$IDEA_BUILD_DIR"
    
    # Ensure Roo Code is in place
    if [[ ! -d "$IDEA_BUILD_DIR/plugins/roo-code/extension" ]]; then
        log_error "Roo Code extension not found in plugins/roo-code/extension directory"
        exit 2
    fi
    
    # Use gradlew if available
    local gradle_cmd="gradle"
    if [[ -f "./gradlew" ]]; then
        gradle_cmd="./gradlew"
        chmod +x "./gradlew"
    fi
    
    # Set build mode
    local debug_mode="release"
    if [[ "$BUILD_MODE" == "debug" ]]; then
        debug_mode="idea"
    fi
    
    # Build plugin
    log_info "Building IDEA plugin in $BUILD_MODE mode..."
    execute_cmd "$gradle_cmd -PdebugMode=$debug_mode -PvscodePlugin=roo-code buildPlugin --info" "IDEA plugin build"
    
    # Find generated plugin
    local plugin_file
    plugin_file=$(find "$IDEA_BUILD_DIR/build/distributions" \( -name "*.zip" -o -name "*.jar" \) -type f | sort -r | head -n 1)
    
    if [[ -z "$plugin_file" ]]; then
        log_error "IDEA plugin build failed - no output file found"
        exit 2
    fi
    
    # Copy to output directory
    copy_files "$plugin_file" "$EXTENSION_OUTPUT_DIR/" "IDEA plugin"
    
    log_success "IDEA plugin built with Roo Code: $(basename "$plugin_file")"
}

# Copy debug resources if needed
copy_debug_resources_with_roo() {
    if [[ "$BUILD_MODE" != "debug" ]]; then
        return 0
    fi
    
    log_step "Copying debug resources..."
    
    local debug_dir="$EXTENSION_OUTPUT_DIR/debug-resources"
    ensure_dir "$debug_dir"
    
    # Copy Roo Code debug resources
    local roo_debug="$debug_dir/roo-code"
    ensure_dir "$roo_debug"
    
    if [[ -d "$IDEA_BUILD_DIR/plugins/roo-code" ]]; then
        copy_files "$IDEA_BUILD_DIR/plugins/roo-code/*" "$roo_debug/" "Roo Code debug resources"
    fi
    
    # Copy Extension Host debug resources
    if [[ -d "$PROJECT_ROOT/extension_host/dist" ]]; then
        local host_debug="$debug_dir/extension_host"
        ensure_dir "$host_debug"
        copy_files "$PROJECT_ROOT/extension_host/dist/*" "$host_debug/" "Extension Host debug resources"
    fi
    
    log_success "Debug resources copied"
}

# Create installation instructions
create_installation_instructions() {
    log_step "Creating installation instructions..."
    
    local readme_file="$EXTENSION_OUTPUT_DIR/README.md"
    
    cat > "$readme_file" << EOF
# RunVSAgent with Roo Code - Build Output

This directory contains the complete build of RunVSAgent with Roo Code pre-packaged.

## Build Information
- Build Date: $(date)
- Build Mode: $BUILD_MODE
- Roo Code Version: $ROO_EXTENSION_VERSION

## Contents

### IDEA Plugin
The IDEA plugin file (\`*.zip\` or \`*.jar\`) includes:
- Complete RunVSAgent integration
- Pre-packaged Roo Code extension
- Extension Host runtime
- All required dependencies

### Extension Host
The \`extension_host/\` directory contains:
- Compiled Extension Host runtime
- Node.js dependencies
- Configuration files

EOF

    if [[ "$BUILD_MODE" == "debug" ]]; then
        cat >> "$readme_file" << EOF

### Debug Resources
The \`debug-resources/\` directory contains:
- Uncompressed extension files for debugging
- Source maps
- Development configurations

EOF
    fi

    cat >> "$readme_file" << EOF

## Installation Instructions

### Installing the IDEA Plugin

1. Open your JetBrains IDE (IntelliJ IDEA, WebStorm, etc.)
2. Go to **Settings/Preferences** → **Plugins**
3. Click the gear icon ⚙️ → **Install Plugin from Disk...**
4. Select the plugin file from this directory
5. Restart the IDE when prompted

### Verifying Installation

After installation:
1. Look for the RunVSAgent toolbar in your IDE
2. Click on the Roo Code icon to open the assistant
3. The extension should be ready to use immediately

### Configuration

The Roo Code extension is pre-configured and ready to use. You can customize settings through:
- IDE Settings → RunVSAgent → Extensions
- The extension's own settings panel

## Troubleshooting

If you encounter issues:

1. **Extension not loading**: 
   - Ensure the IDE was restarted after installation
   - Check the IDE logs for error messages

2. **Missing features**:
   - Verify that all files were extracted properly
   - Check that Node.js is installed on your system

3. **Performance issues**:
   - Try the release build if using debug
   - Ensure sufficient memory is allocated to the IDE

## Support

For issues or questions:
- Check the project documentation
- Review the troubleshooting guide
- Submit issues to the project repository

## Build Details

This build was created with:
\`\`\`bash
$SCRIPT_NAME --mode $BUILD_MODE --output $EXTENSION_OUTPUT_DIR
\`\`\`

EOF

    log_success "Installation instructions created: $readme_file"
}

# Show build summary
show_build_summary() {
    log_step "Build Summary"
    
    echo ""
    log_success "Build completed successfully!"
    echo ""
    log_info "Build Configuration:"
    log_info "  Mode: $BUILD_MODE"
    log_info "  Roo Code Version: $ROO_EXTENSION_VERSION"
    log_info "  Output Directory: $EXTENSION_OUTPUT_DIR"
    
    echo ""
    log_info "Generated Files:"
    
    # List generated files
    if [[ -d "$EXTENSION_OUTPUT_DIR" ]]; then
        for file in "$EXTENSION_OUTPUT_DIR"/*.zip "$EXTENSION_OUTPUT_DIR"/*.jar; do
            if [[ -f "$file" ]]; then
                log_info "  - $(basename "$file") ($(du -h "$file" | cut -f1))"
            fi
        done
        
        if [[ -d "$EXTENSION_OUTPUT_DIR/extension_host" ]]; then
            log_info "  - extension_host/ directory"
        fi
        
        if [[ "$BUILD_MODE" == "debug" && -d "$EXTENSION_OUTPUT_DIR/debug-resources" ]]; then
            log_info "  - debug-resources/ directory"
        fi
        
        if [[ -f "$EXTENSION_OUTPUT_DIR/README.md" ]]; then
            log_info "  - README.md (Installation instructions)"
        fi
    fi
    
    echo ""
    log_info "Next Steps:"
    log_info "  1. Navigate to: $EXTENSION_OUTPUT_DIR"
    log_info "  2. Read the README.md for installation instructions"
    log_info "  3. Install the plugin in your JetBrains IDE"
    log_info "  4. Enjoy Roo Code pre-integrated and ready to use!"
    
    echo ""
    log_success "Build with Roo Code complete! 🎉"
}

# Main function
main() {
    log_info "Starting RunVSAgent build with Roo Code integration..."
    log_info "Script: $SCRIPT_NAME v$SCRIPT_VERSION"
    
    # Parse arguments
    parse_args "$@"
    
    if [[ "$DRY_RUN" == "true" ]]; then
        log_warn "DRY RUN MODE - No changes will be made"
    fi
    
    # Initialize environment
    init_roo_build_env
    
    # Run build process
    check_requirements
    clean_build_artifacts
    
    # Download and prepare Roo Code
    download_roo_extension
    prepare_roo_extension
    
    # Build components
    build_vscode_if_needed
    build_extension_host_component
    build_idea_with_roo
    
    # Finalize
    copy_debug_resources_with_roo
    create_installation_instructions
    show_build_summary
    
    log_success "All done! Your build with Roo Code is ready at: $EXTENSION_OUTPUT_DIR"
}

# Run main function with all arguments
main "$@"