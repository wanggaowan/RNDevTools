package com.wanggaowan.rndevtools.actions

import com.intellij.lang.ecmascript6.psi.ES6ExportDefaultAssignment
import com.intellij.lang.javascript.psi.JSElement
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.lang.javascript.psi.impl.JSChangeUtil
import com.intellij.lang.javascript.psi.impl.JSObjectLiteralExpressionImpl
import com.intellij.lang.javascript.psi.impl.JSPsiElementFactory
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.codeStyle.CodeStyleManagerImpl
import com.wanggaowan.rndevtools.entity.Property
import com.wanggaowan.rndevtools.utils.XUtils

private val Log = logger<CreateImagesResourceAction>()

/**
 * 右键目录生成图片资源引用
 *
 * @author Created by wanggaowan on 2022/4/15 09:09
 */
class CreateImagesResourceAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        var virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        if (!virtualFile.isDirectory) {
            virtualFile = virtualFile.parent ?: return
            if (!virtualFile.isDirectory) {
                return
            }
        }

        WriteCommandAction.runWriteCommandAction(event.project) {
            createImages(project, virtualFile)
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        if (!XUtils.isRNProject(project)) {
            e.presentation.isVisible = false
            return
        }

        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (virtualFile == null) {
            e.presentation.isVisible = false
            return
        }

        if (virtualFile.isDirectory) {
            e.presentation.isVisible = true
            return
        }

        val parent = virtualFile.parent
        if (parent != null && parent.isDirectory) {
            e.presentation.isVisible = true
            return
        }

        e.presentation.isVisible = false
    }

    private fun isImage(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith("png")
            || lower.endsWith("jpg")
            || lower.endsWith("jpeg")
            || lower.endsWith("webp")
            || lower.endsWith("gif")
            || lower.endsWith("svg")
    }

    /**
     * 创建Images.ts文件
     */
    private fun createImages(project: Project, rootDir: VirtualFile?) {
        if (rootDir == null) {
            return
        }

        var findChild = rootDir.findChild("Images.ts")
        if (findChild == null) {
            createDefaultImages(project, rootDir)
        }

        findChild = rootDir.findChild("Images.ts")
        if (findChild == null) {
            return
        }

        val changeFile: Set<Property> = getDeDuplicationList(rootDir)
        if (changeFile.isEmpty()) {
            return
        }

        val psiFile = PsiManager.getInstance(project).findFile(findChild) ?: return
        val rootElement = getRootElement(psiFile)
        if (rootElement == null) {
            val export = JSPsiElementFactory.createJSStatement("export default ", psiFile)
            val jsObject = JSObjectLiteralExpressionImpl(JSPsiElementFactory.createJSStatement("{}", export).node)
            insertOrRemoveImagesContent(jsObject, changeFile)

            export.addAfter(jsObject, export.lastChild)
            export.addAfter(JSPsiElementFactory.createJSSourceElement(";", export), jsObject)
            psiFile.addAfter(export, psiFile.lastChild)
        } else if (rootElement is ES6ExportDefaultAssignment) {
            val jsObject = JSPsiElementFactory.createJSStatement("{}", rootElement)
            insertOrRemoveImagesContent(jsObject, changeFile)

            if (rootElement.lastChild.text == ";") {
                rootElement.addBefore(jsObject, rootElement.lastChild)
            } else {
                rootElement.addAfter(jsObject, rootElement.lastChild)
            }
        } else {
            insertOrRemoveImagesContent(rootElement, changeFile)
        }
        reformatFile(project, psiFile)
    }

    private fun getRootElement(psiFile: PsiFile): JSElement? {
        for (child in psiFile.children) {
            if (child is ES6ExportDefaultAssignment) {
                for (child2 in child.children) {
                    if (child2 is JSObjectLiteralExpression) {
                        return child2
                    }
                }

                return child
            }
        }

        return null
    }

    private fun createDefaultImages(project: Project, rootDir: VirtualFile) {
        try {
            rootDir.createChildData(project, "Images.ts")
        } catch (e: Exception) {
            Log.error(e)
        }
    }

    private fun insertOrRemoveImagesContent(parentElement: JSElement, changeProperty: Set<Property>) {
        // 移除不存在的数据
        parentElement.children.forEach {
            if (it is JSProperty) {
                val name = it.name
                var exist = false
                for (property in changeProperty) {
                    if (name == property.key) {
                        exist = true
                        break
                    }
                }

                if (!exist) {
                    it.delete()
                }

            }
        }

        changeProperty.forEach {
            addJSProperty(parentElement, it)
        }
    }

    private fun addJSProperty(parentElement: JSElement, addProperty: Property) {
        val value = parentElement.children.find { child -> child is JSProperty && child.name == addProperty.key }
        if (value == null) {
            val content = "\n${addProperty.key}: require('./${addProperty.value}')"
            val astNode = JSChangeUtil.createObjectLiteralPropertyFromText(content, parentElement)
            parentElement.addBefore(astNode, parentElement.lastChild)
            // 在结尾新增一个英文逗号","
            val child2 = JSChangeUtil.createCommaPsiElement(parentElement)
            parentElement.addBefore(child2, parentElement.lastChild)
        }
    }

    /**
     * 获取去重后的属性列表
     */
    private fun getDeDuplicationList(rootDir: VirtualFile, parentPath: String = ""): LinkedHashSet<Property> {
        val childrenSet = linkedSetOf<Property>()
        for (child in rootDir.children) {
            if (child.isDirectory) {
                childrenSet.addAll(getDeDuplicationList(child, "$parentPath${child.name}/"))
            } else if (isImage(child.name)) {
                val value = parentPath + getPropertyValue(child.name)
                val key = getPropertyKey(value)
                childrenSet.add(Property(key, value))
            }
        }

        return childrenSet
    }

    private fun getPropertyKey(value: String): String {
        return value.substring(0, value.lastIndexOf(".")).replace("/", "_")
    }

    private fun getPropertyValue(value: String): String {
        return value.replace("@1x", "")
            .replace("@2x", "")
            .replace("@3x", "")
            .replace("_android", "")
            .replace("_ios", "")
    }

    /**
     * 执行格式化
     *
     * @param project     项目对象
     * @param psiFile 需要格式化文件
     */
    private fun reformatFile(project: Project, psiFile: PsiFile) {
        CodeStyleManagerImpl(project).reformatText(psiFile, mutableListOf(TextRange(0, psiFile.textLength)))
    }
}
