'use client'

import { Button } from '@/components/shadcn-ui/button'
import { useDictionary } from '@/contexts/dictionary-context'

export default function ErrorBoundary({ reset }: { error: Error; reset: () => void }) {
  const dict = useDictionary()
  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-4 p-6 text-center">
      <h1 className="text-2xl font-semibold">{dict.errors.genericTitle}</h1>
      <p className="text-muted-foreground max-w-sm text-sm">{dict.errors.genericDescription}</p>
      <Button onClick={reset}>{dict.common.retry}</Button>
    </div>
  )
}
