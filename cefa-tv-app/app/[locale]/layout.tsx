import { notFound } from 'next/navigation'
import { hasLocale, locales } from '@/i18n/config'
import { getDictionary } from '@/i18n/get-dictionary'
import type { Locale } from '@/i18n/config'
import { DictionaryProvider } from '@/contexts/dictionary-context'
import { AuthProvider } from '@/contexts/auth-context'
import { HtmlLangSync } from '@/components/providers/html-lang-sync'

export async function generateStaticParams() {
  return locales.map((locale) => ({ locale }))
}

export default async function LocaleLayout({
  children,
  params,
}: {
  children: React.ReactNode
  params: Promise<{ locale: string }>
}) {
  const { locale } = await params
  if (!hasLocale(locale)) notFound()

  const dict = await getDictionary(locale as Locale)

  return (
    <DictionaryProvider dictionary={dict}>
      <HtmlLangSync locale={locale} />
      <AuthProvider>{children}</AuthProvider>
    </DictionaryProvider>
  )
}
