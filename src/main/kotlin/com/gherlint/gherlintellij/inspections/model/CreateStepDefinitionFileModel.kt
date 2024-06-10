package com.gherlint.gherlintellij.inspections.model

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.cucumber.BDDFrameworkType
import javax.swing.DefaultComboBoxModel

class CreateStepDefinitionFileModel(
    @NotNull val context: PsiFile,
    @NotNull fileTypeToDefaultNameMap: Map<BDDFrameworkType, String>,
    @NotNull private val fileTypeToDefaultDirectoryMap: Map<BDDFrameworkType, String>
) {
    private var myFileName: String? = null
    private val myFileTypeModel: DefaultComboBoxModel<FileTypeComboboxItem>
    private var myDirectory: String? = null
    val myProject: Project = context.project

    init {
        val myFileTypeList = ArrayList<FileTypeComboboxItem>()
        for ((key, value) in fileTypeToDefaultNameMap) {
            if (myFileName == null) {
                myFileName = value
            }
            val item = FileTypeComboboxItem(key, value)
            myFileTypeList.add(item)
        }
        myFileTypeModel = DefaultComboBoxModel(myFileTypeList.toTypedArray())
        myDirectory = fileTypeToDefaultDirectoryMap[getSelectedFileType()]
    }

    fun getFilePath(): String {
        return FileUtil.join(stepDefinitionFolderPath, fileNameWithExtension)
    }

    val fileNameWithExtension: String
        get() = "$myFileName.${getSelectedFileType()?.fileType?.defaultExtension}"

    var fileName: String?
        get() = myFileName
        set(fileName) {
            myFileName = fileName
        }

    val defaultDirectory: String?
        get() = fileTypeToDefaultDirectoryMap[getSelectedFileType()]

    val stepDefinitionFolderPath: String?
        get() = myDirectory

    fun setDirectory(directory: String?) {
        myDirectory = directory
    }

    fun getSelectedFileType(): BDDFrameworkType? {
        val selectedItem = myFileTypeModel.selectedItem as FileTypeComboboxItem?
        return selectedItem?.frameworkType
    }

    fun getFileTypeModel(): DefaultComboBoxModel<FileTypeComboboxItem> {
        return myFileTypeModel
    }

    fun getProject(): Project {
        return myProject
    }

    @NotNull
    fun getContext(): PsiFile {
        return context
    }
}
