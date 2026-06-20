package com.communi.suggestu.formatum.adhibe.formatting.steps;

import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepFormatterTest {
    private static final String STEP_NAME = "testStep";

    @Test
    void trimsTrailingWhitespace() {
        TrimTrailingWhitespaceStep step = ProjectBuilder.builder().build().getObjects().newInstance(TrimTrailingWhitespaceStep.class, STEP_NAME);
        String formatted = step.formatter().format("Example.java", "a  \n\tb \n");
        assertEquals("a\n\tb\n", formatted);
    }

    @Test
    void removesBraceAdjacentBlankLines() {
        RemoveBlankLineAfterOpeningBraceStep after = ProjectBuilder.builder().build().getObjects().newInstance(RemoveBlankLineAfterOpeningBraceStep.class, STEP_NAME);
        RemoveBlankLineBeforeClosingBraceStep before = ProjectBuilder.builder().build().getObjects().newInstance(RemoveBlankLineBeforeClosingBraceStep.class, STEP_NAME);

        assertEquals("if (true) {\n\tcall();\n}", after.formatter().format("Example.java", "if (true) {\n\n\tcall();\n}"));
        assertEquals("if (true) {\n\tcall();\n}", before.formatter().format("Example.java", "if (true) {\n\tcall();\n\n}"));
    }

    @Test
    void insertsBlankLinesAroundIndentedBlocks() {
        InsertBlankLineBeforeIndentedBlockStep before = ProjectBuilder.builder().build().getObjects().newInstance(InsertBlankLineBeforeIndentedBlockStep.class, STEP_NAME);
        InsertBlankLineAfterIndentedBlockStep after = ProjectBuilder.builder().build().getObjects().newInstance(InsertBlankLineAfterIndentedBlockStep.class, STEP_NAME);

        String source = "\tint value = 1;\n\tif (value > 0) {\n\t\tvalue++;\n\t}\n\tvalue--;";
        String expectedBefore = "\tint value = 1;\n\n\tif (value > 0) {\n\t\tvalue++;\n\t}\n\tvalue--;";
        String expectedAfter = "\tint value = 1;\n\tif (value > 0) {\n\t\tvalue++;\n\t}\n\n\tvalue--;";

        assertEquals(expectedBefore, before.formatter().format("Example.java", source));
        assertEquals(expectedAfter, after.formatter().format("Example.java", source));
    }

    @Test
    void convertsLeadingWhitespaceToTabs() {
        LeadingWhitespaceToTabsStep step = ProjectBuilder.builder().build().getObjects().newInstance(LeadingWhitespaceToTabsStep.class, STEP_NAME);
        step.getTabWidth().set(4);

        String formatted = step.formatter().format("Example.java", "    value();\n\t    mixed();\n    * comment\n");
        assertEquals("\tvalue();\n\t\tmixed();\n    * comment\n", formatted);
    }

    @Test
    void ordersStaticAndRegularImports() {
        CheckstyleImportOrderStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleImportOrderStep.class, STEP_NAME);
        step.getGroups().set(java.util.List.of("java", "javax", "*", "net.fabricmc"));
        step.getSeparated().set(true);
        step.getOption().set("top");
        step.getSortStaticImportsAlphabetically().set(true);

        String source = "package test;\n\nimport test.Helper;\nimport javax.swing.JButton;\nimport static java.util.Collections.emptyList;\nimport java.util.Map;\nimport net.fabricmc.api.EnvType;\n\nclass Example {}\n";
        String expected = "package test;\n\nimport static java.util.Collections.emptyList;\n\nimport java.util.Map;\n\nimport javax.swing.JButton;\n\nimport test.Helper;\n\nimport net.fabricmc.api.EnvType;\n\nclass Example {}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void appliesImportLintRules() {
        CheckstyleImportLintStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleImportLintStep.class, STEP_NAME);
        step.getAvoidStarImport().set(true);
        step.getRemoveIllegalImports().set(true);
        step.getIllegalImportsReason().set("We use JSPECIFY in this project for nullable annotations.");
        step.getIllegalClasses().set(java.util.List.of("org.jetbrains.annotations.Nullable"));
        step.getIllegalPkgs().set(java.util.List.of());
        step.getRemoveRedundantImports().set(true);
        step.getRemoveUnusedImports().set(true);

        String source = "package test;\n\n"
                + "import java.util.*;\n"
                + "import java.util.Map;\n"
                + "import java.lang.String;\n"
                + "import org.jetbrains.annotations.Nullable;\n"
                + "import test.Helper;\n\n"
                + "class Example {\n"
                + "\tMap<String, String> values = java.util.Collections.emptyMap();\n"
                + "}\n\n"
                + "class Helper {}\n";

        String expected = "package test;\n\n"
                + "import java.util.Map;\n"
                + "import java.lang.String;\n"
                + "// ILLEGAL IMPORT: -> import org.jetbrains.annotations.Nullable;   ->   We use JSPECIFY in this project for nullable annotations.\n\n"
                + "class Example {\n"
                + "\tMap<String, String> values = java.util.Collections.emptyMap();\n"
                + "}\n\n"
                + "class Helper {}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void enforcesLeftCurlyPolicy() {
        CheckstyleLeftCurlyStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleLeftCurlyStep.class, STEP_NAME);
        step.getOption().set("eol");
        step.getIgnoreEnums().set(true);

        String source = "class Example\n{\n}\n";
        assertEquals("class Example {\n}\n", step.formatter().format("Example.java", source));
    }

    @Test
    void enforcesRightCurlySamePolicy() {
        CheckstyleRightCurlyStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleRightCurlyStep.class, STEP_NAME);
        step.getOption().set("same");

        String source = "if (flag) {\n\tcall();\n}\nelse {\n\tother();\n}\nint x = 1;\n";
        String expected = "if (flag) {\n\tcall();\n} else {\n\tother();\n}\nint x = 1;\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void insertsNeedBracesForConfiguredTokens() {
        CheckstyleNeedBracesStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleNeedBracesStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("LITERAL_IF", "LITERAL_FOR", "LITERAL_WHILE"));
        step.getAllowSingleLineStatement().set(false);
        step.getAllowEmptyLoopBody().set(false);

        String source = "if (ok)\n\tcall();\nfor (int i = 0; i < 1; i++)\n\tcall();\n";
        String expected = "if (ok) {\n\tcall();\n}\nfor (int i = 0; i < 1; i++) {\n\tcall();\n}\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void allowsSingleLineNeedBracesWhenConfigured() {
        CheckstyleNeedBracesStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleNeedBracesStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("LITERAL_IF"));
        step.getAllowSingleLineStatement().set(true);
        step.getAllowEmptyLoopBody().set(false);

        String source = "if (ok) return;\n";
        assertEquals(source, step.formatter().format("Example.java", source));
    }

    @Test
    void doesNotCorruptMultilineIfConditionWhenApplyingNeedBraces() {
        CheckstyleNeedBracesStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleNeedBracesStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("LITERAL_IF"));
        step.getAllowSingleLineStatement().set(false);
        step.getAllowEmptyLoopBody().set(false);

        String source = "if (inAreaTarget.x() < 0 ||\n"
                + "\tinAreaTarget.y() < 0 ||\n"
                + "\tinAreaTarget.z() < 0 ||\n"
                + "\tinAreaTarget.x() >= 1 ||\n"
                + "\tinAreaTarget.y() >= 1 ||\n"
                + "\tinAreaTarget.z() >= 1) {\n"
                + "\tthrow new IllegalArgumentException(\"Target is not in the current area.\");\n"
                + "}\n";

        String sourceTwo = "\t\tif (Minecraft.getInstance().player != null && !Minecraft.getInstance().player.isSpectator()) {\n"
            + "\t\t\tMeasurementRenderer.getInstance().renderMeasurements(\n"
            + "\t\t}\n"
            + "\t\t\t\tposeStack,\n"
            + "\t\t\t\tbufferSource,\n"
            + "\t\t\t\tpartialTickTime\n"
            + "\t\t\t);";

        assertEquals(source, step.formatter().format("Example.java", source));
        assertEquals(sourceTwo, step.formatter().format("Example.java", sourceTwo));
    }

    @Test
    void doesNotMangleNestedIfWhenBodyStartsWithMultilineIfHeader() {
        CheckstyleNeedBracesStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleNeedBracesStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("LITERAL_IF"));
        step.getAllowSingleLineStatement().set(false);
        step.getAllowEmptyLoopBody().set(false);

        String source = "package mod.chiselsandbits.logic;\n\n"
                + "import java.util.List;\n\n"
                + "import mod.chiselsandbits.api.chiseling.eligibility.IEligibilityAnalysisResult;\n"
                + "import mod.chiselsandbits.api.chiseling.eligibility.IEligibilityManager;\n"
                + "import mod.chiselsandbits.api.config.ICommonConfiguration;\n"
                + "import mod.chiselsandbits.item.MagnifyingGlassItem;\n\n"
                + "import net.minecraft.ChatFormatting;\n"
                + "import net.minecraft.client.Minecraft;\n"
                + "import net.minecraft.network.chat.Component;\n"
                + "import net.minecraft.world.item.BlockItem;\n"
                + "import net.minecraft.world.item.ItemStack;\n\n"
                + "public class MagnifyingGlassTooltipHandler\n"
                + "{\n"
                + "\tpublic static void onItemTooltip(final ItemStack itemStack, final List<Component> toolTips)\n\n"
                + "\t{\n"
                + "\t\tif (Minecraft.getInstance().player != null && ICommonConfiguration.getInstance().getEnableHelp().get())\n"
                + "\t\t\tif (Minecraft.getInstance().player.getMainHandItem().getItem() instanceof MagnifyingGlassItem\n"
                + "\t\t\t\t  || Minecraft.getInstance().player.getOffhandItem().getItem() instanceof MagnifyingGlassItem)\n\n"
                + "\t\t\t\tif (itemStack.getItem() instanceof BlockItem) {\n"
                + "\t\t\t\t\tfinal IEligibilityAnalysisResult result = IEligibilityManager.getInstance().analyse(itemStack);\n\n"
                + "\t\t\t\t\ttoolTips.add(\n"
                + "\t\t\t\t\t\tresult.canBeChiseled() || result.isAlreadyChiseled() ?\n"
                + "\t\t\t\t\t\t  result.getReason().withStyle(ChatFormatting.GREEN) :\n"
                + "\t\t\t\t\t\t  result.getReason().withStyle(ChatFormatting.RED)\n"
                + "\t\t\t\t\t);\n"
                + "\t\t\t\t}\n"
                + "\t}\n"
                + "}\n";

        assertEquals(source, step.formatter().format("MagnifyingGlassTooltipHandler.java", source));
    }

    @Test
    void wrapsUnbracedIfWithMultilineMethodCallBody() {
        CheckstyleNeedBracesStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleNeedBracesStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("LITERAL_IF"));
        step.getAllowSingleLineStatement().set(false);
        step.getAllowEmptyLoopBody().set(false);

        String source = "package mod.chiselsandbits.client.logic;\n\n"
                + "import com.mojang.blaze3d.vertex.PoseStack;\n"
                + "import mod.chiselsandbits.client.render.MeasurementRenderer;\n\n"
                + "import net.minecraft.client.Minecraft;\n"
                + "import net.minecraft.client.renderer.MultiBufferSource;\n\n"
                + "public class MeasurementsRenderHandler\n"
                + "{\n"
                + "\tpublic static void renderMeasurements(\n"
                + "\t\tfinal PoseStack poseStack,\n"
                + "\t\tfinal MultiBufferSource.BufferSource bufferSource,\n"
                + "\t\tfinal float partialTickTime)\n"
                + "\t{\n"
                + "\t\tif (Minecraft.getInstance().player != null && !Minecraft.getInstance().player.isSpectator())\n"
                + "\t\t\tMeasurementRenderer.getInstance().renderMeasurements(\n"
                + "\t\t\t\tposeStack,\n"
                + "\t\t\t\tbufferSource,\n"
                + "\t\t\t\tpartialTickTime\n"
                + "\t\t\t);\n"
                + "\t}\n"
                + "}\n";

        String expected = "package mod.chiselsandbits.client.logic;\n\n"
                + "import com.mojang.blaze3d.vertex.PoseStack;\n"
                + "import mod.chiselsandbits.client.render.MeasurementRenderer;\n\n"
                + "import net.minecraft.client.Minecraft;\n"
                + "import net.minecraft.client.renderer.MultiBufferSource;\n\n"
                + "public class MeasurementsRenderHandler\n"
                + "{\n"
                + "\tpublic static void renderMeasurements(\n"
                + "\t\tfinal PoseStack poseStack,\n"
                + "\t\tfinal MultiBufferSource.BufferSource bufferSource,\n"
                + "\t\tfinal float partialTickTime)\n"
                + "\t{\n"
                + "\t\tif (Minecraft.getInstance().player != null && !Minecraft.getInstance().player.isSpectator()) {\n"
                + "\t\t\tMeasurementRenderer.getInstance().renderMeasurements(\n"
                + "\t\t\t\tposeStack,\n"
                + "\t\t\t\tbufferSource,\n"
                + "\t\t\t\tpartialTickTime\n"
                + "\t\t\t);\n"
                + "\t\t}\n"
                + "\t}\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("MeasurementsRenderHandler.java", source));
    }

    @Test
    void keepsUsedImportsWhenRemovingUnusedImports() {
        CheckstyleImportLintStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleImportLintStep.class, STEP_NAME);
        step.getAvoidStarImport().set(false);
        step.getRemoveIllegalImports().set(false);
        step.getIllegalClasses().set(java.util.List.of());
        step.getIllegalPkgs().set(java.util.List.of());
        step.getRemoveRedundantImports().set(false);
        step.getRemoveUnusedImports().set(true);

        String source = "package test;\n\n"
                + "import java.util.Map;\n"
                + "import java.util.Map.Entry;\n\n"
                + "class Example {\n"
                + "\tprivate final Entry<String, String> entry;\n"
                + "\tprivate final Map<String, String> map;\n\n"
                + "\tExample(Entry<String, String> entry, Map<String, String> map) {\n"
                + "\t\tthis.entry = entry;\n"
                + "\t\tthis.map = map;\n"
                + "\t}\n"
                + "}\n";

        assertEquals(source, step.formatter().format("Example.java", source));
    }

    @Test
    void expandsWildcardImportsToAllUsedTypesAndStaticMembers() {
        CheckstyleImportLintStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleImportLintStep.class, STEP_NAME);
        step.getAvoidStarImport().set(true);
        step.getRemoveIllegalImports().set(false);
        step.getIllegalClasses().set(java.util.List.of());
        step.getIllegalPkgs().set(java.util.List.of());
        step.getRemoveRedundantImports().set(false);
        step.getRemoveUnusedImports().set(false);

        String source = "package test;\n\n"
                + "import java.util.*;\n"
                + "import static java.util.Collections.*;\n\n"
                + "class Example {\n"
                + "\tprivate final List<String> values = emptyList();\n"
                + "\tprivate final Map<String, String> mappings = emptyMap();\n"
                + "}\n";

        String expected = "package test;\n\n"
                + "import java.util.List;\n"
                + "import java.util.Map;\n"
                + "import static java.util.Collections.emptyList;\n"
                + "import static java.util.Collections.emptyMap;\n\n"
                + "class Example {\n"
                + "\tprivate final List<String> values = emptyList();\n"
                + "\tprivate final Map<String, String> mappings = emptyMap();\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void keepsImportsWhenTypeResolutionFails() {
        CheckstyleImportLintStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleImportLintStep.class, STEP_NAME);
        step.getAvoidStarImport().set(false);
        step.getRemoveIllegalImports().set(false);
        step.getIllegalClasses().set(java.util.List.of());
        step.getIllegalPkgs().set(java.util.List.of());
        step.getRemoveRedundantImports().set(false);
        step.getRemoveUnusedImports().set(true);

        String source = "package test;\n\n"
                + "import com.example.project.IAreaAccessor;\n\n"
                + "class BlockNeighborhoodEntry {\n"
                + "\tpublic IAreaAccessor getAccessor() {\n"
                + "\t\treturn null;\n"
                + "\t}\n"
                + "}\n";

        assertEquals(source, step.formatter().format("Example.java", source));
    }

    @Test
    void writesAnalyzerDiagnosticsWhenTypeResolutionFails() throws Exception {
        var project = ProjectBuilder.builder().build();
        CheckstyleImportLintStep step = project.getObjects().newInstance(CheckstyleImportLintStep.class, STEP_NAME);
        step.getAvoidStarImport().set(false);
        step.getRemoveIllegalImports().set(false);
        step.getIllegalClasses().set(java.util.List.of());
        step.getIllegalPkgs().set(java.util.List.of());
        step.getRemoveRedundantImports().set(false);
        step.getRemoveUnusedImports().set(true);

        String source = "package test;\n\n"
                + "import com.example.project.IAreaAccessor;\n\n"
                + "class BlockNeighborhoodEntry {\n"
                + "\tpublic IAreaAccessor getAccessor() {\n"
                + "\t\treturn null;\n"
                + "\t}\n"
                + "}\n";

        step.formatter().format("Example.java", source);

        Path diagnosticsPath = project.getLayout()
                .getBuildDirectory()
                .dir("formatting/parsing/errors")
                .get()
                .getAsFile()
                .toPath()
                .resolve("Example.java");

        assertTrue(Files.exists(diagnosticsPath));
        String diagnostics = Files.readString(diagnosticsPath);
        assertTrue(diagnostics.contains("ERROR:"));
    }

    @Test
    void preservesImportsUsedInVariableDeclarations() {
        CheckstyleImportLintStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleImportLintStep.class, STEP_NAME);
        step.getAvoidStarImport().set(false);
        step.getRemoveIllegalImports().set(false);
        step.getIllegalClasses().set(java.util.List.of());
        step.getIllegalPkgs().set(java.util.List.of());
        step.getRemoveRedundantImports().set(false);
        step.getRemoveUnusedImports().set(true);

        String source = "package mod.chiselsandbits.api.util;\n\n"
                + "import java.lang.reflect.Field;\n\n"
                + "public class ReflectionUtils {\n"
                + "\tpublic static void setField(final Object targetObject, final String fieldName, final Object value) {\n"
                + "\t\ttry {\n"
                + "\t\t\tField f = targetObject.getClass().getDeclaredField(fieldName);\n"
                + "\t\t\tf.setAccessible(true);\n"
                + "\t\t\tf.set(targetObject, value);\n"
                + "\t\t} catch (NoSuchFieldException | IllegalAccessException e) {\n"
                + "\t\t\tthrow new IllegalStateException(\"Failed to set value!\");\n"
                + "\t\t}\n"
                + "\t}\n\n"
                + "\tpublic static Object getField(final Object target, final String name) {\n"
                + "\t\ttry {\n"
                + "\t\t\tField f = target.getClass().getDeclaredField(name);\n"
                + "\t\t\tf.setAccessible(true);\n"
                + "\t\t\treturn f.get(target);\n"
                + "\t\t} catch (NoSuchFieldException | IllegalAccessException e) {\n"
                + "\t\t\tthrow new IllegalStateException(\"Failed to get value!\");\n"
                + "\t\t}\n"
                + "\t}\n"
                + "}\n";

        assertEquals(source, step.formatter().format("ReflectionUtils.java", source));
    }

    @Test
    void removesUnusedExternalImportWhenClasspathIsAvailable(@TempDir Path tempDir) throws Exception {
        Path srcRoot = tempDir.resolve("src");
        Path clsRoot = tempDir.resolve("classes");
        Files.createDirectories(srcRoot.resolve("com/example/project"));
        Files.createDirectories(clsRoot);

        Path usedType = srcRoot.resolve("com/example/project/IAreaAccessor.java");
        Path unusedType = srcRoot.resolve("com/example/project/UnusedType.java");
        Files.writeString(usedType, "package com.example.project; public interface IAreaAccessor {}\n");
        Files.writeString(unusedType, "package com.example.project; public final class UnusedType {}\n");

        int compileResult = ToolProvider.getSystemJavaCompiler().run(
                null,
                null,
                null,
                "-d", clsRoot.toString(),
                usedType.toString(),
                unusedType.toString()
        );
        assertEquals(0, compileResult);

        CheckstyleImportLintStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleImportLintStep.class, STEP_NAME);
        step.getAvoidStarImport().set(false);
        step.getRemoveIllegalImports().set(false);
        step.getIllegalClasses().set(java.util.List.of());
        step.getIllegalPkgs().set(java.util.List.of());
        step.getRemoveRedundantImports().set(false);
        step.getRemoveUnusedImports().set(true);
        step.getAnalysisClasspath().from(clsRoot.toFile());
        step.getAnalysisSourcepath().from(srcRoot.toFile());

        String source = "package test;\n\n"
                + "import com.example.project.IAreaAccessor;\n"
                + "import com.example.project.UnusedType;\n\n"
                + "class BlockNeighborhoodEntry {\n"
                + "\tpublic IAreaAccessor getAccessor() {\n"
                + "\t\treturn null;\n"
                + "\t}\n"
                + "}\n";

        String expected = "package test;\n\n"
                + "import com.example.project.IAreaAccessor;\n\n"
                + "class BlockNeighborhoodEntry {\n"
                + "\tpublic IAreaAccessor getAccessor() {\n"
                + "\t\treturn null;\n"
                + "\t}\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }
}
