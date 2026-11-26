import { Sparkles } from 'lucide-react';

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { config } from '@/config';

const USERS = [
  { username: 'alice', password: config.demo.customerPassword, role: 'Customer' },
  { username: 'bob', password: config.demo.customerPassword, role: 'Customer' },
  { username: 'admin', password: config.demo.adminPassword, role: 'Admin' },
];

/** Lets a visitor try the platform in 10 seconds: one click fills the login form. */
export function DemoBanner({ onPick }: { onPick: (username: string, password: string) => void }) {
  if (!config.demo.enabled) return null;
  return (
    <Alert variant="info" className="mb-6">
      <Sparkles className="h-4 w-4" />
      <AlertTitle>Demo mode</AlertTitle>
      <AlertDescription>
        <p className="mb-3 text-muted-foreground">Pick a demo user - data is reset every night.</p>
        <div className="grid gap-2 sm:grid-cols-3">
          {USERS.map((u) => (
            <Button
              key={u.username}
              type="button"
              variant="outline"
              size="sm"
              className="h-auto flex-col items-start gap-0 py-2"
              onClick={() => onPick(u.username, u.password)}
            >
              <span className="font-semibold">{u.username}</span>
              <span className="font-mono text-[11px] text-muted-foreground">{u.password}</span>
              <span className="text-[11px] text-muted-foreground">{u.role}</span>
            </Button>
          ))}
        </div>
      </AlertDescription>
    </Alert>
  );
}
