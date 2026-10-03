// Joins a running server with bots and checks the ActionHealth action bar end to end.
//
//   node test.js <host> <port> <clientVersion> <serverVersion> <serverDir>
//
// Scenarios:
//   look    - Bot1 looks at a cow and gets "Cow: 10/10 ..." ("Show On Look").
//   hex     - The message color &#4fdfc4 arrives as that hex color (1.16+) or as aqua, the closest legacy color.
//   health-color - {healthcolor} before {health} makes "10/10" green, the 'Health Colors' color for full health.
//   absorption - The cow gets 8 absorption health. The bar shows "+8" and 8 absorption icons after the 10 health icons.
//   decimals - With "Health Decimals: 1", the bar shows "Cow: 10.0/10.0".
//   config-update - run.sh removes "Display Time" from the config. The plugin adds it back at the end
//                   of the file, with its comment, and keeps the rest of the file.
//   method  - The server log names the action bar method that works. With ACTIONBAR (run.sh starts
//             the plugin with that method), it must be that method.
//   damage  - "Show On Look" off and /actionhealth reload, then Bot1 hits the cow and gets "Cow: <10/10".
//   last-damage - The same message shows the damage of the hit with {opponentlastdamage}.
//   permission - Bot1 is not an operator. /actionhealth reload shows the no permission message.
//   toggle  - /actionhealth toggle stops the messages.
//   toggle-message - With a "Toggle Message", Bot1 (toggled off) gets it while looking at a cow,
//                    and no longer after looking away.
//   action-damage - Action system with the default "DAMAGE: ANY": one hit on Bot2 gives Bot1 one
//                   message, the ANY message, not the health message too.
//   consume - Action system: Bot1 tags Bot2, Bot2 drinks a regeneration potion, Bot1 gets the CONSUME message.
//   action-tags - Bot1 hit Bot2 twice, but gets the CONSUME message once (one tag for each target).
//   display-time - With "Display Time: 10", the bar is cleared about 10 ticks after Bot1 looks away.
//                  No clear message arrives while Bot1 still looks at the cow.
//                  Before that, with the default -1, no clear message is sent.
//   update-check - run.sh makes the update check read 99.0.0. The server log names the new version,
//                  and Bot3, an operator, gets the 'Update Message' when they join.
//   reload-message - Bot3 runs /actionhealth reload and gets the 'Reload Message'.
//   default-off - With "Enabled By Default: false", Bot3 gets no action bar while looking at a cow.
//                 After /actionhealth toggle, Bot3 gets it.
//
// With MODE=worldguard (run.sh creates the WorldGuard region "testing_region" around spawn):
//   region-blocks  - no action bar inside the region, which is in "Disabled regions".
//   region-outside - the action bar works again 10000 blocks away, outside the region.
//
// With MODE=placeholderapi (run.sh adds %player_name% to the health message):
//   placeholderapi - the action bar shows the player name filled in by PlaceholderAPI.
//   placeholderapi-style - PlaceholderAPI placeholders in the health icons are filled in too.
//
// With MODE=modelengine (run.sh adds the test model fixtures/bigmob.bbmodel: a hitbox 3 blocks wide
// and 4 blocks high, with the body at the top):
//   model-look-level - Bot1 looks straight ahead at the model from 4 blocks and gets "Cow: 10/10".
//   model-look-body  - Bot1 looks up at the body from 4 blocks (40 degrees up) and gets the action bar.
//   model-look-far   - Bot1 looks up at the body from 7 blocks (20 degrees up) and gets the action bar.
//   model-look-over  - Bot1 looks over the model (80 degrees up) and gets no action bar.
//   model-hit        - Bot1 hits the hitbox entity that ModelEngine shows and gets "Cow: <10/10".
// Exit code 0 means every scenario passed.
//
// When the server runs ViaBackwards to translate for an older client (VIA=1), the bots
// send no movement packets, because translated movement can get the bot kicked. The test turns
// the bot with /tp instead, which is the server-side rotation that ActionHealth reads anyway.

const fs = require('fs')
const path = require('path')
const mineflayer = require('mineflayer')

