export interface ZoneInfo {
  id: string
  region: string
  city: string
  offsetMinutes: number
  offsetLabel: string
  country: string
}

export const POPULAR_ZONES = ['Europe/Madrid', 'Europe/Warsaw', 'Europe/London', 'UTC']

const ZONE_COUNTRY: Record<string, string> = {
  'Europe/Madrid': 'ES',
  'Atlantic/Canary': 'ES',
  'Africa/Ceuta': 'ES',
  'Europe/Warsaw': 'PL',
  'Europe/London': 'GB',
  'Europe/Paris': 'FR',
  'Europe/Berlin': 'DE',
  'Europe/Rome': 'IT',
  'Europe/Lisbon': 'PT',
  'Atlantic/Azores': 'PT',
  'Europe/Dublin': 'IE',
  'Europe/Amsterdam': 'NL',
  'Europe/Brussels': 'BE',
  'Europe/Zurich': 'CH',
  'Europe/Vienna': 'AT',
  'Europe/Prague': 'CZ',
  'Europe/Budapest': 'HU',
  'Europe/Bucharest': 'RO',
  'Europe/Athens': 'GR',
  'Europe/Stockholm': 'SE',
  'Europe/Oslo': 'NO',
  'Europe/Copenhagen': 'DK',
  'Europe/Helsinki': 'FI',
  'Europe/Kiev': 'UA',
  'Europe/Kyiv': 'UA',
  'Europe/Moscow': 'RU',
  'Europe/Istanbul': 'TR',
  'America/New_York': 'US',
  'America/Chicago': 'US',
  'America/Denver': 'US',
  'America/Los_Angeles': 'US',
  'America/Mexico_City': 'MX',
  'America/Bogota': 'CO',
  'America/Lima': 'PE',
  'America/Santiago': 'CL',
  'America/Argentina/Buenos_Aires': 'AR',
  'America/Sao_Paulo': 'BR',
  'America/Toronto': 'CA',
  'America/Caracas': 'VE',
  'Africa/Casablanca': 'MA',
  'Africa/Cairo': 'EG',
  'Africa/Johannesburg': 'ZA',
  'Asia/Dubai': 'AE',
  'Asia/Tokyo': 'JP',
  'Asia/Shanghai': 'CN',
  'Asia/Kolkata': 'IN',
  'Asia/Singapore': 'SG',
  'Australia/Sydney': 'AU',
  'Pacific/Auckland': 'NZ',
}

function parseOffset(label: string): number {
  const match = /GMT([+-])(\d{1,2})(?::(\d{2}))?/.exec(label)
  if (!match) return 0
  const minutes = Number(match[2]) * 60 + Number(match[3] ?? 0)
  return match[1] === '-' ? -minutes : minutes
}

export function zoneOffsetLabel(zone: string, at: Date = new Date()): string {
  try {
    const parts = new Intl.DateTimeFormat('en', { timeZone: zone, timeZoneName: 'shortOffset' })
      .formatToParts(at)
      .find((part) => part.type === 'timeZoneName')
    return parts?.value ?? 'GMT'
  } catch {
    return 'GMT'
  }
}

export function zoneTime(zone: string, locale: string, at: Date = new Date()): string {
  try {
    return new Intl.DateTimeFormat(locale, {
      timeZone: zone,
      hour: '2-digit',
      minute: '2-digit',
    }).format(at)
  } catch {
    return ''
  }
}

function countryName(code: string | undefined, locale: string): string {
  if (!code) return ''
  try {
    return new Intl.DisplayNames([locale], { type: 'region' }).of(code) ?? ''
  } catch {
    return ''
  }
}

export function listZones(locale: string, at: Date = new Date()): ZoneInfo[] {
  const base =
    typeof Intl.supportedValuesOf === 'function' ? Intl.supportedValuesOf('timeZone') : []
  const ids = base.includes('UTC') ? base : [...base, 'UTC']
  return ids
    .map((id) => {
      const offsetLabel = zoneOffsetLabel(id, at)
      const parts = id.split('/')
      return {
        id,
        region: parts.length > 1 ? parts[0] : 'UTC',
        city: parts[parts.length - 1].replace(/_/g, ' '),
        offsetMinutes: parseOffset(offsetLabel),
        offsetLabel,
        country: countryName(ZONE_COUNTRY[id], locale),
      }
    })
    .sort((a, b) => a.offsetMinutes - b.offsetMinutes || a.id.localeCompare(b.id))
}

export function groupByRegion(zones: ZoneInfo[]): Array<[string, ZoneInfo[]]> {
  const map = new Map<string, ZoneInfo[]>()
  for (const zone of zones) {
    const list = map.get(zone.region) ?? []
    list.push(zone)
    map.set(zone.region, list)
  }
  return Array.from(map).sort((a, b) => a[0].localeCompare(b[0]))
}

export function zoneSearchText(zone: ZoneInfo): string {
  return `${zone.id} ${zone.city} ${zone.region} ${zone.country} ${zone.offsetLabel}`
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
}
