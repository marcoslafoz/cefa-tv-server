'use client'

import { Badge } from '@/components/shadcn-ui/badge'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/shadcn-ui/tooltip'
import { useDictionary } from '@/contexts/dictionary-context'

export function OutdatedBadge({ latest }: { latest: string }) {
  const dict = useDictionary()
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Badge variant="outline" className="text-muted-foreground w-fit text-[10px]">
          {dict.players.versionInfo.outdated}
        </Badge>
      </TooltipTrigger>
      <TooltipContent className="max-w-xs">
        {dict.players.versionInfo.outdatedHint.replace('{version}', latest)}
      </TooltipContent>
    </Tooltip>
  )
}
