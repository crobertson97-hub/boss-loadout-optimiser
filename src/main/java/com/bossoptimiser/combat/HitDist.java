package com.bossoptimiser.combat;

/**
 * Probability distribution of a single hitsplat. Damage values are tracked separately for hits
 * that passed the accuracy roll and hits that failed it, because several mechanics (enchanted
 * bolts, the "accurate zero becomes one" rule) treat the two differently.
 */
public final class HitDist
{
	/** Receives the outcome(s) of transforming one (damage, accurate) cell. */
	public interface Sink
	{
		void add(int damage, boolean accurate, double probability);
	}

	/** Maps one (damage, accurate) outcome with probability {@code p} to one or more outcomes. */
	public interface Transform
	{
		void apply(int damage, boolean accurate, double p, Sink out);
	}

	private double[] accurate;
	private double[] inaccurate;

	public HitDist()
	{
		this(8);
	}

	private HitDist(int capacity)
	{
		accurate = new double[capacity];
		inaccurate = new double[capacity];
	}

	/** Uniform damage in [min, max] with probability {@code accuracy}, otherwise a miss. */
	public static HitDist linear(double accuracy, int min, int max)
	{
		HitDist d = new HitDist(Math.max(max, 0) + 2);
		if (max < min)
		{
			max = min;
		}
		double each = accuracy / (max - min + 1);
		for (int i = min; i <= max; i++)
		{
			d.add(i, true, each);
		}
		d.add(0, false, 1 - accuracy);
		return d;
	}

	public static HitDist miss()
	{
		HitDist d = new HitDist(2);
		d.add(0, false, 1.0);
		return d;
	}

	public void add(int damage, boolean isAccurate, double p)
	{
		if (p == 0)
		{
			return;
		}
		if (damage < 0)
		{
			damage = 0;
		}
		if (damage >= accurate.length)
		{
			int n = Math.max(damage + 1, accurate.length * 2);
			accurate = java.util.Arrays.copyOf(accurate, n);
			inaccurate = java.util.Arrays.copyOf(inaccurate, n);
		}
		if (isAccurate)
		{
			accurate[damage] += p;
		}
		else
		{
			inaccurate[damage] += p;
		}
	}

	public HitDist transform(Transform t)
	{
		return transform(t, true);
	}

	/**
	 * @param transformInaccurate when false, missed hits pass through untouched.
	 */
	public HitDist transform(Transform t, boolean transformInaccurate)
	{
		HitDist out = new HitDist(accurate.length);
		Sink sink = out::add;
		for (int d = 0; d < accurate.length; d++)
		{
			if (accurate[d] != 0)
			{
				t.apply(d, true, accurate[d], sink);
			}
			if (inaccurate[d] != 0)
			{
				if (transformInaccurate)
				{
					t.apply(d, false, inaccurate[d], sink);
				}
				else
				{
					out.add(d, false, inaccurate[d]);
				}
			}
		}
		return out;
	}

	public HitDist scaleProbability(double factor)
	{
		HitDist out = new HitDist(accurate.length);
		for (int d = 0; d < accurate.length; d++)
		{
			out.accurate[d] = accurate[d] * factor;
			out.inaccurate[d] = inaccurate[d] * factor;
		}
		return out;
	}

	/** Adds every outcome of {@code other} into this distribution (used to mix weighted cases). */
	public void addAll(HitDist other)
	{
		for (int d = 0; d < other.accurate.length; d++)
		{
			add(d, true, other.accurate[d]);
			add(d, false, other.inaccurate[d]);
		}
	}

	public double expected()
	{
		double e = 0;
		for (int d = 1; d < accurate.length; d++)
		{
			e += d * (accurate[d] + inaccurate[d]);
		}
		return e;
	}

	public int max()
	{
		for (int d = accurate.length - 1; d > 0; d--)
		{
			if (accurate[d] + inaccurate[d] > 0)
			{
				return d;
			}
		}
		return 0;
	}

	public double totalProbability()
	{
		double t = 0;
		for (int d = 0; d < accurate.length; d++)
		{
			t += accurate[d] + inaccurate[d];
		}
		return t;
	}

	/** Probability that the hitsplat deals at least one damage. */
	public double chanceToDamage()
	{
		double t = 0;
		for (int d = 1; d < accurate.length; d++)
		{
			t += accurate[d] + inaccurate[d];
		}
		return t;
	}
}
