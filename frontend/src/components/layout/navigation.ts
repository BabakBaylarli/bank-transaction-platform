import { ArrowLeftRight, LayoutDashboard, Search, ShieldCheck, Wallet, type LucideIcon } from 'lucide-react';

import type { Role } from '@/api/types';

export interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  role?: Role;
}

export const NAV_ITEMS: NavItem[] = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/accounts', label: 'Accounts', icon: Wallet },
  { to: '/transfer', label: 'Transfer', icon: ArrowLeftRight },
  { to: '/transactions', label: 'Transactions', icon: Search },
  { to: '/admin', label: 'Admin', icon: ShieldCheck, role: 'ADMIN' },
];
