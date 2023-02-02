package com.wanggaowan.rndevtools.description

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.JSStatement
import com.intellij.lang.javascript.psi.impl.JSPsiElementFactory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.wanggaowan.rndevtools.utils.XUtils
import java.io.File
import javax.imageio.ImageIO

/**
 * 提供图片文件描述
 *
 * @author Created by wanggaowan on 2022/5/2 13:30
 */
class ImageDocumentDescriptionProvider : DocumentationProvider {
    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        return null
    }

    override fun generateHoverDoc(element: PsiElement, originalElement: PsiElement?): String? {
        if (!XUtils.isRNProject(element.project)) {
            return null
        }

        return getDoc(element, originalElement)
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element != null && !XUtils.isRNProject(element.project)) {
            return null
        }

        return getDoc(element, originalElement)
    }

    /**
     * 生成DOC
     */
    private fun getDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element == null || originalElement == null || element !is JSStatement) {
            return null
        }

        if (!element.text.startsWith(IMAGE_ELEMENT_PREFIX)) {
            return null
        }

        // 获得 a: require('./a.png') 节点 或 './a.png' 节点
        // a: require('./a.png')是通过如：Images.a引用，展示Images.a引用资源
        // './a.png' 是定义在Images.ts中的属性，展示属性引用资源
        val imageProperty = if (originalElement is LeafPsiElement) {
            if (originalElement.parent is JSReferenceExpression) {
                originalElement.parent.reference?.resolve()
            } else {
                originalElement
            }
        } else originalElement.parent?.reference?.resolve()

        if (imageProperty !is JSProperty && imageProperty !is LeafPsiElement) {
            return null
        }

        // 获取节点文本
        val value =
            (if (imageProperty is LeafPsiElement) imageProperty.text else (imageProperty as JSProperty).value?.text)?.replace(
                "require",
                ""
            )?.replace("(", "")?.replace(")", "")?.replace("'", "")
        if (value.isNullOrEmpty()) {
            return null
        }

        // 拿到图片相对地址，此处相对仅图片名称是相对，因为引用的时候去除了如@2x，@3x等标记
        val imgPath: String? = if (value.startsWith("/")) {
            "file://$value"
        } else {
            val dirPath = getAttachFileDirAbsolutePath(imageProperty)
            if (dirPath != null) {
                "file://${dirPath}/${value.replace("./", "").replace("../", "")}"
            } else {
                null
            }
        }

        if (imgPath == null) {
            return null
        }

        val imgFile = VirtualFileManager.getInstance().findFileByUrl(imgPath) // 如果图片相对地址图片存在，则直接使用相对地址，否则查找绝对地址
        val imageAbsolutePath = if (imgFile != null) imgPath else findImageAbsolutePath(imgPath) ?: return null
        val projectPath = element.project.basePath ?: ""
        val read = ImageIO.read(File(imageAbsolutePath.replace("file://", "")).inputStream())
        val width = read?.width ?: 200
        val height = read?.height ?: 200
        val scale = (width / 200).coerceAtLeast(height / 200)
        return if (scale <= 1) {
            "<img src=\"${imageAbsolutePath}\" width=\"${width}\" height=\"${height}\"><br>${
                imageAbsolutePath.replace(
                    "file://${projectPath}", ""
                )
            }"
        } else {
            "<img src=\"${imageAbsolutePath}\" width=\"${width / scale}\" height=\"${height / scale}\"><br>${
                imageAbsolutePath.replace(
                    "file://${projectPath}", ""
                )
            }"
        }
    }

    /**
     * 根据相对图片地址获取绝对图片地址
     */
    private fun findImageAbsolutePath(relativeImgPath: String): String? {
        val indexOf = relativeImgPath.lastIndexOf("/")
        if (indexOf == -1) {
            return null
        }

        val dirPath = relativeImgPath.substring(0, indexOf)
        val imageName = relativeImgPath.substring(indexOf + 1, relativeImgPath.length)
        if (imageName.isEmpty() || dirPath.isEmpty()) {
            return null
        }

        val dir = VirtualFileManager.getInstance().findFileByUrl(dirPath)
        if (dir == null || !dir.isDirectory) {
            return null
        }

        for (child in dir.children) {
            if (!child.isDirectory) {
                if (getRelativeImgName(child.name) == imageName) {
                    return dirPath + "/" + child.name
                }
            }
        }
        return null
    }

    /**
     * 根据绝对图片名称获取相对图片名称
     */
    private fun getRelativeImgName(absoluteImageName: String): String {
        return absoluteImageName.replace("@1x", "")
            .replace("@2x", "")
            .replace("@3x", "")
            .replace("_android", "")
            .replace("_ios", "")
    }

    /**
     * 获取节点所处文件所在目录的绝对路径
     */
    private tailrec fun getAttachFileDirAbsolutePath(element: PsiElement?): String? {
        return when (element) {
            null -> {
                null
            }

            is PsiFile -> {
                element.virtualFile?.path?.replace("/${element.name}", "")
            }

            else -> {
                getAttachFileDirAbsolutePath(element.parent)
            }
        }
    }

    override fun getCustomDocumentationElement(
        editor: Editor, file: PsiFile, contextElement: PsiElement?, targetOffset: Int
    ): PsiElement? {
        val project = editor.project
        if (project != null && !XUtils.isRNProject(project)) {
            return null
        }

        // 需要特殊显示的节点，需要生成自定义节点返回，然后才会进入generateHoverDoc/generateDoc方法
        val imageElement = getCustomImageDocumentationElement(contextElement)
        if (imageElement != null) {
            return imageElement
        }

        return super.getCustomDocumentationElement(editor, file, contextElement, targetOffset)
    }

    /**
     * 创建图片自定义节点
     */
    private fun getCustomImageDocumentationElement(contextElement: PsiElement?): PsiElement? {
        if (contextElement == null) {
            return null
        }

        var typeValid = false
        var parent = contextElement.parent
        if (parent is JSReferenceExpression) {
            typeValid = true
        }

        if (!typeValid) {
            parent = parent?.parent?.parent
            if (parent is JSCallExpression) {
                typeValid = true
            }
        }

        if (!typeValid) {
            return null
        }

        val text = contextElement.text
        if (parent.textMatches("Images.${text}") || parent.textMatches("require(${text})")) {
            // 自定义节点，不能有JSDocComment ELEMENT,否侧不执行generateHoverDoc/generateDoc
            // 如果是简单的Document文档，可以直接使用JSDocComment,generateHoverDoc/generateDoc方法支持完整的html语法
            return JSPsiElementFactory.createJSStatement(IMAGE_ELEMENT_PREFIX + text, contextElement.parent)
        }
        return null
    }

    companion object {
        // 自定义图片标识
        const val IMAGE_ELEMENT_PREFIX = "ImageElement_"
    }
}
