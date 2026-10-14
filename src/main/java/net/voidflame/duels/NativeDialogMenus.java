package net.voidflame.duels;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native Minecraft dialog screens for the Paper 26.2 client UI.
 * These are client-native dialogs, not chest/inventory menus.
 */
public final class NativeDialogMenus {
    private static final List<KitCard> KITS = List.of(
            new KitCard("sword", "Sword", Material.DIAMOND_SWORD, NamedTextColor.LIGHT_PURPLE, "Classic sword combat"),
            new KitCard("axe", "Axe", Material.DIAMOND_AXE, NamedTextColor.LIGHT_PURPLE, "Axe and shield combat"),
            new KitCard("uhc", "UHC", Material.GOLDEN_APPLE, NamedTextColor.GOLD, "UHC-style healing and combat"),
            new KitCard("mace", "Mace", Material.MACE, NamedTextColor.AQUA, "Mace movement and impact"),
            new KitCard("spear_mace", "Spear & Mace", Material.TRIDENT, NamedTextColor.AQUA, "Spear and mace combat"),
            new KitCard("crystal", "Crystal", Material.END_CRYSTAL, NamedTextColor.LIGHT_PURPLE, "Crystal PvP"),
            new KitCard("netherite_pot", "Netherite Pot", Material.SPLASH_POTION, NamedTextColor.DARK_PURPLE, "Netherite potion combat"),
            new KitCard("smp", "SMP", Material.TOTEM_OF_UNDYING, NamedTextColor.GREEN, "SMP-style PvP kit"),
            new KitCard("diamond_smp", "Diamond SMP", Material.DIAMOND_CHESTPLATE, NamedTextColor.AQUA, "Diamond gear PvP"),
            new KitCard("tnt_minecart_lt", "TNT Minecart LT", Material.TNT_MINECART, NamedTextColor.GOLD, "Low-tier TNT minecart combat"),
            new KitCard("tnt_minecart_ht", "TNT Minecart HT", Material.TNT_MINECART, NamedTextColor.RED, "High-tier TNT minecart combat")
    );

    private final VoidFlameDuelsPlugin plugin;
    private final Map<UUID, UUID> duelTargets = new ConcurrentHashMap<>();

