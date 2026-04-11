import { Application, Assets, Container, Graphics, Sprite, Text, TextStyle, Texture } from 'pixi.js'
import type { ClientCombat, ClientCombatMember } from '../../session/model/clientModels'
import type { SkillEffectEvent } from '../../../core/types/api'
import combatPresets from './combatPresets.json'

interface CombatSceneRenderState {
  snapshot: ClientCombat | null
  skillEvents: SkillEffectEvent[]
  memberLabels: Record<string, string>
  memberImages: Record<string, string | null | undefined>
}

interface MemberNode {
  container: Container
  fallback: Graphics
  portrait: Sprite
  label: Text
  characterKey: string | null
  imageUrl: string | null
  loadVersion: number
}

interface FloatingDamageText {
  text: Text
  lifetimeMs: number
  velocityY: number
}

interface Vec2 {
  x: number
  y: number
}

interface FlipperState {
  pivot: Vec2
  length: number
  restAngle: number
  activeAngle: number
  angle: number
  cooldownMs: number
}

interface PathNodePreset {
  x: number
  y: number
  vel: number
}

interface PathPreset {
  id: string
  flipperSide: 'left' | 'right'
  flipperBump: number
  start: string
  end: string
  path: PathNodePreset[]
}

interface ActivePathNode {
  x: number
  y: number
  vel: number
}

interface ActivePathPreset {
  id: string
  flipperSide: 'left' | 'right'
  flipperBump: number
  start: string
  end: string
  path: ActivePathNode[]
}

const BASE_WIDTH = 900
const BASE_HEIGHT = 1900
const ATTACK_CYCLE_MS = 1000
const IMPACT_PROGRESS = 0.34
const MAX_VISIBLE_MEMBERS = 3
const BALL_RADIUS = 18
const TRAIL_SPACING = 14
const ENEMY_RADIUS = 54
const FLIPPER_RADIUS = 16
const FLIPPER_ACTIVE_MS = 150
const FLIPPER_PIVOT_OFFSET = 0.25
const FLIPPER_BUMP_WINDOW = 0.06
const BALL_COLORS = [0xf0a35f, 0x86c17a, 0x78b8ff]
const PATH_PRESETS = combatPresets.paths as PathPreset[]

export class CombatScene {
  private app: Application | null = null
  private mounted = false
  private destroyed = false
  private pendingState: CombatSceneRenderState = { snapshot: null, skillEvents: [], memberLabels: {}, memberImages: {} }
  private stageRoot = new Container()
  private board = new Graphics()
  private rails = new Graphics()
  private enemyBody = new Graphics()
  private enemyPortrait = new Sprite(Texture.EMPTY)
  private enemyBar = new Graphics()
  private flippers = new Graphics()
  private enemyLabel = new Text({
    text: 'Waiting for combat',
    style: new TextStyle({
      fill: '#f4e9d8',
      fontFamily: 'Georgia, serif',
      fontSize: 26,
    }),
  })
  private memberNodes: MemberNode[] = []
  private floatingTexts: FloatingDamageText[] = []
  private processedSkillEventIds = new Set<string>()
  private skillEventBaselineInitialized = false
  private cycleElapsedMs = 0
  private previousProgress = 0
  private previousStatus: ClientCombat['status'] | null = null
  private activePathPreset: ActivePathPreset | null = null
  private pathSizeKey = ''
  private failedMemberImages = new Set<string>()
  private failedEnemyImages = new Set<string>()
  private enemyImageUrl: string | null = null
  private enemyImageLoadVersion = 0
  private enemyFlashMs = 0
  private ballPosition: Vec2 = { x: BASE_WIDTH * 0.42, y: BASE_HEIGHT * 0.66 }
  private leaderTrail: Vec2[] = []
  private enemyAnchor: Vec2 = { x: BASE_WIDTH * 0.5, y: 164 }
  private leftFlipper: FlipperState = {
    pivot: { x: 124, y: 720 },
    length: 86,
    restAngle: 0.34,
    activeAngle: -0.18,
    angle: 0.34,
    cooldownMs: 0,
  }
  private rightFlipper: FlipperState = {
    pivot: { x: BASE_WIDTH - 124, y: 720 },
    length: 86,
    restAngle: Math.PI - 0.34,
    activeAngle: Math.PI + 0.18,
    angle: Math.PI - 0.34,
    cooldownMs: 0,
  }

