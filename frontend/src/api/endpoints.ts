import { request } from './client';
import type {
  Account,
  Balance,
  CreateAccountRequest,
  CreateTransferRequest,
  DailyStats,
  FraudRules,
  LoginRequest,
  PageResponse,
  RegisterRequest,
  SearchCriteria,
  SearchHit,
  TokenResponse,
  Transaction,
  TransferResult,
  UserResponse,
} from './types';

/** All HTTP calls of the app, grouped by backend service. */
export const api = {
  auth: {
    register: (body: RegisterRequest) =>
      request<UserResponse>('/api/v1/auth/register', { method: 'POST', body }).then((r) => r.data),
    login: (body: LoginRequest) =>
      request<TokenResponse>('/api/v1/auth/login', { method: 'POST', body }).then((r) => r.data),
  },

  accounts: {
    mine: () => request<Account[]>('/api/v1/accounts').then((r) => r.data),
    create: (body: CreateAccountRequest) =>
      request<Account>('/api/v1/accounts', { method: 'POST', body }).then((r) => r.data),
    balance: (number: string) =>
      request<Balance>(`/api/v1/accounts/${encodeURIComponent(number)}/balance`).then((r) => r.data),
  },

  transactions: {
    /** A fresh Idempotency-Key per submit; retries of the *same* submit should reuse it. */
    create: async (body: CreateTransferRequest, idempotencyKey: string): Promise<TransferResult> => {
      const r = await request<Transaction>('/api/v1/transactions', {
        method: 'POST',
        body,
        headers: { 'Idempotency-Key': idempotencyKey },
      });
      return { transaction: r.data, replayed: r.headers.get('Idempotent-Replayed') === 'true' };
    },
    get: (id: string) => request<Transaction>(`/api/v1/transactions/${id}`).then((r) => r.data),
  },

  search: {
    transactions: (criteria: SearchCriteria, signal?: AbortSignal) =>
      request<PageResponse<SearchHit>>('/api/v1/search/transactions', {
        query: { ...criteria },
        signal,
      }).then((r) => r.data),
    stats: (from?: string, to?: string) =>
      request<DailyStats[]>('/api/v1/search/transactions/stats', { query: { from, to } }).then((r) => r.data),
  },

  processing: {
    fraudRules: () => request<FraudRules>('/api/v1/processing/fraud-rules').then((r) => r.data),
  },
};
