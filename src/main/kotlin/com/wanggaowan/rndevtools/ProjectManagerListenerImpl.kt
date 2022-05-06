package com.wanggaowan.rndevtools

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

private val Log = logger<AutoCreateImagesResourceListener>()

/**
 * 项目管理监听
 *
 * @author Created by wanggaowan on 2022/4/16 19:57
 */
class ProjectManagerListenerImpl : ProjectManagerListener {

    override fun projectOpened(project: Project) {
        super.projectOpened(project)
        Log.info("项目启动")
        // val imgListener = AutoCreateImagesResourceListener(project)
        // project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, imgListener)
    }

    override fun projectClosed(project: Project) {
        super.projectClosed(project)
        Log.info("项目关闭")
    }
}
