import { cn } from '@/lib/utils'
import { DEFAULT_GROUP_ICON, GROUP_ICONS } from '@/components/shared/icon-registry'

export const GROUP_COLORS = [
  'slate',
  'blue',
  'green',
  'amber',
  'red',
  'violet',
  'pink',
  'teal',
] as const
export type GroupColor = (typeof GROUP_COLORS)[number]

export const GROUP_COLOR_CLASSES: Record<GroupColor, { badge: string; swatch: string }> = {
  slate: { badge: 'bg-slate-500/15 text-slate-600 dark:text-slate-300', swatch: 'bg-slate-500' },
  blue: { badge: 'bg-blue-500/15 text-blue-600 dark:text-blue-300', swatch: 'bg-blue-500' },
  green: { badge: 'bg-green-500/15 text-green-600 dark:text-green-300', swatch: 'bg-green-500' },
  amber: { badge: 'bg-amber-500/15 text-amber-600 dark:text-amber-300', swatch: 'bg-amber-500' },
  red: { badge: 'bg-red-500/15 text-red-600 dark:text-red-300', swatch: 'bg-red-500' },
  violet: {
    badge: 'bg-violet-500/15 text-violet-600 dark:text-violet-300',
    swatch: 'bg-violet-500',
  },
  pink: { badge: 'bg-pink-500/15 text-pink-600 dark:text-pink-300', swatch: 'bg-pink-500' },
  teal: { badge: 'bg-teal-500/15 text-teal-600 dark:text-teal-300', swatch: 'bg-teal-500' },
}

export function normalizeGroupColor(color?: string | null): GroupColor {
  return (GROUP_COLORS as readonly string[]).includes(color ?? '') ? (color as GroupColor) : 'slate'
}

export function DynamicIcon({ name, className }: { name?: string | null; className?: string }) {
  const Icon = GROUP_ICONS[name ?? ''] ?? GROUP_ICONS[DEFAULT_GROUP_ICON]
  return <Icon className={className} aria-hidden />
}

export function GroupBadge({
  icon,
  color,
  className,
  iconClassName,
}: {
  icon?: string | null
  color?: string | null
  className?: string
  iconClassName?: string
}) {
  return (
    <span
      className={cn(
        'flex size-9 shrink-0 items-center justify-center rounded-lg',
        GROUP_COLOR_CLASSES[normalizeGroupColor(color)].badge,
        className
      )}
    >
      <DynamicIcon name={icon} className={cn('size-4', iconClassName)} />
    </span>
  )
}
