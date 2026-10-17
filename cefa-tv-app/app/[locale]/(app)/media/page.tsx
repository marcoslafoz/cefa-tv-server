'use client'

import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/shadcn-ui/tooltip'
import { NumberField } from '@/components/shared/number-field'
import { Suspense, useCallback, useEffect, useRef, useState } from 'react'
import { IconCloudUpload, IconGlobe, IconLoader2, IconMovie } from '@tabler/icons-react'
import { FolderExplorer } from '@/components/explorer/explorer'
import { useFolderParam } from '@/components/explorer/use-folder-param'
import { ConfirmDialog } from '@/components/shared/confirm-dialog'
import { MediaThumb } from '@/components/shared/media-thumb'
import { PageHeader } from '@/components/shared/page-header'
import { PageSkeleton } from '@/components/shared/state-views'
import { Badge } from '@/components/shadcn-ui/badge'
import { Button } from '@/components/shadcn-ui/button'
import { Card } from '@/components/shadcn-ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/shadcn-ui/dialog'
import { Input } from '@/components/shadcn-ui/input'
import { Label } from '@/components/shadcn-ui/label'
import { Progress } from '@/components/shadcn-ui/progress'
import { Tabs, TabsList, TabsTrigger } from '@/components/shadcn-ui/tabs'
import { useDictionary } from '@/contexts/dictionary-context'
import { api } from '@/lib/api'
import { API_V1, apiErrorMessage, uploadWithProgress } from '@/lib/api-client'
import { MEDIA_ICONS, MEDIA_TONES, MEDIA_TYPES } from '@/lib/media'
import { toast } from '@/lib/toast'
import { cn, formatBytes, formatDuration } from '@/lib/utils'
import type { MediaAsset, MediaType, Page } from '@/types/api'
import { RequirePermission } from '@/components/shared/require-permission'
import { useFormat } from '@/hooks/use-format'

function assetSummary(
  asset: MediaAsset,
  pagesLabel: string,
  fmt: ReturnType<typeof useFormat>
): string {
  if (asset.type === 'VIDEO' && asset.durationMs) return formatDuration(asset.durationMs / 1000)
  if (asset.type === 'PDF' && asset.pageCount) return fmt(pagesLabel, { count: asset.pageCount })
  return asset.sizeBytes > 0 ? formatBytes(asset.sizeBytes) : ''
}

interface UploadItem {
  key: string
  name: string
  progress: number
  error: string | null
}

