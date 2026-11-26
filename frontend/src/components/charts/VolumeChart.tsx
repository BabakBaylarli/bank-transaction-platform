import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

import { CURRENCIES } from '@/api/types';
import { formatDay } from '@/lib/format';

export interface VolumePoint {
  day: string;
  AZN: number;
  USD: number;
  EUR: number;
}

export const CURRENCY_COLORS = {
  AZN: 'hsl(var(--chart-1))',
  USD: 'hsl(var(--chart-2))',
  EUR: 'hsl(var(--chart-3))',
} as const;

export function VolumeChart({ data, height = 240 }: { data: VolumePoint[]; height?: number }) {
  return (
    <div style={{ height }} role="img" aria-label="Transfer volume per day and currency">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 4, right: 4, left: -12, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="hsl(var(--border))" />
          <XAxis
            dataKey="day"
            tickFormatter={formatDay}
            tickLine={false}
            axisLine={false}
            fontSize={12}
            stroke="hsl(var(--muted-foreground))"
          />
          <YAxis
            tickLine={false}
            axisLine={false}
            fontSize={12}
            stroke="hsl(var(--muted-foreground))"
            width={56}
          />
          <Tooltip
            cursor={{ fill: 'hsl(var(--muted))' }}
            contentStyle={{
              background: 'hsl(var(--popover))',
              border: '1px solid hsl(var(--border))',
              borderRadius: 8,
              color: 'hsl(var(--popover-foreground))',
            }}
            labelFormatter={(d: string) => formatDay(d)}
            formatter={(v: number) => v.toLocaleString('en-US', { minimumFractionDigits: 2 })}
          />
          <Legend iconType="circle" wrapperStyle={{ fontSize: 12 }} />
          {CURRENCIES.map((c) => (
            <Bar
              key={c}
              dataKey={c}
              stackId="v"
              maxBarSize={36}
              fill={CURRENCY_COLORS[c]}
              radius={c === 'EUR' ? [4, 4, 0, 0] : 0}
            />
          ))}
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
