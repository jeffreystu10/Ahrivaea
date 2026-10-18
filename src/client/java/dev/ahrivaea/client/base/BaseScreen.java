package dev.ahrivaea.client.base;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ahrivaea.base.Bases;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A base's screen: its workers (what each is doing, with a button to send it away) on the left, and your party and
 * box on the right, each with a button to put that creature to work here.
 */
public class BaseScreen extends Screen {
	/** Everything sits this much lower, clear of the F3 debug text. */
	static final int TOP = 14, Y0 = 58 + TOP;
	private JsonObject state;
	private int tick;
	private int boxScroll, workerScroll;
	/** The worker whose jobs are being set up (-1: the main view). */
	private int jobsFor = -1;

	/** How many worker rows fit above the Done button. */
	private int workerRows() {
		return Math.max(1, (height - Y0 - 34) / 26);
	}

	public BaseScreen(JsonObject state) {
		super(Component.literal("Base"));
		this.state = state;
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Bases.StatePayload.TYPE, (payload, ctx) -> {
			JsonObject o = JsonParser.parseString(payload.json()).getAsJsonObject();
			Minecraft mc = ctx.client();
			if (mc.gui.screen() instanceof BaseScreen s) s.update(o);
			else mc.gui.setScreen(new BaseScreen(o));
		});
	}

	public JsonObject state() {
		return state;
	}

	void update(JsonObject o) {
		state = o;
		rebuildWidgets();
	}

	public void send(String kind, int index) {
		send(kind, index, null);
	}

	public void send(String kind, int index, String job) {
		JsonObject a = new JsonObject();
		a.addProperty("kind", kind);
		a.addProperty("index", index);
		if (job != null) a.addProperty("job", job);
		a.add("pos", state.getAsJsonArray("pos"));
		ClientPlayNetworking.send(new Bases.ActionPayload(a.toString()));
	}

	/** Opens (or with -1 closes) the job settings of a worker. */
	public void showJobs(int worker) {
		jobsFor = worker;
		rebuildWidgets();
	}

	private JsonArray jobList() {
		JsonArray workers = state.getAsJsonArray("workers");
		if (jobsFor < 0 || jobsFor >= workers.size()) return null;
		JsonObject w = workers.get(jobsFor).getAsJsonObject();
		return w.has("jobList") ? w.getAsJsonArray("jobList") : new JsonArray();
	}

	@Override
	protected void init() {
		JsonArray jobs = jobList();
		if (jobs != null) {
			int x = width / 2 - 220;
			for (int i = 0; i < jobs.size(); i++) {
				JsonObject job = jobs.get(i).getAsJsonObject();
				String id = job.get("id").getAsString();
				int y = Y0 + i * 40;
				boolean on = job.get("on").getAsBoolean();
				addRenderableWidget(Button.builder(Component.literal(on ? "On" : "Off"), b -> send("job_toggle", jobsFor, id)).bounds(x, y, 40, 20).build());
				Button up = addRenderableWidget(Button.builder(Component.literal("▲"), b -> send("job_up", jobsFor, id)).bounds(x + 44, y, 20, 20).build());
				up.active = i > 0;
				Button down = addRenderableWidget(Button.builder(Component.literal("▼"), b -> send("job_down", jobsFor, id)).bounds(x + 66, y, 20, 20).build());
				down.active = i < jobs.size() - 1;
			}
			addRenderableWidget(Button.builder(Component.literal("Back"), b -> showJobs(-1)).bounds(width / 2 - 40, height - 26, 80, 20).build());
			return;
		}
		jobsFor = -1;
		int left = width / 2 - 250, right = width / 2 + 10;
		JsonArray workers = state.getAsJsonArray("workers");
		workerScroll = Math.clamp(workerScroll, 0, Math.max(0, workers.size() - workerRows()));
		for (int i = workerScroll; i < Math.min(workers.size(), workerScroll + workerRows()); i++) {
			final int idx = i;
			addRenderableWidget(Button.builder(Component.literal("Jobs"), b -> showJobs(idx))
					.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Switch its jobs on or off, and choose which comes first")))
					.bounds(left + 128, Y0 + (i - workerScroll) * 26, 40, 18).build());
			addRenderableWidget(Button.builder(Component.literal("Send away"), b -> send("unassign", idx)).bounds(left + 170, Y0 + (i - workerScroll) * 26, 70, 18).build());
		}
		boolean full = workers.size() >= state.get("cap").getAsInt();
		int y = Y0;
		JsonArray party = state.getAsJsonArray("party");
		for (int i = 0; i < party.size(); i++) {
			final int idx = i;
			Button b = addRenderableWidget(Button.builder(Component.literal("Work here"), x -> send("assign_party", idx)).bounds(right + 170, y + i * 26, 70, 18).build());
			b.active = !full;
		}
		JsonArray box = state.getAsJsonArray("box");
		int boxTop = y + Math.max(1, party.size()) * 26 + 22;
		int rows = Math.max(1, (height - boxTop - 34) / 26);
		boxScroll = Math.clamp(boxScroll, 0, Math.max(0, box.size() - rows));
		for (int i = boxScroll; i < Math.min(box.size(), boxScroll + rows); i++) {
			final int idx = i;
			Button b = addRenderableWidget(Button.builder(Component.literal("Work here"), x -> send("assign_box", idx)).bounds(right + 170, boxTop + (i - boxScroll) * 26, 70, 18).build());
			b.active = !full;
		}
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(width / 2 - 40, height - 26, 80, 20).build());
		boolean loading = !state.has("loading") || state.get("loading").getAsBoolean();
		addRenderableWidget(Button.builder(Component.literal("Keep loaded: " + (loading ? "On" : "Off")), b -> send("toggle_loading", -1))
				.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("On: the whole base stays loaded and working while you're online, wherever you are. "
						+ "Turn it off for bases you don't need running.")))
				.bounds(width / 2 - 200, height - 26, 150, 20).build());
	}

	@Override
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		if (x < width / 2.0) workerScroll -= (int) Math.signum(dy);
		else boxScroll -= (int) Math.signum(dy);
		rebuildWidgets();
		return true;
	}

	@Override
	public void tick() {
		// keep the workers' status up to date
		if (++tick % 40 == 0) send("refresh", -1);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		JsonArray jobs = jobList();
		if (jobs != null) {
			JsonObject w = state.getAsJsonArray("workers").get(jobsFor).getAsJsonObject();
			g.centeredText(font, "Jobs: " + w.get("name").getAsString() + " Lv." + w.get("level").getAsInt(), width / 2, 10 + TOP, 0xFFFFD040);
			g.centeredText(font, "It works on the first job in the list that has something to do. Jobs that are off are skipped.", width / 2, 22 + TOP, 0xFFAAAAAA);
			g.text(font, "Now: " + w.get("status").getAsString(), width / 2 - 220, 42 + TOP, 0xFF9FE09F);
			if (w.has("speed")) {
				String speed = "Works at " + w.get("speed").getAsInt() + "% speed (100% at level 20, 2% more each level)";
				g.text(font, speed, width / 2 + 220 - font.width(speed), 42 + TOP, 0xFFFFD040);
			}
			int x = width / 2 - 220 + 94;
			for (int i = 0; i < jobs.size(); i++) {
				JsonObject job = jobs.get(i).getAsJsonObject();
				boolean on = job.get("on").getAsBoolean();
				int y = Y0 + i * 40;
				g.text(font, (i + 1) + ". " + job.get("name").getAsString() + (job.get("works").getAsBoolean() ? "" : " (not a base job yet)"), x, y + 1, on ? 0xFFFFFFFF : 0xFF777777);
				g.text(font, job.get("desc").getAsString(), x, y + 12, on ? 0xFF9FC8FF : 0xFF666666);
			}
			if (jobs.isEmpty()) g.text(font, "This creature has no jobs.", width / 2 - 220, Y0, 0xFFAAAAAA);
			return;
		}
		int left = width / 2 - 250, right = width / 2 + 10;
		JsonArray workers = state.getAsJsonArray("workers");
		int side = state.has("chunks") ? state.get("chunks").getAsInt() : 3;
		g.centeredText(font, "Base (level " + state.get("level").getAsInt() + ": " + side + " x " + side + " chunks)", width / 2, 10 + TOP, 0xFFFFD040);
		g.centeredText(font, state.get("upgrade").getAsString(), width / 2, 22 + TOP, 0xFFAAAAAA);
		g.text(font, "Workers " + workers.size() + "/" + state.get("cap").getAsInt() + (workers.size() > workerRows() ? " (scroll for more)" : ""), left, 42 + TOP, 0xFFFFFFFF);
		for (int i = workerScroll; i < Math.min(workers.size(), workerScroll + workerRows()); i++)
			entry(g, workers.get(i).getAsJsonObject(), left, Y0 + (i - workerScroll) * 26, true);
		if (workers.isEmpty()) g.text(font, "Nobody works here yet: pick creatures on the right.", left, Y0, 0xFFAAAAAA);
		JsonArray party = state.getAsJsonArray("party");
		g.text(font, "Party", right, 42 + TOP, 0xFFFFFFFF);
		for (int i = 0; i < party.size(); i++) entry(g, party.get(i).getAsJsonObject(), right, Y0 + i * 26, false);
		if (party.isEmpty()) g.text(font, "(empty)", right, Y0, 0xFFAAAAAA);
		JsonArray box = state.getAsJsonArray("box");
		int boxTop = Y0 + Math.max(1, party.size()) * 26 + 22;
		g.text(font, "Box (" + box.size() + (box.size() > 0 ? ", scroll for more" : "") + ")", right, boxTop - 16, 0xFFFFFFFF);
		int rows = Math.max(1, (height - boxTop - 34) / 26);
		for (int i = boxScroll; i < Math.min(box.size(), boxScroll + rows); i++) entry(g, box.get(i).getAsJsonObject(), right, boxTop + (i - boxScroll) * 26, false);
	}

	private void entry(GuiGraphicsExtractor g, JsonObject c, int x, int y, boolean worker) {
		g.fill(x - 2, y - 2, x + 244, y + 22, 0x80000000);
		String title = c.get("name").getAsString() + " Lv." + c.get("level").getAsInt();
		if (worker && font.width(title) > 124) title = font.plainSubstrByWidth(title, 119) + "...";
		g.text(font, title, x, y, worker || c.get("works").getAsBoolean() ? 0xFFFFFFFF : 0xFFAAAAAA);
		String sub = worker ? c.get("status").getAsString() : c.get("jobs").getAsString();
		int room = worker ? 122 : 165;
		if (font.width(sub) > room) sub = font.plainSubstrByWidth(sub, room - 5) + "...";
		g.text(font, sub, x, y + 11, worker ? 0xFF9FE09F : 0xFF9FC8FF);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
