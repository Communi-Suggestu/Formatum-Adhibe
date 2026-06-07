package com.communi.suggestu.formatum.adhibe.plugin;

import com.communi.suggestu.formatum.adhibe.checkstyle.hints.FixMode;
import dev.lukebemish.immaculate.FormattingWorkflow;
import groovy.lang.Closure;
import org.gradle.api.Project;

public final class WorkflowCheckstyleAction {
    private final Project project;
    private final FormattingWorkflow workflow;

    public WorkflowCheckstyleAction(Project project, FormattingWorkflow workflow) {
        this.project = project;
        this.workflow = workflow;
    }

    public void call(String name) {
        call(name, (Closure<CheckstyleDeterministicStep>) null);
    }

    public void call(String name, Closure<CheckstyleDeterministicStep> closure) {
        workflow.step(name, CheckstyleDeterministicStep.class, step -> {
            applyDefaults(step);
            if (closure != null) {
                project.configure(step, closure);
            }
        });
    }

    private void applyDefaults(CheckstyleDeterministicStep step) {
        step.getCheckstyleConfig().convention(project.getLayout().getProjectDirectory().file("checkstyle.xml"));
        step.getStepNamePrefix().convention("checkstyle");
        step.getFixMode().convention(FixMode.SAFE);
        step.getFailOnUnmatchedHints().convention(true);
        step.getFailOnHintConflicts().convention(true);
    }
}


