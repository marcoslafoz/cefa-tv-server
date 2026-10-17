'use client'

import { useState } from 'react'
import { IconCheck, IconCopy } from '@tabler/icons-react'
import { Button } from '@/components/shadcn-ui/button'
import { useDictionary } from '@/contexts/dictionary-context'

export function CopyButton({ value, label }: { value: string; label?: string }) {
  const dict = useDictionary()
  const [copied, setCopied] = useState(false)

  async function copy() {
    await navigator.clipboard.writeText(value)
    setCopied(true)
    window.setTimeout(() => setCopied(false), 1500)
  }

  return (
    <Button type="button" variant="outline" size="sm" onClick={() => void copy()}>
      {copied ? <IconCheck /> : <IconCopy />}
      {copied ? dict.common.copied : (label ?? dict.common.copy)}
    </Button>
  )
}
