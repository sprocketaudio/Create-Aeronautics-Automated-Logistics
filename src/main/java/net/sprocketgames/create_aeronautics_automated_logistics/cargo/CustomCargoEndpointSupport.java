package net.sprocketgames.create_aeronautics_automated_logistics.cargo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import org.jetbrains.annotations.Nullable;

final class CustomCargoEndpointSupport {
    private static final List<ItemStack> SAMPLE_ITEM_STACKS = List.of(new ItemStack(Items.STONE));
    private static final List<FluidStack> SAMPLE_FLUID_STACKS = List.of(new FluidStack(Fluids.WATER, 1));

    private static final String RS2_STORAGE_MONITOR =
            "com.refinedmods.refinedstorage.common.storagemonitor.StorageMonitorBlockEntity";
    private static final String RS2_INTERFACE =
            "com.refinedmods.refinedstorage.common.iface.InterfaceBlockEntity";
    private static final String RS2_CONTROLLER =
            "com.refinedmods.refinedstorage.common.controller.ControllerBlockEntity";
    private static final String RS2_EXPORTER =
            "com.refinedmods.refinedstorage.common.exporter.AbstractExporterBlockEntity";
    private static final String RS2_STORAGE_COMPONENT =
            "com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent";
    private static final String RS2_ITEM_RESOURCE =
            "com.refinedmods.refinedstorage.common.support.resource.ItemResource";
    private static final String RS2_FLUID_RESOURCE =
            "com.refinedmods.refinedstorage.common.support.resource.FluidResource";
    private static final String RS2_ACTION =
            "com.refinedmods.refinedstorage.api.core.Action";
    private static final String RS2_ACTOR =
            "com.refinedmods.refinedstorage.api.storage.Actor";
    private static final String RS2_FILTER_MODE =
            "com.refinedmods.refinedstorage.api.resource.filter.FilterMode";

    private static final String AE2_ME_CHEST =
            "appeng.blockentity.storage.MEChestBlockEntity";
    private static final String AE2_SKY_STONE_TANK =
            "appeng.blockentity.storage.SkyStoneTankBlockEntity";
    private static final String AE2_CONTROLLER =
            "appeng.blockentity.networking.ControllerBlockEntity";
    private static final String AE2_ITEM_KEY =
            "appeng.api.stacks.AEItemKey";
    private static final String AE2_FLUID_KEY =
            "appeng.api.stacks.AEFluidKey";
    private static final String AE2_ACTIONABLE =
            "appeng.api.config.Actionable";
    private static final String AE2_BASE_ACTION_SOURCE =
            "appeng.me.helpers.BaseActionSource";
    private static final String AE2_CABLE_BUS =
            "appeng.blockentity.networking.CableBusBlockEntity";
    private static final String AE2_EXPORT_BUS_PART =
            "appeng.parts.automation.ExportBusPart";
    private static final String AE2_STORAGE_MONITOR_PART =
            "appeng.parts.reporting.StorageMonitorPart";
    private static final String MEK_ACTION =
            "mekanism.api.Action";
    private static final String MEK_HASHED_ITEM =
            "mekanism.common.lib.inventory.HashedItem";
    private static final String MEK_QIO_DRIVE_ARRAY =
            "mekanism.common.tile.qio.TileEntityQIODriveArray";
    private static final String MEK_QIO_EXPORTER =
            "mekanism.common.tile.qio.TileEntityQIOExporter";
    private static final String MEK_QIO_ITEMSTACK_FILTER =
            "mekanism.common.content.qio.filter.QIOItemStackFilter";
    private static final String MEK_QIO_TAG_FILTER =
            "mekanism.common.content.qio.filter.QIOTagFilter";
    private static final String MEK_QIO_MODID_FILTER =
            "mekanism.common.content.qio.filter.QIOModIDFilter";

    private static final Map<ClassMethodKey, Optional<Method>> METHOD_CACHE = new HashMap<>();
    private static final Map<String, Optional<Class<?>>> CLASS_CACHE = new HashMap<>();
    private static final Map<String, Optional<Constructor<?>>> CTOR_CACHE = new HashMap<>();

    private CustomCargoEndpointSupport() {
    }

    static EndpointKinds detect(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return EndpointKinds.none();
        }

