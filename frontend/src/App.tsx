import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { AdminScreen } from './screens/admin/AdminScreen'
import { ComingSoonView } from './screens/admin/views/ComingSoonView'
import { MealsView } from './screens/admin/views/meals/MealsView'
import { MaterialsView } from './screens/admin/views/materials/MaterialsView'
import { TablesView } from './screens/admin/views/tables/TablesView'
import { StaffView } from './screens/admin/views/staff/StaffView'
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
          <Route path="meals" element={<MealsView />} />
          <Route path="materials" element={<MaterialsView />} />
          <Route path="tables" element={<TablesView />} />
          <Route path="staff" element={<StaffView />} />
          <Route path="settings" element={<ComingSoonView titleKey="admin.nav.settings" />} />
        </Route>
        <Route path="*" element={<NotFoundScreen />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
