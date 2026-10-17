'use client'

import { Suspense, useEffect, useState } from 'react'
import Image from 'next/image'
import { useRouter, useSearchParams } from 'next/navigation'
import { IconLoader2 } from '@tabler/icons-react'
import { LocaleSwitcher } from '@/components/layout/locale-switcher'
import { ThemeToggle } from '@/components/layout/theme-toggle'
import { BrandLogo } from '@/components/shared/brand-logo'
import { MicrosoftIcon } from '@/components/shared/microsoft-icon'
import { Button } from '@/components/shadcn-ui/button'
import { Input } from '@/components/shadcn-ui/input'
import { Label } from '@/components/shadcn-ui/label'
import { Separator } from '@/components/shadcn-ui/separator'
import { useAuth } from '@/contexts/auth-context'
import { useDictionary } from '@/contexts/dictionary-context'
import { useLocalePath } from '@/hooks/use-locale-path'
import { api } from '@/lib/api'
import { ApiError } from '@/lib/api-client'

const MICROSOFT_AUTHORIZATION_PATH = '/core-api/oauth2/authorization/azure'

function safeRedirect(value: string | null): string | null {
  return value && value.startsWith('/') && !value.startsWith('//') ? value : null
}

function useOauthError(code: string | null): string | null {
  const dict = useDictionary()
  if (!code) return null
  const normalized = code.toLowerCase()
  if (normalized.includes('disabled')) return dict.auth.oauthErrors.disabled
  if (
    normalized.includes('link') ||
    normalized.includes('unknown') ||
    normalized.includes('found')
  ) {
    return dict.auth.oauthErrors.unlinked
  }
  if (
    normalized.includes('access') ||
    normalized.includes('forbidden') ||
    normalized.includes('denied')
  ) {
    return dict.auth.oauthErrors.noAccess
  }
  return dict.auth.oauthErrors.provider
}

function LoginForm() {
  const dict = useDictionary()
  const { login } = useAuth()
  const router = useRouter()
  const search = useSearchParams()
  const localePath = useLocalePath()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [microsoftEnabled, setMicrosoftEnabled] = useState(false)
  const oauthError = useOauthError(search.get('error'))
  const redirect = safeRedirect(search.get('redirect'))

  useEffect(() => {
    let active = true
    api.auth
      .providers()
      .then((providers) => active && setMicrosoftEnabled(providers.microsoft === true))
      .catch(() => active && setMicrosoftEnabled(false))
    return () => {
      active = false
    }
  }, [])

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await login(username.trim(), password)
      router.replace(redirect ?? localePath('/dashboard'))
    } catch (err) {
      setError(err instanceof ApiError && err.status === 401 ? dict.auth.invalid : dict.auth.failed)
      setSubmitting(false)
    }
  }

  function signInWithMicrosoft() {
    const query = redirect ? `?redirect=${encodeURIComponent(redirect)}` : ''
    window.location.assign(`${MICROSOFT_AUTHORIZATION_PATH}${query}`)
  }

  const shownError = error ?? oauthError

  return (
    <div className="space-y-4">
      <form onSubmit={submit} className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="username">{dict.auth.username}</Label>
          <Input
            id="username"
            autoComplete="username"
            autoFocus
            required
            value={username}
            onChange={(event) => setUsername(event.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="password">{dict.auth.password}</Label>
          <Input
            id="password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </div>
        {shownError ? (
          <p role="alert" className="text-destructive text-sm">
            {shownError}
          </p>
        ) : null}
        <Button type="submit" className="h-9 w-full" disabled={submitting}>
          {submitting ? <IconLoader2 className="animate-spin" /> : null}
          {dict.auth.signIn}
        </Button>
      </form>
      {microsoftEnabled ? (
        <>
          <div className="flex items-center gap-3">
            <Separator className="flex-1" />
            <span className="text-muted-foreground text-xs uppercase">{dict.auth.or}</span>
            <Separator className="flex-1" />
          </div>
          <Button
            type="button"
            variant="outline"
            className="h-9 w-full"
            onClick={signInWithMicrosoft}
          >
            <MicrosoftIcon className="size-4" />
            {dict.auth.microsoft}
          </Button>
        </>
      ) : null}
    </div>
  )
}

export default function LoginPage() {
  const dict = useDictionary()
  return (
    <div className="relative grid min-h-svh lg:grid-cols-2">
      <div className="absolute top-4 right-4 flex items-center gap-1">
        <LocaleSwitcher />
        <ThemeToggle />
      </div>
      <div className="flex items-center justify-center p-6">
        <div className="w-full max-w-sm space-y-8">
          <BrandLogo />
          <div className="space-y-1">
            <h1 className="text-2xl font-semibold tracking-tight">{dict.auth.title}</h1>
            <p className="text-muted-foreground text-sm">{dict.auth.subtitle}</p>
          </div>
          <Suspense>
            <LoginForm />
          </Suspense>
        </div>
      </div>
      <div className="bg-primary text-primary-foreground relative hidden overflow-hidden lg:flex lg:flex-col lg:justify-end lg:p-12">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_30%_20%,rgba(255,255,255,0.25),transparent_55%)]" />
        <Image
          src="/brand/cefa-logo-white.svg"
          alt="CEFA"
          width={84}
          height={128}
          unoptimized
          className="absolute top-12 left-12 h-24 w-auto"
        />
        <div className="relative max-w-md space-y-3">
          <p className="text-3xl font-semibold tracking-tight">{dict.auth.heroTitle}</p>
          <p className="text-primary-foreground/80 text-sm">{dict.auth.heroText}</p>
        </div>
      </div>
    </div>
  )
}
