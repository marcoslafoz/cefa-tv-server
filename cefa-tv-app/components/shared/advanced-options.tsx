'use client'

import { IconChevronRight } from '@tabler/icons-react'
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/shadcn-ui/collapsible'

export function AdvancedOptions({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Collapsible className="group/advanced rounded-xl border p-3">
      <CollapsibleTrigger className="flex w-full items-center gap-1.5 text-left text-sm font-medium">
        <IconChevronRight className="size-4 transition-transform group-data-[state=open]/advanced:rotate-90 motion-reduce:transition-none" />
        {title}
      </CollapsibleTrigger>
      <CollapsibleContent className="mt-3">{children}</CollapsibleContent>
    </Collapsible>
  )
}
