package net.sprocketgames.create_aeronautics_automated_logistics.item;

import java.util.List;
import com.simibubi.create.foundation.item.ItemDescription;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public class LogisticsBlockItem extends BlockItem {
    private final String tooltipKey;

    public LogisticsBlockItem(Block block, Properties properties, String tooltipKey) {
        super(block, properties);
        this.tooltipKey = tooltipKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        ItemDescription description = ItemDescription.create(tooltipKey, FontHelper.Palette.STANDARD_CREATE);
        if (description != null) {
            tooltipComponents.addAll(description.getCurrentLines());
        }
    }
}
