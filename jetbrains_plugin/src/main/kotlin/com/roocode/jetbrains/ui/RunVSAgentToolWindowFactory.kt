// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.roocode.jetbrains.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.ui.jcef.JBCefApp
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.ide.BrowserUtil
import com.roocode.jetbrains.actions.OpenDevToolsAction
import com.roocode.jetbrains.plugin.WecoderPlugin
import com.roocode.jetbrains.plugin.WecoderPluginService
import com.roocode.jetbrains.plugin.DEBUG_MODE
import com.roocode.jetbrains.webview.DragDropHandler
import com.roocode.jetbrains.webview.WebViewCreationCallback
import com.roocode.jetbrains.webview.WebViewInstance
import com.roocode.jetbrains.webview.WebViewManager
import com.roocode.jetbrains.util.PluginConstants
import com.roocode.jetbrains.extensions.core.ExtensionConfigurationManager
import com.roocode.jetbrains.extensions.core.ExtensionManager
import com.roocode.jetbrains.plugin.SystemObjectProvider
import com.roocode.jetbrains.extensions.ui.VsixUploadDialog
import java.awt.BorderLayout
import java.awt.datatransfer.StringSelection
import java.awt.Toolkit
import java.awt.Dimension
import java.awt.Font
import java.awt.Component
import java.awt.Cursor
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.BorderFactory
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.roocode.jetbrains.util.ConfigFileUtils

class RunVSAgentToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Initialize plugin service
        val pluginService = WecoderPlugin.getInstance(project)
