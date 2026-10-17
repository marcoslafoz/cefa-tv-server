export interface ApiErrorBody {
  status: number
  errorCode: string
  message: string
  details?: Record<string, string>
}

export interface Page<T> {
  content: T[]
  totalElements: number
  page: number
  size: number
}

export type Permission =
  | 'MEDIA_MANAGE'
  | 'PLAYLIST_MANAGE'
  | 'SCHEDULE_MANAGE'
  | 'PLAYER_VIEW'
  | 'PLAYER_MANAGE'
  | 'PLAYER_REMOTE'
  | 'PLAYER_TERMINAL'
  | 'USER_MANAGE'
  | 'AUDIT_VIEW'

export type AccessLevel = 'VIEW' | 'OPERATE'

export interface AccessGrant {
  playerGroupId?: string | null
  siteId?: string | null
  level: AccessLevel
}

export interface Site {
  id: string
  name: string
  city: string | null
  country: string | null
  timezone: string | null
  playerCount: number
  onlineCount: number
}

export interface SiteInput {
  name: string
  city: string | null
  country: string | null
  timezone: string | null
}

export interface AuthUser {
  id: string
  username: string
  displayName: string | null
  groups: Array<{ id: string; name: string }>
  permissions: Permission[]
  grants: AccessGrant[]
  admin: boolean
  email?: string | null
  authProvider?: AuthProvider
}

export type AuthProvider = 'LOCAL' | 'MICROSOFT' | 'HYBRID'

export interface AuthProvidersInfo {
  local: boolean
  microsoft: boolean
}

export interface AppUser {
  id: string
  username: string
  displayName: string | null
  email: string | null
  authProvider: AuthProvider
  enabled: boolean
  createdAt: string
  groupIds: string[]
}

export interface UserUpdateInput {
  displayName: string
  email: string | null
  authProvider: AuthProvider
  enabled: boolean
  groupIds: string[]
}

export interface UserCreateInput extends UserUpdateInput {
  username: string
  password?: string
}

export interface UserGroup {
  id: string
  name: string
  description: string | null
  builtin?: boolean
  allAccess: boolean
  permissions: Permission[]
  memberIds: string[]
  grants: AccessGrant[]
}

export interface UserGroupInput {
  name: string
  description: string | null
  allAccess: boolean
  permissions: Permission[]
  memberIds: string[]
  grants: AccessGrant[]
}

export interface PendingPairing {
  id: string
  hostname: string | null
  ip: string | null
  resolution: string | null
  playerVersion: string | null
  requestedAt: string
  expiresAt: string
}

export interface Folder {
  id: string
  name: string
  parentId: string | null
  itemCount?: number
  subfolderCount?: number
}

export type MediaType = 'IMAGE' | 'VIDEO' | 'PDF' | 'WEB' | 'HTML'
export type MediaStatus = 'READY' | 'PROCESSING' | 'FAILED'

export interface MediaAsset {
  id: string
  name: string
  type: MediaType
  mime: string | null
  sizeBytes: number
  status: MediaStatus
  durationMs: number | null
  width: number | null
  height: number | null
  pageCount: number | null
  url: string | null
  refreshSec: number | null
  folderId?: string | null
  folderPath?: Array<{ id: string; name: string }>
  createdAt: string
}

export type PlaylistFit = 'COVER' | 'CONTAIN'

export interface PlaylistItem {
  id?: string
  position?: number
  asset: MediaAsset
  durationSec: number
  fit: PlaylistFit
  muted: boolean
}

export interface Playlist {
  id: string
  name: string
  description: string | null
  hidden: boolean
  totalDurationSec: number
  items: PlaylistItem[]
  updatedAt?: string
}

export interface PlaylistInput {
  name: string
  hidden?: boolean
  description: string | null
  items: Array<{ assetId: string; durationSec: number; fit: PlaylistFit; muted: boolean }>
}

export interface PlayerGroup {
  id: string
  name: string
  idleLogo?: IdleLogo | null
  siteId?: string | null
  siteName?: string | null
  playerCount?: number
  fallbackPlaylistId?: string | null
  icon?: string | null
  color?: string | null
}

export type PlayerStatus = 'ACTIVE' | 'DISABLED'
export type PlayerState = 'IDLE' | 'DOWNLOADING' | 'PLAYING' | 'ERROR' | string
export type Orientation = 'LANDSCAPE' | 'PORTRAIT'
export type IdleLogo = 'CEFA' | 'ALGONTEC'
export type IdleLogoSource = 'PLAYER' | 'GROUP' | 'DEFAULT'

export interface Player {
  id: string
  name: string
  description?: string | null
  groupId: string | null
  groupName?: string | null
  siteId?: string | null
  siteName?: string | null
  status: PlayerStatus
  hostname: string | null
  ip: string | null
  resolution: string | null
  orientation: Orientation | null
  playerVersion: string | null
  playerBuild?: string | null
  timezone: string | null
  lastSeenAt: string | null
  online: boolean
  desiredVersion: number
  appliedVersion: number
  pending: boolean
  state: PlayerState | null
  currentPlaylistId: string | null
  currentAssetId: string | null
  currentAssetName?: string | null
  currentAssetThumbnailUrl?: string | null
  fallbackPlaylistId?: string | null
  lastError: string | null
  neverConnected?: boolean
  preferredOutput?: string | null
  idleLogo?: IdleLogo | null
  effectiveIdleLogo?: IdleLogo | null
  idleLogoSource?: IdleLogoSource | null
  system?: PlayerSystem | null
  displays?: PlayerDisplay[] | null
}

