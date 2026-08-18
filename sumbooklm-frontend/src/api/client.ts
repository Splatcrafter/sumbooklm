import createClient from 'openapi-fetch';

import type { paths } from '@/api/schema';

/**
 * Type safe HTTP client bound to the OpenAPI specification published by the backend.
 *
 * The paths of the specification already carry the /api prefix, so no base URL is configured and
 * every request stays relative to the origin the application is served from.
 */
export const apiClient = createClient<paths>();
