package com.communi.suggestu.formatum.adhibe.plugin;

import dev.lukebemish.immaculate.ImmaculateExtension;
import dev.lukebemish.immaculate.ImmaculatePlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;

public class FormatumAdhibePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(ImmaculatePlugin.class);

        ImmaculateExtension immaculate = project.getExtensions().getByType(ImmaculateExtension.class);
        immaculate.getWorkflows().configureEach(workflow -> {
            if (workflow instanceof ExtensionAware extensionAware && extensionAware.getExtensions().findByName("checkstyle") == null) {
                extensionAware.getExtensions().add("checkstyle", new WorkflowCheckstyleAction(project, workflow));
            }

            workflow.getSteps().configureEach(step -> {
                if (step instanceof CheckstyleDeterministicStep checkstyleStep) {
                    checkstyleStep.getCheckstyleConfig().convention(project.getLayout().getProjectDirectory().file("checkstyle.xml"));
                    checkstyleStep.getStepNamePrefix().convention("checkstyle");
                }
            });
        });
    }
}


