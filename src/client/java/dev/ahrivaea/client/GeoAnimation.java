package dev.ahrivaea.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * One animation from a Bedrock .animation.json: per bone, rotation (degrees) and position (pixels) over time.
 * Values may be Molang; keyframes are blended linearly.
 */
public final class GeoAnimation {
	private record Channel(TreeMap<Double, Molang.Expr[]> frames) {
		double[] at(double t) {
			var lo = frames.floorEntry(t);
			var hi = frames.ceilingEntry(t);
			if (lo == null) lo = hi;
			if (hi == null) hi = lo;
			double[] a = eval(lo.getValue(), t);
			if (hi.getKey().equals(lo.getKey())) return a;
			double[] b = eval(hi.getValue(), t);
			double f = (t - lo.getKey()) / (hi.getKey() - lo.getKey());
			return new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
		}

		static double[] eval(Molang.Expr[] e, double t) {
			return new double[]{e[0].eval(t), e[1].eval(t), e[2].eval(t)};
		}
	}

	private record BoneTrack(String bone, Channel rotation, Channel position) {}

	private final List<BoneTrack> tracks;
	private final double length;
	private final boolean loop;

	private GeoAnimation(List<BoneTrack> tracks, double length, boolean loop) {
		this.tracks = tracks;
		this.length = length;
		this.loop = loop;
	}

	/** All animations in a file, by short name ("idle", "fly" ...). */
	public static Map<String, GeoAnimation> read(JsonObject file) {
		Map<String, GeoAnimation> out = new HashMap<>();
		JsonObject anims = file.getAsJsonObject("animations");
		if (anims == null) return out;
		for (var entry : anims.entrySet()) {
			String name = entry.getKey().substring(entry.getKey().lastIndexOf('.') + 1);
			JsonObject a = entry.getValue().getAsJsonObject();
			List<BoneTrack> tracks = new ArrayList<>();
			JsonObject bones = a.getAsJsonObject("bones");
			if (bones != null) for (var b : bones.entrySet()) {
				JsonObject o = b.getValue().getAsJsonObject();
				tracks.add(new BoneTrack(b.getKey(), channel(o.get("rotation")), channel(o.get("position"))));
			}
			double length = a.has("animation_length") ? a.get("animation_length").getAsDouble() : 0;
			boolean loop = a.has("loop") && a.get("loop").isJsonPrimitive() && a.get("loop").getAsJsonPrimitive().isBoolean() && a.get("loop").getAsBoolean();
			out.put(name, new GeoAnimation(tracks, length, loop));
		}
		return out;
	}

	private static Channel channel(JsonElement e) {
		if (e == null) return null;
		TreeMap<Double, Molang.Expr[]> frames = new TreeMap<>();
		if (e.isJsonObject()) {
			for (var f : e.getAsJsonObject().entrySet()) {
				JsonElement v = f.getValue();
				if (v.isJsonObject()) v = v.getAsJsonObject().has("post") ? v.getAsJsonObject().get("post") : v.getAsJsonObject().get("pre");
				frames.put(Double.parseDouble(f.getKey()), vec(v));
			}
		} else frames.put(0.0, vec(e));
		return frames.isEmpty() ? null : new Channel(frames);
	}

	private static Molang.Expr[] vec(JsonElement v) {
		if (v != null && v.isJsonArray()) {
			JsonArray a = v.getAsJsonArray();
			return new Molang.Expr[]{expr(a.get(0)), expr(a.get(1)), expr(a.get(2))};
		}
		Molang.Expr one = expr(v);
		return new Molang.Expr[]{one, one, one};
	}

	private static Molang.Expr expr(JsonElement v) {
		if (v == null || v.isJsonNull()) return Molang.ZERO;
		if (v.getAsJsonPrimitive().isNumber()) {
			double d = v.getAsDouble();
			return t -> d;
		}
		return Molang.parse(v.getAsString());
	}

	/**
	 * Moves the bones for time {@code seconds}. Bedrock is y-up and mirrored in x relative to Java models (see
	 * {@link GeoModels#build}), so x and y flip for both rotation and position.
	 */
	public void apply(Function<String, ModelPart> bones, double seconds) {
		double t = seconds;
		if (length > 0) t = loop ? t % length : Math.min(t, length);
		for (BoneTrack track : tracks) {
			ModelPart p = bones.apply(track.bone());
			if (p == null) continue;
			if (track.rotation() != null) {
				double[] r = track.rotation().at(t);
				p.xRot += (float) -r[0] * Mth.DEG_TO_RAD;
				p.yRot += (float) -r[1] * Mth.DEG_TO_RAD;
				p.zRot += (float) r[2] * Mth.DEG_TO_RAD;
			}
			if (track.position() != null) {
				double[] o = track.position().at(t);
				p.x += (float) -o[0];
				p.y += (float) -o[1];
				p.z += (float) o[2];
			}
		}
	}
}
