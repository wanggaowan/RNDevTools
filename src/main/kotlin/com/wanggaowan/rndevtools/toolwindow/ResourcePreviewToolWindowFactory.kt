package com.wanggaowan.rndevtools.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.Content
import com.wanggaowan.rndevtools.ui.ImagePreviewPanel
import com.wanggaowan.rndevtools.utils.XUtils


/**
 * 资源文件预览，目前仅支持预览图片
 *
 * @author Created by wanggaowan on 2022/6/17 13:09
 */
class ResourcePreviewToolWindowFactory : ToolWindowFactory {

    override fun isApplicable(project: Project): Boolean {
        return XUtils.isRNProject(project)
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val manager = toolWindow.contentManager
        val panel = ImagePreviewPanel(project)
        val content: Content = manager.factory.createContent(panel, "", false)
        manager.addContent(content)
    }
}
