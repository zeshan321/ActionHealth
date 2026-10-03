# Version compatibility

ActionHealth ships one jar for every Spigot version from 1.8 to the current release, on any Java version from 8 up.

## How the plugin stays version independent

- **Compiled against the oldest API.** The build uses `spigot-api:1.8.8` and `--release 8`. Any newer API must go through feature detection in `utils/Compat.java` or `support/ActionBar.java`.
- **No version strings.** Nothing reads the CraftBukkit package name.
- **Action bar:** the plugin uses the first method that exists:
  1. The Spigot API `Player.Spigot#sendMessage(ChatMessageType, BaseComponent...)` (1.9.2 and later).
  2. Paper's `Player#sendActionBar(String)`.
  3. An NMS chat packet (1.8 to 1.9 only, before the Spigot API existed).

  If a method fails at runtime, the plugin logs it once and moves to the next method.
- **Optional plugins** (PlaceholderAPI, MVdWPlaceholderAPI, MythicMobs 4 and 5, LangUtils) are called through reflection. They need no compile dependency, and any plugin version works.

## Linkage check (runs in CI)

```sh
./gradlew build
compat/linkage-check.sh
```

`LinkageCheck.java` reads every Bukkit and BungeeCord chat class, method and field that the built jar references directly. It resolves each one against every `spigot-api` release in the Spigot Maven repository: 1.8 to 26.3 at the time of writing, 68 versions. A method that was removed, renamed or added later than 1.8 fails the check. The GitHub workflow runs it on every push and once a week, so a new Spigot release that breaks something shows up without a code change.

## End-to-end tests (manual, needs only Docker)

```sh
./gradlew build
compat/e2e/matrix.sh         # every version in matrix.txt
compat/e2e/integrations.sh   # WorldGuard and PlaceholderAPI
```

`matrix.sh` builds each Spigot version with BuildTools in Docker. It starts the server with the plugin and joins it with [mineflayer](https://github.com/PrismarineJS/mineflayer) bots. Then it checks the action bar the bots get:

| Scenario | What it checks |
| --- | --- |
| look | A bot looks at a cow and gets `Cow: 10/10 ...` (Show On Look). |
| hex | The message starts with `&#4fdfc4`. The bot gets that hex color on 1.16+, and aqua (the closest legacy color) before 1.16. |
| absorption | The cow gets 8 absorption health. The bot gets `+8` from `{absorption}` and 8 absorption icons after the 10 health icons. The absorption API exists from 1.14.4, and older versions read it from the server entity. |
| damage | Show On Look is turned off with `/actionhealth reload`. The bot hits the cow and gets `Cow: 9/10 ...`. |
| toggle | `/actionhealth toggle` stops the messages. |
| consume | Action system: Bot1 tags Bot2, Bot2 drinks a regeneration potion, Bot1 gets `Bot2 consumed regen potion!`. The potion name comes from a different API on 1.8, 1.9 to 1.20.1 and 1.20.2+. |
| display-time | With `Display Time: 10`, the bot gets a blank action bar about 10 ticks after it looks away from the cow. With the default `-1`, it gets no blank action bar. |
| log | The server log has no ActionHealth errors or warnings. |

`matrix.txt` has 27 Spigot versions: the last release of every minor version from 1.8 to 1.21, plus 26.1.2, 26.2 and 26.3. It also has every NMS revision of 1.8 and 1.9 (the only versions that use NMS), plus extra releases around API changes (1.13, 1.16.1, 1.16.3, 1.20.1, 1.20.4, 1.21.1, 1.21.4). It covers Java 8, 17, 21 and 25. Mineflayer does not support 26.2+ yet, so 26.2 and 26.3 run with ViaVersion and ViaBackwards and a 26.1 client.

`integrations.sh` runs:

- The WorldGuard region check on one server per WorldGuardWrapper implementation: WorldGuard 6.1 on 1.8.8, 6.2.2 on 1.12.2, 7.0.15 on 1.21.11 and 7.0.19 on 26.3. Inside `testing_region` (a default "Disabled regions" entry) there is no action bar. Outside it, the action bar works.
- The PlaceholderAPI check on 1.8.8 and 26.3: `%player_name%` in the health message and in the health icons shows the player name.
- The ModelEngine check on 1.20.4, with a test model that has a hitbox 3 blocks wide and 4 blocks high. The bot looks straight ahead at the model, looks up at its body from 4 and 7 blocks, and looks over it (no action bar). Then it hits the hitbox entity that ModelEngine shows. ModelEngine has no public download URL, so this check runs only if a `ModelEngine-*.jar` is in `compat/.cache/plugins`. The free [Legacy Model Engine Demo](https://www.spigotmc.org/resources/106521/) (R3) works.
- The main test on Paper 1.8.8, 1.20.6, 1.21.11 and 26.3. Paper changes how plugin reflection works on 1.20.5+, so Paper needs its own check.

To test a new Minecraft version, add a line to `matrix.txt` and run `matrix.sh`. The scripts also take a Paper jar: `run.sh <paper.jar> <version> <java> <plugin.jar>`.

### Results

| Server | 3.5.9 | 3.7.1 |
| --- | --- | --- |
| Spigot 1.8 to 1.16.5 on Java 8 (15 versions) | Fails: does not load (compiled for Java 16) | Pass |
| Spigot 1.17.1 to 1.20.6 (6 versions) | Pass | Pass |
| Spigot 1.21.1 to 26.3 (6 versions) | Fails: no action bar. Every message logs `ClassNotFoundException: net.minecraft.server.<revision>.PacketPlayOutChat` | Pass |
| Paper 1.8.8, 1.20.6, 1.21.11 and 26.3 | Not run | Pass |
| WorldGuard 6.1, 6.2.2, 7.0.15, 7.0.19 | Not run | Pass |
| PlaceholderAPI 2.12.3 on 1.8.8 and 26.3 | Not run | Pass |
| ModelEngine R3.1.11 on 1.20.4 | Not run | Pass. 3.7.0 failed the two checks that look up at the body |

3.6.0 failed on Paper 1.20.6. Paper's reflection remapper threw on `getMethod("getName", (Class<?>[]) null)`. 3.6.1 fixes this, and the Paper check is now part of `integrations.sh`.
