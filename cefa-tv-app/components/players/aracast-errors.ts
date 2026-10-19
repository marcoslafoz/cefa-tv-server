import { ApiError, apiErrorMessage } from '@/lib/api-client'

export function aracastErrorMessage(
  err: unknown,
  messages: Record<string, string>,
  fallback: string
): string {
  if (err instanceof ApiError && messages[err.errorCode]) return messages[err.errorCode]
  return apiErrorMessage(err, fallback)
}
