'use client'

import { IconAlertTriangle, type Icon } from '@tabler/icons-react'
import { Button } from '@/components/shadcn-ui/button'
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from '@/components/shadcn-ui/empty'
import { Skeleton } from '@/components/shadcn-ui/skeleton'
import { useDictionary } from '@/contexts/dictionary-context'

export function EmptyState({
  icon: IconComponent,
  title,
  description,
  action,
}: {
  icon: Icon
  title: string
  description?: string
  action?: React.ReactNode
}) {
  return (
    <Empty className="border border-dashed">
      <EmptyHeader>
        <EmptyMedia variant="icon">
          <IconComponent />
        </EmptyMedia>
        <EmptyTitle>{title}</EmptyTitle>
        {description ? <EmptyDescription>{description}</EmptyDescription> : null}
      </EmptyHeader>
      {action ? <EmptyContent>{action}</EmptyContent> : null}
    </Empty>
  )
}

export function ErrorState({ onRetry }: { onRetry?: () => void }) {
  const dict = useDictionary()
  return (
    <Empty className="border border-dashed">
      <EmptyHeader>
        <EmptyMedia variant="icon">
          <IconAlertTriangle />
        </EmptyMedia>
        <EmptyTitle>{dict.errors.loadTitle}</EmptyTitle>
        <EmptyDescription>{dict.errors.loadDescription}</EmptyDescription>
      </EmptyHeader>
      {onRetry ? (
        <EmptyContent>
          <Button variant="outline" onClick={onRetry}>
            {dict.common.retry}
          </Button>
        </EmptyContent>
      ) : null}
    </Empty>
  )
}

export function PageSkeleton({ rows = 4 }: { rows?: number }) {
  return (
    <div className="space-y-4" aria-busy="true">
      <Skeleton className="h-8 w-56" />
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {Array.from({ length: rows }).map((_, index) => (
          <Skeleton key={index} className="h-28 rounded-xl" />
        ))}
      </div>
      <Skeleton className="h-64 rounded-xl" />
    </div>
  )
}
