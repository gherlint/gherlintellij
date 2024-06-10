package com.gherlint.gherlintellij.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.cucumber.CucumberBundle
import org.jetbrains.plugins.cucumber.CucumberUtil.getCucumberStepReference
import org.jetbrains.plugins.cucumber.inspections.CucumberCreateAllStepsFix
import org.jetbrains.plugins.cucumber.inspections.CucumberCreateStepFix
import org.jetbrains.plugins.cucumber.psi.GherkinElementVisitor
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition
import org.jetbrains.plugins.cucumber.steps.CucumberStepHelper
import org.jetbrains.plugins.cucumber.steps.reference.CucumberStepReference

class CucumberStepInspection : GherkinInspection() {
    override fun isEnabledByDefault(): Boolean {
        return true
    }

    @NotNull
    override fun getShortName(): String {
        return "CucumberUndefinedStep"
    }

    @NotNull
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : GherkinElementVisitor() {
            override fun visitStep(step: GherkinStep) {
                super.visitStep(step)
                val parent: PsiElement? = step.parent
                if (parent is GherkinStepsHolder) {
                    val reference: CucumberStepReference? = getCucumberStepReference(step)
                    if (reference == null) {
                        return
                    }
                    val definition: AbstractStepDefinition? = reference.resolveToDefinition()
                    if (definition == null) {
                        var createStepFix: CucumberCreateStepFix? = null
                        var createAllStepsFix: CucumberCreateAllStepsFix? = null
                        if (CucumberStepHelper.getExtensionCount() > 0) {
                            createStepFix = CucumberCreateStepFix()
                            createAllStepsFix = CucumberCreateAllStepsFix()
                        }
                        holder.registerProblem(
                            reference.element, reference.rangeInElement,
                            CucumberBundle.message("cucumber.inspection.undefined.step.msg.name"),
                            createStepFix, createAllStepsFix
                        )
                    }
                }
            }
        }
    }
}