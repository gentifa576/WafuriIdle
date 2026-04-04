import { useEffect, useRef } from 'react'
import type { ClientCombat } from '../../session/model/clientModels'
import { CombatScene } from '../scene/CombatScene'

interface CombatViewportProps {
  snapshot: ClientCombat | null
  memberLabels?: Record<string, string>
  memberImages?: Record<string, string | null | undefined>
}

export function CombatViewport({ snapshot, memberLabels = {}, memberImages = {} }: CombatViewportProps) {
  const hostRef = useRef<HTMLDivElement | null>(null)
  const sceneRef = useRef<CombatScene | null>(null)
  const latestSnapshotRef = useRef<ClientCombat | null>(snapshot)
  const latestMemberLabelsRef = useRef<Record<string, string>>(memberLabels)
  const latestMemberImagesRef = useRef<Record<string, string | null | undefined>>(memberImages)

  latestSnapshotRef.current = snapshot
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
      scene.render(latestSnapshotRef.current, latestMemberLabelsRef.current, latestMemberImagesRef.current)
    })

    return () => {
      scene.destroy()
      sceneRef.current = null
    }
  }, [])

  useEffect(() => {
    sceneRef.current?.render(snapshot, memberLabels, memberImages)
  }, [memberImages, memberLabels, snapshot])

  return <div className="combat-viewport" ref={hostRef} />
}
