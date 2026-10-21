package net.voidflame.duels;

import net.voidflame.core.storage.PlayerSettingsService;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SettingsMenu implements Listener {
    private static final String TITLE = "§8VoidFlame §7• §bSettings";
    private static final List<Setting> SETTINGS = List.of(
            new Setting("duel_requests", Material.DIAMOND_SWORD, "Duel Requests", true, "Enabled", "Disabled"),
            new Setting("party_invites", Material.GOAT_HORN, "Party Invites", true, "Enabled", "Disabled"),
            new Setting("explosion_effects", Material.FIREWORK_STAR, "Match Effects", false, "Enabled", "Disabled"),
            new Setting("kit_profile", Material.BOOK, "Kit Profile", false, "Visible", "Hidden"),
            new Setting("personal_level", Material.NAME_TAG, "Personal Level", false, "Enabled", "Disabled"),
            new Setting("friend_requests", Material.PLAYER_HEAD, "Friend Requests", false, "Enabled", "Disabled"),
            new Setting("private_messages", Material.WRITABLE_BOOK, "Private Messages", false, "Friends Only", "Disabled"),
            new Setting("friend_join_notifications", Material.BELL, "Friend Join Notifications", true, "Enabled", "Disabled"),
            new Setting("scoreboard", Material.DARK_OAK_HANGING_SIGN, "Scoreboard", true, "Enabled", "Disabled"),
            new Setting("show_players", Material.ENDER_EYE, "Show Players", true, "Enabled", "Disabled")
    );
    private final VoidFlameDuelsPlugin plugin;
    private final PlayerSettingsService service;
    private final Map<UUID, Map<String, Boolean>> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> busy = new ConcurrentHashMap<>();

    private record Setting(String key, Material material, String label, boolean defaultValue, String on, String off) {}

    public SettingsMenu(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
        var reg = Bukkit.getServicesManager().getRegistration(PlayerSettingsService.class);
        if (reg == null || reg.getProvider() == null) throw new IllegalStateException("VoidFlame-Core PlayerSettingsService is unavailable.");
        this.service = reg.getProvider();
    }

    public void open(Player player) {
        if (JavaAdminAccess.isBedrock(player)) {
            if (plugin.bedrockMenus().available()) openBedrock(player);
            else openBedrockInventory(player);
            return;
        }
        Map<String, Boolean> values = cache.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>());
        for (Setting setting : SETTINGS) {
            values.putIfAbsent(setting.key(), service.getCached(player.getUniqueId(), setting.key(), setting.defaultValue()));
        }
        List<DialogBody> body = new java.util.ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text("CUSTOMIZE YOUR PRACTICE EXPERIENCE", NamedTextColor.AQUA), 300));
        for (Setting setting : SETTINGS) {
            boolean enabled = values.getOrDefault(setting.key(), setting.defaultValue());
            String state = enabled ? setting.on() : setting.off();
            NamedTextColor stateColor = enabled ? NamedTextColor.GREEN : NamedTextColor.RED;
            body.add(DialogBody.item(new ItemStack(setting.material()),
                    DialogBody.plainMessage(Component.text(setting.label() + "  •  " + state, stateColor), 220),
                    true, true, 36, 36));
        }
        List<ActionButton> actions = new java.util.ArrayList<>();
        for (Setting setting : SETTINGS) {
            boolean enabled = values.getOrDefault(setting.key(), setting.defaultValue());
            actions.add(ActionButton.create(
                    Component.text(setting.label() + ": " + (enabled ? "ON" : "OFF"), enabled ? NamedTextColor.GREEN : NamedTextColor.RED),
                    Component.text("Toggle this preference and save it.", NamedTextColor.GRAY),
                    180,
                    DialogAction.commandTemplate("/settings toggle " + setting.key())
            ));
        }
        actions.add(ActionButton.create(Component.text("Main Menu", NamedTextColor.GRAY), Component.text("Return to VoidFlame navigation.", NamedTextColor.GRAY), 180, DialogAction.commandTemplate("/vfmenu")));
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("VoidFlame • Settings", NamedTextColor.WHITE))
                        .externalTitle(Component.text("VoidFlame", NamedTextColor.AQUA))
                        .canCloseWithEscape(true)
                        .body(body)
                        .build())
                .type(DialogType.multiAction(actions).columns(2).build()));
        player.showDialog(dialog);
    }

    private void openBedrock(Player player) {
        Map<String, Boolean> values = cache.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>());
        for (Setting setting : SETTINGS) {
            values.putIfAbsent(setting.key(), service.getCached(player.getUniqueId(), setting.key(), setting.defaultValue()));
        }
        org.geysermc.cumulus.form.SimpleForm.Builder form = org.geysermc.cumulus.form.SimpleForm.builder()
                .title("Practice Settings")
                .content("Choose a preference to toggle. Changes are saved to your profile.");
        List<Runnable> actions = new java.util.ArrayList<>();
        for (Setting setting : SETTINGS) {
            boolean enabled = values.getOrDefault(setting.key(), setting.defaultValue());
            form.button(setting.label() + ": " + (enabled ? setting.on() : setting.off()));
            actions.add(() -> toggle(player, setting));
        }
        form.button("Main Menu");
        actions.add(() -> plugin.bedrockMenus().openMain(player));
        form.validResultHandler((org.geysermc.cumulus.response.SimpleFormResponse response) -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < actions.size()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) actions.get(index).run();
                });
            }
        });
        plugin.bedrockMenus().sendForm(player, form);
    }

    private void openBedrockInventory(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, TITLE);
        int[] slots = {10,11,12,13,14,15,16,19,20,21};
        Map<String, Boolean> values = cache.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>());
        for (Setting setting : SETTINGS) {
            values.putIfAbsent(setting.key(), service.getCached(player.getUniqueId(), setting.key(), setting.defaultValue()));
        }
        for (int i = 0; i < SETTINGS.size(); i++) {
            Setting setting = SETTINGS.get(i);
            boolean enabled = values.getOrDefault(setting.key(), setting.defaultValue());
            button(inventory, slots[i], setting.material(), setting.label() + ": " + (enabled ? setting.on() : setting.off()),
                    "Click to toggle and save.");
        }
        button(inventory, 18, Material.ARROW, "Main Menu", "Return to VoidFlame navigation.");
        button(inventory, 26, Material.BARRIER, "Close", "Close settings.");
        player.openInventory(inventory);
    }

    public boolean toggleByKey(Player player, String key) {
        for (Setting setting : SETTINGS) {
            if (setting.key().equalsIgnoreCase(key)) {
                toggle(player, setting);
                return true;
            }
        }
        return false;
    }

    private void toggle(Player p, Setting s) {
        if (busy.putIfAbsent(p.getUniqueId(), true) != null) return;
        boolean next=!cache.computeIfAbsent(p.getUniqueId(),id->new ConcurrentHashMap<>()).getOrDefault(s.key(),s.defaultValue());
        service.set(p.getUniqueId(),s.key(),next).whenComplete((v,error)->Bukkit.getScheduler().runTask(plugin,()->{
            busy.remove(p.getUniqueId());
            if(error!=null){p.sendMessage("§cCould not save setting.");return;}
            cache.get(p.getUniqueId()).put(s.key(),next);
            open(p);
        }));
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) {
        Map<String,Boolean> values=cache.computeIfAbsent(e.getPlayer().getUniqueId(),id->new ConcurrentHashMap<>());
        for(Setting s:SETTINGS) service.get(e.getPlayer().getUniqueId(),s.key(),s.defaultValue()).thenAccept(v->values.put(s.key(),v));
    }

    @EventHandler public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !e.getView().getTitle().equals(TITLE)) return;
        e.setCancelled(true);
        int s=e.getRawSlot();
        if(s==26){p.closeInventory();return;}
        if(s==18){p.closeInventory(); if (JavaAdminAccess.isBedrock(p) && plugin.bedrockMenus().available()) plugin.bedrockMenus().openMain(p); return;}
        int[] slots={10,11,12,13,14,15,16,19,20,21};
        for(int i=0;i<slots.length;i++) if(s==slots[i]) { toggle(p,SETTINGS.get(i)); return; }
    }

    private void fill(Inventory inv){ /* Intentionally leave unused slots empty for a clean native-style layout. */ }
    private void button(Inventory inv,int slot,Material m,String name,String... lore){inv.setItem(slot,item(m,name,lore));}
    private ItemStack item(Material m,String name,String... lore){ItemStack x=new ItemStack(m);ItemMeta meta=x.getItemMeta();if(meta!=null){meta.setDisplayName(name);meta.setLore(List.of(lore));x.setItemMeta(meta);}return x;}
}