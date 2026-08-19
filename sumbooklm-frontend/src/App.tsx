import { createBrowserRouter, RouterProvider } from 'react-router';

import { AuthProvider } from '@/auth/AuthProvider';
import { ModelSettingsProvider } from '@/byok/ModelSettingsProvider';
import { AppLayout } from '@/routes/AppLayout';
import { NotFoundPage } from '@/routes/NotFoundPage';
import { AccountLayout } from '@/routes/account/AccountLayout';
import { LoginPage } from '@/routes/account/LoginPage';
import { RegisterPage } from '@/routes/account/RegisterPage';
import { DashboardPage } from '@/routes/dashboard/DashboardPage';
import { SumbookPage } from '@/routes/sumbook/SumbookPage';

const router = createBrowserRouter([
  {
    path: '/account',
    element: <AccountLayout />,
    children: [
      { path: 'login', element: <LoginPage /> },
      { path: 'register', element: <RegisterPage /> },
    ],
  },
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <DashboardPage /> },
      { path: 'dashboard/sumbook/:notebookId', element: <SumbookPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);

export default function App() {
  return (
    <AuthProvider>
      <ModelSettingsProvider>
        <RouterProvider router={router} />
      </ModelSettingsProvider>
    </AuthProvider>
  );
}
