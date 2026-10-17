export function browserTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone
}

export function differsFromBrowser(timeZone: string | null | undefined): boolean {
  return Boolean(timeZone) && timeZone !== browserTimeZone()
}

export function formatInstant(
  instant: string | Date,
  locale: string,
  timeZone: string | null | undefined
): string {
  const date = typeof instant === 'string' ? new Date(instant) : instant
  if (!differsFromBrowser(timeZone)) {
    return new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }).format(date)
  }
  return new Intl.DateTimeFormat(locale, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    timeZoneName: 'short',
    timeZone: timeZone ?? undefined,
  }).format(date)
}

export function zonedDay(instant: Date, timeZone: string | null | undefined): string {
  return new Intl.DateTimeFormat('en-CA', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    ...(timeZone ? { timeZone } : {}),
  }).format(instant)
}

export function zonedClock(instant: Date, timeZone: string | null | undefined): string {
  return new Intl.DateTimeFormat('en-GB', {
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
    ...(timeZone ? { timeZone } : {}),
  }).format(instant)
}

export function dayDifference(from: Date, to: Date, timeZone: string | null | undefined): number {
  const start = Date.parse(`${zonedDay(from, timeZone)}T00:00:00Z`)
  const end = Date.parse(`${zonedDay(to, timeZone)}T00:00:00Z`)
  return Math.round((end - start) / 86_400_000)
}
