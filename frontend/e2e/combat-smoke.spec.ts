import { expect, test } from '@playwright/test'

test('guest onboarding can reach combat with mocked backend transport', async ({ page }) => {
  const state = {
    starterClaimed: false,
    teamAssigned: false,
    activeTeam: false,
  }

  const player = () => ({
    id: 'player-1',
    name: 'Scout',
    ownedCharacterKeys: state.starterClaimed ? ['hero'] : [],
    activeTeamId: state.activeTeam ? 'team-1' : null,
    experience: 0,
    level: 1,
    gold: 250,
    essence: 0,
  })

  const team = () => ({
    id: 'team-1',
    playerId: 'player-1',
    characterKeys: state.teamAssigned ? ['hero'] : [],
    slots: [
      {
        position: 1,
        characterKey: state.teamAssigned ? 'hero' : null,
        weaponItemId: null,
        armorItemId: null,
        accessoryItemId: null,
      },
      { position: 2, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
      { position: 3, characterKey: null, weaponItemId: null, armorItemId: null, accessoryItemId: null },
    ],
  })

  const fulfillJson = async (url: string, body: unknown) => {
    await page.route(url, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(body),
      })
    })
  }

  await page.addInitScript(() => {
    class MockWebSocket {
      static CONNECTING = 0
      static OPEN = 1
      static CLOSING = 2
      static CLOSED = 3

      readyState = MockWebSocket.CONNECTING
      listeners = new Map()

      constructor() {
        window.__lastSocket = this
        setTimeout(() => {
          this.readyState = MockWebSocket.OPEN
          this.dispatch('open', new Event('open'))
        }, 0)
      }

      addEventListener(type, listener) {
        const listeners = this.listeners.get(type) ?? []
        listeners.push(listener)
        this.listeners.set(type, listeners)
      }

      removeEventListener(type, listener) {
        const listeners = this.listeners.get(type) ?? []
        this.listeners.set(
          type,
          listeners.filter((entry) => entry !== listener),
        )
      }

      dispatch(type, event) {
        const listeners = this.listeners.get(type) ?? []
        for (const listener of listeners) {
          listener.call(this, event)
        }
      }

      send(data) {
        window.__wsMessages.push(data)
        const parsed = JSON.parse(data)
        if (parsed.type === 'START_COMBAT') {
          setTimeout(() => {
            window.__pushSocketMessage({
              type: 'COMBAT_STATE_SYNC',
              playerId: 'player-1',
              snapshot: {
                playerId: 'player-1',
                status: 'active',
                zoneId: 'starter-plains',
                activeTeamId: 'team-1',
                enemyName: 'Training Slime',
                enemyAttack: 1,
                enemyHp: 12,
                enemyMaxHp: 20,
                teamDps: 5.5,
                pendingReviveMillis: 0,
                members: [
                  {
                    characterKey: 'hero',
                    attack: 10,
                    hit: 8,
                    currentHp: 24,
                    maxHp: 24,
                    alive: true,
                  },
                ],
              },
              serverTime: '2099-01-01T00:00:00Z',
            })
          }, 0)
        }
      }

      close() {
        this.readyState = MockWebSocket.CLOSED
        this.dispatch('close', new Event('close'))
      }
    }

    window.__wsMessages = []
    window.__pushSocketMessage = (message) => {
      const socket = window.__lastSocket
      if (!socket) {
        return
      }
      socket.dispatch(
        'message',
        new MessageEvent('message', {
          data: JSON.stringify(message),
        }),
      )
    }
    window.WebSocket = MockWebSocket
  })

  await fulfillJson('**/characters/templates', [{ key: 'hero', name: 'Hero' }])
  await fulfillJson('**/characters/starters', [{ key: 'hero', name: 'Hero' }])
  await page.route('**/auth/signup', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        player: player(),
        sessionToken: 'session-token',
        sessionExpiresAt: '2099-01-01T00:00:00Z',
        guestAccount: true,
      }),
    })
  })
  await page.route('**/players/player-1/starter', async (route) => {
    state.starterClaimed = true
    await route.fulfill({ status: 204 })
  })
  await page.route('**/players/player-1', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(player()),
    })
  })
  await page.route('**/players/player-1/teams', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([team()]),
    })
  })
  await page.route('**/players/player-1/inventory', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    })
  })
  await page.route('**/teams/team-1/slots/1/characters/hero', async (route) => {
    state.teamAssigned = true
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(team()),
    })
  })
  await page.route('**/teams/team-1/activate', async (route) => {
    state.activeTeam = true
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(team()),
    })
  })

  await page.goto('/')

  await page.getByLabel('Player name').fill('Scout')
  await page.getByRole('button', { name: 'Create Guest' }).click()

  await expect(page.getByRole('heading', { name: 'Choose Your First Character' })).toBeVisible()
  await expect(page.getByText('connected')).toBeVisible()

  await page.getByRole('button', { name: 'Confirm Starter' }).click()
  await expect(page.getByRole('heading', { name: 'Choose Your First Character' })).toBeHidden()

  await page.evaluate(() => {
    window.__pushSocketMessage({
      type: 'PLAYER_STATE_SYNC',
      playerId: 'player-1',
      snapshot: {
        playerId: 'player-1',
        playerName: 'Scout',
        playerExperience: 0,
        playerLevel: 1,
        playerGold: 250,
        playerEssence: 0,
        ownedCharacters: [{ key: 'hero', name: 'Hero', level: 1 }],
        zoneProgress: [],
        inventory: [],
        serverTime: '2099-01-01T00:00:00Z',
      },
    })
  })

  await page.getByRole('button', { name: 'Team' }).click()
  await page.locator('.slot-card').first().locator('select').first().selectOption('hero')
  await page.getByRole('button', { name: 'Set As Active Team' }).click()

  await page.getByRole('button', { name: 'Combat' }).click()
  await page.getByRole('button', { name: 'Start Combat' }).click()

  await expect(page.getByRole('heading', { name: 'Training Slime' })).toBeVisible()
  await expect(page.locator('.status-card').filter({ hasText: 'Combat' }).getByText('active')).toBeVisible()
  await expect.poll(async () => page.evaluate(() => window.__wsMessages)).toContain(JSON.stringify({ type: 'START_COMBAT' }))
})
