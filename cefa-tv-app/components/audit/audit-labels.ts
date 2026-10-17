import type { Dictionary } from '@/i18n/get-dictionary'
import type { AuditOutcome } from '@/types/api'

export const OUTCOMES: AuditOutcome[] = ['SUCCESS', 'DENIED', 'FAILED']

function humanize(code: string): string {
  const text = code.toLowerCase().replace(/_/g, ' ')
  return text.charAt(0).toUpperCase() + text.slice(1)
}

export function entityLabel(dict: Dictionary, type: string | null): string {
  if (!type) return '-'
  return (dict.audit.entities as Record<string, string>)[type] ?? humanize(type)
}

export function actionLabel(dict: Dictionary, code: string): string {
  return (dict.audit.actions as Record<string, string>)[code] ?? humanize(code)
}

export function outcomeLabel(dict: Dictionary, outcome: AuditOutcome): string {
  return dict.audit.results[outcome]
}
