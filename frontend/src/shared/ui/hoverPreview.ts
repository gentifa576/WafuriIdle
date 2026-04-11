import type { CSSProperties, MouseEvent as ReactMouseEvent } from 'react'

interface HoverStatePosition {
  x: number
  y: number
}

interface HoverController<TTarget> {
  clearIfTarget: (target: TTarget) => void
  queueFromPointer: (target: TTarget, event: ReactMouseEvent<HTMLButtonElement>) => void
  updateFromPointer: (target: TTarget, event: ReactMouseEvent<HTMLButtonElement>) => void
  showFromFocus: (target: TTarget) => void
}

interface HoverPreviewOptions {
  offset?: number
  width?: number
  height?: number
}

export function hoverPreviewStyle(
  hoverState: HoverStatePosition,
  options: HoverPreviewOptions = {},
): CSSProperties {
  const offset = options.offset ?? 18
  const width = options.width ?? 340
  const height = options.height ?? 280
  return {
    left: `${Math.min(hoverState.x + offset, window.innerWidth - width)}px`,
    top: `${Math.min(hoverState.y + offset, window.innerHeight - height)}px`,
  }
}

export function createHoverTileHandlers<TTarget>(
  hover: HoverController<TTarget>,
  target: TTarget,
) {
  return {
    onBlur: () => hover.clearIfTarget(target),
    onFocus: () => hover.showFromFocus(target),
    onMouseEnter: (event: ReactMouseEvent<HTMLButtonElement>) => hover.queueFromPointer(target, event),
    onMouseLeave: () => hover.clearIfTarget(target),
    onMouseMove: (event: ReactMouseEvent<HTMLButtonElement>) => hover.updateFromPointer(target, event),
  }
}
