package com.wanggaowan.rndevtools.actions

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.lang.ecmascript6.psi.ES6ExportDefaultAssignment
import com.intellij.lang.ecmascript6.psi.ES6Property
import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.impl.JSChangeUtil
import com.intellij.lang.javascript.psi.impl.JSObjectLiteralExpressionImpl
import com.intellij.lang.javascript.psi.impl.JSPsiElementFactory
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.wanggaowan.rndevtools.entity.Property
import com.wanggaowan.rndevtools.utils.NotificationUtils


private val Log = logger<CreateStringsResourceAction>()

/**
 * 右键目录生成文本资源引用
 *
 * @author Created by wanggaowan on 2022/5/1 21:52
 */
class CreateStringsResourceAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        WriteCommandAction.runWriteCommandAction(event.project) {
            var defaultFile: VirtualFile? = null
            for (child in virtualFile.children) {
                if ("default.ts" == child.name || "default.js" == child.name) {
                    defaultFile = child
                    break
                }
            }

            if (defaultFile == null) {
                NotificationUtils.showBalloonMsg(project, "目录下不存在default.ts或default.js文件", NotificationType.ERROR)
            }

            val rootElement = getDefaultFileRootElement(project, defaultFile) ?: return@runWriteCommandAction
            createStrFile(project, virtualFile, rootElement)
        }
    }

    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isVisible = virtualFile != null && virtualFile.isDirectory
    }

    /**
     * 创建str.ts文件
     */
    private fun createStrFile(project: Project, rootDir: VirtualFile?, originalRootElement: JSObjectLiteralExpression) {
        if (rootDir == null) {
            return
        }

        var findChild = rootDir.findChild("str.ts")
        if (findChild == null) {
            createDefaultImages(project, rootDir)
        }

        findChild = rootDir.findChild("str.ts")
        if (findChild == null) {
            return
        }

        val changeFile: Set<Property> = getNewPropertyList(originalRootElement)
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

    private fun createDefaultImages(project: Project, rootDir: VirtualFile) {
        try {
            rootDir.createChildData(project, "str.ts")
        } catch (e: Exception) {
            Log.error(e)
        }
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

    /**
     * 获取字符属性列表
     */
    private fun getNewPropertyList(rootElement: JSObjectLiteralExpression): LinkedHashSet<Property> {
        val childrenSet = linkedSetOf<Property>()
        for (child in rootElement.children) {
            if (child is ES6Property) {
                val element = getValidRootElement(child)
                if (element != null) {
                    val list = getNewPropertyList(element)
                    childrenSet.addAll(list)
                }
            } else if (child is JSProperty) {
                child.name?.let {
                    childrenSet.add(Property(it, it))
                }
            }
        }

        return childrenSet
    }

    private fun getDefaultFileRootElement(project: Project, virtualFile: VirtualFile?): JSObjectLiteralExpression? {
        if (virtualFile == null) {
            return null
        }

        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return null
        for (child in psiFile.children) {
            if (child is ES6ExportDefaultAssignment) {
                for (child2 in child.children) {
                    if (child2 is JSObjectLiteralExpression) {
                        return child2
                    }
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
            val content = "\n${addProperty.key}: '${addProperty.value}'"
            val astNode = JSChangeUtil.createObjectLiteralPropertyFromText(content, parentElement)
            parentElement.addBefore(astNode, parentElement.lastChild) // 在结尾新增一个英文逗号","
            val child2 = JSChangeUtil.createCommaPsiElement(parentElement)
            parentElement.addBefore(child2, parentElement.lastChild)
        }
    }

    /**
     * 执行格式化
     *
     * @param project     项目对象
     * @param psiFile 需要格式化文件
     */
    private fun reformatFile(project: Project, psiFile: PsiFile) { // 尝试对文件进行格式化处理
        val processor = ReformatCodeProcessor(project, psiFile, null, false) // 执行处理
        processor.run()
    }
}
