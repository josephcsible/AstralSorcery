package hellfirepvp.astralsorcery.common.entities;

import com.google.common.collect.Lists;
import hellfirepvp.astralsorcery.client.effect.EffectHelper;
import hellfirepvp.astralsorcery.client.effect.EntityComplexFX;
import hellfirepvp.astralsorcery.client.effect.fx.EntityFXFacingParticle;
import hellfirepvp.astralsorcery.client.util.RenderingUtils;
import hellfirepvp.astralsorcery.common.util.MiscUtils;
import hellfirepvp.astralsorcery.common.util.data.Vector3;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is part of the Astral Sorcery Mod
 * The complete source code for this mod can be found on github.
 * Class: EntityGrapplingHook
 * Created by HellFirePvP
 * Date: 23.06.2017 / 13:12
 */
public class EntityGrapplingHook extends EntityThrowable implements IEntityAdditionalSpawnData {

    public static final int DESPAWN_TICKS = 10;
    private static DataParameter<Integer> PULLING_ENTITY = EntityDataManager.createKey(EntityGrapplingHook.class, DataSerializers.VARINT);
    private static DataParameter<Boolean> PULLING = EntityDataManager.createKey(EntityGrapplingHook.class, DataSerializers.BOOLEAN);
    //private static DataParameter<Integer> DESPAWNING = EntityDataManager.createKey(EntityGrapplingHook.class, DataSerializers.VARINT);

    //Thrower -> GrapplingHook
    private static final Map<Integer, Integer> currentGrappling = new HashMap<>(); //Change later to hook upon impact instead of launch
    private boolean added = false;

    //Non-moving handling
    private int timeout = 0;
    private int previousDist = 0;

    public int despawning = -1;
    public float pullFactor = 0.0F;

    private EntityLivingBase throwingEntity;

    public EntityGrapplingHook(World worldIn) {
        super(worldIn);
        setSize(0.1F, 0.1F);
    }

    public EntityGrapplingHook(World worldIn, EntityLivingBase throwerIn) {
        super(worldIn, throwerIn);
        setHeadingFromThrower(throwerIn, throwerIn.rotationPitch, throwerIn.rotationYaw, 0.0F, 1.4F, 0F);
        this.throwingEntity = throwerIn;
        setSize(0.1F, 0.1F);
    }

    @Override
    public void readSpawnData(ByteBuf additionalData) {
        int id = additionalData.readInt();
        try {
            if(id > 0) {
                this.throwingEntity = (EntityLivingBase) world.getEntityByID(id);
            }
        } catch (Exception exc) {}
    }

    @Override
    public void writeSpawnData(ByteBuf buffer) {
        int id = -1;
        if(this.throwingEntity != null) {
            id = this.throwingEntity.getEntityId();
        }
        buffer.writeInt(id);
    }

    @Override
    protected float getGravityVelocity() {
        return isPulling() ? 0F : 0.015F;
    }

    public void setPulling(boolean pull, @Nullable EntityLivingBase hit) {
        this.dataManager.set(PULLING, pull);
        this.dataManager.set(PULLING_ENTITY, hit == null ? -1 : hit.getEntityId());
    }

    public boolean isPulling() {
        return this.dataManager.get(PULLING);
    }

    @Nullable
    public EntityLivingBase getPulling() {
        int idPull = this.dataManager.get(PULLING_ENTITY);
        if(idPull > 0) {
            try {
                return (EntityLivingBase) this.world.getEntityByID(idPull);
            } catch (Exception exc) {}
        }
        return null;
    }

    @Override
    protected void entityInit() {
        super.entityInit();

        //this.dataManager.register(DESPAWNING, -1);
        this.dataManager.register(PULLING, false);
        this.dataManager.register(PULLING_ENTITY, -1);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if(getThrower() == null || getThrower().isDead) {
            setDespawning();
        }
        if(!isPulling() && ticksExisted > 15) {
            setDespawning();
        }

        if(!isDespawning()) {
            EntityLivingBase throwing = getThrower();
            if(!world.isRemote) {
                register(throwing);
            }
            double dist = Math.max(0.01, throwing.getDistanceToEntity(this));
            if(!isDead && isPulling()) {
                if((ticksExisted > 10 && dist < 2) || timeout > 15) {
                    setDespawning();
                } else {
                    getThrower().fallDistance = -5F;
                    double mx = this.posX - getThrower().posX;
                    double my = this.posY - getThrower().posY;
                    double mz = this.posZ - getThrower().posZ;
                    mx /= dist * 5.0D;
                    my /= dist * 5.0D;
                    mz /= dist * 5.0D;
                    Vec3d v2 = new Vec3d(mx, my, mz);
                    if (v2.lengthVector() > 0.25D) {
                        v2 = v2.normalize();
                        mx = v2.x / 4.0D;
                        my = v2.y / 4.0D;
                        mz = v2.z / 4.0D;
                    }
                    getThrower().motionX += mx;
                    getThrower().motionY += my + 0.033D;
                    getThrower().motionZ += mz;

                    int roughDst = (int) (dist / 2.0D);
                    if (roughDst >= this.previousDist) {
                        this.timeout += 1;
                    } else {
                        this.timeout = 0;
                    }
                    this.previousDist = roughDst;
                }
            }
        } else {
            despawnTick();
        }
        if(world.isRemote) {
            if(!isPulling()) {
                this.pullFactor += 0.02F;
            } else {
                this.pullFactor *= 0.66F;
            }
            spawnSparkles();
        }
    }

