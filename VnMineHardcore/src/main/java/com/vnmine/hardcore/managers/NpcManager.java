package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.yaml.snakeyaml.error.YAMLException;

public final class NpcManager implements Listener, CommandExecutor, TabCompleter {
    private static final String ADMIN_PERMISSION = "vnmine.command.npc.admin";
    private static final int HARD_MAX_NPCS = 100;
    private static final int SCHEMA_VERSION = 1;
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private final VnMineHardcore plugin;
    private final File storageFile;
    private final NamespacedKey npcIdKey;
    private final Map<String, NpcDefinitionLoader.NpcDefinition> definitions;
    private final Map<String, NpcRecord> npcs = new LinkedHashMap<>();
    private final Map<String, LinkedHashMap<Integer, MerchantListing>> merchantListings = new LinkedHashMap<>();
    private final int maxNpcs;

    public NpcManager(VnMineHardcore plugin,
                      Map<String, NpcDefinitionLoader.NpcDefinition> definitions)
            throws IOException, InvalidConfigurationException {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "npcs.yml");
        this.npcIdKey = new NamespacedKey(plugin, "npc_id");
        this.definitions = definitions;
        this.maxNpcs = configuredNpcLimit();
        loadNpcs();
        if (!storageFile.isFile()) saveNpcs();
        reconcileLoadedNpcs();
    }

    public boolean isManagedNpc(Entity entity) {
        return entity.getPersistentDataContainer().has(npcIdKey, PersistentDataType.STRING);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cBạn không có quyền quản lý NPC.");
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create" -> createNpc(sender, args);
                case "edit" -> editNpc(sender, args);
                case "rename" -> renameNpc(sender, args);
                case "move" -> moveNpc(sender, args);
                case "remove" -> removeNpc(sender, args);
                case "list" -> listNpcs(sender);
                case "info" -> showNpcInfo(sender, args);
                default -> sendUsage(sender);
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "[NPC] Could not save NPC data.", exception);
            sender.sendMessage("§cKhông thể lưu dữ liệu NPC. Kiểm tra console để biết chi tiết.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("create", "edit", "rename", "move", "remove", "list", "info"), args[0]);
        }
        if (args.length == 2 && List.of("edit", "rename", "move", "remove", "info")
                .contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(new ArrayList<>(npcs.keySet()), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            return filter(NpcDefinitionLoader.supportedTypes().stream().sorted().toList(), args[2]);
        }
        return List.of();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        reconcileChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        for (NpcRecord npc : List.copyOf(npcs.values())) {
            if (!npc.world().equals(event.getWorld().getName())) continue;
            int chunkX = chunkCoordinate(npc.x());
            int chunkZ = chunkCoordinate(npc.z());
            if (event.getWorld().isChunkLoaded(chunkX, chunkZ)) {
                reconcileChunk(event.getWorld().getChunkAt(chunkX, chunkZ));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isManagedNpc(event.getRightClicked())) return;
        event.setCancelled(true);
        String id = event.getRightClicked().getPersistentDataContainer()
            .get(npcIdKey, PersistentDataType.STRING);
        NpcRecord npc = id == null ? null : npcs.get(id);
        if (npc == null) {
            plugin.getLogger().warning("[NPC] Managed entity " + event.getRightClicked().getUniqueId()
                + " has no matching runtime record.");
            event.getPlayer().sendMessage("§cDeze NPC-gegevens zijn niet beschikbaar.");
            return;
        }
        if ("system-merchant".equals(npc.type())) {
            plugin.getEconomyManager().openMerchantMenu(event.getPlayer());
            return;
        }
        if ("merchant".equals(npc.type())) {
            Player player = event.getPlayer();
            if (player.isSneaking() && player.hasPermission(ADMIN_PERMISSION)) {
                openMerchantEditor(player, npc.id(), copyListings(merchantListings.get(npc.id())));
                return;
            }
            List<EconomyManager.CustomMerchantOffer> offers = merchantListings
                .getOrDefault(npc.id(), new LinkedHashMap<>()).values().stream()
                .map(listing -> new EconomyManager.CustomMerchantOffer(listing.item().clone(), listing.price()))
                .toList();
            plugin.getEconomyManager().openCustomMerchantMenu(player, npc.id(), dialogueTitle(npc.displayName()), offers);
            return;
        }
        NpcDefinitionLoader.NpcDefinition definition = findDialogueDefinition(id, npc.type(), definitions);
        if (definition == null) {
            event.getPlayer().sendMessage("§7Deze NPC heeft nog geen dialoog in npc-definitions.yml.");
            return;
        }
        openDialogue(event.getPlayer(), npc, definition);
    }

    @EventHandler
    public void onDialogueClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof DialogueHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.viewer.equals(player.getUniqueId())
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getRawSlot() == 49) {
            player.closeInventory();
            return;
        }
        NpcDefinitionLoader.NpcAction action = holder.actions.get(event.getRawSlot());
        if (action == null) return;
        player.closeInventory();
        switch (action.type()) {
            case "open-skill-tree" -> plugin.getSkillManager().openSkillTreeFromNpc(player);
            case "open-quest-journal" -> plugin.getTutorialQuestManager().openJournal(player);
            case "open-story" -> plugin.getMercenaryManager().openStoryJournal(player);
            case "open-merchant" -> plugin.getEconomyManager().openMerchantMenu(player);
            case "give-skill-book" -> plugin.getSkillManager().giveSkillBookFromNpc(player, action.bookId());
            default -> plugin.getLogger().warning("[NPC] Unsupported runtime action '" + action.type() + "'.");
        }
    }

    @EventHandler
    public void onDialogueDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof DialogueHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMerchantEditorClick(InventoryClickEvent event) {
        InventoryHolder rawHolder = event.getView().getTopInventory().getHolder();
        if (!(rawHolder instanceof MerchantEditorHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.viewer.equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack source = event.getCurrentItem();
                if (source != null && isSystemCreatedItem(source)) {
                    int slot = firstEmptyShopSlot(holder.draft);
                    if (slot < 0) {
                        player.sendMessage("§cCửa hàng đã đủ " + configuredMerchantMaxOffers() + " mặt hàng.");
                        return;
                    }
                    holder.draft.put(slot, new MerchantListing(source.clone(), 1L));
                    event.setCurrentItem(null);
                    openMerchantEditor(player, holder.merchantId, holder.draft);
                } else {
                    player.sendMessage("§cChỉ có thể thêm vật phẩm RPG do hệ thống tạo.");
                }
            }
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 45) {
            saveMerchantDraft(player, holder.merchantId, holder.draft);
            return;
        }
        if (slot == 53) {
            player.closeInventory();
            return;
        }
        if (slot < 0 || slot >= 45) return;
        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            if (!isSystemCreatedItem(cursor)) {
                player.sendMessage("§cChỉ có thể đặt vật phẩm RPG do hệ thống tạo.");
                return;
            }
            long price = holder.draft.containsKey(slot) ? holder.draft.get(slot).price() : 1L;
            holder.draft.put(slot, new MerchantListing(cursor.clone(), price));
            event.setCursor(null);
            openMerchantEditor(player, holder.merchantId, holder.draft);
            return;
        }
        MerchantListing listing = holder.draft.get(slot);
        if (listing == null) return;
        if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
            holder.draft.remove(slot);
            openMerchantEditor(player, holder.merchantId, holder.draft);
            return;
        }
        openMerchantPriceEditor(player, holder.merchantId, holder.draft, slot);
    }

    @EventHandler
    public void onMerchantPriceClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MerchantPriceHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.viewer.equals(player.getUniqueId())
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        MerchantListing listing = holder.draft.get(holder.slot);
        if (listing == null) {
            openMerchantEditor(player, holder.merchantId, holder.draft);
            return;
        }
        long delta = switch (event.getRawSlot()) {
            case 10 -> -configuredMerchantPriceStep(2);
            case 11 -> -configuredMerchantPriceStep(1);
            case 12 -> -configuredMerchantPriceStep(0);
            case 14 -> configuredMerchantPriceStep(0);
            case 15 -> configuredMerchantPriceStep(1);
            case 16 -> configuredMerchantPriceStep(2);
            default -> 0L;
        };
        if (delta != 0) {
            long maxPrice = configuredMerchantMaxPrice();
            long updated = Math.max(1L, Math.min(maxPrice, listing.price() + delta));
            holder.draft.put(holder.slot, new MerchantListing(listing.item(), updated));
            openMerchantPriceEditor(player, holder.merchantId, holder.draft, holder.slot);
            return;
        }
        if (event.getRawSlot() == 22) {
            openMerchantEditor(player, holder.merchantId, holder.draft);
        } else if (event.getRawSlot() == 18) {
            openMerchantEditor(player, holder.merchantId, holder.draft);
        }
    }

    @EventHandler
    public void onMerchantEditorDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof MerchantEditorHolder) && !(holder instanceof MerchantPriceHolder)) return;
        if (event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())) {
            event.setCancelled(true);
        }
    }

    private void openMerchantEditor(Player player, String merchantId,
                                    LinkedHashMap<Integer, MerchantListing> draft) {
        MerchantEditorHolder holder = new MerchantEditorHolder(player.getUniqueId(), merchantId, draft);
        Inventory inventory = Bukkit.createInventory(holder, 54, "VnMine • Sửa shop " + merchantId);
        holder.inventory = inventory;
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, createDialogueItem(Material.GRAY_STAINED_GLASS_PANE, "§r", List.of()));
        }
        for (Map.Entry<Integer, MerchantListing> entry : draft.entrySet()) {
            ItemStack display = entry.getValue().item().clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() && meta.getLore() != null
                    ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("§7Giá hiện tại: §6" + entry.getValue().price() + " VNCOIN");
                lore.add("§eChuột trái: sửa giá | Chuột phải: gỡ món");
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            inventory.setItem(entry.getKey(), display);
        }
        inventory.setItem(45, createDialogueItem(Material.EMERALD, "§aLưu cửa hàng",
            List.of("§7Lưu danh sách và giá vào npcs.yml.")));
        inventory.setItem(49, createDialogueItem(Material.BOOK, "§eChỉnh sửa shop",
            List.of("§7Shift-click item RPG trong túi để thêm.",
                "§7Chuột phải món trong shop để gỡ.",
                "§7Chuột trái món trong shop để chỉnh giá.",
                "§7Giá: " + configuredMerchantPriceStep(0) + ", "
                    + configuredMerchantPriceStep(1) + ", " + configuredMerchantPriceStep(2) + " VNCOIN.")));
        inventory.setItem(53, createDialogueItem(Material.BARRIER, "§cĐóng", List.of()));
        player.openInventory(inventory);
    }

    private void openMerchantPriceEditor(Player player, String merchantId,
                                         LinkedHashMap<Integer, MerchantListing> draft, int slot) {
        MerchantListing listing = draft.get(slot);
        if (listing == null) {
            openMerchantEditor(player, merchantId, draft);
            return;
        }
        MerchantPriceHolder holder = new MerchantPriceHolder(player.getUniqueId(), merchantId, draft, slot);
        Inventory inventory = Bukkit.createInventory(holder, 27, "VnMine • Giá VNCOIN");
        holder.inventory = inventory;
        ItemStack item = listing.item().clone();
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta != null) {
            List<String> lore = itemMeta.hasLore() && itemMeta.getLore() != null
                ? new ArrayList<>(itemMeta.getLore()) : new ArrayList<>();
            lore.add("§7Giá: §6" + listing.price() + " VNCOIN");
            itemMeta.setLore(lore);
            item.setItemMeta(itemMeta);
        }
        inventory.setItem(13, item);
        inventory.setItem(10, priceButton(Material.RED_DYE, "-" + configuredMerchantPriceStep(2)));
        inventory.setItem(11, priceButton(Material.RED_DYE, "-" + configuredMerchantPriceStep(1)));
        inventory.setItem(12, priceButton(Material.RED_DYE, "-" + configuredMerchantPriceStep(0)));
        inventory.setItem(14, priceButton(Material.LIME_DYE, "+" + configuredMerchantPriceStep(0)));
        inventory.setItem(15, priceButton(Material.LIME_DYE, "+" + configuredMerchantPriceStep(1)));
        inventory.setItem(16, priceButton(Material.LIME_DYE, "+" + configuredMerchantPriceStep(2)));
        inventory.setItem(18, createDialogueItem(Material.ARROW, "§7Quay lại", List.of()));
        inventory.setItem(22, createDialogueItem(Material.EMERALD, "§aXong, quay lại lưu shop",
            List.of("§7Giá chỉ được ghi khi bấm Lưu cửa hàng.")));
        player.openInventory(inventory);
    }

    private ItemStack priceButton(Material material, String increment) {
        return createDialogueItem(material, (increment.startsWith("+") ? "§a" : "§c") + increment + " VNCOIN",
            List.of("§7Giá được giới hạn trong khoảng 1-" + configuredMerchantMaxPrice() + "."));
    }

    private void saveMerchantDraft(Player player, String merchantId,
                                   LinkedHashMap<Integer, MerchantListing> draft) {
        LinkedHashMap<Integer, MerchantListing> previous = merchantListings.get(merchantId);
        if (draft.isEmpty()) merchantListings.remove(merchantId);
        else merchantListings.put(merchantId, copyListings(draft));
        try {
            saveNpcs();
            player.sendMessage("§aĐã lưu cửa hàng của NPC §f" + merchantId + "§a ("
                + draft.size() + " mặt hàng).");
            player.closeInventory();
            plugin.getLogger().info("[NPC][Merchant] Saved shop: npc=" + merchantId
                + ", editor=" + player.getName() + ", offers=" + draft.size());
        } catch (IOException exception) {
            if (previous == null) merchantListings.remove(merchantId);
            else merchantListings.put(merchantId, previous);
            plugin.getLogger().log(Level.SEVERE, "[NPC][Merchant] Could not save shop " + merchantId, exception);
            player.sendMessage("§cKhông thể lưu cửa hàng; dữ liệu cũ vẫn được giữ.");
        }
    }

    private LinkedHashMap<Integer, MerchantListing> copyListings(Map<Integer, MerchantListing> source) {
        LinkedHashMap<Integer, MerchantListing> copy = new LinkedHashMap<>();
        if (source != null) source.forEach((slot, listing) ->
            copy.put(slot, new MerchantListing(listing.item().clone(), listing.price())));
        return copy;
    }

    private int firstEmptyShopSlot(Map<Integer, MerchantListing> draft) {
        int max = configuredMerchantMaxOffers();
        for (int slot = 0; slot < max; slot++) if (!draft.containsKey(slot)) return slot;
        return -1;
    }

    private boolean isSystemCreatedItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().getKeys().stream()
            .anyMatch(key -> key.getNamespace().equalsIgnoreCase(plugin.getName().toLowerCase(Locale.ROOT))
                && !key.getKey().startsWith("vncoin_") && !key.getKey().equals("merchant_offer_id"));
    }

    private int configuredMerchantMaxOffers() {
        int configured = plugin.getConfig().getInt("economy.merchant.custom.max-offers", 45);
        return configured >= 1 && configured <= 45 ? configured : 45;
    }

    private long configuredMerchantMaxPrice() {
        long configured = plugin.getConfig().getLong("economy.merchant.custom.max-price", 1_000_000L);
        return configured >= 1 && configured <= PlayerProfileDatabaseManager.MAX_WALLET_BALANCE
            ? configured : 1_000_000L;
    }

    private int configuredMerchantPriceStep(int index) {
        List<Integer> configured = plugin.getConfig().getIntegerList("economy.merchant.custom.price-steps");
        int fallback = switch (index) { case 0 -> 1; case 1 -> 10; default -> 100; };
        if (configured.size() != 3) return fallback;
        int step = configured.get(index);
        return step >= 1 && step <= 1_000_000 ? step : fallback;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNpcDamage(EntityDamageEvent event) {
        if (isManagedNpc(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNpcCombust(EntityCombustEvent event) {
        if (isManagedNpc(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNpcTransform(EntityTransformEvent event) {
        if (isManagedNpc(event.getEntity())) event.setCancelled(true);
    }

    static boolean isValidId(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    static NpcDefinitionLoader.NpcDefinition findDialogueDefinition(
            String npcId, String type, Map<String, NpcDefinitionLoader.NpcDefinition> definitions) {
        NpcDefinitionLoader.NpcDefinition exact = definitions.get(npcId);
        if (exact != null && exact.type().equals(type)) return exact;
        return definitions.values().stream()
            .filter(definition -> definition.type().equals(type))
            .findFirst()
            .orElse(null);
    }

    private void openDialogue(Player player, NpcRecord npc, NpcDefinitionLoader.NpcDefinition definition) {
        DialogueHolder holder = new DialogueHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, 54, dialogueTitle(npc.displayName()));
        holder.inventory = inventory;

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot,
                createDialogueItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        }

        inventory.setItem(4, createDialogueItem(Material.WRITABLE_BOOK,
            formatDisplayName(npc.displayName()), List.of("§7" + titleCase(npc.type()))));
        int[] dialogueSlots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
        };
        for (int index = 0; index < definition.dialogue().size(); index++) {
            String line = definition.dialogue().get(index);
            inventory.setItem(dialogueSlots[index], createDialogueItem(
                Material.PAPER, "§eLời thoại " + (index + 1),
                List.of(formatDisplayName(line), "§8" + (index + 1) + "/" + definition.dialogue().size())));
        }
        int[] actionSlots = {39, 41, 43, 45};
        for (int index = 0; index < definition.actions().size(); index++) {
            NpcDefinitionLoader.NpcAction action = definition.actions().get(index);
            int slot = actionSlots[index];
            Material material = switch (action.type()) {
                case "give-skill-book" -> Material.ENCHANTED_BOOK;
                case "open-skill-tree" -> Material.IRON_SWORD;
                case "open-merchant" -> Material.EMERALD;
                default -> Material.WRITABLE_BOOK;
            };
            inventory.setItem(slot, createDialogueItem(material, "§a" + formatDisplayName(action.label()),
                List.of("§7Nhấn để thực hiện.")));
            holder.actions.put(slot, action);
        }
        inventory.setItem(49, createDialogueItem(Material.BARRIER, "§cĐóng", List.of("§7Đóng hội thoại.")));
        player.openInventory(inventory);
    }

    private static ItemStack createDialogueItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String dialogueTitle(String displayName) {
        String plainName = ChatColor.stripColor(formatDisplayName(displayName));
        if (plainName.length() > 32) plainName = plainName.substring(0, 32);
        return "§6" + plainName;
    }

    private void createNpc(CommandSender sender, String[] args) throws IOException {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cLệnh này chỉ dùng được trong game.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("§eDùng: /vnnpc create <id> <type> [tên hiển thị]");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        String type = args[2].toLowerCase(Locale.ROOT);
        if (!isValidId(id)) {
            sender.sendMessage("§cID chỉ gồm chữ thường, số và dấu gạch nối đơn.");
            return;
        }
        if (npcs.containsKey(id)) {
            sender.sendMessage("§cĐã tồn tại NPC có ID " + id + ".");
            return;
        }
        if (!NpcDefinitionLoader.supportedTypes().contains(type)) {
            sender.sendMessage("§cLoại NPC không hợp lệ. Loại hỗ trợ: "
                + String.join(", ", NpcDefinitionLoader.supportedTypes()) + ".");
            return;
        }
        if (npcs.size() >= maxNpcs) {
            sender.sendMessage("§cĐã đạt giới hạn " + maxNpcs + " NPC trong cấu hình.");
            return;
        }
        String displayName = args.length >= 4 ? joinArguments(args, 3) : defaultName(type);
        if (!isValidDisplayName(displayName)) {
            sender.sendMessage("§cTên hiển thị phải có từ 1 đến 64 ký tự.");
            return;
        }

        Location location = player.getLocation();
        location.getWorld().getChunkAt(location.getBlockX() >> 4, location.getBlockZ() >> 4);
        Villager entity = spawnNpc(id, displayName, location);
        npcs.put(id, new NpcRecord(id, type, displayName, location, entity.getUniqueId()));
        try {
            saveNpcs();
        } catch (IOException exception) {
            npcs.remove(id);
            entity.remove();
            throw exception;
        }
        sender.sendMessage("§aĐã tạo NPC §f" + id + " §atại vị trí hiện tại.");
    }

    private void editNpc(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cLệnh này chỉ dùng được trong game.");
            return;
        }
        if (args.length != 2) {
            sender.sendMessage("§eDùng: /vnnpc edit <id>");
            return;
        }
        NpcRecord npc = npcs.get(args[1].toLowerCase(Locale.ROOT));
        if (npc == null) {
            sender.sendMessage("§cKhông tìm thấy NPC " + args[1] + ".");
            return;
        }
        if (!"merchant".equals(npc.type())) {
            sender.sendMessage("§cChỉ NPC merchant tùy chỉnh mới có thể chỉnh danh sách hàng hóa.");
            return;
        }
        if (!plugin.getConfig().getBoolean("economy.merchant.custom.enabled", true)) {
            sender.sendMessage("§cCửa hàng NPC tùy chỉnh hiện đang tắt trong cấu hình.");
            return;
        }
        openMerchantEditor(player, npc.id(), copyListings(merchantListings.get(npc.id())));
    }

    private void renameNpc(CommandSender sender, String[] args) throws IOException {
        if (args.length < 3) {
            sender.sendMessage("§eDùng: /vnnpc rename <id> <tên hiển thị>");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        NpcRecord current = npcs.get(id);
        if (current == null) {
            sender.sendMessage("§cKhông tìm thấy NPC " + id + ".");
            return;
        }
        String displayName = joinArguments(args, 2);
        if (!isValidDisplayName(displayName)) {
            sender.sendMessage("§cTên hiển thị phải có từ 1 đến 64 ký tự.");
            return;
        }
        Entity entity = findNpcEntity(current);
        NpcRecord renamed = current.withDisplayName(displayName);
        npcs.put(id, renamed);
        if (entity != null) applyNpcState(entity, renamed);
        try {
            saveNpcs();
        } catch (IOException exception) {
            npcs.put(id, current);
            if (entity != null) applyNpcState(entity, current);
            throw exception;
        }
        sender.sendMessage("§aĐã đổi tên NPC §f" + id + " §athành " + displayName + ".");
    }

    private void moveNpc(CommandSender sender, String[] args) throws IOException {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cLệnh này chỉ dùng được trong game.");
            return;
        }
        if (args.length != 2) {
            sender.sendMessage("§eDùng: /vnnpc move <id>");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        NpcRecord current = npcs.get(id);
        if (current == null) {
            sender.sendMessage("§cKhông tìm thấy NPC " + id + ".");
            return;
        }
        Entity entity = findNpcEntity(current);
        Location destination = player.getLocation();
        destination.getWorld().getChunkAt(destination.getBlockX() >> 4, destination.getBlockZ() >> 4);
        if (entity == null) {
            Villager replacement = spawnNpc(id, current.displayName(), destination);
            npcs.put(id, current.withLocation(destination, replacement.getUniqueId()));
            try {
                saveNpcs();
            } catch (IOException exception) {
                npcs.put(id, current);
                replacement.remove();
                throw exception;
            }
            sender.sendMessage("§aĐã chuyển NPC §f" + id + " §ađến vị trí hiện tại.");
            return;
        }
        Location previousLocation = entity.getLocation();
        NpcRecord moved = current.withLocation(destination, entity.getUniqueId());
        npcs.put(id, moved);
        if (!entity.teleport(destination)) {
            npcs.put(id, current);
            sender.sendMessage("§cKhông thể di chuyển NPC tới vị trí mới.");
            return;
        }
        try {
            saveNpcs();
        } catch (IOException exception) {
            npcs.put(id, current);
            entity.teleport(previousLocation);
            throw exception;
        }
        sender.sendMessage("§aĐã chuyển NPC §f" + id + " §ađến vị trí hiện tại.");
    }

    private void removeNpc(CommandSender sender, String[] args) throws IOException {
        if (args.length != 2) {
            sender.sendMessage("§eDùng: /vnnpc remove <id>");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        NpcRecord current = npcs.get(id);
        if (current == null) {
            sender.sendMessage("§cKhông tìm thấy NPC " + id + ".");
            return;
        }
        Entity entity = findNpcEntity(current);
        npcs.remove(id);
        LinkedHashMap<Integer, MerchantListing> oldListings = merchantListings.remove(id);
        try {
            saveNpcs();
        } catch (IOException exception) {
            npcs.put(id, current);
            if (oldListings != null) merchantListings.put(id, oldListings);
            throw exception;
        }
        if (entity != null) entity.remove();
        sender.sendMessage("§aĐã xóa NPC §f" + id + ".");
    }

    private void listNpcs(CommandSender sender) {
        if (npcs.isEmpty()) {
            sender.sendMessage("§7Chưa có NPC nào được tạo.");
            return;
        }
        sender.sendMessage("§6NPC đã tạo (" + npcs.size() + "/" + maxNpcs + "):");
        npcs.values().stream().sorted(Comparator.comparing(NpcRecord::id))
            .forEach(npc -> sender.sendMessage("§e" + npc.id() + " §7(" + npc.type() + "): §f"
                + ChatColor.stripColor(formatDisplayName(npc.displayName()))));
    }

    private void showNpcInfo(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("§eDùng: /vnnpc info <id>");
            return;
        }
        NpcRecord npc = npcs.get(args[1].toLowerCase(Locale.ROOT));
        if (npc == null) {
            sender.sendMessage("§cKhông tìm thấy NPC " + args[1] + ".");
            return;
        }
        sender.sendMessage("§6NPC §e" + npc.id() + " §7(" + npc.type() + ")");
        sender.sendMessage("§7Tên: §f" + formatDisplayName(npc.displayName()));
        sender.sendMessage("§7Vị trí: §f" + npc.world() + " " + npc.x() + ", "
            + npc.y() + ", " + npc.z());
    }

    private void loadNpcs() throws IOException, InvalidConfigurationException {
        if (!storageFile.isFile()) return;
        YamlDuplicateKeyValidator.validateNoDuplicateKeys(storageFile);
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(storageFile);
        } catch (YAMLException exception) {
            throw new InvalidConfigurationException("Invalid NPC data YAML.", exception);
        }
        Object rawVersion = configuration.get("schema-version");
        if (!(rawVersion instanceof Number version) || version.intValue() != SCHEMA_VERSION
                || version.doubleValue() != version.intValue()) {
            throw new InvalidConfigurationException("Unsupported or missing NPC data schema-version.");
        }
        ConfigurationSection section = configuration.getConfigurationSection("npcs");
        if (section == null) {
            throw new InvalidConfigurationException("Missing 'npcs' section in " + storageFile.getName() + ".");
        }
        if (section.getKeys(false).size() > HARD_MAX_NPCS) {
            throw new InvalidConfigurationException("NPC data exceeds the hard limit of " + HARD_MAX_NPCS + ".");
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (!isValidId(id) || entry == null) {
                throw new InvalidConfigurationException("Invalid NPC entry '" + id + "'.");
            }
            String type = requiredString(entry, "type", id);
            String displayName = requiredString(entry, "display-name", id);
            String world = requiredString(entry, "world", id);
            if (!NpcDefinitionLoader.supportedTypes().contains(type) || !isValidDisplayName(displayName)) {
                throw new InvalidConfigurationException("Invalid NPC type or display-name for '" + id + "'.");
            }
            double x = finiteNumber(entry, "x", id);
            double y = finiteNumber(entry, "y", id);
            double z = finiteNumber(entry, "z", id);
            float yaw = (float) finiteNumber(entry, "yaw", id);
            float pitch = (float) finiteNumber(entry, "pitch", id);
            UUID entityId = null;
            Object rawEntityId = entry.get("entity-uuid");
            if (rawEntityId != null) {
                if (!(rawEntityId instanceof String entityIdText) || entityIdText.isBlank()) {
                    throw new InvalidConfigurationException("Invalid entity-uuid for NPC '" + id + "'.");
                }
                try {
                    entityId = UUID.fromString(entityIdText);
                } catch (IllegalArgumentException exception) {
                    throw new InvalidConfigurationException("Invalid entity-uuid for NPC '" + id + "'.", exception);
                }
            }
            npcs.put(id, new NpcRecord(id, type, displayName,
                new Location(null, x, y, z, yaw, pitch), entityId, world));
            ConfigurationSection savedListings = entry.getConfigurationSection("merchant-offers");
            if (savedListings != null) {
                if (!"merchant".equals(type)) {
                    throw new InvalidConfigurationException("Only custom merchant NPC '" + id
                        + "' may contain merchant-offers.");
                }
                LinkedHashMap<Integer, MerchantListing> listings = new LinkedHashMap<>();
                for (String rawSlot : savedListings.getKeys(false)) {
                    int slot;
                    try {
                        slot = Integer.parseInt(rawSlot);
                    } catch (NumberFormatException exception) {
                        throw new InvalidConfigurationException("Invalid merchant slot '" + rawSlot
                            + "' for NPC '" + id + "'.", exception);
                    }
                    ConfigurationSection offer = savedListings.getConfigurationSection(rawSlot);
                    ItemStack item = offer == null ? null : offer.getItemStack("item");
                    long price = offer == null ? 0L : offer.getLong("price", 0L);
                    if (slot < 0 || slot >= 45 || !isSystemCreatedItem(item)
                            || price < 1 || price > PlayerProfileDatabaseManager.MAX_WALLET_BALANCE) {
                        throw new InvalidConfigurationException("Invalid merchant offer at NPC '" + id
                            + "', slot " + rawSlot + ".");
                    }
                    listings.put(slot, new MerchantListing(item.clone(), price));
                }
                merchantListings.put(id, listings);
            }
        }
    }

    private void saveNpcs() throws IOException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("schema-version", SCHEMA_VERSION);
        configuration.createSection("npcs");
        for (NpcRecord npc : npcs.values()) {
            String path = "npcs." + npc.id();
            configuration.set(path + ".type", npc.type());
            configuration.set(path + ".display-name", npc.displayName());
            configuration.set(path + ".world", npc.world());
            configuration.set(path + ".x", npc.x());
            configuration.set(path + ".y", npc.y());
            configuration.set(path + ".z", npc.z());
            configuration.set(path + ".yaw", npc.yaw());
            configuration.set(path + ".pitch", npc.pitch());
            configuration.set(path + ".entity-uuid", npc.entityId() == null ? null : npc.entityId().toString());
            Map<Integer, MerchantListing> listings = merchantListings.get(npc.id());
            if (listings != null) {
                for (Map.Entry<Integer, MerchantListing> offer : listings.entrySet()) {
                    String offerPath = path + ".merchant-offers." + offer.getKey();
                    configuration.set(offerPath + ".item", offer.getValue().item().clone());
                    configuration.set(offerPath + ".price", offer.getValue().price());
                }
            }
        }

        File parent = storageFile.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create NPC data directory.");
        }
        File temporary = Files.createTempFile(parent.toPath(), "npcs-", ".yml.tmp").toFile();
        try {
            Files.writeString(temporary.toPath(), configuration.saveToString());
            try {
                Files.move(temporary.toPath(), storageFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), storageFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary.toPath());
        }
    }

    private void reconcileLoadedNpcs() {
        for (NpcRecord npc : List.copyOf(npcs.values())) {
            World world = Bukkit.getWorld(npc.world());
            if (world == null) {
                plugin.getLogger().warning("[NPC] World '" + npc.world()
                    + "' for NPC '" + npc.id() + "' is not loaded; it will spawn when the world loads.");
                continue;
            }
            int chunkX = chunkCoordinate(npc.x());
            int chunkZ = chunkCoordinate(npc.z());
            if (world.isChunkLoaded(chunkX, chunkZ)) {
                reconcileChunk(world.getChunkAt(chunkX, chunkZ));
            }
        }
    }

    private void reconcileChunk(Chunk chunk) {
        Map<String, Entity> found = new LinkedHashMap<>();
        for (Entity entity : chunk.getEntities()) {
            String id = entity.getPersistentDataContainer().get(npcIdKey, PersistentDataType.STRING);
            if (id == null) continue;
            NpcRecord record = npcs.get(id);
            if (!(entity instanceof Villager) || record == null || !isInChunk(record, chunk)
                    || (record.entityId() != null && !record.entityId().equals(entity.getUniqueId()))
                    || found.containsKey(id)) {
                entity.remove();
                continue;
            }
            found.put(id, entity);
        }

        boolean changed = false;
        for (NpcRecord record : List.copyOf(npcs.values())) {
            if (!isInChunk(record, chunk)) continue;
            Entity entity = found.get(record.id());
            if (entity == null) {
                World world = chunk.getWorld();
                Location location = toLocation(record, world);
                Villager spawned = spawnNpc(record.id(), record.displayName(), location);
                entity = spawned;
            } else {
                applyNpcState(entity, record);
            }
            if (!entity.getUniqueId().equals(record.entityId())) {
                npcs.put(record.id(), record.withEntityId(entity.getUniqueId()));
                changed = true;
            }
        }
        if (changed) {
            try {
                saveNpcs();
            } catch (IOException exception) {
                plugin.getLogger().log(Level.SEVERE, "[NPC] Could not save reconciled NPC data.", exception);
            }
        }
    }

    private Entity findNpcEntity(NpcRecord record) {
        World world = Bukkit.getWorld(record.world());
        if (world == null) return null;
        Chunk chunk = world.getChunkAt(chunkCoordinate(record.x()), chunkCoordinate(record.z()));
        reconcileChunk(chunk);
        for (Entity entity : chunk.getEntities()) {
            if (entity.getUniqueId().equals(npcs.get(record.id()).entityId())) return entity;
        }
        return null;
    }

    private Villager spawnNpc(String id, String displayName, Location location) {
        Villager villager = (Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);
        NpcRecord temporary = new NpcRecord(id, "tutorial", displayName, location, villager.getUniqueId());
        applyNpcState(villager, temporary);
        return villager;
    }

    private void applyNpcState(Entity entity, NpcRecord npc) {
        entity.getPersistentDataContainer().set(npcIdKey, PersistentDataType.STRING, npc.id());
        entity.setCustomName(formatDisplayName(npc.displayName()));
        entity.setCustomNameVisible(true);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setPersistent(true);
        if (entity instanceof Villager villager) {
            villager.setAI(false);
            villager.setAware(false);
            villager.setCanPickupItems(false);
            villager.setCollidable(false);
            villager.setAdult();
            villager.setProfession(Villager.Profession.NONE);
        }
    }

    private String defaultName(String type) {
        return definitions.values().stream()
            .filter(definition -> definition.type().equals(type))
            .map(NpcDefinitionLoader.NpcDefinition::displayName)
            .findFirst()
            .orElseGet(() -> titleCase(type));
    }

    private int configuredNpcLimit() {
        int configured = plugin.getConfig().getInt("rpg.npc.max-runtime-npcs", HARD_MAX_NPCS);
        if (configured < 1 || configured > HARD_MAX_NPCS) {
            plugin.getLogger().warning("[NPC] rpg.npc.max-runtime-npcs must be 1-"
                + HARD_MAX_NPCS + "; using " + HARD_MAX_NPCS + ".");
            return HARD_MAX_NPCS;
        }
        return configured;
    }

    private static String requiredString(ConfigurationSection section, String key, String id)
            throws InvalidConfigurationException {
        String value = section.getString(key);
        if (value == null || value.isBlank()) {
            throw new InvalidConfigurationException("Missing or invalid '" + key + "' for NPC '" + id + "'.");
        }
        return value.trim();
    }

    private static double finiteNumber(ConfigurationSection section, String key, String id)
            throws InvalidConfigurationException {
        Object value = section.get(key);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new InvalidConfigurationException("Missing or invalid '" + key + "' for NPC '" + id + "'.");
        }
        return number.doubleValue();
    }

    private static boolean isValidDisplayName(String displayName) {
        return displayName != null && !displayName.isBlank() && displayName.length() <= 64;
    }

    private static int chunkCoordinate(double coordinate) {
        return ((int) Math.floor(coordinate)) >> 4;
    }

    private static boolean isInChunk(NpcRecord record, Chunk chunk) {
        return record.world().equals(chunk.getWorld().getName())
            && chunkCoordinate(record.x()) == chunk.getX()
            && chunkCoordinate(record.z()) == chunk.getZ();
    }

    private static Location toLocation(NpcRecord record, World world) {
        return new Location(world, record.x(), record.y(), record.z(), record.yaw(), record.pitch());
    }

    private static String formatDisplayName(String displayName) {
        return EconomyManager.sanitizeVisibleName(ChatColor.translateAlternateColorCodes('&', displayName));
    }

    private static String titleCase(String value) {
        String[] segments = value.split("-");
        for (int index = 0; index < segments.length; index++) {
            String segment = segments[index];
            if (!segment.isEmpty()) {
                segments[index] = segment.substring(0, 1).toUpperCase(Locale.ROOT) + segment.substring(1);
            }
        }
        return String.join(" ", segments);
    }

    private static String joinArguments(String[] args, int fromIndex) {
        return String.join(" ", java.util.Arrays.copyOfRange(args, fromIndex, args.length));
    }

    private static List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }

    private static void sendUsage(CommandSender sender) {
        sender.sendMessage("§e/vnnpc create <id> <type> [tên hiển thị] §7(tạo tại vị trí hiện tại)");
        sender.sendMessage("§e/vnnpc edit <id> §7(chỉnh cửa hàng của merchant tùy chỉnh)");
        sender.sendMessage("§e/vnnpc rename <id> <tên> §7| §e/vnnpc move <id>");
        sender.sendMessage("§e/vnnpc info <id> §7| §e/vnnpc list §7| §e/vnnpc remove <id>");
    }

    private record NpcRecord(String id, String type, String displayName, Location location,
                             UUID entityId, String world) {
        private NpcRecord(String id, String type, String displayName, Location location, UUID entityId) {
            this(id, type, displayName, location.clone(), entityId, location.getWorld().getName());
        }

        private NpcRecord(String id, String type, String displayName, Location location,
                          UUID entityId, String world) {
            this.id = id;
            this.type = type;
            this.displayName = displayName;
            this.location = location.clone();
            this.entityId = entityId;
            this.world = world;
        }

        private double x() {
            return location.getX();
        }

        private double y() {
            return location.getY();
        }

        private double z() {
            return location.getZ();
        }

        private float yaw() {
            return location.getYaw();
        }

        private float pitch() {
            return location.getPitch();
        }

        private NpcRecord withDisplayName(String name) {
            return new NpcRecord(id, type, name, location, entityId, world);
        }

        private NpcRecord withLocation(Location newLocation, UUID newEntityId) {
            return new NpcRecord(id, type, displayName, newLocation, newEntityId);
        }

        private NpcRecord withEntityId(UUID newEntityId) {
            return new NpcRecord(id, type, displayName, location, newEntityId, world);
        }
    }

    private record MerchantListing(ItemStack item, long price) {
        private MerchantListing {
            item = item.clone();
        }
    }

    private static final class DialogueHolder implements InventoryHolder {
        private final UUID viewer;
        private final Map<Integer, NpcDefinitionLoader.NpcAction> actions = new LinkedHashMap<>();
        private Inventory inventory;

        private DialogueHolder(UUID viewer) {
            this.viewer = viewer;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class MerchantEditorHolder implements InventoryHolder {
        private final UUID viewer;
        private final String merchantId;
        private final LinkedHashMap<Integer, MerchantListing> draft;
        private Inventory inventory;

        private MerchantEditorHolder(UUID viewer, String merchantId,
                                     LinkedHashMap<Integer, MerchantListing> draft) {
            this.viewer = viewer;
            this.merchantId = merchantId;
            this.draft = draft;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class MerchantPriceHolder implements InventoryHolder {
        private final UUID viewer;
        private final String merchantId;
        private final LinkedHashMap<Integer, MerchantListing> draft;
        private final int slot;
        private Inventory inventory;

        private MerchantPriceHolder(UUID viewer, String merchantId,
                                    LinkedHashMap<Integer, MerchantListing> draft, int slot) {
            this.viewer = viewer;
            this.merchantId = merchantId;
            this.draft = draft;
            this.slot = slot;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
