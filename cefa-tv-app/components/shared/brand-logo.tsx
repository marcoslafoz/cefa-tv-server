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
      <Image
        src="/favicon.svg"
        alt="CEFA TV"
        width={32}
        height={32}
        unoptimized
        priority
        className="size-8 shrink-0"
      />
      {withText ? <span className="text-base font-semibold tracking-tight">CEFA TV</span> : null}
    </span>
  )
}
