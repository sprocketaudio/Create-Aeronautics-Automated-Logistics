package net.sprocketgames.create_aeronautics_automated_logistics.cargo;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

final class CargoStorageRootResolver {
    private static final Map<ClassMethodKey, Optional<Method>> METHOD_CACHE = new HashMap<>();

    private CargoStorageRootResolver() {
    }

    static StorageRoot resolve(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return StorageRoot.self(pos);
        }

        StorageRoot createRoot = resolveCreate(blockEntity);
        if (createRoot != null) {
            return createRoot;
        }

        StorageRoot functionalStorageRoot = resolveFunctionalStorage(blockEntity, pos);
        if (functionalStorageRoot != null) {
            return functionalStorageRoot;
        }

        StorageRoot sophisticatedStorageRoot = resolveSophisticatedStorage(blockEntity, pos);
        if (sophisticatedStorageRoot != null) {
            return sophisticatedStorageRoot;
        }

        StorageRoot sophisticatedBackpacksRoot = resolveSophisticatedBackpacks(blockEntity, pos);
        if (sophisticatedBackpacksRoot != null) {
            return sophisticatedBackpacksRoot;
        }

        StorageRoot storageDrawersRoot = resolveStorageDrawers(blockEntity, pos);
        if (storageDrawersRoot != null) {
            return storageDrawersRoot;
        }

        StorageRoot mekanismRoot = resolveMekanism(blockEntity, pos);
        if (mekanismRoot != null) {
            return mekanismRoot;
        }

        StorageRoot vanillaChestRoot = resolveVanillaChest(blockEntity, pos);
        if (vanillaChestRoot != null) {
            return vanillaChestRoot;
        }

