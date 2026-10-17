'use client'

import { Checkbox } from '@/components/shadcn-ui/checkbox'
import { useDictionary } from '@/contexts/dictionary-context'
import { PERMISSION_GROUPS } from '@/lib/permissions'
import type { Permission } from '@/types/api'

export function PermissionChecklist({
  value,
  onChange,
  disabled,
}: {
  value: Permission[]
  onChange: (permissions: Permission[]) => void
  disabled?: boolean
}) {
  const dict = useDictionary()

  function toggle(permission: Permission) {
    onChange(
      value.includes(permission)
        ? value.filter((entry) => entry !== permission)
        : [...value, permission]
    )
  }

  return (
    <div className="space-y-4">
      {PERMISSION_GROUPS.map((group) => (
        <div key={group.id} className="space-y-1.5">
          <p className="text-muted-foreground text-xs font-medium tracking-wide uppercase">
            {dict.access.permissionGroups[group.id]}
          </p>
          <div className="divide-y rounded-lg border">
            {group.items.map((permission) => (
              <label
                key={permission}
                className="hover:bg-muted/50 flex cursor-pointer items-start gap-3 p-3"
              >
                <Checkbox
                  className="mt-0.5"
                  checked={value.includes(permission)}
                  disabled={disabled}
                  onCheckedChange={() => toggle(permission)}
                />
                <span className="min-w-0">
                  <span className="block text-sm font-medium">
                    {dict.access.permissions[permission].label}
                  </span>
                  <span className="text-muted-foreground block text-xs">
                    {dict.access.permissions[permission].description}
                  </span>
                </span>
              </label>
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}