    @Override
    public boolean shouldRenderInPass(int pass) {
        return pass == 1; //After tr
    }

    @SideOnly(Side.CLIENT)
    private void spawnSparkles() {
        if(despawning == 3 && !isPulling()) {
            Vector3 ePos = RenderingUtils.interpolatePosition(this, 1F);
            List<Vector3> positions = buildPoints(1F);
            for (Vector3 v : positions) {
                if(rand.nextInt(3) == 0) {
                    v.add(ePos);
                    EntityFXFacingParticle p = EffectHelper.genericFlareParticle(v.getX(), v.getY(), v.getZ());
                    p.scale(rand.nextFloat() * 0.2F + 0.2F).enableAlphaFade(EntityComplexFX.AlphaFunction.FADE_OUT);
                    if(rand.nextBoolean()) {
                        p.setColor(Color.WHITE);
                    }
                    Vector3 m = new Vector3();
                    MiscUtils.applyRandomOffset(m, rand, 0.005F);
                    p.motion(m.getX(), m.getY(), m.getZ()).setMaxAge(15 + rand.nextInt(10));
                }
            }
        }
    }

    //0 = none, 1=basically gone
    public float despawnPercentage(float partial) {
        int tick = despawning;
        float p = tick - (1 - partial);
        p /= 10;
        return Math.min(1, Math.max(0, p));
    }

    public boolean isDespawning() {
        return despawning != -1;
    }

    private void setDespawning() {
        if(despawning == -1) {
            despawning = 0;
        }
    }

    private void despawnTick() {
        despawning++;
        if(despawning > 10) {
            setDead();
        }
    }

    @Override
    public boolean isInRangeToRenderDist(double distance) {
        return distance < 512;
    }

    public List<Vector3> buildPoints(float partial) {
        if(getThrower() == null) {
            return Collections.emptyList();
        }
        List<Vector3> list = Lists.newLinkedList();
        Vector3 interpThrower = RenderingUtils.interpolatePosition(getThrower(), partial);
        interpThrower.add(getThrower().width / 2, 0, getThrower().width / 2);
        Vector3 interpHook = RenderingUtils.interpolatePosition(this, partial);
        interpHook.add(width / 2, 0, width / 2);
        Vector3 origin = new Vector3();
        Vector3 to = interpThrower.clone().subtract(interpHook).addY(getThrower().height / 4);
        float lineLength = (float) (to.length() * 5);
        list.add(origin.clone());
        int iter = (int) lineLength;
        for (int xx = 1; xx < iter - 1; xx++) {
            float dist = xx * (lineLength / iter);
            double dx = (interpThrower.getX() - interpHook.getX())                            / iter * xx + MathHelper.sin(dist / 3.0F) * pullFactor;
            double dy = (interpThrower.getY() - interpHook.getY() + getThrower().height / 3F) / iter * xx + MathHelper.sin(dist / 1.0F) * pullFactor;
            double dz = (interpThrower.getZ() - interpHook.getZ())                            / iter * xx + MathHelper.sin(dist / 2.0F) * pullFactor;
            list.add(new Vector3(dx, dy, dz));
        }
        list.add(to.clone());

        return list;
    }

    private void register(EntityLivingBase throwingEntity) {
        int throwerId = throwingEntity.getEntityId();
        if(currentGrappling.containsKey(throwerId) && !added) {
            int currentGrapple = currentGrappling.get(throwerId);
            if(currentGrapple != getEntityId()) {
                Entity prev = world.getEntityByID(currentGrapple);
                if(prev != null) {
                    prev.setDead();
                }
            }
            currentGrappling.put(throwerId, getEntityId());
            added = true;
        }
    }

    @Override
    public void setThrowableHeading(double x, double y, double z, float velocity, float inaccuracy) {
        super.setThrowableHeading(x, y, z, velocity, 0.0F);
    }

    @Override
    public EntityLivingBase getThrower() {
        return this.throwingEntity != null ? this.throwingEntity : super.getThrower();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        return TileEntity.INFINITE_EXTENT_AABB; //Advantage: we can render the grapplinghook line in entity render instead of particle hackery
    }

    @Override
    protected void onImpact(RayTraceResult result) {
        switch (result.typeOfHit) {
            case BLOCK:
                setPulling(true, null);
                break;
            case ENTITY:
                Entity e = result.entityHit;
                if(e == null || (getThrower() != null && e.equals(getThrower()))) {
                    return;
                }
                setPulling(true, (result.entityHit instanceof EntityLivingBase) ? (EntityLivingBase) result.entityHit : null);
                break;
        }
        this.motionX = 0;
        this.motionY = 0;
        this.motionZ = 0;
        this.posX = result.hitVec.x;
        this.posY = result.hitVec.y;
        this.posZ = result.hitVec.z;
    }

}