'use client'

import { useRef, useState } from 'react'
import { IconVolume, IconVolumeOff } from '@tabler/icons-react'
import { Button } from '@/components/shadcn-ui/button'
import { Slider } from '@/components/shadcn-ui/slider'
import { useDictionary } from '@/contexts/dictionary-context'
import { api } from '@/lib/api'
import { apiErrorMessage } from '@/lib/api-client'
import { toast } from '@/lib/toast'
import type { Player } from '@/types/api'

const DEFAULT_UNMUTE_VOLUME = 50

interface VolumeControlProps {
  playerId: string
  volume: number
  disabled: boolean
  onVolumeChange: (volume: number) => void
  onPlayerUpdated: (player: Player) => void
}

export function VolumeControl({
  playerId,
  volume,
  disabled,
  onVolumeChange,
  onPlayerUpdated,
}: VolumeControlProps) {
  const dict = useDictionary()
  const [draft, setDraft] = useState<number | null>(null)
  const lastAudibleVolume = useRef(volume > 0 ? volume : DEFAULT_UNMUTE_VOLUME)
  const shown = draft ?? volume
  const muted = shown === 0

  async function apply(next: number) {
    const previous = volume
    if (next > 0) lastAudibleVolume.current = next
    onVolumeChange(next)
    setDraft(null)
    try {
      onPlayerUpdated(await api.players.update(playerId, { volume: next }))
    } catch (err) {
      onVolumeChange(previous)
      toast.error(apiErrorMessage(err, dict.players.volumeFailed))
    }
  }

  function toggleMute() {
    if (muted) void apply(lastAudibleVolume.current)
    else {
      lastAudibleVolume.current = shown
      void apply(0)
    }
  }

  const muteLabel = muted ? dict.players.volumeUnmute : dict.players.volumeMute

  return (
    <div className="flex items-center gap-2 rounded-lg border px-3 py-1.5">
      <Button
        variant="ghost"
        size="icon-sm"
        aria-label={muteLabel}
        title={muteLabel}
        disabled={disabled}
        onClick={toggleMute}
      >
        {muted ? <IconVolumeOff /> : <IconVolume />}
      </Button>
      <Slider
        className="w-32"
        aria-label={dict.players.volume}
        min={0}
        max={100}
        step={1}
        value={[shown]}
        disabled={disabled}
        onValueChange={([next]) => setDraft(next)}
        onValueCommit={([next]) => void apply(next)}
      />
      <span className="w-9 text-right text-sm font-medium tabular-nums">{shown}</span>
    </div>
  )
}
