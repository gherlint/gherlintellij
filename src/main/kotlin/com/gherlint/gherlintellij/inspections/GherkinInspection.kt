package com.gherlint.gherlintellij.inspections

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.cucumber.CucumberBundle

abstract class GherkinInspection : LocalInspectionTool() {

    @NotNull
    override fun getGroupDisplayName(): String {
        return CucumberBundle.message("cucumber.inspection.group.name")
    }
}