export interface PlayerSystem {
  hostname: string | null
  os: string | null
  kernel: string | null
  arch: string | null
  cpuModel: string | null
  cpuCount: number | null
  cpuPercent: number | null
  load1: number | null
  ramTotalMb: number | null
  ramUsedMb: number | null
  diskTotalMb: number | null
  diskFreeMb: number | null
  tempC: number | null
  uptimeSec: number | null
  ips: string[] | null
  macs: string[] | null
  playerVersion: string | null
  playerBuild?: string | null
  javaVersion: string | null
  chromiumVersion: string | null
  timezone: string | null
  displayMode: string | null
  shellEnabled: boolean | null
  shellMode: string | null
}

export interface PlayerDisplay {
  output: string
  connected: boolean
  primary: boolean
  selected: boolean
  width: number | null
  height: number | null
  refreshHz: number | null
  xOffset: number | null
  yOffset: number | null
  modes: string[] | null
}

export interface PlayerMetric {
  at: string
  cpuPercent: number | null
  ramUsedMb: number | null
  diskFreeMb: number | null
  tempC: number | null
}

export interface ShellAudit {
  id: string
  playerId: string
  userId: string | null
  username: string
  openedAt: string
  closedAt: string | null
  bytesIn: number
  bytesOut: number
  closeReason: string | null
}

export type DeliveryStatus = 'QUEUED' | 'DOWNLOADING' | 'APPLIED' | 'FAILED'

export interface Delivery {
  version: number
  status: DeliveryStatus
  createdAt: string
  deliveredAt: string | null
  error: string | null
}

export interface ScheduleTargets {
  playerIds: string[]
  groupIds: string[]
  siteIds?: string[]
}

export type ScheduleServerStatus = 'ACTIVE_NOW' | 'SCHEDULED' | 'ENDED' | 'DISABLED'

export interface Schedule {
  id: string
  name: string
  playlistId: string
  playlistName?: string
  playlistHidden?: boolean
  priority: number
  startDate: string | null
  endDate: string | null
  startTime: string | null
  endTime: string | null
  daysOfWeek: number[]
  allDay: boolean
  enabled: boolean
  targets: ScheduleTargets
  thumbnailUrls?: string[]
  itemCount?: number
  totalDurationSec?: number
  status?: ScheduleServerStatus
  nextRunAt?: string | null
  timezone?: string | null
  timezones?: string[]
}

export interface ScheduleInput {
  name: string
  playlistId?: string
  assetId?: string
  durationSec?: number
  priority: number
  startDate: string | null
  endDate: string | null
  startTime: string | null
  endTime: string | null
  daysOfWeek: number[]
  allDay: boolean
  enabled: boolean
  targets: ScheduleTargets
}

export interface TimelineOccurrence {
  scheduleId: string
  name: string
  playlistId: string
  playlistName?: string
  date: string
  startTime: string
  endTime: string
  priority: number
  allDay: boolean
  thumbnailUrls?: string[]
  itemCount?: number
  totalDurationSec?: number
  playerId?: string
  playerName?: string
  timezone?: string | null
  status?: ScheduleServerStatus
  nextRunAt?: string | null
}

export interface DashboardStats {
  players: number
  playersOnline: number
  playersOffline: number
  playersPending: number
  media: number
  playlists: number
  storageBytes: number
}

export interface SystemInfo {
  service: string
  name: string
  version: string
}

export type RemoteInputKind =
  'MOUSE_MOVE' | 'MOUSE_DOWN' | 'MOUSE_UP' | 'MOUSE_WHEEL' | 'KEY_DOWN' | 'KEY_UP' | 'TEXT'

export interface RemoteInput {
  type: 'INPUT'
  kind: RemoteInputKind
  x?: number
  y?: number
  button?: 'left' | 'middle' | 'right'
  deltaX?: number
  deltaY?: number
  key?: string
  code?: string
  windowsVirtualKeyCode?: number
  text?: string
  modifiers?: number
}

export type AuditOutcome = 'SUCCESS' | 'DENIED' | 'FAILED'

export interface AuditChange {
  field: string
  before: unknown
  after: unknown
}

export interface AuditEvent {
  id: string
  at: string
  userId: string | null
  username: string
  action: string
  entityType: string | null
  entityId: string | null
  entityName: string | null
  outcome: AuditOutcome
  details: Record<string, unknown> | null
  ip: string | null
  userAgent: string | null
}

export interface AuditAction {
  action: string
  entityType: string | null
}

export interface AuditFilters {
  from?: string
  to?: string
  userId?: string
  action?: string
  entityType?: string
  entityId?: string
  outcome?: AuditOutcome | ''
  q?: string
}

export interface AuditStats {
  from: string
  to: string
  total: number
  byOutcome: Record<string, number>
  topActions: Array<{ action: string; total: number }>
  topUsers: Array<{ userId: string | null; username: string; total: number }>
  byDay: Array<{ day: string; total: number }>
}

export interface AuditSettings {
  retentionDays: number
  minRetentionDays: number
  maxRetentionDays: number
}
