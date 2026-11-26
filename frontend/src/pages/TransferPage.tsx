import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeftRight, Loader2, RotateCcw, Send, Wallet } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { Link, useSearchParams } from 'react-router-dom';
import { z } from 'zod';

import { api } from '@/api/endpoints';
import { queryKeys } from '@/api/queryKeys';
import { EmptyState } from '@/components/EmptyState';
import { FormField } from '@/components/FormField';
import { Money } from '@/components/Money';
import { PageHeader } from '@/components/PageHeader';
import { TransferStatus } from '@/components/TransferStatus';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { useAccounts } from '@/hooks/useAccounts';
import { shortIban } from '@/lib/format';

const IBAN = /^[A-Z]{2}\d{2}[A-Z0-9]{11,30}$/;

export const transferSchema = z
  .object({
    sourceAccount: z.string().min(1, 'Choose the account to send from'),
    targetAccount: z
      .string()
      .transform((v) => v.replace(/\s+/g, '').toUpperCase())
      .pipe(z.string().regex(IBAN, 'Enter a valid account number, e.g. AZ31BTPX00000000000000000003')),
    amount: z
      .union([z.string(), z.number()])
      .pipe(
        z.coerce
          .number({ invalid_type_error: 'Enter an amount' })
          .positive('Amount must be greater than 0')
          .multipleOf(0.01, 'At most 2 decimals'),
      ),
    description: z.string().max(255).optional(),
  })
  .refine((v) => v.sourceAccount !== v.targetAccount, {
    message: 'Choose a different target account',
    path: ['targetAccount'],
  });
type FormInput = z.input<typeof transferSchema>;

const POLL_MS = 1000;

