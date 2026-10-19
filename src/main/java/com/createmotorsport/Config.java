package com.createmotorsport;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue FUEL_TANK_CAPACITY = BUILDER
            .defineInRange("fuelTankCapacityMb", 16000, 1000, 1000000);
    public static final ModConfigSpec.IntValue PUMP_CAPACITY = BUILDER
            .defineInRange("fuelPumpCapacityMb", 16000, 1000, 1000000);
    public static final ModConfigSpec.DoubleValue PUMP_RANGE = BUILDER
            .defineInRange("fuelPumpHoseRange", 12.0, 2.0, 64.0);
    public static final ModConfigSpec.IntValue PUMP_TRANSFER = BUILDER
            .defineInRange("fuelPumpTransferMb", 250, 1, 16000);

    public static final ModConfigSpec.BooleanValue ALLOW_PLAYER_TUNING = BUILDER
            .comment("Whether any player may change a part's settings on its Tune screen",
                    "Default true")
            .define("allowPlayerTuning", true);

    public static final ModConfigSpec.BooleanValue ENABLE_DEBUG_LOGGING = BUILDER
            .comment("Set to 'true' to see additional debugging logging",
                    "Default false")
            .define("enableDebugLogging", false);

    // ======================================================================
    // ENGINE
    // ======================================================================
    static { BUILDER.push("engine"); }

    public static final ModConfigSpec.DoubleValue DRIVETRAIN_TRIM = BUILDER
            .comment("Drivetrain Torque now scales with the engine's design mass. ",
                    "Keeping this as a backup way to affect torque",
                    "Default 1.0")
            .defineInRange("drivetrainTrim", 1.0, 0.01, 10.0);

    static { BUILDER.pop(); }

    // =======================================================================
    // PHYSICS
    // =======================================================================
    static { BUILDER.push("physics"); }

    // ---- Tire models (1 Arcade, 2 Pacejka, 3 Fiala, 4 TMeasy) -----------------------------------
    public static final ModConfigSpec.DoubleValue SIM_SLIP_LIMIT = BUILDER
            .comment("Tire model 2 (Pacejka) only; caps how far up the slip curve the slip is allowed to travel,",
                    "so the curve keeps most of its grip until here, then that grip is held; LOWER = more arcade-y,",
                    "HIGHER = easier to spin out; default 1.2")
            .defineInRange("simSlipLimit", 1.2, 0.8, 8.0);

    public static final ModConfigSpec.DoubleValue SIM_LATERAL_GRIP = BUILDER
            .comment("Tire model 2 (Pacejka) only; lateral (sideways) grip as a multiple of longitudinal grip.",
                    "Changing from 1.0 creates a friction ellipse instead of a circle",
                    "1.0 = equal both ways (friction circle)",
                    ">1 = more sideways grip",
                    "Default is 2.0 for now, but needs refining I think")
            .defineInRange("simLateralGrip", 2, 0.3, 3.0);

    public static final ModConfigSpec.DoubleValue FRICTION_ELLIPSE_LONG_WEIGHT = BUILDER
            .comment("Tire model 1 (Arcade) only; weights longitudinal grip inside the friction ellipse",
                    "(so a combined accelerate-while-turning budget) ",
                    "<1 lets you put more power down mid-corner; ",
                    ">1 makes the throttle take from this budget more. Default 0.5")
            .defineInRange("frictionEllipseLongWeight", 0.5, 0.1, 2.0);

    public static final ModConfigSpec.DoubleValue LATERAL_GRIP_FRACTION = BUILDER
            .comment("Tire models 1, 2 and 3 (Arcade, Pacejka, Fiala) only,",
                    "and on 2 and 3 only below simLowSpeedBlend",
                    "Fraction of a wheel's sideways velocity killed per substep by lateral grip",
                    "Higher = more track/rail-like, lower = slidier; default 0.5")
            .defineInRange("lateralGripFraction", 0.5, 0.05, 1.0);

    public static final ModConfigSpec.DoubleValue SIM_LOWSPEED_BLEND_MS = BUILDER
            .comment("Tire model 2 & 3 (Pacejka, Fiala) only; below this speed (m/s) the saturated slip-angle force",
                    "would jitter or spin a car and never stop, so lateral grip blends back to the simple arcade",
                    "cancellation",
                    "The Fiala model will work better with a lower blend, but turn it up to 2m/s if using model 2",
                    "Default 1.0 m/s (~3.6 km/h)")
            .defineInRange("simLowSpeedBlend", 1.0, 0.0, 15.0);

    public static final ModConfigSpec.DoubleValue SIM_LOWSPEED_REF = BUILDER
            .comment("Tire models 2, 3 and 4 (Pacejka, Fiala, TMeasy); floor (in m/s) on the speed used as the",
                    "denominator for slip ratio and slip angle, so they stay finite as the car slows.",
                    "Raising this value helps with twitchy behavior near a stop at the cost of less responsive grip",
                    "below this speed.",
                    "Default 1.0")
            .defineInRange("simLowSpeedRef", 1.0, 0.1, 5.0);

    public static final ModConfigSpec.DoubleValue TIRE_FORCE_RELAXATION = BUILDER
            .comment("How fast the longitudinal tire force chases its target each substep",
                    "(speed dreams' FLOAT_RELAXATION); 1.0 = instant (might judder on launch)",
                    "lower = smoother but laggier. Default 0.22",
                    "",
                    "Difficulty setting, raise to make harder")
            .defineInRange("tireForceRelaxation", 0.22, 0.05, 1.0);

    // ---- Suspension and wheels ------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue MAX_CORNERING_G = BUILDER
            .comment("Cap on suspension spring force, as a multiple of the wheel's share of the car's sprung mass",
                    "multiplied by gravity",
                    "Default 12.0")
            .defineInRange("maxCorneringG", 12.0, 1.0, 20.0);

    public static final ModConfigSpec.BooleanValue TIRE_COMPLIANCE = BUILDER
            .define("tireCompliance", true);

    public static final ModConfigSpec.DoubleValue WHEEL_MASS = BUILDER
            .comment("Wheel and tyre mass for tyres that do not state their own.",
                    "Scales with the car's measured mass. A tyre's own \"mass\" in its datapack entry replaces it",
                    "Default 2.0")
            .defineInRange("tireMass", 2.0, 0.2, 100);

    // ---- Tire temperature (Speed Dreams style; applies to all tire models) ----------------------
    public static final ModConfigSpec.BooleanValue TIRE_THERMAL_MODEL = BUILDER
            .comment("Use thermal modeling for tires? Default is false for now")
            .define("tireThermalModel", false);

    public static final ModConfigSpec.DoubleValue TIRE_AMBIENT_TEMP = BUILDER
            .comment("Thermal model only; ambient temperature in Celsius",
                    "Default 30")
            .defineInRange("tireAmbientTemp", 30.0, -40.0, 60.0);

    // ---- Aero -----------------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue AERO_DOWNFORCE = BUILDER
            .comment("Derived from F1 car doing ~30kN at 300km/h",
                    "Default 0.0479")
            .defineInRange("aeroDownforce", 0.0479, 0.0, 2.0);

    public static final ModConfigSpec.DoubleValue AERO_DRAG_COEFFICIENT = BUILDER
            .comment("drag force = k * speed^2; my assumptions for this number are:",
                    "0.5 * rho * Cd*A * v^2; Cd*A should be 1.4, 1.05 with DRS on.",
                    "Then you have to apply the proper force and drag scaling to get this number.",
                    "This number will be affected by the new wings, but it doesnt actually do that now",
                    "Default 0.0359")
            .defineInRange("aeroDragCoefficient", 0.0359, 0.0, 5.0);

    public static final ModConfigSpec.DoubleValue SABLE_DRAG_SCALE = BUILDER
            .comment("How much Sable drag to apply to the car, 0 = none, 1 = all",
                    "We cancel it then apply it correctly, instead of mixin to Sable. ",
                    "If you have a mod that also fixes Aero drag, then",
                    "this probably isnt going to be correct canceling for now, we'll worry about that later",
                    "Default 0.0")
            .defineInRange("sableDragScale", 0.0, 0.0, 1.0);

    static { BUILDER.pop(); }

    // =======================================================================
    // RACE
    // =======================================================================
    static { BUILDER.push("race"); }

    public static final ModConfigSpec.DoubleValue RACE_MIN_LAP_TIME = BUILDER
            .comment("Min lap time is necessary to give time for cars to cross the grid for",
                    "the first time without triggering a new lap",
                    "In seconds. Default 20")
            .defineInRange("raceMinLapTime", 20.0, 0.5, 600.0);

    static { BUILDER.pop(); }

    // =========================================================================
    // CONTROLS
    // =======================================================================
    static { BUILDER.push("controls"); }

    // ---- Keyboard -------------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue KEYPRESS_STEERING_RAMP = BUILDER
            .comment("1 / RAMP / 20 seconds, is the formula for how long",
                    "It takes a key press to be considered a full steering lock",
                    "It also applies equally to the key release. Default is 0.30",
                    "Difficulty setting, raise to make harder")
            .defineInRange("keypressSteeringRamp", 0.30, 0.01, 1.0);

    public static final ModConfigSpec.DoubleValue PEDAL_KEY_RAMP = BUILDER
            .comment("1 / RAMP / 20 seconds, is the formula for how long",
                    "It takes a key press to be considered a full pedal input",
                    "It also applies equally to the key release. Default is 0.30",
                    "Difficulty setting, raise to make harder")
            .defineInRange("pedalKeyRamp", 0.3, 0.01, 1.0);

    // ---- Analog input ---------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue STEER_INPUT_GAMMA = BUILDER
            .comment("Gamepad Controller 'Assist': Exponent applied to analog steering input",
                    "1.0 = linear; softens small stick movements near centre for finer control",
                    "Default 1.8")
            .defineInRange("steerInputGamma", 1.8, 1.0, 4.0);

    public static final ModConfigSpec.DoubleValue PEDAL_INPUT_GAMMA = BUILDER
            .comment("Exponent applied to analog throttle and brake (trigger) input. 1.0 = linear;",
                    "~1.8 and the first half of the signal only asks for ~30% power, easier to feather the throttle",
                    "Default 1.8")
            .defineInRange("pedalInputGamma", 1.8, 1.0, 4.0);

    public static final ModConfigSpec.DoubleValue STEER_INPUT_SENSITIVITY = BUILDER
            .comment("Increase the sensitivity of the steering input, for racing wheel controllers",
                    "Applies to every analog input, gamepad and mouse included",
                    "Default 1.0")
            .defineInRange("steerInputSensitivity", 1.0, 0.25, 4.0);

    public static final ModConfigSpec.DoubleValue PEDAL_INPUT_SENSITIVITY = BUILDER
            .comment("Increase the sensitivity of the throttle and brake input, for racing pedals",
                    "Applies to every analog input, gamepad and mouse included",
                    "Default 1.0")
            .defineInRange("pedalInputSensitivity", 1.0, 0.25, 4.0);

    public static final ModConfigSpec.DoubleValue STICK_DEADZONE = BUILDER
            .comment("Standard gamepad only; left/right thumbstick deadzone, default 0.10")
            .defineInRange("stickDeadzone", 0.10, 0.0, 0.6);

    public static final ModConfigSpec.DoubleValue TRIGGER_DEADZONE = BUILDER
            .comment("Standard gamepad only; left/right trigger deadzone, default 0.10")
            .defineInRange("triggerDeadzone", 0.10, 0.0, 0.6);

    // ---- Racing wheels and other advanced controllers -------------------------------------------
    public static final ModConfigSpec.BooleanValue ENABLE_ADVANCED_INPUT = BUILDER
            .comment("Enable Racing Wheel / Pedal support, or any other advanced controller",
                    "Leave off if you only use standard gamepads. Default is true")
            .define("enableAdvancedInput", true);

    public static final ModConfigSpec.DoubleValue ADVANCED_INPUT_GAMMA = BUILDER
            .comment("Separate steering assist gamma for advanced controllers",
                    "Default 1.0")
            .defineInRange("advancedInputGamma", 1.0, 1.0, 4.0);

    public static final ModConfigSpec.DoubleValue ADVANCED_INPUT_DEADZONE = BUILDER
            .comment("Advanced input only; default 0.05")
            .defineInRange("advancedInputDeadzone", 0.05, 0.0, 0.6);

    // ---- Mouse ----------------------------------------------------------------------------------
    public static final ModConfigSpec.BooleanValue ENABLE_MOUSE_INPUT = BUILDER
            .comment("Enable the mouse to be used for inputs, like steering",
                    "The camera will be locked while driving if this is true, so hold ALT (rebindable)",
                    " to \"Free Look\", to move the camera while driving",
                    "Default false")
            .define("enableMouseInput", false);

    public static final ModConfigSpec.BooleanValue MOUSE_ABSOLUTE_MODE = BUILDER
            .comment("true = ABSOLUTE: mouse offset from center is the steer angle and holds",
                    "false = VELOCITY: mouse movement speed",
                    "Default true")
            .define("mouseAbsoluteMode", true);

    public static final ModConfigSpec.DoubleValue MOUSE_SENSITIVITY = BUILDER
            .comment("Mouse steering sensitivity",
                    "Default 1.0")
            .defineInRange("mouseSensitivity", 1.0, 0.05, 10.0);

    public static final ModConfigSpec.DoubleValue MOUSE_DEADZONE = BUILDER
            .comment("Default 0.0")
            .defineInRange("mouseDeadzone", 0.0, 0.0, 0.6);

    static { BUILDER.pop(); }

    // =========================================================================
    // ANIMATION
    // =======================================================================
    static { BUILDER.push("animation"); }

    public static final ModConfigSpec.DoubleValue STEERING_WHEEL_MAX_ANGLE = BUILDER
            .comment("How far the steering wheel rim turns at full lock, in degrees, each way",
                    "The wheel animates according to the scaled analog input",
                    "This number is purely cosmetic, default 200 degrees")
            .defineInRange("steeringWheelMaxAngle", 200.0, 30.0, 1080.0);

    static { BUILDER.pop(); }

    // =======================================================================
    // EFFECTS
    // =======================================================================
    static { BUILDER.push("effects"); }

    public static final ModConfigSpec.BooleanValue TIRE_SMOKE = BUILDER
            .comment("Turn it off if FPS is an issue")
            .define("tireSmoke", true);

    public static final ModConfigSpec.DoubleValue TIRE_SMOKE_SLIP_THRESHOLD = BUILDER
            .comment("Default 0.35")
            .defineInRange("tireSmokeSlipThreshold", 0.35, 0.05, 1.0);

    public static final ModConfigSpec.DoubleValue TIRE_SMOKE_MIN_SPEED = BUILDER
            .comment("Min speed in m/s. Default 2.0")
            .defineInRange("tireSmokeMinSpeed", 2.0, 0.0, 15.0);

    public static final ModConfigSpec.DoubleValue TIRE_SMOKE_DENSITY = BUILDER
            .comment("Turn this down to help with FPS issues")
            .defineInRange("tireSmokeDensity", 20.0, 0.1, 32.0);

    public static final ModConfigSpec.DoubleValue TIRE_SMOKE_HEAT_BOOST = BUILDER
            .comment("Default 0.5")
            .defineInRange("tireSmokeHeatBoost", 0.5, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue TIRE_SMOKE_GROUND_DUST = BUILDER
            .comment("On certain blocks (currently just dirt, sand, gravel, snow, grass) kick up matching particles instead",
                    "of smoke. Smoke is on every other surface. Default true")
            .define("tireSmokeGroundDust", true);

    public static final ModConfigSpec.BooleanValue SKIDMARKS = BUILDER
            .define("skidmarks", true);

    public static final ModConfigSpec.DoubleValue SKIDMARK_LIFETIME = BUILDER
            .defineInRange("skidmarkLifetime", 40.0, 2.0, 600.0);

    public static final ModConfigSpec.IntValue SKIDMARK_MAX = BUILDER
            .comment("Lower it for better FPS",
                    "Default 4096")
            .defineInRange("skidmarkMax", 4096, 256, 32768);

    public static final ModConfigSpec.DoubleValue SKIDMARK_OPACITY = BUILDER
            .defineInRange("skidmarkOpacity", 0.65, 0.05, 1.0);

    public static final ModConfigSpec.DoubleValue SKIDMARK_WIDTH = BUILDER
            .defineInRange("skidmarkWidth", 0.55, 0.1, 1.5);

    public static final ModConfigSpec.DoubleValue SKIDMARK_TRAIL_TIME = BUILDER
            .comment("How long after slip that skidmarks appear")
            .defineInRange("skidmarkTrailTime", 0.35, 0.0, 3.0);

    static { BUILDER.pop(); }

    // =======================================================================
    // HUD
    // =======================================================================
    static { BUILDER.push("hud"); }

    public static final ModConfigSpec.DoubleValue HUD_SCALE = BUILDER
            .comment("Default 1.0")
            .defineInRange("hudScale", 1.0, 0.25, 4.0);

    public static final ModConfigSpec.IntValue HUD_X = BUILDER
            .comment("Default 4")
            .defineInRange("hudX", 4, 0, 8000);

    public static final ModConfigSpec.IntValue HUD_Y = BUILDER
            .comment("Default 4")
            .defineInRange("hudY", 4, 0, 8000);

    static { BUILDER.pop(); }

    static final ModConfigSpec SPEC = BUILDER.build();

    // for the csv logging, dump the entire config as "section.name,\"value\"" lines, to help identify issues in bug reports
    public static List<String> dumpForLog() {
        List<String> out = new ArrayList<>();
        for (java.lang.reflect.Field field : Config.class.getDeclaredFields()) {
            if (!ModConfigSpec.ConfigValue.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                ModConfigSpec.ConfigValue<?> value = (ModConfigSpec.ConfigValue<?>) field.get(null);
                String path = String.join(".", value.getPath());
                out.add(path + ",\"" + value.get() + "\"");
            } catch (Exception ignored) {
            }
        }
        return out;
    }

}