function MediaPageContent() {
  const dict = useDictionary()
  const fmt = useFormat()
  const [folderId, setFolderId] = useFolderParam()
  const [type, setType] = useState<MediaType | 'ALL'>('ALL')
  const [query, setQuery] = useState('')
  const [debounced, setDebounced] = useState('')
  const [page, setPage] = useState<Page<MediaAsset> | null>(null)
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const [uploads, setUploads] = useState<UploadItem[]>([])
  const [dragging, setDragging] = useState(false)
  const [webOpen, setWebOpen] = useState(false)
  const [toDelete, setToDelete] = useState<MediaAsset[]>([])
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(query), 250)
    return () => window.clearTimeout(timer)
  }, [query])

  const load = useCallback(async () => {
    try {
      const result = await api.media.list({
        type: type === 'ALL' ? undefined : type,
        q: debounced || undefined,
        folderId,
      })
      setPage(result)
      setFailed(false)
    } catch {
      setFailed(true)
    } finally {
      setLoading(false)
    }
  }, [type, debounced, folderId])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    const processing = page?.content.some((asset) => asset.status === 'PROCESSING')
    if (!processing) return
    const timer = window.setInterval(() => void load(), 3000)
    return () => window.clearInterval(timer)
  }, [page, load])

  async function uploadFiles(files: File[]) {
    if (files.length === 0) return
    const items = files.map((file) => ({
      key: `${file.name}-${file.size}-${Math.random()}`,
      name: file.name,
      progress: 0,
      error: null as string | null,
      file,
    }))
    setUploads((current) => [
      ...current,
      ...items.map(({ key, name, progress, error }) => ({ key, name, progress, error })),
    ])
    await Promise.all(
      items.map(async (item) => {
        const form = new FormData()
        form.append('files', item.file)
        if (folderId) form.append('folderId', folderId)
        try {
          await uploadWithProgress(`${API_V1}/media`, form, (fraction) =>
            setUploads((current) =>
              current.map((entry) =>
                entry.key === item.key ? { ...entry, progress: fraction } : entry
              )
            )
          )
          setUploads((current) => current.filter((entry) => entry.key !== item.key))
        } catch (err) {
          setUploads((current) =>
            current.map((entry) =>
              entry.key === item.key
                ? { ...entry, error: apiErrorMessage(err, dict.media.uploadFailed) }
                : entry
            )
          )
        }
      })
    )
    void load()
  }

  async function remove(assets: MediaAsset[]) {
    try {
      for (const asset of assets) await api.media.remove(asset.id)
      toast.success(dict.media.deleted)
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.media.deleteFailed))
    } finally {
      void load()
    }
  }

  async function moveAssets(ids: string[], target: string | null) {
    await api.media.move(ids, target)
    await load()
  }

  async function renameAsset(asset: MediaAsset, name: string) {
    await api.media.update(asset.id, { name })
    await load()
  }

  const assets = page?.content ?? []

  return (
    <div
      className="space-y-5"
      onDragOver={(event) => {
        if (event.dataTransfer.types.includes('Files')) {
          event.preventDefault()
          setDragging(true)
        }
      }}
      onDragLeave={(event) => {
        if (event.currentTarget === event.target) setDragging(false)
      }}
      onDrop={(event) => {
        event.preventDefault()
        setDragging(false)
        void uploadFiles(Array.from(event.dataTransfer.files))
      }}
    >
      <PageHeader
        title={dict.media.title}
        description={dict.media.description}
        actions={
          <>
            <Button variant="outline" onClick={() => setWebOpen(true)}>
              <IconGlobe /> {dict.media.addWeb}
            </Button>
            <Button onClick={() => inputRef.current?.click()}>
              <IconCloudUpload /> {dict.media.upload}
            </Button>
            <Input
              ref={inputRef}
              type="file"
              multiple
              hidden
              accept="image/*,video/*,application/pdf,text/html,.html,.htm"
              onChange={(event) => {
                void uploadFiles(Array.from(event.target.files ?? []))
                event.target.value = ''
              }}
            />
          </>
        }
      />

      <div
        className={cn(
          'text-muted-foreground flex items-center justify-center gap-2 rounded-xl border-2 border-dashed px-4 py-6 text-sm transition-colors',
          dragging && 'border-primary bg-primary/5 text-primary'
        )}
      >
        <IconCloudUpload className="size-5" />
        {dict.media.dropHint}
      </div>

      {uploads.length > 0 ? (
        <Card className="divide-y p-0">
          {uploads.map((upload) => (
            <div key={upload.key} className="flex items-center gap-3 px-4 py-3">
              <span className="min-w-0 flex-1 truncate text-sm">{upload.name}</span>
              {upload.error ? (
                <>
                  <span className="text-destructive text-xs">{upload.error}</span>
                  <Button
                    size="xs"
                    variant="ghost"
                    onClick={() =>
                      setUploads((current) => current.filter((entry) => entry.key !== upload.key))
                    }
                  >
                    {dict.common.dismiss}
                  </Button>
                </>
              ) : (
                <>
                  <Progress value={upload.progress * 100} className="w-40" />
                  <span className="text-muted-foreground w-10 text-right text-xs tabular-nums">
                    {Math.round(upload.progress * 100)}%
                  </span>
                </>
              )}
            </div>
          ))}
        </Card>
      ) : null}

      <FolderExplorer
        folderId={folderId}
        onFolderChange={setFolderId}
        items={assets}
        loading={loading}
        failed={failed}
        onRetry={() => void load()}
        query={query}
        onQueryChange={setQuery}
        searchPlaceholder={dict.media.search}
        canManage
        emptyIcon={IconMovie}
        emptyTitle={dict.media.emptyTitle}
        emptyDescription={dict.media.emptyDescription}
        onOpenItem={undefined}
        onMoveItems={moveAssets}
        onRenameItem={renameAsset}
        onDeleteItems={setToDelete}
        filters={
          <Tabs value={type} onValueChange={(value) => setType(value as MediaType | 'ALL')}>
            <TabsList>
              <TabsTrigger value="ALL">{dict.media.all}</TabsTrigger>
              {MEDIA_TYPES.map((entry) => (
                <TabsTrigger key={entry} value={entry}>
                  {dict.media.types[entry]}
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>
        }
        renderThumb={(asset) => (
          <MediaThumb id={asset.id} type={asset.type} className="size-9 shrink-0 rounded-md" />
        )}
        renderMeta={(asset) => (
          <span className="flex items-center gap-3">
            <span>{dict.media.types[asset.type]}</span>
            <span className="w-16 text-right tabular-nums">
              {assetSummary(asset, dict.media.pages, fmt)}
            </span>
          </span>
        )}
        renderCard={(asset) => {
          const TypeIcon = MEDIA_ICONS[asset.type]
          return (
            <div className="group overflow-hidden rounded-xl">
              <div className="relative">
                <MediaThumb
                  id={asset.id}
                  type={asset.type}
                  className="aspect-video w-full rounded-none"
                  iconClassName="size-10"
                />
                {asset.status !== 'READY' ? (
                  <div className="absolute inset-0 flex items-center justify-center bg-black/50 text-xs font-medium text-white">
                    {asset.status === 'PROCESSING' ? (
                      <span className="flex items-center gap-1.5">
                        <IconLoader2 className="size-4 animate-spin" />
                        {dict.media.processing}
                      </span>
                    ) : (
                      dict.media.failed
                    )}
                  </div>
                ) : null}
              </div>
              <div className="space-y-1.5 p-3">
                <Tooltip>
                  <TooltipTrigger asChild>
                    <p className="truncate text-sm font-medium">{asset.name}</p>
                  </TooltipTrigger>
                  <TooltipContent>{asset.name}</TooltipContent>
                </Tooltip>
                <div className="text-muted-foreground flex items-center justify-between gap-2 text-xs">
                  <Badge variant="secondary" className={cn('gap-1', MEDIA_TONES[asset.type])}>
                    <TypeIcon />
                    {dict.media.types[asset.type]}
                  </Badge>
                  <span className="tabular-nums">{assetSummary(asset, dict.media.pages, fmt)}</span>
                </div>
              </div>
            </div>
          )
        }}
      />

      <WebDialog
        open={webOpen}
        onOpenChange={setWebOpen}
        folderId={folderId}
        onCreated={() => {
          setWebOpen(false)
          void load()
        }}
      />
      <ConfirmDialog
        open={toDelete.length > 0}
        onOpenChange={(open) => !open && setToDelete([])}
        title={dict.media.deleteTitle}
        description={dict.media.deleteDescription.replace(
          '{name}',
          toDelete.map((asset) => asset.name).join(', ')
        )}
        confirmLabel={dict.common.delete}
        onConfirm={() => {
          void remove(toDelete)
          setToDelete([])
        }}
      />
    </div>
  )
}

function WebDialog({
  open,
  onOpenChange,
  folderId,
  onCreated,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  folderId: string | null
  onCreated: () => void
}) {
  const dict = useDictionary()
  const [name, setName] = useState('')
  const [url, setUrl] = useState('')
  const [refresh, setRefresh] = useState('')
  const [saving, setSaving] = useState(false)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setSaving(true)
    try {
      await api.media.createWeb({
        name: name.trim(),
        url: url.trim(),
        refreshSec: refresh ? Number(refresh) : null,
        folderId,
      })
      toast.success(dict.media.webCreated)
      setName('')
      setUrl('')
      setRefresh('')
      onCreated()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.media.webFailed))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-md">
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>{dict.media.webTitle}</DialogTitle>
            <DialogDescription>{dict.media.webDescription}</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="web-name">{dict.common.name}</Label>
            <Input
              id="web-name"
              required
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="web-url">URL</Label>
            <Input
              id="web-url"
              type="url"
              required
              placeholder="https://"
              value={url}
              onChange={(event) => setUrl(event.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="web-refresh">{dict.media.refreshSec}</Label>
            <NumberField
              id="web-refresh"
              min={0}
              value={Number(refresh) || 0}
              onValueChange={(seconds) => setRefresh(String(seconds))}
            />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {dict.common.cancel}
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? <IconLoader2 className="animate-spin" /> : null}
              {dict.common.create}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

export default function MediaPage() {
  return (
    <RequirePermission permission="MEDIA_MANAGE">
      <Suspense fallback={<PageSkeleton rows={3} />}>
        <MediaPageContent />
      </Suspense>
    </RequirePermission>
  )
}
