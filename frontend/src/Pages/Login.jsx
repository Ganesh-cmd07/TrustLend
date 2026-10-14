import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../AuthContext";
import api, { getApiErrorMessage } from "../api/axios";
import { ArrowRight, CheckCircle2, Eye, EyeOff, Landmark, Loader2, LockKeyhole, Mail, ShieldCheck } from "lucide-react";

function Login() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuth();

  const [email, setEmail] = useState(() => location.state?.email || "");
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState("");
  const [successBanner, setSuccessBanner] = useState(() => location.state?.successMessage || "");
  const [rememberMe, setRememberMe] = useState(true);
  const [showPassword, setShowPassword] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleLogin = async (e) => {
    e.preventDefault();
    setMessage("");
    setSuccessBanner("");
    setIsSubmitting(true);

    try {
      const { data } = await api.post("/api/v1/login", {
        email: email.trim(),
        password
      });

      if (!data.token) {
        setMessage(data.message || "Unable to log in");
        return;
      }

      login(data.token, rememberMe);
      navigate("/profile", { replace: true });
    } catch (requestError) {
      setMessage(getApiErrorMessage(requestError, "Unable to connect to server"));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className="grid min-h-screen place-items-center bg-slate-950 px-5 py-12 text-slate-100">
      <section className="w-full max-w-[460px] rounded-3xl border border-slate-800/80 bg-slate-900/80 p-7 shadow-2xl shadow-black/30 backdrop-blur-xl sm:p-10">
        <Link className="mb-8 inline-flex items-center gap-3 text-xl font-extrabold tracking-tight text-slate-100" to="/">
          <span className="icon-tile h-11 w-11"><Landmark size={20} /></span>
          <span>trust<span className="text-emerald-400">lend</span></span>
        </Link>

        {/* Tab switcher for quick navigation */}
        <div className="mb-7 flex rounded-xl border border-slate-800 bg-slate-950/60 p-1">
          <button
            type="button"
            className="flex-1 rounded-lg bg-emerald-500/10 py-2 text-center text-xs font-semibold text-emerald-300 shadow-sm transition"
          >
            Sign in
          </button>
          <Link
            to="/signup"
            className="flex-1 rounded-lg py-2 text-center text-xs font-medium text-slate-400 transition hover:text-slate-100"
          >
            Create account
          </Link>
        </div>

        <div className="mb-6">
          <span className="inline-flex items-center gap-1.5 text-[10px] font-bold tracking-[1.25px] text-emerald-400">
            <ShieldCheck size={15} /> SECURE FINANCIAL ACCESS
          </span>
          <h1 className="mb-2 mt-3 text-3xl font-bold tracking-tight">Welcome back</h1>
          <p className="text-sm leading-relaxed text-slate-400">Sign in to manage your money and move forward with confidence.</p>
        </div>

        {successBanner && (
          <div className="mb-5 flex items-start gap-3 rounded-2xl border border-emerald-500/30 bg-emerald-500/10 p-3.5 text-xs text-emerald-200" role="status">
            <CheckCircle2 className="mt-0.5 shrink-0 text-emerald-400" size={17} />
            <div className="leading-relaxed">{successBanner}</div>
          </div>
        )}

        <form className="grid gap-3" onSubmit={handleLogin}>
          <label className="text-xs font-semibold text-slate-300" htmlFor="login-email">Email address</label>
          <div className="relative flex items-center">
            <Mail className="absolute left-4 text-slate-500" size={18} />
            <input
              className="field pl-11"
              id="login-email"
              type="email"
              placeholder="you@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="email"
              disabled={isSubmitting}
              required
            />
          </div>

          <label className="mt-1 text-xs font-semibold text-slate-300" htmlFor="login-password">Password</label>
          <div className="relative flex items-center">
            <LockKeyhole className="absolute left-4 text-slate-500" size={18} />
            <input
              className="field pl-11 pr-12"
              id="login-password"
              type={showPassword ? "text" : "password"}
              placeholder="Enter your password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              disabled={isSubmitting}
              required
            />
            <button
              className="absolute right-2 grid h-9 w-9 place-items-center rounded-lg text-slate-400 transition hover:bg-slate-800 hover:text-emerald-300"
              type="button"
              aria-label={showPassword ? "Hide password" : "Show password"}
              onClick={() => setShowPassword((visible) => !visible)}
            >
              {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
            </button>
          </div>

          <label className="mb-2 mt-2 flex cursor-pointer items-center gap-2 text-xs text-slate-400">
            <input
              className="accent-emerald-500"
              type="checkbox"
              checked={rememberMe}
              onChange={(event) => setRememberMe(event.target.checked)}
              disabled={isSubmitting}
            />
            Keep me signed in
          </label>

          <button
            className="primary-button flex w-full items-center justify-center gap-2"
            type="submit"
            disabled={isSubmitting}
          >
            {isSubmitting ? (
              <>
                <Loader2 className="animate-spin" size={18} />
                <span>Signing in...</span>
              </>
            ) : (
              <>
                <span>Sign in</span>
                <ArrowRight size={18} />
              </>
            )}
          </button>
        </form>

        {message && (
          <p className="mt-4 rounded-xl border border-rose-500/20 bg-rose-500/10 p-3 text-xs leading-relaxed text-rose-300" role="alert">
            {message}
          </p>
        )}

        <div className="mt-7 rounded-2xl border border-slate-800/80 bg-slate-950/40 p-4 text-center">
          <p className="text-xs text-slate-400">
            New to TrustLend?
            <Link className="ml-1.5 font-bold text-emerald-400 transition hover:text-emerald-300 hover:underline" to="/signup">
              Create an account
            </Link>
          </p>
        </div>

        <p className="mt-6 flex items-center justify-center gap-2 border-t border-slate-800 pt-4 text-[10px] text-slate-500">
          <LockKeyhole size={13} /> Your information is protected with secure encryption
        </p>
      </section>
    </main>
  );
}

export default Login;