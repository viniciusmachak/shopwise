import type { ReactNode, SVGProps } from 'react'

type IconProps = SVGProps<SVGSVGElement> & { size?: number }

function Svg({ size = 20, children, ...rest }: IconProps & { children: ReactNode }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      {...rest}
    >
      {children}
    </svg>
  )
}

export const SearchIcon = (p: IconProps) => (
  <Svg {...p}>
    <circle cx="11" cy="11" r="7" />
    <path d="m20 20-3.5-3.5" />
  </Svg>
)

export const CartIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M3 4h2l2.4 11.2a1.5 1.5 0 0 0 1.5 1.2h8.6a1.5 1.5 0 0 0 1.5-1.1L21 8H6.2" />
    <circle cx="9.5" cy="20" r="1.2" />
    <circle cx="17.5" cy="20" r="1.2" />
  </Svg>
)

export const UserIcon = (p: IconProps) => (
  <Svg {...p}>
    <circle cx="12" cy="8" r="4" />
    <path d="M4 21c1.2-4 4.3-6 8-6s6.8 2 8 6" />
  </Svg>
)

export const ChatIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M4 5h16v11H9l-5 4z" />
    <path d="M8 10h8M8 13h5" />
  </Svg>
)

export const MicIcon = (p: IconProps) => (
  <Svg {...p}>
    <rect x="9" y="3" width="6" height="11" rx="3" />
    <path d="M5 11a7 7 0 0 0 14 0M12 18v3" />
  </Svg>
)

export const TruckIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M3 6h11v10H3zM14 9h4l3 3v4h-7" />
    <circle cx="7" cy="18" r="1.8" />
    <circle cx="17" cy="18" r="1.8" />
  </Svg>
)

export const TagIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M3 12V4h8l10 10-8 8z" />
    <circle cx="7.5" cy="8.5" r="1.3" />
  </Svg>
)

export const BoxIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="m3 7 9-4 9 4v10l-9 4-9-4z" />
    <path d="m3 7 9 4 9-4M12 11v10" />
  </Svg>
)

export const GridIcon = (p: IconProps) => (
  <Svg {...p}>
    <rect x="4" y="4" width="7" height="7" rx="1.5" />
    <rect x="13" y="4" width="7" height="7" rx="1.5" />
    <rect x="4" y="13" width="7" height="7" rx="1.5" />
    <rect x="13" y="13" width="7" height="7" rx="1.5" />
  </Svg>
)

export const ChevronDown = (p: IconProps) => (
  <Svg {...p}>
    <path d="m6 9 6 6 6-6" />
  </Svg>
)

export const ChevronRight = (p: IconProps) => (
  <Svg {...p}>
    <path d="m9 6 6 6-6 6" />
  </Svg>
)

/** Line drawings that stand in for product photos, picked by category. */
export function CategoryArt({ category, size = 72 }: { category: string; size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 64 64"
      fill="none"
      stroke="currentColor"
      strokeWidth={2.2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {art[categoryKey(category)] ?? art.default}
    </svg>
  )
}

export function categoryKey(category: string): string {
  return category
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
}

const art: Record<string, ReactNode> = {
  'running-shoes': (
    <>
      <path d="M6 40c0-6 2-12 6-16l6 4c4 3 9 3 12 0l4-4c6 6 14 9 22 10 2 0 3 2 3 4v4H6z" />
      <path d="M6 42v4h52v-4M22 30l3-3M27 32l3-3M32 33l2-3" />
    </>
  ),
  'hiking-boots': (
    <>
      <path d="M16 10h16v18c6 2 16 4 20 8 2 2 3 4 3 7v3H9V18c0-4 3-8 7-8z" />
      <path d="M9 46v6h46v-6M20 18h8M20 24h8M14 52v-6M24 52v-6M34 52v-6M44 52v-6" />
    </>
  ),
  jackets: (
    <>
      <path d="M24 8h16l12 8 6 18-8 3-4-10v29H18V27l-4 10-8-3 6-18z" />
      <path d="M24 8c2 5 5 8 8 8s6-3 8-8M32 16v40M27 30h-4M41 30h-4" />
    </>
  ),
  backpacks: (
    <>
      <path d="M22 14c0-5 4-8 10-8s10 3 10 8" />
      <rect x="14" y="14" width="36" height="44" rx="10" />
      <path d="M22 36h20v12H22zM22 42h20M26 22h12" />
    </>
  ),
  't-shirts': (
    <>
      <path d="M22 8h20l14 10-6 10-6-4v32H20V24l-6 4-6-10z" />
      <path d="M26 8c1 4 3 6 6 6s5-2 6-6" />
    </>
  ),
  default: (
    <>
      <path d="M12 22h40l-4 34H16z" />
      <path d="M24 22v-6a8 8 0 0 1 16 0v6" />
    </>
  ),
}
