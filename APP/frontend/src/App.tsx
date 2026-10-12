import { Route, Routes } from 'react-router-dom';
import { SessionProvider } from './session';
import CustomerDashboardPage from './ui/dashboard/CustomerDashboardPage';
import DashboardRouter, { ComingSoonPage, RequireRole } from './ui/dashboard/DashboardRouter';
import RestaurantDashboardPage from './ui/dashboard/RestaurantDashboardPage';
import LoginPage from './ui/reg/LoginPage';
import RegisterPage from './ui/reg/RegisterPage';

export default function App() {
  return (
    <SessionProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route
          path="/customer/*"
          element={
            <RequireRole roles={['customer']}>
              <CustomerDashboardPage />
            </RequireRole>
          }
        />
        <Route
          path="/restaurant"
          element={
            <RequireRole roles={['restaurant']}>
              <RestaurantDashboardPage />
            </RequireRole>
          }
        />
        <Route
          path="/coming-soon"
          element={
            <RequireRole roles={['courier', 'operator']}>
              <ComingSoonPage />
            </RequireRole>
          }
        />
        <Route path="*" element={<DashboardRouter />} />
      </Routes>
    </SessionProvider>
  );
}
