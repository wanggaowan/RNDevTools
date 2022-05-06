package com.wanggaowan.rndevtools.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup

/**
 * 资源创建分组
 *
 * @author Created by wanggaowan on 2022/5/1 21:35
 */
class CreateResGroupAction : DefaultActionGroup() {
    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isVisible = virtualFile != null && virtualFile.isDirectory
    }
}
