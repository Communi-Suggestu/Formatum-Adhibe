package com.communi.suggestu.formatum.adhibe.util;

import com.communi.suggestu.formatum.adhibe.utils.Region;
import com.communi.suggestu.formatum.adhibe.utils.RegionFinder;
import com.communi.suggestu.formatum.adhibe.utils.RegionOffset;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class JavacRegionFinderTest {
	private final RegionFinder finder = new RegionFinder(RegionFinder.ParseMode.HARD, RegionFinder.DebugMode.RegionDepthTracking);

	@Test
	void createsContainedOrderedRegionsForNestedConstructs() {
		String source = """
				class C {
					enum E {
						A(1) { void run() { call(); } }, B(2);
						final int value;
						E(int value) { this.value = value; }
					}
					static final Object VALUE = new MissingType("argument") {
						void run() {}
					};
					void run() {
						for (int i = 0; i < 3; i++) {
							call(x -> {
								if (ok) { first(); } else { second(); }
								try { third(); } catch (MissingException e) { fourth(); }
							});
						}
					}
				}
				""";
		Region root = finder.findRoot(source);
		assertStructure(root, source);
		assertEquals(2, flatten(root).filter(Region::isAnonymousClass).count());
		assertEquals(1, flatten(root).filter(Region::isLambda).count());
		Region tryRegion = flatten(root).filter(region -> region.isControl() && region.content().startsWith("try ")).findFirst().orElseThrow();
		Region catchRegion = flatten(tryRegion).filter(region -> region.isControl() && region.content().startsWith("catch ")).findFirst().orElseThrow();
		assertFalse(flatten(catchRegion).anyMatch(region -> region.content().equals("{ third(); }")));
		assertTrue(flatten(root).anyMatch(region -> region.isStatement() && region.content().equals("final int value;")));
		assertFalse(flatten(root).anyMatch(region -> region.isStatement() && region.content().equals("for (int i = 0;")));
	}

	@Test
	void ignoresCommentAndLiteralDelimitersAndEscapedBackslashes() {
		String source = """
				class C {
					/* } ) ] */
					String slash = "\\\\";
					String text = "not a block: { [ (";
					char quote = '\\'';
					String block = \"""
							{ [ ( // not code
							\\\""" still inside text
							\""";
					void run() { /* { */ call(); } // }
				}
				""";
		Region root = finder.findRoot(source);
		assertStructure(root, source);
		assertEquals(2, flatten(root).filter(Region::isCodeBlock).count());
	}

	@Test
	void tracksRawOffsetsForUnicodeDelimitersAndComments() {
		String escape = "\\" + "u";
		String source = "class C " + escape + "007b "
				+ escape + "002f" + escape + "002a } " + escape + "002a" + escape + "002f "
				+ "void run() " + escape + "007b call(); " + escape + "007d "
				+ escape + "007d";
		Region root = finder.findRoot(source);
		assertStructure(root, source);
		assertEquals(2, flatten(root).filter(Region::isCodeBlock).count());
		assertEquals(source.length(), root.children()[0].end().totalCharacterOffset());
	}

	@Test
	void supportsMemberAndStatementFragmentsWithoutSyntheticRegions() {
		for (String source : List.of(
				"public MissingType run(OtherMissingType input) {\n\treturn input;\n}",
				"if (unknown) {\n\tcall();\n} else {\n\tother();\n}")) {
			Region root = finder.findRoot(source);
			assertStructure(root, source);
			assertEquals(source, root.content());
			assertFalse(flatten(root).anyMatch(region -> region.content().contains("__FormatumFragment")));
			assertEquals(0, root.regionDepthAtStartOfLine(0));
			assertEquals(1, root.regionDepthAtStartOfLine(1));
		}
	}

	@Test
	void parsesModernSyntaxWithoutResolvingTypes() {
		String source = """
				record C(MissingType value) {
					Object run(Object input) {
						return switch (input) {
							case String text when !text.isEmpty() -> text;
							default -> MissingType.VALUE;
						};
					}
				}
				""";
		assertStructure(finder.findRoot(source), source);
	}

	@Test
	void followsTheRunningJdkForNewLanguageSyntax() {
		assumeTrue(Runtime.version().feature() >= 25);
		String source = "import module java.base;\nclass C { void run() { IO.println(\"hello\"); } }\n";
		assertStructure(finder.findRoot(source), source);
	}

	@Test
	void includesSwitchArrayInitializerAndStaticInitializerBodies() {
		String source = """
				class C {
					static {
						call();
					}
					int[][] values = {
						{1, 2},
						{3, 4}
					};
					void run(int input) {
						switch (input) {
							default:
							call();
						}
					}
				}
				""";
		Region root = finder.findRoot(source);
		assertStructure(root, source);
		assertEquals(7, flatten(root).filter(Region::isCodeBlock).count());
		assertTrue(flatten(root).filter(Region::isCodeBlock).allMatch(region -> region.content().startsWith("{")));
		assertEquals(2, root.regionDepthAtStartOfLine(2));
		assertEquals(3, root.regionDepthAtStartOfLine(11));
	}

	@Test
	void handlesAnnotationArraysAndMultipleVariableDeclarators() {
		String source = """
				@Example(values = {"}", "("})
				class C {
					int first = 1, second = call(2);
					void run() {
						int a = 1, b = call(2);
						try (var x = open(); var y = open()) { call(); }
					}
				}
				""";
		Region root = finder.findRoot(source);
		assertStructure(root, source);
		assertEquals(1, flatten(root).filter(region -> region.isStatement() && region.content().startsWith("int first")).count());
		assertEquals(1, flatten(root).filter(region -> region.isStatement() && region.content().startsWith("int a")).count());
	}

	@Test
	void parsesModuleBodies() {
		String source = "module missing.module {\n\trequires other.module;\n}";
		Region root = finder.findRoot(source);
		assertStructure(root, source);
		assertEquals(1, root.children().length);
		assertTrue(root.children()[0].isCodeBlock());
		assertEquals(1, root.regionDepthAtStartOfLine(1));
	}

	@Test
	void doesNotTreatEnumConstantArgumentsAsAFluentMethodChain() {
		String source = """
				enum E {
					FIRST(
							1),
					SECOND(2);
					E(int value) {}
				}
				""";
		Region root = finder.findRoot(source);
		assertStructure(root, source);
		assertEquals(1, root.regionDepthAtStartOfLine(1));
		assertEquals(3, root.regionDepthAtStartOfLine(2));
		assertEquals(1, root.regionDepthAtStartOfLine(3));
		assertEquals(1, root.regionDepthAtStartOfLine(4));
	}

	@Test
	void reportsSyntaxErrorsInBothParseModes() {
		for (RegionFinder.ParseMode mode : RegionFinder.ParseMode.values()) {
			var parser = new RegionFinder(mode, RegionFinder.DebugMode.Off);
			var exception = assertThrows(IllegalArgumentException.class, () -> parser.findRoot("class C {\n void run( { }\n}"));
			assertTrue(exception.getMessage().contains("line 2:"), exception.getMessage());
			assertTrue(exception.getMessage().contains("JDK " + Runtime.version().feature()));
			assertThrows(IllegalArgumentException.class, () -> parser.findRoot("class C {"));
		}
	}

	@Test
	void usesExclusiveEndOffsetsIncludingEmptyAndTrailingLines() {
		assertEquals(new RegionOffset(1, 0, 1), new RegionOffset("x", 1));
		assertEquals(new RegionOffset(2, 2, 0), new RegionOffset("\n\n", 2));
		for (String source : List.of("", "\n\n", "class C {}", "class C {}\n\n")) {
			Region root = finder.findRoot(source);
			assertStructure(root, source);
			assertEquals(source.length(), root.end().totalCharacterOffset());
		}
	}

	private static Stream<Region> flatten(Region region) {
		return Stream.concat(Stream.of(region), Arrays.stream(region.children()).flatMap(JavacRegionFinderTest::flatten));
	}

	private static void assertStructure(Region parent, String source) {
		int previousEnd = parent.start().totalCharacterOffset();
		assertEquals(source.substring(parent.start().totalCharacterOffset(), parent.end().totalCharacterOffset()), parent.content());
		for (Region child : parent.children()) {
			assertTrue(child.start().totalCharacterOffset() >= previousEnd, () -> "Overlapping/out-of-order children in " + parent.content());
			assertTrue(child.end().totalCharacterOffset() <= parent.end().totalCharacterOffset(), () -> "Child outside " + parent.content());
			assertTrue(child.start().totalCharacterOffset() < child.end().totalCharacterOffset());
			assertStructure(child, source);
			previousEnd = child.end().totalCharacterOffset();
		}
	}
}
