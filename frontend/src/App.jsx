import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider, useAuth } from "./AuthContext";
import Navigation from "./Navigation";
import ProtectedRoute from "./ProtectedRoute";
import Login from "./Pages/Login";
import Profile from "./Pages/Profile";
import Signup from "./Pages/Signup";
import Wallet from "./Pages/Wallet";
import Admin from "./Pages/Admin";
import LenderRequest from "./Pages/LenderRequest";
import BorrowerDashboard from "./Pages/BorrowerDashboard";
import LenderDashboard from "./Pages/LenderDashboard";
import GroupLending from "./Pages/GroupLending";
import AiChatbot from "./Components/AiChatbot";

function AppRoutes() {
  const { isAuthenticated, loading } = useAuth();

  return (
    <>
      <Navigation />
      <Routes>
        <Route
          path="/"
          element={<Navigate to={isAuthenticated ? "/profile" : "/login"} replace />}
        />
        <Route
          path="/login"
          element={isAuthenticated ? <Navigate to="/profile" replace /> : <Login />}
        />
        <Route path="/signup" element={<Signup />} />
        <Route element={<ProtectedRoute />}>
          <Route path="/profile" element={loading ? <p role="status">Loading authentication...</p> : <Profile />} />
          <Route path="/wallet" element={<Wallet />} />
        </Route>
        <Route element={<ProtectedRoute roles={["BORROWER"]} />}>
          <Route path="/lender-request" element={<LenderRequest />} />
          <Route path="/borrower" element={<BorrowerDashboard />} />
          <Route path="/groups" element={<GroupLending />} />
        </Route>
        <Route element={<ProtectedRoute roles={["LENDER"]} />}>
          <Route path="/lender" element={<LenderDashboard />} />
        </Route>
        <Route element={<ProtectedRoute roles={["ADMIN"]} />}>
          <Route path="/admin" element={<Admin />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
      {isAuthenticated && <AiChatbot />}
    </>
  );
}

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;