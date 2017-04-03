/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.framework

import com.google.common.collect.Lists
import com.intellij.framework.library.LibraryVersionProperties
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryKind
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryPresentationManager
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.configuration.KotlinWithLibraryConfigurator
import org.jetbrains.kotlin.idea.configuration.createConfigureKotlinNotificationCollector
import org.jetbrains.kotlin.idea.configuration.getConfiguratorByName
import org.jetbrains.kotlin.idea.framework.ui.CreateLibraryDialog
import org.jetbrains.kotlin.idea.framework.ui.FileUIUtils
import org.jetbrains.kotlin.idea.util.projectStructure.findLibrary
import org.jetbrains.kotlin.idea.util.projectStructure.getModuleDir
import org.jetbrains.kotlin.idea.util.projectStructure.replaceFileRoot
import java.io.File
import java.util.*
import javax.swing.JComponent

/**
 * @param project null when project doesn't exist yet (called from project wizard)
 */
abstract class CustomLibraryDescriptorWithDeferredConfig
(
        project: Project?,
        private val configuratorName: String,
        private val libraryName: String,
        private val dialogTitle: String,
        private val modulesSeparatorCaption: String,
        private val libraryKind: LibraryKind,
        private val suitableLibraryKinds: Set<LibraryKind>
) : CustomLibraryDescription() {
    private val projectBaseDir: VirtualFile? = project?.baseDir

    var copyFileRequests: DeferredCopyFileRequests? = null
        private set

    override fun getSuitableLibraryKinds(): Set<LibraryKind> {
        return suitableLibraryKinds
    }

    fun finishLibConfiguration(module: Module, rootModel: ModifiableRootModel) {
        val deferredCopyFileRequests = copyFileRequests ?: return

        val library = rootModel.orderEntries().findLibrary { library ->
            val libraryPresentationManager = LibraryPresentationManager.getInstance()
            val classFiles = Arrays.asList(*library.getFiles(OrderRootType.CLASSES))

            libraryPresentationManager.isLibraryOfKind(classFiles, libraryKind)
        } ?: return

        val model = library.modifiableModel
        try {
            deferredCopyFileRequests.performRequests(module.project, module.getModuleDir(), model)
        }
        finally {
            model.commit()
        }
    }

    class DeferredCopyFileRequests(private val configurator: KotlinWithLibraryConfigurator) {
        private val copyFilesRequests = Lists.newArrayList<CopyFileRequest>()

        fun performRequests(project: Project, relativePath: String, model: Library.ModifiableModel) {
            val collector = createConfigureKotlinNotificationCollector(project)
            for (request in copyFilesRequests) {
                val destinationPath = if (FileUtil.isAbsolute(request.toDir))
                    request.toDir
                else
                    File(relativePath, request.toDir).path

                val resultFile = configurator.copyFileToDir(request.file, destinationPath, collector)

                if (request.replaceInLib && resultFile != null) {
                    model.replaceFileRoot(request.file, resultFile)
                }
            }
            collector.showNotification()
        }

        fun addCopyWithReplaceRequest(file: File, copyIntoPath: String) {
            copyFilesRequests.add(CopyFileRequest(copyIntoPath, file, true))
        }

        class CopyFileRequest(val toDir: String, val file: File, val replaceInLib: Boolean)
    }

    override fun createNewLibrary(parentComponent: JComponent, contextDirectory: VirtualFile?): NewLibraryConfiguration? {
        val configurator = configurator

        copyFileRequests = DeferredCopyFileRequests(configurator)

        val defaultPathToJarFile = if (projectBaseDir == null)
            DEFAULT_LIB_DIR_NAME
        else
            FileUIUtils.createRelativePath(null, projectBaseDir, DEFAULT_LIB_DIR_NAME)

        val jarDescriptors = configurator.libraryJarDescriptors

        val stdJarInDefaultPath = File(defaultPathToJarFile, jarDescriptors.first().jarName)
        val libraryFiles = mutableListOf<File>()
        val librarySourceFiles = mutableListOf<File>()
        if (projectBaseDir != null && stdJarInDefaultPath.exists()) {
            libraryFiles.add(stdJarInDefaultPath)
            for (jarDescriptor in jarDescriptors) {
                var destination = File(defaultPathToJarFile, jarDescriptor.jarName)
                if (!destination.exists()) {
                    copyFileRequests!!.addCopyWithReplaceRequest(jarDescriptor.getPathInPlugin(), defaultPathToJarFile)
                    destination = jarDescriptor.getPathInPlugin()
                }
                if (jarDescriptor.orderRootType == OrderRootType.SOURCES) {
                    librarySourceFiles.add(destination)
                }
                else {
                    libraryFiles.add(destination)
                }
            }
        }
        else {
            val dialog = CreateLibraryDialog(defaultPathToJarFile, dialogTitle, modulesSeparatorCaption)
            dialog.show()

            if (!dialog.isOK) return null

            val copyIntoPath = dialog.copyIntoPath
            if (copyIntoPath != null) {
                for (libraryJarDescriptor in configurator.libraryJarDescriptors) {
                    copyFileRequests!!.addCopyWithReplaceRequest(libraryJarDescriptor.getPathInPlugin(), copyIntoPath)
                }
            }

            for (jarDescriptor in jarDescriptors) {
                if (jarDescriptor.orderRootType == OrderRootType.SOURCES) {
                    librarySourceFiles.add(jarDescriptor.getPathInPlugin())
                }
                else {
                    libraryFiles.add(jarDescriptor.getPathInPlugin())
                }
            }
        }

        return createConfiguration(libraryFiles, librarySourceFiles)
    }

    private val configurator: KotlinWithLibraryConfigurator
        get() {
            val configurator = getConfiguratorByName(configuratorName) as KotlinWithLibraryConfigurator? ?: error("Configurator with name $configuratorName should exists")
            return configurator
        }

    // Implements an API added in IDEA 16
    override fun createNewLibraryWithDefaultSettings(contextDirectory: VirtualFile?): NewLibraryConfiguration? {
        return createConfiguration(collectPathsInPlugin(OrderRootType.CLASSES),
                                   collectPathsInPlugin(OrderRootType.SOURCES))
    }

    private fun collectPathsInPlugin(rootType: OrderRootType): List<File> {
        return configurator.libraryJarDescriptors
                .filter { it.orderRootType == rootType }
                .map { it.getPathInPlugin() }
    }

    protected fun createConfiguration(libraryFiles: List<File>, librarySourceFiles: List<File>): NewLibraryConfiguration {
        return object : NewLibraryConfiguration(libraryName, null, LibraryVersionProperties()) {
            override fun addRoots(editor: LibraryEditor) {
                for (libraryFile in libraryFiles) {
                    editor.addRoot(VfsUtil.getUrlForLibraryRoot(libraryFile), OrderRootType.CLASSES)
                }
                for (librarySrcFile in librarySourceFiles) {
                    editor.addRoot(VfsUtil.getUrlForLibraryRoot(librarySrcFile), OrderRootType.SOURCES)
                }
            }
        }
    }

    companion object {

        private val DEFAULT_LIB_DIR_NAME = "lib"
    }
}
