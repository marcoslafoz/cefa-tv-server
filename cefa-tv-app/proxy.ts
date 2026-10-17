import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'
import { defaultLocale, hasLocale, locales } from '@/i18n/config'

const GUEST_ONLY_ROUTES = ['/login']
const SESSION_COOKIE = 'ctv_session'

function applyAuthGuard(request: NextRequest, pathname: string, locale: string) {
  const pathWithoutLocale = pathname.slice(locale.length + 1) || '/'
  const isGuestOnlyRoute = GUEST_ONLY_ROUTES.includes(pathWithoutLocale)
  const hasSession = request.cookies.has(SESSION_COOKIE)

  if (!hasSession && !isGuestOnlyRoute) {
    const loginUrl = new URL(`/${locale}/login`, request.url)
    loginUrl.searchParams.set('redirect', pathname + request.nextUrl.search)
    return NextResponse.redirect(loginUrl)
  }

  if (hasSession && isGuestOnlyRoute) {
    return NextResponse.redirect(new URL(`/${locale}/dashboard`, request.url))
  }

  return NextResponse.next()
}

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl

  if (
    pathname.startsWith('/_next') ||
    pathname.startsWith('/api') ||
    pathname.startsWith('/core-api') ||
    pathname.startsWith('/favicon') ||
    pathname.endsWith('.svg') ||
    pathname.endsWith('.ico')
  ) {
    return NextResponse.next()
  }

  const pathLocale = pathname.split('/')[1]
  if (hasLocale(pathLocale)) {
    return applyAuthGuard(request, pathname, pathLocale)
  }

  const cookieLocale = request.cookies.get('NEXT_LOCALE')?.value
  const acceptLanguage = request.headers.get('accept-language') || ''
  const preferred = acceptLanguage.split(',')[0].split('-')[0].toLowerCase()
  const locale =
    cookieLocale && hasLocale(cookieLocale)
      ? cookieLocale
      : locales.includes(preferred as (typeof locales)[number])
        ? preferred
        : defaultLocale

  const target = new URL(`/${locale}${pathname}`, request.url)
  target.search = request.nextUrl.search
  return NextResponse.redirect(target)
}
