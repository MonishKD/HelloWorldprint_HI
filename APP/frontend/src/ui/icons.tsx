// Small line-icon set (24px grid, stroke-based) so the UI never relies on emoji.

const PATHS = {
  bag: 'M5 8h14l-1.2 12.2a1 1 0 0 1-1 .8H7.2a1 1 0 0 1-1-.8L5 8Z M9 8V7a3 3 0 0 1 6 0v1',
  store: 'M4 9.5 5.5 4h13L20 9.5 M4 9.5h16 M4 9.5a2.7 2.7 0 0 0 5.3 0 2.7 2.7 0 0 0 5.4 0 2.7 2.7 0 0 0 5.3 0 M5.5 12.5V20h13v-7.5 M10 20v-4h4v4',
  bike: 'M5.5 20a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z M18.5 20a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z M5.5 17 9 9h5.5l4 8 M9 9 7.5 5.5H5 M14.5 9 16 6h3',
  grid: 'M4 4h6.5v6.5H4Z M13.5 4H20v6.5h-6.5Z M4 13.5h6.5V20H4Z M13.5 13.5H20V20h-6.5Z',
  search: 'm20 20-4.2-4.2 M10.5 17.5a7 7 0 1 1 0-14 7 7 0 0 1 0 14Z',
  bell: 'M6 16.5V11a6 6 0 1 1 12 0v5.5l1.5 2h-15l1.5-2Z M10 21h4',
  pot: 'M4 11h16 M5.5 11v6.5A2.5 2.5 0 0 0 8 20h8a2.5 2.5 0 0 0 2.5-2.5V11 M9 7.5c0-1.5 1.2-1.8 1.2-3.5 M13.8 7.5c0-1.5 1.2-1.8 1.2-3.5',
  box: 'M3.5 7.5 12 3.5l8.5 4L12 11.5 3.5 7.5Z M3.5 7.5v9l8.5 4 8.5-4v-9 M12 11.5v9',
  check: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z m-3.8-9.2 2.6 2.6 5-5.4',
  moon: 'M20 14.5A8.5 8.5 0 0 1 9.5 4 8.5 8.5 0 1 0 20 14.5Z',
  signal: 'M12 12.01V12 M8.5 8.5a5 5 0 0 0 0 7 M15.5 8.5a5 5 0 0 1 0 7 M5.6 5.6a9 9 0 0 0 0 12.8 M18.4 5.6a9 9 0 0 1 0 12.8',
  alert: 'M12 3.5 2.5 20h19L12 3.5Z M12 10v4.5 M12 17.2v.1',
  inbox: 'M3 13.5h5l1.5 3h5l1.5-3h5 M5.5 5h13L21 13.5V19H3v-5.5L5.5 5Z',
  sparkle: 'M12 3c.6 4.6 2.4 6.4 7 7-4.6.6-6.4 2.4-7 7-.6-4.6-2.4-6.4-7-7 4.6-.6 6.4-2.4 7-7Z M19 16c.2 1.6.9 2.3 2.5 2.5-1.6.2-2.3.9-2.5 2.5-.2-1.6-.9-2.3-2.5-2.5 1.6-.2 2.3-.9 2.5-2.5Z',
  utensils: 'M7 3v7 M4.5 3v4.5a2.5 2.5 0 0 0 5 0V3 M7 10v11 M17.5 3c-2 1.5-3 4-3 7h3v11',
  pin: 'M12 21s-6.5-5.8-6.5-11.5a6.5 6.5 0 0 1 13 0C18.5 15.2 12 21 12 21Z M12 12a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z',
  clock: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z M12 7v5l3.2 2',
  trash: 'M4.5 7h15 M9.5 7V4.5h5V7 M6.5 7l.9 12.1a1 1 0 0 0 1 .9h7.2a1 1 0 0 0 1-.9L17.5 7',
  tag: 'M3.5 12.5V4.5a1 1 0 0 1 1-1h8l8 8a1 1 0 0 1 0 1.4l-7.6 7.6a1 1 0 0 1-1.4 0l-8-8Z M8 9a1 1 0 1 0 0-2 1 1 0 0 0 0 2Z',
  star: 'M12 3.5l2.6 5.3 5.8.8-4.2 4.1 1 5.8L12 16.8l-5.2 2.7 1-5.8-4.2-4.1 5.8-.8L12 3.5Z',
  home: 'M4 11 12 4l8 7 M6 9.5V20h12V9.5 M10 20v-5h4v5',
  hourglass: 'M6.5 3.5h11 M6.5 20.5h11 M7.5 3.5c0 5 9 5 9 8.5s-9 3.5-9 8.5 M16.5 3.5c0 5-9 5-9 8.5s9 3.5 9 8.5',
} as const;

export type IconName = keyof typeof PATHS;

export function Icon({ name, size = 18, filled, className }: { name: IconName; size?: number; filled?: boolean; className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      className={`icon ${className ?? ''}`}
      fill={filled ? 'currentColor' : 'none'}
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={PATHS[name]} />
    </svg>
  );
}

/** Same icon as an SVG string, for places that need raw HTML (map markers). */
export function iconSvg(name: IconName, size = 16): string {
  return `<svg viewBox="0 0 24 24" width="${size}" height="${size}" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${PATHS[name]}"/></svg>`;
}
