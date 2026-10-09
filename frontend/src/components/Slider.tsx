import { Children, useCallback, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { useT } from '../i18n/useT'
import { Icon } from './Icon'
import './Slider.css'

type SliderProps = {
  /** The section's heading element, shown at the start of the header row. */
  heading: ReactNode
  /** Anything else for the header, such as a link to the full page; it sits before the slider's own buttons. */
  actions?: ReactNode
  /** The accessible name of the slider. */
  label: string
  /** How wide one item wants to be (px); the number of items per page follows from the room there is. */
  minItemWidth: number
  /** The space between items (px). */
  gap?: number
  /** Read out to screen readers as the page changes, e.g. "Showing 1 to 3 of 8". */
  rangeLabel: (from: number, to: number, total: number) => string
  children: ReactNode
}

/**
 * A row of items shown a page at a time. How many fit on a page follows from the width the slider has (a phone
 * shows one meal, a wide screen four), the page is a snapped stop of a native scroll track so a touch swipe works
 * as well as the buttons, and "View more" moves on to the next items (the arrow beside it goes back). When
 * everything fits on one page the buttons are not shown. Works left to right and right to left.
 */
export function Slider({ heading, actions, label, minItemWidth, gap = 16, rangeLabel, children }: SliderProps) {
  const { t } = useT()
  const items = Children.toArray(children)
  const count = items.length
  const track = useRef<HTMLDivElement>(null)
  const [perPage, setPerPage] = useState(1)
  const [first, setFirst] = useState(0)

  const step = useCallback(() => {
    const element = track.current?.firstElementChild as HTMLElement | null
    return element ? element.getBoundingClientRect().width + gap : 0
  }, [gap])

  // How many items fit: as many minimum-width items (and the gaps between them) as the track is wide.
  useEffect(() => {
    const element = track.current
    if (!element) return
    const measure = () => {
      const width = element.clientWidth
      setPerPage(Math.max(1, Math.floor((width + gap) / (minItemWidth + gap))))
    }
    measure()
    const observer = new ResizeObserver(measure)
    observer.observe(element)
    return () => observer.disconnect()
  }, [minItemWidth, gap])

  // Which item is at the start of the view, from the scroll position (negative in right-to-left, so use its size).
  const updateFirst = useCallback(() => {
    const element = track.current
    const size = step()
    if (!element || size === 0) return
    setFirst(Math.round(Math.abs(element.scrollLeft) / size))
  }, [step])

  useEffect(() => {
    updateFirst()
  }, [perPage, count, updateFirst])

  const scrollByPage = (direction: 1 | -1) => {
    const element = track.current
    if (!element) return
    const rtl = getComputedStyle(element).direction === 'rtl'
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    element.scrollBy({ left: direction * (rtl ? -1 : 1) * step() * perPage, behavior: reduce ? 'auto' : 'smooth' })
  }

  const last = Math.min(count, first + perPage)
  const paged = count > perPage

  return (
    <section className="slider" aria-roledescription="carousel" aria-label={label}>
      <header className="slider__header">
        {heading}
        <div className="slider__controls">
          {actions}
          {paged && (
            <>
              <button
                type="button"
                className="button button--icon button--small slider__previous"
                aria-label={t('common.slider.previous')}
                disabled={first <= 0}
                onClick={() => scrollByPage(-1)}
              >
                <Icon name="chevron" size={16} />
              </button>
              <button
                type="button"
                className="button button--secondary button--small slider__more"
                disabled={last >= count}
                onClick={() => scrollByPage(1)}
              >
                {t('common.slider.viewMore')}
                <Icon name="chevron" size={16} />
              </button>
            </>
          )}
        </div>
      </header>
      <div
        ref={track}
        className="slider__track"
        style={{ '--per-page': perPage, '--slider-gap': `${gap}px` } as React.CSSProperties}
        onScroll={updateFirst}
      >
        {items.map((item, index) => (
          <div className="slider__item" key={index}>
            {item}
          </div>
        ))}
      </div>
      <p className="visually-hidden" aria-live="polite">
        {count > 0 ? rangeLabel(first + 1, last, count) : ''}
      </p>
    </section>
  )
}
