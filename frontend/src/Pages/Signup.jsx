import { useState } from "react";
import { Link } from "react-router-dom";
import api, { getApiErrorMessage } from "../api/axios";
import { ArrowRight, Eye, EyeOff, Landmark, LockKeyhole, Mail, UserRound } from "lucide-react";

function Signup() {

  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState("BORROWER");
  const [message, setMessage] = useState("");
  const [showPassword, setShowPassword] = useState(false);

  const handleSignup = async (e) => {

    e.preventDefault();

    setMessage("");

    try {

      const { data } = await api.post("/api/v1/signup", {
        name,
        email,
        password,
        role
      });

      setMessage(data.message || "Your account has been created.");
    } catch (requestError) {
      setMessage(getApiErrorMessage(requestError, "Unable to connect to server"));
    }
  };

  return (
    <main className="grid min-h-screen place-items-center bg-slate-950 px-5 py-12 text-slate-100">
      <section className="w-full max-w-[460px] rounded-3xl border border-slate-800/80 bg-slate-900/80 p-7 shadow-2xl shadow-black/30 backdrop-blur-xl sm:p-10">
        <Link className="mb-9 inline-flex items-center gap-3 text-xl font-extrabold tracking-tight text-slate-100" to="/login">
          <span className="icon-tile h-11 w-11"><Landmark size={20} /></span>
          <span>trust<span className="text-emerald-400">lend</span></span>
        </Link>
        <div className="mb-7">
          <span className="text-[10px] font-bold tracking-[1.25px] text-emerald-400">A BETTER WAY FORWARD</span>
          <h1 className="mb-2 mt-3 text-3xl font-bold tracking-tight">Create your account</h1>
          <p className="text-sm leading-relaxed text-slate-400">Join a community building stronger financial futures together.</p>
        </div>
        <form className="grid gap-3" onSubmit={handleSignup}>
          <label className="text-xs font-semibold text-slate-300" htmlFor="signup-name">Full name</label>
          <div className="relative flex items-center"><UserRound className="absolute left-4 text-slate-500" size={18} /><input className="field pl-11" id="signup-name" type="text" placeholder="Your name" value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" required /></div>
          <label className="mt-1 text-xs font-semibold text-slate-300" htmlFor="signup-email">Email address</label>
          <div className="relative flex items-center"><Mail className="absolute left-4 text-slate-500" size={18} /><input className="field pl-11" id="signup-email" type="email" placeholder="you@example.com" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required /></div>
          <label className="mt-1 text-xs font-semibold text-slate-300" htmlFor="signup-role">Account type</label>
          <select className="field" id="signup-role" value={role} onChange={(e) => setRole(e.target.value)}><option value="BORROWER">Borrower</option><option value="LENDER">Lender</option></select>
          {role === "LENDER" && <p className="text-xs leading-relaxed text-amber-300" role="note">Lender access starts after administrator approval. You can sign in while your request is reviewed.</p>}
          <label className="mt-1 text-xs font-semibold text-slate-300" htmlFor="signup-password">Password</label>
          <div className="relative flex items-center"><LockKeyhole className="absolute left-4 text-slate-500" size={18} /><input className="field pl-11 pr-12" id="signup-password" type={showPassword ? "text" : "password"} placeholder="Create a password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" required /><button className="absolute right-2 grid h-9 w-9 place-items-center rounded-lg text-slate-400 transition hover:bg-slate-800 hover:text-emerald-300" type="button" aria-label={showPassword ? "Hide password" : "Show password"} onClick={() => setShowPassword((visible) => !visible)}>{showPassword ? <EyeOff size={17} /> : <Eye size={17} />}</button></div>
          <button className="primary-button mt-2 w-full" type="submit">Create account <ArrowRight size={18} /></button>
        </form>
        {message && <p className="mt-4 rounded-xl border border-emerald-500/20 bg-emerald-500/10 p-3 text-sm text-emerald-300" role="status">{message}</p>}
        <p className="mt-6 text-center text-xs text-slate-400">Already have an account? <Link className="ml-1 font-bold text-emerald-400 hover:text-emerald-300" to="/login">Sign in</Link></p>
        <p className="mt-6 flex items-center justify-center gap-2 border-t border-slate-800 pt-4 text-[10px] text-slate-500"><LockKeyhole size={13} /> Your information is protected with secure encryption</p>
      </section>
    </main>
  );
}

export default Signup;
