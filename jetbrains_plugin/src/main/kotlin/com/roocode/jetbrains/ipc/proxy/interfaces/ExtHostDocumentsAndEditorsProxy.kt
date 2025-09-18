// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.roocode.jetbrains.ipc.proxy.interfaces

import com.roocode.jetbrains.editor.DocumentsAndEditorsDelta


interface ExtHostDocumentsAndEditorsProxy {
    fun acceptDocumentsAndEditorsDelta(d: DocumentsAndEditorsDelta)
}