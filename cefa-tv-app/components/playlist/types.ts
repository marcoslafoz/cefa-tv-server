import type { MediaAsset, PlaylistFit } from '@/types/api'

export interface EditorItem {
  uid: string
  assetId: string
  durationSec: number
  fit: PlaylistFit
  muted: boolean
}

export function effectiveSeconds(item: EditorItem, asset: MediaAsset | undefined): number {
  if (asset?.type === 'PDF') return item.durationSec * Math.max(1, asset.pageCount ?? 1)
  return item.durationSec
}

export function newUid(): string {
  return crypto.randomUUID()
}
