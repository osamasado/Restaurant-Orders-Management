import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import './HomeScreen.css'

/** Dev-only landing page for this ticket's QA - not a production route. */
export function HomeScreen() {
  const { t } = useTranslation()

  return (
    <div className="home-screen">
      <h1>{t('home.title')}</h1>
      <nav className="home-screen__links">
        <Link to="/guest">{t('home.links.guest')}</Link>
        <Link to="/kitchen">{t('home.links.kitchen')}</Link>
        <Link to="/hall">{t('home.links.hall')}</Link>
        <Link to="/admin">{t('home.links.admin')}</Link>
      </nav>
    </div>
  )
}
