import Image from 'next/image'
import { cn } from '@/lib/utils'

export function BrandLogo({
  className,
  withText = true,
}: {
  className?: string
  withText?: boolean
}) {
  return (
    <span className={cn('flex items-center gap-2', className)}>
      <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-neutral-900 p-1.5">
        <Image
          src="/brand/cefa-logo-white.svg"
          alt="CEFA"
          width={22}
          height={34}
          unoptimized
          priority
          className="h-full w-auto"
        />
      </span>
      {withText ? <span className="text-base font-semibold tracking-tight">CEFA TV</span> : null}
    </span>
  )
}
