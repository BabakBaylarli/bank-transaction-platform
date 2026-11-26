import { Activity } from 'lucide-react';

export function Logo({ compact = false }: { compact?: boolean }) {
  return (
    <div className="flex items-center gap-2 font-semibold">
      <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-primary-foreground shadow-sm">
        <Activity className="h-4 w-4" />
      </span>
      {!compact && <span className="tracking-tight">Bank Platform</span>}
    </div>
  );
}
