package com.wanggaowan.rndevtools

import com.intellij.codeInsight.actions.AbstractLayoutCodeProcessor
import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.lang.ecmascript6.psi.ES6ExportDefaultAssignment
import com.intellij.lang.javascript.psi.JSElement
import com.intellij.lang.javascript.psi.JSExpression
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.lang.javascript.psi.impl.JSChangeUtil
import com.intellij.lang.javascript.psi.impl.JSObjectLiteralExpressionImpl
import com.intellij.lang.javascript.psi.impl.JSPsiElementFactory
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.elementType
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException


private val Log = logger<AutoCreateImagesResourceListener>()

/**
 * 自动生成图片资源文件(暂不使用)
 *
 * 存在问题：1. 目前项目打开时，一直处在inspect code无法自动关闭，手动关闭则导致此次自动生成代码无效
 * 2. 存在实际文档内容与当前PsiFile不一致，目前没找到如果实时同步，在AnAction actionPerformed下处理不需要额外同步
 *
 * @author Created by wanggaowan on 2022/4/16 19:55
 */
class AutoCreateImagesResourceListener(private val project: Project) : BulkFileListener {

    private var isInit = false

    override fun after(events: MutableList<out VFileEvent>) {
        super.after(events)
        WriteCommandAction.runWriteCommandAction(this.project) {
            if (isCreateImagesFile(events)) {
                isInit = true
                createImages(getImageRootFile(), null)
                return@runWriteCommandAction
            }

            if (!isInit) {
                isInit = true
                createImages(getImageRootFile(), null)
                return@runWriteCommandAction
            }

            val changes = getChangeImagesFile(events)
            if (changes.isEmpty()) {
                return@runWriteCommandAction
            }
            createImages(getImageRootFile(), events)
        }
    }

    private fun isCreateImagesFile(events: MutableList<out VFileEvent>): Boolean {
        for (event in events) {
            if (event is VFileCreateEvent || event is VFileCopyEvent) {
                val fileName: String? = if (event is VFileCreateEvent) {
                    event.file?.name
                } else {
                    (event as VFileCopyEvent).newChildName
                }

                if (fileName == "Images.ts") {
                    return true
                }
            }
        }
        return false
    }

    private fun getChangeImagesFile(events: MutableList<out VFileEvent>): MutableList<VFileEvent> {
        val changes = mutableListOf<VFileEvent>()
        events.forEach {
            if ((it is VFileCreateEvent || it is VFileDeleteEvent || it is VFilePropertyChangeEvent || it is VFileCopyEvent)
                && project.basePath != null && it.path.contains("${project.basePath}/app/res/imgs")
                && isImage(it.path)
            ) {
                changes.add(it)
            }
        }
        return changes
    }

    private fun isImage(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith("png") || lower.endsWith("jpg")
            || lower.endsWith("jpeg") || lower.endsWith("webp")
            || lower.endsWith("gif") || lower.endsWith("svg")
    }

    private fun getImageRootFile(): VirtualFile? {
        val basePath = project.basePath ?: return null
        val file = VirtualFileManager.getInstance().findFileByUrl("file://${basePath}/app/res/imgs") ?: return null
        return if (file.isDirectory) {
            file
        } else {
            null
        }
    }

    private fun createImages(rootDir: VirtualFile?, events: List<VFileEvent>?) {
        if (rootDir == null || rootDir.children.isNullOrEmpty()) {
            return
        }

        val findChild = rootDir.findChild("Images.ts")
        if (findChild == null) {
            Log.info("创建Images.ts")
            createDefaultImages(rootDir)
            return
        }

        val changeFile: Set<ChangeFile> = if (events == null) {
            getDeDuplicationList(rootDir)
        } else {
            getDeDuplicationList(events)
        }

        if (changeFile.isEmpty()) {
            return
        }

        Log.info("changeFile:$changeFile")
        val psiFile = PsiManager.getInstance(this.project).findFile(findChild) ?: return
        val rootElement = getRootElement(psiFile)
        try {
            if (rootElement == null) {
                val export = JSPsiElementFactory.createJSStatement("export default ", psiFile)
                val jsObject = JSObjectLiteralExpressionImpl(JSPsiElementFactory.createJSStatement("{}", export).node)
                insertOrRemoveImagesContent(jsObject, changeFile)

                export.addAfter(jsObject, export.lastChild)
                export.addAfter(JSPsiElementFactory.createJSSourceElement(";", export), jsObject)
                psiFile.addAfter(export, psiFile.lastChild)
            } else if (rootElement is ES6ExportDefaultAssignment) {
                val jsObject =
                    JSObjectLiteralExpressionImpl(JSPsiElementFactory.createJSStatement("{}", rootElement).node)
                insertOrRemoveImagesContent(jsObject, changeFile)

                if (rootElement.lastChild.text == ";") {
                    rootElement.addBefore(jsObject, rootElement.lastChild)
                } else {
                    rootElement.addAfter(jsObject, rootElement.lastChild)
                }
            } else {
                insertOrRemoveImagesContent(rootElement, changeFile)
            }
            reformatFile(this.project, psiFile)
        } catch (e: Exception) {
            psiFile.clearCaches()
        }
    }

