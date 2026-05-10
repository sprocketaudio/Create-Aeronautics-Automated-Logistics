package net.sprocketgames.create_aeronautics_automated_logistics.item;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.simibubi.create.foundation.item.ItemDescription;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.AirshipScheduleMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipSchedule;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleNbtSerializer;

public class AirshipScheduleItem extends Item {
    private static final String SCHEDULE_TAG = "airshipSchedule";

    public AirshipScheduleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        serverPlayer.openMenu(new SimpleMenuProvider(
                (containerId, inventory, menuPlayer) -> new AirshipScheduleMenu(containerId, inventory),
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.title")
        ));
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        AirshipSchedule schedule = readSchedule(stack);
        tooltipComponents.add(Component.literal(schedule.title()).withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(
                "tooltip.create_aeronautics_automated_logistics.airship_schedule.entries",
                schedule.entries().size()
        ).withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.create_aeronautics_automated_logistics.airship_schedule.open").withStyle(ChatFormatting.GRAY));
        ItemDescription description = ItemDescription.create(getDescriptionId() + ".tooltip", FontHelper.Palette.STANDARD_CREATE);
        if (description != null) {
            tooltipComponents.addAll(description.getCurrentLines());
        }
    }

    public static AirshipSchedule readSchedule(ItemStack stack) {
        try {
            return readScheduleTag(stack).map(AirshipScheduleNbtSerializer::read).orElseGet(AirshipSchedule::empty);
        } catch (Throwable ignored) {
            return AirshipSchedule.empty();
        }
    }

    public static void writeSchedule(ItemStack stack, AirshipSchedule schedule) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        root.put(SCHEDULE_TAG, AirshipScheduleNbtSerializer.write(schedule));
        CustomData.set(DataComponents.CUSTOM_DATA, stack, root);
    }

    public static boolean isLegacyUnboundSchedule(ItemStack stack) {
        return readScheduleTag(stack).map(AirshipScheduleNbtSerializer::isLegacyUnbound).orElse(false);
    }

    public static void bindScheduleToTransponder(ItemStack stack, UUID transponderId, String shipName) {
        AirshipSchedule schedule = readSchedule(stack).withAssignedShip(Optional.of(transponderId), shipName);
        writeSchedule(stack, schedule);
    }

    private static Optional<CompoundTag> readScheduleTag(ItemStack stack) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!root.contains(SCHEDULE_TAG, TagType.COMPOUND)) {
            return Optional.empty();
        }
        return Optional.of(root.getCompound(SCHEDULE_TAG));
    }

    private static final class TagType {
        private static final int COMPOUND = 10;
    }
}