  async mount(container: HTMLDivElement) {
    const app = new Application()
    await app.init({
      resizeTo: container,
      background: '#0f110d',
      antialias: true,
    })

    if (this.destroyed) {
      app.destroy()
      return
    }

    this.app = app
    this.enemyPortrait.anchor.set(0.5)
    this.enemyPortrait.visible = false
    app.canvas.style.width = '100%'
    app.canvas.style.height = '100%'
    app.canvas.style.display = 'block'
    container.appendChild(app.canvas)
    app.stage.addChild(this.stageRoot)
    this.stageRoot.addChild(this.board)
    this.stageRoot.addChild(this.rails)
    this.stageRoot.addChild(this.enemyBar)
    this.stageRoot.addChild(this.enemyBody)
    this.stageRoot.addChild(this.enemyPortrait)
    this.stageRoot.addChild(this.flippers)
    this.stageRoot.addChild(this.enemyLabel)
    this.enemyLabel.position.set(this.sx(24), this.sy(22))
    app.ticker.add(this.onTick)
    this.mounted = true
    this.applyState()
  }

  render(
    snapshot: ClientCombat | null,
    skillEvents: SkillEffectEvent[] = [],
    memberLabels: Record<string, string> = {},
    memberImages: Record<string, string | null | undefined> = {},
  ) {
    this.pendingState = {
      snapshot,
      skillEvents,
      memberLabels,
      memberImages,
    }

    if (this.mounted) {
      this.applyState()
    }
  }

  destroy() {
    this.destroyed = true
    this.mounted = false

    const app = this.app
    this.app = null
    if (!app) {
      return
    }

    app.ticker.remove(this.onTick)
    app.canvas.parentElement?.removeChild(app.canvas)
    app.destroy()
  }

  private applyState() {
    if (!this.app) {
      return
    }

    this.syncMemberNodes(this.pendingState.snapshot?.members ?? [])
    if (!this.skillEventBaselineInitialized) {
      // Avoid replaying buffered events when re-entering the combat view.
      this.processedSkillEventIds = new Set(this.pendingState.skillEvents.map((event) => event.eventId))
      this.skillEventBaselineInitialized = true
    }
    this.consumeSkillEvents(this.pendingState.skillEvents)
    this.updateBoardGeometry()
    if (this.activePathPreset == null || this.pathSizeKey !== this.boardSizeKey()) {
      const shouldResetBall = this.activePathPreset == null
      this.activePathPreset = this.selectInitialPath()
      this.pathSizeKey = this.boardSizeKey()
      if (shouldResetBall && this.activePathPreset.path.length > 0) {
        this.ballPosition = { ...this.activePathPreset.path[0] }
      }
    }
    this.enemyLabel.text =
      this.pendingState.snapshot == null
        ? 'Waiting for combat'
        : `${this.pendingState.snapshot.enemyName} · ${this.pendingState.snapshot.enemyHp.toFixed(0)} HP`
    this.syncEnemyImage(this.pendingState.snapshot?.enemyImage ?? null)
    this.renderFrame()
  }

