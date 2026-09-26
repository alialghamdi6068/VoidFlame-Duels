package net.voidflame.duels;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LobbyItemsManager implements Listener {
    private static final String KIT_MENU = "§8⚔・𝗗𝘂𝗲𝗹𝘀";
    private static final String PARTY_MENU = "§8➕・𝗣𝗮𝗿𝘁𝘆";

    private final VoidFlameDuelsPlugin plugin;
    private final NamespacedKey lobbyItemKey;
    private final Map<UUID, KitType> selectedKits = new ConcurrentHashMap<>();

    public LobbyItemsManager(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
        this.lobbyItemKey = new NamespacedKey(plugin, "lobby-item");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> giveLobbyItems(event.getPlayer()));
    }

    public void giveLobbyItems(Player player) {
        if (plugin.matchManager().isInMatch(player.getUniqueId())
                || plugin.matchManager().isDisconnected(player.getUniqueId())
                || plugin.spectatorManager().isSpectating(player.getUniqueId())) return;
        PlayerInventoryAccess.clearAndPlace(player, lobbyItemKey);
        player.updateInventory();
    }

    public void openKitMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 45, KIT_MENU);
        fill(inv);
        int slot = 10;
        for (KitType kit : KitType.values()) {
            if (slot >= 29) break;
            Material icon = switch (kit) {
                case SWORD -> Material.IRON_SWORD;
                case AXE -> Material.NETHERITE_AXE;
                case UHC -> Material.GOLDEN_APPLE;
                case MACE -> Material.MACE;
                case SPEAR_MACE -> Material.TRIDENT;
                case CRYSTAL -> Material.END_CRYSTAL;
                case NETHERITE_OP -> Material.NETHERITE_CHESTPLATE;
            };
            button(inv, slot++, icon, "§b" + pretty(kit), "§7اضغط لاختيار هذا الكيت");
        }
        button(inv, 31, Material.BARRIER, "§c✕・𝗖𝗹𝗼𝘀𝗲", "§7إغلاق القائمة");
        player.openInventory(inv);
    }

    public void openPartyMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 45, PARTY_MENU);
        fill(inv);
        button(inv, 10, Material.DIAMOND_SWORD, "§b⚔・𝗣𝗮𝗿𝘁𝘆 𝟭𝘃𝟭", "§7مباراة لاعب ضد لاعب من البارتي");
        button(inv, 13, Material.IRON_SWORD, "§a⚔・𝗣𝗮𝗿𝘁𝘆 𝟮𝘃𝟮", "§7مباراة فريقين، لاعبان ضد لاعبين");
        button(inv, 16, Material.TNT, "§c☠・𝗣𝗮𝗿𝘁𝘆 𝗙𝗙𝗔", "§7كل أعضاء البارتي ضد بعضهم");
        button(inv, 22, Material.PLAYER_HEAD, "§e👥・𝗣𝗮𝗿𝘁𝘆 𝗠𝗲𝗺𝗯𝗲𝗿𝘀", "§7عرض أعضاء البارتي");
        button(inv, 31, Material.BARRIER, "§c✕・𝗖𝗹𝗼𝘀𝗲", "§7إغلاق القائمة");
        player.openInventory(inv);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (!isLobbyItem(item)) return;
        event.setCancelled(true);

        String key = item.getItemMeta().getPersistentDataContainer().get(lobbyItemKey, PersistentDataType.STRING);
        if ("kit".equals(key)) {
            openKitMenu(player);
        } else if ("party".equals(key)) {
            if (plugin.partyManager().partyOf(player.getUniqueId()) == null) {
                plugin.partyManager().create(player);
            }
            openPartyMenu(player);
        } else if ("editor".equals(key)) {
            KitType kit = selectedKits.getOrDefault(player.getUniqueId(), KitType.SWORD);
            if (!plugin.kitEditorManager().open(player, kit)) {
                player.sendMessage(plugin.message("kit-editor-failed"));
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (title.equals(KIT_MENU) || title.equals(PARTY_MENU)) {
            event.setCancelled(true);
            if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) return;

            if (title.equals(KIT_MENU)) {
                int slot = event.getRawSlot();
                int index = slot - 10;
                if (index >= 0 && index < KitType.values().length && slot < 29) {
                    KitType kit = KitType.values()[index];
                    if (plugin.kitManager().apply(player, kit)) {
                        selectedKits.put(player.getUniqueId(), kit);
                        player.sendMessage(plugin.message("kit-selected").replace("<kit>", pretty(kit)));
                        player.closeInventory();
                        giveLobbyItems(player);
                    } else {
                        player.sendMessage(plugin.message("unknown-kit"));
                    }
                } else if (slot == 31) {
                    player.closeInventory();
                }
            } else {
                switch (event.getRawSlot()) {
                    case 10 -> startPartyMode(player, PartyMode.ONE_V_ONE);
                    case 13 -> startPartyMode(player, PartyMode.TWO_V_TWO);
                    case 16 -> startPartyMode(player, PartyMode.FFA);
                    case 22 -> {
                        PartyManager.Party party = plugin.partyManager().partyOf(player.getUniqueId());
                        if (party == null) {
                            player.sendMessage(plugin.message("party-not-in"));
                        } else {
                            player.sendMessage(ChatColor.AQUA + "Party members: " + party.members().stream()
                                    .map(id -> {
                                        Player p = Bukkit.getPlayer(id);
                                        return p == null ? Bukkit.getOfflinePlayer(id).getName() : p.getName();
                                    })
                                    .filter(java.util.Objects::nonNull)
                                    .reduce((a, b) -> a + ", " + b).orElse("-"));
                        }
                    }
                    case 31 -> player.closeInventory();
                    default -> {}
                }
            }
            return;
        }

        if (plugin.matchManager().isInMatch(player.getUniqueId())) return;
        if (event.getRawSlot() >= 0 && event.getRawSlot() < 9 && isLobbyItem(event.getCurrentItem())) {
            event.setCancelled(true);
        }
        if (event.isShiftClick() && isLobbyItem(event.getCurrentItem())) {
            event.setCancelled(true);
        }
    }

    private void startPartyMode(Player player, PartyMode mode) {
        if (plugin.partyManager().partyOf(player.getUniqueId()) == null) {
            player.sendMessage(plugin.message("party-not-in"));
            return;
        }
        if (!plugin.partyManager().isLeader(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "فقط قائد البارتي يقدر يبدأ هذا الطور.");
            return;
        }
        plugin.partyManager().setMode(player.getUniqueId(), mode);
        if (!plugin.getConfig().getBoolean("settings.party-auto-start", true)) {
            player.sendMessage(plugin.message("party-started").replace("<mode>", mode.displayName));
            player.closeInventory();
            return;
        }
        if (!plugin.matchManager().startParty(player.getUniqueId())) {
            int size = plugin.partyManager().size(player.getUniqueId());
            boolean sizeOk = mode == PartyMode.FFA
                    ? size >= plugin.getConfig().getInt("settings.party-min-ffa-size", 2)
                    : (mode == PartyMode.ONE_V_ONE ? size == 2 : size == 4);
            player.sendMessage(sizeOk ? plugin.message("party-no-arena") : plugin.message("party-invalid-size"));
            return;
        }
        player.sendMessage(plugin.message("party-started").replace("<mode>", mode.displayName));
        player.closeInventory();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (title.equals(KIT_MENU) || title.equals(PARTY_MENU)) event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isLobbyItem(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isLobbyItem);
    }

    private boolean isLobbyItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(lobbyItemKey, PersistentDataType.STRING);
    }

    private void fill(Inventory inv) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler.clone());
    }

    private void button(Inventory inv, int slot, Material material, String name, String... lore) {
        inv.setItem(slot, item(material, name, lore));
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String pretty(KitType kit) {
        return kit == KitType.SPEAR_MACE ? "Spear & Mace" : kit.name().replace('_', ' ');
    }

    public enum PartyMode {
        ONE_V_ONE("Party 1v1"),
        TWO_V_TWO("Party 2v2"),
        FFA("Party FFA");

        private final String displayName;
        PartyMode(String displayName) { this.displayName = displayName; }
    }

    private static final class PlayerInventoryAccess {
        static void clearAndPlace(Player player, NamespacedKey key) {
            var inv = player.getInventory();
            inv.clear();
            inv.setItem(0, named(Material.IRON_SWORD, "§b⚔・𝗗𝘂𝗲𝗹𝘀", "§7اضغط بالزر الأيمن لاختيار الـKit", key, "kit"));
            inv.setItem(1, named(Material.GOAT_HORN, "§d➕・𝗣𝗮𝗿𝘁𝘆", "§7اضغط بالزر الأيمن لفتح نظام البارتي", key, "party"));
            inv.setItem(8, named(Material.BOOK, "§6✎・𝗞𝗶𝘁 𝗘𝗱𝗶𝘁𝗼𝗿", "§7اضغط بالزر الأيمن لتعديل الكيت", key, "editor"));
        }

        private static ItemStack named(Material material, String name, String lore, NamespacedKey key, String value) {
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(name);
                meta.setLore(List.of(lore));
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
                item.setItemMeta(meta);
            }
            return item;
        }
    }
}
