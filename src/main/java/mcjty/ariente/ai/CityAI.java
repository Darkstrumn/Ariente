package mcjty.ariente.ai;

import mcjty.ariente.blocks.ModBlocks;
import mcjty.ariente.blocks.aicore.AICoreTile;
import mcjty.ariente.blocks.defense.ForceFieldTile;
import mcjty.ariente.blocks.generators.NegariteGeneratorTile;
import mcjty.ariente.blocks.generators.PosiriteGeneratorTile;
import mcjty.ariente.blocks.utility.AlarmTile;
import mcjty.ariente.blocks.utility.AlarmType;
import mcjty.ariente.blocks.utility.StorageTile;
import mcjty.ariente.blocks.utility.wireless.RedstoneChannels;
import mcjty.ariente.blocks.utility.wireless.SignalChannelTileEntity;
import mcjty.ariente.cities.*;
import mcjty.ariente.config.AIConfiguration;
import mcjty.ariente.dimension.ArienteChunkGenerator;
import mcjty.ariente.entities.drone.DroneEntity;
import mcjty.ariente.entities.drone.SentinelDroneEntity;
import mcjty.ariente.entities.levitator.FluxLevitatorEntity;
import mcjty.ariente.entities.soldier.MasterSoldierEntity;
import mcjty.ariente.entities.soldier.SoldierBehaviourType;
import mcjty.ariente.entities.soldier.SoldierEntity;
import mcjty.ariente.items.BlueprintItem;
import mcjty.ariente.items.ModItems;
import mcjty.ariente.items.modules.ArmorUpgradeType;
import mcjty.ariente.items.modules.ModuleSupport;
import mcjty.ariente.power.PowerSenderSupport;
import mcjty.ariente.recipes.ConstructorRecipe;
import mcjty.ariente.recipes.RecipeRegistry;
import mcjty.ariente.security.SecuritySystem;
import mcjty.ariente.varia.ChunkCoord;
import mcjty.ariente.varia.WeightedRandom;
import mcjty.hologui.api.IHoloGuiEntity;
import mcjty.lib.blocks.BaseBlock;
import mcjty.lib.varia.RedstoneMode;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.*;


public class CityAI {

    private final ChunkCoord center;
    private boolean initialized = false;

    private CityAISettings settings = null;

    private boolean foundEquipment = false;
    private Set<BlockPos> aiCores = new HashSet<>();
    private Set<BlockPos> forceFields = new HashSet<>();
    private Set<BlockPos> alarms = new HashSet<>();
    private Set<BlockPos> negariteGenerators = new HashSet<>();
    private Set<BlockPos> posiriteGenerators = new HashSet<>();
    private Map<BlockPos, EnumFacing> guardPositions = new HashMap<>();
    private Map<BlockPos, EnumFacing> soldierPositions = new HashMap<>();
    private Map<BlockPos, EnumFacing> masterSoldierPositions = new HashMap<>();

    private boolean foundArmy = false;
    private int[] sentinels = null;
    private int sentinelMovementTicks = 6;
    private int sentinelAngleOffset = 0;

    private int[] drones = new int[40];
    private int droneTicker = 0;

    private int levitator = -1;
    private int levitatorTicker = 20;
    private BlockPos levitatorPrevPos = null;

    private String keyId;
    private String storageKeyId;
    private String forcefieldId;

    private int[] soldiers = new int[60];
    private int soldierTicker = 0;

    private int onAlert = 0;
    private boolean highAlert = false;
    private Map<UUID, BlockPos> watchingPlayers = new HashMap<>();  // Players we are watching as well as their last known position

    private static Random random = new Random();

    public CityAI(ChunkCoord center) {
        this.center = center;
    }

    public ChunkCoord getCenter() {
        return center;
    }

    private boolean setup(World world) {
        if (!initialized) {
            initialized = true;
            initialize(world);
            findArmy(world);
            return false;
        } else {
            findEquipment(world, false);
            findArmy(world);
            return true;
        }
    }

    // Return true if we potentially have to save the city system state
    public boolean tick(AICoreTile tile) {
        // We use the given AICoreTile parameter to make sure only one tick per city happens
        if (setup(tile.getWorld())) {
            // If there are no more ai cores the city AI is dead
            if (aiCores.isEmpty()) {
                return false;
            }

            AICoreTile core = findFirstValidAICore(tile.getWorld());
            if (core == null) {
                // All cores are no longer valid and have been removed
                return false;
            }
            // Only tick for the first valid aicore
            if (!tile.getPos().equals(core.getPos())) {
                return false;
            }

            handleAI(tile.getWorld());
            return true;
        }
        return true;
    }

    public boolean isDead(World world) {
        return !hasValidCoreExcept(world, null);
    }

