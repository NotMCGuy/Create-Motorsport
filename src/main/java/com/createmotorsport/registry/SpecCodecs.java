package com.createmotorsport.registry;

import com.createmotorsport.physics.spec.BrakeSpec;
import com.createmotorsport.physics.spec.ClutchSpec;
import com.createmotorsport.physics.spec.DamperSpec;
import com.createmotorsport.physics.spec.DiffParams;
import com.createmotorsport.physics.spec.DifferentialSpec;
import com.createmotorsport.physics.spec.DriveInputSpec;
import com.createmotorsport.physics.spec.EngineSpec;
import com.createmotorsport.physics.spec.GearboxSpec;
import com.createmotorsport.physics.spec.ModelParams;
import com.createmotorsport.physics.spec.SurfaceResponse;
import com.createmotorsport.physics.spec.SuspensionArt;
import com.createmotorsport.physics.spec.SuspensionSpec;
import com.createmotorsport.physics.spec.TerrainAssistSpec;
import com.createmotorsport.physics.spec.ThermalSpec;
import com.createmotorsport.physics.spec.TireSpec;
import com.createmotorsport.physics.spec.TorqueSplitSpec;
import com.createmotorsport.physics.spec.StarterSpec;
import com.createmotorsport.physics.spec.TractionControlSpec;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class SpecCodecs {

    static <T> MapCodec<T> nested(Codec<T> codec, String name, T fallback) {
        return field(codec, name, fallback);
    }
    static <T> MapCodec<T> field(Codec<T> codec, String name, T fallback) {
        return codec.optionalFieldOf(name).xmap(o -> o.orElse(fallback), Optional::of);
    }

    private SpecCodecs() {
    }

    public static final Codec<SurfaceResponse> SURFACE_RESPONSE = RecordCodecBuilder.create(i -> i.group(
            field(Codec.DOUBLE, "peak_mu_scale", 1.0).forGetter(SurfaceResponse::peakMuScale),
            field(Codec.DOUBLE, "slip_at_peak_scale", 1.0)
                    .forGetter(SurfaceResponse::slipAtPeakScale),
            field(Codec.DOUBLE, "slide_grip_scale", 1.0).forGetter(SurfaceResponse::slideGripScale),
            field(Codec.DOUBLE, "rolling_resistance_scale", 1.0)
                    .forGetter(SurfaceResponse::rollingResistanceScale)
    ).apply(i, SurfaceResponse::new));

    public static final Codec<SurfaceResponse.ByCategory> SURFACES = RecordCodecBuilder.create(i -> i.group(
            nested(SURFACE_RESPONSE, "road", SurfaceResponse.NEUTRAL)
                    .forGetter(SurfaceResponse.ByCategory::road),
            nested(SURFACE_RESPONSE, "dirt", SurfaceResponse.NEUTRAL)
                    .forGetter(SurfaceResponse.ByCategory::dirt),
            nested(SURFACE_RESPONSE, "gravel", SurfaceResponse.NEUTRAL)
                    .forGetter(SurfaceResponse.ByCategory::gravel),
            nested(SURFACE_RESPONSE, "sand", SurfaceResponse.NEUTRAL)
                    .forGetter(SurfaceResponse.ByCategory::sand)
    ).apply(i, SurfaceResponse.ByCategory::new));

    public static final Codec<TireSpec.Axis> AXIS = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("slip_at_peak").forGetter(TireSpec.Axis::slipAtPeak),
            field(Codec.DOUBLE, "peak_ratio", 1.0).forGetter(TireSpec.Axis::peakRatio),
            Codec.DOUBLE.fieldOf("slide_grip").forGetter(TireSpec.Axis::slideGrip),
            Codec.DOUBLE.fieldOf("slide_slip_factor").forGetter(TireSpec.Axis::slideSlipFactor),
            Codec.DOUBLE.fieldOf("initial_stiffness").forGetter(TireSpec.Axis::initialStiffness)
    ).apply(i, TireSpec.Axis::new));

    public static final Codec<DamperSpec> DAMPER = RecordCodecBuilder.create(i -> i.group(
            field(Codec.DOUBLE, "knee_velocity", 0.0).forGetter(DamperSpec::kneeVelocity),
            field(Codec.DOUBLE, "blow_off_slope", 1.0).forGetter(DamperSpec::blowOffSlope)
    ).apply(i, DamperSpec::new));

    public static final Codec<TerrainAssistSpec> TERRAIN_ASSIST = RecordCodecBuilder.create(i -> i.group(
            field(Codec.DOUBLE, "max_vertical_rate", TerrainAssistSpec.DEFAULT.maxVerticalRate())
                    .forGetter(TerrainAssistSpec::maxVerticalRate),
            field(Codec.DOUBLE, "max_climb_fraction", TerrainAssistSpec.DEFAULT.maxClimbFraction())
                    .forGetter(TerrainAssistSpec::maxClimbFraction),
            field(Codec.DOUBLE, "look_ahead_blocks", TerrainAssistSpec.DEFAULT.lookAheadBlocks())
                    .forGetter(TerrainAssistSpec::lookAheadBlocks),
            field(Codec.DOUBLE, "extra_max_slope", TerrainAssistSpec.DEFAULT.extraMaxSlope())
                    .forGetter(TerrainAssistSpec::extraMaxSlope),
            field(Codec.DOUBLE, "extra_max_climb_blocks", TerrainAssistSpec.DEFAULT.extraMaxClimbBlocks())
                    .forGetter(TerrainAssistSpec::extraMaxClimbBlocks),
            field(Codec.DOUBLE, "extra_look_ahead_blocks", TerrainAssistSpec.DEFAULT.extraLookAheadBlocks())
                    .forGetter(TerrainAssistSpec::extraLookAheadBlocks)
    ).apply(i, TerrainAssistSpec::new));

    public static final Codec<TireSpec.Model> TIRE_MODEL = Codec.STRING.comapFlatMap(
            id -> {
                TireSpec.Model m = TireSpec.Model.byId(id);
                return m == null ? DataResult.error(() -> "Unknown tire model: " + id) : DataResult.success(m);
            }, TireSpec.Model::id);

    public static final Codec<SuspensionSpec.Type> SUSPENSION_TYPE = Codec.STRING.comapFlatMap(
            id -> {
                SuspensionSpec.Type t = SuspensionSpec.Type.byId(id);
                return t == null ? DataResult.error(() -> "Unknown suspension type: " + id) : DataResult.success(t);
            }, SuspensionSpec.Type::id);

    public static final Codec<BrakeSpec> BRAKE = RecordCodecBuilder.create(i -> i.group(
            field(Codec.DOUBLE, "strength", 2000.0).forGetter(BrakeSpec::strength),
            field(Codec.BOOL, "abs", true).forGetter(BrakeSpec::abs),
            field(Codec.DOUBLE, "abs_slip_threshold", 0.15).forGetter(BrakeSpec::absSlipThreshold),
            field(Codec.DOUBLE, "abs_dump_rate", BrakeSpec.DEFAULT.absDumpRate()).forGetter(BrakeSpec::absDumpRate),
            field(Codec.DOUBLE, "abs_build_rate", BrakeSpec.DEFAULT.absBuildRate())
                    .forGetter(BrakeSpec::absBuildRate),
            field(Codec.DOUBLE, "abs_min_speed", 1.5).forGetter(BrakeSpec::absMinSpeed),
            field(Codec.DOUBLE, "abs_floor", 0.05).forGetter(BrakeSpec::absFloor),
            field(Codec.doubleRange(0.0, 5.0), "apply_time", BrakeSpec.DEFAULT.applyTime())
                    .forGetter(BrakeSpec::applyTime)
    ).apply(i, BrakeSpec::new));

    public static final Codec<SuspensionArt> SUSPENSION_ART = RecordCodecBuilder.create(i -> i.group(
            field(Codec.STRING, "model", SuspensionArt.DEFAULT.model()).forGetter(SuspensionArt::model),
            field(Codec.STRING, "texture", SuspensionArt.DEFAULT.texture()).forGetter(SuspensionArt::texture),
            field(Codec.STRING, "animation", SuspensionArt.DEFAULT.animation())
                    .forGetter(SuspensionArt::animation)
    ).apply(i, SuspensionArt::new));

    private record SuspensionExtras(double wheelOffset, double maxRideHeight, SuspensionArt art) {
    }

    private static final MapCodec<SuspensionExtras> SUSPENSION_EXTRAS = RecordCodecBuilder.mapCodec(i -> i.group(
            field(Codec.doubleRange(-SuspensionSpec.MAX_WHEEL_OFFSET, SuspensionSpec.MAX_WHEEL_OFFSET), "wheel_offset",
                    SuspensionSpec.DEFAULT_WHEEL_OFFSET).forGetter(SuspensionExtras::wheelOffset),
            field(Codec.DOUBLE, "max_ride_height", SuspensionSpec.DEFAULT_MAX_RIDE_HEIGHT)
                    .forGetter(SuspensionExtras::maxRideHeight),
            nested(SUSPENSION_ART, "art", SuspensionArt.DEFAULT).forGetter(SuspensionExtras::art)
    ).apply(i, SuspensionExtras::new));

    public static final Codec<SuspensionSpec> SUSPENSION_SPEC = RecordCodecBuilder.create(i -> i.group(
            field(SUSPENSION_TYPE, "type", SuspensionSpec.Type.VERTICAL_STRUT)
                    .forGetter(SuspensionSpec::type),
            Codec.BOOL.optionalFieldOf("driven").forGetter(SuspensionSpec::driven),
            Codec.DOUBLE.fieldOf("natural_freq_hz").forGetter(SuspensionSpec::naturalFreqHz),
            Codec.DOUBLE.fieldOf("damping_ratio").forGetter(SuspensionSpec::dampingRatio),
            field(Codec.DOUBLE, "ride_height", SuspensionSpec.DEFAULT_RIDE_HEIGHT)
                    .forGetter(SuspensionSpec::rideHeight),
            field(Codec.DOUBLE, "track_width", SuspensionSpec.DEFAULT_TRACK_WIDTH)
                    .forGetter(SuspensionSpec::trackWidth),
            field(Codec.DOUBLE, "max_steer_degrees", SuspensionSpec.DEFAULT_MAX_STEER_DEGREES)
                    .forGetter(SuspensionSpec::maxSteerDegrees),
            field(Codec.DOUBLE, "ackermann", 0.0).forGetter(SuspensionSpec::ackermann),
            field(Codec.DOUBLE, "camber_deg", SuspensionSpec.DEFAULT_CAMBER_DEG)
                    .forGetter(SuspensionSpec::camberDeg),
            field(Codec.DOUBLE, "camber_stiffness", SuspensionSpec.DEFAULT_CAMBER_STIFFNESS)
                    .forGetter(SuspensionSpec::camberStiffness),
            field(Codec.DOUBLE, "steer_full_lock_speed",
                    SuspensionSpec.DEFAULT_STEER_FULL_LOCK_SPEED)
                    .forGetter(SuspensionSpec::steerFullLockSpeed),
            field(Codec.DOUBLE, "roll_influence", SuspensionSpec.DEFAULT_ROLL_INFLUENCE)
                    .forGetter(SuspensionSpec::rollInfluence),
            SUSPENSION_EXTRAS.forGetter(s -> new SuspensionExtras(s.wheelOffset(), s.maxRideHeight(), s.art())),
            nested(BRAKE, "brakes", BrakeSpec.DEFAULT).forGetter(SuspensionSpec::brakes),
            nested(DAMPER, "damper", DamperSpec.F1_DIGRESSIVE).forGetter(SuspensionSpec::damper),
            nested(TERRAIN_ASSIST, "terrain_assist", TerrainAssistSpec.DEFAULT)
                    .forGetter(SuspensionSpec::terrainAssist)
    ).apply(i, (type, driven, freq, zeta, ride, track, maxSteer, ackermann, camber, camberK, lockSpeed,
                roll, extras, brakes, damper, assist) -> new SuspensionSpec(type, driven, freq, zeta, ride, track,
            extras.wheelOffset(), maxSteer, ackermann, camber, camberK, lockSpeed, roll, extras.maxRideHeight(),
            extras.art(), brakes, damper, assist)));

    public static final Codec<DriveInputSpec> DRIVE_INPUT = RecordCodecBuilder.create(i -> i.group(
            field(Codec.DOUBLE, "engage_rate_nm_per_s", 0.0)
                    .forGetter(DriveInputSpec::engageRateNmPerS),
            field(Codec.DOUBLE, "lag_seconds", 0.0).forGetter(DriveInputSpec::lagSeconds)
    ).apply(i, DriveInputSpec::new));

    public static final Codec<DifferentialSpec.Type> DIFF_TYPE = Codec.STRING.comapFlatMap(
            id -> {
                DifferentialSpec.Type t = DifferentialSpec.Type.byId(id);
                return t == null ? DataResult.error(() -> "Unknown differential type: " + id)
                        : DataResult.success(t);
            }, DifferentialSpec.Type::id);

    public static final MapCodec<DiffParams> OPEN_PARAMS = MapCodec.unit(() -> new DiffParams.Open());

    public static final MapCodec<DiffParams> LOCKED_PARAMS = MapCodec.unit(() -> new DiffParams.Locked());

    public static final MapCodec<DiffParams> CLUTCH_LSD_PARAMS = RecordCodecBuilder.mapCodec(i -> i.group(
            field(Codec.DOUBLE, "anti_slip_torque", 200.0)
                    .forGetter(p -> ((DiffParams.ClutchLsd) p).antiSlipTorque()),
            field(Codec.DOUBLE, "drift_anti_slip_torque", 40.0)
                    .forGetter(p -> ((DiffParams.ClutchLsd) p).driftAntiSlipTorque()),
            field(Codec.DOUBLE, "torque_sensitivity", 0.0)
                    .forGetter(p -> ((DiffParams.ClutchLsd) p).torqueSensitivity()),
            field(Codec.DOUBLE, "deceleration_factor", 0.0)
                    .forGetter(p -> ((DiffParams.ClutchLsd) p).decelerationFactor())
    ).apply(i, DiffParams.ClutchLsd::new));

    private static MapCodec<DiffParams> diffParams(DifferentialSpec.Type type) {
        return switch (type) {
            case OPEN -> OPEN_PARAMS;
            case CLUTCH_LSD -> CLUTCH_LSD_PARAMS;
            case LOCKED -> LOCKED_PARAMS;
        };
    }

    public static final Codec<DifferentialSpec> DIFFERENTIAL_SPEC =
            DIFF_TYPE.dispatch("type", DifferentialSpec::type, SpecCodecs::differentialCodec);

    private static MapCodec<DifferentialSpec> differentialCodec(DifferentialSpec.Type type) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                diffParams(type).forGetter(DifferentialSpec::params),
                nested(DRIVE_INPUT, "input", DriveInputSpec.INSTANT)
                        .forGetter(DifferentialSpec::input)
        ).apply(i, DifferentialSpec::new));
    }

    public static final Codec<TorqueSplitSpec> TORQUE_SPLIT = RecordCodecBuilder.create(i -> i.group(
            field(Codec.DOUBLE, "front_bias", 0.5).forGetter(TorqueSplitSpec::frontBias),
            field(Codec.DOUBLE, "lock", 0.5).forGetter(TorqueSplitSpec::lock),
            field(Codec.DOUBLE, "coupling", 50.0).forGetter(TorqueSplitSpec::coupling)
    ).apply(i, TorqueSplitSpec::new));

    public static final Codec<TractionControlSpec> TRACTION_CONTROL = RecordCodecBuilder.create(i -> i.group(
            field(Codec.DOUBLE, "target_slip", 0.14).forGetter(TractionControlSpec::targetSlip),
            field(Codec.DOUBLE, "min_throttle", 0.02).forGetter(TractionControlSpec::minThrottle),
            field(Codec.DOUBLE, "cut_rate", 10.0).forGetter(TractionControlSpec::cutRate),
            field(Codec.DOUBLE, "build_rate", 8.0).forGetter(TractionControlSpec::buildRate),
            field(Codec.doubleRange(0.0, 1.0), "drag_max_throttle", 0.3)
                    .forGetter(TractionControlSpec::dragMaxThrottle)
    ).apply(i, TractionControlSpec::new));

    public static final Codec<StarterSpec> STARTER = RecordCodecBuilder.create(i -> i.group(
            field(Codec.doubleRange(0.0, Double.MAX_VALUE), "stall_torque", 0.0).forGetter(StarterSpec::stallTorque),
            field(Codec.doubleRange(0.0, Double.MAX_VALUE), "free_rpm", 0.0).forGetter(StarterSpec::freeRpm)
    ).apply(i, StarterSpec::new));

    public static final Codec<GearboxSpec.Type> GEARBOX_TYPE = Codec.STRING.comapFlatMap(
            id -> {
                GearboxSpec.Type t = GearboxSpec.Type.byId(id);
                return t == null ? DataResult.error(() -> "Unknown gearbox type: " + id) : DataResult.success(t);
            }, GearboxSpec.Type::id);

    public static final Codec<ClutchSpec> CLUTCH = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("max_torque").forGetter(ClutchSpec::maxTorque),
            Codec.DOUBLE.fieldOf("lock_stiffness").forGetter(ClutchSpec::lockStiffness),
            field(Codec.DOUBLE, "engage_time", 0.15).forGetter(ClutchSpec::engageTime),
            Codec.DOUBLE.fieldOf("launch_rpm").forGetter(ClutchSpec::launchRpm),
            field(Codec.DOUBLE, "launch_grab", 0.75).forGetter(ClutchSpec::launchGrab),
            field(Codec.DOUBLE, "hold_grab", 1.30).forGetter(ClutchSpec::holdGrab),
            field(Codec.DOUBLE, "flare_band", 2500.0).forGetter(ClutchSpec::flareBand),
            field(Codec.DOUBLE, "dump_band", 4000.0).forGetter(ClutchSpec::dumpBand),
            field(Codec.DOUBLE, "idle_creep", 0.10).forGetter(ClutchSpec::idleCreep)
    ).apply(i, ClutchSpec::new));

    public static final Codec<GearboxSpec> GEARBOX_SPEC = RecordCodecBuilder.create(i -> i.group(
            field(GEARBOX_TYPE, "type", GearboxSpec.Type.SEQUENTIAL).forGetter(GearboxSpec::type),
            Codec.DOUBLE.listOf().fieldOf("forward_ratios").forGetter(GearboxSpec::forwardRatios),
            Codec.DOUBLE.fieldOf("reverse_ratio").forGetter(GearboxSpec::reverseRatio),
            Codec.DOUBLE.optionalFieldOf("final_drive").forGetter(GearboxSpec::finalDrive),
            field(Codec.BOOL, "semi_auto", false).forGetter(GearboxSpec::semiAuto),
            field(Codec.DOUBLE, "shift_interrupt_time", 0.0)
                    .forGetter(GearboxSpec::shiftInterruptTime),
            field(Codec.BOOL, "rev_match", false).forGetter(GearboxSpec::revMatch),
            CLUTCH.fieldOf("clutch").forGetter(GearboxSpec::clutch),
            nested(TORQUE_SPLIT, "center_diff", TorqueSplitSpec.DEFAULT)
                    .forGetter(GearboxSpec::centerDiff),
            nested(TORQUE_SPLIT, "inter_axle_diff", TorqueSplitSpec.OPEN)
                    .forGetter(GearboxSpec::interAxleDiff)
    ).apply(i, GearboxSpec::new));

    public static final Codec<EngineSpec.TorquePoint> TORQUE_POINT = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("rpm").forGetter(EngineSpec.TorquePoint::rpm),
            Codec.DOUBLE.fieldOf("nm").forGetter(EngineSpec.TorquePoint::nm)
    ).apply(i, EngineSpec.TorquePoint::new));

    public static final Codec<EngineSpec> ENGINE_SPEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("design_vehicle_mass_blocks").forGetter(EngineSpec::designVehicleMassBlocks),
            Codec.DOUBLE.fieldOf("idle_rpm").forGetter(EngineSpec::idleRpm),
            Codec.DOUBLE.fieldOf("redline_rpm").forGetter(EngineSpec::redlineRpm),
            Codec.DOUBLE.fieldOf("shift_up_rpm").forGetter(EngineSpec::shiftUpRpm),
            Codec.DOUBLE.fieldOf("shift_down_rpm").forGetter(EngineSpec::shiftDownRpm),
            field(Codec.DOUBLE, "driveline_efficiency", 0.93)
                    .forGetter(EngineSpec::drivelineEfficiency),
            field(Codec.DOUBLE, "engine_brake_fraction", 0.15)
                    .forGetter(EngineSpec::engineBrakeFraction),
            field(Codec.DOUBLE, "engine_inertia", 0.10).forGetter(EngineSpec::engineInertia),
            Codec.DOUBLE.fieldOf("final_drive").forGetter(EngineSpec::finalDrive),
            Codec.DOUBLE.fieldOf("real_vehicle_mass_kg").forGetter(EngineSpec::realVehicleMassKg),
            TORQUE_POINT.listOf().fieldOf("torque_curve").forGetter(EngineSpec::torqueCurve),
            nested(TRACTION_CONTROL, "traction_control", TractionControlSpec.DEFAULT)
                    .forGetter(EngineSpec::tractionControl),
            field(Codec.doubleRange(0.0, Double.MAX_VALUE), "stall_rpm", 0.0).forGetter(EngineSpec::stallRpm),
            nested(STARTER, "starter", StarterSpec.DEFAULT).forGetter(EngineSpec::starter)
    ).apply(i, EngineSpec::new));

    public static final Codec<TireEntry> TIRE_ENTRY = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(SpecRegistries.TIRE_COMPOUND).fieldOf("compound").forGetter(TireEntry::compound),
            Codec.DOUBLE.fieldOf("design_mass_kg").forGetter(TireEntry::designMassKg),
            Codec.FLOAT.fieldOf("radius").forGetter(TireEntry::radius),
            field(Vec3.CODEC, "rotation", Vec3.ZERO).forGetter(TireEntry::rotation),
            field(Vec3.CODEC, "offset", Vec3.ZERO).forGetter(TireEntry::offset),
            ResourceLocation.CODEC.optionalFieldOf("model").forGetter(TireEntry::model),
            field(Codec.FLOAT, "minimum_friction", 0.0f).forGetter(TireEntry::minimumFriction),
            field(Codec.FLOAT, "vertical_deflection", TireEntry.DEFAULT_VERTICAL_DEFLECTION)
                    .forGetter(TireEntry::verticalDeflection),
            field(Codec.FLOAT, "vertical_damping_ratio", TireEntry.DEFAULT_VERTICAL_DAMPING)
                    .forGetter(TireEntry::verticalDampingRatio),
            field(Codec.floatRange(0.0f, 10000.0f), "mass", 0.0f).forGetter(TireEntry::mass),
            field(Codec.floatRange(0.0f, 16.0f), "width", 0.0f).forGetter(TireEntry::width),
            ResourceLocation.CODEC.optionalFieldOf("item").forGetter(TireEntry::item)
    ).apply(i, TireEntry::new));

    public static final Codec<TireFit> TIRE_FIT = RecordCodecBuilder.create(i -> i.group(
            Codec.doubleRange(1.0, 100000.0).fieldOf("design_mass_kg").forGetter(TireFit::designMassKg),
            Codec.floatRange(0.05f, 16.0f).fieldOf("radius").forGetter(TireFit::radius),
            Codec.floatRange(0.001f, 1.0f).fieldOf("vertical_deflection").forGetter(TireFit::verticalDeflection),
            Codec.floatRange(0.0f, 10.0f).fieldOf("vertical_damping_ratio").forGetter(TireFit::verticalDampingRatio),
            Codec.floatRange(0.0f, 10000.0f).fieldOf("mass").forGetter(TireFit::mass),
            Codec.floatRange(0.0f, 16.0f).fieldOf("width").forGetter(TireFit::width)
    ).apply(i, TireFit::new));

    public static final MapCodec<ModelParams> TMEASY_PARAMS = RecordCodecBuilder.mapCodec(i -> i.group(
            AXIS.fieldOf("longitudinal").forGetter(p -> ((ModelParams.TMeasy) p).longitudinal()),
            AXIS.fieldOf("lateral").forGetter(p -> ((ModelParams.TMeasy) p).lateral()),
            field(Codec.DOUBLE, "slip_at_peak_lat_load_rise", 1.24589)
                    .forGetter(p -> ((ModelParams.TMeasy) p).slipAtPeakLatLoadRise()),
            field(Codec.DOUBLE, "relax_length_lat", ModelParams.TMeasy.DEFAULT_RELAX_LENGTH_LAT)
                    .forGetter(p -> ((ModelParams.TMeasy) p).relaxLengthLat()),
            field(Codec.DOUBLE, "bristle_damping", TireSpec.TMEASY_BRISTLE_DAMPING)
                    .forGetter(p -> ((ModelParams.TMeasy) p).bristleDamping()),
            field(Codec.DOUBLE, "standstill_leak", TireSpec.TMEASY_STANDSTILL_LEAK)
                    .forGetter(p -> ((ModelParams.TMeasy) p).standstillLeak()),
            field(Codec.DOUBLE, "standstill_speed", TireSpec.TMEASY_STANDSTILL_SPEED)
                    .forGetter(p -> ((ModelParams.TMeasy) p).standstillSpeed())
    ).apply(i, ModelParams.TMeasy::new));

    public static final MapCodec<ModelParams> PACEJKA_PARAMS = RecordCodecBuilder.mapCodec(i -> i.group(
            field(Codec.DOUBLE, "cornering_stiffness", TireSpec.PACEJKA_DEFAULTS.corneringStiffness())
                    .forGetter(p -> ((ModelParams.Pacejka) p).corneringStiffness()),
            field(Codec.DOUBLE, "r_factor", TireSpec.PACEJKA_DEFAULTS.rFactor())
                    .forGetter(p -> ((ModelParams.Pacejka) p).rFactor()),
            field(Codec.DOUBLE, "e_factor", TireSpec.PACEJKA_DEFAULTS.eFactor())
                    .forGetter(p -> ((ModelParams.Pacejka) p).eFactor())
    ).apply(i, ModelParams.Pacejka::new));

    public static final MapCodec<ModelParams> FIALA_PARAMS = RecordCodecBuilder.mapCodec(i -> i.group(
            field(Codec.DOUBLE, "slip_stiffness", TireSpec.FIALA_DEFAULTS.slipStiffness())
                    .forGetter(p -> ((ModelParams.Fiala) p).slipStiffness()),
            field(Codec.DOUBLE, "cornering_stiffness", TireSpec.FIALA_DEFAULTS.corneringStiffness())
                    .forGetter(p -> ((ModelParams.Fiala) p).corneringStiffness()),
            field(Codec.DOUBLE, "mu_max", TireSpec.FIALA_DEFAULTS.muMax())
                    .forGetter(p -> ((ModelParams.Fiala) p).muMax()),
            field(Codec.DOUBLE, "mu_min", TireSpec.FIALA_DEFAULTS.muMin())
                    .forGetter(p -> ((ModelParams.Fiala) p).muMin()),
            field(Codec.DOUBLE, "relax_length", TireSpec.FIALA_DEFAULTS.relaxLength())
                    .forGetter(p -> ((ModelParams.Fiala) p).relaxLength())
    ).apply(i, ModelParams.Fiala::new));

    public static final MapCodec<ModelParams> ARCADE_PARAMS =
            MapCodec.unit(() -> new ModelParams.Arcade());

    public static Set<String> modelFields(TireSpec.Model model) {
        return paramsCodec(model).keys(JsonOps.INSTANCE).map(JsonElement::getAsString).collect(Collectors.toSet());
    }

    private static MapCodec<ModelParams> paramsCodec(TireSpec.Model model) {
        return switch (model) {
            case TMEASY -> TMEASY_PARAMS;
            case PACEJKA -> PACEJKA_PARAMS;
            case FIALA -> FIALA_PARAMS;
            case ARCADE -> ARCADE_PARAMS;
        };
    }

    public static final Codec<ThermalSpec> THERMAL = RecordCodecBuilder.create(i -> i.group(
            field(Codec.DOUBLE, "optimal_temp_c", ThermalSpec.DEFAULT.optimalTempC())
                    .forGetter(ThermalSpec::optimalTempC),
            field(Codec.DOUBLE, "cold_grip_factor", ThermalSpec.DEFAULT.coldGripFactor())
                    .forGetter(ThermalSpec::coldGripFactor),
            field(Codec.DOUBLE, "heating_rate", ThermalSpec.DEFAULT.heatingRate())
                    .forGetter(ThermalSpec::heatingRate),
            field(Codec.DOUBLE, "cooling_rate", ThermalSpec.DEFAULT.coolingRate())
                    .forGetter(ThermalSpec::coolingRate)
    ).apply(i, ThermalSpec::new));

    public static final Codec<TireSpec> TIRE_SPEC =
            TIRE_MODEL.dispatch("model", TireSpec::model, SpecCodecs::specCodec);

    private static MapCodec<TireSpec> specCodec(TireSpec.Model model) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                field(Codec.DOUBLE, "grip", 1.0).forGetter(TireSpec::grip),
                field(Codec.DOUBLE, "load_factor_light", 1.15).forGetter(TireSpec::loadFactorLight),
                field(Codec.DOUBLE, "load_factor_heavy", 0.80).forGetter(TireSpec::loadFactorHeavy),
                field(Codec.DOUBLE, "load_falloff", -0.6).forGetter(TireSpec::loadFalloff),
                paramsCodec(model).forGetter(TireSpec::params),
                nested(SURFACES, "surfaces", SurfaceResponse.ByCategory.NEUTRAL)
                        .forGetter(TireSpec::surfaces),
                nested(THERMAL, "thermal", ThermalSpec.DEFAULT).forGetter(TireSpec::thermal),
                field(Codec.DOUBLE, "rolling_resistance", TireSpec.DEFAULT_ROLLING_RESISTANCE)
                        .forGetter(TireSpec::rollingResistance)
        ).apply(i, TireSpec::new));
    }
}
