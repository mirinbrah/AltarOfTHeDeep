package player.offeringOfTheDeep;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.Objects;

public final class OfferingOfTheDeep extends JavaPlugin implements Listener {

    private NamespacedKey pedestalKey;
    private NamespacedKey stateKey;
    private NamespacedKey priceKey;
    private NamespacedKey currencyKey;
    private NamespacedKey itemKey;

    private static final PersistentDataType<byte[], ItemStack> ITEM_STACK_TYPE = new PersistentDataType<>() {
        @Override
        public @NotNull Class<byte[]> getPrimitiveType() { return byte[].class; }
        @Override
        public @NotNull Class<ItemStack> getComplexType() { return ItemStack.class; }
        @Override
        public byte @NotNull [] toPrimitive(@NotNull ItemStack complex, @NotNull PersistentDataAdapterContext context) {
            return complex.serializeAsBytes();
        }
        @Override
        public @NotNull ItemStack fromPrimitive(byte @NotNull [] primitive, @NotNull PersistentDataAdapterContext context) {
            return ItemStack.deserializeBytes(primitive);
        }
    };

    @Override
    public void onEnable() {
        this.pedestalKey = new NamespacedKey(this, "is_pedestal");
        this.stateKey = new NamespacedKey(this, "pedestal_state");
        this.priceKey = new NamespacedKey(this, "pedestal_price");
        this.currencyKey = new NamespacedKey(this, "pedestal_currency");
        this.itemKey = new NamespacedKey(this, "pedestal_item");

        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("givepedestal") != null) {
            Objects.requireNonNull(getCommand("givepedestal")).setExecutor(this);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("pedestal.admin")) return true;

        ItemStack item = new ItemStack(Material.CHISELED_DEEPSLATE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Пьедестал Глубин", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
            meta.getPersistentDataContainer().set(pedestalKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        player.getInventory().addItem(item);
        return true;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(pedestalKey, PersistentDataType.BYTE)) return;

        Block block = event.getBlock();
        Location loc = block.getLocation().add(0.5, 1.3, 0.5);
        TextDisplay display = (TextDisplay) block.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);

        display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        display.text(Component.text("Настройте пьедестал\n(Кликните предметом для продажи)", NamedTextColor.YELLOW));

        Transformation transformation = display.getTransformation();
        transformation.getScale().set(new Vector3f(1.0f, 1.0f, 1.0f));
        display.setTransformation(transformation);

        display.getPersistentDataContainer().set(pedestalKey, PersistentDataType.BYTE, (byte) 1);
        display.getPersistentDataContainer().set(stateKey, PersistentDataType.STRING, "WAITING_ITEM");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return; // Игнорируем вторую руку

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHISELED_DEEPSLATE) return;

        TextDisplay display = findDisplay(block.getLocation());
        if (display == null) return;

        event.setCancelled(true); // Отменяем стандартное действие сразу

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        String state = display.getPersistentDataContainer().getOrDefault(stateKey, PersistentDataType.STRING, "NONE");

        switch (state) {
            case "WAITING_ITEM" -> {
                if (hand.getType().isAir()) return;

                ItemStack saleStack = hand.clone();
                saleStack.setAmount(1);
                display.getPersistentDataContainer().set(itemKey, ITEM_STACK_TYPE, saleStack);
                display.getPersistentDataContainer().set(stateKey, PersistentDataType.STRING, "WAITING_PRICE");

                display.text(Component.text("Предмет: ", NamedTextColor.GRAY)
                        .append(Component.translatable(saleStack.translationKey(), NamedTextColor.AQUA))
                        .append(Component.text("\nКликните Золотом или Алмазом\n(Количество в руке станет ценой)", NamedTextColor.GOLD)));

                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.2f);
            }
            case "WAITING_PRICE" -> {
                if (hand.getType() != Material.GOLD_INGOT && hand.getType() != Material.DIAMOND) return;

                int amountInHand = hand.getAmount();
                display.getPersistentDataContainer().set(priceKey, PersistentDataType.INTEGER, amountInHand);
                display.getPersistentDataContainer().set(currencyKey, PersistentDataType.STRING, hand.getType().name());
                display.getPersistentDataContainer().set(stateKey, PersistentDataType.STRING, "ACTIVE");

                updatePedestalText(display);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
            case "ACTIVE" -> {
                ItemStack saleItem = display.getPersistentDataContainer().get(itemKey, ITEM_STACK_TYPE);
                Integer price = display.getPersistentDataContainer().get(priceKey, PersistentDataType.INTEGER);
                String currencyStr = display.getPersistentDataContainer().get(currencyKey, PersistentDataType.STRING);

                if (saleItem == null || price == null || currencyStr == null) return;
                Material currencyMat = Material.valueOf(currencyStr);

                if (player.getInventory().containsAtLeast(new ItemStack(currencyMat), price)) {
                    player.getInventory().removeItem(new ItemStack(currencyMat, price));
                    player.getInventory().addItem(saleItem.clone());
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
                } else {
                    player.sendMessage(Component.text("Недостаточно средств!", NamedTextColor.RED));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                }
            }
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.CHISELED_DEEPSLATE) return;
        TextDisplay display = findDisplay(event.getBlock().getLocation());
        if (display != null) {
            display.remove();
        }
    }

    private TextDisplay findDisplay(Location blockLoc) {
        Location checkLoc = blockLoc.clone().add(0.5, 1.3, 0.5);
        for (Entity entity : checkLoc.getWorld().getNearbyEntities(checkLoc, 0.5, 0.5, 0.5)) {
            if (entity instanceof TextDisplay display && display.getPersistentDataContainer().has(pedestalKey, PersistentDataType.BYTE)) {
                return display;
            }
        }
        return null;
    }

    private void updatePedestalText(TextDisplay display) {
        ItemStack item = display.getPersistentDataContainer().get(itemKey, ITEM_STACK_TYPE);
        Integer price = display.getPersistentDataContainer().get(priceKey, PersistentDataType.INTEGER);
        String currency = display.getPersistentDataContainer().get(currencyKey, PersistentDataType.STRING);

        if (item == null || price == null || currency == null) return;

        NamedTextColor color = currency.equals("DIAMOND") ? NamedTextColor.AQUA : NamedTextColor.GOLD;
        String currencyName = currency.equals("DIAMOND") ? "Алмазов" : "Золота";

        display.text(Component.text("Торговая точка\n", NamedTextColor.DARK_GRAY)
                .append(Component.translatable(item.translationKey(), NamedTextColor.WHITE))
                .append(Component.text("\nЦена: ", NamedTextColor.YELLOW))
                .append(Component.text(price + " " + currencyName, color)));
    }
}