    // Check if there is still a valid AI core except for the input parameter
    public boolean hasValidCoreExcept(World world, @Nullable BlockPos exclude) {
        if (!initialized) {
            return true;        // If not initialized we assume this city is alive
        }
        for (BlockPos pos : aiCores) {
            if (!pos.equals(exclude)) {
                TileEntity te = world.getTileEntity(pos);
                if (te instanceof AICoreTile) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    private AICoreTile findFirstValidAICore(World world) {
        for (BlockPos pos : aiCores) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof AICoreTile) {
                return (AICoreTile) te;
            }
        }
        return null;
    }

    private void handleAI(World world) {
        handlePower(world);
        handleSentinels(world);
        handleAlert(world);
        handleDrones(world);
        handleSoldiers(world);
        handleFluxLevitators(world);
    }

    private void handleFluxLevitators(World world) {
        // First check if there is a levitator and if it is near its destination
        if (levitator != -1) {
            Entity entity = world.getEntityByID(levitator);
            if (!(entity instanceof FluxLevitatorEntity)) {
                levitator = -1;
            } else {
                FluxLevitatorEntity levitatorEntity = (FluxLevitatorEntity) entity;
                BlockPos desiredDestination = levitatorEntity.getDesiredDestination();
                if (desiredDestination != null) {
                    double distanceSq = levitatorEntity.getPosition().distanceSq(desiredDestination);
                    if (distanceSq < 5*5) {
                        // Arrived
                        dismountAndKill(levitatorEntity);
                    } else {
                        // Check if we actually moved since last time. If not we let the soldier get out and remove the flux levitator
                        if (levitatorPrevPos != null) {
                            distanceSq = levitatorEntity.getPosition().distanceSq(levitatorPrevPos);
                            if (distanceSq <= 0.1) {
                                dismountAndKill(levitatorEntity);
                            }
                        }
                    }
                    levitatorPrevPos = levitatorEntity.getPosition();
                } else {
                    dismountAndKill(levitatorEntity);
                }
            }
            return;
        }

        levitatorTicker--;
        if (levitatorTicker <= 0) {
            levitatorTicker = 80;
            if (levitator != -1) {
                Entity entity = world.getEntityByID(levitator);
                if (entity != null) {
                    for (Entity passenger : entity.getPassengers()) {
                        if (!(passenger instanceof IHoloGuiEntity) && !(passenger instanceof EntityPlayer)) {
                            passenger.setDead();
                        }
                    }

                    entity.setDead();
                }
                levitator = -1;
            } else {
                LevitatorPath path = findValidBeam(world);
                if (path != null) {
                    List<SoldierEntity> entities = world.getEntitiesWithinAABB(SoldierEntity.class, new AxisAlignedBB(path.end).grow(15));
                    if (entities.size() > 2) {
                        // Too many already
                        return;
                    }


                    BlockPos pos = path.start;
                    IBlockState state = world.getBlockState(pos);
                    BlockRailBase.EnumRailDirection dir = FluxLevitatorEntity.getBeamDirection(state);
                    double d0 = 0.0D;

                    if (dir.isAscending()) {
                        d0 = 0.5D;
                    }

                    FluxLevitatorEntity entity = new FluxLevitatorEntity(world, pos.getX() + 0.5D, pos.getY() + 0.0625D + d0, pos.getZ() + 0.5D);
                    if (path.direction == EnumFacing.SOUTH || path.direction == EnumFacing.EAST) {
                        entity.changeSpeed(-50);
                    } else {
                        entity.changeSpeed(50);
                    }
                    entity.setDesiredDestination(path.end);
                    world.spawnEntity(entity);
                    levitator = entity.getEntityId();

                    SoldierEntity soldier = createSoldier(world, pos, path.direction, SoldierBehaviourType.SOLDIER_FIGHTER, false);
                    soldier.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(ModItems.energySabre));
                    world.spawnEntity(soldier);
                    soldier.startRiding(entity);
                }
            }
        }
    }

    private void dismountAndKill(FluxLevitatorEntity levitatorEntity) {
        for (Entity passenger : levitatorEntity.getPassengers()) {
            if (!(passenger instanceof IHoloGuiEntity) && !(passenger instanceof EntityPlayer)) {
                passenger.dismountRidingEntity();
            }
        }
        levitatorEntity.setDead();
        levitator = -1;
    }

