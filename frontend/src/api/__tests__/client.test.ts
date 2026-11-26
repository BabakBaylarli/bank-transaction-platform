import { ApiError, request } from '@/api/client';

describe('api client', () => {
  afterEach(() => vi.restoreAllMocks());

  it('turns an RFC 7807 response into an ApiError with field errors', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          type: 'https://bank-platform.dev/problems/validation-error',
          title: 'Validation failed',
          status: 400,
          detail: 'Request body contains invalid fields',
          errorCode: 'validation-error',
          correlationId: 'abc',
          errors: [{ field: 'amount', message: 'must be greater than or equal to 0.01' }],
        }),
        { status: 400 },
      ),
    );

    const error = await request('/api/v1/transactions', { method: 'POST', body: {} }).catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(400);
    expect(error.problem.errorCode).toBe('validation-error');
    expect(error.userMessage).toBe(
      'Request body contains invalid fields (amount: must be greater than or equal to 0.01)',
    );
  });

  it('reports network failures as status 0', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));
    const error = await request('/api/v1/accounts').catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(0);
  });

  it('drops empty query parameters', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response('{"content":[]}', { status: 200 }));
    await request('/api/v1/search/transactions', {
      query: { q: 'rent', status: undefined, minAmount: '' },
    });
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/search/transactions?q=rent');
  });
});
