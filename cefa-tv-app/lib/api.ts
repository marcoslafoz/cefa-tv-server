import { API_V1, CORE_API_PREFIX, apiClient } from '@/lib/api-client'
import type {
  AuditAction,
  AuditEvent,
  AuditFilters,
  AuditSettings,
  AuditStats,
  DashboardStats,
  Delivery,
  Folder,
  IdleLogo,
  AppUser,
  AuthProvidersInfo,
  PendingPairing,
  Site,
  SiteInput,
  UserGroup,
  UserGroupInput,
  UserCreateInput,
  UserUpdateInput,
  MediaAsset,
  Page,
  Player,
  PlayerMetric,
  PlayerGroup,
  Playlist,
  PlaylistInput,
  Schedule,
  ScheduleInput,
  ShellAudit,
  SystemInfo,
  TimelineOccurrence,
} from '@/types/api'

export function asList<T>(value: T[] | Page<T> | null | undefined): T[] {
  if (!value) return []
  return Array.isArray(value) ? value : value.content
}

function dayStart(date: string, offsetDays = 0): string {
  const value = new Date(`${date}T00:00:00`)
  value.setDate(value.getDate() + offsetDays)
  return value.toISOString()
}

export function auditQuery(filters: AuditFilters, extra: Record<string, string | number> = {}) {
  const query = new URLSearchParams()
  Object.entries(filters).forEach(([key, value]) => {
    if (!value) return
    if (key === 'from') query.set(key, dayStart(String(value)))
    else if (key === 'to') query.set(key, dayStart(String(value), 1))
    else query.set(key, String(value))
  })
  Object.entries(extra).forEach(([key, value]) => query.set(key, String(value)))
  return query.toString()
}

function appendFolderScope(
  query: URLSearchParams,
  folderId: string | null | undefined,
  searching: boolean
) {
  if (searching || folderId === undefined) {
    query.set('recursive', 'true')
    return
  }
  query.set('folderId', folderId ?? 'root')
  query.set('recursive', 'false')
}