    private fun getRootElement(psiFile: PsiFile): JSElement? {
        for (child in psiFile.children) {
            if (child is ES6ExportDefaultAssignment) {
                Log.info("检测到ES6ExportDefaultAssignment")
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

    private fun createDefaultImages(rootDir: VirtualFile) {
        try {
            isInit = false
            rootDir.createChildData(this.project, "Images.ts")
        } catch (e: Exception) {
            Log.error(e)
        }
    }

    private fun initDefaultImagesContent(images: VirtualFile): Boolean {
        val psiFile = PsiManager.getInstance(this.project).findFile(images) ?: return false
        val element = JSPsiElementFactory.createJSStatement("export default {};", psiFile)
        psiFile.addAfter(element, psiFile.lastChild)
        return true
    }

    private fun insertOrRemoveImagesContent(
        parentElement: JSElement,
        changeFile: Set<ChangeFile>
    ) {

        Log.info("开始文档操作")
        changeFile.forEach {
            when (it.op) {
                ChangeFile.OP_ADD -> {
                    addJSProperty(parentElement, it)
                }

                ChangeFile.OP_DELETE -> {
                    deleteJSProperty(parentElement, it)
                }

                ChangeFile.OP_RENAME -> {
                    renameJSProperty(parentElement, it)
                }
            }
        }
    }

    private fun addJSProperty(
        parentElement: JSElement,
        addFile: ChangeFile
    ) {
        Log.info("call addJSProperty:" + addFile.name)
        val match = getPropertyKey(addFile.name)
        val find = parentElement.children.find { child -> child is JSProperty && child.name == match }
        if (find == null) {
            val content = "\n${match}: require('./${addFile.name}')"
            val astNode = JSChangeUtil.createObjectLiteralPropertyFromText(content, parentElement)
            parentElement.addBefore(astNode, parentElement.lastChild)
            val child2 = JSChangeUtil.createCommaPsiElement(parentElement)
            parentElement.addBefore(child2, parentElement.lastChild)
        }
    }

    private fun deleteJSProperty(parentElement: JSElement, deleteFile: ChangeFile) {
        val match = getPropertyKey(deleteFile.name)
        val find = parentElement.children.find { child -> child is JSProperty && child.name == match } ?: return
        WriteCommandAction.runWriteCommandAction(this.project) {
            find.delete()
        }
    }

    private fun renameJSProperty(parentElement: JSElement, renameFile: ChangeFile) {
        if (renameFile.oldName == null) {
            addJSProperty(parentElement, ChangeFile(renameFile.name))
            return
        }

        val oldMatch = getPropertyKey(renameFile.oldName)
        val find = parentElement.children.find { child -> child is JSProperty && child.name == oldMatch }
        if (find == null) {
            addJSProperty(parentElement, ChangeFile(renameFile.name))
            return
        }

        val match = getPropertyKey(renameFile.name)
        (find as JSProperty).let {
            for (child in find.children) {
                if (child.elementType?.toString() == "JS:IDENTIFIER"
                    || child.elementType?.toString() == "JS:COLON"
                ) {
                    child.delete()
                } else if (child is JSExpression) {
                    child.delete()
                }
            }

            val newElement =
                JSPsiElementFactory.createParameterOrVariableItem(
                    "${match}: require('./${renameFile.name}')",
                    it,
                    false,
                    false
                )
            it.addAfter(newElement, it.lastChild)
        }
    }

    /**
     * 获取去重后的文件名称
     */
    private fun getDeDuplicationList(rootDir: VirtualFile): LinkedHashSet<ChangeFile> {
        val childrenSet = linkedSetOf<ChangeFile>()
        for (child in rootDir.children) {
            if (isImage(child.name)) {
                val name = getPropertyValue(child.name)
                childrenSet.add(ChangeFile(name))
            }
        }

        return childrenSet
    }

    private fun getPropertyKey(value: String): String {
        return value.subSequence(0, value.lastIndexOf(".")).toString()
    }

    private fun getPropertyValue(value: String): String {
        return value.replace("@1x", "")
            .replace("@2x", "")
            .replace("@3x", "")
            .replace("_android", "")
            .replace("_ios", "")
    }

    /**
     * 获取去重后的文件名称
     */
    private fun getDeDuplicationList(events: List<VFileEvent>): LinkedHashSet<ChangeFile> {
        val childrenSet = linkedSetOf<ChangeFile>()
        for (child in events) {
            if (child.file == null) {
                continue
            }

            var name = child.file!!.name
            if (isImage(name)) {
                when (child) {
                    is VFileCreateEvent -> {
                        name = getPropertyValue(name)
                        childrenSet.add(ChangeFile((name)))
                    }

                    is VFileCopyEvent -> {
                        val newChildName = getPropertyValue(child.newChildName)
                        childrenSet.add(ChangeFile((newChildName)))
                    }

                    is VFileDeleteEvent -> {
                        name = getPropertyValue(name)
                        val rootDir = getImageRootFile()
                        var exist = false
                        if (rootDir != null) {
                            for (child2 in rootDir.children) {
                                val name2 = getPropertyValue(child2.name)
                                if (name == name2) {
                                    exist = true
                                }
                            }
                        }

                        if (!exist) {
                            childrenSet.add(ChangeFile(name, ChangeFile.OP_DELETE))
                        }
                    }

                    is VFilePropertyChangeEvent -> {
                        val oldName = getPropertyValue(child.oldValue.toString())
                        name = getPropertyValue(name)
                        childrenSet.add(
                            ChangeFile(
                                name,
                                ChangeFile.OP_RENAME,
                                oldName
                            )
                        )
                    }
                }

            }
        }

        return childrenSet
    }

    /**
     * 执行格式化
     *
     * @param project     项目对象
     * @param psiFile 需要格式化文件
     */
    @Suppress("UNCHECKED_CAST")
    private fun reformatFile(project: Project?, psiFile: PsiFile) {
        // 尝试对文件进行格式化处理
        var processor: AbstractLayoutCodeProcessor =
            ReformatCodeProcessor(project, psiFile, null, false)
        // 优化导入，有时候会出现莫名其妙的问题，暂时关闭
        // processor = new OptimizeImportsProcessor(processor);
        // 重新编排代码（会将代码中的属性与方法的顺序进行重新调整）
        // processor = new RearrangeCodeProcessor(processor);

        // 清理代码，进行旧版本兼容，旧版本的IDEA尚未提供该处理器
        try {
            val codeCleanupCodeProcessorCls =
                Class.forName("com.intellij.codeInsight.actions.CodeCleanupCodeProcessor") as Class<AbstractLayoutCodeProcessor>
            val constructor: Constructor<AbstractLayoutCodeProcessor> = codeCleanupCodeProcessorCls.getConstructor(
                AbstractLayoutCodeProcessor::class.java
            )
            processor = constructor.newInstance(processor)
        } catch (ignored: ClassNotFoundException) {
            // 类不存在直接忽略
        } catch (e: NoSuchMethodException) {
            // 抛出未知异常
            Log.error(e)
        } catch (e: IllegalAccessException) {
            Log.error(e)
        } catch (e: InstantiationException) {
            Log.error(e)
        } catch (e: InvocationTargetException) {
            Log.error(e)
        }
        // 执行处理
        processor.run()
    }

    /**
     * 改变的文件
     */
    private data class ChangeFile(val name: String, val op: Int = OP_ADD, val oldName: String? = null) {
        companion object {
            const val OP_DELETE = 0
            const val OP_ADD = 1
            const val OP_RENAME = 2
        }
    }
}