const [host, port, clientVersion, serverVersion, serverDir] = process.argv.slice(2)
const configPath = path.join(serverDir, 'plugins', 'ActionHealth', 'config.yml')
const consolePath = path.join(serverDir, 'console.in')
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
const results = []

function versionAtLeast (version, minimum) {
  const a = version.split('.').map(Number)
  const b = minimum.split('.').map(Number)
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    if ((a[i] || 0) !== (b[i] || 0)) return (a[i] || 0) > (b[i] || 0)
  }
  return true
}

// Runs a server console command. run.sh pipes console.in into the server's standard input,
// which works the same on every Minecraft version.
async function command (line) {
  fs.appendFileSync(consolePath, line + '\n')
  await sleep(700)
}

function editConfig (replacements) {
  let text = fs.readFileSync(configPath, 'utf8')
  for (const [from, to] of replacements) {
    if (!text.includes(from)) throw new Error('config does not contain: ' + from)
    text = text.replace(from, to)
  }
  fs.writeFileSync(configPath, text)
}

function createBot (username) {
  const bot = mineflayer.createBot({ host, port: Number(port), username, version: clientVersion, auth: 'offline' })
  bot.actionBars = []
  bot.chatLines = []
  const record = (text, raw) => bot.actionBars.push({ time: Date.now(), text, raw })
  bot.on('actionBar', (message) => record(message.toString(), JSON.stringify(message.json)))
  bot.on('messagestr', (text, position) => { if (position !== 'game_info') bot.chatLines.push(text) })
  // Fallback for protocol versions with a dedicated action bar packet, and for the title packet
  // with the action bar action (2), which Paper's Adventure API uses on 1.11 to 1.16.
  bot._client.on('packet', (data, meta) => {
    if (meta.name === 'action_bar' || meta.name === 'set_action_bar_text' || (meta.name === 'title' && data.action === 2)) {
      try {
        const message = require('prismarine-chat')(bot.registry).fromNotch(data.text)
        record(message.toString(), JSON.stringify(message.json))
      } catch (e) { record(String(data.text), JSON.stringify(data.text)) }
    }
  })
  if (process.env.VIA === '1') {
    const write = bot._client.write.bind(bot._client)
    bot._client.write = (name, params) => {
      if (['flying', 'position', 'position_look', 'look'].includes(name)) return
      return write(name, params)
    }
  }
  bot.on('kicked', (reason) => console.log(`${username} kicked: ${JSON.stringify(reason)}`))
  bot.on('error', (error) => console.log(`${username} error: ${error.message}`))
  return new Promise((resolve, reject) => {
    bot.once('spawn', () => resolve(bot))
    bot.once('end', (reason) => reject(new Error(`${username} disconnected: ${reason}`)))
    setTimeout(() => reject(new Error(`${username} spawn timeout`)), 60000)
  })
}

async function waitForBar (bot, since, pattern, timeout) {
  const match = await waitForBarEntry(bot, since, pattern, timeout)
  return match ? match.text : null
}

async function waitForBarEntry (bot, since, pattern, timeout) {
  const end = Date.now() + timeout
  while (Date.now() < end) {
    const match = bot.actionBars.find((bar) => bar.time >= since && pattern.test(bar.text))
    if (match) return match
    await sleep(100)
  }
  return null
}

// The clear message that "Display Time" sends.
const isBlank = (bar) => bar.text.trim() === ''

function effectCommands () {
  if (!versionAtLeast(serverVersion, '1.13')) {
    const cows = `@e[type=${legacyEntityIds() ? 'Cow' : 'cow'}]`
    return { give: `effect ${cows} 22 60 1`, clear: `effect ${cows} clear` }
  }
  return { give: 'effect give @e[type=minecraft:cow] minecraft:absorption 60 1', clear: 'effect clear @e[type=minecraft:cow]' }
}

async function waitFor (predicate, timeout) {
  const end = Date.now() + timeout
  while (Date.now() < end) {
    const value = predicate()
    if (value) return value
    await sleep(100)
  }
  return null
}

function record (name, pass, detail) {
  results.push({ name, pass })
  console.log(`${pass ? 'PASS' : 'FAIL'} ${name}: ${detail}`)
}

function lastBars (bot) {
  return JSON.stringify(bot.actionBars.slice(-3).map((bar) => bar.text))
}

