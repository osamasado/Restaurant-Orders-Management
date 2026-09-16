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
          <Route path="orders" element={<ComingSoonView title="Orders" />} />
          <Route path="history" element={<ComingSoonView title="Audit history" />} />
          <Route path="meals" element={<ComingSoonView title="Meals" />} />
          <Route path="materials" element={<ComingSoonView title="Raw materials" />} />
          <Route path="tables" element={<ComingSoonView title="Tables & devices" />} />
          <Route path="staff" element={<ComingSoonView title="Staff accounts" />} />
          <Route path="settings" element={<ComingSoonView title="Settings" />} />
        </Route>
        <Route path="*" element={<NotFoundScreen />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
