// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.roocode.jetbrains.editor

import com.intellij.openapi.project.Project
import com.roocode.jetbrains.core.PluginContext
import com.roocode.jetbrains.core.ServiceProxyRegistry
import com.roocode.jetbrains.ipc.proxy.interfaces.ExtHostDocumentsAndEditorsProxy
import com.roocode.jetbrains.ipc.proxy.interfaces.ExtHostDocumentsProxy
import com.roocode.jetbrains.ipc.proxy.interfaces.ExtHostEditorTabsProxy
import com.roocode.jetbrains.ipc.proxy.interfaces.ExtHostEditorsProxy
import com.roocode.jetbrains.util.URI

class EditorStateService(val project: Project) {
    var extHostDocumentsAndEditorsProxy : ExtHostDocumentsAndEditorsProxy? = null
    var extHostEditorsProxy : ExtHostEditorsProxy? = null
    var extHostDocumentsProxy : ExtHostDocumentsProxy? = null

    fun acceptDocumentsAndEditorsDelta(detail:DocumentsAndEditorsDelta){
        val protocol = PluginContext.getInstance(project).getRPCProtocol()
        if(extHostDocumentsAndEditorsProxy == null){
            extHostDocumentsAndEditorsProxy = protocol?.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostDocumentsAndEditors)
        }
        extHostDocumentsAndEditorsProxy?.acceptDocumentsAndEditorsDelta(detail)
    }

    fun acceptEditorPropertiesChanged(detail: Map<String, EditorPropertiesChangeData>){
        val protocol = PluginContext.getInstance(project).getRPCProtocol()
        if(extHostEditorsProxy == null){
            extHostEditorsProxy = protocol?.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostEditors)
        }
        extHostEditorsProxy?.let {
            for ((id, data) in detail){
                it.acceptEditorPropertiesChanged(id,data)
            }
        }
    }

    fun acceptModelChanged( detail: Map<URI, ModelChangedEvent>){
        val protocol = PluginContext.getInstance(project).getRPCProtocol()
        if (extHostDocumentsProxy == null){
            extHostDocumentsProxy = protocol?.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostDocuments)
        }
        extHostDocumentsProxy?.let {
            for ((uri, data) in detail) {
                it.acceptModelChanged(uri,data,data.isDirty)
            }
        }
    }

}


class TabStateService(val project: Project) {
    var extHostEditorTabsProxy : ExtHostEditorTabsProxy? = null

    fun acceptEditorTabModel(detail: List<EditorTabGroupDto>){
        val protocol = PluginContext.getInstance(project).getRPCProtocol()
        if (extHostEditorTabsProxy == null){
            extHostEditorTabsProxy = protocol?.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostEditorTabs)
        }
        extHostEditorTabsProxy?.acceptEditorTabModel(detail)
    }

    fun acceptTabOperation(detail: TabOperation) {
        val protocol = PluginContext.getInstance(project).getRPCProtocol()
        if (extHostEditorTabsProxy == null){
            extHostEditorTabsProxy = protocol?.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostEditorTabs)
        }
        extHostEditorTabsProxy?.acceptTabOperation(detail)
    }

    fun acceptTabGroupUpdate(detail: EditorTabGroupDto) {
        val protocol = PluginContext.getInstance(project).getRPCProtocol()
        if (extHostEditorTabsProxy == null){
            extHostEditorTabsProxy = protocol?.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostEditorTabs)
        }
        extHostEditorTabsProxy?.acceptTabGroupUpdate(detail)
    }
}