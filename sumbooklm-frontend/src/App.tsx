/*
 * Copyright (c) 2026 Erik Pförtner
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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
