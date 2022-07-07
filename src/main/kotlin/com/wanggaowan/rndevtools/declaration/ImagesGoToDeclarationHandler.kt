package com.wanggaowan.rndevtools.declaration

import com.intellij.lang.javascript.psi.JSArgumentList
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.tree.LeafPsiElement

/**
 * 图片资源定位
 *
 * @author Created by wanggaowan on 2022/7/7 14:49
 */
object ImagesGoToDeclarationHandler {
    fun getGotoDeclarationTargets(
        sourceElement: PsiElement?, offset: Int, editor: Editor?
    ): Array<PsiElement>? {
        if (sourceElement == null || sourceElement !is LeafPsiElement) {
            return null
        }

        val elementType = sourceElement.elementType.toString()
        if (elementType != "JS:STRING_LITERAL") {
            return null
        }

        var parent = sourceElement.parent
        if (parent !is JSLiteralExpression) {
            return null
        }

        parent = parent.parent
        if (parent !is JSArgumentList) {
            return null
        }

        parent = parent.parent
        if (parent !is JSCallExpression) {
            return null
        }

        val text = parent.text
        if (text.isNullOrEmpty() || !text.startsWith("require")) {
            return null
        }

        val fileName = sourceElement.text.replace("'", "").replace("\"", "")
        val dirFile: VirtualFile = if (fileName.startsWith(".")) {
            val dirPath = getAttachFileDirPath(parent) ?: return null
            if (fileName.startsWith("./")) {
                getActualFile(fileName.replace("./", ""), dirPath) ?: return null
            } else {
                getActualFile(fileName, dirPath) ?: return null
            }
        } else {
            VirtualFileManager.getInstance().findFileByUrl("file://${fileName}") ?: return null
        }

        val psiFile = PsiManager.getInstance(sourceElement.project).findFile(dirFile) ?: return null
        return arrayOf(psiFile)
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

    /**
     * 获取图片实际路径
     */
    private fun getActualFile(relativePath: String, dirPath: String): VirtualFile? {
        val indexOf = relativePath.lastIndexOf(".")
        if (indexOf == -1) {
            return VirtualFileManager.getInstance().findFileByUrl("file://${dirPath}/${relativePath}")
        }

        val name = relativePath.substring(0, indexOf)
        val suffix = relativePath.substring(indexOf)
        val typeList1 = arrayOf("", "_android", "_ios")
        val typeList2 = arrayOf("", "@1x", "@2x", "@3x")
        for (type1 in typeList1) {
            for (type2 in typeList2) {
                val file = VirtualFileManager.getInstance().findFileByUrl("file://${dirPath}/$name$type1$type2$suffix")
                if (file != null) {
                    return file
                }
            }
        }

        return null
    }
}