  private syncMemberNodes(members: ClientCombatMember[]) {
    const visibleMembers = members.slice(0, MAX_VISIBLE_MEMBERS)

    while (this.memberNodes.length > visibleMembers.length) {
      const removed = this.memberNodes.pop()
      removed?.container.destroy({ children: true })
    }

    while (this.memberNodes.length < visibleMembers.length) {
      const index = this.memberNodes.length
      const container = new Container()
      const fallback = new Graphics()
      const portrait = new Sprite(Texture.EMPTY)
      portrait.anchor.set(0.5)
      const label = new Text({
        text: '',
        style: new TextStyle({
          fill: '#11130f',
          fontFamily: 'IBM Plex Sans, sans-serif',
          fontSize: 13,
          fontWeight: '700',
        }),
      })
      label.anchor.set(0.5)
      container.addChild(fallback)
      container.addChild(portrait)
      container.addChild(label)
      this.stageRoot.addChild(container)
      this.memberNodes.push({ container, fallback, portrait, label, characterKey: null, imageUrl: null, loadVersion: 0 })
      this.drawMemberToken(index)
    }

    visibleMembers.forEach((member, index) => {
      const node = this.memberNodes[index]
      node.label.text = this.toMemberLabel(member.characterKey)
      this.syncMemberImage(node, member.characterKey)
    })
  }

  private drawMemberToken(index: number) {
    const node = this.memberNodes[index]
    const radius = this.boardRadius()
    node.fallback.clear()
    node.fallback.circle(0, 0, radius).fill(BALL_COLORS[index] ?? 0xf4e9d8)
    node.fallback.circle(0, 0, radius - this.s(5)).fill(0xf8f0e3)
    node.fallback.circle(0, -radius + this.s(4), this.s(5)).fill(BALL_COLORS[index] ?? 0xf4e9d8)
    node.label.position.set(0, 0)
  }

  private onTick = () => {
    if (!this.app || !this.mounted) {
      return
    }

    const snapshot = this.pendingState.snapshot
    const shouldAnimate = this.shouldAnimateCombat()

    if (!shouldAnimate) {
      this.cycleElapsedMs = 0
      this.previousProgress = 0
      this.leftFlipper.cooldownMs = 0
      this.rightFlipper.cooldownMs = 0
      this.resetToPathStart()
      this.updateFlippers(0)
      this.renderFrame()
      this.previousStatus = snapshot?.status ?? null
      return
    }

    if (snapshot?.status !== this.previousStatus) {
      this.cycleElapsedMs = 0
      this.previousProgress = 0
      this.resetToPathStart()
    }

    const deltaMs = this.app.ticker.deltaMS
    const previousCycleElapsed = this.cycleElapsedMs
    this.cycleElapsedMs = (this.cycleElapsedMs + deltaMs) % ATTACK_CYCLE_MS
    const progress = this.cycleElapsedMs / ATTACK_CYCLE_MS
    const crossedImpact = this.previousProgress < IMPACT_PROGRESS && progress >= IMPACT_PROGRESS
    this.previousProgress = progress
    const wrappedCycle = this.cycleElapsedMs < previousCycleElapsed

    if (wrappedCycle) {
      this.activePathPreset = this.selectNextPath(this.activePath())
    }

    this.updateFlippers(deltaMs)
    this.updateLeaderMotion(progress)
    this.enemyFlashMs = Math.max(this.enemyFlashMs - deltaMs, 0)
    this.updateFloatingTexts(deltaMs)

    if (crossedImpact && snapshot?.status === 'FIGHTING' && snapshot.members.length) {
      this.triggerImpact()
    }

    this.renderFrame()
    this.previousStatus = snapshot?.status ?? null
  }

  private updateFlippers(deltaMs: number) {
    this.updateFlipperState(this.leftFlipper, deltaMs)
    this.updateFlipperState(this.rightFlipper, deltaMs)
  }

  private updateFlipperState(flipper: FlipperState, deltaMs: number) {
    flipper.cooldownMs = Math.max(flipper.cooldownMs - deltaMs, 0)
    const targetAngle = flipper.cooldownMs > 0 ? flipper.activeAngle : flipper.restAngle
    flipper.angle = lerp(flipper.angle, targetAngle, flipper.cooldownMs > 0 ? 0.34 : 0.2)
  }

  private updateLeaderMotion(progress: number) {
    this.updateBoardGeometry()
    this.ballPosition = sampleWeightedPath(this.activePath().path, easeInOutSine(progress))
    this.triggerFlipperIfNeeded(progress)
    this.recordTrail()
  }