export const api = {
  dashboard: () => apiClient.get<DashboardStats>(`${API_V1}/dashboard`),

  audit: {
    list: (filters: AuditFilters, page: number, size: number) =>
      apiClient.get<Page<AuditEvent>>(`${API_V1}/audit?${auditQuery(filters, { page, size })}`),
    get: (id: string) => apiClient.get<AuditEvent>(`${API_V1}/audit/${id}`),
    stats: (filters: AuditFilters) =>
      apiClient.get<AuditStats>(`${API_V1}/audit/stats?${auditQuery(filters)}`),
    actions: () => apiClient.get<AuditAction[]>(`${API_V1}/audit/actions`),
    exportUrl: (filters: AuditFilters) =>
      `${CORE_API_PREFIX}${API_V1}/audit/export.csv?${auditQuery(filters)}`,
    settings: () => apiClient.get<AuditSettings>(`${API_V1}/settings/audit`),
    updateSettings: (input: { retentionDays: number }) =>
      apiClient.put<AuditSettings>(`${API_V1}/settings/audit`, input),
  },
  systemInfo: () => apiClient.get<SystemInfo>(`${API_V1}/public/info`),

  sites: {
    list: () => apiClient.get<Site[] | Page<Site>>(`${API_V1}/sites`),
    get: (id: string) => apiClient.get<Site>(`${API_V1}/sites/${id}`),
    create: (input: SiteInput) => apiClient.post<Site>(`${API_V1}/sites`, input),
    update: (id: string, input: SiteInput) => apiClient.put<Site>(`${API_V1}/sites/${id}`, input),
    remove: (id: string, reassignTo: string | null) =>
      apiClient.delete<void>(
        `${API_V1}/sites/${id}${reassignTo ? `?reassignTo=${reassignTo}` : ''}`
      ),
  },

  folders: {
    list: () => apiClient.get<Folder[] | Page<Folder>>(`${API_V1}/folders?kind=MEDIA`),
    create: (input: { name: string; parentId: string | null }) =>
      apiClient.post<Folder>(`${API_V1}/folders`, { ...input, kind: 'MEDIA' }),
    update: (
      id: string,
      input: { name?: string; parentId?: string | null; clearParent?: boolean }
    ) => apiClient.patch<Folder>(`${API_V1}/folders/${id}`, input),
    remove: (id: string, moveContentsToParent: boolean) =>
      apiClient.delete<void>(
        `${API_V1}/folders/${id}?moveContentsToParent=${moveContentsToParent}`
      ),
  },

  media: {
    list: (params: {
      type?: string
      q?: string
      page?: number
      size?: number
      folderId?: string | null
    }) => {
      const query = new URLSearchParams()
      if (params.type) query.set('type', params.type)
      if (params.q) query.set('q', params.q)
      appendFolderScope(query, params.folderId, Boolean(params.q))
      query.set('page', String(params.page ?? 0))
      query.set('size', String(params.size ?? 60))
      return apiClient.get<Page<MediaAsset>>(`${API_V1}/media?${query.toString()}`)
    },
    createWeb: (input: {
      name: string
      url: string
      refreshSec: number | null
      folderId?: string | null
    }) => apiClient.post<MediaAsset>(`${API_V1}/media/web`, input),
    update: (id: string, input: { name?: string }) =>
      apiClient.patch<MediaAsset>(`${API_V1}/media/${id}`, input),
    move: (ids: string[], folderId: string | null) =>
      apiClient.post<void>(`${API_V1}/media/move`, { ids, folderId }),
    remove: (id: string) => apiClient.delete<void>(`${API_V1}/media/${id}`),
  },

  playlists: {
    list: () => apiClient.get<Playlist[] | Page<Playlist>>(`${API_V1}/playlists`),
    get: (id: string) => apiClient.get<Playlist>(`${API_V1}/playlists/${id}`),
    create: (input: PlaylistInput) => apiClient.post<Playlist>(`${API_V1}/playlists`, input),
    update: (id: string, input: PlaylistInput) =>
      apiClient.put<Playlist>(`${API_V1}/playlists/${id}`, input),
    remove: (id: string) => apiClient.delete<void>(`${API_V1}/playlists/${id}`),
  },

  groups: {
    list: () => apiClient.get<PlayerGroup[] | Page<PlayerGroup>>(`${API_V1}/groups`),
    create: (input: {
      name: string
      icon?: string | null
      color?: string | null
      siteId?: string
      idleLogo?: IdleLogo
    }) => apiClient.post<PlayerGroup>(`${API_V1}/groups`, input),
    update: (
      id: string,
      input: {
        name: string
        fallbackPlaylistId?: string | null
        icon?: string | null
        color?: string | null
        siteId?: string
        clearSite?: boolean
        idleLogo?: IdleLogo
        clearIdleLogo?: boolean
      }
    ) => apiClient.put<PlayerGroup>(`${API_V1}/groups/${id}`, input),
    remove: (id: string) => apiClient.delete<void>(`${API_V1}/groups/${id}`),
  },

  players: {
    list: () => apiClient.get<Player[] | Page<Player>>(`${API_V1}/players`),
    get: (id: string) => apiClient.get<Player>(`${API_V1}/players/${id}`),
    update: (
      id: string,
      input: Partial<Pick<Player, 'name' | 'orientation' | 'status'>> & {
        groupId?: string | null
        fallbackPlaylistId?: string | null
        clearFallbackPlaylist?: boolean
        preferredOutput?: string
        clearPreferredOutput?: boolean
        idleLogo?: IdleLogo
        clearIdleLogo?: boolean
        description?: string
        siteId?: string
        clearSite?: boolean
      }
    ) => apiClient.patch<Player>(`${API_V1}/players/${id}`, input),
    remove: (id: string) => apiClient.delete<void>(`${API_V1}/players/${id}`),
    command: (id: string, command: 'resync' | 'restart' | 'reload') =>
      apiClient.post<void>(`${API_V1}/players/${id}/${command}`),
    metrics: (id: string, hours: number) =>
      apiClient.get<PlayerMetric[]>(`${API_V1}/players/${id}/metrics?hours=${hours}`),
    redetectDisplay: (id: string) =>
      apiClient.post<void>(`${API_V1}/players/${id}/display/redetect`),
    setResolution: (id: string, input: { output: string; width: number; height: number }) =>
      apiClient.post<void>(`${API_V1}/players/${id}/display/resolution`, input),
    shellAudits: (id: string) =>
      apiClient.get<ShellAudit[] | Page<ShellAudit>>(`${API_V1}/players/${id}/shell-audits`),
    deliveries: (id: string) =>
      apiClient.get<Delivery[] | Page<Delivery>>(`${API_V1}/players/${id}/deliveries`),
  },

  pairings: {
    pending: () =>
      apiClient.get<PendingPairing[] | Page<PendingPairing>>(`${API_V1}/pairings/pending`),
    claim: (input: { code: string; name: string; groupId: string | null }) =>
      apiClient.post<Player>(`${API_V1}/pairings/claim`, input),
  },

  users: {
    list: () => apiClient.get<AppUser[] | Page<AppUser>>(`${API_V1}/users`),
    create: (input: UserCreateInput) => apiClient.post<AppUser>(`${API_V1}/users`, input),
    update: (id: string, input: UserUpdateInput) =>
      apiClient.put<AppUser>(`${API_V1}/users/${id}`, input),
    remove: (id: string) => apiClient.delete<void>(`${API_V1}/users/${id}`),
    setPassword: (id: string, password: string) =>
      apiClient.post<void>(`${API_V1}/users/${id}/password`, { password }),
  },

  userGroups: {
    list: () => apiClient.get<UserGroup[] | Page<UserGroup>>(`${API_V1}/user-groups`),
    create: (input: UserGroupInput) => apiClient.post<UserGroup>(`${API_V1}/user-groups`, input),
    update: (id: string, input: UserGroupInput) =>
      apiClient.put<UserGroup>(`${API_V1}/user-groups/${id}`, input),
    remove: (id: string) => apiClient.delete<void>(`${API_V1}/user-groups/${id}`),
  },

  auth: {
    providers: () => apiClient.get<AuthProvidersInfo>(`${API_V1}/auth/providers`),
    changePassword: (input: { currentPassword: string; newPassword: string }) =>
      apiClient.post<void>(`${API_V1}/auth/password`, input),
  },

  schedules: {
    list: () => apiClient.get<Schedule[] | Page<Schedule>>(`${API_V1}/schedules`),
    get: (id: string) => apiClient.get<Schedule>(`${API_V1}/schedules/${id}`),
    create: (input: ScheduleInput) => apiClient.post<Schedule>(`${API_V1}/schedules`, input),
    update: (id: string, input: ScheduleInput) =>
      apiClient.put<Schedule>(`${API_V1}/schedules/${id}`, input),
    remove: (id: string) => apiClient.delete<void>(`${API_V1}/schedules/${id}`),
    timeline: (
      targets: { playerIds?: string[]; groupIds?: string[]; siteIds?: string[] },
      from: string,
      to: string
    ) => {
      const params = new URLSearchParams({ from, to })
      for (const id of targets.playerIds ?? []) params.append('playerIds', id)
      for (const id of targets.groupIds ?? []) params.append('groupIds', id)
      for (const id of targets.siteIds ?? []) params.append('siteIds', id)
      return apiClient.get<TimelineOccurrence[]>(
        `${API_V1}/schedules/timeline?${params.toString()}`
      )
    },
  },
}
