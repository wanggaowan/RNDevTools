package com.wanggaowan.rndevtools.projectviewpane

import com.intellij.ide.SelectInContext
import com.intellij.ide.SelectInManager
import com.intellij.ide.SelectInTarget
import com.intellij.ide.StandardTargetWeights
import com.intellij.ide.impl.ProjectViewSelectInTarget
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.ProjectViewSettings
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase
import com.intellij.ide.projectView.impl.ProjectTreeStructure
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.ide.projectView.impl.nodes.*
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleDescription
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.module.impl.LoadedModuleDescriptionImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import icons.SdkIcons
import javax.swing.Icon


/**
 * RN项目结构，仅展示开发RN时关注的文件
 *
 * @author Created by wanggaowan on 2022/6/27 11:10
 */
class RNProjectViewPane(val project: Project) : ProjectViewPane(project) {
    private val id = "React Native Pane"

    override fun getTitle(): String {
        return "React Native"
    }

    /**
     * 判断项目视图是否展示
     */
    override fun isInitiallyVisible(): Boolean {
        val basePath = project.basePath ?: return false
        val file = VirtualFileManager.getInstance().findFileByUrl("file://$basePath") ?: return false
        var isRNProject = false
        for (child in file.children) {
            if (child.name == "package.json") {
                // 存在package.json文件就认为是RN项目
                isRNProject = true
                break
            }
        }
        return isRNProject
    }

    override fun getIcon(): Icon {
        return SdkIcons.defaultIcon
    }

    override fun getId(): String {
        return id
    }

    override fun getComponentName(): String {
        return id
    }

    override fun getWeight(): Int {
        return 10
    }

    override fun createStructure(): ProjectAbstractTreeStructureBase {
        return ProjectViewPaneTreeStructure(project, id)
    }

    override fun createSelectInTarget(): SelectInTarget {
        return ProjectPaneSelectInTarget(project, id)
    }
}

private class ProjectViewPaneTreeStructure(val project: Project, val id: String) :
    ProjectTreeStructure(project, id), ProjectViewSettings {
    override fun createRoot(project: Project, settings: ViewSettings): AbstractTreeNode<*> {
        return InnerProjectViewProjectNode(project, settings)
    }

    override fun isShowExcludedFiles(): Boolean {
        return ProjectView.getInstance(project).isShowExcludedFiles(id)
    }

    override fun isShowLibraryContents(): Boolean {
        return true
    }

    override fun isShowVisibilityIcons(): Boolean {
        return ProjectView.getInstance(project).isShowVisibilityIcons(id)
    }

    override fun isUseFileNestingRules(): Boolean {
        return ProjectView.getInstance(project).isUseFileNestingRules(id)
    }

    override fun isToBuildChildrenInBackground(element: Any): Boolean {
        return Registry.`is`("ide.projectView.ProjectViewPaneTreeStructure.BuildChildrenInBackground")
    }
}

private class InnerProjectViewProjectNode(project: Project, viewSettings: ViewSettings) :
    ProjectViewProjectNode(project, viewSettings) {
    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        // 获取项目所有模块
        val project = myProject
        if (project == null || project.isDisposed || project.isDefault) {
            return emptyList()
        }

        val topLevelContentRoots = ProjectViewDirectoryHelper.getInstance(project).topLevelRoots
        val modules: MutableSet<ModuleDescription> = LinkedHashSet(topLevelContentRoots.size)
        for (root in topLevelContentRoots) {
            val module = ModuleUtilCore.findModuleForFile(root!!, project)
            if (module != null) {
                modules.add(LoadedModuleDescriptionImpl(module))
            }
        }

        return ArrayList(modulesAndGroups(modules))
    }

    override fun createModuleGroup(module: Module): AbstractTreeNode<*> {
        // 获取模块节点
        val first = ModuleRootManager.getInstance(module).contentRoots.first()
        if (first != null) {
            val psi = PsiManager.getInstance(myProject).findDirectory(first)
            if (psi != null) {
                return PsiDirectoryNode(myProject, psi, settings, PsiFileSystemItemFilter { item ->
                    // 过滤模块下文件，将不需要展示的文件剔除
                    val file: VirtualFile? = item.virtualFile
                    if (file != null) {
                        val name = file.name
                        if (name.endsWith(".lock")
                            || name.endsWith(".log")
                            || name.startsWith(".")
                            || name.startsWith("_")
                        ) {
                            return@PsiFileSystemItemFilter false
                        }

                        if (file.isDirectory
                            && (name == "android"
                                || name == "ios"
                                || name == "node_modules")
                        ) {
                            return@PsiFileSystemItemFilter false
                        }

                        if (name == "babel.config.js"
                            || name == "Gemfile"
                            || name == "jest.config.js"
                            || name == "metro.config.js"
                            || name == "package-lock.json"
                            || name == "tsconfig.json"
                            || name == ""
                        ) {
                            return@PsiFileSystemItemFilter false
                        }
                    }
                    true
                })
            }
        }

        return ProjectViewModuleNode(project, module, settings)
    }
}

private class ProjectPaneSelectInTarget(project: Project, val id: String) : ProjectViewSelectInTarget(project),
    DumbAware {
    override fun toString(): String {
        return SelectInManager.getProject()
    }

    override fun isSubIdSelectable(subId: String, context: SelectInContext): Boolean {
        return canSelect(context)
    }

    override fun getMinorViewId(): String {
        return id
    }

    override fun getWeight(): Float {
        return StandardTargetWeights.PROJECT_WEIGHT
    }
}
