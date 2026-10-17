'use client'

import { useState } from 'react'
import { mediaThumbnailUrl } from '@/lib/api-client'
import { MEDIA_ICONS, MEDIA_TONES } from '@/lib/media'
import { cn } from '@/lib/utils'
import type { MediaType } from '@/types/api'

export function MediaThumb({
  id,
  type,
  className,
  iconClassName,
}: {
  id: string
  type: MediaType
  className?: string
  iconClassName?: string
}) {
  const [failed, setFailed] = useState(false)
  const IconComponent = MEDIA_ICONS[type]
  const withThumbnail = type !== 'WEB' && type !== 'HTML' && !failed

  return (
    <div
      className={cn(
        'relative flex items-center justify-center overflow-hidden rounded-md',
        MEDIA_TONES[type],
        className
      )}
    >
      {withThumbnail ? (
        <img
          src={mediaThumbnailUrl(id)}
          alt=""
          loading="lazy"
          draggable={false}
          className="absolute inset-0 size-full object-cover"
          onError={() => setFailed(true)}
        />
      ) : (
        <IconComponent className={cn('size-6', iconClassName)} />
      )}
    </div>
  )
}
