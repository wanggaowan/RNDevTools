package com.wanggaowan.rndevtools.utils.msg

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.Messages

/**
 * 消息工具类,界面显示一个确定按钮消息。不能在写入操作线程执行，比如WriteCommandAction
 *
 * @author Created by wanggaowan on 2022/5/9 09:31
 */
object MessageUtils {
    fun show(
        msg: String,
        messageType: MessageType = MessageType.INFO,
        title: String? = null,
        project: Project? = null
    ) {
        Messages.showMessageDialog(project, msg, title ?: "", messageType.defaultIcon)
    }
}