  private triggerImpact() {
    if (!this.pendingState.snapshot) {
      return
    }

    this.enemyFlashMs = 160
    this.spawnDamageTexts(this.pendingState.snapshot.members)
  }

  private recordTrail() {
    this.leaderTrail.unshift({ ...this.ballPosition })
    this.leaderTrail = this.leaderTrail.slice(0, 240)
  }

  private renderFrame() {
    const snapshot = this.pendingState.snapshot
    this.drawBoard()
    this.drawEnemy(snapshot)
    this.drawFlippers()
    this.layoutMembers(snapshot)
  }

  private drawBoard() {
    const width = this.width()
    const height = this.height()
    this.board.clear()
    this.rails.clear()

    this.board.roundRect(0, 0, width, height, this.s(34)).fill(0x131712)
    this.board.roundRect(this.sx(18), this.sy(18), width - this.sx(36), height - this.sy(36), this.s(28)).fill(0x181c15)
    this.board.roundRect(this.sx(30), this.sy(96), width - this.sx(60), height - this.sy(148), this.s(24)).fill(0x0f110d)

    this.rails.roundRect(this.sx(30), this.sy(96), width - this.sx(60), height - this.sy(148), this.s(24)).stroke({ width: this.s(3), color: 0x323827 })
    this.rails.circle(this.enemyAnchor.x, this.enemyAnchor.y, this.s(86)).stroke({ width: this.s(2), color: 0x433525 })
    this.rails.circle(this.sx(252), this.sy(300), this.s(44)).stroke({ width: this.s(1), color: 0x2e3327, alpha: 0.8 })
    this.rails.circle(this.sx(666), this.sy(412), this.s(54)).stroke({ width: this.s(1), color: 0x2e3327, alpha: 0.8 })
    this.rails.circle(this.sx(468), this.sy(598), this.s(66)).stroke({ width: this.s(1), color: 0x2e3327, alpha: 0.8 })
  }

  private drawEnemy(snapshot: ClientCombat | null) {
    const width = this.width()
    this.enemyBar.clear()
    this.enemyBody.clear()

    this.enemyBar.roundRect(this.sx(24), this.sy(60), width - this.sx(48), this.sy(20), this.s(10)).fill('#322b20')
    const ratio = snapshot == null || snapshot.enemyMaxHp === 0 ? 0 : snapshot.enemyHp / snapshot.enemyMaxHp
    const barWidth = Math.max((width - this.sx(48)) * ratio, 0)
    this.enemyBar.roundRect(this.sx(24), this.sy(60), barWidth, this.sy(20), this.s(10)).fill('#d8572a')

    this.enemyPortrait.position.set(this.enemyAnchor.x, this.enemyAnchor.y)
    if (this.enemyPortrait.visible) {
      return
    }

    const coreColor = this.enemyFlashMs > 0 ? 0xffddb8 : 0x8f3d27
    this.enemyBody.circle(this.enemyAnchor.x, this.enemyAnchor.y, this.enemyRadius()).fill(coreColor)
    this.enemyBody.circle(this.enemyAnchor.x, this.enemyAnchor.y, this.enemyRadius() - this.s(16)).fill(0x24130d)
    this.enemyBody.circle(this.enemyAnchor.x - this.s(15), this.enemyAnchor.y - this.s(8), this.s(7)).fill(0xf4e9d8)
    this.enemyBody.circle(this.enemyAnchor.x + this.s(15), this.enemyAnchor.y - this.s(8), this.s(7)).fill(0xf4e9d8)
    this.enemyBody.roundRect(this.enemyAnchor.x - this.s(18), this.enemyAnchor.y + this.s(14), this.s(36), this.s(10), this.s(5)).fill(0xf4e9d8)
  }

  private drawFlippers() {
    this.flippers.clear()
    this.drawFlipper(this.leftFlipper, 0xd8572a)
    this.drawFlipper(this.rightFlipper, 0xd8572a)
  }

