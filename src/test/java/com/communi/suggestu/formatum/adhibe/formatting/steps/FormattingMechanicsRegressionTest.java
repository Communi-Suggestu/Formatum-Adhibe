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

        String source = """
            class Example {
            	void run(MultiStateBlockEntity multiStateBlockEntity, List<IStateEntryInfo> before) {
            		try (IBatchMutation batch = multiStateBlockEntity.batch()) {
            			multiStateBlockEntity.initializeWith(BlockInformation.AIR);
            			before.stream().forEach(
            					iStateEntryInfo -> {
            						try {
            				multiStateBlockEntity.setInAreaTarget(iStateEntryInfo.getBlockInformation(), iStateEntryInfo.getStartPoint());
            				} catch (SpaceOccupiedException e) {
            				//Noop
            				}
            					});
            		}
            	}
            }
            """;

        String expected = """
            class Example {
            	void run(MultiStateBlockEntity multiStateBlockEntity, List<IStateEntryInfo> before) {
            		try (IBatchMutation batch = multiStateBlockEntity.batch()) {
            			multiStateBlockEntity.initializeWith(BlockInformation.AIR);
            			before.stream().forEach(
                                        iStateEntryInfo -> {
                                            try {
                                                multiStateBlockEntity.setInAreaTarget(iStateEntryInfo.getBlockInformation(), iStateEntryInfo.getStartPoint());
                                            } catch (SpaceOccupiedException e) {
                                                //Noop
                                            }
                                        });
            		}
            	}
            }
            """;

        assertEquals(expected.replace("    ", "\t"), step.formatter().format("Example.java", source));
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

        String source = """
            class Example {
            	void render() {
            		if (potentialPlacingContext.isPresent()) {
            			final IChiselingContext placingContext = potentialPlacingContext.get();
            			if (placingContext.getMode() == chiselMode && potentialPlacingContext.get()
            					.getMode()
            					.isStillValid(playerEntity, potentialPlacingContext.get(), ChiselingOperation.PLACING)) {
            				IChiselContextPreviewRendererRegistry.getInstance()
            						.getCurrent()
            						.renderExistingContextsBoundingBox(worldRenderer, matrixStack, bufferSource, levelRenderState, partialTicks, placingContext);
            					} else {
            				ILocalChiselingContextCache.getInstance().clear(ChiselingOperation.PLACING);
            					}
            		}
            	}
            }""";

        String expected = """
            class Example {
            	void render() {
            		if (potentialPlacingContext.isPresent()) {
            			final IChiselingContext placingContext = potentialPlacingContext.get();
            			if (placingContext.getMode() == chiselMode && potentialPlacingContext.get()
            					.getMode()
            					.isStillValid(playerEntity, potentialPlacingContext.get(), ChiselingOperation.PLACING)) {
            				IChiselContextPreviewRendererRegistry.getInstance()
            						.getCurrent()
            						.renderExistingContextsBoundingBox(worldRenderer, matrixStack, bufferSource, levelRenderState, partialTicks, placingContext);
            			} else {
            				ILocalChiselingContextCache.getInstance().clear(ChiselingOperation.PLACING);
            			}
            		}
            	}
            }""";

        assertEquals(expected.replace("    ", "\t"), step.formatter().format("Example.java", source));
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
                + "\t\t\t\tinformation.isFluid()\n"
                + "\t\t\t\t\t? getFluidTintSources()\n"
                + "\t\t\t\t\t: Minecraft.getInstance().getBlockColors().getTintSources(information.blockState());\n"
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
                + "\t\t\t\t\tinformation.isFluid()\n"
                + "\t\t\t\t\t\t? getFluidTintSources()\n"
                + "\t\t\t\t\t\t: Minecraft.getInstance().getBlockColors().getTintSources(information.blockState());\n"
                + "\t\t\tquadsByTints.computeIfAbsent(tints, (_) -> new ArrayList<>())\n"
                + "\t\t\t\t\t.add(generatedQuad.quad());\n"
                + "\t\t};\n"
                + "\n"
                + "\t\tfinal List<BitBlockModelPart> parts =\n"
                + "\t\t\t\tquadsByTints.entrySet().stream()\n"
                + "\t\t\t\t\t.map(c -> new BitBlockModelPart(c.getValue(), c.getKey()))\n"
                + "\t\t\t\t\t.toList();\n"
                + "\t}\n"
                + "}\n";

        String afterWhitespace = whitespace.formatter().format("Example.java", source);
        String formatted = indentation.formatter().format("Example.java", afterWhitespace);
        assertEquals(expected, formatted);
    }

    @Test
    void runSimulatedMethodWithContinuationOfIndentsOnAssignmentAndTerniaries() {
        CheckstyleWhitespaceAroundStep whitespace = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAroundStep.class, STEP_NAME);
        whitespace.getTokens().set(java.util.List.of("ASSIGN", "QUESTION", "COLON"));
        CheckstyleIndentationStep indentation = indentationStep();

        String source = """
            public BitBlockModelInformation build(final BlockAndTintGetter surroundings) {
                final Map<IntList, List<BakedQuad>> quadsByTints = new HashMap<>();
        
                final SingleBlockBlockAndTintGetter blockAndTintGetter = new SingleBlockBlockAndTintGetter.Builder() \s
                        .withBlockState(information().blockState())
                        .withBlockEntity(information()::newBlockEntityAtZero)
                        .withSource(surroundings)
                        .createSingleBlockBlockAndTintGetter();
        
                for (final Direction myFace : Direction.values()) {
                    QuadGenerationUtils.generateQuads(
                            information(),
                            myFace,
                            blockAndTintGetter,
                            BlockPos.ZERO,
                            myFace.getAxisDirection() == Direction.AxisDirection.POSITIVE ? TO : FROM,
                            myFace.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? TO : FROM,
                            (layer, quad) -> {
                                if (layer.material().tintIndex() != -1) {
                                    quad.tintIndex(0);
                                }
                            },
        
        
                            generatedQuad -> {
                                final List<BlockTintSource> tintSources =
                            information.isFluid()
                                        ? getFluidTintSources()
                                        : Minecraft.getInstance().getBlockColors().getTintSources(information.blockState());
                                final IntList tints = new IntArrayList(tintSources.size());
                                tintSources.forEach(source -> {
                                    tints.add(
                                            source.colorInWorld(information.blockState(),
                                                    blockAndTintGetter,
                                                    BlockPos.ZERO)
                                    );
                                });
        
                                quadsByTints.computeIfAbsent(tints, (_) -> new ArrayList<>())
                                .add(generatedQuad.quad());
                            });
                }
        
                final List<BitBlockModelPart> parts =
                    quadsByTints.entrySet().stream()
                        .map(c -> new BitBlockModelPart(c.getValue(), c.getKey()))
                        .toList();
        
                return new BitBlockModelInformation(parts, true, isLarge());
            }""";

        String expected = """
            public BitBlockModelInformation build(final BlockAndTintGetter surroundings) {
            	final Map<IntList, List<BakedQuad>> quadsByTints = new HashMap<>();
            
            	final SingleBlockBlockAndTintGetter blockAndTintGetter = new SingleBlockBlockAndTintGetter.Builder()
            			.withBlockState(information().blockState())
            			.withBlockEntity(information()::newBlockEntityAtZero)
            			.withSource(surroundings)
            			.createSingleBlockBlockAndTintGetter();
            
            	for (final Direction myFace : Direction.values()) {
            		QuadGenerationUtils.generateQuads(
            				information(),
            				myFace,
            				blockAndTintGetter,
            				BlockPos.ZERO,
            				myFace.getAxisDirection() == Direction.AxisDirection.POSITIVE ? TO : FROM,
            				myFace.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? TO : FROM,
            				(layer, quad) -> {
            					if (layer.material().tintIndex() != -1) {
            						quad.tintIndex(0);
            					}
            				},
            
            
            				generatedQuad -> {
            					final List<BlockTintSource> tintSources =
            							information.isFluid()
                                            ? getFluidTintSources()
                                            : Minecraft.getInstance().getBlockColors().getTintSources(information.blockState());
            					final IntList tints = new IntArrayList(tintSources.size());
            					tintSources.forEach(source -> {
            						tints.add(
            								source.colorInWorld(information.blockState(),
            										blockAndTintGetter,
            										BlockPos.ZERO)
            						);
            					});
            
            					quadsByTints.computeIfAbsent(tints, (_) -> new ArrayList<>())
                                        .add(generatedQuad.quad());
            				});
            	}
            
            	final List<BitBlockModelPart> parts =
            			quadsByTints.entrySet().stream()
            				.map(c -> new BitBlockModelPart(c.getValue(), c.getKey()))
            				.toList();
            
            	return new BitBlockModelInformation(parts, true, isLarge());
            }""";

        String afterWhitespace = whitespace.formatter().format("Example.java", source);
        String formatted = indentation.formatter().format("Example.java", afterWhitespace);
        assertEquals(expected.replace("    ", "\t"), formatted);
    }

    @Test
    void runSimulatedMethodWithContinuationOfIndentsOnIfSwitches() {
        CheckstyleWhitespaceAroundStep whitespace = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAroundStep.class, STEP_NAME);
        whitespace.getTokens().set(java.util.List.of("ASSIGN", "QUESTION", "COLON"));
        CheckstyleIndentationStep indentation = indentationStep();

        String source = """
            public static List<AABB> compressStates(
            			final IAreaAccessor accessor,
            			final CollisionType sizeType) {
            		final BuildingState state = new BuildingState();
            
            		//X == REGION
            		//Y == FACE
            
            		//noinspection Convert2Lambda We need this to be pre-compiled
            		accessor.forEachWithPositionMutator(
            				IPositionMutator.xyz(),
            			new Consumer<>() {
            				@Override
            				public void accept(final IStateEntryInfo stateEntryInfo) {
            					if (state.getRegionBuildingAxisValue() != stateEntryInfo.getStartPoint().x()) {
            						state.setCurrentBox(null, null);
            					}
            
            					state.setRegionBuildingAxisValue(stateEntryInfo.getStartPoint().x());
            
            					if (state.getFaceBuildingAxisValue() != stateEntryInfo.getStartPoint().y()) {
            						state.setCurrentBox(null, null);
            					}
            
            					state.setFaceBuildingAxisValue(stateEntryInfo.getStartPoint().y());
            
            					final Optional<Vec3> previousCenterPoint = state.getLastCenter();
            					final Vec3 centerPoint = stateEntryInfo.getCenterPoint();
            					state.onNextEntry(centerPoint);
            
            					final Optional<Direction> stepDirection = previousCenterPoint.flatMap(
            							d -> DirectionUtils.getDirectionVectorBetweenIfAligned(centerPoint, d)
            					);
            
            					final Optional<AABB> potentialEntryData = buildBoundingBox(stateEntryInfo, sizeType);
            
            					if (potentialEntryData.isEmpty()) {
            						state.setCurrentBox(null, centerPoint);
            						return;
            					}
            
            					final AABB entryData = potentialEntryData.get();
            
            					if (state.getCurrentBox() != null) {
            						if (stepDirection
            							.map(direction -> AABBUtils.areBoxesNeighbors(state.getCurrentBox(), entryData, direction))
            							.filter(b -> b)
            							.isPresent()) {
            						state.expandCurrentBoxToInclude(entryData, centerPoint);
            
            						if (attemptMergeWithNeighbors(state, centerPoint, state.getCurrentBox())) {
            							return;
            						}
            
            						return;
            					}
            					}
            
            					if (attemptMergeWithNeighbors(state, centerPoint, entryData)) {
            						return;
            					}
            
            					state.setCurrentBox(potentialEntryData.get(), centerPoint);
            				}
            			});
            		return Lists.newArrayList(state.getBoxes());
            	}""";

        String expected = """
            public static List<AABB> compressStates(
            		final IAreaAccessor accessor,
            		final CollisionType sizeType) {
            	final BuildingState state = new BuildingState();
            
            	//X == REGION
            	//Y == FACE
            
            	//noinspection Convert2Lambda We need this to be pre-compiled
            	accessor.forEachWithPositionMutator(
            			IPositionMutator.xyz(),
            			new Consumer<>() {
            				@Override
            				public void accept(final IStateEntryInfo stateEntryInfo) {
            					if (state.getRegionBuildingAxisValue() != stateEntryInfo.getStartPoint().x()) {
            						state.setCurrentBox(null, null);
            					}
            
            					state.setRegionBuildingAxisValue(stateEntryInfo.getStartPoint().x());
            
            					if (state.getFaceBuildingAxisValue() != stateEntryInfo.getStartPoint().y()) {
            						state.setCurrentBox(null, null);
            					}
            
            					state.setFaceBuildingAxisValue(stateEntryInfo.getStartPoint().y());
            
            					final Optional<Vec3> previousCenterPoint = state.getLastCenter();
            					final Vec3 centerPoint = stateEntryInfo.getCenterPoint();
            					state.onNextEntry(centerPoint);
            
            					final Optional<Direction> stepDirection = previousCenterPoint.flatMap(
            							d -> DirectionUtils.getDirectionVectorBetweenIfAligned(centerPoint, d)
            					);
            
            					final Optional<AABB> potentialEntryData = buildBoundingBox(stateEntryInfo, sizeType);
            
            					if (potentialEntryData.isEmpty()) {
            						state.setCurrentBox(null, centerPoint);
            						return;
            					}
            
            					final AABB entryData = potentialEntryData.get();
            
            					if (state.getCurrentBox() != null) {
            						if (stepDirection
            								.map(direction -> AABBUtils.areBoxesNeighbors(state.getCurrentBox(), entryData, direction))
            								.filter(b -> b)
            								.isPresent()) {
            							state.expandCurrentBoxToInclude(entryData, centerPoint);
            
            							if (attemptMergeWithNeighbors(state, centerPoint, state.getCurrentBox())) {
            								return;
            							}
            
            							return;
            						}
            					}
            
            					if (attemptMergeWithNeighbors(state, centerPoint, entryData)) {
            						return;
            					}
            
            					state.setCurrentBox(potentialEntryData.get(), centerPoint);
            				}
            			});
            	return Lists.newArrayList(state.getBoxes());
            }""";

        String afterWhitespace = whitespace.formatter().format("Example.java", source);
        String formatted = indentation.formatter().format("Example.java", afterWhitespace);
        assertEquals(expected, formatted);
    }
}

