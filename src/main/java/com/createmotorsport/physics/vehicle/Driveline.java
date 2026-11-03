package com.createmotorsport.physics.vehicle;

import com.createmotorsport.physics.spec.TorqueSplitSpec;

import net.minecraft.util.Mth;

import java.util.List;
import java.util.stream.IntStream;

/**
 * One engine's torque to the axles, as a tree of splits with an axle at each leaf
 * Based on Chrono's ChShaftsDriveline4WD
 * DrivelineSynthesizer builds one per engine, AssembledCar calls distribute() each substep
 */
public final class Driveline {

    public sealed interface Node permits Leaf, Share, Split {
    }

    // axle index in the CarSpec
    public record Leaf(int axle) implements Node {
    }

    public record Share(List<Node> children) implements Node {
    }

    // a centre diff or a tandem's divider
    public record Split(TorqueSplitSpec spec, Node first, Node second) implements Node {
    }

    private final Node root;
    private final int[] axles;

    public Driveline(Node root) {
        this.root = root;
        this.axles = leaves(root).sorted().toArray();
    }

    public int[] axles() {
        return this.axles;
    }

    public double[] distribute(double torque, CarSpec spec, double[] omega) {
        double[] perWheel = new double[spec.size()];
        distribute(this.root, torque, spec, omega, perWheel);
        return perWheel;
    }

    private static void distribute(Node node, double torque, CarSpec spec, double[] omega, double[] out) {
        switch (node) {
            case Leaf leaf -> out[leaf.axle()] = torque / spec.axle(leaf.axle()).tires();
            case Share share -> {
                int tires = tires(share, spec);
                if (tires == 0) {
                    return;
                }
                double perWheel = torque / tires;
                for (Node child : share.children()) {
                    distribute(child, perWheel * tires(child, spec), spec, omega, out);
                }
            }
            case Split split -> {
                TorqueSplitSpec s = split.spec();
                double cap = s.lock() * Math.abs(torque);
                double transfer = Mth.clamp((meanOmega(split.first(), spec, omega)
                        - meanOmega(split.second(), spec, omega)) * s.coupling(), -cap, cap);
                distribute(split.first(), torque * s.frontBias() - transfer, spec, omega, out);
                distribute(split.second(), torque * (1.0 - s.frontBias()) + transfer, spec, omega, out);
            }
        }
    }

    private static double meanOmega(Node node, CarSpec spec, double[] omega) {
        double sum = 0.0;
        int tires = 0;
        for (int axle : leaves(node).sorted().toArray()) {
            int t = spec.axle(axle).tires();
            sum += omega[axle] * t;
            tires += t;
        }
        return tires == 0 ? 0.0 : sum / tires;
    }

    private static int tires(Node node, CarSpec spec) {
        return leaves(node).map(axle -> spec.axle(axle).tires()).sum();
    }

    private static IntStream leaves(Node node) {
        return switch (node) {
            case Leaf leaf -> IntStream.of(leaf.axle());
            case Share share -> share.children().stream().flatMapToInt(Driveline::leaves);
            case Split split -> IntStream.concat(leaves(split.first()), leaves(split.second()));
        };
    }
}
