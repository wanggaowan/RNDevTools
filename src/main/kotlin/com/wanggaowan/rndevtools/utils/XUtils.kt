package com.wanggaowan.rndevtools.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager

/**
 * 提供通用工具方法
 *
 * @author Created by wanggaowan on 2023/2/2 11:35
 */
object XUtils {
    fun isRNProject(project: Project): Boolean {
        val basePath = project.basePath ?: return false
        val file = VirtualFileManager.getInstance().findFileByUrl("file://$basePath") ?: return false
        var isRNProject = false
        for (child in file.children) {
            if (child.name == "package.json") {
                // 存在package.json文件就认为是RN项目
                isRNProject = true
                break
            }
        }
        return isRNProject
    }
}
