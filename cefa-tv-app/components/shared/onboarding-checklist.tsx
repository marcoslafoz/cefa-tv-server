'use client'

import Link from 'next/link'
import {
  IconArrowRight,
  IconCheck,
  IconDeviceTv,
  IconMovie,
  IconPlaylist,
  IconRocket,
  type Icon,
} from '@tabler/icons-react'
import { Button } from '@/components/shadcn-ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/shadcn-ui/card'
import { useAuth } from '@/contexts/auth-context'
import { useDictionary } from '@/contexts/dictionary-context'
import { useApiResource } from '@/hooks/use-api-resource'
import { useLocalePath } from '@/hooks/use-locale-path'
import { asList } from '@/lib/api'
import { API_V1 } from '@/lib/api-client'
import { hasPermission } from '@/lib/permissions'
import { cn } from '@/lib/utils'
import type { DashboardStats, Page, Permission, Schedule } from '@/types/api'

interface Step {
  id: 'media' | 'playlist' | 'pair' | 'publish'
  href: string
  icon: Icon
  permission: Permission
  done: boolean
}

export function OnboardingChecklist({ stats }: { stats: DashboardStats }) {
  const dict = useDictionary()
  const { user } = useAuth()
  const localePath = useLocalePath()
  const canSchedule = hasPermission(user, 'SCHEDULE_MANAGE')
  const schedules = useApiResource<Schedule[] | Page<Schedule>>(`${API_V1}/schedules`, {
    enabled: canSchedule,
  })

  const steps: Step[] = [
    {
      id: 'media',
      href: '/media',
      icon: IconMovie,
      permission: 'MEDIA_MANAGE',
      done: stats.media > 0,
    },
    {
      id: 'playlist',
      href: '/playlists',
      icon: IconPlaylist,
      permission: 'PLAYLIST_MANAGE',
      done: stats.playlists > 0,
    },
    {
      id: 'pair',
      href: '/players',
      icon: IconDeviceTv,
      permission: 'PLAYER_MANAGE',
      done: stats.players > 0,
    },
    {
      id: 'publish',
      href: '/playlists',
      icon: IconRocket,
      permission: 'SCHEDULE_MANAGE',
      done: asList(schedules.data).length > 0,
    },
  ]
  const visible = steps.filter((step) => hasPermission(user, step.permission))
  const loading = canSchedule && schedules.loading && !schedules.data
  if (loading || visible.length === 0 || visible.every((step) => step.done)) return null
  const firstPending = visible.find((step) => !step.done)
  const doneCount = visible.filter((step) => step.done).length

  return (
    <Card className="border-primary/30 bg-primary/5">
      <CardHeader>
        <CardTitle>{dict.onboarding.title}</CardTitle>
        <p className="text-muted-foreground text-sm">
          {dict.onboarding.progress
            .replace('{done}', String(doneCount))
            .replace('{total}', String(visible.length))}
        </p>
      </CardHeader>
      <CardContent>
        <ol className="grid gap-2 md:grid-cols-2 xl:grid-cols-4">
          {visible.map((step, index) => (
            <li
              key={step.id}
              className={cn(
                'bg-card flex flex-col gap-3 rounded-xl border p-4',
                step === firstPending && 'border-primary ring-primary/30 ring-2'
              )}
            >
              <div className="flex items-center gap-3">
                <span
                  className={cn(
                    'flex size-8 shrink-0 items-center justify-center rounded-full border text-sm font-semibold',
                    step.done
                      ? 'bg-success text-success-foreground border-success'
                      : 'text-muted-foreground'
                  )}
                >
                  {step.done ? <IconCheck className="size-4" /> : index + 1}
                </span>
                <span className={cn('text-sm font-medium', step.done && 'text-muted-foreground')}>
                  {dict.onboarding.steps[step.id].title}
                </span>
              </div>
              <p className="text-muted-foreground text-xs">
                {dict.onboarding.steps[step.id].description}
              </p>
              {!step.done ? (
                <Button
                  asChild
                  size="sm"
                  variant={step === firstPending ? 'default' : 'outline'}
                  className="mt-auto w-fit"
                >
                  <Link href={localePath(step.href)}>
                    <step.icon /> {dict.onboarding.steps[step.id].action} <IconArrowRight />
                  </Link>
                </Button>
              ) : null}
            </li>
          ))}
        </ol>
      </CardContent>
    </Card>
  )
}
