package com.wanggaowan.rndevtools.ui

import com.intellij.ide.util.EditorHelper
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.ui.ColorUtil
import com.intellij.ui.Gray
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import com.wanggaowan.rndevtools.ui.listener.SimpleComponentListener
import icons.SdkIcons
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.*
import java.io.File
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

@Suppress("UseJBColor")
private object Config {
    var isDarkTheme = false

    private val LINE_COLOR = Gray._209
    private val LINE_COLOR_DARK = Gray._50

    private val MOUSE_ENTER_COLOR = Gray._223
    private val MOUSE_ENTER_COLOR_DARK = Color(76, 80, 82)

    // 用于透明Icon使用
    private val MOUSE_ENTER_COLOR2 = Color(191, 197, 200)
    private val MOUSE_ENTER_COLOR_DARK2 = Color(98, 106, 110)

    private val MOUSE_PRESS_COLOR = Gray._207
    private val MOUSE_PRESS_COLOR_DARK = Color(92, 97, 100)

    private val INPUT_FOCUS_COLOR = Color(71, 135, 201)

    private val INPUT_UN_FOCUS_COLOR = Gray._196
    private val INPUT_UN_FOCUS_COLOR_DARK = Gray._100

    private val IMAGE_TITLE_BG_COLOR = Gray._252
    private val IMAGE_TITLE_BG_COLOR_DARK = Color(49, 52, 53)

    val TRANSPARENT = Color(0, 0, 0, 0)

    fun getLineColor(): Color {
        if (isDarkTheme) {
            return LINE_COLOR_DARK
        }

        return LINE_COLOR
    }

    fun getMouseEnterColor(): Color {
        if (isDarkTheme) {
            return MOUSE_ENTER_COLOR_DARK
        }

        return MOUSE_ENTER_COLOR
    }

    fun getMouseEnterColor2(): Color {
        if (isDarkTheme) {
            return MOUSE_ENTER_COLOR_DARK2
        }

        return MOUSE_ENTER_COLOR2
    }

    fun getMousePressColor(): Color {
        if (isDarkTheme) {
            return MOUSE_PRESS_COLOR_DARK
        }

        return MOUSE_PRESS_COLOR
    }

    fun getInputFocusColor(): Color {
        if (isDarkTheme) {
            return INPUT_FOCUS_COLOR
        }

        return INPUT_FOCUS_COLOR
    }

    fun getInputUnFocusColor(): Color {
        if (isDarkTheme) {
            return INPUT_UN_FOCUS_COLOR_DARK
        }

        return INPUT_UN_FOCUS_COLOR
    }

    fun getImageTitleBgColor(): Color {
        if (isDarkTheme) {
            return IMAGE_TITLE_BG_COLOR_DARK
        }

        return IMAGE_TITLE_BG_COLOR
    }
}

/**
 * 图片资源预览面板
 *
 * @author Created by wanggaowan on 2022/6/17 13:13
 */
class ImagePreviewPanel(val project: Project) : JPanel(), Disposable {

    // 搜素布局相关View
    private lateinit var mSearchPanel: JPanel
    private lateinit var mSearchBtn: ImageButton
    private lateinit var mClearBtn: ImageButton
    private lateinit var mSearchTextField: JTextField

    private lateinit var mScrollPane: JBScrollPane
    private lateinit var mImagePanel: JPanel

    // 底部布局相关View
    private lateinit var mListLayoutBtn: ImageButton
    private lateinit var mGridLayoutBtn: ImageButton
    private lateinit var mRefreshBtn: ImageButton
    private lateinit var mPathLabel: JLabel

    // 网格展示模式时图片布局宽度
    private val mGridImageLayoutWidth = 160

    // 当前布局模式
    private var mLayoutMode = 0

    // 需要展示的图片
    private var mImages: Set<String>? = null

    // 当前展示图片数量
    private var mShowImageCount = 1

    // 默认预览图片文件夹
    private var mRootFilePath: String? = null

