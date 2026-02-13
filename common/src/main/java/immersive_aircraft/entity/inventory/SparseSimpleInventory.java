package immersive_aircraft.entity.inventory;

import com.mojang.datafixers.util.Pair;
import immersive_aircraft.Main;
import immersive_aircraft.cobalt.network.NetworkHandler;
import immersive_aircraft.entity.InventoryVehicleEntity;
import immersive_aircraft.network.c2s.InventoryRequest;
import immersive_aircraft.network.s2c.InventoryUpdateMessage;
import immersive_aircraft.screen.VehicleScreenHandler;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class SparseSimpleInventory extends SimpleContainer {
    private final NonNullList<ItemStack> tracked;
    private boolean inventoryRequested = false;

    public SparseSimpleInventory(int size) {
        super(size);

        tracked = NonNullList.withSize(size, ItemStack.EMPTY);
    }

    public void fromIndexedItemList(ValueInput.TypedInputList<Pair<Integer, ItemStack>> typedInputList) {
        clearContent();
        for (Pair<Integer, ItemStack> pair : typedInputList) {
            getItems().set(pair.getFirst(), pair.getSecond());
        }
    }

    public void storeAsIndexedItemList(ValueOutput.TypedOutputList<Pair<Integer, ItemStack>> typedOutputList) {
        NonNullList<ItemStack> items = getItems();
        for (int i = 0; i < items.size(); i++) {
            ItemStack itemStack = items.get(i);
            if (!itemStack.isEmpty()) {
                typedOutputList.add(Pair.of(i, itemStack));
            }
        }
    }

    public void tick(InventoryVehicleEntity entity) {
        if (entity.level().isClientSide()) {
            // Sync initial inventory
            if (!inventoryRequested) {
                NetworkHandler.sendToServer(new InventoryRequest(entity.getId()));
                inventoryRequested = true;
            }
        } else {
            // Sync changed slots (excluding trailing inventory slots since they won't affect behavior)
            int lastSyncIndex = entity.getInventoryDescription().getLastSyncIndex();
            if (lastSyncIndex < 0) return;
            int index = entity.tickCount % (lastSyncIndex + 1);
            ItemStack stack = getItem(index);
            ItemStack trackedStack = tracked.get(index);
            if (!ItemStack.isSameItem(stack, trackedStack)) {
                tracked.set(index, stack.copy());
                entity.level().players().forEach(p -> {
                    if (!(p.containerMenu instanceof VehicleScreenHandler vehicleScreenHandler && vehicleScreenHandler.getVehicle() == entity)) {
                        NetworkHandler.sendToPlayer(new InventoryUpdateMessage(entity, index, stack), (ServerPlayer) p);
                    }
                });
            }
        }
    }
}