    private BlockPos isValidBeam(World world, ChunkCoord c, EnumFacing direction, int minOffset, int maxOffset) {
        for (int i = minOffset ; i <= maxOffset ; i++) {
            BlockPos pos = new BlockPos(c.getChunkX() * 16 + 8 + direction.getDirectionVec().getX() * i, 32, (c.getChunkZ() * 16) + 8 + direction.getDirectionVec().getZ() * i);
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() == ModBlocks.fluxBeamBlock) {
                return pos;
            }
        }
        return null;
    }

    private static class LevitatorPath {
        private final EnumFacing direction;
        private final BlockPos start;
        private final BlockPos end;

        public LevitatorPath(EnumFacing direction, BlockPos start, BlockPos end) {
            this.direction = direction;
            this.start = start;
            this.end = end;
        }
    }

    private boolean isValidPath(World world, BlockPos start, BlockPos end, EnumFacing facing) {
        BlockPos p = start;
        while (!end.equals(p)) {
            IBlockState state = world.getBlockState(p);
            if (state.getBlock() != ModBlocks.fluxBeamBlock) {
                return false;
            }
            p = p.offset(facing.getOpposite());
        }
        List<FluxLevitatorEntity> entities = world.getEntitiesWithinAABB(FluxLevitatorEntity.class, new AxisAlignedBB(start).union(new AxisAlignedBB(end)));
        return entities.isEmpty();
    }

    @Nullable
    private LevitatorPath findValidBeam(World world) {
        CityAISystem system = CityAISystem.getCityAISystem(world);
        List<LevitatorPath> positions = new ArrayList<>();
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            ChunkCoord otherCoord = new ChunkCoord(center.getChunkX() + facing.getDirectionVec().getX() * 16,
                    center.getChunkZ() + facing.getDirectionVec().getZ() * 16);
            CityAI otherCity = system.getCityAI(otherCoord);
            if (otherCity != null && !otherCity.isDead(world)) {
                BlockPos end = isValidBeam(world, center, facing, 1, 40);
                if (end != null) {
                    BlockPos start = isValidBeam(world, center, facing, 100, 120);
                    if (start != null) {
                        if (isValidPath(world, start, end, facing)) {
                            positions.add(new LevitatorPath(facing, start, end));
                        }
                    }
                }
            }
        }
        if (positions.isEmpty()) {
            return null;
        }
        if (positions.size() == 1) {
            return positions.get(0);
        }
        return positions.get(random.nextInt(positions.size()));
    }

    private void handleAlert(World world) {
        // Handle alert mode
        if (onAlert > 0) {
            onAlert--;
        }

        if (onAlert > 0) {
            // Turn on forcefields if present
            for (BlockPos pos : forceFields) {
                TileEntity te = world.getTileEntity(pos);
                if (te instanceof ForceFieldTile) {
                    ForceFieldTile forcefield = (ForceFieldTile) te;
                    if (forcefield.getRSMode() != RedstoneMode.REDSTONE_IGNORED) {
                        forcefield.setRSMode(RedstoneMode.REDSTONE_IGNORED);
                    }
                }
            }
        } else {
            setAlarmType(world, AlarmType.SAFE);
            highAlert = false;
            watchingPlayers.clear();
            // Turn off forcefields if present
            for (BlockPos pos : forceFields) {
                TileEntity te = world.getTileEntity(pos);
                if (te instanceof ForceFieldTile) {
                    ForceFieldTile forcefield = (ForceFieldTile) te;
                    if (forcefield.getRSMode() != RedstoneMode.REDSTONE_ONREQUIRED) {
                        forcefield.setRSMode(RedstoneMode.REDSTONE_ONREQUIRED);
                    }
                }
            }
        }
    }

    private int countEntities(World world, int[] entityIds) {
        int cnt = 0;
        for (int id : entityIds) {
            if (id != 0 && world.getEntityByID(id) != null) {
                cnt++;
            }
        }

        return cnt;
    }

    @Nullable
    private BlockPos findRandomPlayer(World world) {
        List<BlockPos> players = new ArrayList<>();
        for (Map.Entry<UUID, BlockPos> entry : watchingPlayers.entrySet()) {
            UUID uuid = entry.getKey();
            EntityPlayerMP player = world.getMinecraftServer().getPlayerList().getPlayerByUUID(uuid);
            if (player != null && player.getEntityWorld().provider.getDimension() == world.provider.getDimension()) {
                BlockPos pos = entry.getValue();    // Use the last known position
                double sq = pos.distanceSq(new BlockPos(center.getChunkX() * 16 + 8, 50, center.getChunkZ() * 16 + 8));
                if (sq < 80 * 80) {
                    players.add(pos);
                }
            }
        }
        if (players.isEmpty()) {
            return null;
        }
        return players.get(random.nextInt(players.size()));
    }

    private void handleDrones(World world) {
        if (onAlert > 0) {
            droneTicker--;
            if (droneTicker > 0) {
                return;
            }
            droneTicker = 10;

            City city = CityTools.getCity(center);
            CityPlan plan = city.getPlan();
            ArienteChunkGenerator generator = (ArienteChunkGenerator)(((WorldServer) world).getChunkProvider().chunkGenerator);
            int droneHeight = plan.getDroneHeightOffset() + CityTools.getLowestHeight(city, generator, center.getChunkX(), center.getChunkZ());

            int desiredMinimumCount = 0;
            int newWaveMaximum = 0;
            if (watchingPlayers.size() > 2) {
                desiredMinimumCount = plan.getDronesMinimumN();
                newWaveMaximum = plan.getDronesWaveMaxN();
            } else if (watchingPlayers.size() > 1) {
                desiredMinimumCount = plan.getDronesMinimum2();
                newWaveMaximum = plan.getDronesWaveMax2();
            } else {
                desiredMinimumCount = plan.getDronesMinimum1();
                newWaveMaximum = plan.getDronesWaveMax1();
            }

            int cnt = countEntities(world, drones);
            while (cnt < desiredMinimumCount) {
                spawnDrone(world, droneHeight);
                cnt++;
            }

            if (cnt < newWaveMaximum && random.nextFloat() < 0.1f) {
                // Randomly spawn a new wave of drones
                System.out.println("WAVE");
                while (cnt < newWaveMaximum) {
                    spawnDrone(world, droneHeight);
                    cnt++;
                }
            }
        }
    }

    private void handleSoldiers(World world) {
        if (onAlert > 0) {
            soldierTicker--;
            if (soldierTicker > 0) {
                return;
            }
            soldierTicker = 10;

            City city = CityTools.getCity(center);
            CityPlan plan = city.getPlan();

            int desiredMinimumCount = 0;
            int newWaveMaximum = 0;
            if (watchingPlayers.size() > 2) {
                desiredMinimumCount = plan.getSoldiersMinimumN();
                newWaveMaximum = plan.getSoldiersWaveMaxN();
            } else if (watchingPlayers.size() > 1) {
                desiredMinimumCount = plan.getSoldiersMinimum2();
                newWaveMaximum = plan.getSoldiersWaveMax2();
            } else {
                desiredMinimumCount = plan.getSoldiersMinimum1();
                newWaveMaximum = plan.getSoldiersWaveMax1();
            }
            if (highAlert) {
                desiredMinimumCount *= 2;
                if (desiredMinimumCount > soldiers.length) {
                    desiredMinimumCount = soldiers.length;
                }
                newWaveMaximum *= 2;
                if (newWaveMaximum > soldiers.length) {
                    newWaveMaximum = soldiers.length;
                }
            }

            int cnt = countEntities(world, soldiers);
            while (cnt < desiredMinimumCount) {
                spawnSoldier(world);
                cnt++;
            }

            if (cnt < newWaveMaximum && random.nextFloat() < 0.2f) {
                // Randomly spawn a new wave of drones
                System.out.println("SOLDIER WAVE");
                while (cnt < newWaveMaximum) {
                    spawnSoldier(world);
                    cnt++;
                }
            }
        }
    }

    private void spawnSoldier(World world) {
        if (soldierPositions.isEmpty()) {
            return;
        }

        // Too few soldiers. Spawn a new one
        int foundId = -1;
        for (int i = 0 ; i < soldiers.length ; i++) {
            if (soldiers[i] == 0 || world.getEntityByID(soldiers[i]) == null) {
                foundId = i;
                break;
            }
        }
        if (foundId != -1) {
            City city = CityTools.getCity(center);
            CityPlan plan = city.getPlan();

            BlockPos pos;
            // Avoid too close to player if possible
            int avoidNearby = 3;
            do {
                pos = new ArrayList<>(soldierPositions.keySet()).get(random.nextInt(soldierPositions.size()));
                EntityPlayer closestPlayer = world.getClosestPlayer(pos.getX(), pos.getY(), pos.getZ(), 10, false);
                if (closestPlayer == null) {
                    avoidNearby = 0;
                } else {
                    avoidNearby--;
                }
            } while (avoidNearby > 0);

            System.out.println("CityAI.spawnSoldier at " + pos);

            EnumFacing facing = soldierPositions.get(pos);
            SoldierEntity entity = createSoldier(world, pos, facing, SoldierBehaviourType.SOLDIER_FIGHTER,
                    random.nextDouble() < plan.getMasterChance());
            entity.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(ModItems.energySabre));    // @todo need a lasergun

            if (random.nextFloat() < plan.getPowerArmorChance()) {
                entity.setItemStackToSlot(EntityEquipmentSlot.HEAD, createNiceHelmet());
                entity.setItemStackToSlot(EntityEquipmentSlot.FEET, createNiceBoots());
                entity.setItemStackToSlot(EntityEquipmentSlot.CHEST, createNiceChestplate(plan));
                entity.setItemStackToSlot(EntityEquipmentSlot.LEGS, createNiceLegs());
            }
            soldiers[foundId] = entity.getEntityId();
        }
    }

    private ItemStack createNiceHelmet() {
        ItemStack helmet = new ItemStack(ModItems.powerSuitHelmet);
        NBTTagCompound compound = new NBTTagCompound();
        compound.setBoolean(ArmorUpgradeType.ARMOR.getModuleKey(), true);
        compound.setBoolean(ArmorUpgradeType.ARMOR.getWorkingKey(), compound.getBoolean(ArmorUpgradeType.ARMOR.getModuleKey()));
        helmet.setTagCompound(compound);
        return helmet;
    }


    private ItemStack createNiceBoots() {
        ItemStack helmet = new ItemStack(ModItems.powerSuitBoots);
        NBTTagCompound compound = new NBTTagCompound();
        compound.setBoolean(ArmorUpgradeType.ARMOR.getModuleKey(), true);
        compound.setBoolean(ArmorUpgradeType.ARMOR.getWorkingKey(), compound.getBoolean(ArmorUpgradeType.ARMOR.getModuleKey()));
        helmet.setTagCompound(compound);
        return helmet;
    }


    private ItemStack createNiceChestplate(CityPlan plan) {
        ItemStack helmet = new ItemStack(ModItems.powerSuitChest);
        NBTTagCompound compound = new NBTTagCompound();
        compound.setBoolean(ArmorUpgradeType.ARMOR.getModuleKey(), true);
        compound.setBoolean(ArmorUpgradeType.ARMOR.getWorkingKey(), compound.getBoolean(ArmorUpgradeType.ARMOR.getModuleKey()));
        if (random.nextFloat() < plan.getForcefieldChance()) {
            compound.setBoolean(ArmorUpgradeType.ENERGY.getModuleKey(), true);
            compound.setBoolean(ArmorUpgradeType.FORCEFIELD.getModuleKey(), true);
            compound.setBoolean(ArmorUpgradeType.FORCEFIELD.getWorkingKey(), compound.getBoolean(ArmorUpgradeType.FORCEFIELD.getModuleKey()));
        }
        helmet.setTagCompound(compound);
        return helmet;
    }


    private ItemStack createNiceLegs() {
        ItemStack helmet = new ItemStack(ModItems.powerSuitLegs);
        NBTTagCompound compound = new NBTTagCompound();
        compound.setBoolean(ArmorUpgradeType.ARMOR.getModuleKey(), true);
        compound.setBoolean(ArmorUpgradeType.ARMOR.getWorkingKey(), compound.getBoolean(ArmorUpgradeType.ARMOR.getModuleKey()));
        helmet.setTagCompound(compound);
        return helmet;
    }


    private void spawnDrone(World world, int height) {
        // Too few drones. Spawn a new one
        int foundId = -1;
        for (int i = 0 ; i < drones.length ; i++) {
            if (drones[i] == 0 || world.getEntityByID(drones[i]) == null) {
                foundId = i;
                break;
            }
        }
        if (foundId != -1) {
            DroneEntity entity = new DroneEntity(world, center);
            int cx = center.getChunkX() * 16 + 8;
            int cy = height;
            int cz = center.getChunkZ() * 16 + 8;
            entity.setPosition(cx, cy, cz);
            world.spawnEntity(entity);
            drones[foundId] = entity.getEntityId();
        }
    }

    private void handleSentinels(World world) {
        // Sentinel movement
        sentinelMovementTicks--;
        if (sentinelMovementTicks <= 0) {
            sentinelMovementTicks = 6;
            sentinelAngleOffset++;
            if (sentinelAngleOffset >= 12) {
                sentinelAngleOffset = 0;
            }
        }

        // Small chance to revive sentinels if they are missing. Only revive if all are missing
        if (random.nextFloat() < .1f) {
            if (countEntities(world, sentinels) == 0) {
                City city = CityTools.getCity(center);
                CityPlan plan = city.getPlan();
                ArienteChunkGenerator generator = (ArienteChunkGenerator)(((WorldServer) world).getChunkProvider().chunkGenerator);
                int droneHeight = plan.getDroneHeightOffset() + CityTools.getLowestHeight(city, generator, center.getChunkX(), center.getChunkZ());
                for (int i = 0; i < sentinels.length; i++) {
//                    System.out.println("revive: i = " + i);
                    createSentinel(world, i, droneHeight);
                }
            }
        }
    }

    private void handlePower(World world) {
        // Handle power
        for (BlockPos pos : negariteGenerators) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof NegariteGeneratorTile) {
                NegariteGeneratorTile generator = (NegariteGeneratorTile) te;
                if (generator.getStackInSlot(NegariteGeneratorTile.SLOT_NEGARITE_INPUT).isEmpty()) {
                    generator.setInventorySlotContents(NegariteGeneratorTile.SLOT_NEGARITE_INPUT, new ItemStack(ModItems.negariteDust, 1));
                    generator.markDirtyClient();
                }
            }
        }
        for (BlockPos pos : posiriteGenerators) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof PosiriteGeneratorTile) {
                PosiriteGeneratorTile generator = (PosiriteGeneratorTile) te;
                if (generator.getStackInSlot(PosiriteGeneratorTile.SLOT_POSIRITE_INPUT).isEmpty()) {
                    generator.setInventorySlotContents(PosiriteGeneratorTile.SLOT_POSIRITE_INPUT, new ItemStack(ModItems.posiriteDust, 1));
                    generator.markDirtyClient();
                }
            }
        }
    }

    public BlockPos requestNewSoldierPosition(World world, EntityLivingBase currentTarget) {
        // Sometimes we let a solider pick a different location independent of target
        if (random.nextFloat() > .6) {
            return null;
        }

        BlockPos target;
        if (currentTarget != null) {
            target = currentTarget.getPosition();
        } else {
            target = findRandomPlayer(world);
        }
        if (target != null) {
            float angle = random.nextFloat() * 360.0f;
            float distance = 4;
            int cx = (int) (target.getX()+.5 + Math.cos(angle) * distance);
            int cz = (int) (target.getZ()+.5 + Math.sin(angle) * distance);
            return new BlockPos(cx, target.getY(), cz);
        }
        return null;
    }

    public BlockPos requestNewDronePosition(World world, EntityLivingBase currentTarget) {
        BlockPos target;
        if (currentTarget != null) {
            target = currentTarget.getPosition();
        } else {
            target = findRandomPlayer(world);
        }
        if (target != null) {
            float angle = random.nextFloat() * 360.0f;
            float distance = 15;
            int cx = (int) (target.getX()+.5 + Math.cos(angle) * distance);
            int cz = (int) (target.getZ()+.5 + Math.sin(angle) * distance);
            return new BlockPos(cx, target.getY()+3, cz);
        }
        return null;
    }

    public BlockPos requestNewSentinelPosition(World world, int sentinelId) {
        if (sentinels == null) {
            return null;
        }
        if (aiCores.isEmpty()) {
            return null;
        }

        City city = CityTools.getCity(center);
        CityPlan plan = city.getPlan();
        ArienteChunkGenerator generator = (ArienteChunkGenerator)(((WorldServer) world).getChunkProvider().chunkGenerator);
        int droneHeight = plan.getSentinelRelHeight() + CityTools.getLowestHeight(city, generator, center.getChunkX(), center.getChunkZ());

        int angleI = (sentinelAngleOffset + sentinelId * 12 / sentinels.length) % 12;
        int cx = center.getChunkX() * 16 + 8;
        int cy = droneHeight;
        int cz = center.getChunkZ() * 16 + 8;

        float angle = angleI * 360.0f / 12;
        float distance = plan.getSentinelDistance();
        cx = (int) (cx + Math.cos(angle) * distance);
        cz = (int) (cz + Math.sin(angle) * distance);
        return new BlockPos(cx, cy, cz);
    }

    public void pacify(World world) {
        setAlarmType(world, AlarmType.SAFE);
        onAlert = 0;
        highAlert = false;
    }

    public void playerSpotted(EntityPlayer player) {
        // The scramble module helps protect against player allertness
        ItemStack helmet = player.getItemStackFromSlot(EntityEquipmentSlot.HEAD);
        if (helmet.getItem() == ModItems.powerSuitHelmet) {
            if (ModuleSupport.hasWorkingUpgrade(helmet, ArmorUpgradeType.SCRAMBLE)) {
                return;
            }
        }

        alertCity(player);
    }

    public void alertCity(EntityPlayer player) {
        if (findFirstValidAICore(player.getEntityWorld()) == null) {
            // City is dead
            return;
        }

        if (onAlert <= 0) {
            // Set alarm type in case it is not already set
            setAlarmType(player.world, AlarmType.ALERT);
        }
        onAlert = AIConfiguration.ALERT_TIME.get();
        watchingPlayers.put(player.getUniqueID(), player.getPosition());    // Register the last known position
    }

    public void highAlertMode(EntityPlayer player) {
        alertCity(player);
        highAlert = true;
    }

    private void findArmy(World world) {
        if (foundArmy) {
            return;
        }

        City city = CityTools.getCity(center);
        assert city != null;
        CityPlan plan = city.getPlan();
        List<String> pattern = plan.getPlan();
        int dimX = pattern.get(0).length() * 16 * 2;
        int dimZ = pattern.size() * 16 * 2;

        BlockPos ctr = new BlockPos(this.center.getChunkX() * 16 + 8, 50, this.center.getChunkZ() * 16 + 8);
        List<EntityLivingBase> entities = world.getEntitiesWithinAABB(EntityLivingBase.class, new AxisAlignedBB(ctr).grow(dimX, 200, dimZ));

        if (sentinels == null) {
            sentinels = new int[settings.getNumSentinels()];
        }
        int cntSentinel = 0;
        int cntDrone = 0;
        int cntSoldier = 0;

        for (EntityLivingBase entity : entities) {
            if (entity instanceof SentinelDroneEntity) {
                if (cntSentinel < sentinels.length) {
                    sentinels[cntSentinel++] = entity.getEntityId();
                }
            } else if (entity instanceof SoldierEntity) {
                if (cntSoldier < soldiers.length) {
                    soldiers[cntSoldier++] = entity.getEntityId();
                }
            } else if (entity instanceof DroneEntity) {
                if (cntDrone < drones.length) {
                    drones[cntDrone++] = entity.getEntityId();
                }
            }
        }

        // @todo remove later
        System.out.println("cntSoldier = " + cntSoldier);
        System.out.println("cntSentinel = " + cntSentinel);
        System.out.println("cntDrone = " + cntDrone);

        foundArmy = true;
    }

    private void findEquipment(World world, boolean firstTime) {
        if (foundEquipment) {
            return;
        }

        if (firstTime) {
            keyId = SecuritySystem.getSecuritySystem(world).generateKeyId();
            storageKeyId = SecuritySystem.getSecuritySystem(world).generateKeyId();
            forcefieldId = SecuritySystem.getSecuritySystem(world).generateKeyId();
        }

        City city = CityTools.getCity(center);
        assert city != null;
        CityPlan plan = city.getPlan();
        List<String> pattern = plan.getPlan();
        int dimX = pattern.get(0).length();
        int dimZ = pattern.size();
        int cx = center.getChunkX();
        int cz = center.getChunkZ();

        Map<Integer, Integer> desiredToReal = new HashMap<>();

        for (int dx = cx - dimX / 2 - 1; dx <= cx + dimX / 2 + 1; dx++) {
            for (int dz = cz - dimZ / 2 - 1; dz <= cz + dimZ / 2 + 1; dz++) {
                int starty;
                if (plan.isUnderground()) {
                    starty = 1;
                } else {
                    // @todo is this a safe minimum height to assume?
                    starty = 30;
                }
                for (int x = dx * 16; x < dx * 16 + 16; x++) {
                    for (int z = dz * 16; z < dz * 16 + 16; z++) {
                        for (int y = starty; y < starty + 100; y++) {
                            BlockPos p = new BlockPos(x, y, z);
                            IBlockState state = world.getBlockState(p);
                            Block block = state.getBlock();
                            if (block == ModBlocks.guardDummy) {
                                guardPositions.put(p, state.getValue(BaseBlock.FACING_HORIZ));
                                world.setBlockToAir(p);
                            } else if (block == ModBlocks.soldierDummy) {
                                soldierPositions.put(p, state.getValue(BaseBlock.FACING_HORIZ));
                                world.setBlockToAir(p);
                            } else if (block == ModBlocks.masterSoldierDummy) {
                                masterSoldierPositions.put(p, state.getValue(BaseBlock.FACING_HORIZ));
                                world.setBlockToAir(p);
                            } else {
                                TileEntity te = world.getTileEntity(p);
                                if (te instanceof ICityEquipment) {
                                    ((ICityEquipment) te).setup(this, world, firstTime);
                                }

                                if (firstTime && te instanceof SignalChannelTileEntity) {
                                    int desired = ((SignalChannelTileEntity) te).getDesiredChannel();
                                    if (!desiredToReal.containsKey(desired)) {
                                        // New channel is needed
                                        RedstoneChannels redstoneChannels = RedstoneChannels.getChannels(world);
                                        int newChannel = redstoneChannels.newChannel();
                                        redstoneChannels.save();
                                        desiredToReal.put(desired, newChannel);
                                        System.out.println("Mapping channel " + desired + " to " + newChannel);
                                    }
                                    desired = desiredToReal.get(desired);
                                    ((SignalChannelTileEntity) te).setChannel(desired);
                                }

                                if (te instanceof AICoreTile) {
                                    aiCores.add(p);
                                } else if (te instanceof ForceFieldTile) {
                                    // We already have this as equipment but we need it separate
                                    ((ForceFieldTile)te).setCityCenter(center);
                                    forceFields.add(p);
                                } else if (te instanceof AlarmTile) {
                                    alarms.add(p);
                                } else if (te instanceof NegariteGeneratorTile) {
                                    negariteGenerators.add(p);
                                } else if (te instanceof PosiriteGeneratorTile) {
                                    posiriteGenerators.add(p);
                                }
                            }
                        }
                    }
                }
            }
        }
        foundEquipment = true;
    }

    public String getKeyId() {
        return keyId;
    }

    public String getStorageKeyId() {
        return storageKeyId;
    }

    public String getForcefieldId() {
        return forcefieldId;
    }

    public void fillLoot(CityPlan plan, StorageTile te) {
        WeightedRandom<Loot> randomLoot = plan.getRandomLoot();
        for (int i = 0 ; i < 4 ; i++) {
            if (random.nextFloat() > .3f) {
                Loot l = randomLoot.getRandom();
                int amount;
                if (l.getMaxAmount() <= 1) {
                    amount = 1;
                } else {
                    amount = 1 + random.nextInt(l.getMaxAmount() - 1);
                }
                ResourceLocation id = l.getId();
                if (id == null) {
                    // Random blueprint
                    ConstructorRecipe recipe = RecipeRegistry.getRandomRecipes().getRandom();
                    ItemStack blueprint = BlueprintItem.makeBluePrint(recipe.getDestination());
                    te.initTotalStack(i, blueprint);
                } else {
                    Item item = ForgeRegistries.ITEMS.getValue(id);
                    if (item != null) {
                        if (l.isBlueprint()) {
                            ItemStack blueprint = BlueprintItem.makeBluePrint(new ItemStack(item, 1, l.getMeta()));
                            te.initTotalStack(i, blueprint);
                        } else {
                            te.initTotalStack(i, new ItemStack(item, amount, l.getMeta()));
                        }
                    }
                }
            }
        }
        te.markDirtyClient();
    }

    private static int getMinMax(Random rnd, int min, int max) {
        if (min >= max) {
            return min;
        }
        return min + rnd.nextInt(max-min);
    }

    private void createSettings(World world) {
        long seed = DimensionManager.getWorld(0).getSeed();
        Random rnd = new Random(seed + center.getChunkX() * 567000003533L + center.getChunkZ() * 234516783139L);
        rnd.nextFloat();
        rnd.nextFloat();
        City city = CityTools.getCity(center);
        CityPlan plan = city.getPlan();
        settings = new CityAISettings();
        settings.setNumSentinels(getMinMax(rnd, plan.getMinSentinels(), plan.getMaxSentinels()));
    }

    private void initialize(World world) {
        createSettings(world);
        findEquipment(world, true);
        initCityEquipment(world);
        initSentinels(world);
        initGuards(world);
        initMasterSoldiers(world);
    }

    private SoldierEntity createSoldier(World world, BlockPos p, EnumFacing facing, SoldierBehaviourType behaviourType,
                                        boolean master) {
        SoldierEntity entity;
        if (master) {
            entity = new MasterSoldierEntity(world, center, behaviourType);
        } else {
            entity = new SoldierEntity(world, center, behaviourType);
        }
        entity.setPosition(p.getX()+.5, p.getY(), p.getZ()+.5);
        float yaw = 0;
        switch (facing) {
            case NORTH:
                yaw = 0;
                break;
            case SOUTH:
                yaw = 90;
                break;
            case WEST:
                yaw = 180;
                break;
            case EAST:
                yaw = 270;
                break;
            default:
                break;
        }
        entity.setLocationAndAngles(entity.posX, entity.posY, entity.posZ, yaw, 0);
        world.spawnEntity(entity);
        return entity;
    }

    private void initGuards(World world) {
        for (Map.Entry<BlockPos, EnumFacing> entry : guardPositions.entrySet()) {
            BlockPos pos = entry.getKey();
            EnumFacing facing = entry.getValue();
            SoldierEntity soldier = createSoldier(world, pos, facing, SoldierBehaviourType.SOLDIER_GUARD, false);
            soldier.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(ModItems.energySabre));
        }
    }

    private void initMasterSoldiers(World world) {
        for (Map.Entry<BlockPos, EnumFacing> entry : masterSoldierPositions.entrySet()) {
            BlockPos pos = entry.getKey();
            EnumFacing facing = entry.getValue();
            SoldierEntity soldier = createSoldier(world, pos, facing, SoldierBehaviourType.SOLDIER_FIGHTER, true);
            soldier.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(ModItems.energySabre));
        }
    }

    private void initCityEquipment(World world) {

        setAlarmType(world, AlarmType.SAFE);

        for (BlockPos p : negariteGenerators) {
            TileEntity te = world.getTileEntity(p);
            if (te instanceof NegariteGeneratorTile) {
                NegariteGeneratorTile generator = (NegariteGeneratorTile) te;
                PowerSenderSupport.fixNetworks(world, p);
                generator.setRSMode(RedstoneMode.REDSTONE_IGNORED);
            }
        }
        for (BlockPos p : posiriteGenerators) {
            TileEntity te = world.getTileEntity(p);
            if (te instanceof PosiriteGeneratorTile) {
                posiriteGenerators.add(p);
                PosiriteGeneratorTile generator = (PosiriteGeneratorTile) te;
                PowerSenderSupport.fixNetworks(world, p);
                generator.setRSMode(RedstoneMode.REDSTONE_IGNORED);
            }
        }
    }

    public void setAlarmType(World world, AlarmType type) {
        for (BlockPos p : alarms) {
            TileEntity te = world.getTileEntity(p);
            if (te instanceof AlarmTile) {
                ((AlarmTile) te).setAlarmType(type);
            }
        }
    }

    private void initSentinels(World world) {
        City city = CityTools.getCity(center);
        CityPlan plan = city.getPlan();
        ArienteChunkGenerator generator = (ArienteChunkGenerator)(((WorldServer) world).getChunkProvider().chunkGenerator);
        int droneHeight = plan.getDroneHeightOffset() + CityTools.getLowestHeight(city, generator, center.getChunkX(), center.getChunkZ());

        int numSentinels = settings.getNumSentinels();
        sentinels = new int[numSentinels];
        for (int i = 0 ; i < numSentinels ; i++) {
            System.out.println("initSentinels: i = " + i);
            createSentinel(world, i, droneHeight);
        }
    }

    private void createSentinel(World world, int i, int height) {
        SentinelDroneEntity entity = new SentinelDroneEntity(world, i, center);
        int cx = center.getChunkX() * 16 + 8;
        int cy = height;
        int cz = center.getChunkZ() * 16 + 8;
        entity.setPosition(cx, cy, cz);
        world.spawnEntity(entity);
        sentinels[i] = entity.getEntityId();
    }

    public void enableEditMode(World world) {
        for (Map.Entry<BlockPos, EnumFacing> entry : guardPositions.entrySet()) {
            world.setBlockState(entry.getKey(), ModBlocks.guardDummy.getDefaultState().withProperty(BaseBlock.FACING_HORIZ, entry.getValue()));
        }
        for (Map.Entry<BlockPos, EnumFacing> entry : soldierPositions.entrySet()) {
            world.setBlockState(entry.getKey(), ModBlocks.soldierDummy.getDefaultState().withProperty(BaseBlock.FACING_HORIZ, entry.getValue()));
        }
        for (Map.Entry<BlockPos, EnumFacing> entry : masterSoldierPositions.entrySet()) {
            world.setBlockState(entry.getKey(), ModBlocks.masterSoldierDummy.getDefaultState().withProperty(BaseBlock.FACING_HORIZ, entry.getValue()));
        }
    }

    public void readFromNBT(NBTTagCompound nbt) {
        initialized = nbt.getBoolean("initialized");
        settings = null;
        if (initialized) {
            if (nbt.hasKey("settings")) {
                settings = new CityAISettings();
                settings.readFromNBT(nbt.getCompoundTag("settings"));
            }
            keyId = nbt.getString("keyId");
            storageKeyId = nbt.getString("storageKeyId");
            forcefieldId = nbt.getString("forcefieldId");
            watchingPlayers.clear();
            if (nbt.hasKey("players")) {
                NBTTagList list = nbt.getTagList("players", Constants.NBT.TAG_COMPOUND);
                for (int i = 0; i < list.tagCount(); i++) {
                    NBTTagCompound tc = list.getCompoundTagAt(i);
                    UUID uuid = tc.getUniqueId("id");
                    BlockPos pos = NBTUtil.getPosFromTag(tc);
                    watchingPlayers.put(uuid, pos);
                }

            }
            sentinelMovementTicks = nbt.getInteger("sentinelMovementTicks");
            sentinelAngleOffset = nbt.getInteger("sentinelAngleOffset");
            onAlert = nbt.getInteger("onAlert");
            highAlert = nbt.getBoolean("highAlert");
            droneTicker = nbt.getInteger("droneTicker");
            readMapFromNBT(nbt.getTagList("guards", Constants.NBT.TAG_COMPOUND), guardPositions);
            readMapFromNBT(nbt.getTagList("soldierPositions", Constants.NBT.TAG_COMPOUND), soldierPositions);
            readMapFromNBT(nbt.getTagList("masterSoldierPositions", Constants.NBT.TAG_COMPOUND), masterSoldierPositions);
            levitator = nbt.getInteger("levitator");
            levitatorTicker = nbt.getInteger("levitatorTicker");
        }
    }

    public void writeToNBT(NBTTagCompound compound) {
        compound.setBoolean("initialized", initialized);
        if (initialized) {
            if (settings != null) {
                NBTTagCompound tc = new NBTTagCompound();
                settings.writeToNBT(tc);
                compound.setTag("settings", tc);
            }

            compound.setString("keyId", keyId);
            compound.setString("storageKeyId", storageKeyId);
            compound.setString("forcefieldId", forcefieldId);
            if (!watchingPlayers.isEmpty()) {
                NBTTagList list = new NBTTagList();
                for (Map.Entry<UUID, BlockPos> entry : watchingPlayers.entrySet()) {
                    NBTTagCompound tc = NBTUtil.createPosTag(entry.getValue());
                    tc.setUniqueId("id", entry.getKey());
                    list.appendTag(tc);
                }
                compound.setTag("players", list);
            }
            compound.setInteger("sentinelMovementTicks", sentinelMovementTicks);
            compound.setInteger("sentinelAngleOffset", sentinelAngleOffset);
            compound.setInteger("onAlert", onAlert);
            compound.setBoolean("highAlert", highAlert);
            compound.setInteger("droneTicker", droneTicker);
            compound.setTag("guards", writeMapToNBT(guardPositions));
            compound.setTag("soldierPositions", writeMapToNBT(soldierPositions));
            compound.setTag("masterSoldierPositions", writeMapToNBT(masterSoldierPositions));
            compound.setInteger("levitator", levitator);
            compound.setInteger("levitatorTicker", levitatorTicker);
        }
    }

    private NBTTagList writeSetToNBT(Set<BlockPos> set) {
        NBTTagList list = new NBTTagList();
        for (BlockPos pos : set) {
            list.appendTag(NBTUtil.createPosTag(pos));
        }
        return list;
    }

    private void readSetFromNBT(NBTTagList list, Set<BlockPos> set) {
        set.clear();
        for (int i = 0 ; i < list.tagCount() ; i++) {
            BlockPos pos = NBTUtil.getPosFromTag(list.getCompoundTagAt(i));
            set.add(pos);
        }
    }

    private NBTTagList writeMapToNBT(Map<BlockPos, EnumFacing> map) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<BlockPos, EnumFacing> entry : map.entrySet()) {
            NBTTagCompound tc = NBTUtil.createPosTag(entry.getKey());
            tc.setInteger("facing", entry.getValue().ordinal());
            list.appendTag(tc);
        }
        return list;
    }

    private void readMapFromNBT(NBTTagList list, Map<BlockPos, EnumFacing> map) {
        map.clear();
        for (int i = 0 ; i < list.tagCount() ; i++) {
            NBTTagCompound tc = list.getCompoundTagAt(i);
            BlockPos pos = NBTUtil.getPosFromTag(tc);
            EnumFacing facing = EnumFacing.VALUES[tc.getInteger("facing")];
            map.put(pos, facing);
        }
    }
}
