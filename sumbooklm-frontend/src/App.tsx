import { createBrowserRouter, RouterProvider } from 'react-router';

import { AuthProvider } from '@/auth/AuthProvider';
import { AppLayout } from '@/routes/AppLayout';
import { HomePage } from '@/routes/HomePage';
import { NotFoundPage } from '@/routes/NotFoundPage';
import { AccountLayout } from '@/routes/account/AccountLayout';
import { LoginPage } from '@/routes/account/LoginPage';
import { RegisterPage } from '@/routes/account/RegisterPage';

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
      { index: true, element: <HomePage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);

export default function App() {
  return (
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>
  );
}
