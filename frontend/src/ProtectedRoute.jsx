import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "./AuthContext";

function ProtectedRoute({ roles }) {
  const { isAuthenticated, loading, user } = useAuth();

  if (loading || (isAuthenticated && !user)) {
    return <main className="mx-auto min-h-screen max-w-6xl px-5 pt-32 text-sm text-slate-400 sm:px-8"><p className="glass-panel" role="status">Loading authentication...</p></main>;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (roles && !roles.includes(user.role)) {
    return <Navigate to="/profile" replace />;
  }

  return <Outlet />;
}

export default ProtectedRoute;
