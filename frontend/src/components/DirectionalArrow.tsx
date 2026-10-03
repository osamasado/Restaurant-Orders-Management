import { useLanguage } from '../i18n/language-context'
import { RTL_LANGUAGES } from '../i18n/i18n'

type DirectionalArrowProps = {
  /** "back" points to where the user came from, "forward" to where they go next. */
  direction: 'back' | 'forward'
}

/**
 * A back/forward arrow that follows the reading direction: in left-to-right
 * languages back points left, in Arabic it points right. A hard-coded ← or →
 * would point the wrong way in Arabic - the "mirrored English" problem.
 */
export function DirectionalArrow({ direction }: DirectionalArrowProps) {
  const { language } = useLanguage()
  const pointsLeft = (direction === 'back') !== RTL_LANGUAGES.has(language)
  return <span aria-hidden="true">{pointsLeft ? '←' : '→'}</span>
}
