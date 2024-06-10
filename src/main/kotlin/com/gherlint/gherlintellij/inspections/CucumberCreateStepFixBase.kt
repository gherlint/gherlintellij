package com.gherlint.gherlintellij.inspections

import com.gherlint.gherlintellij.CucumberBundle
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable
//import org.jetbrains.plugins.cucumber.OptionalStepDefinitionExtensionPoint
//import org.jetbrains.plugins.cucumber.BDDFrameworkType
//import org.jetbrains.plugins.cucumber.CucumberBundle
//import org.jetbrains.plugins.cucumber.CucumberJvmExtensionPoint
//import org.jetbrains.plugins.cucumber.StepDefinitionCreator
import org.jetbrains.plugins.cucumber.*
import org.jetbrains.plugins.cucumber.inspections.model.CreateStepDefinitionFileModel
import org.jetbrains.plugins.cucumber.inspections.ui.CreateStepDefinitionFileDialog
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.CucumberStepHelper
import java.io.IOException
import javax.swing.Icon

abstract class CucumberCreateStepFixBase : LocalQuickFix {
    protected abstract fun createStepOrSteps(
        step: GherkinStep, @NotNull fileAndFrameworkType: CucumberStepDefinitionCreationContext
    )

    override fun startInWriteAction(): Boolean {
        return false
    }

    @NotNull
    override fun getFamilyName(): String {
        return name
    }

    override fun applyFix(@NotNull project: Project, @NotNull descriptor: ProblemDescriptor) {
        val step: GherkinStep = descriptor.psiElement as GherkinStep
        val featureFile: GherkinFile = step.containingFile as GherkinFile
        val pairs = ArrayList(getStepDefinitionContainers(featureFile))
        if (pairs.isNotEmpty()) {
            pairs.add(0, CucumberStepDefinitionCreationContext())
            val popupFactory: JBPopupFactory = JBPopupFactory.getInstance()
            val popupStep: ListPopup =
                popupFactory.createListPopup(object : BaseListPopupStep<CucumberStepDefinitionCreationContext>(
                    CucumberBundle.message("choose.step.definition.file"), ArrayList(pairs)
                ) {

                    override fun isSpeedSearchEnabled(): Boolean {
                        return true
                    }

                    @NotNull
                    override fun getTextFor(value: CucumberStepDefinitionCreationContext): String {
                        return value.psiFile?.let { psiFile ->
                            val file: VirtualFile = psiFile.virtualFile ?: return@let ""
                            val stepDefinitionCreator: StepDefinitionCreator? =
                                CucumberStepHelper.getExtensionMap()[value.frameworkType]?.stepDefinitionCreator
                            stepDefinitionCreator?.getStepDefinitionFilePath(psiFile) ?: ""
                        } ?: CucumberBundle.message("create.new.file")
                    }

                    override fun getIconFor(value: CucumberStepDefinitionCreationContext): Icon {
                        return value.psiFile?.getIcon(0) ?: AllIcons.Actions.IntentionBulb
                    }

                    override fun onChosen(
                        selectedValue: CucumberStepDefinitionCreationContext, finalChoice: Boolean
                    ): PopupStep<*>? {
                        return doFinalStep { createStepOrSteps(step, selectedValue) }
                    }
                })

            if (!ApplicationManager.getApplication().isUnitTestMode) {
                popupStep.showCenteredInCurrentWindow(step.project)
            } else {
                createStepOrSteps(step, pairs[1])
            }
        } else {
            createStepOrSteps(step, CucumberStepDefinitionCreationContext())
        }
    }

    companion object {
        private val LOG = Logger.getInstance(CucumberCreateStepFixBase::class.java)

        @JvmStatic
        fun getStepDefinitionContainers(@NotNull featureFile: GherkinFile): Set<CucumberStepDefinitionCreationContext> {
            val result = CucumberStepHelper.getStepDefinitionContainers(featureFile)
            result.removeIf { e -> CucumberStepHelper.getExtensionMap()[e.frameworkType] == null }
            return result
        }
    }

