package dev.ahrivaea.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ahrivaea.Ahrivaea;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads creature models made in Blockbench (Bedrock .geo.json) from resource packs:
 * assets/<ns>/geo/creature/<species>/<influence>.geo.json, plus an optional settings.json in the same
 * folder ({"model_scale": 0.15}) to shrink or grow every form of that species.
 * Textures are assets/<ns>/textures/entity/creature/<species>/<influence>.png.
 */
public final class GeoModels {
	private GeoModels() {}

	private static Map<String, GeoCreatureModel> models = Map.of();
	private static Map<String, Float> scales = Map.of();
	private static Set<String> textures = Set.of();

	public static void reload(ResourceManager manager) {
		Map<String, GeoCreatureModel> loadedModels = new HashMap<>();
		Map<String, Float> loadedScales = new HashMap<>();
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
					String influence = file.substring(0, file.length() - ".geo.json".length());
					loadedModels.put(species + "/" + influence, new GeoCreatureModel(build(json).bakeRoot()));
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
		models = Map.copyOf(loadedModels);
		scales = Map.copyOf(loadedScales);
		textures = Set.copyOf(loadedTextures);
		Ahrivaea.LOGGER.info("Loaded {} creature models: {}", models.size(), String.join(", ", new java.util.TreeSet<>(models.keySet())));
	}

	/** The model for a species' form, falling back to its None form; null if the species has no models. */
	public static GeoCreatureModel model(String species, String influence) {
		GeoCreatureModel m = models.get(species + "/" + influence);
		return m != null ? m : models.get(species + "/none");
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
	 * turn about z, so x and y flip, as do x and y rotations.
	 */
	static LayerDefinition build(JsonObject file) {
		JsonObject geo = file.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
		JsonObject desc = geo.getAsJsonObject("description");
		int texW = desc.has("texture_width") ? desc.get("texture_width").getAsInt() : 64;
		int texH = desc.has("texture_height") ? desc.get("texture_height").getAsInt() : 64;

		MeshDefinition mesh = new MeshDefinition();
		Map<String, JsonObject> bones = new HashMap<>();
		List<String> order = new ArrayList<>();
		for (JsonElement e : geo.getAsJsonArray("bones")) {
			JsonObject b = e.getAsJsonObject();
			bones.put(b.get("name").getAsString(), b);
			order.add(b.get("name").getAsString());
		}
		Map<String, PartDefinition> parts = new HashMap<>();
		// parents first
		int guard = 0;
		while (parts.size() < order.size() && guard++ < order.size() + 1) {
			for (String name : order) {
				if (parts.containsKey(name)) continue;
				JsonObject b = bones.get(name);
				String parent = b.has("parent") ? b.get("parent").getAsString() : null;
				if (parent != null && bones.containsKey(parent) && !parts.containsKey(parent)) continue;
				PartDefinition parentPart = parent != null && parts.containsKey(parent) ? parts.get(parent) : mesh.getRoot();
				float[] p = vec(b, "pivot");
				float[] pp = parent != null && bones.containsKey(parent) ? vec(bones.get(parent), "pivot") : null;
				// Java position of this pivot, relative to the parent's
				float jx = -p[0], jy = 24 - p[1], jz = p[2];
				float ox = pp == null ? jx : jx - -pp[0];
				float oy = pp == null ? jy : jy - (24 - pp[1]);
				float oz = pp == null ? jz : jz - pp[2];
				float[] r = vec(b, "rotation");
				CubeListBuilder cubes = CubeListBuilder.create();
				if (b.has("cubes")) {
					for (JsonElement ce : b.getAsJsonArray("cubes")) {
						JsonObject c = ce.getAsJsonObject();
						float[] o = vec(c, "origin"), s = vec(c, "size");
						int u = 0, v = 0;
						if (c.has("uv") && c.get("uv").isJsonArray()) {
							JsonArray uv = c.getAsJsonArray("uv");
							u = uv.get(0).getAsInt();
							v = uv.get(1).getAsInt();
						}
						float minX = -(o[0] + s[0]) - jx, minY = (24 - o[1] - s[1]) - jy, minZ = o[2] - jz;
						cubes.texOffs(u, v).addBox(minX, minY, minZ, s[0], s[1], s[2]);
					}
				}
				PartPose pose = PartPose.offsetAndRotation(ox, oy, oz, -r[0] * Mth.DEG_TO_RAD, -r[1] * Mth.DEG_TO_RAD, r[2] * Mth.DEG_TO_RAD);
				parts.put(name, parentPart.addOrReplaceChild(name, cubes, pose));
			}
		}
		return LayerDefinition.create(mesh, texW, texH);
	}

	private static float[] vec(JsonObject o, String key) {
		if (!o.has(key)) return new float[3];
		JsonArray a = o.getAsJsonArray(key);
		return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
	}
}
