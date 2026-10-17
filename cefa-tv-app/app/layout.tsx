import type { Metadata } from 'next'

import { inter } from '@/app/fonts'
import '@/app/globals.css'
import { ThemeProvider } from '@/components/providers/theme-provider'
import { Toaster } from '@/components/shadcn-ui/sonner'

export const metadata: Metadata = {
  title: {
    default: 'CEFA TV',
    template: '%s · CEFA TV',
  },
  description: 'CEFA TV — digital signage',
  icons: { icon: [{ url: '/favicon.svg', type: 'image/svg+xml' }] },
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="es" suppressHydrationWarning className={inter.variable}>
      <body className="min-h-full antialiased">
        <ThemeProvider
          attribute="class"
          defaultTheme="system"
          enableSystem
          disableTransitionOnChange
        >
          {children}
          <Toaster />
        </ThemeProvider>
      </body>
    </html>
  )
}
