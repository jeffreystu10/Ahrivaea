package dev.ahrivaea.base;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ahrivaea.creature.Job;
import dev.ahrivaea.party.CaughtCreature;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * A creature working at a base: what it is (to bring it back if its body goes missing), its body in the world, and
 * how its owner set up its jobs: which come first ({@code order}, job names, most important first; jobs not listed
 * keep the creature's own order after them) and which are switched off ({@code off}).
 */
public record Worker(CaughtCreature creature, UUID entity, List<String> order, List<String> off) {
	public static final Codec<Worker> CODEC = RecordCodecBuilder.create(i -> i.group(
			CaughtCreature.CODEC.fieldOf("creature").forGetter(Worker::creature),
			UUIDUtil.CODEC.fieldOf("entity").forGetter(Worker::entity),
			Codec.STRING.listOf().optionalFieldOf("job_order", List.of()).forGetter(Worker::order),
			Codec.STRING.listOf().optionalFieldOf("jobs_off", List.of()).forGetter(Worker::off)
	).apply(i, Worker::new));

	public Worker {
		order = List.copyOf(order);
		off = List.copyOf(off);
	}

	public Worker(CaughtCreature creature, UUID entity) {
		this(creature, entity, List.of(), List.of());
	}

	public Worker withCreature(CaughtCreature c) {
		return new Worker(c, entity, order, off);
	}

	public Worker withEntity(UUID e) {
		return new Worker(creature, e, order, off);
	}

	/** The creature's jobs in the order its owner wants, all of them (switched off or not). */
	public List<Job> ordered(List<Job> jobs) {
		List<Job> out = new ArrayList<>(jobs);
		out.sort(Comparator.comparingInt(j -> {
			int k = order.indexOf(j.getSerializedName());
			return k < 0 ? order.size() + jobs.indexOf(j) : k;
		}));
		return out;
	}

	/** The jobs it actually does, most important first. */
	public List<Job> active(List<Job> jobs) {
		List<Job> out = ordered(jobs);
		out.removeIf(j -> off.contains(j.getSerializedName()));
		return out;
	}

	/** In {@code off}: a level 20+ miner's silk touch is switched off. */
	public static final String SILK_TOUCH = "silk_touch";

	public Worker toggled(Job job) {
		return toggled(job.getSerializedName());
	}

	public Worker toggled(String setting) {
		List<String> o = new ArrayList<>(off);
		if (!o.remove(setting)) o.add(setting);
		return new Worker(creature, entity, order, o);
	}

	/** Moves a job one place earlier (or later) in its list. */
	public Worker moved(List<Job> jobs, Job job, int by) {
		List<Job> now = ordered(jobs);
		int i = now.indexOf(job), j = i + by;
		if (i < 0 || j < 0 || j >= now.size()) return this;
		now.remove(i);
		now.add(j, job);
		return new Worker(creature, entity, now.stream().map(Job::getSerializedName).toList(), off);
	}
}