  private drawFlipper(flipper: FlipperState, color: number) {
    const end = {
      x: flipper.pivot.x + Math.cos(flipper.angle) * flipper.length,
      y: flipper.pivot.y + Math.sin(flipper.angle) * flipper.length,
    }
    this.flippers.circle(flipper.pivot.x, flipper.pivot.y, this.s(18)).fill(0x272c21)
    this.flippers.circle(flipper.pivot.x, flipper.pivot.y, this.s(10)).fill(0x0f110d)
    this.flippers.moveTo(flipper.pivot.x, flipper.pivot.y)
    this.flippers.lineTo(end.x, end.y)
    this.flippers.stroke({ width: this.flipperRadius() * 2, color, cap: 'round' })
  }

  private layoutMembers(snapshot: ClientCombat | null) {
    const members = snapshot?.members.slice(0, MAX_VISIBLE_MEMBERS) ?? []

    this.memberNodes.forEach((node, index) => {
      const member = members[index]
      node.container.visible = member != null
      if (!member) {
        return
      }

      const trailIndex = index === 0 ? 0 : Math.min(index * TRAIL_SPACING, this.leaderTrail.length - 1)
      const position = index === 0 ? this.ballPosition : this.leaderTrail[trailIndex] ?? this.ballPosition
      this.drawMemberToken(index)
      node.container.position.set(position.x, position.y)
      node.container.alpha = member.currentHp > 0 ? 1 : 0.4
      node.container.scale.set(index === 0 ? 1 : 0.94 - index * 0.06)
    })
  }

  private syncMemberImage(node: MemberNode, characterKey: string) {
    const imageUrl = this.pendingState.memberImages[characterKey] ?? null
    if (node.characterKey === characterKey && node.imageUrl === imageUrl) {
      return
    }

    node.characterKey = characterKey
    node.imageUrl = imageUrl
    node.loadVersion += 1
    node.portrait.texture = Texture.EMPTY

    if (!imageUrl || this.failedMemberImages.has(imageUrl)) {
      node.portrait.visible = false
      node.fallback.visible = true
      node.label.visible = true
      return
    }

    const loadVersion = node.loadVersion
    void Assets.load<Texture>(imageUrl)
      .then((texture) => {
        if (this.destroyed || node.loadVersion !== loadVersion || node.imageUrl !== imageUrl) {
          return
        }
        node.portrait.texture = texture
        this.sizePortrait(node, texture)
        node.portrait.visible = true
        node.fallback.visible = false
        node.label.visible = false
      })
      .catch(() => {
        if (node.loadVersion !== loadVersion || node.imageUrl !== imageUrl) {
          return
        }
        this.failedMemberImages.add(imageUrl)
        node.portrait.visible = false
        node.fallback.visible = true
        node.label.visible = true
      })
  }

  private syncEnemyImage(imageUrl: string | null) {
    if (this.enemyImageUrl === imageUrl) {
      return
    }

    this.enemyImageUrl = imageUrl
    this.enemyImageLoadVersion += 1
    this.enemyPortrait.texture = Texture.EMPTY
    this.enemyPortrait.visible = false

    if (!imageUrl || this.failedEnemyImages.has(imageUrl)) {
      return
    }

    const loadVersion = this.enemyImageLoadVersion
    void Assets.load<Texture>(imageUrl)
      .then((texture) => {
        if (this.destroyed || this.enemyImageLoadVersion !== loadVersion || this.enemyImageUrl !== imageUrl) {
          return
        }
        this.enemyPortrait.texture = texture
        this.sizeEnemyPortrait(texture)
        this.enemyPortrait.visible = true
      })
      .catch(() => {
        if (this.enemyImageLoadVersion !== loadVersion || this.enemyImageUrl !== imageUrl) {
          return
        }
        this.failedEnemyImages.add(imageUrl)
        this.enemyPortrait.visible = false
      })
  }

  private sizePortrait(node: MemberNode, texture: Texture) {
    node.portrait.width = texture.width * 2
    node.portrait.height = texture.height * 2
  }

