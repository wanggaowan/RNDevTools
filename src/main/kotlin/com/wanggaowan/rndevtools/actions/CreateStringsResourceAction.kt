package com.wanggaowan.rndevtools.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.wanggaowan.rndevtools.utils.XUtils

/**
 * 右键目录/文件生成文本资源引用
 *
 * @author Created by wanggaowan on 2022/5/1 21:52
 */
class CreateStringsResourceAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        var virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        if (!virtualFile.isDirectory) {
            virtualFile = virtualFile.parent ?: return
            if (!virtualFile.isDirectory) {
                return
            }
        }

        CreateStringsResource.create(project, virtualFile)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        if (!XUtils.isRNProject(project)) {
            e.presentation.isVisible = false
            return
        }

        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (virtualFile == null) {
            e.presentation.isVisible = false
            return
        }

        if (virtualFile.isDirectory) {
            e.presentation.isVisible = true
            return
        }

        val parent = virtualFile.parent
        if (parent != null && parent.isDirectory) {
            e.presentation.isVisible = true
            return
        }

        e.presentation.isVisible = false
    }
}
