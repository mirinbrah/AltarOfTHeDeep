package player.offeringOfTheDeep;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class OfferingOfTheDeep extends JavaPlugin implements Listener {

    private NamespacedKey pedestalKey;
    private NamespacedKey priceKey;
    private NamespacedKey currencyKey;
    private NamespacedKey storageKey;
    private NamespacedKey itemDisplayKey;
    private NamespacedKey indexKey;

    private static final PersistentDataType<String, ItemStack[]> ITEM_ARRAY_TYPE = new PersistentDataType<>() {
        @Override public @NotNull Class<String> getPrimitiveType() { return String.class; }
        @Override public @NotNull Class<ItemStack[]> getComplexType() { return ItemStack[].class; }
        @Override
        public @NotNull String toPrimitive(ItemStack @NotNull [] complex, @NotNull PersistentDataAdapterContext context) {
            YamlConfiguration config = new YamlConfiguration();
            config.set("items", complex);
            return config.saveToString();
        }
        @Override
        public ItemStack @NotNull [] fromPrimitive(@NotNull String primitive, @NotNull PersistentDataAdapterContext context) {
            YamlConfiguration config = new YamlConfiguration();
            try {
                config.loadFromString(primitive);
                List<?> list = config.getList("items");
                if (list == null) return new ItemStack[0];
                return list.toArray(new ItemStack[0]);
            } catch (Exception e) { return new ItemStack[0]; }
        }
    };

    @Override
    public void onEnable() {
        this.pedestalKey = new NamespacedKey(this, "is_pedestal");
        this.priceKey = new NamespacedKey(this, "price");
        this.currencyKey = new NamespacedKey(this, "currency");
        this.storageKey = new NamespacedKey(this, "storage");
        this.itemDisplayKey = new NamespacedKey(this, "pedestal_item_display");
        this.indexKey = new NamespacedKey(this, "display_index");

        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("givepedestal") != null) {
            Objects.requireNonNull(getCommand("givepedestal")).setExecutor(this);
        }

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (World world : Bukkit.getWorlds()) {
                for (TextDisplay td : world.getEntitiesByClass(TextDisplay.class)) {
                    if (td.getPersistentDataContainer().has(pedestalKey, PersistentDataType.BYTE)) {
                        ItemStack[] items = td.getPersistentDataContainer().get(storageKey, ITEM_ARRAY_TYPE);
                        if (items != null && items.length > 0) {
                            int nextIdx = td.getPersistentDataContainer().getOrDefault(indexKey, PersistentDataType.INTEGER, 0);
                            updatePedestalVisuals(td.getLocation().subtract(0.5, 1.2, 0.5), items, nextIdx);
                            td.getPersistentDataContainer().set(indexKey, PersistentDataType.INTEGER, nextIdx + 1);
                        }
                    }
                }
            }
        }, 0L, 100L);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player) || !player.hasPermission("pedestal.admin")) return true;
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

        Location loc = event.getBlock().getLocation().add(0.5, 1.2, 0.5);
        TextDisplay display = (TextDisplay) loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
        display.getPersistentDataContainer().set(pedestalKey, PersistentDataType.BYTE, (byte) 1);
        display.getPersistentDataContainer().set(storageKey, ITEM_ARRAY_TYPE, new ItemStack[0]);
        display.setBillboard(Display.Billboard.CENTER);
        updatePedestalVisuals(event.getBlock().getLocation(), new ItemStack[0], 0);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHISELED_DEEPSLATE) return;
        TextDisplay display = findDisplay(block.getLocation());
        if (display == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.isSneaking() && player.hasPermission("pedestal.admin")) {
            openAdminGui(player, display);
        } else {
            openCustomerGui(player, display);
        }
    }

    private void openAdminGui(Player player, TextDisplay display) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Настройка Пьедестала"));
        ItemStack[] savedItems = display.getPersistentDataContainer().get(storageKey, ITEM_ARRAY_TYPE);
        if (savedItems != null) inv.setContents(savedItems);
        player.openInventory(inv);
        player.setMetadata("pedestal_id", new org.bukkit.metadata.FixedMetadataValue(this, display.getUniqueId().toString()));
    }

    private void openCustomerGui(Player player, TextDisplay display) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Магазин Глубин"));
        ItemStack[] items = display.getPersistentDataContainer().get(storageKey, ITEM_ARRAY_TYPE);
        if (items != null) {
            for (ItemStack item : items) {
                if (item == null || item.getType().isAir()) continue;
                ItemStack icon = item.clone();
                ItemMeta meta = icon.getItemMeta();
                if (meta == null) continue;
                int price = meta.getPersistentDataContainer().getOrDefault(priceKey, PersistentDataType.INTEGER, 0);
                String curr = meta.getPersistentDataContainer().getOrDefault(currencyKey, PersistentDataType.STRING, "DIAMOND");
                List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
                if (lore == null) lore = new ArrayList<>();
                lore.add(Component.text("----------------", NamedTextColor.DARK_GRAY));
                lore.add(Component.text("Цена: ", NamedTextColor.YELLOW)
                        .append(Component.text(price + " " + curr.replace("_", " "), NamedTextColor.GOLD)));
                meta.lore(lore);
                icon.setItemMeta(meta);
                inv.addItem(icon);
            }
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getPlayer().hasMetadata("pedestal_id")) return;
        String uuidStr = event.getPlayer().getMetadata("pedestal_id").get(0).asString();
        event.getPlayer().removeMetadata("pedestal_id", this);
        Entity entity = Bukkit.getEntity(UUID.fromString(uuidStr));
        if (entity instanceof TextDisplay display) {
            ItemStack[] contents = event.getInventory().getContents();
            display.getPersistentDataContainer().set(storageKey, ITEM_ARRAY_TYPE, contents);
            updatePedestalVisuals(display.getLocation().subtract(0.5, 1.2, 0.5), contents, 0);
            event.getPlayer().sendMessage(Component.text("Сохранено!", NamedTextColor.GREEN));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        Player player = (Player) event.getWhoClicked();
        if (title.equals("Магазин Глубин")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) return;
            ItemMeta meta = clicked.getItemMeta();
            int price = meta.getPersistentDataContainer().getOrDefault(priceKey, PersistentDataType.INTEGER, 0);
            Material curr = Material.valueOf(meta.getPersistentDataContainer().getOrDefault(currencyKey, PersistentDataType.STRING, "DIAMOND"));
            if (player.getInventory().containsAtLeast(new ItemStack(curr), price)) {
                player.getInventory().removeItem(new ItemStack(curr, price));
                ItemStack res = clicked.clone();
                ItemMeta rM = res.getItemMeta();
                List<Component> l = rM.lore();
                if (l != null && l.size() >= 2) { l.removeLast(); l.removeLast(); }
                rM.lore(l);
                res.setItemMeta(rM);
                player.getInventory().addItem(res);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
            } else { player.sendMessage(Component.text("Недостаточно: " + curr.name(), NamedTextColor.RED)); }
        } else if (title.equals("Настройка Пьедестала")) {
            ItemStack cursor = event.getCursor();
            ItemStack clicked = event.getCurrentItem();
            if (cursor != null && !cursor.getType().isAir() && clicked != null && !clicked.getType().isAir()) {
                ItemMeta m = clicked.getItemMeta();
                m.getPersistentDataContainer().set(priceKey, PersistentDataType.INTEGER, cursor.getAmount());
                m.getPersistentDataContainer().set(currencyKey, PersistentDataType.STRING, cursor.getType().name());
                clicked.setItemMeta(m);
                player.sendMessage(Component.text("Цена: " + cursor.getAmount() + " " + cursor.getType().name(), NamedTextColor.GREEN));
                event.setCancelled(true);
            }
        }
    }

    private void updatePedestalVisuals(Location blockLoc, ItemStack[] items, int index) {
        List<ItemStack> validItems = new ArrayList<>();
        if (items != null) for (ItemStack is : items) if (is != null && !is.getType().isAir()) validItems.add(is);

        Location textLoc = blockLoc.clone().add(0.5, 1.2, 0.5);
        Location itemLoc = blockLoc.clone().add(0.5, 1.7, 0.5);
        TextDisplay td = findEntity(textLoc, TextDisplay.class, pedestalKey);
        ItemDisplay id = findEntity(itemLoc, ItemDisplay.class, itemDisplayKey);

        if (validItems.isEmpty()) {
            if (td != null) td.text(Component.text("Пусто", NamedTextColor.GRAY));
            if (id != null) id.remove();
            return;
        }

        ItemStack show = validItems.get(index % validItems.size());
        ItemMeta m = show.getItemMeta();
        int p = m.getPersistentDataContainer().getOrDefault(priceKey, PersistentDataType.INTEGER, 0);
        String c = m.getPersistentDataContainer().getOrDefault(currencyKey, PersistentDataType.STRING, "DIAMOND");

        if (td != null) {
            td.text(Component.text(p + " ", NamedTextColor.YELLOW, TextDecoration.BOLD)
                    .append(Component.text(c.replace("_", " "), NamedTextColor.GOLD))
                    .append(Component.text("\n" + PlainTextComponentSerializer.plainText().serialize(m.hasDisplayName() ? m.displayName() : Component.translatable(show.translationKey())), NamedTextColor.WHITE)));
        }

        if (id == null) {
            id = (ItemDisplay) blockLoc.getWorld().spawnEntity(itemLoc, EntityType.ITEM_DISPLAY);
            id.getPersistentDataContainer().set(itemDisplayKey, PersistentDataType.BYTE, (byte) 1);
            id.setBillboard(Display.Billboard.CENTER);
        }
        id.setItemStack(show);
        Transformation t = id.getTransformation();
        t.getScale().set(0.6f, 0.6f, 0.6f);
        id.setTransformation(t);
    }

    private <T extends Entity> T findEntity(Location l, Class<T> c, NamespacedKey k) {
        for (Entity e : l.getWorld().getNearbyEntities(l, 0.6, 2.0, 0.6)) if (c.isInstance(e) && e.getPersistentDataContainer().has(k, PersistentDataType.BYTE)) return c.cast(e);
        return null;
    }

    private TextDisplay findDisplay(Location b) { return findEntity(b.clone().add(0.5, 1.2, 0.5), TextDisplay.class, pedestalKey); }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.CHISELED_DEEPSLATE) return;
        Location l = event.getBlock().getLocation().add(0.5, 1.5, 0.5);
        for (Entity e : l.getWorld().getNearbyEntities(l, 0.8, 2.0, 0.8)) if (e.getPersistentDataContainer().has(pedestalKey, PersistentDataType.BYTE) || e.getPersistentDataContainer().has(itemDisplayKey, PersistentDataType.BYTE)) e.remove();
    }
}