  private sizeEnemyPortrait(texture: Texture) {
    const maxSize = this.enemyRadius() * 2.25
    const textureWidth = texture.width || 1
    const textureHeight = texture.height || 1
    const scale = Math.min(maxSize / textureWidth, maxSize / textureHeight)
    this.enemyPortrait.width = textureWidth * scale
    this.enemyPortrait.height = textureHeight * scale
  }

  private spawnDamageTexts(members: ClientCombatMember[]) {
    members.slice(0, MAX_VISIBLE_MEMBERS).forEach((member, memberIndex) => {
      const hitCount = Math.max(1, Math.round(member.hit))
      for (let index = 0; index < hitCount; index += 1) {
        const jitterX = randomBetween(-22, 22)
        const jitterY = randomBetween(-16, 16)
        const damageText = new Text({
          text: `${member.attack.toFixed(0)}`,
          style: new TextStyle({
            fill: memberIndex === 0 ? '#ffd166' : '#f4e9d8',
            fontFamily: 'IBM Plex Sans, sans-serif',
            fontSize: this.s(20),
            fontWeight: '700',
            stroke: { color: '#11130f', width: 4 },
          }),
        })
        damageText.anchor.set(0.5)
        damageText.position.set(
          this.enemyAnchor.x + (index - hitCount / 2) * 18 + jitterX,
          this.enemyAnchor.y - this.s(26) - memberIndex * this.s(20) + jitterY,
        )
        this.stageRoot.addChild(damageText)
        this.floatingTexts.push({
          text: damageText,
          lifetimeMs: 900 - index * 55,
          velocityY: 0.04 + index * 0.004,
        })
      }
    })
  }

  private consumeSkillEvents(skillEvents: SkillEffectEvent[]) {
    for (const event of skillEvents) {
      if (this.processedSkillEventIds.has(event.eventId)) {
        continue
      }
      this.processedSkillEventIds.add(event.eventId)
      if (event.effectType === 'DAMAGE' && event.targetType === 'ENEMY') {
        this.spawnSkillDamageText(event)
      }
    }

    if (this.processedSkillEventIds.size > 200) {
      this.processedSkillEventIds = new Set(skillEvents.slice(-120).map((event) => event.eventId))
    }
  }

  private spawnSkillDamageText(event: SkillEffectEvent) {
    const damageText = new Text({
      text: `${Math.max(0, event.value ?? 0).toFixed(0)}`,
      style: new TextStyle({
        fill: '#7a2f4f',
        fontFamily: 'IBM Plex Sans, sans-serif',
        fontSize: this.s(42),
        fontWeight: '800',
        stroke: { color: '#08050a', width: 6 },
      }),
    })
    damageText.anchor.set(0.5)
    damageText.position.set(
      this.enemyAnchor.x + randomBetween(-34, 34),
      this.enemyAnchor.y - this.s(52) + randomBetween(-18, 18),
    )
    this.stageRoot.addChild(damageText)
    this.floatingTexts.push({
      text: damageText,
      lifetimeMs: 1_050,
      velocityY: 0.06,
    })
  }

  private updateFloatingTexts(deltaMs: number) {
    this.floatingTexts = this.floatingTexts.filter((entry) => {
      entry.lifetimeMs -= deltaMs
      entry.text.y -= deltaMs * entry.velocityY
      entry.text.alpha = Math.max(entry.lifetimeMs / 900, 0)
      if (entry.lifetimeMs <= 0) {
        entry.text.destroy()
        return false
      }
      return true
    })
  }