    init {
        Disposer.register(this, UiNotifyConnector(this, object : Activatable {
            override fun hideNotify() {}

            override fun showNotify() {
                mImages = getImageData()
                setNewImages()
            }
        }))

        project.messageBus.connect()
            .subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
                override fun stateChanged(toolWindowManager: ToolWindowManager) {
                    // 通过监听窗口的变化判断是否修改了主题，当打开设置界面并关闭后，此方法会回调
                    // 目前未找到直接监听主题变更的方法
                    checkTheme()
                }
            })

        layout = BorderLayout()
        preferredSize = Dimension(320, 100)
        val basePath = project.basePath
        mRootFilePath = if (project.basePath.isNullOrEmpty()) null else "${basePath}/app/res/imgs"
        initPanel()
    }

    private fun initPanel() {
        val isDarkTheme = StartupUiUtil.isUnderDarcula()
        Config.isDarkTheme = isDarkTheme
        SdkIcons.isDarkTheme = isDarkTheme
        val topPanel = JPanel()
        topPanel.layout = BorderLayout()
        add(topPanel, BorderLayout.NORTH)

        initSearchLayout(topPanel)
        initBottomLayout()

        // 展示Image预览内容的面板
        mImagePanel = JPanel()
        mImagePanel.layout = FlowLayout(FlowLayout.LEFT, 0, 0)
        mImagePanel.background = null
        mImagePanel.border = null
        mScrollPane = JBScrollPane(mImagePanel)
        mScrollPane.background = null
        mScrollPane.border = LineBorder(Config.getLineColor(), 0, 0, 1, 0)
        mScrollPane.horizontalScrollBar = null
        add(mScrollPane, BorderLayout.CENTER)

        registerSizeChange()
    }

    /**
     * 初始化搜索界面布局
     */
    private fun initSearchLayout(parent: JPanel) {
        // 搜索一栏根布局
        mSearchPanel = JPanel()
        mSearchPanel.layout = BoxLayout(mSearchPanel, BoxLayout.X_AXIS)
        mSearchPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createCompoundBorder(
                LineBorder(Config.getLineColor(), 0, 0, 1, 0),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ), LineBorder(Config.getInputUnFocusColor(), 1, true)
        )
        parent.add(mSearchPanel, BorderLayout.NORTH)

        mSearchBtn = ImageButton()
        mSearchBtn.preferredSize = Dimension(30, 30)
        mSearchBtn.minimumSize = mSearchBtn.preferredSize
        mSearchBtn.maximumSize = mSearchBtn.preferredSize
        mSearchBtn.icon = SdkIcons.search
        mSearchBtn.background = Config.TRANSPARENT
        mSearchBtn.isOpaque = true
        mSearchPanel.add(mSearchBtn)

        mSearchTextField = JTextField()
        mSearchTextField.preferredSize = Dimension(100, 30)
        mSearchTextField.minimumSize = Dimension(100, 30)
        mSearchTextField.background = Config.TRANSPARENT
        mSearchTextField.border = BorderFactory.createEmptyBorder()
        mSearchTextField.isOpaque = true
        mSearchTextField.addFocusListener(object : FocusListener {
            override fun focusGained(p0: FocusEvent?) {
                mSearchPanel.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createCompoundBorder(
                        LineBorder(Config.getLineColor(), 0, 0, 1, 0),
                        BorderFactory.createEmptyBorder(9, 9, 9, 9)
                    ), LineBorder(Config.getInputFocusColor(), 2, true)
                )
            }

            override fun focusLost(p0: FocusEvent?) {
                mSearchPanel.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createCompoundBorder(
                        LineBorder(Config.getLineColor(), 0, 0, 1, 0),
                        BorderFactory.createEmptyBorder(10, 10, 10, 10)
                    ), LineBorder(Config.getInputUnFocusColor(), 1, true)
                )
            }
        })

        mSearchPanel.add(mSearchTextField)

        mClearBtn = ImageButton()
        mClearBtn.preferredSize = Dimension(30, 30)
        mClearBtn.minimumSize = mSearchBtn.preferredSize
        mClearBtn.maximumSize = mSearchBtn.preferredSize
        mClearBtn.icon = SdkIcons.close
        mClearBtn.background = null
        mClearBtn.isVisible = false
        mClearBtn.radius = 100
        mClearBtn.padding = 7
        mClearBtn.isOpaque = true
        mClearBtn.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                mClearBtn.background = Config.getMouseEnterColor2()
                mClearBtn.icon = SdkIcons.closeFocus
            }

            override fun mouseExited(e: MouseEvent?) {
                super.mouseExited(e)
                mClearBtn.background = null
                mClearBtn.icon = SdkIcons.close
            }

            override fun mouseClicked(e: MouseEvent?) {
                mSearchTextField.text = null
                mClearBtn.background = null
                mClearBtn.icon = SdkIcons.close
                mClearBtn.isVisible = false
            }
        })

        mSearchPanel.add(mClearBtn)

        // 文本改变监听
        mSearchTextField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(p0: DocumentEvent?) {
                val str = mSearchTextField.text.trim()
                mClearBtn.isVisible = str.isNotEmpty()
                setNewImages()
            }

            override fun removeUpdate(p0: DocumentEvent?) {
                val str = mSearchTextField.text.trim()
                mClearBtn.isVisible = str.isNotEmpty()
                setNewImages()
            }

            override fun changedUpdate(p0: DocumentEvent?) {
                val str = mSearchTextField.text.trim()
                mClearBtn.isVisible = str.isNotEmpty()
                setNewImages()
            }

        })
    }

    /**
     * 初始化底部界面布局
     */
    private fun initBottomLayout() {
        // 底部按钮面板
        val bottomPanel = JPanel()
        bottomPanel.layout = BorderLayout().apply {
            hgap = 0
        }
        bottomPanel.border = BorderFactory.createEmptyBorder(5, 0, 5, 0)
        add(bottomPanel, BorderLayout.SOUTH)

        // 底部靠右的布局面板
        val bottomRightPanel = JPanel()
        bottomRightPanel.layout = FlowLayout().apply {
            hgap = 10
            alignment = FlowLayout.RIGHT
        }
        bottomPanel.add(bottomRightPanel, BorderLayout.EAST)

        mListLayoutBtn = ImageButton()
        this.mListLayoutBtn.isOpaque = true
        mListLayoutBtn.icon = SdkIcons.list
        mListLayoutBtn.preferredSize = Dimension(24, 24)
        mListLayoutBtn.background = Config.getMousePressColor()
        mListLayoutBtn.radius = 5
        bottomRightPanel.add(mListLayoutBtn)

        mGridLayoutBtn = ImageButton()
        mGridLayoutBtn.isOpaque = true
        mGridLayoutBtn.icon = SdkIcons.grid
        mGridLayoutBtn.preferredSize = Dimension(24, 24)
        mGridLayoutBtn.border = null
        mGridLayoutBtn.background = null
        mGridLayoutBtn.radius = 5
        bottomRightPanel.add(mGridLayoutBtn)

        mListLayoutBtn.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                if (mLayoutMode != 0) {
                    mListLayoutBtn.background = Config.getMouseEnterColor()
                }

            }

            override fun mouseExited(e: MouseEvent?) {
                super.mouseExited(e)
                if (mLayoutMode != 0) {
                    mListLayoutBtn.background = null
                }
            }

            override fun mousePressed(e: MouseEvent?) {
                super.mousePressed(e)
                if (mLayoutMode != 0) {
                    mListLayoutBtn.background = Config.getMousePressColor()
                }
            }

            override fun mouseClicked(e: MouseEvent?) {
                if (mLayoutMode == 0) {
                    return
                }

                mListLayoutBtn.background = Config.getMousePressColor()
                mGridLayoutBtn.background = null

                mLayoutMode = 0
                setNewImages()
            }
        })

        mGridLayoutBtn.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                if (mLayoutMode != 1) {
                    mGridLayoutBtn.background = Config.getMouseEnterColor()
                }

            }

            override fun mouseExited(e: MouseEvent?) {
                super.mouseExited(e)
                if (mLayoutMode != 1) {
                    mGridLayoutBtn.background = null
                }
            }

            override fun mousePressed(e: MouseEvent?) {
                super.mousePressed(e)
                if (mLayoutMode != 1) {
                    mGridLayoutBtn.background = Config.getMousePressColor()
                }
            }

            override fun mouseClicked(e: MouseEvent?) {
                if (mLayoutMode == 1) {
                    return
                }

                mGridLayoutBtn.background = Config.getMousePressColor()
                mListLayoutBtn.background = null

                mLayoutMode = 1
                setNewImages()
            }
        })

        // 底部靠左面板
        val bottomLeftPanel = JPanel()
        bottomLeftPanel.layout = FlowLayout().apply {
            hgap = 10
            alignment = FlowLayout.LEFT
        }
        bottomPanel.add(bottomLeftPanel, BorderLayout.WEST)

        mRefreshBtn = ImageButton()
        this.mRefreshBtn.isOpaque = true
        mRefreshBtn.icon = SdkIcons.refresh
        mRefreshBtn.preferredSize = Dimension(24, 24)
        mRefreshBtn.border = null
        mRefreshBtn.background = null
        mRefreshBtn.radius = 5
        bottomLeftPanel.add(mRefreshBtn)

        mRefreshBtn.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                mRefreshBtn.background = Config.getMouseEnterColor()
            }

            override fun mouseExited(e: MouseEvent?) {
                super.mouseExited(e)
                mRefreshBtn.background = null
            }

            override fun mousePressed(e: MouseEvent?) {
                super.mousePressed(e)
                mRefreshBtn.background = Config.getMousePressColor()
            }

            override fun mouseClicked(e: MouseEvent?) {
                mRefreshBtn.background = Config.getMouseEnterColor()
                mImages = getImageData()
                setNewImages()
            }
        })

        // 增加图片预览的根路径显示
        val path = mRootFilePath ?: ""
        val basePath = project.basePath ?: ""
        mPathLabel = JLabel(path.replace(basePath, ""))
        mPathLabel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(2, 2, 2, 2),
                LineBorder(Config.getInputUnFocusColor(), 1, true)
            ),
            BorderFactory.createEmptyBorder(0, 4, 0, 4)
        )
        bottomPanel.add(mPathLabel, BorderLayout.CENTER)
    }

    /**
     * 注册窗口尺寸改变监听
     */
    private fun registerSizeChange() {
        addComponentListener(object : SimpleComponentListener() {
            override fun componentResized(p0: ComponentEvent?) {
                if (mLayoutMode == 0) {
                    for (component in mImagePanel.components) {
                        component.preferredSize = Dimension(width, 100)
                    }
                    val totalHeight = mShowImageCount * 100 + 100
                    mImagePanel.preferredSize = Dimension(width, totalHeight)
                    mImagePanel.updateUI()
                    return
                }

                val itemHeight: Int = mGridImageLayoutWidth + 60 + 20
                val itemWidth = mGridImageLayoutWidth + 20
                val columns: Int = if (width <= itemWidth) 1 else width / itemWidth
                var rows: Int = mShowImageCount / columns
                if (mShowImageCount % columns != 0) {
                    rows += 1
                }
                val totalHeight = rows * itemHeight + 100
                mImagePanel.preferredSize = Dimension(width, totalHeight)
            }
        })
    }

    private fun getImageData(): Set<String>? {
        if (mRootFilePath.isNullOrEmpty()) {
            return null
        }

        val file = VirtualFileManager.getInstance().findFileByUrl("file://$mRootFilePath") ?: return null
        if (!file.isDirectory) {
            return null
        }

        val images = getDeDuplicationList(file)
        if (images.size == 0) {
            return null
        }

        return images
    }

    // 设置需要预览的图片数据
    private fun setNewImages() {
        mImagePanel.removeAll()
        mImagePanel.updateUI()
        mShowImageCount = 1

        var data = mImages
        if (data.isNullOrEmpty()) {
            return
        }

        val searchStr = mSearchTextField.text.trim()
        if (searchStr.isNotEmpty()) {
            val newData = mutableSetOf<String>()
            for (path: String in data) {
                var name = path
                var indexOf = path.lastIndexOf("/")
                name = name.substring(indexOf + 1)
                indexOf = name.indexOf(".")
                if (indexOf != -1) {
                    name = name.substring(0, indexOf)
                }

                if (name.contains(searchStr)) {
                    newData.add(path)
                }
            }
            data = newData
        }

        if (data.isEmpty()) {
            return
        }

        mShowImageCount = data.size
        setImageLayout()
        data.forEach { image ->
            mImagePanel.add(getPreviewItemPanel(getFile(image), mLayoutMode))
        }
        mImagePanel.updateUI()
    }

    // 设置图片预览的布局样式
    private fun setImageLayout() {
        (mImagePanel.layout as FlowLayout).let {
            if (mLayoutMode == 0) {
                val totalHeight = mShowImageCount * 100 + 100
                mImagePanel.preferredSize = Dimension(width, totalHeight)
            } else {
                val itemHeight: Int = mGridImageLayoutWidth + 60 + 20
                val itemWidth = mGridImageLayoutWidth + 20
                val columns: Int = if (width <= itemWidth) 1 else width / itemWidth
                var rows: Int = mShowImageCount / columns
                if (mShowImageCount % columns != 0) {
                    rows += 1
                }
                val totalHeight = rows * itemHeight + 100
                mImagePanel.preferredSize = Dimension(width, totalHeight)
            }
        }
    }

    /**
     * 获取图片预览Item样式
     * @param layoutType 0:线性布局，1：网格布局
     */
    private fun getPreviewItemPanel(image: File, layoutType: Int): JPanel {
        val panel = JPanel()
        panel.layout = BorderLayout()
        panel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                super.mouseClicked(e)
                if (e.clickCount == 2) {
                    val file = VirtualFileManager.getInstance().findFileByUrl("file://${image.path}") ?: return
                    val psiFile = PsiManager.getInstance(project).findFile(file) ?: return
                    EditorHelper.openFilesInEditor(arrayOf<PsiFile?>(psiFile))
                }
            }
        })

        if (layoutType == 0) {
            // 列表布局
            panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            panel.preferredSize = Dimension(width, 100)

            val imageView = ImageView(image, Config.isDarkTheme)
            imageView.preferredSize = Dimension(80, 80)
            panel.add(imageView, BorderLayout.WEST)
            imageView.border = LineBorder(Config.getLineColor(), 1)

            val label = JLabel()
            label.border = BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 30, 0, 0),
                LineBorder(Config.getLineColor(), 0, 0, 1, 0)
            )

            var path = image.path
            var indexOf = path.lastIndexOf("/")
            if (indexOf != -1) {
                path = path.substring(indexOf + 1)
            }
            indexOf = path.indexOf(".")
            if (indexOf != -1) {
                path = path.substring(0, indexOf)
            }
            label.text = getPropertyValue(path)

            panel.add(label, BorderLayout.CENTER)
        } else {
            // 网格布局
            val labelHeight = 60
            // 20 为padding 10
            panel.preferredSize = Dimension(mGridImageLayoutWidth + 20, mGridImageLayoutWidth + labelHeight + 20)
            panel.border = BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(10, 10, 10, 10),
                LineBorder(Config.getLineColor(), 1)
            )

            val imageView = ImageView(image, Config.isDarkTheme)
            imageView.preferredSize = Dimension(mGridImageLayoutWidth, mGridImageLayoutWidth)
            panel.add(imageView, BorderLayout.CENTER)

            val label = JLabel()
            label.background = Config.getImageTitleBgColor()
            label.isOpaque = true
            label.preferredSize = Dimension(mGridImageLayoutWidth, labelHeight)
            label.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

            var path = image.path
            var indexOf = path.lastIndexOf("/")
            if (indexOf != -1) {
                path = path.substring(indexOf + 1)
            }
            indexOf = path.indexOf(".")
            if (indexOf != -1) {
                path = path.substring(0, indexOf)
            }

            label.text = getPropertyValue(path)

            panel.add(label, BorderLayout.SOUTH)
        }

        return panel
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
     * 获取去重后的属性列表
     */
    private fun getDeDuplicationList(rootDir: VirtualFile, parentPath: String = ""): LinkedHashSet<String> {
        val childrenSet = linkedSetOf<String>()
        for (child in rootDir.children) {
            if (child.isDirectory) {
                childrenSet.addAll(getDeDuplicationList(child, "$parentPath${child.name}/"))
            } else if (isImage(child.name)) {
                childrenSet.add(getPropertyValue(child.path))
            }
        }

        return childrenSet
    }

    private fun getPropertyValue(value: String): String {
        return value.replace("@1x", "")
            .replace("@2x", "")
            .replace("@3x", "")
    }

    private fun getFile(relativePath: String): File {
        val indexOf = relativePath.lastIndexOf(".")
        if (indexOf == -1) {
            return File(relativePath)
        }

        val name = relativePath.substring(0, indexOf)
        val suffix = relativePath.substring(indexOf)
        var file = File("$name@3x$suffix")
        if (file.exists()) {
            return file
        }

        file = File("$name@2x$suffix")
        if (file.exists()) {
            return file
        }

        file = File("$name@1x$suffix")
        if (file.exists()) {
            return file
        }

        return File(relativePath)
    }

    override fun dispose() {

    }

    private fun checkTheme() {
        val isDarkTheme = ColorUtil.isDark(background)
        if (isDarkTheme != Config.isDarkTheme) {
            Config.isDarkTheme = isDarkTheme
            SdkIcons.isDarkTheme = isDarkTheme
            updateTheme()
        }
    }

    private fun updateTheme() {
        val inputRectColor =
            if (mSearchTextField.hasFocus()) Config.getInputFocusColor() else Config.getInputUnFocusColor()
        mSearchPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createCompoundBorder(
                LineBorder(Config.getLineColor(), 0, 0, 1, 0),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ), LineBorder(inputRectColor, 1, true)
        )
        mSearchBtn.icon = SdkIcons.search
        mClearBtn.icon = SdkIcons.close

        mListLayoutBtn.icon = SdkIcons.list
        if (mLayoutMode == 0) {
            mListLayoutBtn.background = Config.getMousePressColor()
        }

        mGridLayoutBtn.icon = SdkIcons.grid
        if (mLayoutMode == 1) {
            mGridLayoutBtn.background = Config.getMousePressColor()
        }

        mRefreshBtn.icon = SdkIcons.refresh

        mPathLabel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(2, 2, 2, 2),
                LineBorder(Config.getInputUnFocusColor(), 1, true)
            ),
            BorderFactory.createEmptyBorder(0, 4, 0, 4)
        )

        mScrollPane.border = LineBorder(Config.getLineColor(), 0, 0, 1, 0)
        setNewImages()
    }
}
