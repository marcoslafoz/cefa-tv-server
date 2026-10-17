import type { Player } from '@/types/api'

function parts(version: string): number[] {
  return version
    .replace(/^v/i, '')
    .split(/[.+-]/)
    .map((segment) => Number.parseInt(segment, 10))
    .map((value) => (Number.isNaN(value) ? 0 : value))
}

export function compareVersions(left: string, right: string): number {
  const a = parts(left)
  const b = parts(right)
  const length = Math.max(a.length, b.length)
  for (let index = 0; index < length; index += 1) {
    const difference = (a[index] ?? 0) - (b[index] ?? 0)
    if (difference !== 0) return difference
  }
  return 0
}

export function playerBuild(player: Player): string | null {
  return player.playerBuild ?? player.system?.playerBuild ?? null
}

export function formatPlayerVersion(player: Player): string | null {
  if (!player.playerVersion) return null
  const build = playerBuild(player)
  return build ? `v${player.playerVersion} · ${build}` : `v${player.playerVersion}`
}

export function latestVersion(players: Player[]): string | null {
  return players.reduce<string | null>((latest, player) => {
    if (!player.playerVersion) return latest
    return latest === null || compareVersions(player.playerVersion, latest) > 0
      ? player.playerVersion
      : latest
  }, null)
}

export function isOutdated(player: Player, latest: string | null): boolean {
  return (
    latest !== null &&
    Boolean(player.playerVersion) &&
    compareVersions(player.playerVersion as string, latest) < 0
  )
}