function legacyEntityIds () {
  return !versionAtLeast(serverVersion, '1.11')
}

function potionCommand (player) {
  if (!versionAtLeast(serverVersion, '1.9')) return `give ${player} potion 1 8193`
  if (!versionAtLeast(serverVersion, '1.13')) return `give ${player} potion 1 0 {Potion:"minecraft:regeneration"}`
  if (!versionAtLeast(serverVersion, '1.20.5')) return `give ${player} minecraft:potion{Potion:"minecraft:regeneration"} 1`
  return `give ${player} minecraft:potion[potion_contents={potion:"minecraft:regeneration"}] 1`
}

// Summons a cow 3 blocks south of (x, y, z) and turns Bot1 to face it from (x, y, z).
async function cowInFront (bot, x, y, z) {
  const summon = `summon ${legacyEntityIds() ? 'Cow' : 'minecraft:cow'} ${x} ${y} ${z + 3} {NoAI:1b}`
  await command(summon)
  // Yaw 0 faces south (+z).
  await command(`tp ${bot.username} ${x} ${y} ${z} 0 0`)
  const cow = await waitFor(() => bot.nearestEntity((e) => (e.name || '').toLowerCase() === 'cow' &&
    e.position.distanceTo(bot.entity.position) < 5), 10000)
  if (!cow) throw new Error(`cow did not appear after "${summon}" (bot at ${bot.entity.position})`)
  return cow
}

async function worldGuard () {
  await command('gamerule doMobSpawning false')
  const bot1 = await createBot('Bot1')
  await sleep(2000)
  const p = bot1.entity.position
  const y = Math.floor(p.y)

  let since = Date.now()
  await cowInFront(bot1, Math.floor(p.x) + 0.5, y, Math.floor(p.z) + 0.5)
  let bar = await waitForBar(bot1, since, /Cow:/, 4000)
  record('region-blocks', !bar, bar ? 'got action bar inside disabled region: ' + bar : 'no action bar inside "testing_region"')

  await command(`tp Bot1 10000.5 ${y} 10000.5 0 0`)
  await sleep(4000)
  since = Date.now()
  await cowInFront(bot1, 10000.5, y, 10000.5)
  bar = await waitForBar(bot1, since, /Cow: 10\/10/, 10000)
  record('region-outside', !!bar, bar || 'no action bar outside the region, last: ' + lastBars(bot1))
  bot1.quit()
}

async function placeholderApi () {
  await command('gamerule doMobSpawning false')
  await command('papi ecloud download Player')
  await sleep(5000)
  await command('papi reload')
  await sleep(3000)
  const bot1 = await createBot('Bot1')
  await sleep(2000)
  const p = bot1.entity.position
  const since = Date.now()
  await cowInFront(bot1, Math.floor(p.x) + 0.5, Math.floor(p.y), Math.floor(p.z) + 0.5)
  const bar = await waitForBar(bot1, since, /Cow: 10\/10 \+0 Bot1/, 10000)
  record('placeholderapi', !!bar, bar || 'no "Cow: 10/10 +0 Bot1" action bar, last: ' + lastBars(bot1))
  // run.sh sets "Full Health Icon" to %player_name%.
  record('placeholderapi-style', !!bar && bar.includes('Bot1Bot1Bot1'), bar || 'no action bar')
  bot1.quit()
}

