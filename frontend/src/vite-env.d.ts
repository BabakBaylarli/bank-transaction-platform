/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_KIBANA_URL?: string;
  readonly VITE_DEMO_MODE?: string;
  readonly VITE_DEMO_CUSTOMER_PASSWORD?: string;
  readonly VITE_DEMO_ADMIN_PASSWORD?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
