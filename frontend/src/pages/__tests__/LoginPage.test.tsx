import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';

import { LoginPage } from '@/pages/LoginPage';
import { fakeToken, renderWithProviders } from '@/test/utils';

function renderLogin() {
  return renderWithProviders(
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<p>Dashboard home</p>} />
    </Routes>,
    { route: '/login' },
  );
}

describe('LoginPage', () => {
  afterEach(() => vi.restoreAllMocks());

  it('validates required fields', async () => {
    renderLogin();
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));
    expect(await screen.findByText('Username is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
  });

  it('demo banner fills credentials and a successful login stores the session', async () => {
    const token = fakeToken({ username: 'alice' });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ accessToken: token, tokenType: 'Bearer', expiresAt: '' }), {
        status: 200,
      }),
    );
    renderLogin();

    await userEvent.click(screen.getByRole('button', { name: /alice/ }));
    expect(screen.getByLabelText('Username')).toHaveValue('alice');

    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));

    expect(await screen.findByText('Dashboard home')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/login', expect.objectContaining({ method: 'POST' }));
    expect(localStorage.getItem('btp.session')).toBe(token);
  });

  it('shows the ProblemDetail message on bad credentials', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          status: 401,
          title: 'Invalid credentials',
          detail: 'Username or password is incorrect',
        }),
        { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
      ),
    );
    renderLogin();
    await userEvent.type(screen.getByLabelText('Username'), 'alice');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));

    await waitFor(() => expect(screen.getByText('Username or password is incorrect')).toBeInTheDocument());
  });
});
