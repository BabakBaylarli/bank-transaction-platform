import type { ReactNode } from 'react';

import { Logo } from '@/components/Logo';
import { ThemeToggle } from '@/components/ThemeToggle';

export function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      <div className="relative hidden flex-col justify-between overflow-hidden bg-gradient-to-br from-indigo-700 via-violet-700 to-slate-900 p-10 text-white lg:flex">
        <div className="pointer-events-none absolute -right-24 top-24 h-80 w-80 rounded-full bg-white/10 blur-2xl" />
        <div className="[&_span:first-child]:bg-white/15">
          <Logo />
        </div>
        <div className="relative max-w-md space-y-4">
          <h2 className="text-3xl font-semibold leading-tight">Event-driven money transfers, end to end.</h2>
          <ul className="space-y-2 text-sm text-white/80">
            <li>• Idempotent API with transactional outbox</li>
            <li>• Kafka processing with fraud rules, retries &amp; DLT</li>
            <li>• Elasticsearch search and Kibana analytics</li>
          </ul>
        </div>
        <p className="text-xs text-white/50">Portfolio project · Java 21 · Spring Boot · React</p>
      </div>
      <div className="flex flex-col">
        <div className="flex items-center justify-between p-4 lg:justify-end">
          <div className="lg:hidden">
            <Logo />
          </div>
          <ThemeToggle />
        </div>
        <div className="flex flex-1 items-center justify-center px-4 pb-10">
          <div className="w-full max-w-md">{children}</div>
        </div>
      </div>
    </div>
  );
}
