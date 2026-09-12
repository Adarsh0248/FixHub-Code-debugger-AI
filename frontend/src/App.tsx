import { Toaster } from 'sonner';
import { useAuth } from './hooks/useAuth';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';

export default function App() {
  const auth = useAuth();

  return (
    <>
      <Toaster position="top-center" richColors closeButton theme="system" />
      {auth.status === 'loading' && (
        <div className="grid min-h-screen place-items-center">
          <div className="h-5 w-5 animate-spin rounded-full border-2 border-accent-500 border-t-transparent" />
        </div>
      )}
      {auth.status === 'signed-out' && <LoginPage />}
      {auth.status === 'signed-in' && (
        <DashboardPage username={auth.username} avatarUrl={auth.avatarUrl} />
      )}
    </>
  );
}
