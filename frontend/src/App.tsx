import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { AdminScreen } from './screens/admin/AdminScreen'
import { ComingSoonView } from './screens/admin/views/ComingSoonView'
import { GuestScreen } from './screens/guest/GuestScreen'
import { HallScreen } from './screens/hall/HallScreen'
import { HomeScreen } from './screens/home/HomeScreen'
import { KitchenScreen } from './screens/kitchen/KitchenScreen'
import { NotFoundScreen } from './screens/NotFoundScreen'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomeScreen />} />
        <Route path="/guest" element={<GuestScreen />} />
        <Route path="/kitchen" element={<KitchenScreen />} />
        <Route path="/hall" element={<HallScreen />} />
        <Route path="/admin" element={<AdminScreen />}>
          <Route index element={<Navigate to="orders" replace />} />
          <Route path="orders" element={<ComingSoonView titleKey="admin.nav.orders" />} />
          <Route path="history" element={<ComingSoonView titleKey="admin.nav.history" />} />
          <Route path="meals" element={<ComingSoonView titleKey="admin.nav.meals" />} />
          <Route path="materials" element={<ComingSoonView titleKey="admin.nav.materials" />} />
          <Route path="tables" element={<ComingSoonView titleKey="admin.nav.tables" />} />
          <Route path="staff" element={<ComingSoonView titleKey="admin.nav.staff" />} />
          <Route path="settings" element={<ComingSoonView titleKey="admin.nav.settings" />} />
        </Route>
        <Route path="*" element={<NotFoundScreen />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
