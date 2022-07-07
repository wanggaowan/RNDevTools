package com.wanggaowan.rndevtools.declaration

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
 * 文本资源定位
 *
 * @author Created by wanggaowan on 2022/7/7 14:49
 */
object I18nGoToDeclarationHandler {
    fun getGotoDeclarationTargets(
        sourceElement: PsiElement?, offset: Int, editor: Editor?
    ): Array<PsiElement>? {
        if (sourceElement == null || sourceElement !is LeafPsiElement) {
            return null
        }

        val elementType = sourceElement.elementType.toString()
        if (elementType != "JS:IDENTIFIER" && elementType != "JS:STRING_LITERAL") {
            return null
        }

        var parent = sourceElement.parent
        val strProperty = if (parent is JSReferenceExpression) {
            // 解析格式：str.test
            val text = sourceElement.text
            if (parent.textMatches("str.${text}")) {
                val reference = parent.reference?.resolve()
                if (reference is JSProperty) {
                    reference
                } else null
            } else null
        } else {
            // 解析的格式如： test: '测试数据'
            // 此种格式仅解析str中的文本
            parent = parent?.parent
            if (parent is JSProperty) {
                parent
            } else null
        }

        if (strProperty == null) {
            return null
        }

        // 获取节点文本
        val value = strProperty.value?.text?.replace(
            "'", ""
        )?.replace(
            "\"", ""
        )

        if (value.isNullOrEmpty()) {
            return null
        }

        val dirPath = getAttachFileDirPath(strProperty, elementType == "JS:STRING_LITERAL") ?: return null
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
     * @param isStringLiteral 是否是纯文本数据，类似： '文本数据'
     */
    private tailrec fun getAttachFileDirPath(element: PsiElement?, isStringLiteral: Boolean): String? {
        return when (element) {
            null -> {
                null
            }

            is PsiFile -> {
                val name = element.name
                if (isStringLiteral) {
                    if (name == "str.ts" || name == "str.js") {
                        element.virtualFile?.path?.replace("/$name", "")
                    } else null
                } else element.virtualFile?.path?.replace("/${element.name}", "")
            }

            else -> {
                getAttachFileDirPath(element.parent, isStringLiteral)
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
