import { Route, Routes } from 'react-router-dom';
import { SessionProvider } from './session';
import CourierDashboardPage from './ui/dashboard/CourierDashboardPage';
import CustomerDashboardPage from './ui/dashboard/CustomerDashboardPage';
import DashboardRouter, { RequireRole } from './ui/dashboard/DashboardRouter';
import OperatorDashboardPage from './ui/dashboard/OperatorDashboardPage';
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
            <RequireRole role="customer">
              <CustomerDashboardPage />
            </RequireRole>
          }
        />
        <Route
          path="/restaurant"
          element={
            <RequireRole role="restaurant">
              <RestaurantDashboardPage />
            </RequireRole>
          }
        />
        <Route
          path="/courier"
          element={
            <RequireRole role="courier">
              <CourierDashboardPage />
            </RequireRole>
          }
        />
        <Route
          path="/operator"
          element={
            <RequireRole role="operator">
              <OperatorDashboardPage />
            </RequireRole>
          }
        />
        <Route path="*" element={<DashboardRouter />} />
      </Routes>
    </SessionProvider>
  );
}