        return StorageRoot.self(pos);
    }

    static Object handlerIdentityKey(Object handler) {
        return new IdentityKey(handler);
    }

    private static StorageRoot resolveCreate(BlockEntity blockEntity) {
        String className = blockEntity.getClass().getName();
        if (!className.equals("com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity")
                && !className.equals("com.simibubi.create.content.fluids.tank.FluidTankBlockEntity")) {
            return null;
        }

        BlockPos controllerPos = invokeBlockPos(blockEntity, "getController");
        if (controllerPos == null) {
            return null;
        }
        String kind = className.contains("vault") ? "create_item_vault" : "create_fluid_tank";
        return StorageRoot.of(kind, controllerPos, controllerPos);
    }

    private static StorageRoot resolveFunctionalStorage(BlockEntity blockEntity, BlockPos pos) {
        String className = blockEntity.getClass().getName();
        if (!className.startsWith("com.buuz135.functionalstorage.block.tile.")) {
            return null;
        }

        BlockPos controllerPos = optionalBlockPos(invoke(blockEntity, "getControllerPos"));
        if (controllerPos != null) {
            return StorageRoot.of("functional_storage_controller", controllerPos, controllerPos);
        }

        if (className.contains("StorageControllerTile")) {
            return StorageRoot.of("functional_storage_controller", pos, pos);
        }

        return StorageRoot.self(pos);
    }

    private static StorageRoot resolveSophisticatedStorage(BlockEntity blockEntity, BlockPos pos) {
        String className = blockEntity.getClass().getName();
        if (!className.startsWith("net.p3pp3rf1y.sophisticatedstorage.block.")) {
            return null;
        }

        BlockPos controllerPos = optionalBlockPos(invoke(blockEntity, "getControllerPos"));
        if (controllerPos != null) {
            return StorageRoot.of("sophisticated_storage_controller", controllerPos, controllerPos);
        }

        BlockPos mainPos = invokeBlockPos(blockEntity, "getMainPos");
        if (mainPos != null) {
            return StorageRoot.of("sophisticated_storage_chest", mainPos, mainPos);
        }

        return StorageRoot.self(pos);
    }

    private static StorageRoot resolveSophisticatedBackpacks(BlockEntity blockEntity, BlockPos pos) {
        String className = blockEntity.getClass().getName();
        if (!className.equals("net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlockEntity")) {
            return null;
        }

        BlockPos controllerPos = optionalBlockPos(invoke(blockEntity, "getControllerPos"));
        if (controllerPos != null) {
            return StorageRoot.of("sophisticated_backpacks_controller", controllerPos, controllerPos);
        }

        return StorageRoot.self(pos);
    }

    private static StorageRoot resolveStorageDrawers(BlockEntity blockEntity, BlockPos pos) {
        String className = blockEntity.getClass().getName();
        if (!className.startsWith("com.jaquadro.minecraft.storagedrawers.block.tile.")) {
            return null;
        }

        if (className.endsWith("BlockEntityController")) {
            return StorageRoot.of("storage_drawers_controller", pos, pos);
        }

        if (className.endsWith("BlockEntityControllerIO")) {
            BlockPos controllerPos = invokeBlockPos(blockEntity, "getControllerPos");
            if (controllerPos != null) {
                return StorageRoot.of("storage_drawers_controller", controllerPos, controllerPos);
            }
            return StorageRoot.self(pos);
        }

        BlockEntity controller = invokeBlockEntity(blockEntity, "getController");
        if (controller != null) {
            return StorageRoot.of("storage_drawers_controller", controller.getBlockPos(), controller.getBlockPos());
        }

        return StorageRoot.self(pos);
    }

    private static StorageRoot resolveMekanism(BlockEntity blockEntity, BlockPos pos) {
        String className = blockEntity.getClass().getName();
        if (!className.startsWith("mekanism.")) {
            return null;
        }

        Object multiblockId = invoke(blockEntity, "getMultiblockUUID");
        if (multiblockId instanceof UUID uuid) {
            return StorageRoot.of("mekanism_multiblock", uuid, pos);
        }

        return StorageRoot.self(pos);
    }

    private static StorageRoot resolveVanillaChest(BlockEntity blockEntity, BlockPos pos) {
        if (!(blockEntity instanceof ChestBlockEntity chest)) {
            return null;
        }

        BlockState state = chest.getBlockState();
        if (!(state.getBlock() instanceof ChestBlock)) {
            return null;
        }

        ChestType chestType = state.getValue(ChestBlock.TYPE);
        if (chestType == ChestType.SINGLE) {
            return StorageRoot.self(pos);
        }

        BlockPos otherPos = pos.relative(ChestBlock.getConnectedDirection(state));
        BlockPos accessPos = compare(pos, otherPos) <= 0 ? pos : otherPos;
        return StorageRoot.of("vanilla_double_chest", accessPos, accessPos);
    }

    private static Object invoke(Object target, String methodName) {
        Optional<Method> method = zeroArgMethod(target.getClass(), methodName);
        if (method.isEmpty()) {
            return null;
        }
        try {
            return method.get().invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Optional<Method> zeroArgMethod(Class<?> type, String methodName) {
        ClassMethodKey cacheKey = new ClassMethodKey(type, methodName);
        Optional<Method> cached = METHOD_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Method method = findZeroArgMethod(type, methodName);
        Optional<Method> result = Optional.ofNullable(method);
        METHOD_CACHE.put(cacheKey, result);
        return result;
    }

    private static Method findZeroArgMethod(Class<?> type, String methodName) {
        try {
            Method method = type.getMethod(methodName);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
        }

        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }

        return null;
    }

    private static BlockPos invokeBlockPos(Object target, String methodName) {
        Object result = invoke(target, methodName);
        return result instanceof BlockPos blockPos ? blockPos.immutable() : null;
    }

    private static BlockEntity invokeBlockEntity(Object target, String methodName) {
        Object result = invoke(target, methodName);
        return result instanceof BlockEntity blockEntity ? blockEntity : null;
    }

    private static BlockPos optionalBlockPos(Object value) {
        if (value instanceof Optional<?> optional) {
            Object resolved = optional.orElse(null);
            return resolved instanceof BlockPos blockPos ? blockPos.immutable() : null;
        }
        return value instanceof BlockPos blockPos ? blockPos.immutable() : null;
    }

    private static int compare(BlockPos a, BlockPos b) {
        if (a.getY() != b.getY()) {
            return Integer.compare(a.getY(), b.getY());
        }
        if (a.getZ() != b.getZ()) {
            return Integer.compare(a.getZ(), b.getZ());
        }
        return Integer.compare(a.getX(), b.getX());
    }

    record StorageRoot(String kind, Object discriminator, BlockPos accessPos) {
        static StorageRoot self(BlockPos pos) {
            return of("self", pos, pos);
        }

        static StorageRoot of(String kind, Object discriminator, BlockPos accessPos) {
            return new StorageRoot(kind, discriminator, accessPos.immutable());
        }
    }

    private record IdentityKey(Object value) {
        @Override
        public boolean equals(Object obj) {
            return obj instanceof IdentityKey other && value == other.value;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(value);
        }
    }

    private record ClassMethodKey(Class<?> type, String methodName) {
    }
}
