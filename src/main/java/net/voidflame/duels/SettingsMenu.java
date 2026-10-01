package net.voidflame.duels;

import net.voidflame.core.storage.PlayerSettingsService;
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
        Map<String, Boolean> values = cache.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>());
        for (Setting s : SETTINGS) values.putIfAbsent(s.key(), service.getCached(player.getUniqueId(), s.key(), s.defaultValue()));
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        fill(inv);
        inv.setItem(4, item(Material.COMPARATOR, "§b§lSettings", "§7Customize your practice experience.", "", "§8Click an option to toggle it."));
        int[] slots = {10,11,12,13,14,15,16,19,20,21};
        for (int i=0;i<SETTINGS.size();i++) {
            Setting s=SETTINGS.get(i); boolean enabled=values.getOrDefault(s.key(),s.defaultValue());
            button(inv,slots[i],s.material(),(enabled?"§a":"§c")+s.label(),"§7Status: "+(enabled?"§a"+s.on():"§c"+s.off()),"","§dClick §8» §fToggle");
        }
        inv.setItem(18,item(Material.ARROW,"§7§lBack","§7Return to spawn."));
        inv.setItem(26,item(Material.BARRIER,"§c§lClose","§7Close this menu."));
        player.openInventory(inv);
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
        if(s==18){p.closeInventory();return;}
        int[] slots={10,11,12,13,14,15,16,19,20,21};
        for(int i=0;i<slots.length;i++) if(s==slots[i]) { toggle(p,SETTINGS.get(i)); return; }
    }

    private void fill(Inventory inv){ ItemStack x=item(Material.GRAY_STAINED_GLASS_PANE," "); for(int i=0;i<27;i++)inv.setItem(i,x.clone()); }
    private void button(Inventory inv,int slot,Material m,String name,String... lore){inv.setItem(slot,item(m,name,lore));}
    private ItemStack item(Material m,String name,String... lore){ItemStack x=new ItemStack(m);ItemMeta meta=x.getItemMeta();if(meta!=null){meta.setDisplayName(name);meta.setLore(List.of(lore));x.setItemMeta(meta);}return x;}
}