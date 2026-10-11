import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState
} from "react";
import { apiFetch } from "./api/axios";

const AUTH_TOKEN_KEY = "trustlend_jwt";
const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() =>
    localStorage.getItem(AUTH_TOKEN_KEY) || sessionStorage.getItem(AUTH_TOKEN_KEY)
  );
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(() =>
    Boolean(localStorage.getItem(AUTH_TOKEN_KEY) || sessionStorage.getItem(AUTH_TOKEN_KEY))
  );
  const [error, setError] = useState("");

  const login = useCallback((newToken, rememberMe = true) => {
    const storage = rememberMe ? localStorage : sessionStorage;
    const otherStorage = rememberMe ? sessionStorage : localStorage;
    storage.setItem(AUTH_TOKEN_KEY, newToken);
    otherStorage.removeItem(AUTH_TOKEN_KEY);
    setUser(null);
    setError("");
    setLoading(true);
    setToken(newToken);
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem(AUTH_TOKEN_KEY);
    sessionStorage.removeItem(AUTH_TOKEN_KEY);
    setUser(null);
    setError("");
    setLoading(false);
    setToken(null);
  }, []);

  const refreshUser = useCallback(async (signal) => {
    if (!token) {
      setUser(null);
      return null;
    }

    try {
      const response = await apiFetch("/api/v1/auth/me", {
        headers: { Authorization: `Bearer ${token}` },
        signal
      });

      if (response.status === 401) {
        logout();
        return null;
      }
      if (!response.ok) {
        throw new Error("Unable to load authenticated user");
      }

      const authenticatedUser = await response.json();
      setUser(authenticatedUser);
      setError("");
      return authenticatedUser;
    } catch (requestError) {
      if (requestError.name === "AbortError") {
        return null;
      }
      setError("Unable to connect to server");
      throw requestError;
    } finally {
      if (!signal?.aborted) {
        setLoading(false);
      }
    }
  }, [token, logout]);

  useEffect(() => {
    if (!token) {
      return undefined;
    }

    const controller = new AbortController();

    async function loadAuthenticatedUser() {
      try {
        const response = await apiFetch("/api/v1/auth/me", {
          headers: { Authorization: `Bearer ${token}` },
          signal: controller.signal
        });
        if (response.status === 401) {
          logout();
          return;
        }
        if (!response.ok) {
          throw new Error("Unable to load authenticated user");
        }
        setUser(await response.json());
        setError("");
      } catch (requestError) {
        if (requestError.name !== "AbortError") {
          setError("Unable to connect to server");
        }
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      }
    }

    loadAuthenticatedUser();
    return () => controller.abort();
  }, [token, logout]);

  const value = useMemo(
    () => ({
      token,
      user,
      loading,
      error,
      isAuthenticated: Boolean(token),
      login,
      logout,
      refreshUser
    }),
    [token, user, loading, error, login, logout, refreshUser]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
