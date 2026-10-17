import { formatDecimal } from '@/lib/utils'

export function formatMegabytes(value: number | null | undefined): string {
  if (value === null || value === undefined) return '-'
  if (value >= 1024 * 1024) return `${formatDecimal(value / (1024 * 1024))} TB`
  if (value >= 1024) return `${formatDecimal(value / 1024)} GB`
  return `${Math.round(value)} MB`
}

export function formatBytes(value: number): string {
  if (value >= 1024 * 1024) return `${formatDecimal(value / (1024 * 1024))} MB`
  if (value >= 1024) return `${formatDecimal(value / 1024)} KB`
  return `${value} B`
}

export function formatPercent(value: number | null | undefined): string {
  return value === null || value === undefined ? '-' : `${Math.round(value)}%`
}

export function usedPercent(used: number | null | undefined, total: number | null | undefined) {
  if (used === null || used === undefined || !total) return null
  return Math.min(100, Math.max(0, (used / total) * 100))
}

export function formatUptime(seconds: number | null | undefined, template: string): string {
  if (seconds === null || seconds === undefined) return '-'
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  return template
    .replace('{days}', String(days))
    .replace('{hours}', String(hours))
    .replace('{minutes}', String(minutes))
}
