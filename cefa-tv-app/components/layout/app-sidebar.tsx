'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import {
  IconBuildingStore,
  IconCalendarTime,
  IconDeviceTv,
  IconHistory,
  IconLayoutDashboard,
  IconLayoutGrid,
  IconLogout,
  IconMovie,
  IconPlaylist,
  IconSettings,
  IconUsers,
  IconUsersGroup,
  type Icon,
} from '@tabler/icons-react'
import { BrandLogo } from '@/components/shared/brand-logo'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from '@/components/shadcn-ui/sidebar'
import { useAuth } from '@/contexts/auth-context'
import { useDictionary } from '@/contexts/dictionary-context'
import { useLocalePath } from '@/hooks/use-locale-path'
import { hasPermission } from '@/lib/permissions'

interface NavItem {
  href: string
  label: string
  icon: Icon
  visible: boolean
}

export function AppSidebar() {
  const dict = useDictionary()
  const pathname = usePathname()
  const localePath = useLocalePath()
  const { user, logout } = useAuth()
  const can = (permission: Parameters<typeof hasPermission>[1]) => hasPermission(user, permission)

  const content: NavItem[] = [
    { href: '/dashboard', label: dict.nav.dashboard, icon: IconLayoutDashboard, visible: true },
    { href: '/media', label: dict.nav.media, icon: IconMovie, visible: can('MEDIA_MANAGE') },
    {
      href: '/playlists',
      label: dict.nav.playlists,
      icon: IconPlaylist,
      visible: can('PLAYLIST_MANAGE'),
    },
    {
      href: '/schedule',
      label: dict.nav.schedule,
      icon: IconCalendarTime,
      visible: can('SCHEDULE_MANAGE'),
    },
  ]
  const network: NavItem[] = [
    { href: '/players', label: dict.nav.players, icon: IconDeviceTv, visible: can('PLAYER_VIEW') },
    { href: '/groups', label: dict.nav.groups, icon: IconLayoutGrid, visible: can('PLAYER_VIEW') },
    { href: '/sites', label: dict.nav.sites, icon: IconBuildingStore, visible: can('PLAYER_VIEW') },
  ]
  const administration: NavItem[] = [
    { href: '/users', label: dict.nav.users, icon: IconUsers, visible: can('USER_MANAGE') },
    {
      href: '/user-groups',
      label: dict.nav.userGroups,
      icon: IconUsersGroup,
      visible: can('USER_MANAGE'),
    },
    { href: '/audit', label: dict.nav.audit, icon: IconHistory, visible: can('AUDIT_VIEW') },
  ]
  const system: NavItem[] = [
    { href: '/settings', label: dict.nav.settings, icon: IconSettings, visible: true },
  ]

  function renderGroup(label: string, items: NavItem[]) {
    const visible = items.filter((item) => item.visible)
    if (visible.length === 0) return null
    return (
      <SidebarGroup>
        <SidebarGroupLabel>{label}</SidebarGroupLabel>
        <SidebarGroupContent>
          <SidebarMenu>
            {visible.map((item) => {
              const href = localePath(item.href)
              const active = pathname === href || pathname.startsWith(`${href}/`)
              return (
                <SidebarMenuItem key={item.href}>
                  <SidebarMenuButton asChild isActive={active} tooltip={item.label}>
                    <Link href={href}>
                      <item.icon />
                      <span>{item.label}</span>
                    </Link>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              )
            })}
          </SidebarMenu>
        </SidebarGroupContent>
      </SidebarGroup>
    )
  }

  return (
    <Sidebar collapsible="icon">
      <SidebarHeader className="h-14 justify-center border-b px-4">
        <Link href={localePath('/dashboard')}>
          <BrandLogo />
        </Link>
      </SidebarHeader>
      <SidebarContent>
        {renderGroup(dict.nav.groupContent, content)}
        {renderGroup(dict.nav.groupNetwork, network)}
        {renderGroup(dict.nav.groupAdmin, administration)}
        {renderGroup(dict.nav.groupSystem, system)}
      </SidebarContent>
      <SidebarFooter className="border-t">
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton onClick={() => void logout()} tooltip={dict.auth.logout}>
              <IconLogout />
              <span className="truncate">
                {dict.auth.logout}
                {user ? ` · ${user.username}` : ''}
              </span>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarFooter>
    </Sidebar>
  )
}
