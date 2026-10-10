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
- Queue and direct duel matchmaking.
- Party matches, FFA, BotDuels, spectators, reconnects, rematches, rewards, and audit integration.

## Architecture
Queue, matchmaking and matches live in this plugin. Shared infrastructure is provided by VoidFlame-Core.

## Native ESC menu
- Compatibility template: `extras/DonutQuickActions/config.yml`.
- Setup notes: `docs/PAUSE-MENU.md`.
- The template opens a native VoidFlame dialog from the pause screen and includes an Admin Panel action gated by `voidflame.duels.admin`.
- `/leaderboard` opens the same player leaderboard as hotbar slot 9.
