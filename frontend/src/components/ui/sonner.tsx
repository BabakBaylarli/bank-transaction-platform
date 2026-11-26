import { Toaster as Sonner } from 'sonner';

import { useTheme } from '@/theme/ThemeProvider';

export function Toaster() {
  const { theme } = useTheme();
  return <Sonner theme={theme} richColors closeButton position="top-right" />;
}
