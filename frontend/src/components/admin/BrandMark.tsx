import { useId } from 'react'
import './BrandMark.css'

/**
 * The restaurant's mark: a coral serving cloche on an amber plate with a green leaf, drawn inline so it takes no
 * request and no tile behind it. It sits straight on the dark rail; the colours are the brand's own and do not
 * change with the theme.
 */
export function BrandMark({ size = 46 }: { size?: number }) {
  // Each mark needs its own gradient id: the rail and the phone's top bar both draw one, and one of them is hidden.
  const gradient = `brand-dome-${useId().replace(/:/g, '')}`
  return (
    <svg className="brand-mark" width={size} height={size} viewBox="0 0 48 48" aria-hidden="true" focusable="false">
      <defs>
        <linearGradient id={gradient} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#ff7a6e" />
          <stop offset="1" stopColor="#e2483c" />
        </linearGradient>
      </defs>
      <path d="M8 31a16 16 0 0 1 32 0z" fill={`url(#${gradient})`} />
      <path d="M13 25.5a11 11 0 0 1 6-7" fill="none" stroke="#ffffff" strokeOpacity="0.45" strokeWidth="2.4" strokeLinecap="round" />
      <circle cx="24" cy="12.5" r="2.6" fill="#f2b84b" />
      <rect x="5" y="32" width="38" height="4.6" rx="2.3" fill="#f2b84b" />
      <path d="M33 11c4.5-1 7.6 1.4 8 5.4-4.2.7-7.6-1.2-8-5.4z" fill="#4ccb62" />
      <path d="M34 12.4c2.2.2 4.4 1.4 6 3" fill="none" stroke="#1b7a37" strokeWidth="1" strokeLinecap="round" />
    </svg>
  )
}
