package com.wanggaowan.rndevtools.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

/**
 * 右键文档，在弹窗菜单生成一栏 生成文本资源引用
 *
 * @author Created by wanggaowan on 2022/5/1 21:52
 */
class CreateStringsResourceAction2 : AnAction() {
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
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (virtualFile != null && !virtualFile.isDirectory) {
            e.presentation.isVisible = true
            return
        }

        e.presentation.isVisible = false
    }
}
