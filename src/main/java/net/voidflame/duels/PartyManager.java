package net.voidflame.duels;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PartyManager implements Listener {
    public record Party(UUID leader, Set<UUID> members) {
        public Party {
            members = Collections.unmodifiableSet(new LinkedHashSet<>(members));
        }
    }

    private record Invite(UUID leader, long expiresAt) {}

    private final VoidFlameDuelsPlugin plugin;
    private final Map<UUID, LinkedHashSet<UUID>> parties = new ConcurrentHashMap<>();
    private final Map<UUID, Invite> pendingInvites = new ConcurrentHashMap<>();
    private final Map<UUID, LobbyItemsManager.PartyMode> partyModes = new ConcurrentHashMap<>();

    public PartyManager(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized boolean create(Player leader) {
        if (partyOf(leader.getUniqueId()) != null) return false;
        LinkedHashSet<UUID> members = new LinkedHashSet<>();
        members.add(leader.getUniqueId());
        parties.put(leader.getUniqueId(), members);
        return true;
    }

    public synchronized Party partyOf(UUID player) {
        for (Map.Entry<UUID, LinkedHashSet<UUID>> entry : parties.entrySet()) {
            if (entry.getValue().contains(player)) return snapshot(entry.getKey(), entry.getValue());
        }
        return null;
    }

    public synchronized boolean invite(Player leader, Player target) {
        Party party = partyOf(leader.getUniqueId());
        if (party == null || !party.leader().equals(leader.getUniqueId()) || target.equals(leader)) return false;
        if (party.members().contains(target.getUniqueId()) || partyOf(target.getUniqueId()) != null) return false;
        int max = Math.max(2, plugin.getConfig().getInt("settings.party-max-size", 8));
        if (party.members().size() >= max) return false;
        long ttl = Math.max(5, plugin.getConfig().getLong("settings.party-invite-expiry-seconds", 60));
        pendingInvites.put(target.getUniqueId(), new Invite(leader.getUniqueId(), System.currentTimeMillis() + ttl * 1000L));
        return true;
    }

    public synchronized boolean accept(Player target) {
        Invite invite = pendingInvites.remove(target.getUniqueId());
        if (invite == null || invite.expiresAt() <= System.currentTimeMillis()) return false;
        LinkedHashSet<UUID> members = parties.get(invite.leader());
        if (members == null || partyOf(target.getUniqueId()) != null) return false;
        int max = Math.max(2, plugin.getConfig().getInt("settings.party-max-size", 8));
        if (members.size() >= max) return false;
        members.add(target.getUniqueId());
        return true;
    }

    public synchronized boolean leave(Player player) {
        return leave(player.getUniqueId());
    }

    public synchronized boolean leave(UUID playerId) {
        Party party = partyOf(playerId);
        if (party == null) return false;
        LinkedHashSet<UUID> members = parties.get(party.leader());
        if (members == null) return false;

        members.remove(playerId);
        clearInvitesFor(playerId);

        if (members.isEmpty()) {
            parties.remove(party.leader());
        } else if (party.leader().equals(playerId)) {
            UUID newLeader = members.iterator().next();
            parties.remove(party.leader());
            parties.put(newLeader, members);
            LobbyItemsManager.PartyMode mode = partyModes.remove(party.leader());
            if (mode != null) partyModes.put(newLeader, mode);
        }
        return true;
    }

    public synchronized boolean kick(Player leader, Player target) {
        Party party = partyOf(leader.getUniqueId());
        if (party == null || !party.leader().equals(leader.getUniqueId()) || target.equals(leader)) return false;
        LinkedHashSet<UUID> members = parties.get(leader.getUniqueId());
        if (members == null || !members.remove(target.getUniqueId())) return false;
        clearInvitesFor(target.getUniqueId());
        return true;
    }

    public synchronized boolean disband(Player leader) {
        Party party = partyOf(leader.getUniqueId());
        if (party == null || !party.leader().equals(leader.getUniqueId())) return false;
        parties.remove(leader.getUniqueId());
        party.members().forEach(this::clearInvitesFor);
        return true;
    }

    public synchronized boolean hasInvite(Player target) {
        Invite invite = pendingInvites.get(target.getUniqueId());
        if (invite == null) return false;
        if (invite.expiresAt() <= System.currentTimeMillis()) {
            pendingInvites.remove(target.getUniqueId(), invite);
            return false;
        }
        return parties.containsKey(invite.leader());
    }

    public boolean isLeader(UUID player) {
        Party party = partyOf(player);
        return party != null && party.leader().equals(player);
    }

    public boolean setMode(UUID leader, LobbyItemsManager.PartyMode mode) {
        if (!isLeader(leader) || mode == null) return false;
        partyModes.put(leader, mode);
        return true;
    }

    public LobbyItemsManager.PartyMode modeOf(UUID player) {
        Party party = partyOf(player);
        return party == null ? null : partyModes.get(party.leader());
    }

    public int size(UUID player) {
        Party party = partyOf(player);
        return party == null ? 0 : party.members().size();
    }

    public Collection<Player> onlineMembers(UUID player) {
        Party party = partyOf(player);
        if (party == null) return List.of();
        List<Player> online = new ArrayList<>();
        for (UUID id : party.members()) {
            Player member = Bukkit.getPlayer(id);
            if (member != null && member.isOnline()) online.add(member);
        }
        return List.copyOf(online);
    }

    private Party snapshot(UUID leader, Set<UUID> members) {
        return new Party(leader, members);
    }

    private void clearInvitesFor(UUID uuid) {
        pendingInvites.remove(uuid);
        pendingInvites.entrySet().removeIf(entry -> entry.getValue().leader().equals(uuid));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        pendingInvites.remove(id);
        Party party = partyOf(id);
        if (party != null) leave(id);
    }

    public void expireInvites() {
        long now = System.currentTimeMillis();
        pendingInvites.entrySet().removeIf(entry ->
                entry.getValue().expiresAt() <= now || !parties.containsKey(entry.getValue().leader()));
    }

    public void shutdown() {
        parties.clear();
        pendingInvites.clear();
    }
}
