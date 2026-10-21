package net.voidflame.duels;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.event.ClickEvent;
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
                DialogBody.item(new ItemStack(Material.NETHER_STAR), DialogBody.plainMessage(Component.text("Wins, ELO, losses and streaks", NamedTextColor.GRAY), 180), true, true, 48, 48)
        );
        show(player, "VoidFlame • Practice", body, List.of(
                button("Duels & Queues", "Choose a kit and enter ranked or unranked matchmaking.", "/vfui kits", NamedTextColor.LIGHT_PURPLE),
                button("Leaderboards", "View competitive ELO and player statistics.", "/vfui leaderboard", NamedTextColor.AQUA),
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

    public void openMatchResult(Player player, String result, String opponent, String kit, String arena, long durationSeconds) {
        if (!"VICTORY".equals(result) && !"DEFEAT".equals(result)) {
            throw new IllegalArgumentException("Match result dialog only supports VICTORY or DEFEAT.");
        }
        NamedTextColor resultColor = "VICTORY".equals(result)
                ? NamedTextColor.GOLD : NamedTextColor.RED;
        String duration = String.format(Locale.ROOT, "%02d:%02d",
                Math.max(0L, durationSeconds) / 60L, Math.max(0L, durationSeconds) % 60L);
        List<DialogBody> body = List.of(
                DialogBody.plainMessage(Component.text("✦  " + result + "  ✦", resultColor), 300),
                DialogBody.plainMessage(Component.text("OPPONENT  •  " + opponent, NamedTextColor.WHITE), 300),
                DialogBody.plainMessage(Component.text("KIT  •  " + kit, NamedTextColor.LIGHT_PURPLE), 300),
                DialogBody.plainMessage(Component.text("ARENA  •  " + arena, NamedTextColor.AQUA), 300),
                DialogBody.plainMessage(Component.text("MATCH TIME  •  " + duration, NamedTextColor.GRAY), 300),
                DialogBody.plainMessage(Component.text("Queue again, return to spawn, or open the main menu.", NamedTextColor.GRAY), 300)
        );
        List<ActionButton> buttons = List.of(
                button("Rematch", "Challenge your previous opponent again.", "/rematch", NamedTextColor.GREEN),
                button("Return to Spawn", "Return to the Practice spawn.", "/spawn", NamedTextColor.LIGHT_PURPLE),
                button("Main Menu", "Open VoidFlameMC Practice.", "/vfmenu", NamedTextColor.AQUA)
        );
        show(player, "MATCH COMPLETE", body, buttons, 2);
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
                button("Back to Main Menu", "Return to main menu.", "/vfmenu", NamedTextColor.GRAY)
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

    public void openParty(Player player) {
        PartyManager.Party party = plugin.partyManager().partyOf(player.getUniqueId());
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.item(new ItemStack(Material.GOAT_HORN),
                DialogBody.plainMessage(Component.text("PARTY SYSTEM", NamedTextColor.AQUA), 200),
                true, true, 48, 48));
        if (party == null) {
            body.add(DialogBody.plainMessage(Component.text("You are not currently in a party. Create one or accept an invite.", NamedTextColor.GRAY), 300));
        } else {
            String members = party.members().stream().map(id -> {
                Player online = plugin.getServer().getPlayer(id);
                String name = online == null ? plugin.getServer().getOfflinePlayer(id).getName() : online.getName();
                return name == null ? id.toString().substring(0, 8) : name;
            }).reduce((a, b) -> a + ", " + b).orElse("-");
            body.add(DialogBody.plainMessage(Component.text("Leader: " + memberName(party.leader()) + "  •  Members: " + members, NamedTextColor.GRAY), 300));
        }
        List<ActionButton> actions = List.of(
                button("Create Party", "Create a new party.", "/party create", NamedTextColor.AQUA),
                button("Accept Invite", "Accept a pending party invitation.", "/party accept", NamedTextColor.GREEN),
                button("Invite Player", "Invite the name entered above.", "/party invite $(target)", NamedTextColor.LIGHT_PURPLE),
                button("Queue 1v1", "Queue your party for 1v1.", "/party queue 1v1", NamedTextColor.GOLD),
                button("Queue 2v2", "Queue your party for 2v2.", "/party queue 2v2", NamedTextColor.AQUA),
                button("Queue FFA", "Queue your party for Free For All.", "/party queue ffa", NamedTextColor.RED),
                button("Party Info", "Show current party members.", "/party info", NamedTextColor.WHITE),
                button("Leave Party", "Leave your current party.", "/party leave", NamedTextColor.RED),
                button("Main Menu", "Return to VoidFlame navigation.", "/vfmenu", NamedTextColor.GRAY)
        );
        show(player, "VoidFlame • Party", body, actions, 2, List.of(
                DialogInput.text("target", Component.text("Player name", NamedTextColor.GRAY))
                        .initial("").maxLength(16).width(240).build()
        ));
    }

    private String memberName(java.util.UUID uuid) {
        Player online = plugin.getServer().getPlayer(uuid);
        if (online != null) return online.getName();
        String name = plugin.getServer().getOfflinePlayer(uuid).getName();
        return name == null ? uuid.toString().substring(0, 8) : name;
    }

    public void openAdmin(Player player) {
        if (!player.hasPermission("voidflame.duels.admin")) {
            player.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
            return;
        }
        List<DialogBody> body = List.of(
                DialogBody.plainMessage(Component.text("VOIDFLAME STAFF CONTROL", NamedTextColor.RED), 300),
                DialogBody.plainMessage(Component.text("Queued players: " + plugin.queueManager().totalQueued()
                        + "   •   Active matches: " + plugin.matchManager().activeMatches(), NamedTextColor.GRAY), 300),
                DialogBody.plainMessage(Component.text("Choose an administrative area. Sensitive actions still enforce their own permissions.", NamedTextColor.GRAY), 300)
        );
        show(player, "VoidFlame • Admin Panel", body, List.of(
                button("FFA Status", "View current FFA status.", "/ffa stats", NamedTextColor.RED),
                button("Reports", "Open report management.", "/reports", NamedTextColor.AQUA),
                button("Settings", "Open player preferences.", "/settings", NamedTextColor.GRAY),
                button("Kit Administration", "Open kit management.", "/kitadmin", NamedTextColor.GOLD),
                button("Back", "Return to main menu.", "/vfmenu", NamedTextColor.LIGHT_PURPLE)
        ), 2);
    }

    public boolean handle(Player player, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("main")) { openMain(player); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "kits", "queue" -> openQueue(player);
            case "kit" -> { if (args.length < 2) openQueue(player); else openKit(player, args[1]); }
            case "targetkit" -> { if (args.length < 2) openQueue(player); else sendTargetDuel(player, args[1]); }
            case "leaderboard-choice", "rankings" -> openLeaderboard(player);
            case "leaderboard" -> openLeaderboard(player);
            case "editor" -> openEditorChoice(player);
            case "party" -> openParty(player);
            case "admin" -> openAdmin(player);
            default -> openMain(player);
        }
        return true;
    }

    private void show(Player player, String title, List<DialogBody> body, List<ActionButton> buttons, int columns) {
        show(player, title, body, buttons, columns, List.of());
    }

    private void show(Player player, String title, List<DialogBody> body, List<ActionButton> buttons, int columns, List<DialogInput> inputs) {
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(title, NamedTextColor.WHITE))
                        .externalTitle(Component.text("VoidFlame", NamedTextColor.LIGHT_PURPLE))
                        .canCloseWithEscape(true)
                        .body(body)
                        .inputs(inputs)
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
