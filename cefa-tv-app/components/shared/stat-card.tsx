import type { Icon } from '@tabler/icons-react'
import { Card, CardContent } from '@/components/shadcn-ui/card'
import { cn } from '@/lib/utils'

export function StatCard({
  label,
  value,
  hint,
  icon: IconComponent,
  tone,
}: {
  label: string
  value: React.ReactNode
  hint?: string
  icon: Icon
  tone?: string
}) {
  return (
    <Card>
      <CardContent className="flex items-start justify-between gap-3">
        <div className="min-w-0 space-y-1">
          <p className="text-muted-foreground text-sm">{label}</p>
          <p className="text-3xl font-semibold tracking-tight tabular-nums">{value}</p>
          {hint ? <p className="text-muted-foreground text-xs">{hint}</p> : null}
        </div>
        <span
          className={cn(
            'bg-primary/10 text-primary flex size-10 shrink-0 items-center justify-center rounded-lg',
            tone
          )}
        >
          <IconComponent className="size-5" />
        </span>
      </CardContent>
    </Card>
  )
}
