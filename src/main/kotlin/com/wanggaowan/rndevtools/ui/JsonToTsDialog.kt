package com.wanggaowan.rndevtools.ui

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.intellij.lang.javascript.psi.JSBlockStatement
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass
import com.intellij.lang.javascript.psi.ecma6.TypeScriptInterface
import com.intellij.lang.javascript.psi.ecma6.TypeScriptObjectType
import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeAlias
import com.intellij.lang.javascript.psi.impl.JSPsiElementFactory
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.codeStyle.CodeStyleManagerImpl
import com.intellij.psi.util.PsiTreeUtil
import com.wanggaowan.rndevtools.utils.StringUtils
import com.wanggaowan.rndevtools.utils.msg.Toast
import java.awt.Point
import javax.swing.*

/**
 * json文件转RN对象
 */
class JsonToTsDialog(
    private val project: Project,
    private val psiFile: PsiFile,
    private val selectElement: PsiElement?
) : JDialog() {

    private lateinit var mRootPanel: JPanel
    private lateinit var mCreateObjectName: JTextField
    private lateinit var mCreateObjectType: JComboBox<String>
    private lateinit var mEtJsonContent: JTextArea
    private lateinit var mBtOk: JButton
    private lateinit var mBtCancel: JButton
    private lateinit var mCbCreateDoc: JCheckBox
    private lateinit var mObjSuffix: JTextField

    // 插入位置根节点
    private var mRootElement: PsiElement? = null

    init {
        contentPane = mRootPanel
        getRootPane().defaultButton = mBtOk
        this.isAlwaysOnTop = true
        pack()
        initData()
        initEvent()
    }

    override fun setVisible(b: Boolean) {
        if (b) {
            val window = WindowManager.getInstance().suggestParentWindow(project)
            window?.let {
                location = Point(it.x + (it.width - this.width) / 2, it.y + (it.height - this.height) / 2)
            }
        }
        super.setVisible(b)
    }

    private fun initData() {
        if (selectElement == null) {
            mCreateObjectName.isEnabled = true
            mCreateObjectType.isEnabled = true
            return
        }

        if (selectElement is TypeScriptClass || selectElement is TypeScriptInterface || selectElement is TypeScriptTypeAlias) {
            mCreateObjectName.isEnabled = false
            mCreateObjectType.isEnabled = false
            mRootElement = selectElement
            when (selectElement) {
                is TypeScriptClass -> {
                    mCreateObjectName.text = selectElement.name
                    mCreateObjectType.selectedIndex = 0
                }

                is TypeScriptInterface -> {
                    mCreateObjectName.text = selectElement.name
                    mCreateObjectType.selectedIndex = 1
                }

                else -> {
                    mCreateObjectName.text = (selectElement as TypeScriptTypeAlias).name
                    mCreateObjectType.selectedIndex = 2
                }
            }
            return
        }

        val element = PsiTreeUtil.getParentOfType(selectElement, TypeScriptClass::class.java)
        if (element != null) {
            mRootElement = element
            mCreateObjectName.isEnabled = false
            mCreateObjectType.isEnabled = false
            mCreateObjectName.text = element.name
            mCreateObjectType.selectedIndex = 0
            return
        }

        val interfaceElement = PsiTreeUtil.getParentOfType(selectElement, TypeScriptInterface::class.java)
        if (interfaceElement != null) {
            mRootElement = interfaceElement
            mCreateObjectName.isEnabled = false
            mCreateObjectType.isEnabled = false
            mCreateObjectName.text = interfaceElement.name
            mCreateObjectType.selectedIndex = 1
            return
        }

        val typeElement = PsiTreeUtil.getParentOfType(selectElement, TypeScriptTypeAlias::class.java)
        if (typeElement != null) {
            mRootElement = typeElement
            mCreateObjectName.isEditable = false
            mCreateObjectType.isEditable = false
            mCreateObjectName.text = typeElement.name
            mCreateObjectType.selectedIndex = 2
            return
        }

        mCreateObjectName.isEditable = true
        mCreateObjectType.isEditable = true
    }

    private fun initEvent() {
        mBtCancel.addActionListener {
            isVisible = false
        }

        mBtOk.addActionListener {
            val objName = mCreateObjectName.text
            if (objName.isNullOrEmpty()) {
                Toast.show(mCreateObjectName, MessageType.ERROR, "请输入要创建的对象名称")
                return@addActionListener
            }

            val jsonStr = mEtJsonContent.text
            if (jsonStr.isNullOrEmpty()) {
                Toast.show(mEtJsonContent, MessageType.ERROR, "请输入JSON内容")
                return@addActionListener
            }

            val jsonObject: JsonObject?
            try {
                jsonObject = Gson().fromJson(jsonStr, JsonObject::class.java)
                isVisible = false
            } catch (e: Exception) {
                Toast.show(mEtJsonContent, MessageType.ERROR, "JSON数据格式不正确")
                return@addActionListener
            }


            WriteCommandAction.runWriteCommandAction(project) {
                if (mRootElement == null) {
                    val element = createParentElement(objName, psiFile)
                    val lastChild = psiFile.lastChild
                    findParentElement(element)?.let {
                        createRnObjectOnJsonObject(jsonObject, it)
                    }
                    psiFile.addAfter(element, lastChild)
                } else {
                    mRootElement?.let {
                        if (it is TypeScriptClass) {
                            createRnObjectOnJsonObject(jsonObject, it)
                            return@let
                        }

                        val element = findParentElement(it)
                        if (element == null) {
                            val block = JSPsiElementFactory.createJSStatement("{}", it)
                            createRnObjectOnJsonObject(jsonObject, block)
                            it.addAfter(block, it.lastChild)
                        } else {
                            createRnObjectOnJsonObject(jsonObject, element)
                        }
                    }
                }
                reformatFile(project, psiFile)
            }
        }
    }

    /**
     * 创建将json属性新增到rn对象的父属性
     */
    private fun createParentElement(name: String, parentElement: PsiElement): PsiElement {
        // 此处创建的对象非标准的TypeScriptClass，TypeScriptInterface，TypeScriptTypeAlias
        // findParentElement中查找此处创建的JSBlockStatement对象，如果是标准对象，TypeScriptClass根节点则是自己本身
        // TypeScriptInterface，TypeScriptTypeAlias都是TypeScriptObjectType
        val suffix = mObjSuffix.text.trim()
        when (mCreateObjectType.selectedIndex) {
            0 -> {
                val element = JSPsiElementFactory.createJSClass("class ${name}${suffix} ", parentElement)
                val block = JSPsiElementFactory.createJSStatement("{}", element)
                element.addAfter(block, element.lastChild)
                return element
            }
            1 -> {
                val element = JSPsiElementFactory.createJSClass("interface ${name}${suffix} ", parentElement)
                val block = JSPsiElementFactory.createJSStatement("{}", element)
                element.addAfter(block, element.lastChild)
                return element
            }
            else -> {
                val element = JSPsiElementFactory.createJSClass("type ${name}${suffix} = ", parentElement)
                val block = JSPsiElementFactory.createJSStatement("{}", element)
                element.addAfter(block, element.lastChild)
                return element
            }
        }
    }

    /**
     * 查找将json属性新增到rn对象的父属性
     */
    private fun findParentElement(parentElement: PsiElement): PsiElement? {
        for (child in parentElement.children) {
            when (child) {
                is TypeScriptObjectType -> {
                    return child
                }

                is JSBlockStatement -> {
                    return child
                }
            }
        }

        return null
    }

    private fun createRnObjectOnJsonObject(jsonObject: JsonObject, parentElement: PsiElement) {
        jsonObject.keySet().forEach {
            val obj = jsonObject.get(it)
            if (obj == null || obj.isJsonNull) {
                addTsField(it, null, parentElement)
            } else if (obj.isJsonPrimitive) {
                addTsField(it, obj as JsonPrimitive, parentElement)
            } else if (obj.isJsonObject) {
                val className = StringUtils.toHumpFormat(it)
                val suffix = mObjSuffix.text.trim()
                addTsFieldForObjType(it, className + suffix, false, parentElement)

                val element = createParentElement(className, psiFile)
                val lastChild = psiFile.lastChild
                findParentElement(element)?.let { root ->
                    createRnObjectOnJsonObject(obj as JsonObject, root)
                }
                psiFile.addAfter(element, lastChild)
            } else if (obj.isJsonArray) {
                val className = StringUtils.toHumpFormat(it)
                obj.asJsonArray.let { jsonArray ->
                    if (jsonArray.size() == 0) {
                        addTsFieldForObjType(it, "any", true, parentElement)
                        return@let
                    }

                    var typeSame = true
                    var type: String? = null
                    var isCreateObjChild = false
                    jsonArray.forEach { child ->
                        if (child.isJsonObject) {
                            if (type == null) {
                                type = "JsonObject"
                            } else {
                                typeSame = "JsonObject" == type
                            }

                            if (!isCreateObjChild) {
                                isCreateObjChild = true
                                val element = createParentElement(className, psiFile)
                                val lastChild = psiFile.lastChild
                                findParentElement(element)?.let { root ->
                                    createRnObjectOnJsonObject(child as JsonObject, root)
                                }
                                psiFile.addAfter(element, lastChild)
                            }
                        } else if (child.isJsonPrimitive) {
                            val typeName = child.asJsonPrimitive.let { primitive ->
                                if (primitive.isNumber) {
                                    "number"
                                } else if (primitive.isBoolean) {
                                    "boolean"
                                } else if (primitive.isString) {
                                    "string"
                                } else {
                                    "null"
                                }
                            }

                            if (type == null) {
                                type = typeName
                            } else {
                                typeSame = typeName == type
                            }
                        } else if (child.isJsonArray) {
                            typeSame = false
                        }
                    }

                    if (!typeSame) {
                        addTsFieldForObjType(it, "any", true, parentElement)
                        return@let
                    }

                    when (type) {
                        null, "null" -> {
                            addTsFieldForObjType(it, "any", true, parentElement)
                        }

                        "JsonObject" -> {
                            val suffix = mObjSuffix.text.trim()
                            addTsFieldForObjType(it, className + suffix, true, parentElement)
                        }

                        else -> {
                            addTsFieldForObjType(it, type!!, true, parentElement)
                        }
                    }
                }
            }
        }
    }

    private fun addTsField(key: String, jsonElement: JsonPrimitive?, parentElement: PsiElement) {
        var content = if (jsonElement == null) {
            "$key?: string | null"
        } else if (jsonElement.isBoolean) {
            "$key?: boolean | null"
        } else if (jsonElement.isNumber) {
            "$key?: number | null"
        } else {
            jsonElement.asString
            "$key?: string | null"
        }

        content += if (mCreateObjectType.selectedIndex == 0) {
            " = null;\n"
        } else {
            ";\n"
        }

        val element = JSPsiElementFactory.createJSStatement(content, parentElement)
        if (mCbCreateDoc.isSelected && jsonElement != null) {
            // 不能把doc加到element，虽然此doc文档是属于element的，因为加到element下，doc结束符号'*/'与属性之间不会换行
            // 即使在'*/'后加换行'*/\n'也无效
            val doc = JSPsiElementFactory.createJSDocComment(
                "/**\n* ${jsonElement.toString().replace("\"", "")}\n*/",
                parentElement
            )
            parentElement.addBefore(doc, parentElement.lastChild)
        }
        parentElement.addBefore(element, parentElement.lastChild)
    }

    private fun addTsFieldForObjType(key: String, typeName: String, isArray: Boolean, parentElement: PsiElement) {
        var content = if ("any" == typeName) {
            mCreateObjectType.selectedIndex
            "$key?: any"
        } else {
            val type = if (isArray) "$typeName[]" else typeName
            "$key?: $type | null"
        }

        content += if (mCreateObjectType.selectedIndex == 0) {
            " = null;\n"
        } else {
            ";\n"
        }

        val element = JSPsiElementFactory.createJSStatement(content, parentElement)
        parentElement.addBefore(element, parentElement.lastChild)
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
