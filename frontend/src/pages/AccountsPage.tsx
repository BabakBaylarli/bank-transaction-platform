import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Loader2, Plus, Wallet } from 'lucide-react';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { api } from '@/api/endpoints';
import { queryKeys } from '@/api/queryKeys';
import { CURRENCIES } from '@/api/types';
import { AccountCard } from '@/components/AccountCard';
import { EmptyState } from '@/components/EmptyState';
import { FormField } from '@/components/FormField';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { useAccounts } from '@/hooks/useAccounts';
import { formatIban } from '@/lib/format';

const schema = z.object({
  currency: z.enum(['AZN', 'USD', 'EUR']),
  initialDeposit: z
    .union([z.literal(''), z.coerce.number().min(0, 'Cannot be negative').max(10_000, 'Max 10,000 in the demo')])
    .optional(),
});
type FormValues = z.input<typeof schema>;

function OpenAccountDialog() {
  const [open, setOpen] = useState(false);
  const queryClient = useQueryClient();
  const form = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { currency: 'AZN', initialDeposit: '' } });
  const { errors } = form.formState;

  const mutation = useMutation({
    mutationFn: api.accounts.create,
    onSuccess: (account) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.accounts });
      toast.success(`${account.currency} account opened`, { description: formatIban(account.number) });
      setOpen(false);
      form.reset();
    },
  });

  const onSubmit = form.handleSubmit((values) => {
    const parsed = schema.parse(values);
    const deposit = parsed.initialDeposit === '' || parsed.initialDeposit === undefined ? undefined : parsed.initialDeposit;
    mutation.mutate({ currency: parsed.currency, initialDeposit: deposit });
  });

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button>
          <Plus /> Open account
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Open a new account</DialogTitle>
          <DialogDescription>An IBAN-format account number is generated for you.</DialogDescription>
        </DialogHeader>
        <form onSubmit={onSubmit} className="space-y-4" noValidate>
          <FormField id="currency" label="Currency">
            <Controller
              control={form.control}
              name="currency"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger id="currency">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {CURRENCIES.map((c) => (
                      <SelectItem key={c} value={c}>
                        {c}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </FormField>
          <FormField
            id="initialDeposit"
            label="Initial deposit (optional)"
            error={errors.initialDeposit?.message}
            hint="Simulates a cash deposit so you have money to move. Max 10,000."
          >
            <Input
              id="initialDeposit"
              inputMode="decimal"
              placeholder="0.00"
              aria-invalid={!!errors.initialDeposit}
              {...form.register('initialDeposit')}
            />
          </FormField>
          <DialogFooter>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending && <Loader2 className="animate-spin" />}
              Open account
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

export default function AccountsPage() {
  const accounts = useAccounts();
  return (
    <>
      <PageHeader title="Accounts" description="All your accounts. Click one for its history." actions={<OpenAccountDialog />} />
      {accounts.isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-[150px] rounded-xl" />
          ))}
        </div>
      ) : accounts.data?.length ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {accounts.data.map((a) => (
            <AccountCard key={a.id} account={a} />
          ))}
        </div>
      ) : (
        <EmptyState icon={Wallet} title="No accounts yet" description="Use “Open account” to create your first one." />
      )}
    </>
  );
}
