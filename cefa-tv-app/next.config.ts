import type { NextConfig } from 'next'

const nextConfig: NextConfig = {
  output: 'standalone',
  images: { unoptimized: true },
  async rewrites() {
    return [
      {
        source: '/core-api/:path*',
        destination: `${process.env.CORE_API_URL || 'http://localhost:8080'}/:path*`,
      },
    ]
  },
}

export default nextConfig
