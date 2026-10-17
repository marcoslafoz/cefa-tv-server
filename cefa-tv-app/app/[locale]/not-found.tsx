'use client'

import Link from 'next/link'
import { Button } from '@/components/shadcn-ui/button'
import { useDictionary } from '@/contexts/dictionary-context'
import { useLocalePath } from '@/hooks/use-locale-path'

export default function NotFound() {
  const dict = useDictionary()
  const localePath = useLocalePath()
  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-4 p-6 text-center">
      <p className="text-muted-foreground text-sm font-medium">404</p>
      <h1 className="text-2xl font-semibold">{dict.errors.notFoundTitle}</h1>
      <p className="text-muted-foreground max-w-sm text-sm">{dict.errors.notFoundDescription}</p>
      <Button asChild>
        <Link href={localePath('/dashboard')}>{dict.errors.backHome}</Link>
      </Button>
    </div>
  )
}
