import { useT } from '../i18n/useT'

export function NotFoundScreen() {
  const { t } = useT()
  return <div>{t('notFound.message')}</div>
}
