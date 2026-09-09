package com.createmotorsport.trailer;

import com.createmotorsport.fuel.FuelCoordinates;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static com.createmotorsport.trailer.CouplingRules.*;
import static org.junit.jupiter.api.Assertions.*;

class CouplingRulesTest {
    @Test void onlyComplementaryHitchesConnect() {
        for(Kind a:Kind.values())for(Kind b:Kind.values()) {
            boolean expected=a==Kind.FIFTH_WHEEL && b==Kind.KINGPIN || a==Kind.KINGPIN && b==Kind.FIFTH_WHEEL
                    || a==Kind.TOW_BALL && b==Kind.COUPLING_HEAD || a==Kind.COUPLING_HEAD && b==Kind.TOW_BALL;
            assertEquals(expected,a.compatible(b),a+" / "+b);
        }
    }
    @Test void transformedAlignmentIsInvariantUnderCommonVehicleRotation() {
        var world=new Quaterniond().rotateXYZ(.2,1.2,-.1);
        assertTrue(aligned(world,new Quaterniond(world).rotateY(Math.toRadians(19)),20));
        assertFalse(aligned(world,new Quaterniond(world).rotateY(Math.toRadians(21)),20));
        assertFalse(aligned(world,new Quaterniond(world).rotateX(Math.toRadians(30)),20));
        assertFalse(aligned(world,new Quaterniond(world).rotateZ(Math.PI),20));
    }
    @Test void translatedRotatedAttachmentRoundTrips() {
        var pose=new Pose3d();pose.position().set(70,30,-40);pose.rotationPoint().set(1000000,0,1000000);
        pose.orientation().rotateXYZ(.3,1.1,-.2);
        for(Direction direction:Direction.Plane.HORIZONTAL) {
            Vec3 local=FuelCoordinates.rotate(new Vec3(.5,.625,.875),direction).add(1000000,5,1000000);
            Vec3 world=pose.transformPosition(local);
            assertTrue(local.distanceTo(pose.transformPositionInverse(world))<1e-6);
            assertTrue(world.distanceTo(local)>1000);
        }
    }
    @Test void captureBoundsRejectNonFiniteValues() {
        assertTrue(near(new Vector3d(),new Vector3d(.28,0,0),.28));
        assertFalse(near(new Vector3d(),new Vector3d(.281,0,0),.28));
        assertFalse(near(new Vector3d(),new Vector3d(Double.NaN),.28));
        assertTrue(speed(.75,.75));assertFalse(speed(.751,.75));assertFalse(speed(Double.NaN,.75));
    }
    @Test void safeReleaseRequiresPermissionSpeedAndSupport() {
        assertEquals(Status.OPEN,release(false,false,true,0,0,.3,true,true,false));
        assertEquals(Status.REMOTE_DISABLED,release(true,false,true,0,0,.3,true,true,false));
        assertEquals(Status.SPEED,release(true,true,true,1,0,.3,true,true,false));
        assertEquals(Status.SPEED,release(false,false,true,0,.4,.3,false,false,false));
        assertEquals(Status.UNSUPPORTED,release(false,false,true,0,0,.3,true,false,false));
        assertEquals(Status.OPEN,release(false,false,true,0,0,.3,true,false,true));
        assertEquals(Status.OPEN,release(false,false,true,0,0,.3,false,false,false));
        assertEquals(Status.INVALID,release(false,false,false,0,0,.3,true,true,true));
    }
    @Test void ownershipIsExplicit() {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        assertTrue(owners(a,a,false));assertFalse(owners(a,b,false));assertTrue(owners(a,b,true));assertTrue(owners(null,a,false));
    }
    @Test void heldRedstoneAndReloadDoNotRepeatActions() {
        Edge edge=new Edge();assertFalse(edge.sample(false));assertTrue(edge.sample(true));
        for(int tick=0;tick<200;tick++)assertFalse(edge.sample(true));
        Edge restored=new Edge();restored.restore(edge.high());assertFalse(restored.sample(true));
        assertFalse(restored.sample(false));assertTrue(restored.sample(true));
    }
    @Test void animationReversesWithoutRestartingOrOvershooting() {
        double position=0;for(int tick=0;tick<3;tick++)position=progress(position,1,.15);
        assertEquals(.45,position,1e-9);assertEquals(.3,progress(position,0,.15),1e-9);
        assertEquals(1,progress(.98,1,.15));assertEquals(0,progress(.02,0,.15));
    }
    @Test void savedReferenceUsesIdentityBodyDimensionAndLocalOffset() {
        var ref=new EquipmentRef("minecraft:overworld",UUID.randomUUID(),new BlockPos(-4,7,9),UUID.randomUUID());
        assertEquals(ref,EquipmentRef.load(ref.save()));
        assertNotEquals(ref,new EquipmentRef(ref.dimension(),UUID.randomUUID(),ref.offset(),ref.endpoint()));
        assertNotEquals(ref,new EquipmentRef("minecraft:the_nether",ref.sublevel(),ref.offset(),ref.endpoint()));
        assertNotEquals(ref,new EquipmentRef(ref.dimension(),ref.sublevel(),ref.offset(),UUID.randomUUID()));
        assertNull(EquipmentRef.load(new CompoundTag()));
        assertEquals(4,ref.save().getAllKeys().size());
    }
    @Test void relationshipRequiresBothMatchingDirections() {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        assertTrue(reciprocal(a,b,a,b));assertFalse(reciprocal(a,b,b,a));
        assertFalse(reciprocal(a,b,a,UUID.randomUUID()));assertFalse(reciprocal(a,b,null,b));
    }
}