  private toMemberLabel(characterKey: string) {
    const label = this.pendingState.memberLabels[characterKey] ?? characterKey
    return label
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase() ?? '')
      .join('')
  }

  private updateBoardGeometry() {
    const width = this.width()
    const height = this.height()
    this.enemyAnchor = { x: width * 0.5, y: height * 0.195 }
    this.leftFlipper.pivot = { x: width * FLIPPER_PIVOT_OFFSET, y: height * 0.853 }
    this.rightFlipper.pivot = { x: width * (1 - FLIPPER_PIVOT_OFFSET), y: height * 0.853 }
    this.leftFlipper.length = width * 0.195
    this.rightFlipper.length = width * 0.195
    if (this.enemyPortrait.visible) {
      this.enemyPortrait.position.set(this.enemyAnchor.x, this.enemyAnchor.y)
      this.sizeEnemyPortrait(this.enemyPortrait.texture)
    }
    if (this.pathSizeKey !== '' && this.pathSizeKey !== this.boardSizeKey()) {
      const currentPreset = this.activePathPreset
      this.activePathPreset =
        currentPreset == null ? this.selectInitialPath() : this.scalePreset(currentPreset.id)
      this.pathSizeKey = this.boardSizeKey()
    }
  }

  private triggerFlipperIfNeeded(progress: number) {
    const activePreset = this.activePath()
    const bumpProgress = this.bumpProgress(activePreset)
    if (Math.abs(progress - bumpProgress) > FLIPPER_BUMP_WINDOW) {
      return
    }

    if (this.leftFlipper.cooldownMs > 0 || this.rightFlipper.cooldownMs > 0) {
      return
    }

    this.leftFlipper.cooldownMs = FLIPPER_ACTIVE_MS
    this.rightFlipper.cooldownMs = FLIPPER_ACTIVE_MS
  }

  private activePath() {
    return this.activePathPreset ?? {
      id: 'fallback',
      flipperSide: 'left',
      flipperBump: 0,
      start: 'left',
      end: 'right',
      path: [],
    }
  }

  private shouldAnimateCombat() {
    const snapshot = this.pendingState.snapshot
    return snapshot != null && snapshot.status !== 'IDLE' && snapshot.status !== 'DOWN' && snapshot.members.length > 0
  }

  private resetToPathStart() {
    const startNode = this.activePath().path[0]
    if (startNode) {
      this.ballPosition = { ...startNode }
      this.recordTrail()
    }
  }

  private selectInitialPath() {
    return this.scalePreset(PATH_PRESETS[0]?.id ?? 'fallback')
  }

  private selectNextPath(currentPreset: ActivePathPreset) {
    const candidates = PATH_PRESETS.filter((preset) => preset.start === currentPreset.end)
    const nextPreset = pickRandom(candidates.length > 0 ? candidates : PATH_PRESETS)
    return this.scalePreset(nextPreset?.id ?? currentPreset.id)
  }

  private scalePreset(id: string): ActivePathPreset {
    const preset = PATH_PRESETS.find((candidate) => candidate.id === id) ?? PATH_PRESETS[0]
    if (preset == null) {
      return {
        id: 'fallback',
        flipperSide: 'left',
        flipperBump: 0,
        start: 'left',
        end: 'right',
        path: [],
      }
    }

    const scaledPath = preset.path.map((point) => ({
      x: point.x * this.width(),
      y: point.y * this.height(),
      vel: point.vel,
    }))
    if (scaledPath.length > 0) {
      const launchTip = this.flipperTip(preset.flipperSide === 'left' ? this.leftFlipper : this.rightFlipper)
      const receiveTip = this.flipperTip(preset.flipperSide === 'left' ? this.rightFlipper : this.leftFlipper)
      scaledPath[0] = { ...launchTip, vel: scaledPath[0].vel }
      scaledPath[scaledPath.length - 1] = { ...receiveTip, vel: scaledPath[scaledPath.length - 1].vel }
    }

    return {
      id: preset.id,
      flipperSide: preset.flipperSide,
      flipperBump: preset.flipperBump,
      start: preset.start,
      end: preset.end,
      path: scaledPath,
    }
  }

  private bumpProgress(preset: ActivePathPreset) {
    if (preset.path.length <= 1) {
      return 0
    }

    const cappedIndex = Math.min(Math.max(preset.flipperBump, 0), preset.path.length - 1)
    return weightedProgressAtNode(preset.path, cappedIndex)
  }

  private boardSizeKey() {
    return `${Math.round(this.width())}x${Math.round(this.height())}`
  }

  private width() {
    return this.app?.screen.width ?? BASE_WIDTH
  }

  private height() {
    return this.app?.screen.height ?? BASE_HEIGHT
  }

  private sx(value: number) {
    return (value / BASE_WIDTH) * this.width()
  }

  private sy(value: number) {
    return (value / BASE_HEIGHT) * this.height()
  }

  private s(value: number) {
    return Math.min(this.sx(value), this.sy(value))
  }

  private boardRadius() {
    return this.s(BALL_RADIUS)
  }

  private enemyRadius() {
    return this.s(ENEMY_RADIUS)
  }

  private flipperRadius() {
    return this.s(FLIPPER_RADIUS)
  }

  private flipperTip(flipper: FlipperState): Vec2 {
    const tipX = flipper.pivot.x + Math.cos(flipper.restAngle) * flipper.length
    const tipY = flipper.pivot.y + Math.sin(flipper.restAngle) * flipper.length
    const normalAngle =
      flipper === this.leftFlipper
        ? flipper.restAngle - Math.PI / 2
        : flipper.restAngle + Math.PI / 2
    const contactOffset = this.boardRadius() + this.flipperRadius() * 0.9

    return {
      x: tipX + Math.cos(normalAngle) * contactOffset,
      y: tipY + Math.sin(normalAngle) * contactOffset,
    }
  }
}

