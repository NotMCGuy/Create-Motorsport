package com.createmotorsport.vehicle;

import com.createmotorsport.physics.spec.TorqueSplitSpec;
import com.createmotorsport.physics.vehicle.CarSpec;
import com.createmotorsport.physics.vehicle.Driveline;

import java.util.ArrayList;
import java.util.List;

/**
 * Temporary way of synthesizing the driven axles until we redo this to work more like Create shaft network
 */
public final class DrivelineSynthesizer {

    private DrivelineSynthesizer() {
    }

    public static Driveline synthesize(CarSpec spec, TorqueSplitSpec centre,
                                       TorqueSplitSpec interAxle) {
        List<Driveline.Node> front = new ArrayList<>();
        List<Driveline.Node> rear = new ArrayList<>();
        for (int i = 0; i < spec.size(); i++) {
            boolean isFront = spec.axle(i).front();
            if (spec.axle(i).driven()) {
                (isFront ? front : rear).add(new Driveline.Leaf(i));
            }
        }
        if (!front.isEmpty() && !rear.isEmpty()) {
            return new Driveline(new Driveline.Split(centre, end(front, interAxle, spec),
                    end(rear, interAxle, spec)));
        }
        return new Driveline(end(front.isEmpty() ? rear : front, interAxle, spec));
    }

    private static Driveline.Node end(List<Driveline.Node> axles, TorqueSplitSpec interAxle, CarSpec spec) {
        if (axles.size() == 2) {
            Driveline.Node a = axles.get(0);
            Driveline.Node b = axles.get(1);
            double alongA = spec.axle(((Driveline.Leaf) a).axle()).along();
            double alongB = spec.axle(((Driveline.Leaf) b).axle()).along();
            if (alongB > alongA) {
                return new Driveline.Split(interAxle, b, a);
            }
            return new Driveline.Split(interAxle, a, b);
        }
        return new Driveline.Share(axles);
    }
}
