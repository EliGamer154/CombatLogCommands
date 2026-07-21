# CombatLogCommands

A Fabric server-side mod for Minecraft **26.1.2 - 26.2** that adds PvP combat tagging. No client-side mod is required — players join with a vanilla client.

## How it works

- Hitting another player, or being hit by one, puts **both** players in combat for **15 seconds**. Landing another hit refreshes the timer back to 15 seconds for both players involved.
- While in combat, a small red "Combat Tag: Xs" countdown is shown at the bottom of the screen (the action bar).
- While in combat, players can't use `/back`, `/tpa`, `/tpaccept`, `/home`, `/spawn`, `/tpahere`, or `/rtp` (or any other command listed in the config) — attempting to do so sends a warning in chat and cancels the command, regardless of which mod added it.
- Players also can't send `/tpa <name>` or `/tpahere <name>` **to** someone who is currently in combat — the sender gets a warning that the target is mid-fight.
- If a tagged player disconnects while still in combat, they are killed **immediately**, before the disconnect completes — there's no window to camp offline and dodge the punishment. A visual-only lightning bolt strikes them for effect (no fire, no block/entity damage from it — the kill itself is what does the damage), with the vanilla thunder sound, and everyone sees "**\<player\> has logged out during combat!**" in chat.
- `/back` also has its own **30-second cooldown**, independent of combat, so it can't be spammed.
- Firework rockets have a **1.5-second cooldown while in combat** (elytra boosting included), shown as the vanilla ender-pearl-style white bar on the item — no chat spam. Every 3rd rocket, the cooldown stretches to **2.5 seconds**. No cooldown outside combat.

### Teleport requests & countdowns

The mod takes over `/tpa`, `/tpahere`, `/tpaccept`, and `/tpdeny` so teleports feel deliberate and can't be used to bail out of a fight:

- Sending `/tpa <player>` or `/tpahere <player>` does **not** spam chat. The target instead gets an **action-bar notice with a chime sound** (re-shown every few seconds while the request is pending), and the sender sees a confirmation.
- The target accepts by running `/tpaccept`, which opens a small **menu of pending requests** (each shown as the requester's head) — click one to accept it. `/tpdeny` clears all pending requests.
- **No spamming**: while a request you sent is still pending, you can't send another to the same player — you have to wait for it to time out (or for them to respond) first. So no one can spam the notice on someone's screen to annoy them.
- On accept, a **3... 2... 1... countdown** appears on the action bar (same spot as the combat timer) for **both** players, with an ascending pearl sound on each count. The player who's about to teleport must stand still; **moving or entering combat cancels** it for both. On success they teleport with an ender pearl sound.
- Self-teleport commands like `/back`, `/rtp`, and `/home` get the same countdown (no acceptance needed) — which commands do is configurable.

### Waymark integration

If the [Waymark](https://github.com/mbreyno/waymark) home mod is installed, teleporting from **its home menu** (clicking a home, then Teleport) also runs the 3-2-1 countdown — the same standing-still / combat-cancel rules apply, and it's blocked during combat. This is an optional hook: it activates only when Waymark is present and does nothing (a harmless startup log line) when it isn't. It's tied to `home` being in `warmupCommands`, so removing `home` there also removes the countdown from the menu.

## Config

On first run, a config file is created at `config/combatlogcommands.json`:

```json
{
  "blockedCommands": [
    "back",
    "tpa",
    "tpaccept",
    "home",
    "spawn",
    "tpahere",
    "rtp"
  ],
  "blockedWhenTargetInCombat": [
    "tpa",
    "tpahere"
  ],
  "warmupCommands": [
    "back",
    "rtp",
    "home"
  ],
  "warmupRequiresArg": [
    "home"
  ],
  "combatDurationSeconds": 15.0,
  "backCooldownSeconds": 30.0,
  "fireworkCooldownSeconds": 1.5,
  "fireworkEveryThirdCooldownSeconds": 2.5,
  "teleportWarmupSeconds": 3.0,
  "playerOverrides": {}
}
```

- `blockedCommands` — commands a combat-tagged player can't use (no leading slash, case-insensitive).
- `blockedWhenTargetInCombat` — commands that can't be sent **at** a player who is in combat (first argument is treated as the target's name).
- `warmupCommands` — self-teleport commands (no acceptance needed) that get held for the 3-2-1 countdown before running.
- `warmupRequiresArg` — warmup commands that only count down when given an argument (e.g. `/home <name>`), so a bare `/home` that just opens the home list doesn't start a countdown.
- `combatDurationSeconds` — how long the combat tag lasts per hit.
- `backCooldownSeconds` — the always-on `/back` cooldown.
- `fireworkCooldownSeconds` / `fireworkEveryThirdCooldownSeconds` — the in-combat firework rocket cooldown, and the longer one applied to every 3rd rocket.
- `playerOverrides` — per-player settings, keyed by player name (case-insensitive). Each entry may set `combatDurationSeconds`, `fireworkCooldownSeconds`, and/or `fireworkEveryThirdCooldownSeconds`; anything left out falls back to the global value. Example:

```json
"playerOverrides": {
  "Steve": {
    "combatDurationSeconds": 30.0,
    "fireworkCooldownSeconds": 5.0
  }
}
```

Old config files upgrade automatically: any missing settings are added with their defaults on server start, and your existing values are kept. After editing by hand, apply live with `/combatlog reload`.

## Admin commands

Requires op (permission level 2+). Every change is saved to the config file immediately — no restart or reload needed:

- `/combatlog show` — print the current settings.
- `/combatlog reload` — re-read the config file after editing it by hand.
- `/combatlog reset` — overwrite the config file with default settings.
- `/combatlog set combatduration <seconds>` — combat tag length.
- `/combatlog set backcooldown <seconds>` — the `/back` cooldown.
- `/combatlog set fireworkcooldown <seconds>` — in-combat firework cooldown.
- `/combatlog set fireworkthirdcooldown <seconds>` — the longer every-3rd-rocket cooldown.
- `/combatlog set warmup <seconds>` — the teleport countdown length.
- `/combatlog blocked add|remove <command>` — edit the in-combat blocked command list.
- `/combatlog targetblocked add|remove <command>` — edit the can't-target-someone-in-combat list.
- `/combatlog warmupcmd add|remove <command>` — edit the list of self-teleport commands that get the countdown.
- `/combatlog warmupneedsarg add|remove <command>` — edit which countdown commands only fire when given an argument (like `/home <name>`).
- `/combatlog player <name> combatduration|fireworkcooldown|fireworkthirdcooldown <seconds>` — set a per-player override.
- `/combatlog player <name> clear` — remove all of a player's overrides.

## Requirements

- Minecraft **26.1.2 - 26.2** (Fabric)
- Fabric Loader `>= 0.19.3`
- Fabric API (matching game version)
- Java **25** on the server

## Building

```
./gradlew build
```

The output jar is written to `build/libs/`.
