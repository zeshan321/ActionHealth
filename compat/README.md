# Version compatibility

ActionHealth ships one jar for every Spigot version from 1.8 to the current release, on any Java version from 8 up.

## How the plugin stays version independent

- **Compiled against the oldest API.** The build uses `spigot-api:1.8.8` and `--release 8`. Any newer API must go through feature detection in `utils/Compat.java` or `support/ActionBar.java`.
- **No version strings.** Nothing reads the CraftBukkit package name.
- **Action bar:** the plugin uses the first method that exists:
  1. The Spigot API `Player.Spigot#sendMessage(ChatMessageType, BaseComponent...)` (1.9.2 and later).
  2. Paper's Adventure API `Audience#sendActionBar(Component)` (Paper 1.16.5 and later). Paper deprecated the BungeeCord chat API that the Spigot method uses, so this method is ready if Paper removes it.
  3. Paper's `Player#sendActionBar(String)`.
  4. An NMS chat packet (1.8 to 1.9 only, before the Spigot API existed).

  If a method fails before it has worked, the plugin logs it and moves to the next method. The log names the method that works: `Sending action bars with the Spigot API.` The system property `-Dactionhealth.actionbar=adventure` (or `spigot`, `paper`, `packet`) starts with a later method, so the tests can check it.
- **Optional plugins** (WorldGuard 6 and 7, PlaceholderAPI, MVdWPlaceholderAPI, MythicMobs 4 and 5, LangUtils) are called through reflection. They need no compile dependency, and the jar includes no library for them.
- **Schedulers:** Folia has a thread for each region of the world and no main thread. On Folia, `utils/Scheduler.java` uses the Folia schedulers through reflection, and the look check for each player runs on the thread of that player. An arrow can hit an entity in a region that another thread owns. The health message then runs on the thread of the player who shot it. On other servers, the plugin uses the Bukkit scheduler.

## Unit tests (run with every build)

`./gradlew build` runs the JUnit tests in `src/test`. They cover the parts that need no server: the health icons, the color conversion, the line of sight math, the config update, the saved toggle choices and the combat tags of the action system.

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
| config-update | `run.sh` removes `Display Time` from the config, as in a config from before 3.7.0. The plugin adds it back at the end of the file, with its comment, and the rest of the file stays the same. |
| method | The server log names the action bar method that works. With `ACTIONBAR=adventure` (see below), it must be the Adventure API. |
| damage | Show On Look is turned off with `/actionhealth reload`. The bot hits the cow and gets `Cow: 9/10 ...`. |
| last-damage | The same message shows the damage of the hit with `{opponentlastdamage}`. |
| permission | The bot is not an operator. `/actionhealth reload` shows the no permission message. |
| toggle | `/actionhealth toggle` stops the messages. |
| toggle-message | With a `Toggle Message`, the bot (still toggled off) gets it while it looks at a cow. It gets no toggle message after it looks away. |
| action-damage | Action system with the default `DAMAGE: ANY`: one hit on Bot2 gives Bot1 one message, the ANY message. On 3.8.0, the first hit gave the health message instead. |
| consume | Action system: Bot1 tags Bot2, Bot2 drinks a regeneration potion, Bot1 gets `Bot2 consumed regen potion!`. The potion name comes from a different API on 1.8, 1.9 to 1.20.1 and 1.20.2+. |
| action-tags | Bot1 hit Bot2 twice, but gets the consume message once. Before 3.8.1, each hit added a tag, and each tag sent the message. |
| display-time | With `Display Time: 10`, the bot gets a blank action bar about 10 ticks after it looks away from the cow. It gets no blank action bar while it looks at the cow, or with the default `-1`. |
| log | The server log has no ActionHealth errors or warnings. |

`matrix.txt` has 27 Spigot versions: the last release of every minor version from 1.8 to 1.21, plus 26.1.2, 26.2 and 26.3. It also has every NMS revision of 1.8 and 1.9 (the only versions that use NMS), plus extra releases around API changes (1.13, 1.16.1, 1.16.3, 1.20.1, 1.20.4, 1.21.1, 1.21.4). It covers Java 8, 17, 21 and 25. Mineflayer does not support 26.2+ yet, so 26.2 and 26.3 run with ViaVersion and ViaBackwards and a 26.1 client.

`integrations.sh` runs:

