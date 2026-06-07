package com.communi.suggestu.formatum.adhibe.plugin;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.jspecify.annotations.NonNull;

public class FormatumAdhibeSettingsPlugin implements Plugin<Settings>
{
    @Override
    public void apply(final Settings target)
    {
        target.getGradle().beforeProject(project -> project.getPlugins().apply(FormatumAdhibePlugin.class));
    }
}
