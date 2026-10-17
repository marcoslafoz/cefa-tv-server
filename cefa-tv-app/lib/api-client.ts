import type { ApiErrorBody } from '@/types/api'
import { authEvents } from '@/lib/auth-events'

export const CORE_API_PREFIX = '/core-api'
export const API_V1 = '/api/v1'

export class ApiError extends Error {
  status: number
  errorCode: string
  details: Record<string, string>

  constructor(body: ApiErrorBody) {
    super(body.message)
    this.status = body.status
    this.errorCode = body.errorCode
    this.details = body.details ?? {}
  }
}

async function parseError(res: Response, path: string): Promise<never> {
  if (res.status === 401 && !path.startsWith(`${API_V1}/auth/login`)) {
    authEvents.emit401()
  }
  if (res.status === 403) authEvents.emitForbidden()
  const body = (await res.json().catch(() => null)) as ApiErrorBody | null
  if (body?.message) throw new ApiError(body)
  throw new Error(`HTTP ${res.status} ${path}`)
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const isFormData = options.body instanceof FormData
  const res = await fetch(`${CORE_API_PREFIX}${path}`, {
    ...options,
    credentials: 'include',
    headers: isFormData
      ? options.headers
      : { 'Content-Type': 'application/json', ...options.headers },
  })
  if (!res.ok) return parseError(res, path)
  if (res.status === 204) return undefined as T
  const text = await res.text()
  if (!text) return undefined as T
  return JSON.parse(text) as T
}

function body(value: unknown): string | undefined {
  return value === undefined ? undefined : JSON.stringify(value)
}

export const apiClient = {
  get: <T>(path: string, signal?: AbortSignal) => request<T>(path, { method: 'GET', signal }),
  post: <T>(path: string, value?: unknown) =>
    request<T>(path, { method: 'POST', body: body(value) }),
  put: <T>(path: string, value?: unknown) => request<T>(path, { method: 'PUT', body: body(value) }),
  patch: <T>(path: string, value?: unknown) =>
    request<T>(path, { method: 'PATCH', body: body(value) }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}

export function uploadWithProgress<T>(
  path: string,
  form: FormData,
  onProgress: (fraction: number) => void
): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', `${CORE_API_PREFIX}${path}`)
    xhr.withCredentials = true
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress(event.loaded / event.total)
    }
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve((xhr.responseText ? JSON.parse(xhr.responseText) : undefined) as T)
        return
      }
      if (xhr.status === 401) authEvents.emit401()
      if (xhr.status === 403) authEvents.emitForbidden()
      try {
        reject(new ApiError(JSON.parse(xhr.responseText) as ApiErrorBody))
      } catch {
        reject(new Error(`HTTP ${xhr.status} ${path}`))
      }
    }
    xhr.onerror = () => reject(new Error(`Network error ${path}`))
    xhr.send(form)
  })
}

export function apiErrorMessage(err: unknown, fallback: string): string {
  if (err instanceof ApiError) {
    const detail = Object.values(err.details)[0]
    if (detail) return detail
    if (err.message) return err.message
  }
  return fallback
}

export function mediaContentUrl(id: string): string {
  return `${CORE_API_PREFIX}${API_V1}/media/${id}/content`
}

export function mediaThumbnailUrl(id: string): string {
  return `${CORE_API_PREFIX}${API_V1}/media/${id}/thumbnail`
}

function socketUrl(path: string): string {
  const configured = process.env.NEXT_PUBLIC_CORE_WS_URL
  if (configured) return `${configured.replace(/\/$/, '')}${path}`
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}${CORE_API_PREFIX}${path}`
}

export function previewsSocketUrl(): string {
  return socketUrl('/ws/previews')
}

export function playerPreviewUrl(playerId: string): string {
  return `${CORE_API_PREFIX}${API_V1}/players/${playerId}/preview`
}

export function remoteSocketUrl(playerId: string): string {
  return socketUrl(`/ws/remote/${playerId}`)
}

export function shellSocketUrl(playerId: string): string {
  return socketUrl(`/ws/shell/${playerId}`)
}