- The WorldGuard region check on WorldGuard 6.1 on 1.8.8, 6.2.2 on 1.12.2, 7.0.15 on 1.21.11 and 7.0.19 on 26.3. WorldGuard 6 and 7 have different APIs. Inside `testing_region` (a default "Disabled regions" entry) there is no action bar. Outside it, the action bar works.
- The PlaceholderAPI check on 1.8.8 and 26.3: `%player_name%` in the health message and in the health icons shows the player name.
- The ModelEngine check on 1.20.4, with a test model that has a hitbox 3 blocks wide and 4 blocks high. The bot looks straight ahead at the model, looks up at its body from 4 and 7 blocks, and looks over it (no action bar). Then it hits the hitbox entity that ModelEngine shows. ModelEngine has no public download URL, so this check runs only if a `ModelEngine-*.jar` is in `compat/.cache/plugins`. The free [Legacy Model Engine Demo](https://www.spigotmc.org/resources/106521/) (R3) works.
- The main test on Paper 1.8.8, 1.20.6, 1.21.11 and 26.3. Paper changes how plugin reflection works on 1.20.5+, so Paper needs its own check.
- The main test on Paper 1.16.5 (Java 8) with `ACTIONBAR=adventure`. 1.16.5 is the oldest Paper version with Adventure. On 1.11 to 1.16, Adventure sends the action bar in a title packet, and the bot reads that packet too.
- The main test on Folia 1.21.11 and 26.2. Folia runs events and commands on many threads.

To test a new Minecraft version, add a line to `matrix.txt` and run `matrix.sh`. The scripts also take a Paper or Folia jar: `run.sh <paper.jar> <version> <java> <plugin.jar>`. To test one action bar method, set `ACTIONBAR`, for example `ACTIONBAR=adventure run.sh ...`.

## End-to-end tests in CI

`compat/e2e/ci.sh` runs the main test on 5 servers: Spigot 1.8.8, Spigot 1.21.11, Paper 26.3, Paper 1.21.11 with the Adventure method, and Folia 1.21.11. The GitHub workflow runs it on every push and once a week. Together, these servers use each way of sending the action bar and each type of scheduler. Before a release, run `matrix.sh` and `integrations.sh` for the full set.

## Releases

1. Set the new version in `build.gradle`, merge the change and run the full tests.
2. Push an annotated tag with the version as its name, for example `git tag -a 3.8.0 -F notes.md`. The first line of the message is the title of the release, and the rest is the release text.
3. The release workflow checks that the tag matches `build.gradle`. Then it builds the jar, runs the linkage check and creates the GitHub release with the jar and its SHA-256. The build is reproducible: the same commit and JDK give the same jar. So the SHA-256 of the release should match the jar that CI built for that commit.
4. Upload the jar to the Spigot page by hand. Spigot has no API for uploads.

### Results

| Server | 3.5.9 | 3.8.0 | 3.8.1 |
| --- | --- | --- | --- |
| Spigot 1.8 to 1.16.5 on Java 8 (15 versions) | Fails: does not load (compiled for Java 16) | Pass | Pass |
| Spigot 1.17.1 to 1.20.6 (6 versions) | Pass | Pass | Pass |
| Spigot 1.21.1 to 26.3 (6 versions) | Fails: no action bar. Every message logs `ClassNotFoundException: net.minecraft.server.<revision>.PacketPlayOutChat` | Pass | Pass |
| Paper 1.8.8, 1.20.6, 1.21.11 and 26.3 | Not run | Pass | Pass |
| Paper 1.16.5 and 1.21.11 with the Adventure method | Not run | Not run (no Adventure method) | Pass |
| Folia 1.21.11 and 26.2 | Not run | Pass. Folia does not load 3.7.1 and older, because their plugin.yml does not have `folia-supported: true` | Pass |
| WorldGuard 6.1, 6.2.2, 7.0.15, 7.0.19 | Not run | Pass | Pass, without WorldGuardWrapper |
| PlaceholderAPI 2.12.3 on 1.8.8 and 26.3 | Not run | Pass | Pass |
| ModelEngine R3.1.11 on 1.20.4 | Not run | Pass. 3.7.0 failed the two checks that look up at the body | Pass |

On Spigot 1.21.11, 3.8.0 fails the new checks method, toggle-message, action-damage and action-tags. 3.8.1 passes them.

3.6.0 failed on Paper 1.20.6. Paper's reflection remapper threw on `getMethod("getName", (Class<?>[]) null)`. 3.6.1 fixes this, and the Paper check is now part of `integrations.sh`.
