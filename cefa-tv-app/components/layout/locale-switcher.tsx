'use client'

import { IconLanguage } from '@tabler/icons-react'
import { usePathname, useRouter } from 'next/navigation'
import { Button } from '@/components/shadcn-ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/shadcn-ui/dropdown-menu'
import { useDictionary } from '@/contexts/dictionary-context'
import { locales, type Locale } from '@/i18n/config'
import { setUserLocale } from '@/i18n/locale-detector'

export function LocaleSwitcher() {
  const dict = useDictionary()
  const pathname = usePathname()
  const router = useRouter()

  function change(locale: Locale) {
    setUserLocale(locale)
    const segments = pathname.split('/')
    segments[1] = locale
    router.replace(segments.join('/') || `/${locale}`)
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" aria-label={dict.layout.language}>
          <IconLanguage className="size-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {locales.map((locale) => (
          <DropdownMenuItem key={locale} onClick={() => change(locale)}>
            {dict.layout.locales[locale]}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
