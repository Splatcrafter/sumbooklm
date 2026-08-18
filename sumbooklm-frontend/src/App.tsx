import { createBrowserRouter, RouterProvider } from 'react-router';

import { AppLayout } from '@/routes/AppLayout';
import { HomePage } from '@/routes/HomePage';
import { NotFoundPage } from '@/routes/NotFoundPage';

const router = createBrowserRouter([
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
  return <RouterProvider router={router} />;
}
