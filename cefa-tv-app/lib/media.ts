import {
  IconFileTypePdf,
  IconGlobe,
  IconHtml,
  IconPhoto,
  IconVideo,
  type Icon,
} from '@tabler/icons-react'
import type { MediaType } from '@/types/api'

export const MEDIA_TYPES: MediaType[] = ['IMAGE', 'VIDEO', 'PDF', 'WEB', 'HTML']

export const MEDIA_ICONS: Record<MediaType, Icon> = {
  IMAGE: IconPhoto,
  VIDEO: IconVideo,
  PDF: IconFileTypePdf,
  WEB: IconGlobe,
  HTML: IconHtml,
}

export const MEDIA_TONES: Record<MediaType, string> = {
  IMAGE: 'bg-media-image/15 text-media-image',
  VIDEO: 'bg-media-video/15 text-media-video',
  PDF: 'bg-media-pdf/15 text-media-pdf',
  WEB: 'bg-media-web/15 text-media-web',
  HTML: 'bg-media-html/15 text-media-html',
}

export const MEDIA_BLOCK_TONES: Record<MediaType, string> = {
  IMAGE: 'border-media-image/60 bg-media-image/20',
  VIDEO: 'border-media-video/60 bg-media-video/20',
  PDF: 'border-media-pdf/60 bg-media-pdf/20',
  WEB: 'border-media-web/60 bg-media-web/20',
  HTML: 'border-media-html/60 bg-media-html/20',
}

export const DEFAULT_IMAGE_SECONDS = 10
export const DEFAULT_WEB_SECONDS = 30
export const DEFAULT_PDF_PAGE_SECONDS = 8

export function defaultDurationSec(type: MediaType, durationMs: number | null): number {
  if (type === 'VIDEO') return Math.max(1, Math.round((durationMs ?? 10000) / 1000))
  if (type === 'PDF') return DEFAULT_PDF_PAGE_SECONDS
  if (type === 'WEB' || type === 'HTML') return DEFAULT_WEB_SECONDS
  return DEFAULT_IMAGE_SECONDS
}

export function hasFixedDuration(type: MediaType): boolean {
  return type === 'VIDEO'
}
