'use client'

import { useId, useMemo, useState } from 'react'

export interface ChartPoint {
  at: number
  value: number | null
}

const WIDTH = 600
const HEIGHT = 140
const PADDING_TOP = 8
const PADDING_BOTTOM = 8

export function LineChart({
  title,
  points,
  domain,
  format,
  formatTime,
  emptyLabel,
}: {
  title: string
  points: ChartPoint[]
  domain?: [number, number]
  format: (value: number) => string
  formatTime: (at: number) => string
  emptyLabel: string
}) {
  const gradientId = useId()
  const [hover, setHover] = useState<number | null>(null)

  const chart = useMemo(() => {
    const valid = points.filter((point) => point.value !== null) as Array<{
      at: number
      value: number
    }>
    if (valid.length === 0) return null
    const values = valid.map((point) => point.value)
    let min = domain ? domain[0] : Math.min(...values)
    let max = domain ? domain[1] : Math.max(...values)
    if (max - min < 1e-6) {
      min -= 1
      max += 1
    }
    const start = points[0].at
    const end = points[points.length - 1].at
    const span = Math.max(end - start, 1)
    const plotHeight = HEIGHT - PADDING_TOP - PADDING_BOTTOM
    const project = (point: { at: number; value: number }) => ({
      x: ((point.at - start) / span) * WIDTH,
      y: PADDING_TOP + (1 - (point.value - min) / (max - min)) * plotHeight,
    })
    const segments: string[] = []
    let current: string[] = []
    for (const point of points) {
      if (point.value === null) {
        if (current.length) segments.push(current.join(' '))
        current = []
        continue
      }
      const { x, y } = project({ at: point.at, value: point.value })
      current.push(`${current.length ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`)
    }
    if (current.length) segments.push(current.join(' '))
    const last = valid[valid.length - 1]
    const first = valid[0]
    const area =
      segments.length === 1
        ? `${segments[0]} L${project(last).x.toFixed(1)} ${HEIGHT} L${project(first).x.toFixed(1)} ${HEIGHT} Z`
        : null
    return { valid, min, max, start, end, span, project, segments, area, last }
  }, [points, domain])

  if (!chart) {
    return (
      <figure className="space-y-2">
        <figcaption className="text-sm font-medium">{title}</figcaption>
        <div className="text-muted-foreground flex h-[140px] items-center justify-center rounded-lg border border-dashed text-sm">
          {emptyLabel}
        </div>
      </figure>
    )
  }

  const hovered = hover === null ? null : chart.valid[hover]
  const hoveredPosition = hovered ? chart.project(hovered) : null

  function onMove(event: React.PointerEvent<HTMLDivElement>) {
    if (!chart) return
    const rect = event.currentTarget.getBoundingClientRect()
    const ratio = Math.min(Math.max((event.clientX - rect.left) / rect.width, 0), 1)
    const target = chart.start + ratio * chart.span
    let best = 0
    let bestDistance = Infinity
    chart.valid.forEach((point, index) => {
      const distance = Math.abs(point.at - target)
      if (distance < bestDistance) {
        best = index
        bestDistance = distance
      }
    })
    setHover(best)
  }

  return (
    <figure className="space-y-2">
      <figcaption className="flex items-baseline justify-between gap-2 text-sm">
        <span className="font-medium">{title}</span>
        <span className="text-muted-foreground tabular-nums">{format(chart.last.value)}</span>
      </figcaption>
      <div>
        <div className="relative">
          <div className="text-muted-foreground pointer-events-none absolute top-0 left-0 text-[10px] tabular-nums">
            {format(chart.max)}
          </div>
          <div className="text-muted-foreground pointer-events-none absolute bottom-0 left-0 text-[10px] tabular-nums">
            {format(chart.min)}
          </div>
          <div
            className="relative ml-12 touch-none"
            onPointerMove={onMove}
            onPointerLeave={() => setHover(null)}
          >
            <svg
              viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
              preserveAspectRatio="none"
              className="block h-[140px] w-full"
              role="img"
              aria-label={title}
            >
              <defs>
                <linearGradient id={gradientId} x1="0" x2="0" y1="0" y2="1">
                  <stop offset="0%" stopColor="var(--chart-1)" stopOpacity="0.18" />
                  <stop offset="100%" stopColor="var(--chart-1)" stopOpacity="0" />
                </linearGradient>
              </defs>
              {[0, 0.5, 1].map((ratio) => (
                <line
                  key={ratio}
                  x1={0}
                  x2={WIDTH}
                  y1={PADDING_TOP + ratio * (HEIGHT - PADDING_TOP - PADDING_BOTTOM)}
                  y2={PADDING_TOP + ratio * (HEIGHT - PADDING_TOP - PADDING_BOTTOM)}
                  stroke="var(--border)"
                  strokeWidth={1}
                  vectorEffect="non-scaling-stroke"
                />
              ))}
              {chart.area ? (
                <path d={chart.area} fill={`url(#${gradientId})`} stroke="none" />
              ) : null}
              {chart.segments.map((segment) => (
                <path
                  key={`line-${segment}`}
                  d={segment}
                  fill="none"
                  stroke="var(--chart-1)"
                  strokeWidth={2}
                  strokeLinejoin="round"
                  strokeLinecap="round"
                  vectorEffect="non-scaling-stroke"
                />
              ))}
            </svg>
            {hovered && hoveredPosition ? (
              <>
                <div
                  className="bg-muted-foreground/40 pointer-events-none absolute top-0 bottom-0 w-px"
                  style={{ left: `${(hoveredPosition.x / WIDTH) * 100}%` }}
                />
                <div
                  className="border-card bg-primary pointer-events-none absolute size-2.5 -translate-x-1/2 -translate-y-1/2 rounded-full border-2"
                  style={{
                    left: `${(hoveredPosition.x / WIDTH) * 100}%`,
                    top: `${(hoveredPosition.y / HEIGHT) * 100}%`,
                  }}
                />
                <div
                  className="bg-popover text-popover-foreground pointer-events-none absolute z-10 rounded-md border px-2 py-1 text-xs shadow-md"
                  style={{
                    left: `${(hoveredPosition.x / WIDTH) * 100}%`,
                    top: 0,
                    transform: `translateX(${hoveredPosition.x > WIDTH * 0.7 ? '-105%' : '8px'})`,
                  }}
                >
                  <p className="font-medium tabular-nums">{format(hovered.value)}</p>
                  <p className="text-muted-foreground tabular-nums">{formatTime(hovered.at)}</p>
                </div>
              </>
            ) : null}
          </div>
        </div>
        <div className="text-muted-foreground mt-1 ml-12 flex justify-between text-[10px] tabular-nums">
          <span>{formatTime(chart.start)}</span>
          <span>{formatTime(chart.end)}</span>
        </div>
      </div>
    </figure>
  )
}