async function modelEngine () {
  await command('gamerule doMobSpawning false')
  // ModelEngine imports the model after the server starts.
  const log = path.join(serverDir, 'logs', 'latest.log')
  const imported = await waitFor(() => fs.readFileSync(log, 'utf8').includes('Resource pack zipped'), 60000)
  if (!imported) throw new Error('ModelEngine did not import the test model')
  const bot1 = await createBot('Bot1')
  await sleep(2000)
  await command('op Bot1')
  const p = bot1.entity.position
  const x = Math.floor(p.x) + 0.5
  const y = Math.floor(p.y)
  const z = Math.floor(p.z) + 0.5
  // /meg summon <model> <type> spawns the model at the player, as a MythicMobs "model" skill does.
  bot1.chat('/meg summon bigmob cow')
  await sleep(2000)
  await command('execute as @e[type=minecraft:cow] run data merge entity @s {NoAI:1b}')
  await command(`tp @e[type=minecraft:cow] ${x} ${y} ${z}`)

  // Yaw 180 faces north, toward the model. A negative pitch looks up.
  async function look (name, distance, pitch, expected, detail) {
    await command(`tp Bot1 ${x} ${y} ${z + distance} 180 ${pitch}`)
    // Let the server apply the teleport before reading new messages.
    await sleep(500)
    const since = Date.now()
    const bar = await waitForBar(bot1, since, /Cow: 10\/10/, expected ? 5000 : 2500)
    record(name, !!bar === expected, bar ? `${detail}: ${bar}` : `${detail}: no action bar`)
  }
  await look('model-look-level', 4, 0, true, 'level from 4 blocks')
  await look('model-look-body', 4, -40, true, '40 degrees up from 4 blocks')
  await look('model-look-far', 7, -20, true, '20 degrees up from 7 blocks')
  await look('model-look-over', 4, -80, false, '80 degrees up from 4 blocks')

  // The client does not see the cow, only the model and a hitbox entity. ModelEngine sends a hit
  // on the hitbox entity to the cow.
  editConfig([['Show On Look: true', 'Show On Look: false']])
  await command('actionhealth reload')
  await command(`tp Bot1 ${x} ${y} ${z + 2.5} 180 0`)
  await sleep(1500)
  const hitbox = bot1.nearestEntity((e) => e !== bot1.entity && e.type !== 'player' && e.name !== 'armor_stand')
  if (!hitbox) throw new Error('Bot1 can not see a hitbox entity')
  const since = Date.now()
  bot1.attack(hitbox)
  const bar = await waitForBar(bot1, since, /Cow: [0-9]\/10/, 5000)
  record('model-hit', !!bar, bar ? `hit ${hitbox.name || 'entity'} ${hitbox.id}: ${bar}` : 'no "Cow: <10/10" action bar, last: ' + lastBars(bot1))
  bot1.quit()
}

