package icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * 图片ICON资源加载
 *
 * @author Created by wanggaowan on 2022/5/1 21:44
 */
object SdkIcons {
    @JvmField
    val Sdk_default_icon: Icon = IconLoader.getIcon("/icons/default_icon.svg", SdkIcons::class.java)
}
