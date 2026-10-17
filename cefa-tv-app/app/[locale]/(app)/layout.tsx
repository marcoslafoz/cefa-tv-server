'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { AppSidebar } from '@/components/layout/app-sidebar'
import { LocaleSwitcher } from '@/components/layout/locale-switcher'
import { ThemeToggle } from '@/components/layout/theme-toggle'
import { PageSkeleton } from '@/components/shared/state-views'
import { Separator } from '@/components/shadcn-ui/separator'
import { SidebarInset, SidebarProvider, SidebarTrigger } from '@/components/shadcn-ui/sidebar'
import { TooltipProvider } from '@/components/shadcn-ui/tooltip'
import { useAuth } from '@/contexts/auth-context'
import { useLocalePath } from '@/hooks/use-locale-path'

export default function AppLayout({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth()
  const router = useRouter()
  const localePath = useLocalePath()

  useEffect(() => {
    if (!loading && !user) router.replace(localePath('/login'))
  }, [loading, user, router, localePath])

  if (loading || !user) {
    return (
      <div className="p-6">
        <PageSkeleton />
      </div>
    )
  }

  return (
    <TooltipProvider>
      <SidebarProvider>
        <AppSidebar />
        <SidebarInset>
          <header className="bg-background/80 sticky top-0 z-20 flex h-14 items-center gap-2 border-b px-4 backdrop-blur">
            <SidebarTrigger />
            <Separator orientation="vertical" className="h-5" />
            <div className="ml-auto flex items-center gap-1">
              <LocaleSwitcher />
              <ThemeToggle />
            </div>
          </header>
          <main className="min-w-0 flex-1 p-4 md:p-6">{children}</main>
        </SidebarInset>
      </SidebarProvider>
    </TooltipProvider>
  )
}
