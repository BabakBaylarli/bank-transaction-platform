// Mirrors the backend DTOs (records) of transaction-, processing- and search-service.

export type CurrencyCode = 'AZN' | 'USD' | 'EUR';
export const CURRENCIES: CurrencyCode[] = ['AZN', 'USD', 'EUR'];

export type Role = 'ADMIN' | 'CUSTOMER';

export type TransactionStatus = 'PENDING' | 'COMPLETED' | 'REJECTED';
export const STATUSES: TransactionStatus[] = ['PENDING', 'COMPLETED', 'REJECTED'];

export type RejectionReason =
  | 'AMOUNT_LIMIT_EXCEEDED'
  | 'DAILY_LIMIT_EXCEEDED'
  | 'VELOCITY_LIMIT_EXCEEDED'
  | 'INSUFFICIENT_FUNDS'
  | 'ACCOUNT_NOT_FOUND'
  | 'CURRENCY_MISMATCH';

/** RFC 7807 problem detail as produced by the platform's GlobalExceptionHandler. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  errorCode?: string;
  correlationId?: string;
  timestamp?: string;
  errors?: { field: string; message: string }[];
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface RegisterRequest {
  username: string;
  password: string;
  fullName: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
}

export interface UserResponse {
  id: string;
  username: string;
  fullName: string;
  role: Role;
  createdAt: string;
}

export interface Account {
  id: string;
  number: string;
  currency: CurrencyCode;
  balance: number;
  createdAt: string;
}

export interface CreateAccountRequest {
  currency: CurrencyCode;
  initialDeposit?: number;
}

export interface Balance {
  accountNumber: string;
  balance: number;
  currency: CurrencyCode;
  asOf: string;
}

export interface CreateTransferRequest {
  sourceAccount: string;
  targetAccount: string;
  amount: number;
  currency: CurrencyCode;
  description?: string;
}

export interface Transaction {
  id: string;
  sourceAccount: string;
  targetAccount: string;
  amount: number;
  currency: CurrencyCode;
  description: string | null;
  status: TransactionStatus;
  rejectionReason: RejectionReason | null;
  rejectionDetail: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TransferResult {
  transaction: Transaction;
  replayed: boolean;
}

/** search-service document (`createdAt` can be null briefly when events arrive out of order). */
export interface SearchHit {
  id: string;
  sourceAccount: string;
  targetAccount: string;
  amount: number;
  currency: CurrencyCode;
  status: TransactionStatus;
  rejectionReason: RejectionReason | null;
  rejectionDetail: string | null;
  description: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface SearchCriteria {
  account?: string;
  status?: TransactionStatus;
  currency?: CurrencyCode;
  minAmount?: number;
  maxAmount?: number;
  from?: string;
  to?: string;
  q?: string;
  page?: number;
  size?: number;
}

export interface DailyStats {
  day: string;
  status: TransactionStatus;
  currency: CurrencyCode;
  count: number;
  totalAmount: number;
}

export interface FraudRules {
  maxSingleAmount: number;
  dailyLimit: number;
  maxTransactionsPerMinute: number;
}
