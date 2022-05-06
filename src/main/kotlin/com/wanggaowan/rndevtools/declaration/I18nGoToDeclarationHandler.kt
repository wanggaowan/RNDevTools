package com.wanggaowan.rndevtools.declaration

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.lang.ecmascript6.psi.ES6ExportDefaultAssignment
import com.intellij.lang.ecmascript6.psi.ES6Property
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.JSVariable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.tree.LeafPsiElement

/**
 * i18n文本内容跳转到定义处
 *
 * @author Created by wanggaowan on 2022/5/2 12:59
 */
class I18nGoToDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?
    ): Array<PsiElement>? {
        if (sourceElement == null) {
            return null
        }

        val strProperty =
            if (sourceElement is LeafPsiElement) {
                if (sourceElement.parent is JSReferenceExpression) {
                    sourceElement.parent.reference?.resolve()
                } else {
                    sourceElement
                }
            } else sourceElement.parent.reference?.resolve()

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

        return propertyList.toTypedArray()
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
                element.virtualFile.path.replace("/${element.name}", "")
            }

            else -> {
                getAttachFileDirPath(element.parent)
            }
        }
    }

    private fun getStrDefineInDir(project: Project, dir: VirtualFile, key: String): List<PsiElement> {
        val list = mutableListOf<PsiElement>()
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

    private fun getStrDefineInFile(project: Project, file: VirtualFile, key: String): PsiElement? {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null
        for (child in psiFile.children) {
            if (child is ES6ExportDefaultAssignment) {
                for (child2 in child.children) {
                    if (child2 is JSObjectLiteralExpression) {
                        val children = child2.children
                        if (children.isEmpty()) {
                            return null
                        }

                        return getStrDefineInJSObjectLiteralExpression(child2, key)
                    }
                }
                break
            }
        }
        return null
    }

    private fun getStrDefineInJSObjectLiteralExpression(element: JSObjectLiteralExpression, key: String): PsiElement? {
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
                    return child
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
}
