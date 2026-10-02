// Joins a running server with bots and checks the ActionHealth action bar end to end.
//
//   node test.js <host> <port> <clientVersion> <serverVersion> <serverDir>
//
// Scenarios:
//   look    - Bot1 looks at a cow and gets "Cow: 10/10 ..." ("Show On Look").
//   damage  - "Show On Look" off and /actionhealth reload, then Bot1 hits the cow and gets "Cow: <10/10".
//   toggle  - /actionhealth toggle stops the messages.
//   consume - Action system: Bot1 tags Bot2, Bot2 drinks a regeneration potion, Bot1 gets the CONSUME message.
//
// With MODE=worldguard (run.sh creates the WorldGuard region "testing_region" around spawn):
//   region-blocks  - no action bar inside the region, which is in "Disabled regions".
//   region-outside - the action bar works again 10000 blocks away, outside the region.
//
// With MODE=placeholderapi (run.sh adds %player_name% to the health message):
//   placeholderapi - the action bar shows the player name filled in by PlaceholderAPI.
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
  const record = (text) => bot.actionBars.push({ time: Date.now(), text })
  bot.on('actionBar', (message) => record(message.toString()))
  bot.on('messagestr', (text, position) => { if (position !== 'game_info') bot.chatLines.push(text) })
  // Fallback for protocol versions with a dedicated action bar packet.
  bot._client.on('packet', (data, meta) => {
    if (meta.name === 'action_bar' || meta.name === 'set_action_bar_text') {
      try { record(require('prismarine-chat')(bot.registry).fromNotch(data.text).toString()) } catch (e) { record(String(data.text)) }
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
  const end = Date.now() + timeout
  while (Date.now() < end) {
    const match = bot.actionBars.find((bar) => bar.time >= since && pattern.test(bar.text))
    if (match) return match.text
    await sleep(100)
  }
  return null
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
  const bar = await waitForBar(bot1, since, /Cow: 10\/10 Bot1/, 10000)
  record('placeholderapi', !!bar, bar || 'no "Cow: 10/10 Bot1" action bar, last: ' + lastBars(bot1))
  bot1.quit()
}

async function main () {
  await command('gamerule doMobSpawning false')
  const bot1 = await createBot('Bot1')
  await sleep(2000)

  // look
  const p = bot1.entity.position
  let since = Date.now()
  const cow = await cowInFront(bot1, Math.floor(p.x) + 0.5, Math.floor(p.y), Math.floor(p.z) + 0.5)
  let bar = await waitForBar(bot1, since, /Cow: 10\/10/, 10000)
  record('look', !!bar, bar || 'no "Cow: 10/10" action bar, last: ' + lastBars(bot1))

  // damage
  editConfig([['Show On Look: true', 'Show On Look: false']])
  await command('actionhealth reload')
  await sleep(1500)
  since = Date.now()
  bot1.attack(cow)
  bar = await waitForBar(bot1, since, /Cow: [0-9]\/10/, 10000)
  record('damage', !!bar, bar || 'no "Cow: <10/10" action bar, last: ' + lastBars(bot1))

  // toggle
  since = Date.now()
  bot1.chat('/actionhealth toggle')
  const disabled = await waitFor(() => bot1.chatLines.find((line) => line.includes('ActionHealth has been disabled')), 10000)
  await sleep(1000)
  const barSince = Date.now()
  bot1.attack(cow)
  const unexpected = await waitForBar(bot1, barSince, /Cow:/, 3000)
  record('toggle', !!disabled && !unexpected, disabled ? (unexpected ? 'still got: ' + unexpected : 'disabled and no action bar after hit') : 'no disable message')
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
  await sleep(700)
  bot1.attack(target)
  const damageBar = await waitForBar(bot1, since, /Bot2:/, 5000)

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
  record('consume', !!bar, bar ? `${bar} (damage event: ${damageBar})` : `no CONSUME action bar (Bot2 holds ${held}), last: ` + lastBars(bot1))

  bot2.quit()
  bot1.quit()
}

const modes = { worldguard: [worldGuard, 2], placeholderapi: [placeholderApi, 1] }
const [run, expected] = modes[process.env.MODE] || [main, 4]
run()
  .catch((error) => record('setup', false, error.stack))
  .finally(() => {
    const passed = results.length >= expected && results.every((r) => r.pass)
    console.log(`RESULT ${passed ? 'PASS' : 'FAIL'}`)
    setTimeout(() => process.exit(passed ? 0 : 1), 500)
  })
