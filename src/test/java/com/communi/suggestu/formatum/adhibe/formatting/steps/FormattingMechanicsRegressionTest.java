package com.communi.suggestu.formatum.adhibe.formatting.steps;

import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormattingMechanicsRegressionTest {
    private static final String STEP_NAME = "regressionStep";

    @Test
    void alignsRecordClosingParenBeforeImplementsClause() {
        CheckstyleIndentationStep step = indentationStep();

        String source = "class Example {\n"
                + "\tpublic record Config(\n"
                + "\t\t\tString namespace,\n"
                + "\t\t\tString registry\n"
                + "\t\t\t) implements NamespacedModuleConfig {}\n"
                + "}\n";

        String expected = "class Example {\n"
                + "\tpublic record Config(\n"
                + "\t\t\tString namespace,\n"
                + "\t\t\tString registry\n"
                + "\t) implements NamespacedModuleConfig {}\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void alignsTryWithResourcesClosingBraceToTryIndent() {
        CheckstyleIndentationStep step = indentationStep();

        String source = "class Example {\n"
                + "\tvoid run() throws Exception {\n"
                + "\t\ttry(FileOutputStream stream = new FileOutputStream(\"out.txt\");\n"
                + "\t\t\t\tOutputStreamWriter writer = new OutputStreamWriter(stream)) {\n"
                + "\t\t\twrite(writer);\n"
                + "\t\t\t\t\t}\n"
                + "\t}\n"
                + "}\n";

        String expected = "class Example {\n"
                + "\tvoid run() throws Exception {\n"
                + "\t\ttry(FileOutputStream stream = new FileOutputStream(\"out.txt\");\n"
                + "\t\t\t\tOutputStreamWriter writer = new OutputStreamWriter(stream)) {\n"
                + "\t\t\twrite(writer);\n"
                + "\t\t}\n"
                + "\t}\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void indentsFluentChainLinesUsingWrappingIndentation() {
        CheckstyleIndentationStep step = indentationStep();

        String source = "class Example {\n"
                + "\tvoid run() {\n"
                + "\t\tRegistry<?> registry = context.registryAccess()\n"
                + "\t\t\t.registries()\n"
                + "\t\t\t.filter(entry -> entry.active())\n"
                + "\t\t\t.findFirst()\n"
                + "\t\t\t.orElseThrow();\n"
                + "\t}\n"
                + "}\n";

        String expected = "class Example {\n"
                + "\tvoid run() {\n"
                + "\t\tRegistry<?> registry = context.registryAccess()\n"
                + "\t\t\t\t.registries()\n"
                + "\t\t\t\t.filter(entry -> entry.active())\n"
                + "\t\t\t\t.findFirst()\n"
                + "\t\t\t\t.orElseThrow();\n"
                + "\t}\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void indentsTryCatchInsideWrappedLambdaBody() {
        CheckstyleIndentationStep step = indentationStep();

        String source = "class Example {\n"
                + "\tvoid run(MultiStateBlockEntity multiStateBlockEntity, List<IStateEntryInfo> before) {\n"
                + "\t\ttry (IBatchMutation batch = multiStateBlockEntity.batch()) {\n"
                + "\t\t\tmultiStateBlockEntity.initializeWith(BlockInformation.AIR);\n"
                + "\t\t\tbefore.stream().forEach(\n"
                + "\t\t\t\t\tiStateEntryInfo -> {\n"
                + "\t\t\t\t\t\ttry {\n"
                + "\t\t\t\tmultiStateBlockEntity.setInAreaTarget(iStateEntryInfo.getBlockInformation(), iStateEntryInfo.getStartPoint());\n"
                + "\t\t\t\t} catch (SpaceOccupiedException e) {\n"
                + "\t\t\t\t//Noop\n"
                + "\t\t\t\t}\n"
                + "\t\t\t\t\t});\n"
                + "\t\t}\n"
                + "\t}\n"
                + "}\n";

        String expected = "class Example {\n"
                + "\tvoid run(MultiStateBlockEntity multiStateBlockEntity, List<IStateEntryInfo> before) {\n"
                + "\t\ttry (IBatchMutation batch = multiStateBlockEntity.batch()) {\n"
                + "\t\t\tmultiStateBlockEntity.initializeWith(BlockInformation.AIR);\n"
                + "\t\t\tbefore.stream().forEach(\n"
                + "\t\t\t\t\tiStateEntryInfo -> {\n"
                + "\t\t\t\t\t\ttry {\n"
                + "\t\t\t\t\t\t\tmultiStateBlockEntity.setInAreaTarget(iStateEntryInfo.getBlockInformation(), iStateEntryInfo.getStartPoint());\n"
                + "\t\t\t\t\t\t} catch (SpaceOccupiedException e) {\n"
                + "\t\t\t\t\t\t\t//Noop\n"
                + "\t\t\t\t\t\t}\n"
                + "\t\t\t\t\t});\n"
                + "\t\t}\n"
                + "\t}\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void addsWhitespaceAfterTryKeyword() {
        CheckstyleWhitespaceAfterStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAfterStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("LITERAL_TRY"));

        String source = "try(FileOutputStream stream = open()) {\n\twork();\n}\n";
        String expected = "try (FileOutputStream stream = open()) {\n\twork();\n}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void addsWhitespaceInsideEmptyRecordBodyBraces() {
        CheckstyleWhitespaceAroundStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAroundStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("LCURLY", "RCURLY"));

        String source = "record Config(String namespace) implements NamespacedModuleConfig {}\n";
        String expected = "record Config(String namespace) implements NamespacedModuleConfig { }\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    private static CheckstyleIndentationStep indentationStep() {
        CheckstyleIndentationStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleIndentationStep.class, STEP_NAME);
        step.getBasicOffset().set(4);
        step.getCaseIndent().set(0);
        step.getThrowsIndent().set(4);
        step.getArrayInitIndent().set(4);
        step.getLineWrappingIndentation().set(8);
        return step;
    }

    @Test
    void preservesWhitespaceAroundComparisonOperatorsInIfHeaders() {
        CheckstyleWhitespaceAroundStep whitespaceStep = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAroundStep.class, STEP_NAME);
        whitespaceStep.getTokens().set(java.util.List.of("ASSIGN", "LT", "GT", "LE", "GE"));

        String source = "class Example {\n"
                + "\tvoid process() {\n"
                + "\t\tif (index <this.bitSlots.size()) {\n"
                + "\t\t\tcall();\n"
                + "\t\t}\n"
                + "\t}\n"
                + "}\n";

        String expected = "class Example {\n"
                + "\tvoid process() {\n"
                + "\t\tif (index < this.bitSlots.size()) {\n"
                + "\t\t\tcall();\n"
                + "\t\t}\n"
                + "\t}\n"
                + "}\n";

        assertEquals(expected, whitespaceStep.formatter().format("Example.java", source));
    }

    @Test
    void alignsClosingBraceInWrappedElseIfBlock() {
        CheckstyleIndentationStep step = indentationStep();

        String source = "class Example {\n"
                + "\tvoid process(int index) {\n"
                + "\t\tif (index < this.bitSlots.size()) {\n"
                + "\t\t\tcall();\n"
                + "\t\t}\n"
                + "\n"
                + "\t\telse if (\n"
                + "\t\t\t\t!this.moveItemStackTo(slotStack, 0, this.bitSlots.size(), false)) {\n"
                + "\t\t\treturn;\n"
                + "\t\t\t}\n"
                + "\t}\n"
                + "}\n";

        String expected = "class Example {\n"
                + "\tvoid process(int index) {\n"
                + "\t\tif (index < this.bitSlots.size()) {\n"
                + "\t\t\tcall();\n"
                + "\t\t}\n"
                + "\n"
                + "\t\telse if (\n"
                + "\t\t\t\t!this.moveItemStackTo(slotStack, 0, this.bitSlots.size(), false)) {\n"
                + "\t\t\treturn;\n"
                + "\t\t}\n"
                + "\t}\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void keepsBodyAndElseAlignedForWrappedIfConditionChains() {
        CheckstyleIndentationStep step = indentationStep();

        String source = "class Example {\n"
                + "\tvoid render() {\n"
                + "\t\tif (potentialPlacingContext.isPresent()) {\n"
                + "\t\t\tfinal IChiselingContext placingContext = potentialPlacingContext.get();\n"
                + "\t\t\tif (placingContext.getMode() == chiselMode && potentialPlacingContext.get()\n"
                + "\t\t\t\t\t.getMode()\n"
                + "\t\t\t\t\t.isStillValid(playerEntity, potentialPlacingContext.get(), ChiselingOperation.PLACING)) {\n"
                + "\t\t\t\tIChiselContextPreviewRendererRegistry.getInstance()\n"
                + "\t\t\t\t\t\t.getCurrent()\n"
                + "\t\t\t\t\t\t.renderExistingContextsBoundingBox(worldRenderer, matrixStack, bufferSource, levelRenderState, partialTicks, placingContext);\n"
                + "\t\t\t\t\t} else {\n"
                + "\t\t\t\tILocalChiselingContextCache.getInstance().clear(ChiselingOperation.PLACING);\n"
                + "\t\t\t\t\t}\n"
                + "\t\t}\n"
                + "\t}\n"
                + "}\n";

        String expected = "class Example {\n"
                + "\tvoid render() {\n"
                + "\t\tif (potentialPlacingContext.isPresent()) {\n"
                + "\t\t\tfinal IChiselingContext placingContext = potentialPlacingContext.get();\n"
                + "\t\t\tif (placingContext.getMode() == chiselMode && potentialPlacingContext.get()\n"
                + "\t\t\t\t\t.getMode()\n"
                + "\t\t\t\t\t.isStillValid(playerEntity, potentialPlacingContext.get(), ChiselingOperation.PLACING)) {\n"
                + "\t\t\t\tIChiselContextPreviewRendererRegistry.getInstance()\n"
                + "\t\t\t\t\t\t.getCurrent()\n"
                + "\t\t\t\t\t\t.renderExistingContextsBoundingBox(worldRenderer, matrixStack, bufferSource, levelRenderState, partialTicks, placingContext);\n"
                + "\t\t\t} else {\n"
                + "\t\t\t\tILocalChiselingContextCache.getInstance().clear(ChiselingOperation.PLACING);\n"
                + "\t\t\t}\n"
                + "\t\t}\n"
                + "\t}\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void keepsWrappedAssignmentAndTernaryIndentedWithoutTrailingAssignSpace() {
        CheckstyleWhitespaceAroundStep whitespace = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAroundStep.class, STEP_NAME);
        whitespace.getTokens().set(java.util.List.of("ASSIGN", "QUESTION", "COLON"));
        CheckstyleIndentationStep indentation = indentationStep();

        String source = "class Example {\n"
                + "\tvoid run() {\n"
                + "\t\tfinal List<BlockTintSource> tintSources = \n"
                + "\t\tinformation.isFluid()\n"
                + "\t\t\t\t? getFluidTintSources()\n"
                + "\t\t\t\t: Minecraft.getInstance().getBlockColors().getTintSources(information.blockState());\n"
                + "\t}\n"
                + "}\n";

        String expected = "class Example {\n"
                + "\tvoid run() {\n"
                + "\t\tfinal List<BlockTintSource> tintSources =\n"
                + "\t\t\tinformation.isFluid()\n"
                + "\t\t\t\t? getFluidTintSources()\n"
                + "\t\t\t\t: Minecraft.getInstance().getBlockColors().getTintSources(information.blockState());\n"
                + "\t}\n"
                + "}\n";

        String afterWhitespace = whitespace.formatter().format("Example.java", source);
        String formatted = indentation.formatter().format("Example.java", afterWhitespace);
        assertEquals(expected, formatted);
    }

    @Test
    void formatsWrappedAssignmentsAndFluentContinuationInLambdaBody() {
        CheckstyleWhitespaceAroundStep whitespace = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAroundStep.class, STEP_NAME);
        whitespace.getTokens().set(java.util.List.of("ASSIGN", "QUESTION", "COLON"));
        CheckstyleIndentationStep indentation = indentationStep();

        String source = "class Example {\n"
                + "\tvoid build() {\n"
                + "\t\tgeneratedQuad -> {\n"
                + "\t\t\tfinal List<BlockTintSource> tintSources = \n"
                + "\t\t\tinformation.isFluid()\n"
                + "\t\t\t\t\t? getFluidTintSources()\n"
                + "\t\t\t\t\t: Minecraft.getInstance().getBlockColors().getTintSources(information.blockState());\n"
                + "\t\t\tquadsByTints.computeIfAbsent(tints, (_) -> new ArrayList<>())\n"
                + "\t\t\t.add(generatedQuad.quad());\n"
                + "\t\t};\n"
                + "\n"
                + "\t\tfinal List<BitBlockModelPart> parts =\n"
                + "\t\tquadsByTints.entrySet().stream()\n"
                + "\t\t\t.map(c -> new BitBlockModelPart(c.getValue(), c.getKey()))\n"
                + "\t\t\t.toList();\n"
                + "\t}\n"
                + "}\n";

        String expected = "class Example {\n"
                + "\tvoid build() {\n"
                + "\t\tgeneratedQuad -> {\n"
                + "\t\t\tfinal List<BlockTintSource> tintSources =\n"
                + "\t\t\t\tinformation.isFluid()\n"
                + "\t\t\t\t\t? getFluidTintSources()\n"
                + "\t\t\t\t\t: Minecraft.getInstance().getBlockColors().getTintSources(information.blockState());\n"
                + "\t\t\tquadsByTints.computeIfAbsent(tints, (_) -> new ArrayList<>())\n"
                + "\t\t\t\t\t.add(generatedQuad.quad());\n"
                + "\t\t};\n"
                + "\n"
                + "\t\tfinal List<BitBlockModelPart> parts =\n"
                + "\t\t\tquadsByTints.entrySet().stream()\n"
                + "\t\t\t\t.map(c -> new BitBlockModelPart(c.getValue(), c.getKey()))\n"
                + "\t\t\t\t.toList();\n"
                + "\t}\n"
                + "}\n";

        String afterWhitespace = whitespace.formatter().format("Example.java", source);
        String formatted = indentation.formatter().format("Example.java", afterWhitespace);
        assertEquals(expected, formatted);
    }

    @Test
    void runSimulatedMethodWithComplexIndentations() {
        CheckstyleWhitespaceAroundStep whitespace = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAroundStep.class, STEP_NAME);
        whitespace.getTokens().set(java.util.List.of("ASSIGN", "QUESTION", "COLON"));
        CheckstyleIndentationStep indentation = indentationStep();

        String source = "\tpublic BitBlockModelInformation build(final BlockAndTintGetter surroundings) {\n"
            + "\t\tfinal Map<IntList, List<BakedQuad>> quadsByTints = new HashMap<>();\n"
            + "\n"
            + "\t\tfinal SingleBlockBlockAndTintGetter blockAndTintGetter = new SingleBlockBlockAndTintGetter.Builder()  \n"
            + "\t\t\t\t.withBlockState(information().blockState())\n"
            + "\t\t\t\t.withBlockEntity(information()::newBlockEntityAtZero)\n"
            + "\t\t\t\t.withSource(surroundings)\n"
            + "\t\t\t\t.createSingleBlockBlockAndTintGetter();\n"
            + "\n"
            + "\t\tfor (final Direction myFace : Direction.values()) {\n"
            + "\t\t\tQuadGenerationUtils.generateQuads(\n"
            + "\t\t\t\t\tinformation(),\n"
            + "\t\t\t\t\tmyFace,\n"
            + "\t\t\t\t\tblockAndTintGetter,\n"
            + "\t\t\t\t\tBlockPos.ZERO,\n"
            + "\t\t\t\t\tmyFace.getAxisDirection() == Direction.AxisDirection.POSITIVE ? TO : FROM,\n"
            + "\t\t\t\t\tmyFace.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? TO : FROM,\n"
            + "\t\t\t\t\t(layer, quad) -> {\n"
            + "\t\t\t\t\t\tif (layer.material().tintIndex() != -1) {\n"
            + "\t\t\t\t\t\t\tquad.tintIndex(0);\n"
            + "\t\t\t\t\t\t}\n"
            + "\t\t\t\t\t},\n"
            + "\n"
            + "\n"
            + "\t\t\t\t\tgeneratedQuad -> {\n"
            + "\t\t\t\t\t\tfinal List<BlockTintSource> tintSources =\n"
            + "\t\t\t\t\tinformation.isFluid()\n"
            + "\t\t\t\t\t\t\t\t? getFluidTintSources()\n"
            + "\t\t\t\t\t\t\t\t: Minecraft.getInstance().getBlockColors().getTintSources(information.blockState());\n"
            + "\t\t\t\t\t\tfinal IntList tints = new IntArrayList(tintSources.size());\n"
            + "\t\t\t\t\t\ttintSources.forEach(source -> {\n"
            + "\t\t\t\t\t\t\ttints.add(\n"
            + "\t\t\t\t\t\t\t\t\tsource.colorInWorld(information.blockState(),\n"
            + "\t\t\t\t\t\t\t\t\t\t\tblockAndTintGetter,\n"
            + "\t\t\t\t\t\t\t\t\t\t\tBlockPos.ZERO)\n"
            + "\t\t\t\t\t\t\t);\n"
            + "\t\t\t\t\t\t});\n"
            + "\n"
            + "\t\t\t\t\t\tquadsByTints.computeIfAbsent(tints, (_) -> new ArrayList<>())\n"
            + "\t\t\t\t\t\t.add(generatedQuad.quad());\n"
            + "\t\t\t\t\t});\n"
            + "\t\t}\n"
            + "\n"
            + "\t\tfinal List<BitBlockModelPart> parts =\n"
            + "\t\t\tquadsByTints.entrySet().stream()\n"
            + "\t\t\t\t.map(c -> new BitBlockModelPart(c.getValue(), c.getKey()))\n"
            + "\t\t\t\t.toList();\n"
            + "\n"
            + "\t\treturn new BitBlockModelInformation(parts, true, isLarge());\n"
            + "\t}";

        String expected = "public BitBlockModelInformation build(final BlockAndTintGetter surroundings) {\n"
            + "\tfinal Map<IntList, List<BakedQuad>> quadsByTints = new HashMap<>();\n"
            + "\n"
            + "\tfinal SingleBlockBlockAndTintGetter blockAndTintGetter = new SingleBlockBlockAndTintGetter.Builder()\n"
            + "\t\t\t.withBlockState(information().blockState())\n"
            + "\t\t\t.withBlockEntity(information()::newBlockEntityAtZero)\n"
            + "\t\t\t.withSource(surroundings)\n"
            + "\t\t\t.createSingleBlockBlockAndTintGetter();\n"
            + "\n"
            + "\tfor (final Direction myFace : Direction.values()) {\n"
            + "\t\tQuadGenerationUtils.generateQuads(\n"
            + "\t\t\t\tinformation(),\n"
            + "\t\t\t\tmyFace,\n"
            + "\t\t\t\tblockAndTintGetter,\n"
            + "\t\t\t\tBlockPos.ZERO,\n"
            + "\t\t\t\tmyFace.getAxisDirection() == Direction.AxisDirection.POSITIVE ? TO : FROM,\n"
            + "\t\t\t\tmyFace.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? TO : FROM,\n"
            + "\t\t\t\t(layer, quad) -> {\n"
            + "\t\t\t\t\tif (layer.material().tintIndex() != -1) {\n"
            + "\t\t\t\t\t\tquad.tintIndex(0);\n"
            + "\t\t\t\t\t}\n"
            + "\t\t\t\t},\n"
            + "\n"
            + "\n"
            + "\t\t\t\tgeneratedQuad -> {\n"
            + "\t\t\t\t\tfinal List<BlockTintSource> tintSources =\n"
            + "\t\t\t\t\t\t\tinformation.isFluid()\n"
            + "\t\t\t\t\t\t\t\t\t\t? getFluidTintSources()\n"
            + "\t\t\t\t\t\t\t\t\t\t: Minecraft.getInstance().getBlockColors().getTintSources(information.blockState());\n"
            + "\t\t\t\t\tfinal IntList tints = new IntArrayList(tintSources.size());\n"
            + "\t\t\t\t\ttintSources.forEach(source -> {\n"
            + "\t\t\t\t\t\ttints.add(\n"
            + "\t\t\t\t\t\t\t\tsource.colorInWorld(information.blockState(),\n"
            + "\t\t\t\t\t\t\t\t\t\tblockAndTintGetter,\n"
            + "\t\t\t\t\t\t\t\t\t\tBlockPos.ZERO)\n"
            + "\t\t\t\t\t\t);\n"
            + "\t\t\t\t\t});\n"
            + "\n"
            + "\t\t\t\t\tquadsByTints.computeIfAbsent(tints, (_) -> new ArrayList<>())\n"
            + "\t\t\t\t\t\t.add(generatedQuad.quad());\n"
            + "\t\t\t\t});\n"
            + "\t}\n"
            + "\n"
            + "\tfinal List<BitBlockModelPart> parts =\n"
            + "\t\tquadsByTints.entrySet().stream()\n"
            + "\t\t\t.map(c -> new BitBlockModelPart(c.getValue(), c.getKey()))\n"
            + "\t\t\t.toList();\n"
            + "\n"
            + "\treturn new BitBlockModelInformation(parts, true, isLarge());\n"
            + "}";

        String afterWhitespace = whitespace.formatter().format("Example.java", source);
        String formatted = indentation.formatter().format("Example.java", afterWhitespace);
        assertEquals(expected, formatted);
    }
}