    private fun createStepDefinitionFile(
        step: GherkinStep, @NotNull context: CucumberStepDefinitionCreationContext
    ): Boolean {
        val featureFile: PsiFile = step.containingFile
        assert(featureFile != null)

        val model: CreateStepDefinitionFileModel = askUserForFilePath(step) ?: return false
        val filePath: String = FileUtil.toSystemDependentName(model.filePath)
        val frameworkType: BDDFrameworkType = model.selectedFileType
        context.frameworkType = frameworkType

        val project: Project = step.project
        if (LocalFileSystem.getInstance().findFileByPath(filePath) == null) {
            val parentDirPath: String = model.stepDefinitionFolderPath

            WriteCommandAction.runWriteCommandAction(project, CucumberBundle.message("create.step.definition"), null, {
                CommandProcessor.getInstance().executeCommand(project, {
                    try {
                        val parentDir: VirtualFile = VfsUtil.createDirectories(parentDirPath)
                        val parentPsiDir: PsiDirectory? = PsiManager.getInstance(project).findDirectory(parentDir)
                        assert(parentPsiDir != null)
                        val newFile: PsiFile = CucumberStepHelper.createStepDefinitionFile(
                            parentPsiDir!!, model.fileName, frameworkType
                        )
                        createStepDefinition(step, CucumberStepDefinitionCreationContext(newFile, frameworkType))
                        context.psiFile = newFile
                    } catch (e: IOException) {
                        LOG.error(e)
                    }
                }, CucumberBundle.message("cucumber.quick.fix.create.step.command.name.create"), null)
            })
            return true
        } else {
            Messages.showErrorDialog(
                project,
                CucumberBundle.message("cucumber.quick.fix.create.step.error.already.exist.msg", filePath),
                CucumberBundle.message("cucumber.quick.fix.create.step.file.name.title")
            )
            return false
        }
    }

    protected fun createFileOrStepDefinition(
        step: GherkinStep, @NotNull context: CucumberStepDefinitionCreationContext
    ): Boolean {
        return if (context.frameworkType == null) {
            createStepDefinitionFile(step, context)
        } else {
            createStepDefinition(step, context)
            true
        }
    }

    protected open fun shouldRunTemplateOnStepDefinition(): Boolean {
        return true
    }

    @Nullable
    private fun askUserForFilePath(@NotNull step: GherkinStep): CreateStepDefinitionFileModel? {
        val validator = object : InputValidator {
            override fun checkInput(filePath: String): Boolean {
                return !StringUtil.isEmpty(filePath)
            }

            override fun canClose(fileName: String): Boolean {
                return true
            }
        }

        val supportedFileTypesAndDefaultFileNames = mutableMapOf<BDDFrameworkType, String>()
        val fileTypeToDefaultDirectoryMap = mutableMapOf<BDDFrameworkType, String>()
        for (e in CucumberJvmExtensionPoint.EP_NAME.extensionList) {
            if (e is OptionalStepDefinitionExtensionPoint) {
                if (!e.participateInStepDefinitionCreation(step)) {
                    continue
                }
            }
            supportedFileTypesAndDefaultFileNames[e.stepFileType] = e.stepDefinitionCreator.getDefaultStepFileName(step)
            fileTypeToDefaultDirectoryMap[e.stepFileType] =
                e.stepDefinitionCreator.getDefaultStepDefinitionFolderPath(step)
        }

        val model = CreateStepDefinitionFileModel(
            step.containingFile, supportedFileTypesAndDefaultFileNames, fileTypeToDefaultDirectoryMap
        )
        val createStepDefinitionFileDialog = CreateStepDefinitionFileDialog(step.project, model, validator)
        return if (createStepDefinitionFileDialog.showAndGet()) model else null
    }

    private fun createStepDefinition(step: GherkinStep, @NotNull context: CucumberStepDefinitionCreationContext) {
        val stepDefCreator: StepDefinitionCreator? =
            CucumberStepHelper.getExtensionMap()[context.frameworkType]?.stepDefinitionCreator
        val file: PsiFile? = context.psiFile
        if (file != null) {
            WriteCommandAction.runWriteCommandAction(step.project, null, null, {
                stepDefCreator?.createStepDefinition(step, file, shouldRunTemplateOnStepDefinition())
            }, file)
        }
    }
}
