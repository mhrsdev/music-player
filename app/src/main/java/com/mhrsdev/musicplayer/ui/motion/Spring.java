package com.mhrsdev.musicplayer.ui.motion;

/**
 * Mowj's own damped harmonic oscillator (mass 1): {@code x'' = -k (x - target) - c x'}, {@code c = 2 zeta sqrt(k)}.
 * Each {@link #step} uses the closed-form solution over the frame delta, so it is exact and stable for any frame
 * time (a dropped frame never makes it explode) and allocates nothing. Pure Java: unit-tested on the JVM.
 */
public class Spring {
    /** Stiffness / damping-ratio presets, tuned for Mowj (see docs/THEMING.md, Motion). */
    public enum Preset {
        /** Quick and firm, a hint of overshoot: presses, nav pill, chips. */
        SNAPPY(900f, 0.82f),
        /** Lively, visible bounce: selection pills, mini-player appear, heart pop. */
        BOUNCY(520f, 0.55f),
        /** Critically damped, never overshoots: sheets, page-level containers. */
        GENTLE(240f, 1f),
        /** The one jiggle: a quick dip, one small rebound, gone (state changes like play/pause). */
        JIGGLE(700f, 0.3f);

        public final float stiffness;
        public final float dampingRatio;

        Preset(float stiffness, float dampingRatio) {
            this.stiffness = stiffness;
            this.dampingRatio = dampingRatio;
        }
    }

    private float stiffness;
    private float dampingRatio;
    private float value;
    private float velocity;
    private float target;
    /** Settled when both |value - target| and |velocity| fall under these. */
    private float valueThreshold = 0.001f;
    private float velocityThreshold = 0.01f;

    public Spring(Preset preset) {
        setPreset(preset);
    }

    public Spring(float stiffness, float dampingRatio) {
        setParams(stiffness, dampingRatio);
    }

    public Spring setPreset(Preset p) {
        return setParams(p.stiffness, p.dampingRatio);
    }

    public Spring setParams(float stiffness, float dampingRatio) {
        if (stiffness <= 0f || dampingRatio < 0f) throw new IllegalArgumentException("stiffness > 0, damping >= 0");
        this.stiffness = stiffness;
        this.dampingRatio = dampingRatio;
        return this;
    }

    /** Thresholds in value units (e.g. 0.001 for a 0..1 fraction, 0.5 for pixels). */
    public Spring setThreshold(float valueThreshold) {
        this.valueThreshold = valueThreshold;
        this.velocityThreshold = valueThreshold * 10f;
        return this;
    }

    public float value() { return value; }
    public float velocity() { return velocity; }
    public float target() { return target; }
    public float stiffness() { return stiffness; }
    public float dampingRatio() { return dampingRatio; }

    /** Jumps to {@code v} at rest. */
    public void snapTo(float v) {
        value = v;
        target = v;
        velocity = 0f;
    }

    /** New target; the current value and velocity are kept (interruption carries momentum). */
    public void setTarget(float target) {
        this.target = target;
    }

    public void setValue(float value) {
        this.value = value;
    }

    public void setVelocity(float velocity) {
        this.velocity = velocity;
    }

    public boolean isSettled() {
        return Math.abs(value - target) < valueThreshold && Math.abs(velocity) < velocityThreshold;
    }

    /** Advances by {@code dtSeconds}; returns true while still moving. Settling snaps exactly onto the target. */
    public boolean step(float dtSeconds) {
        if (dtSeconds > 0f) {
            double x0 = value - target, v0 = velocity, t = dtSeconds;
            double w0 = Math.sqrt(stiffness), z = dampingRatio;
            double x, v;
            if (z < 1.0) {
                double wd = w0 * Math.sqrt(1.0 - z * z);
                double decay = Math.exp(-z * w0 * t);
                double a = x0, b = (v0 + z * w0 * x0) / wd;
                double cos = Math.cos(wd * t), sin = Math.sin(wd * t);
                x = decay * (a * cos + b * sin);
                v = decay * ((b * wd - z * w0 * a) * cos - (a * wd + z * w0 * b) * sin);
            } else if (z == 1.0) {
                double decay = Math.exp(-w0 * t);
                double b = v0 + w0 * x0;
                x = decay * (x0 + b * t);
                v = decay * (b - w0 * (x0 + b * t));
            } else {
                double s = w0 * Math.sqrt(z * z - 1.0);
                double r1 = -z * w0 + s, r2 = -z * w0 - s;
                double c2 = (v0 - r1 * x0) / (r2 - r1), c1 = x0 - c2;
                double e1 = Math.exp(r1 * t), e2 = Math.exp(r2 * t);
                x = c1 * e1 + c2 * e2;
                v = c1 * r1 * e1 + c2 * r2 * e2;
            }
            value = (float) (target + x);
            velocity = (float) v;
        }
        if (isSettled()) {
            value = target;
            velocity = 0f;
            return false;
        }
        return true;
    }

    /** Spring energy (potential + kinetic, unit mass) relative to the target; never rises without new input. */
    public double energy() {
        double x = value - target;
        return 0.5 * stiffness * x * x + 0.5 * velocity * velocity;
    }

    /**
     * Velocity to kick a resting spring with so its first swing reaches {@code amplitude} (underdamped only).
     * A negative amplitude dips first. Used for the jiggle: kick the scale spring, it dips, rebounds once, rests.
     */
    public static float kickFor(float amplitude, float stiffness, float dampingRatio) {
        double z = Math.min(0.999, dampingRatio), w0 = Math.sqrt(stiffness);
        double wd = w0 * Math.sqrt(1 - z * z);
        double tPeak = Math.atan2(Math.sqrt(1 - z * z), z) / wd;
        return (float) (amplitude * w0 / Math.exp(-z * w0 * tPeak));
    }
}
