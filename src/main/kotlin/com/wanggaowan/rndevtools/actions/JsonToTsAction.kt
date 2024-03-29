package com.wanggaowan.rndevtools.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.wanggaowan.rndevtools.ui.JsonToTsDialog
import com.wanggaowan.rndevtools.utils.XUtils


/**
 * JSON文件转Ts class，interface，type
 *
 * @author Created by wanggaowan on 2022/5/7 11:30
 */
class JsonToTsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
        var psiElement = e.getData(CommonDataKeys.PSI_ELEMENT)
        if (psiElement == null) {
            val editor = e.getData(CommonDataKeys.EDITOR)
            editor?.let {
                psiElement = findElementAtOffset(psiFile, it.selectionModel.selectionStart)
            }
        }

        val dialog = JsonToTsDialog(project, psiFile, psiElement)
        dialog.isVisible = true
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        if (!XUtils.isRNProject(project)) {
            e.presentation.isVisible = false
            return
        }

        val editor = e.getData(CommonDataKeys.EDITOR)
        if (editor == null) {
            e.presentation.isVisible = false
            return
        }

        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (virtualFile != null && !virtualFile.isDirectory) {
            e.presentation.isVisible = virtualFile.name.endsWith(".ts")
            return
        }

        e.presentation.isVisible = true
    }

    /**
     * 查找指定下标位置element，如果找不到则往前一位查找，直到下标<0
     */
    private tailrec fun findElementAtOffset(psiFile: PsiFile, offset: Int): PsiElement? {
        if (offset < 0) {
            return null
        }
        val element = psiFile.findElementAt(offset)
        if (element != null) {
            return element
        }

        return findElementAtOffset(psiFile, offset - 1)
    }
}
