package net.sprocketgames.create_aeronautics_automated_logistics.cargo;

import java.lang.reflect.Constructor;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

final class CustomCargoEndpointSupport {
    private static final String RS2_STORAGE_MONITOR =
            "com.refinedmods.refinedstorage.common.storagemonitor.StorageMonitorBlockEntity";
    private static final String RS2_GRID_PREFIX =
            "com.refinedmods.refinedstorage.common.grid.";
    private static final String RS2_INTERFACE =
            "com.refinedmods.refinedstorage.common.iface.InterfaceBlockEntity";
    private static final String RS2_EXPORTER =
            "com.refinedmods.refinedstorage.common.exporter.AbstractExporterBlockEntity";
    private static final String RS2_IMPORTER =
            "com.refinedmods.refinedstorage.common.importer.AbstractImporterBlockEntity";
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

    private static final String AE2_INTERFACE =
            "appeng.blockentity.misc.InterfaceBlockEntity";
    private static final String AE2_ME_CHEST =
            "appeng.blockentity.storage.MEChestBlockEntity";
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
    private static final String AE2_IMPORT_BUS_PART =
            "appeng.parts.automation.ImportBusPart";
    private static final String AE2_INTERFACE_PART =
            "appeng.parts.misc.InterfacePart";
    private static final String AE2_STORAGE_MONITOR_PART =
            "appeng.parts.reporting.StorageMonitorPart";

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
        if (isRs2Endpoint(className)
                || isType(blockEntity, RS2_EXPORTER)
                || isType(blockEntity, RS2_IMPORTER)
                || AE2_INTERFACE.equals(className)
                || AE2_ME_CHEST.equals(className)
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
        if (isType(blockEntity, RS2_IMPORTER)) {
            return captureRs2Importer(blockEntity);
        }
        if (AE2_INTERFACE.equals(className) || AE2_ME_CHEST.equals(className)) {
            return captureAe2(blockEntity);
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
                    Object stackValue = invoke(resource, "toItemStack", resourceAmount);
                    if (stackValue instanceof ItemStack stack && !stack.isEmpty()) {
                        itemStacks.add(stack.copy());
                    }
                } else if (className.equals(RS2_FLUID_RESOURCE)) {
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

        Object simulateAction = enumConstant(RS2_ACTION, "SIMULATE");
        Object emptyActor = staticFieldValue(RS2_ACTOR, "EMPTY");
        if (simulateAction == null || emptyActor == null) {
            return null;
        }

        Object rootKey = rootIdentityKey(storageComponent);
        return new EndpointCapture(
                true,
                true,
                List.copyOf(itemStacks),
                List.copyOf(fluidStacks),
                Integer.MAX_VALUE,
                Long.MAX_VALUE,
                rootKey,
                rootKey,
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

    private static @Nullable EndpointCapture captureRs2Exporter(BlockEntity blockEntity) {
        Object storageComponent = resolveRs2StorageComponent(blockEntity);
        if (storageComponent == null) {
            return null;
        }

        FilteredResources filtered = readRs2FilteredResources(resolveRs2FilterData(blockEntity));
        if (!filtered.hasAny()) {
            return null;
        }

        Object simulateAction = enumConstant(RS2_ACTION, "SIMULATE");
        Object emptyActor = staticFieldValue(RS2_ACTOR, "EMPTY");
        if (simulateAction == null || emptyActor == null) {
            return null;
        }

        List<ItemStack> itemStacks = new ArrayList<>();
        List<FluidStack> fluidStacks = new ArrayList<>();
        for (Object resource : filtered.itemResources()) {
            long amount = rs2ResourceAmount(storageComponent, resource, simulateAction, emptyActor);
            if (amount <= 0L) {
                continue;
            }
            ItemStack stack = rs2ItemStack(resource, amount);
            if (!stack.isEmpty()) {
                itemStacks.add(stack);
            }
        }
        for (Object resource : filtered.fluidResources()) {
            long amount = rs2ResourceAmount(storageComponent, resource, simulateAction, emptyActor);
            if (amount <= 0L) {
                continue;
            }
            FluidStack stack = rs2FluidStack(resource, amount);
            if (!stack.isEmpty()) {
                fluidStacks.add(stack);
            }
        }

        Object rootKey = new RootDescriptor("rs2_exporter", rootIdentityKey(storageComponent));
        return new EndpointCapture(
                !filtered.itemResources().isEmpty(),
                !filtered.fluidResources().isEmpty(),
                List.copyOf(itemStacks),
                List.copyOf(fluidStacks),
                0,
                0L,
                rootKey,
                rootKey,
                offeredItems -> false,
                offeredFluids -> false
        );
    }

    private static @Nullable EndpointCapture captureRs2Importer(BlockEntity blockEntity) {
        Object storageComponent = resolveRs2StorageComponent(blockEntity);
        if (storageComponent == null) {
            return null;
        }

        FilteredResources filtered = readRs2FilteredResources(resolveRs2FilterData(blockEntity));
        Object filterMode = invoke(blockEntity, "getFilterMode");
        Object simulateAction = enumConstant(RS2_ACTION, "SIMULATE");
        Object emptyActor = staticFieldValue(RS2_ACTOR, "EMPTY");
        if (simulateAction == null || emptyActor == null) {
            return null;
        }

        Object rootKey = new RootDescriptor("rs2_importer", rootIdentityKey(storageComponent));
        return new EndpointCapture(
                true,
                true,
                List.of(),
                List.of(),
                Integer.MAX_VALUE,
                Long.MAX_VALUE,
                rootKey,
                rootKey,
                offeredItems -> rs2CanAcceptAny(storageComponent, filtered, filterMode, offeredItems, simulateAction, emptyActor, true),
                offeredFluids -> rs2CanAcceptAny(storageComponent, filtered, filterMode, offeredFluids, simulateAction, emptyActor, false)
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

        Object rootKey = rootIdentityKey(storage);
        return new EndpointCapture(
                true,
                true,
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

    private static @Nullable EndpointCapture captureAe2CableBus(BlockEntity blockEntity) {
        List<Object> exportParts = new ArrayList<>();
        List<Object> importParts = new ArrayList<>();
        for (Object part : ae2Parts(blockEntity)) {
            String className = part.getClass().getName();
            if (AE2_EXPORT_BUS_PART.equals(className)) {
                exportParts.add(part);
            } else if (AE2_IMPORT_BUS_PART.equals(className)) {
                importParts.add(part);
            }
        }
        if (exportParts.isEmpty() && importParts.isEmpty()) {
            return null;
        }

        List<ItemStack> itemStacks = new ArrayList<>();
        List<FluidStack> fluidStacks = new ArrayList<>();
        List<FilteredAe2Key> importItemFilters = new ArrayList<>();
        List<FilteredAe2Key> importFluidFilters = new ArrayList<>();

        for (Object part : exportParts) {
            captureAe2ExportPart(part, itemStacks, fluidStacks);
        }
        for (Object part : importParts) {
            captureAe2ImportPartFilters(part, importItemFilters, importFluidFilters);
        }

        if (itemStacks.isEmpty() && fluidStacks.isEmpty() && importItemFilters.isEmpty() && importFluidFilters.isEmpty()) {
            return null;
        }

        Object source = instantiate(AE2_BASE_ACTION_SOURCE);
        Object simulateMode = enumConstant(AE2_ACTIONABLE, "SIMULATE");
        Object rootKey = new RootDescriptor("ae2_cable_bus", rootIdentityKey(blockEntity));
        return new EndpointCapture(
                !itemStacks.isEmpty() || !importItemFilters.isEmpty(),
                !fluidStacks.isEmpty() || !importFluidFilters.isEmpty(),
                List.copyOf(itemStacks),
                List.copyOf(fluidStacks),
                Integer.MAX_VALUE,
                Long.MAX_VALUE,
                rootKey,
                rootKey,
                offeredItems -> ae2ImportPartsAccept(importParts, importItemFilters, offeredItems, simulateMode, source, true),
                offeredFluids -> ae2ImportPartsAccept(importParts, importFluidFilters, offeredFluids, simulateMode, source, false)
        );
    }

    private static @Nullable Object resolveAe2Storage(BlockEntity blockEntity) {
        if (AE2_INTERFACE.equals(blockEntity.getClass().getName())) {
            Object logic = invoke(blockEntity, "getInterfaceLogic");
            return logic == null ? null : invoke(logic, "getInventory");
        }
        if (AE2_ME_CHEST.equals(blockEntity.getClass().getName())) {
            return invoke(blockEntity, "getInventory");
        }
        return null;
    }

    private static boolean isRs2Endpoint(String className) {
        return className.equals(RS2_STORAGE_MONITOR)
                || (className.startsWith(RS2_GRID_PREFIX) && className.endsWith("BlockEntity"));
    }

    private static boolean hasAe2CargoParts(BlockEntity blockEntity) {
        if (!AE2_CABLE_BUS.equals(blockEntity.getClass().getName())) {
            return false;
        }
        for (Object part : ae2Parts(blockEntity)) {
            String className = part.getClass().getName();
            if (AE2_EXPORT_BUS_PART.equals(className)
                    || AE2_IMPORT_BUS_PART.equals(className)
                    || AE2_INTERFACE_PART.equals(className)
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
        return storageComponentClass == null ? null : invoke(network, "getComponent", storageComponentClass);
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
            FilteredResources filtered,
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

    private static boolean rs2MatchesFilter(FilteredResources filtered, @Nullable Object filterMode, Object resource, boolean items) {
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

    private static void captureAe2ExportPart(Object part, List<ItemStack> itemStacks, List<FluidStack> fluidStacks) {
        Object grid = invoke(invoke(part, "getMainNode"), "getGrid");
        Object storageService = grid == null ? null : invoke(grid, "getStorageService");
        Object cachedInventory = storageService == null ? null : invoke(storageService, "getCachedInventory");
        if (cachedInventory == null) {
            return;
        }

        for (Object key : ae2ConfigKeys(part)) {
            if (key == null) {
                continue;
            }
            Object amountValue = invoke(cachedInventory, "get", key);
            if (!(amountValue instanceof Long amount) || amount <= 0L) {
                continue;
            }
            if (AE2_ITEM_KEY.equals(key.getClass().getName())) {
                Object stack = invoke(key, "toStack", (int) Math.min(Integer.MAX_VALUE, amount));
                if (stack instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                    itemStacks.add(itemStack.copy());
                }
            } else if (AE2_FLUID_KEY.equals(key.getClass().getName())) {
                Object stack = invoke(key, "toStack", (int) Math.min(Integer.MAX_VALUE, amount));
                if (stack instanceof FluidStack fluidStack && !fluidStack.isEmpty()) {
                    fluidStacks.add(fluidStack.copy());
                }
            }
        }
    }

    private static void captureAe2ImportPartFilters(
            Object part,
            List<FilteredAe2Key> itemFilters,
            List<FilteredAe2Key> fluidFilters
    ) {
        for (Object key : ae2ConfigKeys(part)) {
            if (key == null) {
                continue;
            }
            if (AE2_ITEM_KEY.equals(key.getClass().getName())) {
                itemFilters.add(new FilteredAe2Key(part, key));
            } else if (AE2_FLUID_KEY.equals(key.getClass().getName())) {
                fluidFilters.add(new FilteredAe2Key(part, key));
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

    private static <T> boolean ae2ImportPartsAccept(
            List<Object> importParts,
            List<FilteredAe2Key> filters,
            List<T> offeredStacks,
            @Nullable Object simulateMode,
            @Nullable Object source,
            boolean items
    ) {
        if (simulateMode == null || source == null) {
            return false;
        }
        for (Object part : importParts) {
            Object grid = invoke(invoke(part, "getMainNode"), "getGrid");
            Object storageService = grid == null ? null : invoke(grid, "getStorageService");
            Object inventory = storageService == null ? null : invoke(storageService, "getInventory");
            if (inventory == null) {
                continue;
            }
            for (T offered : offeredStacks) {
                Object key = items ? staticInvoke(AE2_ITEM_KEY, "of", offered) : staticInvoke(AE2_FLUID_KEY, "of", offered);
                if (key == null || !ae2MatchesFilter(filters, part, key)) {
                    continue;
                }
                long amount = items ? ((ItemStack) offered).getCount() : ((FluidStack) offered).getAmount();
                Object inserted = invoke(inventory, "insert", key, amount, simulateMode, source);
                if (inserted instanceof Long amountInserted && amountInserted > 0L) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean ae2MatchesFilter(List<FilteredAe2Key> filters, Object part, Object key) {
        List<FilteredAe2Key> scoped = filters.stream().filter(filter -> filter.part() == part).toList();
        return scoped.isEmpty() || scoped.stream().anyMatch(filter -> filter.key().equals(key));
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
