# VoidFlame-Duels

Professional practice and duels foundation for VoidFlame MC.

## Ladders
- Sword
- Axe
- UHC
- Mace
- Spear & Mace
- Crystal
- Netherite Pot

## Features
- Eight canonical practice kits: Sword, Axe, UHC, Mace, Crystal, Netherite Pot, SMP, and Spear & Mace.
- Queue and direct duel matchmaking, ranked/unranked queues, parties, FFA, spectators, reconnects, rematches and rewards.
- Built-in kit definitions and a personal inventory-layout editor persisted through VoidFlame-Core.
- Built-in arena allocation and bounded region restoration. Each locally configured arena must define `region.min` and `region.max`; arenas without a valid reset region are not allocated.
- Match result persistence, wins/losses/streaks/ELO updates, replays and shared audit logging through VoidFlame-Core.

## Architecture
Duels owns gameplay, kit editing and local arena lifecycle. VoidFlame-Core owns the single shared SQLite database and registers persistence/audit services; VoidFlame-Admin owns administration and VoidFlame-Security owns security controls. Retired standalone Kits/Arenas plugins are not required.

## Native ESC menu
- Compatibility template: `extras/DonutQuickActions/config.yml`.
- Setup notes: `docs/PAUSE-MENU.md`.
- The template opens a native VoidFlame dialog from the pause screen and includes an Admin Panel action gated by `voidflame.duels.admin`.
- `/leaderboard` opens the same player leaderboard as hotbar slot 9.