    public NativeDialogMenus(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMain(Player player) {
        List<DialogBody> body = List.of(
                DialogBody.plainMessage(Component.text("VOIDFLAME MC  •  PRACTICE & DUELS", NamedTextColor.LIGHT_PURPLE), 300),
                DialogBody.plainMessage(Component.text("Choose a destination. This screen uses Minecraft's native dialog UI.", NamedTextColor.GRAY), 300),
                DialogBody.item(new ItemStack(Material.DIAMOND_SWORD), DialogBody.plainMessage(Component.text("Competitive duels and matchmaking", NamedTextColor.GRAY), 180), true, true, 48, 48),
                DialogBody.item(new ItemStack(Material.NETHER_STAR), DialogBody.plainMessage(Component.text("Player rankings and tier list", NamedTextColor.GRAY), 180), true, true, 48, 48)
        );
        show(player, "VoidFlame • Practice", body, List.of(
                button("Duels & Queues", "Choose a kit and enter ranked or unranked matchmaking.", "/vfui kits", NamedTextColor.LIGHT_PURPLE),
                button("Tiers / Leaderboards", "Open the tier and player ranking choices.", "/vfui leaderboard-choice", NamedTextColor.AQUA),
                button("My Statistics", "View your practice statistics.", "/stats", NamedTextColor.GREEN),
                button("Kit Layout Editor", "Choose a kit layout to customize.", "/vfui editor", NamedTextColor.GOLD),
                button("Practice Settings", "Change your player settings.", "/settings", NamedTextColor.GRAY),
                button("Free For All", "Choose a kit and enter FFA.", "/ffa", NamedTextColor.RED)
        ), 2);
    }

    public void openQueue(Player player) {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text("Choose a kit to view its queue options.", NamedTextColor.GRAY), 300));
        for (KitCard kit : KITS) {
            body.add(DialogBody.item(new ItemStack(kit.icon()),
                    DialogBody.plainMessage(Component.text(kit.name() + "  •  " + kit.description(), kit.color()), 220),
                    true, true, 44, 44));
        }
        List<ActionButton> buttons = new ArrayList<>();
        for (KitCard kit : KITS) {
            buttons.add(button(kit.name(), "Open ranked, unranked and layout options.", "/vfui kit " + kit.id(), kit.color()));
        }
        buttons.add(button("Back to Main Menu", "Return to the main navigation screen.", "/vfmenu", NamedTextColor.GRAY));
        show(player, "VoidFlame • Choose Kit", body, buttons, 2);
    }

    public void openKit(Player player, String id) {
        KitCard kit = KITS.stream().filter(value -> value.id().equalsIgnoreCase(id)).findFirst().orElse(null);
        if (kit == null) {
            player.sendMessage(Component.text("Unknown kit.", NamedTextColor.RED));
            openQueue(player);
            return;
        }
        List<DialogBody> body = List.of(
                DialogBody.item(new ItemStack(kit.icon()),
                        DialogBody.plainMessage(Component.text(kit.description(), NamedTextColor.GRAY), 220),
                        true, true, 64, 64),
                DialogBody.plainMessage(Component.text("Select how you want to play " + kit.name() + ".", kit.color()), 300),
                DialogBody.plainMessage(Component.text("Ranked affects your competitive rating. Unranked does not.", NamedTextColor.GRAY), 300)
        );
        show(player, "VoidFlame • " + kit.name(), body, List.of(
                button("Unranked Queue", "Play without rating changes.", "/queue unranked " + kit.id(), NamedTextColor.GREEN),
                button("Ranked Queue", "Play for competitive ELO.", "/queue ranked " + kit.id(), NamedTextColor.LIGHT_PURPLE),
                button("Edit Layout", "Open your saved inventory layout editor.", "/kiteditor " + kit.id(), NamedTextColor.AQUA),
                button("Back to Kits", "Return to kit selection.", "/vfui kits", NamedTextColor.GRAY)
        ), 2);
    }

    public void openTargetKit(Player player, Player target) {
        duelTargets.put(player.getUniqueId(), target.getUniqueId());
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text("Choose the kit for your duel with " + target.getName() + ".", NamedTextColor.GRAY), 300));
        for (KitCard kit : KITS) {
            body.add(DialogBody.item(new ItemStack(kit.icon()),
                    DialogBody.plainMessage(Component.text(kit.name() + "  •  " + kit.description(), kit.color()), 220),
                    true, true, 44, 44));
        }
        List<ActionButton> buttons = new ArrayList<>();
        for (KitCard kit : KITS) {
            buttons.add(button(kit.name(), "Send a duel request using this kit.", "/vfui targetkit " + kit.id(), kit.color()));
        }
        buttons.add(button("Cancel", "Cancel the duel request.", "/vfui kits", NamedTextColor.GRAY));
        show(player, "VoidFlame • Duel " + target.getName(), body, buttons, 2);
    }

    private void sendTargetDuel(Player player, String id) {
        UUID targetId = duelTargets.remove(player.getUniqueId());
        KitCard kit = KITS.stream().filter(value -> value.id().equalsIgnoreCase(id)).findFirst().orElse(null);
        if (targetId == null || kit == null) {
            player.sendMessage(Component.text("Duel target expired. Choose a player again.", NamedTextColor.RED));
            return;
        }
        Player target = plugin.getServer().getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            player.sendMessage(Component.text(plugin.message("player-not-found"), NamedTextColor.RED));
            return;
        }
        if (!plugin.requests().send(player, target, KitType.fromConfig(kit.id()))) {
            player.sendMessage(Component.text(plugin.message("duel-unavailable"), NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text(plugin.message("duel-sent").replace("<player>", target.getName()), NamedTextColor.GREEN));
        target.sendMessage(Component.text(plugin.message("duel-received").replace("<player>", player.getName()).replace("<kit>", kit.name()), NamedTextColor.LIGHT_PURPLE));
    }

    public void openLeaderboardChoice(Player player) {
        List<DialogBody> body = List.of(
                DialogBody.plainMessage(Component.text("Choose which ranking system to open.", NamedTextColor.GRAY), 300),
                DialogBody.item(new ItemStack(Material.NETHER_STAR),
                        DialogBody.plainMessage(Component.text("Tiers: competitive player tests and tier placements. Website/API connection will be configured when the site is deployed.", NamedTextColor.GRAY), 240),
                        true, true, 48, 48),
                DialogBody.item(new ItemStack(Material.PLAYER_HEAD),
                        DialogBody.plainMessage(Component.text("Leaderboards: VoidFlameMC wins, ELO, losses and streaks.", NamedTextColor.GRAY), 240),
                        true, true, 48, 48)
        );
        show(player, "VoidFlame • Rankings", body, List.of(
                button("Tiers", "Open the VoidFlame Tiers section.", "/vfui tiers", NamedTextColor.AQUA),
                button("Leaderboards", "Open the in-server player leaderboard.", "/vfui leaderboard", NamedTextColor.LIGHT_PURPLE),
                button("Back", "Return to main menu.", "/vfmenu", NamedTextColor.GRAY)
        ), 2);
    }

    public void openTiersInfo(Player player) {
        show(player, "VoidFlame • Tiers", List.of(
                DialogBody.plainMessage(Component.text("VOIDFLAME TIERS", NamedTextColor.AQUA), 300),
                DialogBody.plainMessage(Component.text("This entry is reserved for the VoidFlame Tiers website integration. The site has no configured public URL/API endpoint in the server config yet, so no fake ranking data or broken link is shown.", NamedTextColor.GRAY), 300),
                DialogBody.plainMessage(Component.text("Once deployed, configure integrations.tiers-url and the API endpoint to connect verified tier data.", NamedTextColor.GRAY), 300)
        ), List.of(button("Back to Rankings", "Return to the ranking choices.", "/vfui leaderboard-choice", NamedTextColor.LIGHT_PURPLE)), 1);
    }

    public void openLeaderboard(Player player) {
        show(player, "VoidFlame • Leaderboards", List.of(
                DialogBody.plainMessage(Component.text("VOIDFLAME MC PLAYER RANKINGS", NamedTextColor.LIGHT_PURPLE), 300),
                DialogBody.plainMessage(Component.text("The live ELO board is available from the statistics service. Additional ranking categories can be added once their queries are backed by stored data.", NamedTextColor.GRAY), 300),
                DialogBody.item(new ItemStack(Material.NETHER_STAR),
                        DialogBody.plainMessage(Component.text("ELO • Wins • Losses • Streak", NamedTextColor.AQUA), 220),
                        true, true, 48, 48)
        ), List.of(
                button("ELO Leaderboard", "Show the current ELO ranking.", "/stats top", NamedTextColor.AQUA),
                button("Back to Rankings", "Choose Tiers or Leaderboards.", "/vfui leaderboard-choice", NamedTextColor.GRAY)
        ), 1);
    }

    public void openEditorChoice(Player player) {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text("Choose a kit whose saved layout you want to edit.", NamedTextColor.GRAY), 300));
        for (KitCard kit : KITS) {
            body.add(DialogBody.item(new ItemStack(kit.icon()),
                    DialogBody.plainMessage(Component.text(kit.name(), kit.color()), 160), true, true, 36, 36));
        }
        List<ActionButton> buttons = new ArrayList<>();
        for (KitCard kit : KITS) {
            buttons.add(button(kit.name(), "Edit this kit's layout.", "/kiteditor " + kit.id(), kit.color()));
        }
        buttons.add(button("Back", "Return to main menu.", "/vfmenu", NamedTextColor.GRAY));
        show(player, "VoidFlame • Kit Editor", body, buttons, 2);
    }

    public void openAdmin(Player player) {
        if (!player.hasPermission("voidflame.duels.admin")) {
            player.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
            return;
        }
        show(player, "VoidFlame • Admin Panel", List.of(
                DialogBody.plainMessage(Component.text("STAFF TOOLS", NamedTextColor.RED), 300),
                DialogBody.plainMessage(Component.text("Administrative actions are only visible to authorized staff.", NamedTextColor.GRAY), 300)
        ), List.of(
                button("Duels Administration", "Manage practice and duel systems.", "/duelsadmin", NamedTextColor.RED),
                button("Kit Administration", "Manage kit definitions and availability.", "/kitadmin", NamedTextColor.GOLD),
                button("Reports", "Review player reports.", "/reports", NamedTextColor.AQUA),
                button("Back", "Return to main menu.", "/vfmenu", NamedTextColor.GRAY)
        ), 2);
    }

    public boolean handle(Player player, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("main")) { openMain(player); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "kits", "queue" -> openQueue(player);
            case "kit" -> { if (args.length < 2) openQueue(player); else openKit(player, args[1]); }
            case "targetkit" -> { if (args.length < 2) openQueue(player); else sendTargetDuel(player, args[1]); }
            case "leaderboard-choice", "rankings" -> openLeaderboardChoice(player);
            case "leaderboard" -> openLeaderboard(player);
            case "tiers" -> openTiersInfo(player);
            case "editor" -> openEditorChoice(player);
            case "admin" -> openAdmin(player);
            default -> openMain(player);
        }
        return true;
    }

    private void show(Player player, String title, List<DialogBody> body, List<ActionButton> buttons, int columns) {
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(title, NamedTextColor.WHITE))
                        .externalTitle(Component.text("VoidFlame", NamedTextColor.LIGHT_PURPLE))
                        .canCloseWithEscape(true)
                        .body(body)
                        .build())
                .type(DialogType.multiAction(buttons).columns(columns).build()));
        player.showDialog(dialog);
    }

    private ActionButton button(String label, String tooltip, String command, NamedTextColor color) {
        return ActionButton.create(
                Component.text(label, color),
                Component.text(tooltip, NamedTextColor.GRAY),
                180,
                DialogAction.commandTemplate(command)
        );
    }

    private record KitCard(String id, String name, Material icon, NamedTextColor color, String description) {}
}
