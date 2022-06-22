package com.wanggaowan.rndevtools.actions

import com.intellij.lang.ecmascript6.psi.ES6ExportDefaultAssignment
import com.intellij.lang.ecmascript6.psi.ES6Property
import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.impl.JSChangeUtil
import com.intellij.lang.javascript.psi.impl.JSObjectLiteralExpressionImpl
import com.intellij.lang.javascript.psi.impl.JSPsiElementFactory
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.codeStyle.CodeStyleManagerImpl
import com.wanggaowan.rndevtools.entity.Property
import com.wanggaowan.rndevtools.utils.msg.MessageUtils

private val Log = logger<CreateStringsResource>()

/**
 * i18n多语言，生成一份用于外部引用的文本资源文件
 *
 * @author Created by wanggaowan on 2022/6/17 11:28
 */
object CreateStringsResource {

    fun create(project: Project, directory: VirtualFile) {
        var defaultFile: VirtualFile? = null
        for (child in directory.children) {
            if ("default.ts" == child.name || "default.js" == child.name) {
                defaultFile = child
                break
            }
        }

        if (defaultFile == null) {
            MessageUtils.show("目录下不存在 default.ts 或 default.js 文件", MessageType.ERROR, project = project)
            return
        }

        WriteCommandAction.runWriteCommandAction(project) {
            val rootElement = getDefaultFileRootElement(project, defaultFile) ?: return@runWriteCommandAction
            createStrFile(project, directory, rootElement)
        }
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
            parentElement.addBefore(astNode, parentElement.lastChild)
            // 在结尾新增一个英文逗号","
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
    private fun reformatFile(project: Project, psiFile: PsiFile) {
        CodeStyleManagerImpl(project).reformatText(psiFile, mutableListOf(TextRange(0, psiFile.textLength)))
    }
}
