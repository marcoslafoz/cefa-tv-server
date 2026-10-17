import type { KeyboardEvent, MouseEvent } from 'react'

const INTERACTIVE =
  'button,a,input,select,textarea,label,[role=switch],[role=checkbox],[role=menuitem]'

function fromInteractive(event: MouseEvent<HTMLElement>) {
  if (!event.currentTarget.contains(event.target as Node)) return true
  const hit = (event.target as HTMLElement).closest(INTERACTIVE)
  return hit !== null && event.currentTarget.contains(hit)
}

export function rowActivation(onActivate: () => void, role: 'link' | 'button' = 'link') {
  return {
    role,
    tabIndex: 0,
    onClick: (event: MouseEvent<HTMLElement>) => {
      if (fromInteractive(event)) return
      onActivate()
    },
    onKeyDown: (event: KeyboardEvent<HTMLElement>) => {
      if (event.target !== event.currentTarget) return
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault()
        onActivate()
      }
    },
  }
}

export const ROW_CLASS =
  'cursor-pointer outline-none transition-colors focus-visible:ring-2 focus-visible:ring-ring'