        String className = blockEntity.getClass().getName();
        if (MEK_QIO_DRIVE_ARRAY.equals(className) || MEK_QIO_EXPORTER.equals(className)) {
            return EndpointKinds.none();
        }
        if (isRs2Endpoint(className)
                || isType(blockEntity, RS2_EXPORTER)
                || AE2_ME_CHEST.equals(className)
                || AE2_CONTROLLER.equals(className)
                || hasAe2CargoParts(blockEntity)) {
            return new EndpointKinds(true, true);
        }
        return EndpointKinds.none();
    }

    static @Nullable EndpointCapture capture(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return null;
        }

        String className = blockEntity.getClass().getName();
        if (isRs2Endpoint(className)) {
            return captureRs2(blockEntity);
        }
        if (isType(blockEntity, RS2_EXPORTER)) {
            return captureRs2Exporter(blockEntity);
        }
        if (AE2_ME_CHEST.equals(className) || AE2_CONTROLLER.equals(className)) {
            return captureAe2(blockEntity);
        }
        if (MEK_QIO_DRIVE_ARRAY.equals(className)) {
            return captureQioDriveArray(blockEntity);
        }
        if (MEK_QIO_EXPORTER.equals(className)) {
            return captureQioExporter(blockEntity);
        }
        if (AE2_CABLE_BUS.equals(className)) {
            return captureAe2CableBus(blockEntity);
        }
        return null;
    }

    static boolean canAcceptAnyItems(Level level, List<LinkedCargoEntry> targetEntries, LinkedCargoSnapshot offered) {
        for (LinkedCargoEntry entry : targetEntries) {
            EndpointCapture capture = capture(level, entry.pos());
            if (capture != null && capture.acceptsAnyItems(offered.itemStacks())) {
                return true;
            }
        }
        return false;
    }

    static boolean canAcceptAnyFluids(Level level, List<LinkedCargoEntry> targetEntries, LinkedCargoSnapshot offered) {
        for (LinkedCargoEntry entry : targetEntries) {
            EndpointCapture capture = capture(level, entry.pos());
            if (capture != null && capture.acceptsAnyFluids(offered.fluidStacks())) {
                return true;
            }
        }
        return false;
    }

    static Object rootIdentityKey(Object handler) {
        if (handler == null) {
            return new IdentityKey(null);
        }

        if (handler.getClass().getName().equals("com.tom.storagemod.inventory.PlatformItemHandler")) {
            Object root = invoke(handler, "getRootHandler", new HashSet<>());
            if (root != null) {
                return new IdentityKey(root);
            }
        }

        return new IdentityKey(handler);
    }

    private static @Nullable EndpointCapture captureRs2(BlockEntity blockEntity) {
        Object network = invoke(blockEntity, "getNetworkForItem");
        if (network == null) {
            return null;
        }

        Class<?> storageComponentClass = loadClass(RS2_STORAGE_COMPONENT).orElse(null);
        if (storageComponentClass == null) {
            return null;
        }
        Object storageComponent = invoke(network, "getComponent", storageComponentClass);
        if (storageComponent == null) {
            return null;
        }

        Object simulateAction = enumConstant(RS2_ACTION, "SIMULATE");
        Object emptyActor = staticFieldValue(RS2_ACTOR, "EMPTY");
        if (simulateAction == null || emptyActor == null) {
            return null;
        }

        return captureRs2StorageComponent(
                storageComponent,
                rootIdentityKey(storageComponent),
                null,
                null,
                offeredItems -> rs2CanAcceptAny(storageComponent, null, null, offeredItems, simulateAction, emptyActor, false),
                offeredFluids -> rs2CanAcceptAny(storageComponent, null, null, offeredFluids, simulateAction, emptyActor, false),
                offeredItems -> {
                    for (ItemStack stack : offeredItems) {
                        if (stack.isEmpty()) {
                            continue;
                        }
                        Object resource = staticInvoke(RS2_ITEM_RESOURCE, "ofItemStack", stack);
                        Object inserted = resource == null ? null : invoke(
                                storageComponent,
                                "insert",
                                resource,
                                (long) stack.getCount(),
                                simulateAction,
                                emptyActor
                        );
                        if (inserted instanceof Long amountInserted && amountInserted > 0L) {
                            return true;
                        }
                    }
                    return false;
                },
                offeredFluids -> {
                    for (FluidStack stack : offeredFluids) {
                        if (stack.isEmpty()) {
                            continue;
                        }
                        Object resource = instantiate(RS2_FLUID_RESOURCE, stack.getFluid(), stack.getComponentsPatch());
                        Object inserted = resource == null ? null : invoke(
                                storageComponent,
                                "insert",
                                resource,
                                (long) stack.getAmount(),
                                simulateAction,
                                emptyActor
                        );
                        if (inserted instanceof Long amountInserted && amountInserted > 0L) {
                            return true;
                        }
                    }
                    return false;
                }
        );
    }

    private static @Nullable EndpointCapture captureRs2StorageComponent(
            Object storageComponent,
            Object rootKey,
            @Nullable FilteredResources filteredResources,
            @Nullable Object filterMode,
            AcceptanceProbe<ItemStack> itemSupportProbe,
            AcceptanceProbe<FluidStack> fluidSupportProbe,
            AcceptanceProbe<ItemStack> itemAcceptanceProbe,
            AcceptanceProbe<FluidStack> fluidAcceptanceProbe
    ) {
        List<ItemStack> itemStacks = new ArrayList<>();
        List<FluidStack> fluidStacks = new ArrayList<>();
        Class<?> actorClass = loadClass(RS2_ACTOR).orElse(null);
        Object resourcesValue = actorClass == null ? null : invoke(storageComponent, "getResources", actorClass);
        if (resourcesValue instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                Object amount = invoke(entry, "resourceAmount");
                Object resource = amount == null ? null : invoke(amount, "resource");
                Object amountValue = amount == null ? null : invoke(amount, "amount");
                if (!(amountValue instanceof Long resourceAmount) || resourceAmount <= 0L || resource == null) {
                    continue;
                }

                String className = resource.getClass().getName();
                if (className.equals(RS2_ITEM_RESOURCE)) {
                    if (!rs2IncludeInCapture(filteredResources, filterMode, resource, true)) {
                        continue;
                    }
                    Object stackValue = invoke(resource, "toItemStack", resourceAmount);
                    if (stackValue instanceof ItemStack stack && !stack.isEmpty()) {
                        itemStacks.add(stack.copy());
                    }
                } else if (className.equals(RS2_FLUID_RESOURCE)) {
                    if (!rs2IncludeInCapture(filteredResources, filterMode, resource, false)) {
                        continue;
                    }
                    Object fluid = invoke(resource, "fluid");
                    if (fluid instanceof net.minecraft.world.level.material.Fluid fluidType) {
                        fluidStacks.add(new FluidStack(
                                fluidType,
                                (int) Math.min(Integer.MAX_VALUE, resourceAmount)
                        ));
                    }
                }
            }
        }

        boolean filteredItemStorage = filteredResources != null && !filteredResources.itemResources().isEmpty();
        boolean filteredFluidStorage = filteredResources != null && !filteredResources.fluidResources().isEmpty();
        boolean hasItemStorage = !itemStacks.isEmpty()
                || filteredItemStorage
                || (itemSupportProbe != null && itemSupportProbe.acceptsAny(SAMPLE_ITEM_STACKS));
        boolean hasFluidStorage = !fluidStacks.isEmpty()
                || filteredFluidStorage
                || (fluidSupportProbe != null && fluidSupportProbe.acceptsAny(SAMPLE_FLUID_STACKS));

        if (!hasItemStorage && !hasFluidStorage) {
            return null;
        }

        return new EndpointCapture(
                hasItemStorage,
                hasFluidStorage,
                List.copyOf(itemStacks),
                List.copyOf(fluidStacks),
                Integer.MAX_VALUE,
                Long.MAX_VALUE,
                rootKey,
                rootKey,
                itemAcceptanceProbe,
                fluidAcceptanceProbe
        );
    }

    private static @Nullable EndpointCapture captureRs2Exporter(BlockEntity blockEntity) {
        Object storageComponent = resolveRs2StorageComponent(blockEntity);
        if (storageComponent == null) {
            CreateAeronauticsAutomatedLogistics.debugCargo(
                    "RS2 exporter capture failed at {}: no storage component",
                    blockEntity.getBlockPos()
            );
            return null;
        }

        FilteredResources filtered = resolveRs2FilteredResources(blockEntity);
        Object filterMode = invoke(blockEntity, "getFilterMode");
        Object simulateAction = enumConstant(RS2_ACTION, "SIMULATE");
        Object emptyActor = staticFieldValue(RS2_ACTOR, "EMPTY");
        if (simulateAction == null || emptyActor == null) {
            CreateAeronauticsAutomatedLogistics.debugCargo(
                    "RS2 exporter capture failed at {}: simulateAction={} emptyActor={}",
                    blockEntity.getBlockPos(),
                    simulateAction != null,
                    emptyActor != null
            );
            return null;
        }
        return captureRs2StorageComponent(
                storageComponent,
                new RootDescriptor("rs2_exporter", rootIdentityKey(storageComponent)),
                filtered.hasAny() ? filtered : null,
                filterMode,
                offeredItems -> rs2CanAcceptAny(storageComponent, filtered, filterMode, offeredItems, simulateAction, emptyActor, true),
                offeredFluids -> rs2CanAcceptAny(storageComponent, filtered, filterMode, offeredFluids, simulateAction, emptyActor, false),
                offeredItems -> false,
                offeredFluids -> false
        );
    }

    private static @Nullable EndpointCapture captureAe2(BlockEntity blockEntity) {
        Object storage = resolveAe2Storage(blockEntity);
        if (storage == null) {
            return null;
        }

        List<ItemStack> itemStacks = new ArrayList<>();
        List<FluidStack> fluidStacks = new ArrayList<>();
        Object availableStacks = invoke(storage, "getAvailableStacks");
        if (availableStacks instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                Object key = invoke(entry, "getKey");
                Object amountValue = invoke(entry, "getLongValue");
                if (!(amountValue instanceof Long amount) || amount <= 0L || key == null) {
                    continue;
                }

                if (key.getClass().getName().equals(AE2_ITEM_KEY)) {
                    Object stackValue = invoke(key, "toStack", (int) Math.min(Integer.MAX_VALUE, amount.longValue()));
                    if (stackValue instanceof ItemStack stack && !stack.isEmpty()) {
                        itemStacks.add(stack.copy());
                    }
                } else if (key.getClass().getName().equals(AE2_FLUID_KEY)) {
                    Object stackValue = invoke(key, "toStack", (int) Math.min(Integer.MAX_VALUE, amount.longValue()));
                    if (stackValue instanceof FluidStack stack && !stack.isEmpty()) {
                        fluidStacks.add(stack.copy());
                    }
                }
            }
        }

        Object simulateMode = enumConstant(AE2_ACTIONABLE, "SIMULATE");
        Object source = instantiate(AE2_BASE_ACTION_SOURCE);
        if (simulateMode == null || source == null) {
            return null;
        }

        boolean hasItemStorage = !itemStacks.isEmpty() || ae2CanAcceptAnyItems(storage, simulateMode, source, SAMPLE_ITEM_STACKS);
        boolean hasFluidStorage = !fluidStacks.isEmpty() || ae2CanAcceptAnyFluids(storage, simulateMode, source, SAMPLE_FLUID_STACKS);
        if (!hasItemStorage && !hasFluidStorage) {
            return null;
        }

        Object rootKey = rootIdentityKey(storage);
        return new EndpointCapture(
                hasItemStorage,
                hasFluidStorage,
                List.copyOf(itemStacks),
                List.copyOf(fluidStacks),
                Integer.MAX_VALUE,
                Long.MAX_VALUE,
                rootKey,
                rootKey,
                offeredItems -> {
                    for (ItemStack stack : offeredItems) {
                        Object key = staticInvoke(AE2_ITEM_KEY, "of", stack);
                        Object inserted = key == null ? null : invoke(
                                storage,
                                "insert",
                                key,
                                (long) stack.getCount(),
                                simulateMode,
                                source
                        );
                        if (inserted instanceof Long amountInserted && amountInserted > 0L) {
                            return true;
                        }
                    }
                    return false;
                },
                offeredFluids -> {
                    for (FluidStack stack : offeredFluids) {
                        Object key = staticInvoke(AE2_FLUID_KEY, "of", stack);
                        Object inserted = key == null ? null : invoke(
                                storage,
                                "insert",
                                key,
                                (long) stack.getAmount(),
                                simulateMode,
                                source
                        );
                        if (inserted instanceof Long amountInserted && amountInserted > 0L) {
                            return true;
                        }
                    }
                    return false;
                }
        );
    }

    private static @Nullable EndpointCapture captureQioDriveArray(BlockEntity blockEntity) {
        Object frequency = invoke(blockEntity, "getQIOFrequency");
        if (frequency == null) {
            return null;
        }

        long totalCount = numberAsLong(invoke(frequency, "getTotalItemCount"));
        long totalCapacity = numberAsLong(invoke(frequency, "getTotalItemCountCapacity"));
        List<ItemStack> itemStacks = totalCount <= 0L ? List.of() : qioAllItems(frequency);
        boolean hasItemStorage = !itemStacks.isEmpty() || qioCanAcceptAnyItems(frequency, SAMPLE_ITEM_STACKS);
        if (!hasItemStorage) {
            return null;
        }

        Object rootKey = new RootDescriptor("mek_qio_frequency", rootIdentityKey(frequency));
        return new EndpointCapture(
                true,
                false,
                List.copyOf(itemStacks),
                List.of(),
                (int) Math.min(Integer.MAX_VALUE, Math.max(0L, totalCapacity - totalCount)),
                0L,
                rootKey,
                rootKey,
                offeredItems -> qioCanAcceptAnyItems(frequency, offeredItems),
                offeredFluids -> false
        );
    }

    private static @Nullable EndpointCapture captureQioExporter(BlockEntity blockEntity) {
        Object frequency = invoke(blockEntity, "getQIOFrequency");
        if (frequency == null) {
            return null;
        }

        Object filterManager = invoke(blockEntity, "getFilterManager");
        Object enabledFilters = filterManager == null ? null : invoke(filterManager, "getEnabledFilters");
        boolean exportWithoutFilter = Boolean.TRUE.equals(invoke(blockEntity, "getExportWithoutFilter"));
        boolean hasFilters = enabledFilters instanceof Iterable<?> iterable && iterable.iterator().hasNext();
        long totalCount = numberAsLong(invoke(frequency, "getTotalItemCount"));
        List<ItemStack> itemStacks = totalCount <= 0L
                ? List.of()
                : qioExporterItems(frequency, enabledFilters, exportWithoutFilter);
        boolean hasItemStorage = !itemStacks.isEmpty()
                || hasFilters
                || (exportWithoutFilter && qioCanAcceptAnyItems(frequency, SAMPLE_ITEM_STACKS));
        if (!hasItemStorage) {
            return null;
        }

        long totalCapacity = numberAsLong(invoke(frequency, "getTotalItemCountCapacity"));
        Object rootKey = new RootDescriptor("mek_qio_exporter", rootIdentityKey(frequency));
        return new EndpointCapture(
                true,
                false,
                List.copyOf(itemStacks),
                List.of(),
                (int) Math.min(Integer.MAX_VALUE, Math.max(0L, totalCapacity - totalCount)),
                0L,
                rootKey,
                rootKey,
                offeredItems -> false,
                offeredFluids -> false
        );
    }

    private static boolean ae2CanAcceptAnyItems(Object storage, Object simulateMode, Object source, List<ItemStack> offeredItems) {
        for (ItemStack stack : offeredItems) {
            Object key = staticInvoke(AE2_ITEM_KEY, "of", stack);
            Object inserted = key == null ? null : invoke(storage, "insert", key, (long) stack.getCount(), simulateMode, source);
            if (inserted instanceof Long amountInserted && amountInserted > 0L) {
                return true;
            }
        }
        return false;
    }

    private static boolean ae2CanAcceptAnyFluids(Object storage, Object simulateMode, Object source, List<FluidStack> offeredFluids) {
        for (FluidStack stack : offeredFluids) {
            Object key = staticInvoke(AE2_FLUID_KEY, "of", stack);
            Object inserted = key == null ? null : invoke(storage, "insert", key, (long) stack.getAmount(), simulateMode, source);
            if (inserted instanceof Long amountInserted && amountInserted > 0L) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable EndpointCapture captureAe2CableBus(BlockEntity blockEntity) {
        List<Object> exportParts = new ArrayList<>();
        for (Object part : ae2Parts(blockEntity)) {
            String className = part.getClass().getName();
            if (AE2_EXPORT_BUS_PART.equals(className)) {
                exportParts.add(part);
            }
        }
        if (exportParts.isEmpty()) {
            return null;
        }

        List<ItemStack> itemStacks = new ArrayList<>();
        List<FluidStack> fluidStacks = new ArrayList<>();
        List<FilteredAe2Key> exportItemFilters = new ArrayList<>();
        List<FilteredAe2Key> exportFluidFilters = new ArrayList<>();

        for (Object part : exportParts) {
            captureAe2ExportPart(part, itemStacks, fluidStacks, exportItemFilters, exportFluidFilters);
        }

        if (itemStacks.isEmpty()
                && fluidStacks.isEmpty()
                && exportItemFilters.isEmpty()
                && exportFluidFilters.isEmpty()) {
            return null;
        }

        Object source = instantiate(AE2_BASE_ACTION_SOURCE);
        Object simulateMode = enumConstant(AE2_ACTIONABLE, "SIMULATE");
        Object rootKey = new RootDescriptor("ae2_cable_bus", rootIdentityKey(blockEntity));
        return new EndpointCapture(
                !itemStacks.isEmpty() || !exportItemFilters.isEmpty(),
                !fluidStacks.isEmpty() || !exportFluidFilters.isEmpty(),
                List.copyOf(itemStacks),
                List.copyOf(fluidStacks),
                Integer.MAX_VALUE,
                Long.MAX_VALUE,
                rootKey,
                rootKey,
                offeredItems -> false,
                offeredFluids -> false
        );
    }

    private static @Nullable Object resolveAe2Storage(BlockEntity blockEntity) {
        if (AE2_ME_CHEST.equals(blockEntity.getClass().getName())) {
            return invoke(blockEntity, "getInventory");
        }
        if (AE2_CONTROLLER.equals(blockEntity.getClass().getName())) {
            Object grid = invoke(invoke(blockEntity, "getMainNode"), "getGrid");
            Object storageService = grid == null ? null : invoke(grid, "getStorageService");
            return storageService == null ? null : invoke(storageService, "getInventory");
        }
        return null;
    }

    private static boolean isRs2Endpoint(String className) {
        return className.equals(RS2_CONTROLLER);
    }

    private static boolean hasAe2CargoParts(BlockEntity blockEntity) {
        if (!AE2_CABLE_BUS.equals(blockEntity.getClass().getName())) {
            return false;
        }
        for (Object part : ae2Parts(blockEntity)) {
            String className = part.getClass().getName();
            if (AE2_EXPORT_BUS_PART.equals(className)
                    || AE2_STORAGE_MONITOR_PART.equals(className)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isType(Object target, String className) {
        for (Class<?> current = target.getClass(); current != null; current = current.getSuperclass()) {
            if (className.equals(current.getName())) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable Object staticFieldValue(String className, String fieldName) {
        try {
            Class<?> type = loadClass(className).orElse(null);
            if (type == null) {
                return null;
            }
            return type.getField(fieldName).get(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static @Nullable Object enumConstant(String className, String constantName) {
        Class<?> type = loadClass(className).orElse(null);
        if (type == null || !type.isEnum()) {
            return null;
        }
        for (Object constant : type.getEnumConstants()) {
            if (constant instanceof Enum<?> enumConstant && enumConstant.name().equals(constantName)) {
                return constant;
            }
        }
        return null;
    }

    private static @Nullable Object staticInvoke(String className, String methodName, Object... args) {
        Class<?> type = loadClass(className).orElse(null);
        if (type == null) {
            return null;
        }
        Optional<Method> method = findMethod(type, methodName, args);
        if (method.isEmpty()) {
            return null;
        }
        try {
            return method.get().invoke(null, args);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static @Nullable Object instantiate(String className, Object... args) {
        Constructor<?> constructor = findConstructor(className, args).orElse(null);
        if (constructor == null) {
            return null;
        }
        try {
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Optional<Constructor<?>> findConstructor(String className, Object... args) {
        String cacheKey = className + "#" + args.length;
        Optional<Constructor<?>> cached = CTOR_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Class<?> type = loadClass(className).orElse(null);
        if (type == null) {
            Optional<Constructor<?>> result = Optional.empty();
            CTOR_CACHE.put(cacheKey, result);
            return result;
        }

        Constructor<?> constructor = null;
        for (Constructor<?> candidate : type.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = candidate.getParameterTypes();
            if (parameterTypes.length != args.length) {
                continue;
            }
            boolean matches = true;
            for (int i = 0; i < parameterTypes.length; i++) {
                if (!isAssignable(parameterTypes[i], args[i])) {
                    matches = false;
                    break;
                }
            }
            if (!matches) {
                continue;
            }
            candidate.setAccessible(true);
            constructor = candidate;
            break;
        }

        Optional<Constructor<?>> result = Optional.ofNullable(constructor);
        CTOR_CACHE.put(cacheKey, result);
        return result;
    }

    private static Optional<Class<?>> loadClass(String className) {
        Optional<Class<?>> cached = CLASS_CACHE.get(className);
        if (cached != null) {
            return cached;
        }

        try {
            Optional<Class<?>> result = Optional.of(Class.forName(className));
            CLASS_CACHE.put(className, result);
            return result;
        } catch (ClassNotFoundException ignored) {
            Optional<Class<?>> result = Optional.empty();
            CLASS_CACHE.put(className, result);
            return result;
        }
    }

    private static @Nullable Object invoke(Object target, String methodName, Object... args) {
        if (target == null) {
            return null;
        }
        Optional<Method> method = findMethod(target.getClass(), methodName, args);
        if (method.isEmpty()) {
            return null;
        }
        try {
            return method.get().invoke(target, args);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Optional<Method> findMethod(Class<?> type, String methodName, Object... args) {
        Class<?>[] argumentTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            argumentTypes[i] = args[i] == null ? Object.class : args[i].getClass();
        }

        ClassMethodKey cacheKey = new ClassMethodKey(type, methodName, List.of(argumentTypes));
        Optional<Method> cached = METHOD_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Method method = null;
        for (Method candidate : type.getMethods()) {
            if (candidate.getName().equals(methodName) && parametersMatch(candidate.getParameterTypes(), args)) {
                candidate.setAccessible(true);
                method = candidate;
                break;
            }
        }
        for (Class<?> current = type; current != null && method == null; current = current.getSuperclass()) {
            for (Method candidate : current.getDeclaredMethods()) {
                if (!candidate.getName().equals(methodName) || !parametersMatch(candidate.getParameterTypes(), args)) {
                    continue;
                }
                candidate.setAccessible(true);
                method = candidate;
                break;
            }
        }

        Optional<Method> result = Optional.ofNullable(method);
        METHOD_CACHE.put(cacheKey, result);
        return result;
    }

    private static boolean parametersMatch(Class<?>[] parameterTypes, Object[] args) {
        if (parameterTypes.length != args.length) {
            return false;
        }
        for (int i = 0; i < parameterTypes.length; i++) {
            if (!isAssignable(parameterTypes[i], args[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAssignable(Class<?> parameterType, Object value) {
        if (value == null) {
            return !parameterType.isPrimitive();
        }
        Class<?> wrappedParameterType = wrap(parameterType);
        return wrappedParameterType.isInstance(value)
                || (wrappedParameterType == Class.class && value instanceof Class<?>);
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private static @Nullable Object resolveRs2StorageComponent(Object blockEntity) {
        Object network = invoke(blockEntity, "getNetworkForItem");
        if (network == null) {
            return null;
        }
        Class<?> storageComponentClass = loadClass(RS2_STORAGE_COMPONENT).orElse(null);
        if (storageComponentClass == null) {
            return null;
        }
        return invoke(network, "getComponent", storageComponentClass);
    }

    private static @Nullable Object resolveRs2FilterData(Object blockEntity) {
        Object menuData = invoke(blockEntity, "getMenuData");
        if (menuData == null) {
            return null;
        }
        if (menuData.getClass().getName().equals("com.refinedmods.refinedstorage.common.exporter.ExporterData")) {
            return invoke(menuData, "resourceContainerData");
        }
        return menuData;
    }

    private static FilteredResources resolveRs2FilteredResources(Object blockEntity) {
        FilteredResources direct = readRs2FilterResourcesFromField(blockEntity);
        if (direct.hasAny()) {
            return direct;
        }
        return readRs2FilteredResources(resolveRs2FilterData(blockEntity));
    }

    private static FilteredResources readRs2FilterResourcesFromField(Object blockEntity) {
        Object filter = readField(blockEntity, "filter");
        if (filter == null) {
            return FilteredResources.empty();
        }
        Object filterContainer = invoke(filter, "getFilterContainer");
        if (filterContainer == null) {
            return FilteredResources.empty();
        }
        Object resources = invoke(filterContainer, "getResources");
        if (!(resources instanceof Iterable<?> iterable)) {
            return FilteredResources.empty();
        }

        List<Object> itemResources = new ArrayList<>();
        List<Object> fluidResources = new ArrayList<>();
        for (Object resource : iterable) {
            if (resource == null) {
                continue;
            }
            String className = resource.getClass().getName();
            if (RS2_ITEM_RESOURCE.equals(className)) {
                itemResources.add(resource);
            } else if (RS2_FLUID_RESOURCE.equals(className)) {
                fluidResources.add(resource);
            }
        }
        return new FilteredResources(List.copyOf(itemResources), List.copyOf(fluidResources));
    }

    private static FilteredResources readRs2FilteredResources(@Nullable Object resourceContainerData) {
        if (resourceContainerData == null) {
            return FilteredResources.empty();
        }
        Object resources = invoke(resourceContainerData, "resources");
        if (!(resources instanceof Iterable<?> iterable)) {
            return FilteredResources.empty();
        }

        List<Object> itemResources = new ArrayList<>();
        List<Object> fluidResources = new ArrayList<>();
        for (Object entry : iterable) {
            Object resourceAmount = entry instanceof Optional<?> optional ? optional.orElse(null) : entry;
            Object resource = resourceAmount == null ? null : invoke(resourceAmount, "resource");
            if (resource == null) {
                continue;
            }
            String className = resource.getClass().getName();
            if (RS2_ITEM_RESOURCE.equals(className)) {
                itemResources.add(resource);
            } else if (RS2_FLUID_RESOURCE.equals(className)) {
                fluidResources.add(resource);
            }
        }
        return new FilteredResources(List.copyOf(itemResources), List.copyOf(fluidResources));
    }

    private static @Nullable Object readField(Object target, String fieldName) {
        for (Class<?> current = target.getClass(); current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                // Continue searching up the hierarchy.
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }

    private static long rs2ResourceAmount(Object storageComponent, Object resource, Object simulateAction, Object emptyActor) {
        Object amount = invoke(storageComponent, "extract", resource, Long.MAX_VALUE, simulateAction, emptyActor);
        return amount instanceof Long value ? Math.max(0L, value) : 0L;
    }

    private static ItemStack rs2ItemStack(Object resource, long amount) {
        Object stack = invoke(resource, "toItemStack", amount);
        return stack instanceof ItemStack itemStack ? itemStack.copy() : ItemStack.EMPTY;
    }

    private static FluidStack rs2FluidStack(Object resource, long amount) {
        Object fluid = invoke(resource, "fluid");
        if (fluid instanceof net.minecraft.world.level.material.Fluid fluidType) {
            return new FluidStack(fluidType, (int) Math.min(Integer.MAX_VALUE, amount));
        }
        return FluidStack.EMPTY;
    }

    private static boolean rs2CanAcceptAny(
            Object storageComponent,
            @Nullable FilteredResources filtered,
            @Nullable Object filterMode,
            List<?> offeredStacks,
            Object simulateAction,
            Object emptyActor,
            boolean items
    ) {
        for (Object offered : offeredStacks) {
            if (items && !(offered instanceof ItemStack offeredItem) || !items && !(offered instanceof FluidStack offeredFluid)) {
                continue;
            }
            Object resource = items
                    ? staticInvoke(RS2_ITEM_RESOURCE, "ofItemStack", offered)
                    : instantiate(RS2_FLUID_RESOURCE, ((FluidStack) offered).getFluid(), ((FluidStack) offered).getComponentsPatch());
            if (resource == null || !rs2MatchesFilter(filtered, filterMode, resource, items)) {
                continue;
            }
            long offeredAmount = items ? ((ItemStack) offered).getCount() : ((FluidStack) offered).getAmount();
            Object inserted = invoke(storageComponent, "insert", resource, offeredAmount, simulateAction, emptyActor);
            if (inserted instanceof Long amountInserted && amountInserted > 0L) {
                return true;
            }
        }
        return false;
    }

    private static boolean rs2MatchesFilter(@Nullable FilteredResources filtered, @Nullable Object filterMode, Object resource, boolean items) {
        if (filtered == null) {
            return true;
        }
        List<Object> filters = items ? filtered.itemResources() : filtered.fluidResources();
        boolean matches = filters.isEmpty() || filters.stream().anyMatch(resource::equals);
        if (filterMode == null || !RS2_FILTER_MODE.equals(filterMode.getClass().getName())) {
            return matches;
        }
        String modeName = filterMode instanceof Enum<?> enumValue ? enumValue.name() : "";
        if ("BLACKLIST".equals(modeName)) {
            return !matches;
        }
        return matches;
    }

    private static boolean rs2IncludeInCapture(
            @Nullable FilteredResources filtered,
            @Nullable Object filterMode,
            Object resource,
            boolean items
    ) {
        if (filtered == null) {
            return true;
        }
        List<Object> filters = items ? filtered.itemResources() : filtered.fluidResources();
        if (filters.isEmpty()) {
            return true;
        }
        return rs2MatchesFilter(filtered, filterMode, resource, items);
    }

    private static List<ItemStack> qioAllItems(Object frequency) {
        Object itemDataMap = invoke(frequency, "getItemDataMap");
        if (!(itemDataMap instanceof Map<?, ?> itemMap)) {
            return List.of();
        }
        List<ItemStack> itemStacks = new ArrayList<>();
        for (Map.Entry<?, ?> entry : itemMap.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            long amount = numberAsLong(invoke(entry.getValue(), "getCount"));
            if (amount <= 0L) {
                continue;
            }
            Object stack = invoke(entry.getKey(), "createStack", (int) Math.min(Integer.MAX_VALUE, amount));
            if (stack instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                itemStacks.add(itemStack.copy());
            }
        }
        return itemStacks;
    }

    private static List<ItemStack> qioExporterItems(Object frequency, @Nullable Object enabledFilters, boolean exportWithoutFilter) {
        if (!(enabledFilters instanceof Iterable<?> filtersIterable)) {
            return exportWithoutFilter ? qioAllItems(frequency) : List.of();
        }

        Map<Object, Long> matchedCounts = new HashMap<>();
        Map<Object, ItemStack> matchedStacks = new HashMap<>();
        boolean hasAnyFilters = false;
        for (Object filter : filtersIterable) {
            if (filter == null) {
                continue;
            }
            hasAnyFilters = true;
            String className = filter.getClass().getName();
            if (MEK_QIO_ITEMSTACK_FILTER.equals(className)) {
                ItemStack target = invoke(filter, "getItemStack") instanceof ItemStack stack ? stack : ItemStack.EMPTY;
                if (target.isEmpty()) {
                    continue;
                }
                boolean fuzzyMode = Boolean.TRUE.equals(readField(filter, "fuzzyMode"));
                if (fuzzyMode) {
                    qioMergeStacksFromMap(invoke(frequency, "getStacksByItem", target.getItem()), matchedCounts, matchedStacks);
                } else {
                    long amount = numberAsLong(invoke(frequency, "getStored", target));
                    if (amount > 0L) {
                        Object hashed = staticInvoke(MEK_HASHED_ITEM, "raw", target);
                        if (hashed != null) {
                            matchedCounts.put(hashed, amount);
                            matchedStacks.put(hashed, target.copyWithCount((int) Math.min(Integer.MAX_VALUE, amount)));
                        }
                    }
                }
            } else if (MEK_QIO_TAG_FILTER.equals(className)) {
                qioMergeStacksFromMap(invoke(frequency, "getStacksByTagWildcard", invoke(filter, "getTagName")), matchedCounts, matchedStacks);
            } else if (MEK_QIO_MODID_FILTER.equals(className)) {
                qioMergeStacksFromMap(invoke(frequency, "getStacksByModIDWildcard", invoke(filter, "getModID")), matchedCounts, matchedStacks);
            }
        }

        if (!hasAnyFilters) {
            return exportWithoutFilter ? qioAllItems(frequency) : List.of();
        }

        List<ItemStack> itemStacks = new ArrayList<>();
        for (Map.Entry<Object, ItemStack> entry : matchedStacks.entrySet()) {
            long amount = matchedCounts.getOrDefault(entry.getKey(), 0L);
            if (amount <= 0L) {
                continue;
            }
            itemStacks.add(entry.getValue().copyWithCount((int) Math.min(Integer.MAX_VALUE, amount)));
        }
        return itemStacks;
    }

    private static void qioMergeStacksFromMap(@Nullable Object mapObject, Map<Object, Long> matchedCounts, Map<Object, ItemStack> matchedStacks) {
        if (!(mapObject instanceof Map<?, ?> map)) {
            return;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            long amount = numberAsLong(entry.getValue());
            if (key == null || amount <= 0L) {
                continue;
            }
            Object stack = invoke(key, "createStack", (int) Math.min(Integer.MAX_VALUE, amount));
            if (stack instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                matchedCounts.put(key, amount);
                matchedStacks.put(key, itemStack.copy());
            }
        }
    }

    private static boolean qioCanAcceptAnyItems(Object frequency, List<ItemStack> offeredItems) {
        Object simulate = enumConstant(MEK_ACTION, "SIMULATE");
        if (simulate == null) {
            return false;
        }
        for (ItemStack stack : offeredItems) {
            if (stack.isEmpty()) {
                continue;
            }
            Object inserted = invoke(frequency, "massInsert", stack, (long) stack.getCount(), simulate);
            if (inserted instanceof Number number && number.longValue() > 0L) {
                return true;
            }
        }
        return false;
    }

    private static List<Object> ae2Parts(Object cableBusBlockEntity) {
        Object cableBus = invoke(cableBusBlockEntity, "getCableBus");
        if (cableBus == null) {
            return List.of();
        }
        List<Object> parts = new ArrayList<>();
        parts.add(invoke(cableBus, "getPart", (Object) null));
        for (var side : net.minecraft.core.Direction.values()) {
            parts.add(invoke(cableBus, "getPart", side));
        }
        return parts.stream().filter(Objects::nonNull).toList();
    }

    private static void captureAe2ExportPart(
            Object part,
            List<ItemStack> itemStacks,
            List<FluidStack> fluidStacks,
            List<FilteredAe2Key> itemFilters,
            List<FilteredAe2Key> fluidFilters
    ) {
        Object mainNode = invoke(part, "getMainNode");
        Object grid = mainNode == null ? null : invoke(mainNode, "getGrid");
        Object storageService = grid == null ? null : invoke(grid, "getStorageService");
        Object inventory = storageService == null ? null : invoke(storageService, "getInventory");
        if (inventory == null && storageService != null) {
            inventory = invoke(storageService, "getCachedInventory");
        }
        if (inventory == null) {
            return;
        }

        List<Object> configuredKeys = ae2ConfigKeys(part);
        List<Object> configuredItemKeys = configuredKeys.stream()
                .filter(Objects::nonNull)
                .filter(key -> AE2_ITEM_KEY.equals(key.getClass().getName()))
                .toList();
        List<Object> configuredFluidKeys = configuredKeys.stream()
                .filter(Objects::nonNull)
                .filter(key -> AE2_FLUID_KEY.equals(key.getClass().getName()))
                .toList();

        configuredItemKeys.forEach(key -> itemFilters.add(new FilteredAe2Key(part, key)));
        configuredFluidKeys.forEach(key -> fluidFilters.add(new FilteredAe2Key(part, key)));

        Object availableStacks = invoke(inventory, "getAvailableStacks");
        if (!(availableStacks instanceof Iterable<?> iterable)) {
            return;
        }
        for (Object entry : iterable) {
            Object key = invoke(entry, "getKey");
            Object amountValue = invoke(entry, "getLongValue");
            if (!(amountValue instanceof Long amount) || amount <= 0L || key == null) {
                continue;
            }
            if (AE2_ITEM_KEY.equals(key.getClass().getName())) {
                if (!configuredItemKeys.isEmpty() && configuredItemKeys.stream().noneMatch(key::equals)) {
                    continue;
                }
                Object stack = invoke(key, "toStack", (int) Math.min(Integer.MAX_VALUE, amount));
                if (stack instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                    itemStacks.add(itemStack.copy());
                }
            } else if (AE2_FLUID_KEY.equals(key.getClass().getName())) {
                if (!configuredFluidKeys.isEmpty() && configuredFluidKeys.stream().noneMatch(key::equals)) {
                    continue;
                }
                Object stack = invoke(key, "toStack", (int) Math.min(Integer.MAX_VALUE, amount));
                if (stack instanceof FluidStack fluidStack && !fluidStack.isEmpty()) {
                    fluidStacks.add(fluidStack.copy());
                }
            }
        }
    }

    private static List<Object> ae2ConfigKeys(Object part) {
        Object config = invoke(part, "getConfig");
        Object keys = config == null ? null : invoke(config, "keySet");
        if (keys instanceof Set<?> set) {
            return new ArrayList<>(set.stream().filter(Objects::nonNull).toList());
        }
        return List.of();
    }

    private static boolean ae2MatchesFilter(List<FilteredAe2Key> filters, Object part, Object key) {
        List<FilteredAe2Key> scoped = filters.stream().filter(filter -> filter.part() == part).toList();
        return scoped.isEmpty() || scoped.stream().anyMatch(filter -> filter.key().equals(key));
    }

    private static long numberAsLong(@Nullable Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    record EndpointKinds(boolean itemStorage, boolean fluidStorage) {
        static EndpointKinds none() {
            return new EndpointKinds(false, false);
        }

        boolean hasAnyStorage() {
            return itemStorage || fluidStorage;
        }
    }

    record EndpointCapture(
            boolean hasItemStorage,
            boolean hasFluidStorage,
            List<ItemStack> itemStacks,
            List<FluidStack> fluidStacks,
            int remainingItemCapacity,
            long remainingFluidCapacity,
            Object itemRootKey,
            Object fluidRootKey,
            AcceptanceProbe<ItemStack> itemAcceptanceProbe,
            AcceptanceProbe<FluidStack> fluidAcceptanceProbe
    ) {
        boolean acceptsAnyItems(List<ItemStack> offeredItems) {
            return itemAcceptanceProbe != null && itemAcceptanceProbe.acceptsAny(offeredItems);
        }

        boolean acceptsAnyFluids(List<FluidStack> offeredFluids) {
            return fluidAcceptanceProbe != null && fluidAcceptanceProbe.acceptsAny(offeredFluids);
        }
    }

    @FunctionalInterface
    interface AcceptanceProbe<T> {
        boolean acceptsAny(List<T> offered);
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

    private record RootDescriptor(String kind, Object identity) {
    }

    private record FilteredResources(List<Object> itemResources, List<Object> fluidResources) {
        static FilteredResources empty() {
            return new FilteredResources(List.of(), List.of());
        }

        boolean hasAny() {
            return !itemResources.isEmpty() || !fluidResources.isEmpty();
        }
    }

    private record FilteredAe2Key(Object part, Object key) {
    }

    private record ClassMethodKey(Class<?> type, String methodName, List<Class<?>> parameterTypes) {
        private ClassMethodKey {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(methodName, "methodName");
            parameterTypes = List.copyOf(parameterTypes);
        }
    }
}
