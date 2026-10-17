import type { AccessLevel, AuthUser, Permission } from '@/types/api'

export const PERMISSION_GROUPS: Array<{
  id: 'content' | 'players' | 'admin'
  items: Permission[]
}> = [
  { id: 'content', items: ['MEDIA_MANAGE', 'PLAYLIST_MANAGE', 'SCHEDULE_MANAGE'] },
  { id: 'players', items: ['PLAYER_VIEW', 'PLAYER_MANAGE', 'PLAYER_REMOTE', 'PLAYER_TERMINAL'] },
  { id: 'admin', items: ['USER_MANAGE', 'AUDIT_VIEW'] },
]

const LEVEL_RANK: Record<AccessLevel, number> = { VIEW: 1, OPERATE: 2 }

export function hasPermission(user: AuthUser | null, permission: Permission): boolean {
  if (!user) return false
  return user.admin || user.permissions.includes(permission)
}

export interface AccessScope {
  groupId?: string | null
  siteId?: string | null
}

export function canAccessScope(
  user: AuthUser | null,
  scope: AccessScope,
  level: AccessLevel
): boolean {
  if (!user) return false
  if (user.admin) return true
  return user.grants.some(
    (grant) =>
      LEVEL_RANK[grant.level] >= LEVEL_RANK[level] &&
      ((Boolean(grant.playerGroupId) && grant.playerGroupId === scope.groupId) ||
        (Boolean(grant.siteId) && grant.siteId === scope.siteId))
  )
}

export function canAccessGroup(
  user: AuthUser | null,
  groupId: string | null | undefined,
  level: AccessLevel
): boolean {
  return canAccessScope(user, { groupId }, level)
}

export function canAccessSite(
  user: AuthUser | null,
  siteId: string | null | undefined,
  level: AccessLevel
): boolean {
  return canAccessScope(user, { siteId }, level)
}

export function canOnPlayer(
  user: AuthUser | null,
  permission: Permission,
  scope: AccessScope | string | null | undefined,
  level: AccessLevel = 'OPERATE'
): boolean {
  const resolved: AccessScope =
    typeof scope === 'object' && scope !== null ? scope : { groupId: scope }
  return hasPermission(user, permission) && canAccessScope(user, resolved, level)
}

export function operableGroupIds(user: AuthUser | null, groupIds: string[]): string[] {
  return groupIds.filter((id) => canAccessGroup(user, id, 'OPERATE'))
}
