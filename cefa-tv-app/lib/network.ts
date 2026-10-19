const OCTET = '(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)'
const IPV4 = new RegExp(`^${OCTET}(\\.${OCTET}){3}$`)
const HOSTNAME = /^[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?$/

export function isIpv4(value: string): boolean {
  return IPV4.test(value.trim())
}

export function isCidr(value: string): boolean {
  const [address, prefix, ...rest] = value.trim().split('/')
  if (rest.length > 0 || prefix === undefined || !/^\d{1,2}$/.test(prefix)) return false
  return Number(prefix) <= 32 && isIpv4(address)
}

export function isHostname(value: string): boolean {
  return HOSTNAME.test(value.trim())
}

export function parseIpRanges(text: string): string[] {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
}
