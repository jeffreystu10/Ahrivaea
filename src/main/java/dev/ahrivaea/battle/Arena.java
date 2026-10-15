package dev.ahrivaea.battle;

import dev.ahrivaea.Ahrivaea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Battle arenas: a field built fresh for each battle in the private battle dimension, themed after the biome
 * where the encounter happened. The 21×21 middle is the battlefield (hills, water, lava and obstacles that
 * matter on the grid); around it a border of the biome's scenery (trees, cliffs, dunes ...) frames it.
 */
public final class Arena {
	private Arena() {}

	public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, Ahrivaea.id("battle_arena"));
	public static final int FIELD = 10, BORDER = 6, GROUND = 64, SPACING = 256;

	/** How each kind of biome looks as an arena. */
	public enum Theme {
		PLAINS(Blocks.GRASS_BLOCK, Blocks.DIRT, 2, Liquid.WATER, Obstacle.BOULDER, Decor.MEADOW, Border.WOODS),
		MEADOW(Blocks.GRASS_BLOCK, Blocks.DIRT, 2, Liquid.NONE, Obstacle.BOULDER, Decor.FLOWERS, Border.HILLS),
		FOREST(Blocks.GRASS_BLOCK, Blocks.DIRT, 1, Liquid.WATER, Obstacle.OAK, Decor.MEADOW, Border.WOODS),
		BIRCH_FOREST(Blocks.GRASS_BLOCK, Blocks.DIRT, 1, Liquid.NONE, Obstacle.BIRCH, Decor.FLOWERS, Border.BIRCH_WOODS),
		DARK_FOREST(Blocks.PODZOL, Blocks.DIRT, 1, Liquid.NONE, Obstacle.DARK_OAK, Decor.MUSHROOMS, Border.DARK_WOODS),
		CHERRY(Blocks.GRASS_BLOCK, Blocks.DIRT, 2, Liquid.WATER, Obstacle.CHERRY, Decor.PETALS, Border.CHERRY_WOODS),
		TAIGA(Blocks.PODZOL, Blocks.DIRT, 2, Liquid.WATER, Obstacle.SPRUCE, Decor.FERNS, Border.SPRUCE_WOODS),
		SNOWY(Blocks.SNOW_BLOCK, Blocks.PACKED_ICE, 2, Liquid.ICE, Obstacle.ICE_SPIKE, Decor.SNOW, Border.SNOW_CLIFFS),
		DESERT(Blocks.SAND, Blocks.SANDSTONE, 2, Liquid.OASIS, Obstacle.CACTUS, Decor.DESERT, Border.DUNES),
		BADLANDS(Blocks.RED_SAND, Blocks.TERRACOTTA, 3, Liquid.NONE, Obstacle.MESA, Decor.DESERT, Border.MESA_CLIFFS),
		SAVANNA(Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, 2, Liquid.NONE, Obstacle.ACACIA, Decor.DRY_GRASS, Border.ACACIA_WOODS),
		JUNGLE(Blocks.GRASS_BLOCK, Blocks.DIRT, 2, Liquid.WATER, Obstacle.JUNGLE, Decor.JUNGLE, Border.JUNGLE_WOODS),
		SWAMP(Blocks.MUD, Blocks.MUD, 1, Liquid.SWAMP, Obstacle.MANGROVE, Decor.SWAMP, Border.SWAMP_WOODS),
		MOUNTAIN(Blocks.STONE, Blocks.STONE, 4, Liquid.NONE, Obstacle.BOULDER, Decor.GRAVEL, Border.STONE_CLIFFS),
		OCEAN(Blocks.SAND, Blocks.SANDSTONE, 1, Liquid.SHALLOWS, Obstacle.CORAL, Decor.BEACH, Border.SEA),
		MUSHROOM(Blocks.MYCELIUM, Blocks.DIRT, 2, Liquid.NONE, Obstacle.MUSHROOM, Decor.MUSHROOMS, Border.MUSHROOM_GROVE),
		CAVE(Blocks.STONE, Blocks.DEEPSLATE, 3, Liquid.LAVA, Obstacle.DRIPSTONE, Decor.GRAVEL, Border.CAVE_WALLS),
		LUSH_CAVE(Blocks.MOSS_BLOCK, Blocks.STONE, 2, Liquid.WATER, Obstacle.AZALEA, Decor.LUSH, Border.CAVE_WALLS),
		DEEP_DARK(Blocks.DEEPSLATE, Blocks.DEEPSLATE, 2, Liquid.NONE, Obstacle.DEEPSLATE_PILLAR, Decor.NONE, Border.DEEPSLATE_WALLS),
		NETHER(Blocks.NETHERRACK, Blocks.NETHERRACK, 3, Liquid.LAVA, Obstacle.NETHER_PILLAR, Decor.NETHER, Border.NETHER_WALLS),
		SOUL_VALLEY(Blocks.SOUL_SAND, Blocks.SOUL_SOIL, 2, Liquid.LAVA, Obstacle.BONE, Decor.SOUL, Border.BASALT_WALLS),
		CRIMSON(Blocks.CRIMSON_NYLIUM, Blocks.NETHERRACK, 2, Liquid.LAVA, Obstacle.CRIMSON, Decor.CRIMSON, Border.NETHER_WALLS),
		WARPED(Blocks.WARPED_NYLIUM, Blocks.NETHERRACK, 2, Liquid.LAVA, Obstacle.WARPED, Decor.WARPED, Border.NETHER_WALLS),
		BASALT(Blocks.BASALT, Blocks.BLACKSTONE, 3, Liquid.LAVA, Obstacle.BASALT_PILLAR, Decor.NONE, Border.BASALT_WALLS),
		END(Blocks.END_STONE, Blocks.END_STONE, 2, Liquid.NONE, Obstacle.OBSIDIAN, Decor.NONE, Border.END_PILLARS);

		final Block ground, under;
		final int hills;
		final Liquid liquid;
		final Obstacle obstacle;
		final Decor decor;
		final Border border;

		Theme(Block ground, Block under, int hills, Liquid liquid, Obstacle obstacle, Decor decor, Border border) {
			this.ground = ground;
			this.under = under;
			this.hills = hills;
			this.liquid = liquid;
			this.obstacle = obstacle;
			this.decor = decor;
			this.border = border;
		}

		/** A biome that looks like this theme (for previewing arenas with /battle arena). */
		public String sampleBiome() {
			return switch (this) {
				case PLAINS -> "plains";
				case MEADOW -> "meadow";
				case FOREST -> "forest";
				case BIRCH_FOREST -> "birch_forest";
				case DARK_FOREST -> "dark_forest";
				case CHERRY -> "cherry_grove";
				case TAIGA -> "taiga";
				case SNOWY -> "snowy_plains";
				case DESERT -> "desert";
				case BADLANDS -> "badlands";
				case SAVANNA -> "savanna";
				case JUNGLE -> "jungle";
				case SWAMP -> "mangrove_swamp";
				case MOUNTAIN -> "stony_peaks";
				case OCEAN -> "beach";
				case MUSHROOM -> "mushroom_fields";
				case CAVE -> "dripstone_caves";
				case LUSH_CAVE -> "lush_caves";
				case DEEP_DARK -> "deep_dark";
				case NETHER -> "nether_wastes";
				case SOUL_VALLEY -> "soul_sand_valley";
				case CRIMSON -> "crimson_forest";
				case WARPED -> "warped_forest";
				case BASALT -> "basalt_deltas";
				case END -> "the_end";
			};
		}

		/** The theme for where an encounter happened (its biome, and whether it was underground). */
		public static Theme of(ServerLevel level, BlockPos pos) {
			Holder<Biome> b = level.getBiome(pos);
			String id = b.unwrapKey().map(k -> k.identifier().getPath()).orElse("plains");
			if (b.is(BiomeTags.IS_NETHER)) {
				return switch (id) {
					case "soul_sand_valley" -> SOUL_VALLEY;
					case "crimson_forest" -> CRIMSON;
					case "warped_forest" -> WARPED;
					case "basalt_deltas" -> BASALT;
					default -> NETHER;
				};
			}
			if (b.is(BiomeTags.IS_END)) return END;
			switch (id) {
				case "deep_dark":
					return DEEP_DARK;
				case "lush_caves":
					return LUSH_CAVE;
				case "dripstone_caves":
					return CAVE;
				default:
			}
			if (pos.getY() < level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) - 6
					&& pos.getY() < level.getSeaLevel() - 8) return CAVE;
			if (id.contains("mushroom")) return MUSHROOM;
			if (id.contains("cherry")) return CHERRY;
			if (id.contains("dark_forest") || id.contains("pale_garden")) return DARK_FOREST;
			if (id.contains("birch")) return BIRCH_FOREST;
			if (id.contains("swamp")) return SWAMP;
			if (b.is(BiomeTags.IS_JUNGLE)) return JUNGLE;
			if (b.is(BiomeTags.IS_BADLANDS)) return BADLANDS;
			if (id.contains("desert")) return DESERT;
			if (b.is(BiomeTags.IS_SAVANNA)) return SAVANNA;
			if (id.contains("snow") || id.contains("frozen") || id.contains("ice") || id.contains("grove") || id.contains("jagged")) return SNOWY;
			if (b.is(BiomeTags.IS_TAIGA)) return TAIGA;
			if (b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_BEACH) || b.is(BiomeTags.IS_RIVER)) return OCEAN;
			if (id.contains("peaks") || id.contains("windswept") || id.contains("stony") || id.contains("slopes")) return MOUNTAIN;
			if (id.contains("meadow") || id.contains("flower") || id.contains("sunflower")) return MEADOW;
			if (b.is(BiomeTags.IS_FOREST)) return FOREST;
			return PLAINS;
		}
	}

	enum Liquid { NONE, WATER, OASIS, SWAMP, SHALLOWS, ICE, LAVA }

	enum Obstacle { BOULDER, OAK, BIRCH, DARK_OAK, CHERRY, SPRUCE, ICE_SPIKE, CACTUS, MESA, ACACIA, JUNGLE, MANGROVE, CORAL, MUSHROOM, DRIPSTONE, AZALEA, DEEPSLATE_PILLAR, NETHER_PILLAR, BONE, CRIMSON, WARPED, BASALT_PILLAR, OBSIDIAN }

	enum Decor { NONE, MEADOW, FLOWERS, MUSHROOMS, PETALS, FERNS, SNOW, DESERT, DRY_GRASS, JUNGLE, SWAMP, GRAVEL, BEACH, LUSH, NETHER, SOUL, CRIMSON, WARPED }

	enum Border { WOODS, BIRCH_WOODS, DARK_WOODS, CHERRY_WOODS, SPRUCE_WOODS, JUNGLE_WOODS, ACACIA_WOODS, SWAMP_WOODS, HILLS, SNOW_CLIFFS, DUNES, MESA_CLIFFS, STONE_CLIFFS, SEA, MUSHROOM_GROVE, CAVE_WALLS, DEEPSLATE_WALLS, NETHER_WALLS, BASALT_WALLS, END_PILLARS }

	/** Where arena number {@code slot} is built in the battle dimension. */
	public static BlockPos center(int slot) {
		return new BlockPos(slot * SPACING, GROUND, 0);
	}

	/** Builds an arena (replacing whatever was there) and returns its centre square, at standing height. */
	public static BlockPos build(ServerLevel level, int slot, Theme theme, Holder<Biome> biome, long seed) {
		BlockPos c = center(slot);
		RandomSource rnd = RandomSource.create(seed);
		int r = FIELD + BORDER;
		// clear the space and anything left from an earlier battle
		for (Entity e : level.getEntities((Entity) null, new AABB(c).inflate(r + 4, 40, r + 4), e -> !(e instanceof net.minecraft.world.entity.player.Player))) e.discard();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int x = -r - 2; x <= r + 2; x++) for (int z = -r - 2; z <= r + 2; z++) for (int y = -14; y <= 30; y++) set(level, c.offset(x, y, z), air);

		// the ground: a few gentle hills, terraced so each step is one block (climbable)
		int[][] h = new int[2 * r + 1][2 * r + 1];
		int hills = theme.hills == 0 ? 0 : 1 + rnd.nextInt(theme.hills + 1);
		for (int i = 0; i < hills; i++) {
			int hx = rnd.nextInt(2 * FIELD + 1) - FIELD, hz = rnd.nextInt(2 * FIELD + 1) - FIELD;
			int height = 1 + rnd.nextInt(Math.max(1, theme.hills));
			double rad = 2.5 + rnd.nextDouble() * 3;
			for (int x = -r; x <= r; x++) {
				for (int z = -r; z <= r; z++) {
					double d = Math.sqrt((x - hx) * (x - hx) + (z - hz) * (z - hz));
					int lift = (int) Math.floor(height * Math.max(0, 1 - d / (rad + height)) + 0.4);
					h[x + r][z + r] = Math.max(h[x + r][z + r], lift);
				}
			}
		}
		// keep the two starting edges level, so both sides have room to line up
		for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) if (Math.abs(z) >= FIELD - 2) h[x + r][z + r] = 0;
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				int top = h[x + r][z + r];
				for (int y = -12; y < top - 1; y++) set(level, c.offset(x, y, z), theme.under.defaultBlockState());
				set(level, c.offset(x, top - 1, z), theme.ground.defaultBlockState());
			}
		}

		// water, ice or lava
		int pools = switch (theme.liquid) {
			case NONE -> 0;
			case SHALLOWS, SWAMP -> 4 + rnd.nextInt(3);
			default -> 1 + rnd.nextInt(2);
		};
		for (int i = 0; i < pools; i++) {
			int px = rnd.nextInt(2 * FIELD - 3) - FIELD + 2, pz = rnd.nextInt(2 * FIELD - 9) - FIELD + 5;
			int size = theme.liquid == Liquid.SHALLOWS ? 3 + rnd.nextInt(3) : 1 + rnd.nextInt(2);
			for (int x = -size; x <= size; x++) {
				for (int z = -size; z <= size; z++) {
					if (x * x + z * z > size * size + 1) continue;
					int ax = px + x, az = pz + z;
					if (Math.abs(ax) > FIELD || Math.abs(az) > FIELD - 3) continue;
					int top = h[ax + r][az + r];
					BlockPos surface = c.offset(ax, top - 1, az);
					BlockState liquid = switch (theme.liquid) {
						case LAVA -> Blocks.LAVA.defaultBlockState();
						case ICE -> Blocks.ICE.defaultBlockState();
						default -> Blocks.WATER.defaultBlockState();
					};
					set(level, surface, liquid);
					// deep in the middle of bigger pools, shallow at the edges
					if (theme.liquid != Liquid.ICE && size >= 2 && x * x + z * z <= 1) set(level, surface.below(), liquid);
					if (theme.liquid == Liquid.OASIS && rnd.nextInt(4) == 0 && x * x + z * z == size * size) set(level, surface.above(), Blocks.SUGAR_CANE.defaultBlockState());
				}
			}
		}

		// obstacles in the field (not on the starting rows)
		int count = 6 + rnd.nextInt(6);
		for (int i = 0; i < count; i++) {
			int ox = rnd.nextInt(2 * FIELD + 1) - FIELD, oz = rnd.nextInt(2 * FIELD - 5) - FIELD + 3;
			BlockPos base = c.offset(ox, h[ox + r][oz + r], oz);
			if (!level.getBlockState(base.below()).isSolid() || !level.getBlockState(base).isAir()) continue;
			obstacle(level, base, theme.obstacle, rnd);
		}

		// passable decoration
		for (int x = -FIELD; x <= FIELD; x++) {
			for (int z = -FIELD; z <= FIELD; z++) {
				BlockPos p = c.offset(x, h[x + r][z + r], z);
				if (!level.getBlockState(p).isAir() || !level.getBlockState(p.below()).isSolid()) continue;
				if (rnd.nextInt(5) != 0) continue;
				BlockState d = decor(theme.decor, rnd);
				if (d != null && d.canSurvive(level, p)) set(level, p, d);
			}
		}

		// the frame around the field
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				int ring = Math.max(Math.abs(x), Math.abs(z));
				if (ring <= FIELD) continue;
				border(level, c.offset(x, h[x + r][z + r], z), theme.border, ring - FIELD, rnd);
			}
		}

		// give the arena the encounter biome's colours (grass, leaves, water, sky)
		if (biome != null) {
			try {
				net.minecraft.server.commands.FillBiomeCommand.fill(level, c.offset(-r - 2, -14, -r - 2), c.offset(r + 2, 30, r + 2), biome);
			} catch (RuntimeException e) {
				Ahrivaea.LOGGER.warn("Couldn't colour the arena", e);
			}
		}
		return c;
	}

	private static void set(ServerLevel level, BlockPos p, BlockState s) {
		level.setBlock(p, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	private static void column(ServerLevel level, BlockPos base, BlockState s, int height) {
		for (int i = 0; i < height; i++) set(level, base.above(i), s);
	}

	private static void obstacle(ServerLevel level, BlockPos p, Obstacle o, RandomSource rnd) {
		int tall = 1 + rnd.nextInt(2);
		switch (o) {
			case BOULDER -> {
				set(level, p, (rnd.nextBoolean() ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).defaultBlockState());
				if (rnd.nextBoolean()) set(level, p.above(), Blocks.COBBLESTONE.defaultBlockState());
			}
			case OAK -> column(level, p, Blocks.OAK_LOG.defaultBlockState(), tall);
			case BIRCH -> column(level, p, Blocks.BIRCH_LOG.defaultBlockState(), tall);
			case DARK_OAK -> column(level, p, Blocks.DARK_OAK_LOG.defaultBlockState(), tall);
			case CHERRY -> column(level, p, Blocks.CHERRY_LOG.defaultBlockState(), tall);
			case SPRUCE -> column(level, p, Blocks.SPRUCE_LOG.defaultBlockState(), tall);
			case ACACIA -> column(level, p, Blocks.ACACIA_LOG.defaultBlockState(), tall);
			case JUNGLE -> {
				column(level, p, Blocks.JUNGLE_LOG.defaultBlockState(), tall);
				set(level, p.above(tall), Blocks.JUNGLE_LEAVES.defaultBlockState());
			}
			case MANGROVE -> column(level, p, Blocks.MANGROVE_ROOTS.defaultBlockState(), tall);
			case ICE_SPIKE -> column(level, p, Blocks.PACKED_ICE.defaultBlockState(), 1 + rnd.nextInt(3));
			case CACTUS -> {
				if (level.getBlockState(p.below()).is(Blocks.SAND)) column(level, p, Blocks.CACTUS.defaultBlockState(), 1 + rnd.nextInt(2));
			}
			case MESA -> column(level, p, (rnd.nextBoolean() ? Blocks.DYED_TERRACOTTA.pick(net.minecraft.world.item.DyeColor.ORANGE) : Blocks.TERRACOTTA).defaultBlockState(), 2 + rnd.nextInt(2));
			case CORAL -> set(level, p, Blocks.SANDSTONE.defaultBlockState());
			case MUSHROOM -> {
				column(level, p, Blocks.MUSHROOM_STEM.defaultBlockState(), 2);
				set(level, p.above(2), (rnd.nextBoolean() ? Blocks.RED_MUSHROOM_BLOCK : Blocks.BROWN_MUSHROOM_BLOCK).defaultBlockState());
			}
			case DRIPSTONE -> column(level, p, Blocks.DRIPSTONE_BLOCK.defaultBlockState(), 1 + rnd.nextInt(3));
			case AZALEA -> set(level, p, (rnd.nextBoolean() ? Blocks.AZALEA_LEAVES : Blocks.FLOWERING_AZALEA_LEAVES).defaultBlockState());
			case DEEPSLATE_PILLAR -> column(level, p, Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 2 + rnd.nextInt(2));
			case NETHER_PILLAR -> column(level, p, (rnd.nextBoolean() ? Blocks.NETHER_BRICKS : Blocks.NETHERRACK).defaultBlockState(), 2 + rnd.nextInt(2));
			case BONE -> column(level, p, Blocks.BONE_BLOCK.defaultBlockState(), 1 + rnd.nextInt(2));
			case CRIMSON -> {
				column(level, p, Blocks.CRIMSON_STEM.defaultBlockState(), tall);
				set(level, p.above(tall), Blocks.NETHER_WART_BLOCK.defaultBlockState());
			}
			case WARPED -> {
				column(level, p, Blocks.WARPED_STEM.defaultBlockState(), tall);
				set(level, p.above(tall), Blocks.WARPED_WART_BLOCK.defaultBlockState());
			}
			case BASALT_PILLAR -> column(level, p, Blocks.BASALT.defaultBlockState(), 2 + rnd.nextInt(3));
			case OBSIDIAN -> column(level, p, Blocks.OBSIDIAN.defaultBlockState(), 2 + rnd.nextInt(2));
		}
	}

	private static BlockState decor(Decor d, RandomSource rnd) {
		List<Block> pick = switch (d) {
			case NONE -> List.of();
			case MEADOW -> List.of(Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.DANDELION, Blocks.POPPY);
			case FLOWERS -> List.of(Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.SHORT_GRASS);
			case MUSHROOMS -> List.of(Blocks.RED_MUSHROOM, Blocks.BROWN_MUSHROOM, Blocks.SHORT_GRASS);
			case PETALS -> List.of(Blocks.PINK_PETALS, Blocks.PINK_PETALS, Blocks.SHORT_GRASS);
			case FERNS -> List.of(Blocks.FERN, Blocks.SHORT_GRASS, Blocks.SWEET_BERRY_BUSH);
			case SNOW -> List.of(Blocks.SNOW);
			case DESERT -> List.of(Blocks.DEAD_BUSH);
			case DRY_GRASS -> List.of(Blocks.SHORT_GRASS, Blocks.DEAD_BUSH);
			case JUNGLE -> List.of(Blocks.FERN, Blocks.SHORT_GRASS, Blocks.MELON);
			case SWAMP -> List.of(Blocks.BLUE_ORCHID, Blocks.SHORT_GRASS);
			case GRAVEL -> List.of();
			case BEACH -> List.of();
			case LUSH -> List.of(Blocks.MOSS_CARPET, Blocks.AZALEA);
			case NETHER -> List.of(Blocks.CRIMSON_ROOTS);
			case SOUL -> List.of();
			case CRIMSON -> List.of(Blocks.CRIMSON_ROOTS, Blocks.CRIMSON_FUNGUS);
			case WARPED -> List.of(Blocks.WARPED_ROOTS, Blocks.WARPED_FUNGUS, Blocks.NETHER_SPROUTS);
		};
		if (pick.isEmpty()) return null;
		Block b = pick.get(rnd.nextInt(pick.size()));
		return b == Blocks.MELON ? null : b.defaultBlockState();
	}

	private static void border(ServerLevel level, BlockPos p, Border b, int depth, RandomSource rnd) {
		switch (b) {
			case WOODS, BIRCH_WOODS, DARK_WOODS, CHERRY_WOODS, SPRUCE_WOODS, JUNGLE_WOODS, ACACIA_WOODS, SWAMP_WOODS -> {
				Block log = switch (b) {
					case BIRCH_WOODS -> Blocks.BIRCH_LOG;
					case DARK_WOODS -> Blocks.DARK_OAK_LOG;
					case CHERRY_WOODS -> Blocks.CHERRY_LOG;
					case SPRUCE_WOODS -> Blocks.SPRUCE_LOG;
					case JUNGLE_WOODS -> Blocks.JUNGLE_LOG;
					case ACACIA_WOODS -> Blocks.ACACIA_LOG;
					case SWAMP_WOODS -> Blocks.MANGROVE_LOG;
					default -> Blocks.OAK_LOG;
				};
				Block leaves = switch (b) {
					case BIRCH_WOODS -> Blocks.BIRCH_LEAVES;
					case DARK_WOODS -> Blocks.DARK_OAK_LEAVES;
					case CHERRY_WOODS -> Blocks.CHERRY_LEAVES;
					case SPRUCE_WOODS -> Blocks.SPRUCE_LEAVES;
					case JUNGLE_WOODS -> Blocks.JUNGLE_LEAVES;
					case ACACIA_WOODS -> Blocks.ACACIA_LEAVES;
					case SWAMP_WOODS -> Blocks.MANGROVE_LEAVES;
					default -> Blocks.OAK_LEAVES;
				};
				if (depth >= 2 && rnd.nextInt(3) == 0) {
					int t = 3 + rnd.nextInt(3);
					column(level, p, log.defaultBlockState(), t);
					for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = t - 1; dy <= t; dy++) {
						BlockPos q = p.offset(dx, dy, dz);
						if (level.getBlockState(q).isAir()) set(level, q, leaves.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
					}
				} else if (rnd.nextInt(2) == 0) {
					set(level, p, leaves.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
				}
			}
			case HILLS, SNOW_CLIFFS, STONE_CLIFFS, MESA_CLIFFS, DUNES, CAVE_WALLS, DEEPSLATE_WALLS, NETHER_WALLS, BASALT_WALLS -> {
				Block wall = switch (b) {
					case HILLS -> Blocks.GRASS_BLOCK;
					case SNOW_CLIFFS -> Blocks.SNOW_BLOCK;
					case STONE_CLIFFS -> Blocks.STONE;
					case MESA_CLIFFS -> depth % 2 == 0 ? Blocks.DYED_TERRACOTTA.pick(net.minecraft.world.item.DyeColor.ORANGE) : Blocks.TERRACOTTA;
					case DUNES -> Blocks.SAND;
					case CAVE_WALLS -> Blocks.STONE;
					case DEEPSLATE_WALLS -> Blocks.DEEPSLATE;
					case NETHER_WALLS -> Blocks.NETHERRACK;
					default -> Blocks.BASALT;
				};
				int height = (b == Border.DUNES || b == Border.HILLS ? depth : depth * 2) + rnd.nextInt(2);
				column(level, p, (b == Border.HILLS ? Blocks.DIRT : wall).defaultBlockState(), height);
				if (b == Border.HILLS) set(level, p.above(height - 1), Blocks.GRASS_BLOCK.defaultBlockState());
				if ((b == Border.CAVE_WALLS || b == Border.DEEPSLATE_WALLS) && rnd.nextInt(6) == 0) set(level, p.above(height), Blocks.GLOW_LICHEN.defaultBlockState());
			}
			case SEA -> {
				set(level, p.below(), Blocks.WATER.defaultBlockState());
				set(level, p.below(2), Blocks.WATER.defaultBlockState());
			}
			case MUSHROOM_GROVE -> {
				if (depth >= 2 && rnd.nextInt(4) == 0) {
					column(level, p, Blocks.MUSHROOM_STEM.defaultBlockState(), 4);
					for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(level, p.offset(dx, 4, dz), Blocks.RED_MUSHROOM_BLOCK.defaultBlockState());
				}
			}
			case END_PILLARS -> {
				if (depth >= 3 && rnd.nextInt(8) == 0) column(level, p, Blocks.OBSIDIAN.defaultBlockState(), 6 + rnd.nextInt(5));
			}
		}
	}
}
