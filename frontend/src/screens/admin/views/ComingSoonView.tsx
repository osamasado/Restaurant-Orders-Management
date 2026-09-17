import { useTranslation } from 'react-i18next'
import '../AdminScreen.css'

type ComingSoonViewProps = {
  titleKey: string
}

/** Shared placeholder for admin sub-sections - issues #13-#16 replace these with real content. */
export function ComingSoonView({ titleKey }: ComingSoonViewProps) {
  const { t } = useTranslation()

  return (
    <div>
      <h2 className="coming-soon-view__title">{t(titleKey)}</h2>
      <p className="coming-soon-view__note">{t('admin.comingSoon')}</p>
    </div>
  )
}
