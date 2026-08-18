import createClient from 'openapi-fetch';

import type { paths } from '@/api/schema';

/**
 * Type safe HTTP client bound to the OpenAPI specification published by the backend.
 */
export const apiClient = createClient<paths>({ baseUrl: '/api' });
