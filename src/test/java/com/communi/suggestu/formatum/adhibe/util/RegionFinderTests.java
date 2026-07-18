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
        sut = new RegionFinder(RegionFinder.ParseMode.DEFAULT);
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
        assertEquals(3, root.end().lineOffset());
        assertEquals(0, root.end().inLineOffset());

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

    @Test
    public void regionFinderParsesInnerRecords() {
        String classText = """
            public interface IBitInventoryItemStack extends IBitInventory, Container {
                	record DisplayContents(List<Component> displayComponents, boolean isEmpty, boolean clipped) { };
            }
        """;

        RegionFinder.Region root = sut.findRoot(classText);
        assertNotNull(root);
    }

    @Test
    public void regionFinderParsesEnumWithDocs() {
        String classText = """
            package mod.chiselsandbits.api.client.render.preview.placement;
            
            /**
             * Determines the way chiseled block and pattern placement previews will render.
             * This value can be independently set for how successful and failed placement renders.
             */
            public enum PlacementPreviewRenderMode
            {
                /**
                 * Causes the preview to render as a ghost of the model of the block to be placed
                 */
                GHOST_BLOCK_MODEL,
        
                /**
                 * Causes the preview to render the model of the block, such that the block's textures are
                 * ignored, and each quad is rendered with the RGBA value specified by the result of placement
                 */
                GHOST_BLOCK_MODEL_SOLID_COLOR,
        
                /**
                 * Causes the preview to render a wireframe comprised of the edges of the block's model
                 * with the RGB value specified by the result of placement
                 */
                WIREFRAME;
        
                public boolean isGhost() {
                    return this == GHOST_BLOCK_MODEL;
                }
        
                public boolean isColoredGhost() {
                    return this == GHOST_BLOCK_MODEL_SOLID_COLOR;
                }
        
                public boolean isWireframe() {
                    return this == WIREFRAME;
                }
            }""";

        RegionFinder.Region root = sut.findRoot(classText);
        assertNotNull(root);
    }

    @Test
    public void regionFinderNotificationManagerParses() {
        String classText = """
            package mod.chiselsandbits.api.notifications;
            
            import mod.chiselsandbits.api.IChiselsAndBitsAPI;
            import net.minecraft.network.chat.Component;
            import net.minecraft.resources.Identifier;
            import net.minecraft.world.phys.Vec3;
            import org.jetbrains.annotations.NotNull;
            
            /**
             * Manager which handles notifying players of events in the game.
             */
            public interface INotificationManager {
            	/**
            	 * The current instance of the notification manager.
            	 *
            	 * @return The notification manager.
            	 */
            	public static INotificationManager getInstance() {
            		return IChiselsAndBitsAPI.getInstance().getNotificationManager();
            	}
            
            	/**
            	 * Notifies the player with a simple message, icon and color.
            	 *
            	 * @param icon The icon to display.
            	 * @param color The color of the icon.
            	 * @param message The message to display.
            	 */
            	default void notify(final Identifier icon, final Vec3 color, final Component message) {
            		notify(new INotification() {
            			@Override
            			public Vec3 getColorVector() {
            				return color;
            			}
            
            			@Override
            			public Identifier getIcon() {
            				return icon;
            			}
            
            			@Override
            			public Component getText() {
            				return message;
            			}
            		});
            	}
            
            	/**
            	 * Notifies the player with a simple message and icon.
            	 *
            	 * @param icon The icon to display.
            	 * @param message The message to display.
            	 */
            	default void notify(final Identifier icon, final Component message) {
            		notify(icon, new Vec3(1, 1, 1), message);
            	}
            
            	/**
            	 * Notifies the player with the given notification.
            	 *
            	 * @param notification The notification to display.
            	 */
            	void notify(final INotification notification);
            }""";

        RegionFinder.Region root = sut.findRoot(classText);
        assertNotNull(root);
    }

    @Test
    public void regionFinderParsesDocumentedInterface() {
        String classText = """
            package mod.chiselsandbits.api.config;
            
            import mod.chiselsandbits.api.IChiselsAndBitsAPI;
            
            /**
             * Represents the configuration of chisels and bits.
             */
            public interface IChiselsAndBitsConfiguration {
            	/**
            	 * Gives access to the current configuration of C{@literal &}B.
            	 * @return The current configuration.
            	 */
            	static IChiselsAndBitsConfiguration getInstance() {
            		return IChiselsAndBitsAPI.getInstance().getConfiguration();
            	}
            
            	/**
            	 * The client configuration.
            	 * Elements in this configuration are only relevant for the client side of C{@literal &}B.
            	 * This configuration does not need to be in-sync with the server values.
            	 *
            	 * @return The client configuration.
            	 */
            	IClientConfiguration getClient();
            
            	/**
            	 * The common configuration.
            	 * Elements in this configuration are relevant for both the server and the client side of C{@literal &}B.
            	 * This configuration does not need to be in-sync with the server values.
            	 *
            	 * @return The common configuration.
            	 */
            	ICommonConfiguration getCommon();
            
            	/**
            	 * Gives access to the current server's configuration.
            	 * Elements in this configuration are relevant for both the server and client side of C{@literal &}B.
            	 * Since this options influence gameplay mechanics they need to be kept in sync.
            	 *
            	 * @return The server configuration.
            	 */
            	IServerConfiguration getServer();
            }""";

        RegionFinder.Region root = sut.findRoot(classText);
        assertNotNull(root);
    }

    @Test
    public void regionFinderParsesSimpleDocumentedInterface() {
        String classText = """
            package mod.chiselsandbits.api.chiseling.eligibility;
            
            import mod.chiselsandbits.api.IChiselsAndBitsAPI;
            
            /**
             * Determines the additional eligibility options for a given platform.
             */
            public interface IEligibilityOptions {
            	/**
            	 * The eligibility manager that is active for the current platform.
            	 * Allows for the modification of the eligibility analysis on the given platform.
            	 * Useful in case the platform defines different default classes for the processing logic.
            	 *
            	 * @return The platform's eligibility manager.
            	 */
            	static IEligibilityOptions getInstance() {
            		return IChiselsAndBitsAPI.getInstance().getEligibilityOptions();
            	}
            
            	/**
            	 * Indicates if the class that defines the explosion resistance is valid for compatibility.
            	 * @param explosionDefinitionClass The class that defines the explosion resistance.
            	 * @return True when the class is valid, false when not.
            	 */
            	boolean isValidExplosionDefinitionClass(final Class<?> explosionDefinitionClass);
            }""";

        RegionFinder.Region root = sut.findRoot(classText);
        assertNotNull(root);
    }

    @Test
    public void regionFinderParsesAndGeneratesOutputForComplicatedEnum() {
        String classText = """
            package mod.chiselsandbits.api.multistate;
            
            import mod.chiselsandbits.api.IChiselsAndBitsAPI;
            import net.minecraft.core.Vec3i;
            import net.minecraft.world.phys.Vec3;
            
            /**
             * The size of state entries in the current instance.
             */
            public enum StateEntrySize
            {
            /**
             * 16 Bits per block.
             */
            ONE_SIXTEENTH(16),
            
            /**
             * 8 Bits per block.
             */
            ONE_EIGHT(8),
            
            /**
             * 4 Bits per block.
             */
            ONE_QUARTER(4),
            
            /**
             * 2 Bits per block.
             */
            ONE_HALF(2),
            
            /**
             * 1 Bit per block side.
             * Generally only used for testing.
             */
            ONE(1);
            
            private static StateEntrySize _current = null;
            
            public static StateEntrySize current() {
            if (_current == null) {
            if (IChiselsAndBitsAPI.getInstance() == null) {
            _current = StateEntrySize.ONE_SIXTEENTH;
            } else {
            _current = IChiselsAndBitsAPI.getInstance().getStateEntrySize();
            }
            }
            
            return _current;
            }
            
            private final int bitsPerBlockSide;
            private final int bitsPerBlock;
            private final int bitsPerLayer;
            private final float sizePerBit;
            private final float sizePerHalfBit;
            private final int damageScaleFactor;
            
            StateEntrySize(final int bitsPerBlockSide) {
            this.bitsPerBlockSide = bitsPerBlockSide;
            this.bitsPerBlock = this.bitsPerBlockSide * this.bitsPerBlockSide * this.bitsPerBlockSide;
            this.bitsPerLayer = this.bitsPerBlockSide * this.bitsPerBlockSide;
            this.sizePerBit = 1 / ((float) bitsPerBlockSide);
            this.sizePerHalfBit = this.sizePerBit / 2f;
            this.damageScaleFactor = (16 / bitsPerBlockSide) ^ 3;
            }
            
            /**
             * The amount of bits in a single layer per side of the block.
             *
             * @return The amount of bits in a layer on a single side of the block.
             */
            public int getBitsPerBlockSide() {
            return bitsPerBlockSide;
            }
            
            /**
             * The total amount of bits per block.
             * This is {@link #getBitsPerBlockSide()} ^ 3.
             *
             * @return The total amount of bits in a block.
             */
            public int getBitsPerBlock() {
            return bitsPerBlock;
            }
            
            /**
             * The total amount of bits per layer.
             * This is {@link #getBitsPerBlockSide()} ^ 2.
             *
             * @return The total amount of bits in a layer.
             */
            public int getBitsPerLayer() {
            return bitsPerLayer;
            }
            
            /**
             * The size of a single bit if a block is a single unit of length.
             * Is always 1 / {@link #getBitsPerBlockSide()}.
             *
             * @return The size of a bit.
             */
            public float getSizePerBit() {
            return sizePerBit;
            }
            
            /**
             * Returns the vector used to scale down another vector with the size of a single bit.
             * Useful for passing to {@link net.minecraft.world.phys.Vec3#multiply(Vec3)}
             *
             * @return The scaling vector.
             */
            public Vec3 getSizePerBitScalingVector() {
            return new Vec3(sizePerBit, sizePerBit, sizePerBit);
            }
            
            /**
             * Returns the vector used to scale down another vector with the size of half a bit.
             * Useful for passing to {@link net.minecraft.world.phys.Vec3#multiply(Vec3)}
             *
             * @return The scaling vector.
             */
            public Vec3 getSizePerHalfBitScalingVector() {
            return new Vec3(sizePerHalfBit, sizePerHalfBit, sizePerHalfBit);
            }
            
            /**
             * Returns the vector used to scale up another vector with the amount of bits on a given side.
             * Useful for passing to {@link Vec3#multiply(Vec3)}
             *
             * @return The scaling vector.
             */
            public Vec3 getBitsPerBlockSideScalingVector() {
            return new Vec3(bitsPerBlockSide, bitsPerBlockSide, bitsPerBlockSide);
            }
            
            /**
             * The size of half a bit if a block is a single unit of length.
             * Is always {@link #getSizePerBit()} / 2.
             *
             * @return The size of half a single bit.
             */
            public float getSizePerHalfBit() {
            return sizePerHalfBit;
            }
            
            /**
             * The y coordinate of the upper of the block.
             *
             * @return The y coordinate.
             */
            public float upperLevelY() {
            return getBitsPerLayer() - 1;
            }
            
            /**
             * The array index for a given position when the current state entry size is used.
             *
             * @param coordinate The coordinate to get the array index for.
             * @return The array index.
             */
            public int getArrayIndexForPosition(final Vec3i coordinate) {
            return getArrayIndexForPosition(coordinate.getX(), coordinate.getY(), coordinate.getZ());
            }
            
            /**
             * The array index for a given position when the current state entry size is used.
             *
             * @param x The x coordinate.
             * @param y The y coordinate.
             * @param z The z coordinate.
             * @return The array index.
             */
            public int getArrayIndexForPosition(final int x, final int y, final int z) {
            return x * getBitsPerLayer() + y * getBitsPerBlockSide() + z;
            }
            
            /**
             * Takes in a 3D vector and rounds its components down to the nearest multiple of the size of a single bit.
             *
             * @param pos The position to round down.
             * @return The rounded down position.
             */
            public Vec3 roundDownToNearest(Vec3 pos) {
            return new Vec3(
            Math.floor(pos.x * getBitsPerBlockSide()) / getBitsPerBlockSide(),
            Math.floor(pos.y * getBitsPerBlockSide()) / getBitsPerBlockSide(),
            Math.floor(pos.z * getBitsPerBlockSide()) / getBitsPerBlockSide()
            );
            }
            
            	/**
            	 * Calculates how much damage should be applied to tools for the harvest of a given bit within this size.
            	 * <p>
            	 * The scale factor is 1 for 1/16, and grows with the cubicly per level, with the amount of 1/16 bits that the size represents.
            	 * So for 1/8, it is 8 (as 2x2x2 1/16th bits fit in one 1/8 bit)
            	 * For 1/4, it is 64 (as 4x4x4 1/16th bits fit in one 1/4 bit)
            	 * etc.
            	 * </p>
            	 *
            	 * @return The damage scale factor per one bit harvested.
            	 */
            	public int getDamageFactor() {
            		return this.damageScaleFactor;
            	}
            }
            """;

        final int lineCount = classText.split("\n").length;

        final RegionFinder.Region root = sut.findRoot(classText);
        assertNotNull(root);

        for (int i = 0; i < lineCount; i++)
        {
            root.regionDepthAtStartOfLine(i);
        }
    }
}
