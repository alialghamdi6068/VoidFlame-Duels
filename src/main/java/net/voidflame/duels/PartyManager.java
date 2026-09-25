package net.voidflame.duels;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PartyManager {
    public record Party(UUID leader, Set<UUID> members) {
        public Party {
            members = Collections.unmodifiableSet(new LinkedHashSet<>(members));
        }
    }

    private final VoidFlameDuelsPlugin plugin;
    private final Map<UUID, LinkedHashSet<UUID>> parties = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingInvites = new ConcurrentHashMap<>();

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
        for (Map.Entry<UUID, LinkedHashSet<UUID>> e : parties.entrySet()) {
            if (e.getValue().contains(player)) return snapshot(e.getKey(), e.getValue());
        }
        return null;
    }

    public synchronized boolean invite(Player leader, Player target) {
        Party party = partyOf(leader.getUniqueId());
        if (party == null || !party.leader().equals(leader.getUniqueId()) || target.equals(leader)) return false;
        if (party.members().contains(target.getUniqueId()) || partyOf(target.getUniqueId()) != null) return false;
        pendingInvites.put(target.getUniqueId(), leader.getUniqueId());
        return true;
    }

    public synchronized boolean accept(Player target) {
        UUID leader = pendingInvites.remove(target.getUniqueId());
        if (leader == null) return false;
        LinkedHashSet<UUID> members = parties.get(leader);
        if (members == null || partyOf(target.getUniqueId()) != null) return false;
        members.add(target.getUniqueId());
        return true;
    }

    public synchronized boolean leave(Player player) {
        Party party = partyOf(player.getUniqueId());
        if (party == null) return false;
        LinkedHashSet<UUID> members = parties.get(party.leader());
        members.remove(player.getUniqueId());
        if (members.isEmpty()) {
            parties.remove(party.leader());
        } else if (party.leader().equals(player.getUniqueId())) {
            UUID newLeader = members.iterator().next();
            parties.remove(party.leader());
            parties.put(newLeader, members);
        }
        pendingInvites.remove(player.getUniqueId());
        return true;
    }

    public synchronized boolean kick(Player leader, Player target) {
        Party party = partyOf(leader.getUniqueId());
        if (party == null || !party.leader().equals(leader.getUniqueId()) || target.equals(leader)) return false;
        LinkedHashSet<UUID> members = parties.get(leader.getUniqueId());
        return members != null && members.remove(target.getUniqueId());
    }

    public synchronized boolean disband(Player leader) {
        Party party = partyOf(leader.getUniqueId());
        if (party == null || !party.leader().equals(leader.getUniqueId())) return false;
        parties.remove(leader.getUniqueId());
        party.members().forEach(pendingInvites::remove);
        return true;
    }

    public int size(UUID player) {
        Party p = partyOf(player);
        return p == null ? 0 : p.members().size();
    }

    public Collection<Player> onlineMembers(UUID player) {
        Party p = partyOf(player);
        if (p == null) return List.of();
        List<Player> online = new ArrayList<>();
        for (UUID id : p.members()) {
            Player member = Bukkit.getPlayer(id);
            if (member != null && member.isOnline()) online.add(member);
        }
        return List.copyOf(online);
    }

    private Party snapshot(UUID leader, Set<UUID> members) {
        return new Party(leader, members);
    }

    public void shutdown() {
        parties.clear();
        pendingInvites.clear();
    }
}
