package com.createmotorsport.motorcycle;

import com.google.gson.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import java.util.*;

public final class MotorcycleDefinition {
    public enum Frame { CHASSIS, STEERING, FORK_SLIDER, SWINGARM }
    public record Socket(Frame frame, Vec3 position, Vec3 rotation) { }
    public static final double REST_HUB = .5 - com.createmotorsport.block.entity.SuspensionBlockEntity.REST_LENGTH;
    private final Map<String, Socket> sockets;
    public final double forkWidth, rake, torsoLean, hipAngle, headCompensation;
    public final String drive;
    public final boolean singleSided;
    public final String json;
    public static final MotorcycleDefinition DEFAULT = parse("{}");

    private MotorcycleDefinition(JsonObject object) {
        forkWidth = number(object, "fork_half_width", .48, .35, .8);
        rake = Math.toRadians(number(object, "rake_degrees", 15, -20, 25));
        torsoLean = Math.toRadians(number(object, "torso_lean_degrees", 20, 0, 45));
        hipAngle = Math.toRadians(number(object, "hip_rotation_degrees", 5, -15, 25));
        headCompensation = number(object, "head_compensation", .7, 0, 1);
        drive = object.has("final_drive") ? object.get("final_drive").getAsString() : "chain";
        if (!Set.of("chain", "belt", "shaft").contains(drive)) throw new IllegalArgumentException("final_drive must be chain, belt or shaft");
        singleSided = object.has("single_sided") && object.get("single_sided").getAsBoolean();
        Map<String, Socket> map = new LinkedHashMap<>();
        add(map,"steering_axis",Frame.CHASSIS,0,1.12,(1.12-REST_HUB)*Math.tan(rake));
        add(map,"fork_left",Frame.STEERING,-forkWidth,.7,(.7-REST_HUB)*Math.tan(rake)); add(map,"fork_right",Frame.STEERING,forkWidth,.7,(.7-REST_HUB)*Math.tan(rake));
        add(map,"triple_upper",Frame.STEERING,0,1.12,(1.12-REST_HUB)*Math.tan(rake)); add(map,"triple_lower",Frame.STEERING,0,.85,(.85-REST_HUB)*Math.tan(rake));
        add(map,"handlebar_mount",Frame.STEERING,0,1.4,.05+(1.4-REST_HUB)*Math.tan(rake));
        add(map,"grip_left",Frame.STEERING,-.43,1.7,.38+(1.7-REST_HUB)*Math.tan(rake)); add(map,"grip_right",Frame.STEERING,.43,1.7,.38+(1.7-REST_HUB)*Math.tan(rake));
        add(map,"front_brake",Frame.STEERING,.43,1.67,.48+(1.7-REST_HUB)*Math.tan(rake)); add(map,"clutch",Frame.STEERING,-.43,1.67,.48+(1.7-REST_HUB)*Math.tan(rake));
        add(map,"throttle",Frame.STEERING,.43,1.7,.38+(1.7-REST_HUB)*Math.tan(rake));
        add(map,"front_axle",Frame.FORK_SLIDER,0,REST_HUB,0);
        add(map,"swingarm_pivot",Frame.CHASSIS,0,.72,-.65);
        add(map,"rear_axle",Frame.SWINGARM,0,REST_HUB,0);
        add(map,"shock_upper",Frame.CHASSIS,0,1.0,-.48);
        add(map,"shock_lower",Frame.SWINGARM,0,.48,-.2);
        add(map,"seat_root",Frame.CHASSIS,0,.1875,-.14);
        add(map,"passenger_seat",Frame.CHASSIS,0,.55,.35);
        add(map,"foot_left",Frame.CHASSIS,-.43,-.13,-.05); add(map,"foot_right",Frame.CHASSIS,.43,-.13,-.05);
        add(map,"gear_lever",Frame.CHASSIS,-.44,-.1,-.23); add(map,"rear_brake",Frame.CHASSIS,.44,-.1,-.23);
        add(map,"engine_mount",Frame.CHASSIS,0,.55,-.7); add(map,"gearbox_mount",Frame.CHASSIS,0,.5,0);
        add(map,"drive_input",Frame.CHASSIS,0,.5,-.5); add(map,"drive_output",Frame.CHASSIS,-.4,.45,.35);
        add(map,"rear_sprocket",Frame.SWINGARM,-.42,REST_HUB,0);
        add(map,"final_drive_front",Frame.CHASSIS,-.42,.45,-.78);
        add(map,"exhaust",Frame.CHASSIS,.48,.25,.3);
        add(map,"tank_shell",Frame.CHASSIS,0,.8,-.45); add(map,"front_fairing",Frame.STEERING,0,1.1,-.35);
        add(map,"side_left",Frame.CHASSIS,-.4,.4,0); add(map,"side_right",Frame.CHASSIS,.4,.4,0);
        add(map,"belly_pan",Frame.CHASSIS,0,.1,0); add(map,"front_mudguard",Frame.FORK_SLIDER,0,REST_HUB+.82,0);
        add(map,"rear_mudguard",Frame.SWINGARM,0,REST_HUB+.82,0); add(map,"headlight",Frame.STEERING,0,1,-.3);
        add(map,"tail",Frame.CHASSIS,0,.5,.45); add(map,"windscreen",Frame.STEERING,0,1.5,-.3);
        add(map,"engine_cover",Frame.CHASSIS,.4,.5,-.3);
        for (String name : List.of("grip_left", "grip_right", "throttle", "front_brake", "clutch")) {
            var old = map.get(name);
            double sweep = name.equals("grip_left") ? 12 : name.equals("clutch") ? -168 : -12;
            map.put(name, new Socket(old.frame, old.position, new Vec3(0, sweep, 0)));
        }
        if (object.has("sockets")) for (var entry : object.getAsJsonObject("sockets").entrySet()) {
            if (!map.containsKey(entry.getKey())) throw new IllegalArgumentException("Unknown socket: " + entry.getKey());
            var value = entry.getValue().getAsJsonObject(); var old = map.get(entry.getKey());
            Frame frame = value.has("frame") ? Frame.valueOf(value.get("frame").getAsString().toUpperCase(Locale.ROOT)) : old.frame;
            map.put(entry.getKey(), new Socket(frame, vector(value,"position",old.position,3), vector(value,"rotation",old.rotation,180)));
        }
        for (String key : List.of("steering_axis","swingarm_pivot")) if (map.get(key).frame != Frame.CHASSIS) throw new IllegalArgumentException(key + " must be CHASSIS");
        sockets = Map.copyOf(map);
        json = object.toString();
    }
    public Socket socket(String name) { return Objects.requireNonNull(sockets.get(name), "Unknown socket " + name); }
    public Set<String> names() { return sockets.keySet(); }
    public static MotorcycleDefinition parse(String json) {
        if (json.length() > 16000) throw new IllegalArgumentException("Definition exceeds 16 KB");
        return new MotorcycleDefinition(JsonParser.parseString(json).getAsJsonObject());
    }
    public static MotorcycleDefinition safe(String json) { try { return parse(json); } catch (RuntimeException ignored) { return DEFAULT; } }
    private static void add(Map<String,Socket> m,String n,Frame f,double x,double y,double z) { m.put(n,new Socket(f,new Vec3(x,y,z),Vec3.ZERO)); }
    private static double number(JsonObject o,String k,double d,double min,double max) {
        double v=o.has(k)?o.get(k).getAsDouble():d;
        if (!Double.isFinite(v)||v<min||v>max) throw new IllegalArgumentException(k+" outside "+min+".."+max);
        return v;
    }
    private static Vec3 vector(JsonObject o,String k,Vec3 fallback,double bound) {
        if (!o.has(k)) return fallback;
        var a=o.getAsJsonArray(k); if(a.size()!=3)throw new IllegalArgumentException(k+" needs three numbers");
        double[] v=new double[3];for(int i=0;i<3;i++){v[i]=a.get(i).getAsDouble();if(!Double.isFinite(v[i])||Math.abs(v[i])>bound)throw new IllegalArgumentException(k+" outside bounds");}
        return new Vec3(v[0],v[1],v[2]);
    }

