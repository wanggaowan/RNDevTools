package com.wanggaowan.rndevtools.utils

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/**
 * 通知工具类
 *
 * @author Created by wanggaowan on 2022/5/4 14:45
 */
object NotificationUtils {

    /**
     * 展示气球形式通知框
     */
    @JvmStatic
    fun showBalloonMsg(project: Project, msg: String, notificationType: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("com.wanggaowan.rndevtools.balloon")
            .createNotification(msg, notificationType)
            .notify(project)
    }
}
