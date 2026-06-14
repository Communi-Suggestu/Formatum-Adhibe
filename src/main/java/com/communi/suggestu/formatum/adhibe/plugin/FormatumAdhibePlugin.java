package com.communi.suggestu.formatum.adhibe.plugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.jspecify.annotations.NonNull;

public class FormatumAdhibePlugin implements Plugin<Object>
{
    @Override
    public void apply(final @NonNull Object target)
    {
        if (target instanceof Project project) {
            project.getPlugins().apply(FormatumAdhibeProjectPlugin.class);
        } else if (target instanceof Settings settings) {
            settings.getPlugins().apply(FormatumAdhibeSettingsPlugin.class);
        }
    }
}
