import { useTranslation } from 'react-i18next'

export function NotFoundScreen() {
  const { t } = useTranslation()
  return <div>{t('notFound.message')}</div>
}