export default function TransferPage() {
  const accounts = useAccounts();
  const queryClient = useQueryClient();
  const [params] = useSearchParams();
  const [transactionId, setTransactionId] = useState<string | null>(null);

  const form = useForm<FormInput>({
    resolver: zodResolver(transferSchema),
    defaultValues: { sourceAccount: params.get('from') ?? '', targetAccount: '', amount: '', description: '' },
  });
  const { errors } = form.formState;
  const sourceNumber = form.watch('sourceAccount');
  const source = useMemo(() => accounts.data?.find((a) => a.number === sourceNumber), [accounts.data, sourceNumber]);

  // preselect the first account once accounts are loaded
  useEffect(() => {
    if (!form.getValues('sourceAccount') && accounts.data?.length) {
      form.setValue('sourceAccount', accounts.data[0].number);
    }
  }, [accounts.data, form]);

  const create = useMutation({
    mutationFn: (vars: { body: Parameters<typeof api.transactions.create>[0]; key: string }) =>
      api.transactions.create(vars.body, vars.key),
    onSuccess: ({ transaction }) => {
      queryClient.setQueryData(queryKeys.transaction(transaction.id), transaction);
      setTransactionId(transaction.id);
    },
  });

  // poll until processing-service has decided
  const status = useQuery({
    queryKey: queryKeys.transaction(transactionId ?? ''),
    queryFn: () => api.transactions.get(transactionId!),
    enabled: !!transactionId,
    refetchInterval: (query) => (query.state.data?.status === 'PENDING' ? POLL_MS : false),
  });

  useEffect(() => {
    if (status.data && status.data.status !== 'PENDING') {
      queryClient.invalidateQueries({ queryKey: queryKeys.accounts });
      queryClient.invalidateQueries({ queryKey: ['search'] });
    }
  }, [status.data, queryClient]);

  const onSubmit = form.handleSubmit((values) => {
    const parsed = transferSchema.parse(values);
    if (!source) return;
    create.mutate({
      body: {
        sourceAccount: parsed.sourceAccount,
        targetAccount: parsed.targetAccount,
        amount: parsed.amount,
        currency: source.currency,
        description: parsed.description || undefined,
      },
      // one key per submit: a network retry of this same request would be answered idempotently
      key: crypto.randomUUID(),
    });
  });

  const reset = () => {
    setTransactionId(null);
    create.reset();
    form.reset({ ...form.getValues(), targetAccount: '', amount: '', description: '' });
  };

  if (!accounts.isLoading && !accounts.data?.length) {
    return (
      <>
        <PageHeader title="Transfer" />
        <EmptyState
          icon={Wallet}
          title="You need an account first"
          action={
            <Button asChild>
              <Link to="/accounts">Open account</Link>
            </Button>
          }
        />
      </>
    );
  }

  return (
    <>
      <PageHeader title="Transfer" description="Send money to any account in the same currency." />
      <div className="grid gap-6 lg:grid-cols-5">
        <Card className="lg:col-span-3">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <ArrowLeftRight className="h-4 w-4" /> New transfer
            </CardTitle>
            <CardDescription>Each submit carries a unique Idempotency-Key, so a retry can never pay twice.</CardDescription>
          </CardHeader>
          <CardContent>
            {accounts.isLoading ? (
              <Skeleton className="h-64 w-full" />
            ) : (
              <form onSubmit={onSubmit} className="space-y-4" noValidate>
                <FormField id="sourceAccount" label="From" error={errors.sourceAccount?.message}>
                  <Controller
                    control={form.control}
                    name="sourceAccount"
                    render={({ field }) => (
                      <Select value={field.value} onValueChange={field.onChange} disabled={!!transactionId}>
                        <SelectTrigger id="sourceAccount" aria-invalid={!!errors.sourceAccount}>
                          <SelectValue placeholder="Choose account" />
                        </SelectTrigger>
                        <SelectContent>
                          {accounts.data?.map((a) => (
                            <SelectItem key={a.number} value={a.number}>
                              {a.currency} · {shortIban(a.number)} · {a.balance.toFixed(2)}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    )}
                  />
                </FormField>
                {source && (
                  <p className="-mt-2 text-xs text-muted-foreground">
                    Available: <Money amount={source.balance} currency={source.currency} />
                  </p>
                )}
                <FormField id="targetAccount" label="To account number" error={errors.targetAccount?.message}>
                  <Input
                    id="targetAccount"
                    placeholder="AZ31 BTPX 0000 0000 0000 0000 0003"
                    autoComplete="off"
                    className="font-mono"
                    disabled={!!transactionId}
                    aria-invalid={!!errors.targetAccount}
                    {...form.register('targetAccount')}
                  />
                </FormField>
                <FormField id="amount" label={`Amount${source ? ` (${source.currency})` : ''}`} error={errors.amount?.message}>
                  <Input
                    id="amount"
                    inputMode="decimal"
                    placeholder="0.00"
                    disabled={!!transactionId}
                    aria-invalid={!!errors.amount}
                    {...form.register('amount')}
                  />
                </FormField>
                <FormField id="description" label="Description (optional)" error={errors.description?.message}>
                  <Input id="description" placeholder="e.g. Rent for May" disabled={!!transactionId} {...form.register('description')} />
                </FormField>
                {transactionId ? (
                  <Button type="button" variant="outline" className="w-full" onClick={reset}>
                    <RotateCcw /> New transfer
                  </Button>
                ) : (
                  <Button type="submit" className="w-full" disabled={create.isPending}>
                    {create.isPending ? <Loader2 className="animate-spin" /> : <Send />}
                    Send money
                  </Button>
                )}
              </form>
            )}
          </CardContent>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Status</CardTitle>
            <CardDescription>Updates live until processing finishes</CardDescription>
          </CardHeader>
          <CardContent>
            {status.data ? (
              <TransferStatus transaction={status.data} />
            ) : (
              <p className="rounded-lg border border-dashed p-6 text-center text-sm text-muted-foreground">
                Submit a transfer to follow it here.
              </p>
            )}
          </CardContent>
        </Card>
      </div>
    </>
  );
}
