import { useState } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import type { Order } from '../../mockData';
import { useUser } from '../../session';
import { ConfirmButton, LoadError, OrderCard, useLoad } from '../common';
import MenuPage from '../ord/MenuPage';
import { listCustomerOrders, updateOrderStatus } from '../ord/ordApi';
import RestaurantListPage from '../ord/RestaurantListPage';
import { errorMessage, send } from './dashboardApi';
import { DashboardLayout } from './DashboardRouter';

const NAV = [
  { to: '/customer', label: 'Restaurants', end: true },
  { to: '/customer/orders', label: 'My orders' },
];

export default function CustomerDashboardPage() {
  return (
    <DashboardLayout title="customer" nav={NAV}>
      <Routes>
        <Route index element={<RestaurantListPage />} />
        <Route path="r/:restaurantId" element={<MenuPage />} />
        <Route path="orders" element={<MyOrders />} />
        <Route path="*" element={<Navigate to="/customer" replace />} />
      </Routes>
    </DashboardLayout>
  );
}

function MyOrders() {
  const user = useUser();
  const placedOrderId = (useLocation().state as { placedOrderId?: string } | null)?.placedOrderId;
  const { data: orders, error, reload } = useLoad(() => listCustomerOrders(user.id), [user.id]);
  const [actionError, setActionError] = useState('');

  async function cancel(order: Order) {
    setActionError('');
    try {
      await send(`Cancel order #${order.id}`, () => updateOrderStatus(order.id, 'cancelled'));
    } catch (e) {
      setActionError(errorMessage(e));
    }
    reload();
  }

  return (
    <section>
      <div className="section-head">
        <div>
          <h1>My orders</h1>
          <p className="muted">You can cancel an order until the restaurant accepts it.</p>
        </div>
        <button className="btn btn-ghost btn-sm" onClick={reload}>
          Refresh
        </button>
      </div>

      {placedOrderId && (
        <p className="alert" style={{ background: 'var(--brand-soft)', marginBottom: '1rem' }}>
          🎉 Order #{placedOrderId} placed! The restaurant will confirm it shortly.
        </p>
      )}
      {actionError && (
        <p className="alert alert-error" role="alert" style={{ marginBottom: '1rem' }}>
          {actionError}
        </p>
      )}

      {error ? (
        <LoadError what="your orders" message={error} onRetry={reload} />
      ) : !orders ? (
        <p className="muted">Loading orders…</p>
      ) : orders.length === 0 ? (
        <p className="empty">No orders yet. Find something delicious to rescue!</p>
      ) : (
        <div className="order-list">
          {[...orders].reverse().map((o) => (
            <OrderCard key={o.id} order={o} title={o.restaurantName}>
              {o.status === 'placed' && (
                <ConfirmButton label="Cancel order" question={`Cancel order #${o.id}?`} variant="danger" onConfirm={() => cancel(o)} />
              )}
            </OrderCard>
          ))}
        </div>
      )}
    </section>
  );
}
