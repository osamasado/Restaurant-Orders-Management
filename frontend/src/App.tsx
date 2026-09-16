import { BrowserRouter, Route, Routes } from 'react-router'
import { AdminScreen } from './screens/admin/AdminScreen'
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
        <Route path="/admin" element={<AdminScreen />} />
        <Route path="*" element={<NotFoundScreen />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
