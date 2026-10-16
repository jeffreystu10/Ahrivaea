package dev.ahrivaea.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ahrivaea.Ahrivaea;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads creature models made in Blockbench (Bedrock .geo.json) from resource packs:
 * assets/<ns>/geo/creature/<species>/<influence>.geo.json (and its <influence>.animation.json), plus an optional settings.json in the same
 * folder ({"model_scale": 0.15}) to shrink or grow every form of that species.
 * Textures are assets/<ns>/textures/entity/creature/<species>/<influence>.png.
 */
public final class GeoModels {
	private GeoModels() {}

	private static Map<String, GeoCreatureModel> models = Map.of();
	/** Each model's longest horizontal size, in blocks, before scaling. */
	private static Map<String, Float> extents = Map.of();
	private static Map<String, Float> scales = Map.of();
	private static Set<String> textures = Set.of();
	/** Each model's height in blocks, before scaling. */
	private static Map<String, Float> heights = Map.of();
	/** Models drawn at the size they were made (one Blockbench pixel = 1/16 block) whatever the creature's hitbox. */
	private static Set<String> realSize = Set.of();

	public static void reload(ResourceManager manager) {
		Map<String, GeoCreatureModel> loadedModels = new HashMap<>();
		Map<String, Float> loadedExtents = new HashMap<>();
		Map<String, Float> loadedScales = new HashMap<>();
		Map<String, Float> loadedHeights = new HashMap<>();
		Set<String> loadedRealSize = new HashSet<>();
		Map<String, Map<String, GeoAnimation>> loadedAnimations = new HashMap<>();
		manager.listResources("geo/creature", path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
			String rest = path.getPath().substring("geo/creature/".length());
			int slash = rest.indexOf('/');
			if (slash < 0) return;
			String species = rest.substring(0, slash), file = rest.substring(slash + 1);
			try (Reader reader = resource.openAsReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				if (file.equals("settings.json")) {
					if (json.has("model_scale")) loadedScales.put(species, json.get("model_scale").getAsFloat());
				} else if (file.endsWith(".geo.json")) {
					String key = species + "/" + file.substring(0, file.length() - ".geo.json".length());
					loadedModels.put(key, new GeoCreatureModel(build(json)));
					loadedExtents.put(key, extent(json));
					loadedHeights.put(key, height(json));
					JsonObject desc = json.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonObject("description");
					if (desc != null && desc.has("ahrivaea:real_size") && desc.get("ahrivaea:real_size").getAsBoolean()) loadedRealSize.add(key);
				} else if (file.endsWith(".animation.json")) {
					loadedAnimations.put(species + "/" + file.substring(0, file.length() - ".animation.json".length()), GeoAnimation.read(json));
				}
			} catch (Exception e) {
				Ahrivaea.LOGGER.error("Could not load creature model {}: {}", path, e.toString());
			}
		});
		Set<String> loadedTextures = new HashSet<>();
		manager.listResources("textures/entity/creature", path -> path.getPath().endsWith(".png")).keySet().forEach(path -> {
			String p = path.getPath().substring("textures/entity/creature/".length());
			loadedTextures.add(p.substring(0, p.length() - ".png".length()));
		});
		loadedAnimations.forEach((key, anims) -> {
			GeoCreatureModel m = loadedModels.get(key);
			if (m != null) m.setAnimations(anims);
		});
		models = Map.copyOf(loadedModels);
		heights = Map.copyOf(loadedHeights);
		realSize = Set.copyOf(loadedRealSize);
		extents = Map.copyOf(loadedExtents);
		scales = Map.copyOf(loadedScales);
		textures = Set.copyOf(loadedTextures);
		Ahrivaea.LOGGER.info("Loaded {} creature models: {}", models.size(), String.join(", ", new java.util.TreeSet<>(models.keySet())));
	}

	/** The model for a species' form, falling back to its None form; null if the species has no models. */
	public static GeoCreatureModel model(String species, String influence) {
		GeoCreatureModel m = models.get(species + "/" + influence);
		return m != null ? m : models.get(species + "/none");
	}

	/** Longest horizontal size of a form's model in blocks (unscaled), falling back to the None form. */
	public static float extent(String species, String influence) {
		Float e = extents.get(species + "/" + influence);
		return e != null ? e : extents.getOrDefault(species + "/none", 1f);
	}

	private static float extent(JsonObject file) {
		float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
		JsonObject geo = file.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
		for (JsonElement e : geo.getAsJsonArray("bones")) {
			JsonObject b = e.getAsJsonObject();
			if (!b.has("cubes")) continue;
			for (JsonElement ce : b.getAsJsonArray("cubes")) {
				float[] o = vec(ce.getAsJsonObject(), "origin"), sz = vec(ce.getAsJsonObject(), "size");
				minX = Math.min(minX, o[0]);
				maxX = Math.max(maxX, o[0] + sz[0]);
				minZ = Math.min(minZ, o[2]);
				maxZ = Math.max(maxZ, o[2] + sz[2]);
			}
		}
		return minX > maxX ? 1f : Math.max(maxX - minX, maxZ - minZ) / 16f;
	}

	/** Height of a form's model in blocks (unscaled), falling back to the None form. */
	public static float height(String species, String influence) {
		Float h = heights.get(species + "/" + influence);
		return h != null ? h : heights.getOrDefault(species + "/none", 1f);
	}

	/** Whether a form's model is drawn at the size it was made, rather than scaled with the creature. */
	public static boolean realSize(String species, String influence) {
		return models.containsKey(species + "/" + influence) ? realSize.contains(species + "/" + influence) : realSize.contains(species + "/none");
	}

	private static float height(JsonObject file) {
		float maxY = 0;
		JsonObject geo = file.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
		for (JsonElement e : geo.getAsJsonArray("bones")) {
			JsonObject b = e.getAsJsonObject();
			if (!b.has("cubes")) continue;
			for (JsonElement ce : b.getAsJsonArray("cubes")) {
				float[] o = vec(ce.getAsJsonObject(), "origin"), sz = vec(ce.getAsJsonObject(), "size");
				maxY = Math.max(maxY, o[1] + sz[1]);
			}
		}
		return maxY > 0 ? maxY / 16f : 1f;
	}

	/** A form's model_scale: the species' settings.json, except for real-size models, which are drawn as made. */
	public static float scale(String species, String influence) {
		return realSize(species, influence) ? 1f : scale(species);
	}

	public static float scale(String species) {
		return scales.getOrDefault(species, 1f);
	}

	public static Identifier texture(String species, String influence) {
		if (textures.contains(species + "/" + influence)) return Ahrivaea.id("textures/entity/creature/" + species + "/" + influence + ".png");
		if (textures.contains(species + "/none")) return Ahrivaea.id("textures/entity/creature/" + species + "/none.png");
		return Ahrivaea.id("textures/entity/creature/placeholder.png");
	}

	/**
	 * Converts Bedrock geometry to a Java model. Bedrock is y-up with absolute pivots; Java models are y-down
	 * with y = 24 at the feet and parts positioned relative to their parent. The two spaces differ by a half
	 * turn about z, so x and y flip, as do x and y rotations. Cubes may use box UVs ("uv": [u, v]) or per-face
	 * UVs ("uv": {"north": {"uv": [u, v], "uv_size": [w, h]}, ...}).
	 */
	static ModelPart build(JsonObject file) {
		JsonObject geo = file.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
		JsonObject desc = geo.getAsJsonObject("description");
		int texW = desc.has("texture_width") ? desc.get("texture_width").getAsInt() : 64;
		int texH = desc.has("texture_height") ? desc.get("texture_height").getAsInt() : 64;

		Map<String, JsonObject> bones = new LinkedHashMap<>();
		Map<String, List<String>> children = new HashMap<>();
		List<String> roots = new ArrayList<>();
		for (JsonElement e : geo.getAsJsonArray("bones")) {
			JsonObject b = e.getAsJsonObject();
			bones.put(b.get("name").getAsString(), b);
		}
		for (var entry : bones.entrySet()) {
			JsonObject b = entry.getValue();
			String parent = b.has("parent") ? b.get("parent").getAsString() : null;
			if (parent != null && bones.containsKey(parent)) children.computeIfAbsent(parent, k -> new ArrayList<>()).add(entry.getKey());
			else roots.add(entry.getKey());
		}
		Map<String, ModelPart> top = new LinkedHashMap<>();
		for (String r : roots) top.put(r, bone(r, null, bones, children, texW, texH, 0));
		return new ModelPart(List.of(), top);
	}

	private static ModelPart bone(String name, JsonObject parentBone, Map<String, JsonObject> bones, Map<String, List<String>> children, int texW, int texH, int depth) {
		JsonObject b = bones.get(name);
		float[] p = vec(b, "pivot");
		float[] pp = parentBone != null ? vec(parentBone, "pivot") : null;
		// Java position of this pivot, relative to the parent's
		float jx = -p[0], jy = 24 - p[1], jz = p[2];
		float ox = pp == null ? jx : jx + pp[0];
		float oy = pp == null ? jy : jy - (24 - pp[1]);
		float oz = pp == null ? jz : jz - pp[2];
		float[] r = vec(b, "rotation");
		List<ModelPart.Cube> cubes = new ArrayList<>();
		if (b.has("cubes")) for (JsonElement ce : b.getAsJsonArray("cubes")) cubes.add(cube(ce.getAsJsonObject(), jx, jy, jz, texW, texH));
		Map<String, ModelPart> kids = new LinkedHashMap<>();
		if (depth < 64) for (String c : children.getOrDefault(name, List.of())) kids.put(c, bone(c, b, bones, children, texW, texH, depth + 1));
		ModelPart part = new ModelPart(cubes, kids);
		PartPose pose = PartPose.offsetAndRotation(ox, oy, oz, -r[0] * Mth.DEG_TO_RAD, -r[1] * Mth.DEG_TO_RAD, r[2] * Mth.DEG_TO_RAD);
		part.setInitialPose(pose);
		part.loadPose(pose);
		return part;
	}

	/** Which Bedrock face each Java face is (x and y are flipped between the two). */
	private static String bedrockFace(Direction d) {
		return switch (d) {
			case DOWN -> "up";
			case UP -> "down";
			case WEST -> "east";
			case EAST -> "west";
			case NORTH -> "north";
			case SOUTH -> "south";
		};
	}

	private static ModelPart.Cube cube(JsonObject c, float jx, float jy, float jz, int texW, int texH) {
		float[] o = vec(c, "origin"), s = vec(c, "size");
		float inflate = c.has("inflate") ? c.get("inflate").getAsFloat() : 0;
		boolean mirror = c.has("mirror") && c.get("mirror").getAsBoolean();
		float minX = -(o[0] + s[0]) - jx, minY = (24 - o[1] - s[1]) - jy, minZ = o[2] - jz;
		JsonElement uv = c.get("uv");
		if (uv == null || uv.isJsonArray()) {
			int u = uv == null ? 0 : uv.getAsJsonArray().get(0).getAsInt(), v = uv == null ? 0 : uv.getAsJsonArray().get(1).getAsInt();
			return new ModelPart.Cube(u, v, minX, minY, minZ, s[0], s[1], s[2], inflate, inflate, inflate, mirror, texW, texH, EnumSet.allOf(Direction.class));
		}
		JsonObject faces = uv.getAsJsonObject();
		Set<Direction> visible = EnumSet.noneOf(Direction.class);
		for (Direction d : Direction.values()) if (faces.has(bedrockFace(d))) visible.add(d);
		ModelPart.Cube cube = new ModelPart.Cube(0, 0, minX, minY, minZ, s[0], s[1], s[2], inflate, inflate, inflate, false, texW, texH, visible);
		for (int i = 0; i < cube.polygons.length; i++) {
			ModelPart.Polygon poly = cube.polygons[i];
			var n = poly.normal();
			Direction d = Direction.getApproximateNearest(n.x(), n.y(), n.z());
			JsonObject f = faces.getAsJsonObject(bedrockFace(d));
			if (f == null) continue;
			float[] at = vec2(f, "uv"), size = vec2(f, "uv_size");
			float u1 = at[0], v1 = at[1], u2 = at[0] + size[0], v2 = at[1] + size[1];
			// pull the corners in a hair so neighbouring colours on the texture don't bleed in
			float eu = Math.signum(u2 - u1) * 0.02f, ev = Math.signum(v2 - v1) * 0.02f;
			cube.polygons[i] = new ModelPart.Polygon(poly.vertices(), u1 + eu, v1 + ev, u2 - eu, v2 - ev, texW, texH, false, d);
		}
		return cube;
	}

	private static float[] vec2(JsonObject o, String key) {
		if (!o.has(key)) return new float[2];
		JsonArray a = o.getAsJsonArray(key);
		return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat()};
	}

	private static float[] vec(JsonObject o, String key) {
		if (!o.has(key)) return new float[3];
		JsonArray a = o.getAsJsonArray(key);
		return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
	}
}
