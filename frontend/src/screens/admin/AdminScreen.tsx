import { Outlet } from 'react-router'
import { AuthProvider } from '../../auth/AuthProvider'
import { ToastProvider } from '../../components/Toast'
import { useAuth } from '../../auth/auth-context'
import { LoginForm } from '../../auth/LoginForm'
import { LanguageProvider } from '../../i18n/LanguageProvider'
import { AdminSidebar } from '../../components/admin/AdminSidebar'
import { useT } from '../../i18n/useT'
import { ThemeProvider } from '../../theme/ThemeProvider'
import './AdminScreen.css'

function AdminScreenContent() {
  const { t } = useT()
  const { staff, loading, logout } = useAuth()

  if (loading) {
    return null
  }

  if (!staff) {
    return <LoginForm />
  }

  if (staff.role !== 'ADMIN') {
    return (
      <div className="admin-screen admin-screen--denied">
        <p>{t('admin.auth.accessDenied')}</p>
      </div>
    )
  }

  return (
    <div className="admin-screen">
      <AdminSidebar
        signedInAs={t('admin.auth.signedInAs', { name: staff.name, role: t(`admin.staff.roles.${staff.role}`) })}
        onLogout={() => void logout()}
      />
      <main id="admin-main" className="admin-screen__content" tabIndex={-1}>
        <ToastProvider>
          <Outlet />
        </ToastProvider>
      </main>
    </div>
  )
}

export function AdminScreen() {
  return (
    <ThemeProvider defaultTheme="dark">
      <LanguageProvider defaultLanguage="de">
        <AuthProvider>
          <AdminScreenContent />
        </AuthProvider>
      </LanguageProvider>
    </ThemeProvider>
  )
}
