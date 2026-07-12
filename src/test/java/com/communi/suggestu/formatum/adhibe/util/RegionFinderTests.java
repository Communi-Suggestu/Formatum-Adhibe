package com.communi.suggestu.formatum.adhibe.util;

import com.communi.suggestu.formatum.adhibe.utils.RegionFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RegionFinderTests
{

    private RegionFinder sut;

    @BeforeEach
    public void setUp() {
        sut = new RegionFinder();
    }

    @AfterEach
    public void tearDown() {
        sut = null;
    }

    @Test
    public void discoverSimpleClassRegion() {

        final String classContent = """
            public class Something {
            
            }
            """;

        final RegionFinder.Region root = sut.findRoot(classContent);

        assertNotNull(root);
        assertEquals(0, root.start().totalCharacterOffset());
        assertEquals(classContent.length(), root.end().totalCharacterOffset());
        assertEquals(0, root.start().lineOffset());
        assertEquals(0, root.start().inLineOffset());
        assertEquals(2, root.end().lineOffset());
        assertEquals(2, root.end().inLineOffset());

        assertEquals(1, root.children().length);

        var child =  root.children()[0];
        assertNotNull(child);
        assertEquals("public class Something ".length(), child.start().totalCharacterOffset());
        assertEquals("public class Something ".length(), child.start().inLineOffset());
        assertEquals(0, child.start().lineOffset());
        assertEquals("public class Something {\n\n}".length(), child.end().totalCharacterOffset());
        assertEquals(2, child.end().lineOffset());
        assertEquals(1, child.end().inLineOffset());
    }

    @Test
    public void discoverClassWithMethodRegion()
    {
        final String classContent = """
            public class Something {
            
                public void main(String[] args) {
                    System.out.println(1);
                }
            }
            """;

        RegionFinder.Region root = sut.findRoot(classContent);
        assertNotNull(root);
    }

    @Test
    public void regionDepthCalculatesProperly() {
        final String classContent = """
            public class Something {
            
                public void main(String[] args) {
                    System.out.println(1);
                }
            }
            """;

        RegionFinder.Region root = sut.findRoot(classContent);

        assertNotNull(root);
        assertEquals(0, root.regionDepthAtStartOfLine(0));
        assertEquals(0, root.regionDepthAtStartOfLine(1));
        assertEquals(1, root.regionDepthAtStartOfLine(2));
        assertEquals(2, root.regionDepthAtStartOfLine(3));
        assertEquals(1, root.regionDepthAtStartOfLine(4));
        assertEquals(0, root.regionDepthAtStartOfLine(5));
    }

    @Test
    public void regionDepthMediumComplexityClassCalculatesProperly() {
        String classText = """
            class Example {
            	void build() {
            		Consumer<Quad> q = generatedQuad -> {
            			final List<BlockTintSource> tintSources =
            					information.isFluid()
            						? getFluidTintSources()
            						: Minecraft.getInstance().getBlockColors().getTintSources(information.blockState());
            			quadsByTints.computeIfAbsent(tints, (_) -> new ArrayList<>())
            					.add(generatedQuad.quad());
            		};
            
            		final List<BitBlockModelPart> parts =
            				quadsByTints.entrySet().stream()
            					.map(c -> new BitBlockModelPart(c.getValue(), c.getKey()))
            					.toList();
            	}
            }""";

        RegionFinder.Region root = sut.findRoot(classText);
        assertNotNull(root);

        final String[] lines = classText.split("\n");
        final String[] calculatedDepthLines = new String[lines.length];

        for (int i = 0; i < lines.length; i++)
        {
            calculatedDepthLines[i] = "\t".repeat(root.regionDepthAtStartOfLine(i)) + lines[i].trim();
        }

        final String formatResult = String.join("\n", calculatedDepthLines).trim();
        assertEquals(classText, formatResult);
    }

    @Test
    public void regionDepthComplexClass() {
        final String classText = """
            class Example {
            	@Override
            	public Map<BlockInformation, Integer> getContainedStates() {
            		return IntStream.range(0, getInventorySize())
            				.mapToObj(this::getItem)
            				.filter(stack -> stack.getItem() instanceof IBitItem)
            				.map(stack -> {
            					IBitItem bitItem = (IBitItem) stack.getItem();
            					return Maps.newHashMap(ImmutableMap.of(bitItem.getBlockInformation(stack), stack.getCount()));
            				})
            				.reduce(
            						Maps.newHashMap(),
            						(blockStateIntegerHashMap, blockStateIntegerHashMap2) -> {
            							final HashMap<BlockInformation, Integer> result = Maps.newHashMap(blockStateIntegerHashMap);
            							blockStateIntegerHashMap2.forEach((state, count) -> {
            								if (!result.containsKey(state)) {
            									result.put(state, count);
            								} else {
            									result.put(state, result.get(state) + count);
            								}
            							});
            
            							return result;
            						});
            	}
            }""";

        RegionFinder.Region root = sut.findRoot(classText);

        assertNotNull(root);

        final String[] lines = classText.split("\n");
        final int[] depth = new int[lines.length];
        final int[] requiredDepth = new int[lines.length];

        final String[] calculatedDepthLines = new String[lines.length];

        for (int i = 0; i < lines.length; i++)
        {
            depth[i] = root.regionDepthAtStartOfLine(i);
            calculatedDepthLines[i] = "\t".repeat(depth[i]) + lines[i].trim();
            requiredDepth[i] = (int) lines[i].chars().filter(c -> ((char) c) == '\t').count();
        }

        final String formatResult = String.join("\n", calculatedDepthLines).trim();

        assertEquals(classText, formatResult);

        assertEquals(
            Arrays.stream(requiredDepth).mapToObj(String::valueOf).collect(Collectors.joining("\n")),
            Arrays.stream(depth).mapToObj(String::valueOf).collect(Collectors.joining("\n"))
        );
    }

    @Test
    public void regionFinderCanParseSimplePackagedClass() {
        String classText = """
            package test;
            
            class Example {
            	void run() {
            		StringBuilder builder = new StringBuilder();
            		builder
            			.append(" t")
            			.append("s ")
            			.toString();
            	}
            }""";

        RegionFinder.Region root = sut.findRoot(classText);
        assertNotNull(root);
    }
}
