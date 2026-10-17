export const MODIFIER_ALT = 1
export const MODIFIER_CTRL = 2
export const MODIFIER_META = 4
export const MODIFIER_SHIFT = 8

export interface RemoteKey {
  key: string
  code: string
  keyCode: number
}

export const SPECIAL_KEYS: RemoteKey[] = [
  { key: 'Escape', code: 'Escape', keyCode: 27 },
  { key: 'Tab', code: 'Tab', keyCode: 9 },
  { key: 'Enter', code: 'Enter', keyCode: 13 },
  { key: 'Backspace', code: 'Backspace', keyCode: 8 },
  { key: 'Delete', code: 'Delete', keyCode: 46 },
  { key: 'Home', code: 'Home', keyCode: 36 },
  { key: 'End', code: 'End', keyCode: 35 },
  { key: 'PageUp', code: 'PageUp', keyCode: 33 },
  { key: 'PageDown', code: 'PageDown', keyCode: 34 },
  { key: 'F5', code: 'F5', keyCode: 116 },
  { key: 'F11', code: 'F11', keyCode: 122 },
]

export const ARROW_KEYS: Array<RemoteKey & { symbol: string }> = [
  { key: 'ArrowLeft', code: 'ArrowLeft', keyCode: 37, symbol: '←' },
  { key: 'ArrowUp', code: 'ArrowUp', keyCode: 38, symbol: '↑' },
  { key: 'ArrowDown', code: 'ArrowDown', keyCode: 40, symbol: '↓' },
  { key: 'ArrowRight', code: 'ArrowRight', keyCode: 39, symbol: '→' },
]

export const SHORTCUTS: Array<{ label: string; key: RemoteKey }> = [
  { label: 'Ctrl+A', key: { key: 'a', code: 'KeyA', keyCode: 65 } },
  { label: 'Ctrl+C', key: { key: 'c', code: 'KeyC', keyCode: 67 } },
  { label: 'Ctrl+X', key: { key: 'x', code: 'KeyX', keyCode: 88 } },
  { label: 'Ctrl+Z', key: { key: 'z', code: 'KeyZ', keyCode: 90 } },
]

const MODIFIER_KEY_NAMES = new Set([
  'Shift',
  'Control',
  'Alt',
  'AltGraph',
  'Meta',
  'CapsLock',
  'NumLock',
  'ScrollLock',
  'ContextMenu',
  'OS',
  'Dead',
  'Process',
  'Unidentified',
])

const CURSORS = new Set([
  'default',
  'pointer',
  'text',
  'vertical-text',
  'move',
  'grab',
  'grabbing',
  'wait',
  'progress',
  'help',
  'crosshair',
  'not-allowed',
  'no-drop',
  'copy',
  'alias',
  'cell',
  'zoom-in',
  'zoom-out',
  'context-menu',
  'e-resize',
  'w-resize',
  'n-resize',
  's-resize',
  'ne-resize',
  'nw-resize',
  'se-resize',
  'sw-resize',
  'ew-resize',
  'ns-resize',
  'nesw-resize',
  'nwse-resize',
  'col-resize',
  'row-resize',
  'all-scroll',
])

export function isModifierKey(key: string): boolean {
  return MODIFIER_KEY_NAMES.has(key)
}

export function safeCursor(css: string | undefined): string {
  return css && CURSORS.has(css) ? css : 'default'
}

export function modifiersOf(event: {
  altKey: boolean
  ctrlKey: boolean
  metaKey: boolean
  shiftKey: boolean
}): number {
  return (
    (event.altKey ? MODIFIER_ALT : 0) |
    (event.ctrlKey ? MODIFIER_CTRL : 0) |
    (event.metaKey ? MODIFIER_META : 0) |
    (event.shiftKey ? MODIFIER_SHIFT : 0)
  )
}

export function isPrintableKey(event: {
  key: string
  ctrlKey: boolean
  metaKey: boolean
  altKey: boolean
  getModifierState: (key: 'AltGraph') => boolean
}): boolean {
  if (event.key.length !== 1) return false
  const altGraph = event.getModifierState('AltGraph') || (event.ctrlKey && event.altKey)
  return altGraph || (!event.ctrlKey && !event.metaKey)
}

export function remoteModifiers(
  event: { altKey: boolean; ctrlKey: boolean; metaKey: boolean; shiftKey: boolean },
  macLayout: boolean
): number {
  const raw = modifiersOf(event)
  if (!macLayout || !event.metaKey) return raw
  return (raw & ~MODIFIER_META) | MODIFIER_CTRL
}
