import { useEffect, useRef, useState, type MouseEvent as ReactMouseEvent, type MutableRefObject } from 'react'

interface HoverPoint {
  x: number
  y: number
}

export type HoverWithPoint<TTarget extends object> = TTarget & HoverPoint

interface UseDelayedHoverOptions<TTarget extends object> {
  matches: (current: HoverWithPoint<TTarget>, target: TTarget) => boolean
  delayMs?: number
}

export function useDelayedHover<TTarget extends object>({
  matches,
  delayMs = 400,
}: UseDelayedHoverOptions<TTarget>) {
  const [hoverState, setHoverState] = useState<HoverWithPoint<TTarget> | null>(null)
  const hoverTimeoutRef = useRef<number | null>(null)
  const pointerRef = useRef<HoverPoint | null>(null)

  useEffect(() => () => clearPendingTimer(hoverTimeoutRef), [])

  const clear = () => {
    clearPendingTimer(hoverTimeoutRef)
    setHoverState(null)
  }

  const clearIfTarget = (target: TTarget) => {
    clearPendingTimer(hoverTimeoutRef)
    setHoverState((current) => {
      if (current == null || !matches(current, target)) {
        return current
      }
      return null
    })
  }

  const queueFromPointer = (target: TTarget, event: ReactMouseEvent<HTMLButtonElement>) => {
    clearPendingTimer(hoverTimeoutRef)
    pointerRef.current = { x: event.clientX, y: event.clientY }
    hoverTimeoutRef.current = window.setTimeout(() => {
      const point = pointerRef.current ?? { x: event.clientX, y: event.clientY }
      setHoverState({ ...target, x: point.x, y: point.y })
    }, delayMs)
  }

  const updateFromPointer = (target: TTarget, event: ReactMouseEvent<HTMLButtonElement>) => {
    pointerRef.current = { x: event.clientX, y: event.clientY }
    setHoverState((current) => {
      if (current == null || !matches(current, target)) {
        return current
      }
      return { ...target, x: event.clientX, y: event.clientY }
    })
  }

  const showFromFocus = (target: TTarget) => {
    setHoverState({ ...target, ...focusAnchorPoint() })
  }

  return {
    hoverState,
    clear,
    clearIfTarget,
    queueFromPointer,
    updateFromPointer,
    showFromFocus,
  }
}

function clearPendingTimer(hoverTimeoutRef: MutableRefObject<number | null>) {
  if (hoverTimeoutRef.current != null) {
    window.clearTimeout(hoverTimeoutRef.current)
    hoverTimeoutRef.current = null
  }
}

function focusAnchorPoint() {
  const element = document.activeElement
  if (!(element instanceof HTMLElement)) {
    return { x: window.innerWidth / 2, y: window.innerHeight / 2 }
  }
  const rect = element.getBoundingClientRect()
  return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 }
}
