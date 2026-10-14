import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import api, { getApiErrorMessage } from "../api/axios";
import {
  ArrowRight,
  Check,
  CheckCircle2,
  Eye,
  EyeOff,
  Info,
  Landmark,
  Loader2,
  LockKeyhole,
  Mail,
  UserRound
} from "lucide-react";

function Signup() {
  const navigate = useNavigate();

  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState("BORROWER");
  const [message, setMessage] = useState("");
  const [isSuccess, setIsSuccess] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const passwordLength = password.length;
  const isPasswordValid = passwordLength >= 8 && passwordLength <= 72;

  const handleSignup = async (e) => {
    e.preventDefault();
    setMessage("");
    setIsSuccess(false);

    // Client-side validation checks
    if (!name.trim()) {
      setMessage("Please enter your full name.");
      return;
    }
    if (!email.trim()) {
      setMessage("Please enter a valid email address.");
      return;
    }
    if (passwordLength < 8) {
      setMessage("Password must be at least 8 characters long (between 8 and 72 characters). Any letters, numbers, or special symbols are accepted.");
      return;
    }
    if (passwordLength > 72) {
      setMessage("Password cannot exceed 72 characters.");
      return;
    }

    setIsSubmitting(true);

    try {
      const trimmedEmail = email.trim();
      const { data } = await api.post("/api/v1/signup", {
        name: name.trim(),
        email: trimmedEmail,
        password,
        role
      });

      setIsSuccess(true);
      setMessage(data?.message || "Account created successfully! Redirecting to sign in...");

      // Automatically redirect to login page after 1.5 seconds with email pre-filled
      setTimeout(() => {
        navigate("/login", {
          replace: true,
          state: {
            successMessage: "Account created successfully! Please sign in with your email and password.",
            email: trimmedEmail
          }
        });
      }, 1500);
    } catch (requestError) {
      setIsSuccess(false);
      setMessage(getApiErrorMessage(requestError, "Unable to create account"));
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
          <Link
            to="/login"
            className="flex-1 rounded-lg py-2 text-center text-xs font-medium text-slate-400 transition hover:text-slate-100"
          >
            Sign in
          </Link>
          <button
            type="button"
            className="flex-1 rounded-lg bg-emerald-500/10 py-2 text-center text-xs font-semibold text-emerald-300 shadow-sm transition"
          >
            Create account
          </button>
        </div>

        <div className="mb-6">
          <span className="text-[10px] font-bold tracking-[1.25px] text-emerald-400">A BETTER WAY FORWARD</span>
          <h1 className="mb-2 mt-3 text-3xl font-bold tracking-tight">Create your account</h1>
          <p className="text-sm leading-relaxed text-slate-400">Join a community building stronger financial futures together.</p>
        </div>

        {/* Success Banner */}
        {isSuccess && (
          <div className="mb-6 rounded-2xl border border-emerald-500/30 bg-emerald-500/10 p-4 text-xs text-emerald-200" role="status">
            <div className="flex items-start gap-3">
              <CheckCircle2 className="mt-0.5 shrink-0 text-emerald-400" size={18} />
              <div className="space-y-1">
                <p className="font-semibold text-emerald-300">Registration Complete!</p>
                <p className="text-slate-300">{message}</p>
                <Link
                  to="/login"
                  state={{
                    successMessage: "Account created successfully! Please sign in.",
                    email: email.trim()
                  }}
                  className="mt-2 inline-flex items-center gap-1.5 font-bold text-emerald-400 hover:text-emerald-300 hover:underline"
                >
                  Proceed to sign in now <ArrowRight size={14} />
                </Link>
              </div>
            </div>
          </div>
        )}

        <form className="grid gap-3" onSubmit={handleSignup}>
          <label className="text-xs font-semibold text-slate-300" htmlFor="signup-name">Full name</label>
          <div className="relative flex items-center">
            <UserRound className="absolute left-4 text-slate-500" size={18} />
            <input
              className="field pl-11"
              id="signup-name"
              type="text"
              placeholder="Your name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              autoComplete="name"
              disabled={isSubmitting || isSuccess}
              required
            />
          </div>

          <label className="mt-1 text-xs font-semibold text-slate-300" htmlFor="signup-email">Email address</label>
          <div className="relative flex items-center">
            <Mail className="absolute left-4 text-slate-500" size={18} />
            <input
              className="field pl-11"
              id="signup-email"
              type="email"
              placeholder="you@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="email"
              disabled={isSubmitting || isSuccess}
              required
            />
          </div>

          <label className="mt-1 text-xs font-semibold text-slate-300" htmlFor="signup-role">Account type</label>
          <select
            className="field"
            id="signup-role"
            value={role}
            onChange={(e) => setRole(e.target.value)}
            disabled={isSubmitting || isSuccess}
          >
            <option value="BORROWER">Borrower</option>
            <option value="LENDER">Lender</option>
          </select>
          {role === "LENDER" && (
            <p className="rounded-xl border border-amber-500/20 bg-amber-500/10 p-2.5 text-xs leading-relaxed text-amber-300" role="note">
              Lender access starts after administrator approval. You can sign in while your request is reviewed.
            </p>
          )}

          <label className="mt-1 text-xs font-semibold text-slate-300" htmlFor="signup-password">Password</label>
          <div className="relative flex items-center">
            <LockKeyhole className="absolute left-4 text-slate-500" size={18} />
            <input
              className="field pl-11 pr-12"
              id="signup-password"
              type={showPassword ? "text" : "password"}
              placeholder="Create a password (min. 8 characters)"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="new-password"
              disabled={isSubmitting || isSuccess}
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

          {/* Interactive password requirements guide */}
          <div className="rounded-xl border border-slate-800/80 bg-slate-950/50 p-2.5 text-[11px] text-slate-400">
            <div className="flex items-center justify-between">
              <span className="flex items-center gap-1.5 font-medium">
                <Info size={13} className="text-slate-400" />
                Password requirement:
              </span>
              <span className={isPasswordValid ? "font-semibold text-emerald-400" : passwordLength > 0 ? "text-amber-400" : "text-slate-500"}>
                {passwordLength}/8 min
              </span>
            </div>
            <p className="mt-1 flex items-center gap-1.5 leading-relaxed text-slate-400">
              {isPasswordValid ? (
                <span className="flex items-center gap-1 text-emerald-400">
                  <Check size={13} strokeWidth={2.5} /> Meets 8–72 characters requirement
                </span>
              ) : (
                <span>Must be between 8 and 72 characters. Any characters or special symbols are allowed.</span>
              )}
            </p>
          </div>

          <button
            className="primary-button mt-2 flex w-full items-center justify-center gap-2"
            type="submit"
            disabled={isSubmitting || isSuccess}
          >
            {isSubmitting ? (
              <>
                <Loader2 className="animate-spin" size={18} />
                <span>Creating account...</span>
              </>
            ) : isSuccess ? (
              <>
                <CheckCircle2 size={18} />
                <span>Account Created!</span>
              </>
            ) : (
              <>
                <span>Create account</span>
                <ArrowRight size={18} />
              </>
            )}
          </button>
        </form>

        {/* Error message alert */}
        {!isSuccess && message && (
          <p className="mt-4 rounded-xl border border-rose-500/20 bg-rose-500/10 p-3 text-xs leading-relaxed text-rose-300" role="alert">
            {message}
          </p>
        )}

        <div className="mt-7 rounded-2xl border border-slate-800/80 bg-slate-950/40 p-4 text-center">
          <p className="text-xs text-slate-400">
            Already have an account?
            <Link className="ml-1.5 font-bold text-emerald-400 transition hover:text-emerald-300 hover:underline" to="/login">
              Sign in
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

export default Signup;
