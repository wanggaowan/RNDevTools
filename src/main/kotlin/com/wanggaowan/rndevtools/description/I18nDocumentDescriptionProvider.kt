package com.wanggaowan.rndevtools.description

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.lang.ecmascript6.psi.ES6ExportDefaultAssignment
import com.intellij.lang.ecmascript6.psi.ES6Property
import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.impl.JSPsiElementFactory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.wanggaowan.rndevtools.entity.Property
import com.wanggaowan.rndevtools.utils.XUtils

/**
 * 提供i18n文本文件描述
 *
 * @author Created by wanggaowan on 2022/5/2 13:30
 */
class I18nDocumentDescriptionProvider : DocumentationProvider {
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

        if (!element.text.startsWith(STRING_ELEMENT_PREFIX)) {
            return null
        }

        // 获得 a_label: 'a_label'  节点 或 'a_label'  节点
        // a_label: 'a_label' 是通过如：str.a_label引用，展示str.a_label引用资源
        // 'a_label' 是定义在str.ts中的属性，展示属性引用资源
        val strProperty =
            if (originalElement is LeafPsiElement) {
                if (originalElement.parent is JSReferenceExpression) {
                    originalElement.parent.reference?.resolve()
                } else {
                    originalElement
                }
            } else originalElement.parent?.reference?.resolve()

        if (strProperty !is JSProperty && strProperty !is LeafPsiElement) {
            return null
        }

        // 获取节点文本
        val value =
            (if (strProperty is LeafPsiElement) strProperty.text else (strProperty as JSProperty).value?.text)
                ?.replace(
                    "'",
                    ""
                )?.replace(
                    "\"",
                    ""
                )
        if (value.isNullOrEmpty()) {
            return null
        }

        val dirPath = getAttachFileDirPath(strProperty) ?: return null
        val dirFile = VirtualFileManager.getInstance().findFileByUrl("file://${dirPath}") ?: return null
        if (!dirFile.isDirectory) {
            return null
        }

        val propertyList = getStrDefineInDir(strProperty.project, dirFile, value)
        if (propertyList.isEmpty()) {
            return null
        }

        val builder = StringBuilder()
        propertyList.forEach {
            val keyBuilder = StringBuilder("<b>${it.key}</b>:")
            for (i in it.key.length / 2 until 6) {
                // 全角空格
                keyBuilder.append("　")
            }
            builder.append(keyBuilder.toString()).append(it.value).append("<br><br>")
        }
        return builder.toString()
    }

    private fun getStrDefineInDir(project: Project, dir: VirtualFile, key: String): List<Property> {
        val list = mutableListOf<Property>()
        for (child in dir.children) {
            if (child.isDirectory) {
                list.addAll(getStrDefineInDir(project, child, key))
            } else {
                val name = child.name
                if (name != "str.ts" && name != "str.js" && (name.contains(".ts") || name.contains(".js"))) {
                    getStrDefineInFile(project, child, key)?.let {
                        if (name.contains("default")) {
                            list.add(0, it)
                        } else {
                            list.add(it)
                        }
                    }
                }
            }
        }

        return list
    }

    private fun getStrDefineInFile(project: Project, file: VirtualFile, key: String): Property? {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null
        for (child in psiFile.children) {
            if (child is ES6ExportDefaultAssignment) {
                for (child2 in child.children) {
                    if (child2 is JSObjectLiteralExpression) {
                        val children = child2.children
                        if (children.isEmpty()) {
                            return null
                        }

                        val value = getStrDefineInJSObjectLiteralExpression(child2, key)
                        if (value != null) {
                            var fileName = file.name
                            val indexOf = fileName.lastIndexOf(".")
                            if (indexOf != -1) {
                                fileName = fileName.substring(0, indexOf)
                            }

                            return Property(fileName, value)
                        }

                        break
                    }
                }
                break
            }
        }
        return null
    }

    private fun getStrDefineInJSObjectLiteralExpression(element: JSObjectLiteralExpression, key: String): String? {
        for (child in element.children) {
            if (child is ES6Property) {
                val element2 = getValidRootElement(child)
                if (element2 != null) {
                    val str = getStrDefineInJSObjectLiteralExpression(element2, key)
                    if (str != null) {
                        return str
                    }
                }
            } else if (child is JSProperty) {
                if (key == child.name) {
                    return child.value?.text?.replace("'", "")?.replace("\"", "")
                }
            }
        }
        return null
    }

    private fun getValidRootElement(element: ES6Property): JSObjectLiteralExpression? {
        val es6Children = element.children
        if (es6Children.size == 1 && es6Children[0] is JSReferenceExpression) {
            val element2 = es6Children[0].reference?.resolve()
            if (element2 is JSVariable) {
                for (child2 in element2.children) {
                    if (child2 is JSObjectLiteralExpression) {
                        return child2
                    }
                }
            }
        }
        return null
    }

    /**
     * 获取节点所处文件所在目录的路径
     */
    private tailrec fun getAttachFileDirPath(element: PsiElement?): String? {
        return when (element) {
            null -> {
                null
            }

            is PsiFile -> {
                element.virtualFile?.path?.replace("/${element.name}", "")
            }

            else -> {
                getAttachFileDirPath(element.parent)
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
        val imageElement = getCustomStringDocumentationElement(contextElement)
        if (imageElement != null) {
            return imageElement
        }

        return super.getCustomDocumentationElement(editor, file, contextElement, targetOffset)
    }

    /**
     * 创建文本自定义节点
     */
    private fun getCustomStringDocumentationElement(contextElement: PsiElement?): PsiElement? {
        if (contextElement == null) {
            return null
        }

        var typeValid = false
        var parent = contextElement.parent
        if (parent is JSReferenceExpression) {
            typeValid = true
        }

        if (!typeValid) {
            parent = parent?.parent
            if (parent is JSProperty) {
                typeValid = true
            }
        }

        if (!typeValid) {
            return null
        }

        val text = contextElement.text
        if (parent.textMatches("str.${text}")) {
            // 自定义节点，不能有JSDocComment ELEMENT,否侧不执行generateHoverDoc/generateDoc
            // 如果是简单的Document文档，可以直接使用JSDocComment,generateHoverDoc/generateDoc方法支持完整的html语法
            return JSPsiElementFactory.createJSStatement(STRING_ELEMENT_PREFIX + text, contextElement.parent)
        }

        if (parent is JSProperty) {
            val value = parent.value ?: return null
            if (value.textMatches(text)) {
                return JSPsiElementFactory.createJSStatement(STRING_ELEMENT_PREFIX + text, contextElement.parent)
            }
        }

        return null
    }

    companion object {
        // 自定义图片标识
        const val STRING_ELEMENT_PREFIX = "StringElement_"
    }
}