async function main () {
  await command('gamerule doMobSpawning false')
  const bot1 = await createBot('Bot1')
  await sleep(2000)

  // config-update
  const configText = fs.readFileSync(configPath, 'utf8')
  const serverLog = fs.readFileSync(path.join(serverDir, 'logs', 'latest.log'), 'utf8')
  const addedAt = configText.indexOf('# Options added by ActionHealth')
  const updated = /Added new options to config\.yml: Display Time/.test(serverLog) && addedAt > 0 &&
    configText.indexOf('Display Time: -1') > addedAt && configText.startsWith('# The message the player is sent.')
  record('config-update', updated, updated ? '"Display Time" added at the end of config.yml' : 'config.yml end: ' + JSON.stringify(configText.slice(-300)))

  // look
  const p = bot1.entity.position
  let since = Date.now()
  const cow = await cowInFront(bot1, Math.floor(p.x) + 0.5, Math.floor(p.y), Math.floor(p.z) + 0.5)
  const lookBar = await waitForBarEntry(bot1, since, /Cow: 10\/10/, 10000)
  let bar = lookBar && lookBar.text
  record('look', !!bar, bar || 'no "Cow: 10/10" action bar, last: ' + lastBars(bot1))

  // method: the plugin logs the method after the first action bar that works.
  const methods = { spigot: 'Spigot API', adventure: 'Adventure API', paper: 'Paper API', packet: 'NMS packet' }
  const methodLine = fs.readFileSync(path.join(serverDir, 'logs', 'latest.log'), 'utf8').match(/Sending action bars with the ([A-Za-z ]+)\./)
  const expectedMethod = methods[process.env.ACTIONBAR]
  record('method', !!methodLine && (!expectedMethod || methodLine[1] === expectedMethod),
    methodLine ? methodLine[1] + (expectedMethod ? ` (expected ${expectedMethod})` : '') : 'no "Sending action bars with the" line in the server log')

  // hex: run.sh starts the health message with &#4fdfc4.
  // The code must not show as text. That is what the bot gets if the plugin does not translate it.
  // Before 1.16, some Spigot versions send action bars as legacy text, where \u00a7b is aqua.
  const hexColor = versionAtLeast(serverVersion, '1.16') ? /"color":"#4fdfc4"/i : /"color":"aqua"|^\{"text":"\u00a7b/
  record('hex', !!lookBar && hexColor.test(lookBar.raw) && !/4fdfc4/i.test(lookBar.text), lookBar ? lookBar.raw : 'no action bar')

  // health-color: run.sh puts {healthcolor} before {health}. The cow has full health, so the color is
  // &a (green). No other part of the message is green. Some versions send legacy text, where \u00a7a is green.
  record('health-color', !!lookBar && /"color":"green"|\u00a7a/.test(lookBar.raw), lookBar ? lookBar.raw : 'no action bar')

  // absorption
  const effects = effectCommands()
  since = Date.now()
  await command(effects.give)
  const absorptionBar = await waitForBarEntry(bot1, since, /Cow: 10\/10 \+8 /, 10000)
  bar = absorptionBar && absorptionBar.text
  // Count in the raw message: mineflayer's toString stops at a nesting depth that 1.8 messages reach.
  const icons = absorptionBar ? (absorptionBar.raw.match(/\u2764/g) || []).length : 0
  record('absorption', !!bar && icons === 18, bar ? `${bar} (${icons} icons, expected 18)` : 'no "+8" action bar, last: ' + lastBars(bot1))
  await command(effects.clear)
  await sleep(500)

  // decimals: Bot1 still looks at the cow.
  editConfig([['Health Decimals: 0', 'Health Decimals: 1']])
  await command('actionhealth reload')
  since = Date.now()
  bar = await waitForBar(bot1, since, /Cow: 10\.0\/10\.0/, 5000)
  record('decimals', !!bar, bar || 'no "Cow: 10.0/10.0" action bar, last: ' + lastBars(bot1))
  editConfig([['Health Decimals: 1', 'Health Decimals: 0']])
  await command('actionhealth reload')

  // damage
  editConfig([['Show On Look: true', 'Show On Look: false']])
  await command('actionhealth reload')
  await sleep(1500)
  since = Date.now()
  bot1.attack(cow)
  const damageBar = await waitForBarEntry(bot1, since, /Cow: [0-9]\/10/, 10000)
  bar = damageBar && damageBar.text
  record('damage', !!bar, bar || 'no "Cow: <10/10" action bar, last: ' + lastBars(bot1))

  // last-damage: the cow had 10 health, so the damage of the hit is 10 minus the health in the message.
  // Read the damage from the raw message, because mineflayer's toString stops early on 1.8.
  const health = bar && bar.match(/Cow: ([0-9]+)\/10/)
  const dealt = damageBar && damageBar.raw.match(/ d([0-9]+)"/)
  const lastDamageOk = !!health && !!dealt && Number(dealt[1]) === 10 - Number(health[1]) && Number(dealt[1]) > 0
  record('last-damage', lastDamageOk, damageBar ? `${bar} (last damage ${dealt ? dealt[1] : 'missing'})` : 'no action bar')

  // permission
  bot1.chat('/actionhealth reload')
  const denied = await waitFor(() => bot1.chatLines.find((line) => line.includes('You do not have permission to do that')), 5000)
  record('permission', !!denied, denied || 'no permission message, chat: ' + JSON.stringify(bot1.chatLines.slice(-3)))

  // toggle
  since = Date.now()
  bot1.chat('/actionhealth toggle')
  const disabled = await waitFor(() => bot1.chatLines.find((line) => line.includes('ActionHealth has been disabled')), 10000)
  await sleep(1000)
  const barSince = Date.now()
  bot1.attack(cow)
  const unexpected = await waitForBar(bot1, barSince, /Cow:/, 3000)
  record('toggle', !!disabled && !unexpected, disabled ? (unexpected ? 'still got: ' + unexpected : 'disabled and no action bar after hit') : 'no disable message')

  // toggle-message: Bot1 is still toggled off.
  editConfig([["Toggle Message: ''", "Toggle Message: 'AH off for {name}'"], ['Show On Look: false', 'Show On Look: true']])
  await command('actionhealth reload')
  await sleep(1000)
  const t = bot1.entity.position
  since = Date.now()
  await cowInFront(bot1, Math.floor(t.x) + 0.5, Math.floor(t.y), Math.floor(t.z) + 0.5)
  const toggleBar = await waitForBar(bot1, since, /AH off for Bot1/, 5000)
  // Yaw 180 faces north, away from the cows.
  await command(`tp Bot1 ${Math.floor(t.x) + 0.5} ${Math.floor(t.y)} ${Math.floor(t.z) + 0.5} 180 0`)
  await sleep(1000)
  const quietSince = Date.now()
  await sleep(2500)
  const afterAway = bot1.actionBars.filter((b) => b.time >= quietSince && /AH off/.test(b.text)).length
  record('toggle-message', !!toggleBar && afterAway === 0,
    !toggleBar ? 'no toggle message while looking at a cow, last: ' + lastBars(bot1)
      : afterAway ? `${afterAway} toggle messages after looking away` : 'toggle message only while looking at a cow')
  editConfig([["Toggle Message: 'AH off for {name}'", "Toggle Message: ''"], ['Show On Look: true', 'Show On Look: false']])
  await command('actionhealth reload')
  bot1.chat('/actionhealth toggle')
  await waitFor(() => bot1.chatLines.find((line) => line.includes('ActionHealth has been enabled')), 10000)

  // consume
  editConfig([['Action:\n  Enabled: false', 'Action:\n  Enabled: true']])
  await command('actionhealth reload')
  const bot2 = await createBot('Bot2')
  await sleep(1500)
  await command('tp Bot2 Bot1')
  await command(potionCommand('Bot2'))
  await sleep(1500)

  since = Date.now()
  const target = await waitFor(() => bot1.players.Bot2 && bot1.players.Bot2.entity, 10000)
  if (!target) throw new Error('Bot1 can not see Bot2')
  bot1.attack(target)
  await sleep(1500)
  // action-damage: the ANY message has the health icons but not the "health/max" of the health message.
  const hitBars = bot1.actionBars.filter((b) => b.time >= since && /Bot2:/.test(b.text)).map((b) => b.text)
  record('action-damage', hitBars.length === 1 && !/Bot2: [0-9]+\/20/.test(hitBars[0]),
    `${hitBars.length} messages for one hit: ${JSON.stringify(hitBars)}`)
  bot1.attack(target)
  const tagBar = await waitForBar(bot1, since, /Bot2:/, 5000)

  const potion = bot2.inventory.items().find((item) => item.name === 'potion')
  if (potion) {
    await bot2.equip(potion, 'hand')
  } else {
    // Some mineflayer protocol versions can not parse potion item components. /give puts the
    // potion in the first hotbar slot, so drink from there.
    bot2.setQuickBarSlot(0)
  }
  // Let the server apply the held slot before using the item. Drink again only if the first
  // use did not start (the potion is still in hand).
  await sleep(500)
  since = Date.now()
  bar = null
  for (let attempt = 0; attempt < 2 && !bar; attempt++) {
    if (attempt > 0 && !(bot2.heldItem && bot2.heldItem.name === 'potion')) break
    bot2.activateItem()
    bar = await waitForBar(bot1, since, /Bot2 consumed regen potion/, 5000)
    bot2.deactivateItem()
  }
  const held = bot2.heldItem ? bot2.heldItem.name : 'nothing'
  record('consume', !!bar, bar ? `${bar} (damage event: ${tagBar})` : `no CONSUME action bar (Bot2 holds ${held}), last: ` + lastBars(bot1))
  await sleep(1000)
  const consumed = bot1.actionBars.filter((b) => b.time >= since && /Bot2 consumed regen potion/.test(b.text)).length
  record('action-tags', consumed === 1, `${consumed} CONSUME messages after two hits on Bot2`)
  bot2.quit()

  // display-time
  const earlyBlank = bot1.actionBars.find(isBlank)
  editConfig([['Display Time: -1', 'Display Time: 10'], ['Show On Look: false', 'Show On Look: true']])
  await command('actionhealth reload')
  await sleep(1000)
  const q = bot1.entity.position
  since = Date.now()
  await cowInFront(bot1, Math.floor(q.x) + 0.5, Math.floor(q.y), Math.floor(q.z) + 0.5)
  const seen = await waitForBar(bot1, since, /Cow:/, 10000)
  // Keep looking for 2 seconds, longer than Display Time. Each health message restarts the timer,
  // so no clear message may arrive while Bot1 still looks at the cow.
  await sleep(2000)
  const blankWhileLooking = bot1.actionBars.find((b) => b.time >= since && isBlank(b))
  // Yaw 180 faces north, away from the cow. The look check sends the same message only once a
  // second, but restarts the timer on each check. So the time counts from when Bot1 looks away.
  const lookedAway = Date.now()
  await command(`tp Bot1 ${Math.floor(q.x) + 0.5} ${Math.floor(q.y)} ${Math.floor(q.z) + 0.5} 180 0`)
  await sleep(3000)
  const cleared = bot1.actionBars.find((b) => b.time > lookedAway && isBlank(b))
  const delay = cleared ? cleared.time - lookedAway : null
  // 10 ticks is 500 ms. Allow for the console command and server tick timing.
  const onTime = delay !== null && delay >= 300 && delay <= 1500
  record('display-time', !!seen && onTime && !earlyBlank && !blankWhileLooking,
    earlyBlank ? 'got a clear message while Display Time was -1'
      : blankWhileLooking ? 'got a clear message while still looking at the cow'
        : cleared ? `cleared ${delay} ms after Bot1 looked away` : 'no clear message, last: ' + lastBars(bot1))

  // update-check: the plugin checks 5 seconds after it starts, long before this point.
  const updateLog = /ActionHealth 99\.0\.0 is available\. This server runs [0-9.]+\./.test(
    fs.readFileSync(path.join(serverDir, 'logs', 'latest.log'), 'utf8'))
  editConfig([['Enabled By Default: true', 'Enabled By Default: false']])
  await command('actionhealth reload')
  // Bot3 joins once, so the server knows the player, and joins again as an operator.
  let bot3 = await createBot('Bot3')
  await command('op Bot3')
  bot3.quit()
  await sleep(1500)
  bot3 = await createBot('Bot3')
  const notice = await waitFor(() => bot3.chatLines.find((line) => line.includes('ActionHealth 99.0.0 is available')), 8000)
  record('update-check', updateLog && !!notice,
    !updateLog ? 'no "ActionHealth 99.0.0 is available" line in the server log'
      : notice || 'no update message for Bot3, chat: ' + JSON.stringify(bot3.chatLines.slice(-3)))

  // reload-message
  bot3.chat('/actionhealth reload')
  const reloaded = await waitFor(() => bot3.chatLines.find((line) => line.includes('ActionHealth has been reloaded!')), 5000)
  record('reload-message', !!reloaded, reloaded || 'no reload message, chat: ' + JSON.stringify(bot3.chatLines.slice(-3)))

  // default-off: Bot3 never turned ActionHealth on.
  const r = bot3.entity.position
  since = Date.now()
  await cowInFront(bot3, Math.floor(r.x) + 0.5, Math.floor(r.y), Math.floor(r.z) + 0.5)
  const offBar = await waitForBar(bot3, since, /Cow:/, 3000)
  bot3.chat('/actionhealth toggle')
  const turnedOn = await waitFor(() => bot3.chatLines.find((line) => line.includes('ActionHealth has been enabled')), 5000)
  since = Date.now()
  const onBar = await waitForBar(bot3, since, /Cow:/, 5000)
  record('default-off', !offBar && !!turnedOn && !!onBar,
    offBar ? 'got an action bar before turning ActionHealth on: ' + offBar
      : !turnedOn ? 'no enable message' : onBar ? 'no action bar until /actionhealth toggle, then: ' + onBar
        : 'no action bar after /actionhealth toggle, last: ' + lastBars(bot3))
  bot3.quit()

  bot1.quit()
}

const modes = { worldguard: [worldGuard, 2], placeholderapi: [placeholderApi, 2], modelengine: [modelEngine, 5] }
const [run, expected] = modes[process.env.MODE] || [main, 19]
run()
  .catch((error) => record('setup', false, error.stack))
  .finally(() => {
    const passed = results.length >= expected && results.every((r) => r.pass)
    console.log(`RESULT ${passed ? 'PASS' : 'FAIL'}`)
    setTimeout(() => process.exit(passed ? 0 : 1), 500)
  })