//        pluginService.initialize(project)

        // toolbar
        val titleActions = mutableListOf<AnAction>()
        val action = ActionManager.getInstance().getAction("WecoderToolbarGroup")
        if (action != null) {
            titleActions.add(action)
        }
        // Add developer tools button only in debug mode
        if ( WecoderPluginService.getDebugMode() != DEBUG_MODE.NONE) {
            titleActions.add(OpenDevToolsAction { project.getService(WebViewManager::class.java).getLatestWebView() })
        }

        toolWindow.setTitleActions(titleActions)
        toolWindow.stripeTitle = "Roo Code"

        // webview panel
        val toolWindowContent = RunVSAgentToolWindowContent(project, toolWindow)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(
            toolWindowContent.content,
            "",
            false
        )
        toolWindow.contentManager.addContent(content)
    }

    private class RunVSAgentToolWindowContent(
        private val project: Project,
        private val toolWindow: ToolWindow
    ) : WebViewCreationCallback {
        private val logger = Logger.getInstance(RunVSAgentToolWindowContent::class.java)

        // Get WebViewManager instance
        private val webViewManager = project.getService(WebViewManager::class.java)

        // Get ExtensionConfigurationManager instance
        private val configManager = ExtensionConfigurationManager.getInstance(project)
        
        // Get ExtensionManager instance
        private val extensionManager = ExtensionManager.getInstance(project)

        // Content panel
        private val contentPanel = JPanel(BorderLayout())

        // Placeholder label with proper padding and centering
        private val placeholderLabel = JLabel(createSystemInfoText()).apply {
            horizontalAlignment = JLabel.CENTER
            verticalAlignment = JLabel.CENTER
            border = BorderFactory.createEmptyBorder(40, 40, 40, 40)
        }

        // System info text for copying
        private val systemInfoText = createSystemInfoPlainText()

        // Plugin selection panel (shown when configuration is invalid)
        private val pluginSelectionPanel = createPluginSelectionPanel()

        // Configuration status panel
        private val configStatusPanel = createConfigStatusPanel()

        // State lock to prevent UI changes during plugin startup
        @Volatile
        private var isPluginStarting = false

        // Plugin running state
        @Volatile
        private var isPluginRunning = false

        /**
         * Check if plugin is actually running
         */
        private fun isPluginActuallyRunning(): Boolean {
            return try {
                val extensionManager = ExtensionManager.getInstance(project)
                extensionManager.isProperlyInitialized()
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Create system information text in HTML format
         */
        private fun createSystemInfoText(): String {
            val appInfo = ApplicationInfo.getInstance()
            val plugin = PluginManagerCore.getPlugin(PluginId.getId(PluginConstants.PLUGIN_ID))
            val pluginVersion = plugin?.version ?: "unknown"
            val osName = System.getProperty("os.name")
            val osVersion = System.getProperty("os.version")
            val osArch = System.getProperty("os.arch")
            val jcefSupported = JBCefApp.isSupported()
            val javaVersion = System.getProperty("java.version")

            // Check for Linux ARM system
            val isLinuxArm = osName.lowercase().contains("linux") && (osArch.lowercase().contains("aarch64") || osArch.lowercase().contains("arm"))

            // Use simple HTML with proper structure for better centering
            return buildString {
                append("<html><body style='text-align: center; padding: 20px;'>")
                append("<div style='margin: 0 auto; max-width: 600px;'>")
                append("<h2 style='margin-bottom: 20px;'>Roo Code is initializing...</h2>")
                append("<div style='text-align: left; display: inline-block;'>")
                append("<h3 style='margin-top: 30px;'>System Information</h3>")
                append("<ul style='padding-left: 20px;'>")
                append("<li>OS: $osName $osVersion</li>")
                append("<li>Architecture: $osArch</li>")
                append("<li>IDE: ${appInfo.fullApplicationName}</li>")
                append("<li>Plugin: v$pluginVersion</li>")
                append("<li>Java: $javaVersion</li>")
                append("<li>JCEF: ${if (jcefSupported) "Supported" else "Not Supported"}</li>")
                append("</ul>")
                append("</div>")

                // Add warning messages if needed
                if (isLinuxArm) {
                    append("<div style='margin-top: 20px;'>")
                    append("<p>⚠️ System Not Supported</p>")
                    append("<p>Linux ARM systems are not currently supported.</p>")
                    append("</div>")
                }

                if (!jcefSupported) {
                    append("<div style='margin-top: 20px;'>")
                    append("<p>⚠️ JCEF Not Supported</p>")
                    append("<p>Please use a JCEF-enabled runtime.</p>")
                    append("</div>")
                }

                append("</div>")
                append("</body></html>")
            }
        }

        /**
         * Detect current IDEA theme
         */
        private fun detectCurrentTheme(): Boolean {
            return try {
                val background = javax.swing.UIManager.getColor("Panel.background")
                if (background != null) {
                    val brightness = (0.299 * background.red + 0.587 * background.green + 0.114 * background.blue) / 255.0
                    brightness < 0.5
                } else {
                    // Default to dark theme if cannot detect
                    true
                }
            } catch (e: Exception) {
                // Default to dark theme on error
                true
            }
        }

        /**
         * Create system information text in plain text format for copying
         */
        private fun createSystemInfoPlainText(): String {
            val appInfo = ApplicationInfo.getInstance()
            val plugin = PluginManagerCore.getPlugin(PluginId.getId(PluginConstants.PLUGIN_ID))
            val pluginVersion = plugin?.version ?: "unknown"
            val osName = System.getProperty("os.name")
            val osVersion = System.getProperty("os.version")
            val osArch = System.getProperty("os.arch")
            val javaVersion = System.getProperty("java.version")
            val jcefSupported = JBCefApp.isSupported()

            // Check for Linux ARM system
            val isLinuxArm = osName.lowercase().contains("linux") && (osArch.lowercase().contains("aarch64") || osArch.lowercase().contains("arm"))

            return buildString {
                append("Roo Code System Information\n")
                append("===========================\n\n")
                append("Status: Initializing...\n\n")
                append("System Details:\n")
                append("  OS: $osName $osVersion ($osArch)\n")
                append("  IDE: ${appInfo.fullApplicationName} (build ${appInfo.build})\n")
                append("  Plugin: v$pluginVersion\n")
                append("  Java: $javaVersion\n")
                append("  JCEF Support: ${if (jcefSupported) "Yes" else "No"}\n")

                // Add warning messages
                if (isLinuxArm) {
                    append("\nWarning: Linux ARM systems are currently not supported.\n")
                }

                if (!jcefSupported) {
                    append("\nError: Your IDE runtime does not support JCEF. Please use a runtime with JCEF support.\n")
                }
            }
        }

        /**
         * Copy system information to clipboard
         */
        private fun copySystemInfo() {
            val stringSelection = StringSelection(systemInfoText)
            val clipboard = Toolkit.getDefaultToolkit().getSystemClipboard()
            clipboard.setContents(stringSelection, null)
        }

        // Known Issues button - use standard IDE button styling
        private val knownIssuesButton = JButton("Known Issues").apply {
            addActionListener {
                BrowserUtil.browse("https://github.com/RooCodeInc/Roo-Code-JetBrains/blob/main/docs/KNOWN_ISSUES.md")
            }
        }

        // Copy button - use standard IDE button styling
        private val copyButton = JButton("Copy Info").apply {
            addActionListener { copySystemInfo() }
        }

        // Button panel to hold both buttons side by side with proper spacing
        private val buttonPanel = JPanel().apply {
            layout = java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 10, 10)
            border = BorderFactory.createEmptyBorder(20, 20, 20, 20)
            add(knownIssuesButton)
            add(copyButton)
        }

        private var dragDropHandler: DragDropHandler? = null

        val content: JPanel = JPanel(BorderLayout()).apply {
            contentPanel.layout = BorderLayout()

            // Check configuration status and show appropriate content
            if (configManager.isConfigurationLoaded() && configManager.isConfigurationValid()) {
                
                val initPanel = JPanel(BorderLayout()).apply {
                    border = BorderFactory.createEmptyBorder(20, 20, 20, 20)
                    add(placeholderLabel, BorderLayout.CENTER)
                    add(buttonPanel, BorderLayout.SOUTH)
                }
                contentPanel.add(initPanel, BorderLayout.CENTER)
            } else {
                val selectionPanel = JPanel(BorderLayout()).apply {
                    border = BorderFactory.createEmptyBorder(20, 20, 20, 20)
                    add(pluginSelectionPanel, BorderLayout.CENTER)
                    add(configStatusPanel, BorderLayout.SOUTH)
                }
                contentPanel.add(selectionPanel, BorderLayout.CENTER)
            }

            add(contentPanel, BorderLayout.CENTER)
        }

        init {
            // Always show system info panel (no plugin selection needed)
            val initPanel = JPanel(BorderLayout()).apply {
                border = BorderFactory.createEmptyBorder(20, 20, 20, 20)
                add(placeholderLabel, BorderLayout.CENTER)
                add(buttonPanel, BorderLayout.SOUTH)
            }
            contentPanel.add(initPanel, BorderLayout.CENTER)
            
            // Don't auto-start here - WecoderPlugin will handle startup
            // The plugin will start automatically if configuration is valid
            
            // Start configuration monitoring
            startConfigurationMonitoring()

            // Add theme change listener
            addThemeChangeListener()

            // Try to get existing WebView
            webViewManager.getLatestWebView()?.let { webView ->
                // Add WebView component immediately when created
                ApplicationManager.getApplication().invokeLater {
                    addWebViewComponent(webView)
                }
                // Set page load callback to hide system info only after page is loaded
                webView.setPageLoadCallback {
                    ApplicationManager.getApplication().invokeLater {
                        hideSystemInfo()
                    }
                }
                // If page is already loaded, hide system info immediately
                if (webView.isPageLoaded()) {
                    ApplicationManager.getApplication().invokeLater {
                        hideSystemInfo()
                    }
                }
            }?:webViewManager.addCreationCallback(this, toolWindow.disposable)
        }

        /**
         * Add theme change listener to automatically update UI when theme changes
         */
        private fun addThemeChangeListener() {
            try {
                val messageBus = ApplicationManager.getApplication().messageBus
                val connection = messageBus.connect(toolWindow.disposable)
                connection.subscribe(com.intellij.ide.ui.LafManagerListener.TOPIC, com.intellij.ide.ui.LafManagerListener {
                    logger.info("Theme changed, updating UI styles")
                    // Update UI content with new theme
                    ApplicationManager.getApplication().invokeLater {
                        updateUIContent()
                        // Update status panel if it exists
                        if (configStatusPanel.componentCount > 0) {
                            updateConfigStatus(configStatusPanel.getComponent(0) as JLabel)
                        }
                    }
                })
                logger.info("Theme change listener added successfully")
            } catch (e: Exception) {
                logger.error("Failed to add theme change listener", e)
            }
        }

        /**
         * Start configuration monitoring to detect changes
         */
        private fun startConfigurationMonitoring() {
            // Start background monitoring thread
            Thread {
                try {
                    while (!project.isDisposed) {
                        Thread.sleep(2000) // Check every 2 seconds

                        if (!project.isDisposed) {
                            // Don't update UI if plugin is starting or running
                            if (isPluginStarting || isPluginRunning) {
                                logger.debug("Plugin is starting or running, skipping UI update")
                                continue
                            }

                            // Only update UI if we're not in the middle of plugin startup
                            // Check if plugin is actually running before updating UI
                            val isPluginRunning = isPluginActuallyRunning()

                            // Only update UI if plugin is not running or if there's a significant change
                            if (!isPluginRunning) {
                                ApplicationManager.getApplication().invokeLater {
                                    updateUIContent()
                                }
                            } else {
                                // Plugin is running, only update status labels, don't change main UI
                                ApplicationManager.getApplication().invokeLater {
                                    updateConfigStatus(configStatusPanel.getComponent(0) as JLabel)
                                }
                            }
                        }
                    }
                } catch (e: InterruptedException) {
                    logger.info("Configuration monitoring interrupted")
                } catch (e: Exception) {
                    logger.error("Error in configuration monitoring", e)
                }
            }.apply {
                isDaemon = true
                name = "RunVSAgent-ConfigMonitor-UI"
                start()
            }
        }

        /**
         * WebView creation callback implementation
         */
        override fun onWebViewCreated(instance: WebViewInstance) {
            // Add WebView component immediately when created
            ApplicationManager.getApplication().invokeLater {
                addWebViewComponent(instance)
            }
            // Set page load callback to hide system info only after page is loaded
            instance.setPageLoadCallback {
                // Ensure UI update in EDT thread
                ApplicationManager.getApplication().invokeLater {
                    hideSystemInfo()
                }
            }
        }

        /**
         * Add WebView component to UI
         */
        private fun addWebViewComponent(webView: WebViewInstance) {
            logger.info("Adding WebView component to UI: ${webView.viewType}/${webView.viewId}")

            // Check if WebView component is already added
            val components = contentPanel.components
            for (component in components) {
                if (component === webView.browser.component) {
                    logger.info("WebView component already exists in UI")
                    return
                }
            }

            // Add WebView component without removing existing components
            contentPanel.add(webView.browser.component, BorderLayout.CENTER)

            setupDragAndDropSupport(webView)

            // Relayout
            contentPanel.revalidate()
            contentPanel.repaint()

            logger.info("WebView component added to tool window")
        }

        /**
         * Hide system info placeholder
         */
        private fun hideSystemInfo() {
            logger.info("Hiding system info placeholder")

            // Remove all components from content panel except WebView component
            val components = contentPanel.components
            for (component in components) {
                if (component !== webViewManager.getLatestWebView()?.browser?.component) {
                    contentPanel.remove(component)
                }
            }

            webViewManager.getLatestWebView()?.let { webView ->
                contentPanel.remove(webView.browser.component)
                contentPanel.add(webView.browser.component, BorderLayout.CENTER)
            }

            // Relayout
            contentPanel.revalidate()
            contentPanel.repaint()

            logger.info("System info placeholder hidden")
        }

        /**
         * Setup drag and drop support
         */
        private fun setupDragAndDropSupport(webView: WebViewInstance) {
            try {
                logger.info("Setting up drag and drop support for WebView")

                dragDropHandler = DragDropHandler(webView, contentPanel)

                dragDropHandler?.setupDragAndDrop()

                logger.info("Drag and drop support enabled")
            } catch (e: Exception) {
                logger.error("Failed to setup drag and drop support", e)
            }
        }

        /**
         * Create plugin selection panel
         */
        private fun createPluginSelectionPanel(): JPanel {
            val panel = JPanel()
            panel.layout = BorderLayout()
            panel.border = javax.swing.BorderFactory.createEmptyBorder(20, 20, 20, 20)

            // Title
            val titleLabel = JLabel("🔧 Select Plugin").apply {
                font = font.deriveFont(18f)
                horizontalAlignment = javax.swing.SwingConstants.CENTER
                border = javax.swing.BorderFactory.createEmptyBorder(0, 0, 20, 0)
            }

            // Description
            val descLabel = JLabel("Invalid configuration detected, please select a default plugin to continue:").apply {
                font = font.deriveFont(14f)
                horizontalAlignment = javax.swing.SwingConstants.CENTER
                border = javax.swing.BorderFactory.createEmptyBorder(0, 0, 20, 0)
            }

            // Plugin list with modern styling
            val pluginListPanel = createPluginListPanel()

            // Action buttons
            val buttonPanel = JPanel()
            buttonPanel.layout = BorderLayout()
            buttonPanel.border = javax.swing.BorderFactory.createEmptyBorder(20, 0, 0, 0)

            val debugButton = JButton("🐛 Debug Info").apply {
                preferredSize = JBUI.size(160, 36)
                font = JBFont.label().deriveFont(14f)
                isFocusPainted = false
                isOpaque = false
                addActionListener {
                    showDebugInfo()
                }
            }
            
            buttonPanel.add(debugButton, BorderLayout.WEST)
            
            // Add all components
            panel.add(titleLabel, BorderLayout.NORTH)
            panel.add(descLabel, BorderLayout.CENTER)
            panel.add(pluginListPanel, BorderLayout.CENTER)
            panel.add(buttonPanel, BorderLayout.SOUTH)
            
            return panel
        }

        /**
         * Create plugin list panel with modern styling
         */
        private fun createPluginListPanel(): JPanel {
            val panel = JPanel()
            panel.layout = javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS)
            panel.border = javax.swing.BorderFactory.createEmptyBorder(10, 0, 10, 0)

            // Dynamically get available providers and their status
            val extensions = extensionManager.getAllExtensions()
            val currentExtensionId = ConfigFileUtils.getCurrentExtensionId()

            val plugins = extensions.map { provider ->
                val extensionId = provider.getExtensionId()
                val isCurrent = provider.getExtensionId() == currentExtensionId
                val isAvailable = provider.isAvailable(project)
                
                PluginInfo(
                    id = extensionId,
                    displayName = provider.getDisplayName(),
                    description = provider.getDescription(),
                    isAvailable = isAvailable,
                    isCurrent = isCurrent
                )
            }

            plugins.forEach { pluginInfo ->
                val pluginRow = createPluginRow(pluginInfo)
                panel.add(pluginRow)
                panel.add(javax.swing.Box.createVerticalStrut(8))
            }

            return panel
        }

        /**
         * Create a single plugin row
         */
        private fun createPluginRow(pluginInfo: PluginInfo): JPanel {
            val rowPanel = JPanel(BorderLayout())
            
            // Main content panel - use default IDE styling
            val contentPanel = JPanel(BorderLayout()).apply {
                // Use default panel background from IDE theme
                isOpaque = true
                
                // Simple border without custom colors
                val borderWidth = if (pluginInfo.isCurrent) 2 else 1
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(javax.swing.UIManager.getColor("Separator.foreground"), borderWidth),
                    javax.swing.BorderFactory.createEmptyBorder(12, 16, 12, 16)
                )
            }

            // Top row: Title and buttons
            val topRowPanel = JPanel(BorderLayout())
            topRowPanel.isOpaque = false
            topRowPanel.border = javax.swing.BorderFactory.createEmptyBorder(0, 0, 4, 0)

            // Left side: Plugin name with status indicator
            val statusIcon = when {
                pluginInfo.isCurrent -> "🟢"
                pluginInfo.isAvailable -> "✅"
                else -> "❌"
            }
            val nameText = if (pluginInfo.isCurrent) {
                "${pluginInfo.displayName} (Currently Running)"
            } else {
                pluginInfo.displayName
            }
            val nameLabel = JLabel("$statusIcon $nameText").apply {
                font = font.deriveFont(15f).deriveFont(java.awt.Font.BOLD)
                // Use default label foreground from IDE theme
                foreground = if (pluginInfo.isAvailable) {
                    javax.swing.UIManager.getColor("Label.foreground")
                } else {
                    javax.swing.UIManager.getColor("Label.disabledForeground")
                }
            }

            // Right side: Action buttons
            val buttonPanel = JPanel()
            buttonPanel.layout = javax.swing.BoxLayout(buttonPanel, javax.swing.BoxLayout.X_AXIS)
            buttonPanel.isOpaque = false

            // VSIX upload button - use standard IDE button styling
            val uploadButton = JButton("📦 Install From VSIX").apply {
                font = JBFont.label()
                isEnabled = true
                isFocusPainted = false
                
                addActionListener {
                    uploadVsixForPlugin(pluginInfo.id, pluginInfo.displayName)
                }
            }

            buttonPanel.add(javax.swing.Box.createHorizontalStrut(8))
            buttonPanel.add(uploadButton)

            // Add title and buttons to top row
            topRowPanel.add(nameLabel, BorderLayout.WEST)
            topRowPanel.add(buttonPanel, BorderLayout.EAST)

            // Bottom row: Plugin description
            val descriptionText = if (pluginInfo.isAvailable) {
                pluginInfo.description
            } else {
                "${pluginInfo.description} (Plugin unavailable, please upload VSIX file)"
            }
            val descLabel = JLabel(descriptionText).apply {
                font = font.deriveFont(12f)
                // Use default label foreground from IDE theme
                foreground = if (pluginInfo.isAvailable) {
                    javax.swing.UIManager.getColor("Label.foreground")
                } else {
                    javax.swing.UIManager.getColor("Label.disabledForeground")
                }
            }

            // Add components to content panel
            contentPanel.add(topRowPanel, BorderLayout.NORTH)
            contentPanel.add(descLabel, BorderLayout.CENTER)

            // Add click listener to the entire row for better UX - only for available plugins
            if (pluginInfo.isAvailable) {
                contentPanel.addMouseListener(object : java.awt.event.MouseAdapter() {
                    override fun mouseClicked(e: java.awt.event.MouseEvent) {
                        if (e.clickCount == 1) {
                            applyPluginSelection(pluginInfo.id)
                        }
                    }
                    
                    override fun mouseEntered(e: java.awt.event.MouseEvent) {
                        contentPanel.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                        // Simple hover border effect using IDE theme colors
                        contentPanel.border = BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(javax.swing.UIManager.getColor("Focus.borderColor") ?: javax.swing.UIManager.getColor("Separator.foreground"), 2),
                            javax.swing.BorderFactory.createEmptyBorder(12, 16, 12, 16)
                        )
                        contentPanel.repaint()
                    }
                    
                    override fun mouseExited(e: java.awt.event.MouseEvent) {
                        contentPanel.cursor = java.awt.Cursor.getDefaultCursor()
                        // Restore normal border
                        val borderWidth = if (pluginInfo.isCurrent) 2 else 1
                        contentPanel.border = BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(javax.swing.UIManager.getColor("Separator.foreground"), borderWidth),
                            javax.swing.BorderFactory.createEmptyBorder(12, 16, 12, 16)
                        )
                        contentPanel.repaint()
                    }
                })
            } else {
                // For unavailable plugins, set default cursor and no hover effects
                contentPanel.cursor = java.awt.Cursor.getDefaultCursor()
            }

            rowPanel.add(contentPanel)
            // Prevent BoxLayout (Y_AXIS) from stretching this row vertically
            // Limit the maximum height of both contentPanel and rowPanel to their preferred heights
            val pref = contentPanel.preferredSize
            // Ensure preferred size is computed
            contentPanel.doLayout()
            val computedPref = if (pref != null && pref.height > 0) pref else contentPanel.preferredSize
            contentPanel.maximumSize = java.awt.Dimension(Int.MAX_VALUE, computedPref.height)
            rowPanel.maximumSize = java.awt.Dimension(Int.MAX_VALUE, computedPref.height)
            // Keep the row aligned to the top when extra vertical space exists
            rowPanel.alignmentY = javax.swing.Box.TOP_ALIGNMENT
            return rowPanel
        }

        /**
         * Plugin information data class
         */
        private data class PluginInfo(
            val id: String,
            val displayName: String,
            val description: String,
            val isAvailable: Boolean,
            val isCurrent: Boolean = false
        )

        /**
         * Upload VSIX file for a specific plugin
         */
        private fun uploadVsixForPlugin(pluginId: String, pluginName: String) {
            try {
                // Use VsixUploadDialog directly
                val success = VsixUploadDialog.show(project, pluginId, pluginName)
                
                if (success) {
                    javax.swing.JOptionPane.showMessageDialog(
                        contentPanel,
                        "VSIX file uploaded successfully!\nPlugin: $pluginName\nYou can now launch the plugin.",
                        "Upload Complete",
                        javax.swing.JOptionPane.INFORMATION_MESSAGE
                    )
                }
            } catch (e: Exception) {
                logger.error("Failed to upload VSIX for plugin: $pluginId", e)
                javax.swing.JOptionPane.showMessageDialog(
                    contentPanel,
                    "Upload failed: ${e.message}",
                    "Error",
                    javax.swing.JOptionPane.ERROR_MESSAGE
                )
            }
        }
        
        /**
         * Create configuration status panel
         */
        private fun createConfigStatusPanel(): JPanel {
            val panel = JPanel()
            panel.layout = BorderLayout()
            panel.border = javax.swing.BorderFactory.createEmptyBorder(20, 20, 20, 20)
            
            // Status label
            val statusLabel = JLabel().apply {
                font = font.deriveFont(14f)
                horizontalAlignment = javax.swing.SwingConstants.CENTER
            }
            
            // Update status
            updateConfigStatus(statusLabel)
            
            panel.add(statusLabel, BorderLayout.CENTER)
            return panel
        }
        
        /**
         * Update configuration status
         */
        private fun updateConfigStatus(statusLabel: JLabel) {
            // Detect current theme for status colors
            val isDarkTheme = detectCurrentTheme()
            
            if (configManager.isConfigurationLoaded()) {
                if (configManager.isConfigurationValid()) {
                    val extensionId = configManager.getCurrentExtensionId()
                    // Check if plugin is actually running
                    val isPluginRunning = isPluginActuallyRunning()
                    
                    if (isPluginRunning) {
                        statusLabel.text = "✅ Plugin Running - Current Plugin: $extensionId"
                        statusLabel.foreground = getThemeAdaptiveColor(isDarkTheme, "success")
                    } else {
                        statusLabel.text = "⚠️ Configuration Valid but Plugin Not Running - Current Plugin: $extensionId"
                        statusLabel.foreground = getThemeAdaptiveColor(isDarkTheme, "warning")
                    }
                } else {
                    statusLabel.text = "❌ Configuration Invalid - ${configManager.getConfigurationError()}"
                    statusLabel.foreground = getThemeAdaptiveColor(isDarkTheme, "error")
                }
            } else {
                statusLabel.text = "⏳ Loading Configuration..."
                statusLabel.foreground = getThemeAdaptiveColor(isDarkTheme, "info")
            }
        }

        /**
         * Get theme-adaptive color for status indicators
         */
        private fun getThemeAdaptiveColor(isDarkTheme: Boolean, colorType: String): java.awt.Color {
            // Try to use IDE theme colors first, fallback to simple defaults
            return when (colorType) {
                "success" -> javax.swing.UIManager.getColor("Actions.Green") ?: java.awt.Color(0, 128, 0)
                "warning" -> javax.swing.UIManager.getColor("Actions.Yellow") ?: java.awt.Color(255, 165, 0)
                "error" -> javax.swing.UIManager.getColor("Actions.Red") ?: java.awt.Color(255, 0, 0)
                "info" -> javax.swing.UIManager.getColor("Actions.Blue") ?: java.awt.Color(0, 0, 255)
                else -> javax.swing.UIManager.getColor("Label.foreground") ?: java.awt.Color(128, 128, 128)
            }
        }
        
        /**
         * Apply plugin selection and create configuration
         */
        private fun applyPluginSelection(pluginId: String) {
            try {
                logger.info("Applying plugin selection: $pluginId")
                
                // Create configuration with selected plugin
                configManager.setCurrentExtensionId(pluginId)
                
                // Verify configuration was saved successfully
                if (configManager.isConfigurationValid()) {
                    // Start the plugin directly instead of just saving configuration
                    startPluginAfterSelection(pluginId)
                    
                    logger.info("Plugin selection applied successfully: $pluginId")
                } else {
                    // Configuration is still invalid after setting
                    val errorMsg = configManager.getConfigurationError() ?: "Unknown error"
                    val message = "❌ Configuration Update Failed\nError: $errorMsg\n\nPlease check the configuration file or try manual configuration."
                    javax.swing.JOptionPane.showMessageDialog(
                        contentPanel,
                        message,
                        "Configuration Update Failed",
                        javax.swing.JOptionPane.ERROR_MESSAGE
                    )
                    
                    logger.error("Configuration is still invalid after setting extension ID: $pluginId, error: $errorMsg")
                }
            } catch (e: Exception) {
                logger.error("Failed to apply plugin selection", e)
                val message = "❌ Configuration Update Failed\nError: ${e.message}\n\nPlease check file permissions or try manual configuration."
                javax.swing.JOptionPane.showMessageDialog(
                    contentPanel,
                    message,
                    "Error",
                    javax.swing.JOptionPane.ERROR_MESSAGE
                )
            }
        }
        
        /**
         * Start plugin after plugin selection
         */
        private fun startPluginAfterSelection(pluginId: String) {
            try {
                logger.info("Starting plugin after selection: $pluginId")
                
                // Set plugin starting state
                isPluginStarting = true
                
                // Update status to show plugin is starting
                updateConfigStatus(configStatusPanel.getComponent(0) as JLabel)
                
                // Get extension manager and set the selected provider
                val extensionManager = ExtensionManager.getInstance(project)
                extensionManager.initialize(pluginId)
                
                // Initialize the current provider
                extensionManager.initializeCurrentProvider()
                
                // Start plugin service
                val pluginService = WecoderPlugin.getInstance(project)
                pluginService.initialize(project)
                
                // Initialize WebViewManager
                val webViewManager = project.getService(WebViewManager::class.java)
                if (webViewManager != null) {
                    // Register to project Disposer
                    com.intellij.openapi.util.Disposer.register(project, webViewManager)
                    
                    // Start configuration monitoring
                    startConfigurationMonitoring()
                    
                    // Register project-level resource disposal
                    com.intellij.openapi.util.Disposer.register(project, com.intellij.openapi.Disposable {
                        logger.info("Disposing RunVSAgent plugin for project: ${project.name}")
                        pluginService.dispose()
                        extensionManager.dispose()
                        SystemObjectProvider.dispose()
                        // Reset state when disposing
                        isPluginRunning = false
                        isPluginStarting = false
                    })
                    
                    logger.info("Plugin started successfully after selection: $pluginId")
                    
                    // Set plugin running state
                    isPluginRunning = true
                    isPluginStarting = false
                    
                    // Update UI to show plugin is running
                    updateUIContent()
                } else {
                    logger.error("WebViewManager not available")
                    throw IllegalStateException("WebViewManager not available")
                }
                
            } catch (e: Exception) {
                logger.error("Failed to start plugin after selection", e)
                // Reset state on failure
                isPluginStarting = false
                isPluginRunning = false
                
                val message = "❌ Plugin Startup Failed\nError: ${e.message}\n\nPlease check plugin configuration or try restarting the IDE."
                javax.swing.JOptionPane.showMessageDialog(
                    contentPanel,
                    message,
                    "Plugin Startup Failed",
                    javax.swing.JOptionPane.ERROR_MESSAGE
                )
            }
        }
        
        /**
         * Update UI content based on configuration status
         */
        private fun updateUIContent() {
            // Don't update UI if plugin is starting or running
            if (isPluginStarting || isPluginRunning) {
                logger.info("Plugin is starting or running, skipping UI update")
                return
            }
            
            // Check if plugin is actually running
            val isPluginRunning = isPluginActuallyRunning()
            
            // If plugin is running, don't change the main UI content
            if (isPluginRunning) {
                logger.info("Plugin is running, keeping current UI content")
                return
            }
            
            contentPanel.removeAll()
            
            // Always show system info panel (Roo Code is always configured)
            
            val initPanel = JPanel(BorderLayout()).apply {
                border = BorderFactory.createEmptyBorder(20, 20, 20, 20)
                add(placeholderLabel, BorderLayout.CENTER)
                add(buttonPanel, BorderLayout.SOUTH)
            }
            contentPanel.add(initPanel, BorderLayout.CENTER)
            logger.info("Showing system info panel - Roo Code auto-configured")
            
            contentPanel.revalidate()
            contentPanel.repaint()
        }
        
        
        /**
         * Show manual configuration instructions
         */
        private fun showManualConfigInstructions() {
            val instructions = """
                📝 Manual Configuration Instructions
                
                1. Create configuration file in user home directory: ${PluginConstants.ConfigFiles.getMainConfigPath()}
                2. Add the following content:
                   ${PluginConstants.ConfigFiles.EXTENSION_TYPE_KEY}=roo-code
                   
                3. Supported plugin types:
                   - roo-code: Roo Code AI Assistant
                   - cline: Cline AI Assistant
                   - custom: Custom Plugin
                   
                4. Save the file and restart IDE
                
                Configuration file path: ${configManager.getConfigurationFilePath()}
            """.trimIndent()
            
            javax.swing.JOptionPane.showMessageDialog(
                contentPanel,
                instructions,
                "Manual Configuration Instructions",
                javax.swing.JOptionPane.INFORMATION_MESSAGE
            )
        }

        /**
         * Show debug information
         */
        private fun showDebugInfo() {
            val debugText = """
                Roo Code Debug Information
                ==========================
                
                🚀 Plugin Status: ${if (configManager.isConfigurationLoaded() && configManager.isConfigurationValid()) "Loaded and Valid" else "Not Loaded or Invalid"}
                
                📝 Current Configuration: ${configManager.getCurrentExtensionId() ?: "Not Set"}
                
                ⚙️ Configuration File Path: ${configManager.getConfigurationFilePath()}
                
                🔄 Configuration Load Time: ${configManager.getConfigurationLoadTime()?.let { it.toString() } ?: "Unknown"}
                
                💡 Tip: If configuration is invalid, please check the configuration file content or try manual configuration.
            """.trimIndent()
            
            javax.swing.JOptionPane.showMessageDialog(
                contentPanel,
                debugText,
                "Debug Information",
                javax.swing.JOptionPane.INFORMATION_MESSAGE
            )
        }
    }
}