function lerp(start: number, end: number, progress: number) {
  return start + (end - start) * progress
}

function randomBetween(min: number, max: number) {
  return Math.random() * (max - min) + min
}

function easeInOutSine(progress: number) {
  return -(Math.cos(Math.PI * progress) - 1) / 2
}

function sampleWeightedPath(points: ActivePathNode[], progress: number): Vec2 {
  if (points.length === 0) {
    return { x: 0, y: 0 }
  }
  if (points.length === 1) {
    return points[0]
  }

  const segmentWeights = buildSegmentWeights(points)
  const totalWeight = segmentWeights.reduce((sum, weight) => sum + weight, 0)
  if (totalWeight <= 0) {
    return points[0]
  }

  let remainingWeight = Math.min(Math.max(progress, 0), 0.999999) * totalWeight
  for (let index = 0; index < segmentWeights.length; index += 1) {
    const weight = segmentWeights[index]
    if (remainingWeight <= weight) {
      const start = points[index]
      const end = points[index + 1]
      const segmentProgress = weight === 0 ? 0 : remainingWeight / weight

      return {
        x: lerp(start.x, end.x, segmentProgress),
        y: lerp(start.y, end.y, segmentProgress),
      }
    }

    remainingWeight -= weight
  }

  return {
    x: points[points.length - 1].x,
    y: points[points.length - 1].y,
  }
}

function weightedProgressAtNode(points: ActivePathNode[], nodeIndex: number) {
  if (points.length <= 1 || nodeIndex <= 0) {
    return 0
  }

  const segmentWeights = buildSegmentWeights(points)
  const totalWeight = segmentWeights.reduce((sum, weight) => sum + weight, 0)
  if (totalWeight <= 0) {
    return 0
  }

  const traversedWeight = segmentWeights
    .slice(0, Math.min(nodeIndex, segmentWeights.length))
    .reduce((sum, weight) => sum + weight, 0)

  return traversedWeight / totalWeight
}

function buildSegmentWeights(points: ActivePathNode[]) {
  const weights: number[] = []

  for (let index = 0; index < points.length - 1; index += 1) {
    const start = points[index]
    const end = points[index + 1]
    const dx = end.x - start.x
    const dy = end.y - start.y
    const distance = Math.sqrt(dx * dx + dy * dy)
    const averageVelocity = Math.max((start.vel + end.vel) / 2, 0.01)
    weights.push(distance / averageVelocity)
  }

  return weights
}

function pickRandom<T>(items: T[]): T | undefined {
  if (items.length === 0) {
    return undefined
  }

  return items[Math.floor(Math.random() * items.length)]
}
