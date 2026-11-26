const env = import.meta.env;

export const config = {
  apiBaseUrl: (env.VITE_API_BASE_URL ?? '').replace(/\/$/, ''),
  kibanaUrl: env.VITE_KIBANA_URL || '/kibana/app/dashboards#/view/transactions-overview',
  demo: {
    enabled: (env.VITE_DEMO_MODE ?? 'true') !== 'false',
    customerPassword: env.VITE_DEMO_CUSTOMER_PASSWORD || 'Customer123!',
    adminPassword: env.VITE_DEMO_ADMIN_PASSWORD || 'Admin123!',
  },
} as const;
