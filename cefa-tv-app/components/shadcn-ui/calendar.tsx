'use client'

import * as React from 'react'
import { DayPicker } from 'react-day-picker'
import { IconChevronLeft, IconChevronRight } from '@tabler/icons-react'

import { buttonVariants } from '@/components/shadcn-ui/button'
import { cn } from '@/lib/utils'

function Calendar({
  className,
  classNames,
  showOutsideDays = true,
  ...props
}: React.ComponentProps<typeof DayPicker>) {
  return (
    <DayPicker
      showOutsideDays={showOutsideDays}
      weekStartsOn={1}
      className={cn('p-3', className)}
      classNames={{
        root: 'w-fit',
        months: 'relative flex flex-col gap-4 sm:flex-row',
        month: 'flex w-full flex-col gap-3',
        nav: 'absolute inset-x-0 top-0 flex items-center justify-between',
        button_previous: cn(buttonVariants({ variant: 'ghost', size: 'icon-sm' }), 'size-7'),
        button_next: cn(buttonVariants({ variant: 'ghost', size: 'icon-sm' }), 'size-7'),
        month_caption: 'flex h-7 items-center justify-center',
        caption_label: 'text-sm font-medium capitalize',
        month_grid: 'w-full border-collapse',
        weekdays: 'flex',
        weekday: 'text-muted-foreground w-9 text-center text-[0.8rem] font-normal capitalize',
        week: 'mt-1 flex w-full',
        day: 'relative size-9 p-0 text-center text-sm',
        day_button: cn(
          buttonVariants({ variant: 'ghost' }),
          'size-9 p-0 font-normal tabular-nums aria-selected:opacity-100'
        ),
        selected:
          '[&>button]:bg-primary [&>button]:text-primary-foreground [&>button]:hover:bg-primary',
        range_start: 'bg-accent rounded-l-md',
        range_end: 'bg-accent rounded-r-md',
        range_middle: 'bg-accent [&>button]:bg-transparent [&>button]:text-accent-foreground',
        today: '[&>button]:border [&>button]:border-primary/60',
        outside: 'text-muted-foreground opacity-50',
        disabled: 'text-muted-foreground opacity-40',
        hidden: 'invisible',
        ...classNames,
      }}
      components={{
        Chevron: ({ orientation, className: iconClass }) =>
          orientation === 'left' ? (
            <IconChevronLeft className={cn('size-4', iconClass)} />
          ) : (
            <IconChevronRight className={cn('size-4', iconClass)} />
          ),
      }}
      {...props}
    />
  )
}

export { Calendar }
