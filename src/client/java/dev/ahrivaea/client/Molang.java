package dev.ahrivaea.client;

import java.util.Locale;

/**
 * Just enough Molang for creature animations: numbers, + - * /, brackets, query.life_time / query.anim_time
 * (seconds) and math.sin/cos (degrees), abs, min, max, clamp, sqrt, pi. Anything it can't read counts as 0.
 */
public final class Molang {
	private Molang() {}

	@FunctionalInterface
	public interface Expr {
		/** @param t seconds the animation has been playing */
		double eval(double t);
	}

	public static final Expr ZERO = t -> 0;

	public static Expr parse(String text) {
		if (text == null || text.isBlank()) return ZERO;
		try {
			double constant = Double.parseDouble(text.trim());
			return t -> constant;
		} catch (NumberFormatException ignored) {
		}
		try {
			Parser p = new Parser(text.toLowerCase(Locale.ROOT).replace(" ", ""));
			Expr e = p.sum();
			return p.pos == p.s.length() ? e : ZERO;
		} catch (RuntimeException e) {
			return ZERO;
		}
	}

	private static final class Parser {
		final String s;
		int pos;

		Parser(String s) {
			this.s = s;
		}

		boolean eat(char c) {
			if (pos < s.length() && s.charAt(pos) == c) {
				pos++;
				return true;
			}
			return false;
		}

		Expr sum() {
			Expr e = product();
			while (true) {
				if (eat('+')) {
					Expr a = e, b = product();
					e = t -> a.eval(t) + b.eval(t);
				} else if (eat('-')) {
					Expr a = e, b = product();
					e = t -> a.eval(t) - b.eval(t);
				} else return e;
			}
		}

		Expr product() {
			Expr e = unary();
			while (true) {
				if (eat('*')) {
					Expr a = e, b = unary();
					e = t -> a.eval(t) * b.eval(t);
				} else if (eat('/')) {
					Expr a = e, b = unary();
					e = t -> {
						double d = b.eval(t);
						return d == 0 ? 0 : a.eval(t) / d;
					};
				} else return e;
			}
		}

		Expr unary() {
			if (eat('-')) {
				Expr a = unary();
				return t -> -a.eval(t);
			}
			if (eat('+')) return unary();
			return primary();
		}

		Expr primary() {
			if (eat('(')) {
				Expr e = sum();
				if (!eat(')')) throw new IllegalArgumentException("missing )");
				return e;
			}
			int start = pos;
			char c = s.charAt(pos);
			if (Character.isDigit(c) || c == '.') {
				while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
				double v = Double.parseDouble(s.substring(start, pos));
				return t -> v;
			}
			while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos) == '_' || s.charAt(pos) == '.')) pos++;
			String name = s.substring(start, pos);
			if (name.isEmpty()) throw new IllegalArgumentException("unexpected " + c);
			if (eat('(')) {
				java.util.List<Expr> args = new java.util.ArrayList<>();
				if (!eat(')')) {
					do args.add(sum());
					while (eat(','));
					if (!eat(')')) throw new IllegalArgumentException("missing )");
				}
				return call(name, args);
			}
			return switch (name) {
				case "query.life_time", "q.life_time", "query.anim_time", "q.anim_time" -> t -> t;
				case "math.pi" -> t -> Math.PI;
				default -> ZERO;
			};
		}

		static Expr call(String name, java.util.List<Expr> a) {
			Expr x = a.isEmpty() ? ZERO : a.get(0), y = a.size() > 1 ? a.get(1) : ZERO, z = a.size() > 2 ? a.get(2) : ZERO;
			return switch (name) {
				case "math.sin" -> t -> Math.sin(Math.toRadians(x.eval(t)));
				case "math.cos" -> t -> Math.cos(Math.toRadians(x.eval(t)));
				case "math.abs" -> t -> Math.abs(x.eval(t));
				case "math.sqrt" -> t -> Math.sqrt(Math.max(0, x.eval(t)));
				case "math.min" -> t -> Math.min(x.eval(t), y.eval(t));
				case "math.max" -> t -> Math.max(x.eval(t), y.eval(t));
				case "math.clamp" -> t -> Math.clamp(x.eval(t), y.eval(t), z.eval(t));
				case "math.mod" -> t -> {
					double d = y.eval(t);
					return d == 0 ? 0 : x.eval(t) % d;
				};
				default -> ZERO;
			};
		}
	}
}
