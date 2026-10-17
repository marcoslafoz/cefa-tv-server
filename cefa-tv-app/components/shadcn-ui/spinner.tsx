'use client'

import { cn } from '@/lib/utils'
import { IconLoader } from '@tabler/icons-react'
import { useOptionalDictionary } from '@/contexts/dictionary-context'

function Spinner({ className, ...props }: React.ComponentProps<'svg'>) {
  const dict = useOptionalDictionary()
  return (
    <IconLoader
      data-slot="spinner"
      role="status"
      aria-label={dict?.common.loading ?? 'Cargando...'}
      className={cn('size-4 animate-spin', className)}
      {...props}
    />
  )
}

export { Spinner }
