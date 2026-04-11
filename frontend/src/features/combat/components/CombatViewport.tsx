import { useEffect, useRef } from 'react'
import type { ClientCombat } from '../../session/model/clientModels'
import type { SkillEffectEvent } from '../../../core/types/api'
import { CombatScene } from '../scene/CombatScene'

interface CombatViewportProps {
  snapshot: ClientCombat | null
  skillEvents: SkillEffectEvent[]
  memberLabels?: Record<string, string>
  memberImages?: Record<string, string | null | undefined>
}

export function CombatViewport({ snapshot, skillEvents, memberLabels = {}, memberImages = {} }: CombatViewportProps) {
  const hostRef = useRef<HTMLDivElement | null>(null)
  const sceneRef = useRef<CombatScene | null>(null)
  const latestSnapshotRef = useRef<ClientCombat | null>(snapshot)
  const latestSkillEventsRef = useRef<SkillEffectEvent[]>(skillEvents)
  const latestMemberLabelsRef = useRef<Record<string, string>>(memberLabels)
  const latestMemberImagesRef = useRef<Record<string, string | null | undefined>>(memberImages)

  latestSnapshotRef.current = snapshot
  latestSkillEventsRef.current = skillEvents
  latestMemberLabelsRef.current = memberLabels
  latestMemberImagesRef.current = memberImages

  useEffect(() => {
    const host = hostRef.current
    if (!host) {
      return
    }

    const scene = new CombatScene()
    sceneRef.current = scene
    void scene.mount(host).then(() => {
      scene.render(latestSnapshotRef.current, latestSkillEventsRef.current, latestMemberLabelsRef.current, latestMemberImagesRef.current)
    })

    return () => {
      scene.destroy()
      sceneRef.current = null
    }
  }, [])

  useEffect(() => {
    sceneRef.current?.render(snapshot, skillEvents, memberLabels, memberImages)
  }, [memberImages, memberLabels, skillEvents, snapshot])

  return <div className="combat-viewport" ref={hostRef} />
}
