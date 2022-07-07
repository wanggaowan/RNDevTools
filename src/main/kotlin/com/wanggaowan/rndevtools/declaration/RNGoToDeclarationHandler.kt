package com.wanggaowan.rndevtools.declaration

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement

/**
 * 资源定位
 *
 * @author Created by wanggaowan on 2022/5/2 12:59
 */
class RNGoToDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?, offset: Int, editor: Editor?
    ): Array<PsiElement>? {
        var targets = I18nGoToDeclarationHandler.getGotoDeclarationTargets(sourceElement, offset, editor)
        if (targets != null) {
            return targets
        }

        targets = ImagesGoToDeclarationHandler.getGotoDeclarationTargets(sourceElement, offset, editor)
        if (targets != null) {
            return targets
        }

        return null
    }
}