    public static final class Pose {
        public final Matrix4f[] frames = {new Matrix4f(),new Matrix4f(),new Matrix4f(),new Matrix4f()};
        public void update(MotorcycleDefinition d,float steer,float spring) {
            frames[0].identity(); Vec3 axis=d.socket("steering_axis").position;
            frames[1].identity().translate((float)axis.x,(float)axis.y,(float)axis.z).rotateX((float)d.rake).rotateY(steer).rotateX((float)-d.rake).translate((float)-axis.x,(float)-axis.y,(float)-axis.z);
            double delta=com.createmotorsport.block.entity.SuspensionBlockEntity.REST_LENGTH-spring;
            frames[2].set(frames[1]).translate(0,(float)(delta*Math.cos(d.rake)),(float)(delta*Math.sin(d.rake)));
            Vec3 pivot=d.socket("swingarm_pivot").position, axle=d.socket("rear_axle").position;
            double length=Math.hypot(axle.y-pivot.y,axle.z-pivot.z);
            double rest=Math.atan2(pivot.y-axle.y,axle.z-pivot.z);
            double angle=Math.asin(Math.clamp((pivot.y-axle.y-delta)/Math.max(.1,length),-.95,.95));
            frames[3].identity().translate((float)pivot.x,(float)pivot.y,(float)pivot.z).rotateX((float)(angle-rest)).translate((float)-pivot.x,(float)-pivot.y,(float)-pivot.z);
        }
        public org.joml.Vector3f point(MotorcycleDefinition d,String name,org.joml.Vector3f out) {
            var s=d.socket(name); return frames[s.frame.ordinal()].transformPosition(out.set((float)s.position.x,(float)s.position.y,(float)s.position.z));
        }
        public Matrix4f socket(MotorcycleDefinition d,String name,Matrix4f out) {
            var s=d.socket(name);return out.set(frames[s.frame.ordinal()]).translate((float)s.position.x,(float)s.position.y,(float)s.position.z)
                    .rotateZYX((float)Math.toRadians(s.rotation.z),(float)Math.toRadians(s.rotation.y),(float)Math.toRadians(s.rotation.x));
        }
    }
}
