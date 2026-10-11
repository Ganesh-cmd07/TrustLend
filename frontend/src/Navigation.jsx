import { NavLink, Link, useNavigate } from "react-router-dom";
import { useAuth } from "./AuthContext";
import { ArrowUpRight, Landmark, LogOut, Wallet, Users, BriefcaseBusiness, ShieldCheck, UserRound } from "lucide-react";

function Navigation() {
  const { isAuthenticated, user, logout } = useAuth();
  const navigate = useNavigate();

  if (!isAuthenticated) {
    return null;
  }

  const handleLogout = () => {
    logout();
    navigate("/login", { replace: true });
  };

  return (
    <header className="fixed inset-x-0 top-0 z-30 border-b border-slate-800/80 bg-slate-950/85 backdrop-blur-xl">
      <div className="mx-auto flex min-h-[76px] max-w-[1440px] items-center justify-between gap-5 px-5 sm:px-8">
        <Link className="flex shrink-0 items-center gap-3 text-lg font-extrabold tracking-tight text-slate-100" to="/profile">
          <span className="icon-tile h-10 w-10 rounded-[13px]"><Landmark size={19} strokeWidth={2.3} /></span>
          <span>trust<span className="text-emerald-400">lend</span><small className="mt-1 block text-[8px] font-bold tracking-[1.65px] text-slate-500">FINANCE, TOGETHER</small></span>
        </Link>
        <nav aria-label="Main navigation" className="flex max-w-full items-center gap-1 overflow-x-auto">
          <NavLink className="flex shrink-0 items-center gap-2 rounded-xl px-3 py-2.5 text-xs font-semibold text-slate-400 transition hover:bg-slate-800/70 hover:text-slate-100 [&.active]:bg-emerald-500/10 [&.active]:text-emerald-300" to="/profile">Overview</NavLink>
          <NavLink className="flex shrink-0 items-center gap-2 rounded-xl px-3 py-2.5 text-xs font-semibold text-slate-400 transition hover:bg-slate-800/70 hover:text-slate-100 [&.active]:bg-emerald-500/10 [&.active]:text-emerald-300" to="/wallet"><Wallet size={16} /> Wallet</NavLink>
          {user?.role === "BORROWER" && <NavLink className="flex shrink-0 items-center gap-2 rounded-xl px-3 py-2.5 text-xs font-semibold text-slate-400 transition hover:bg-slate-800/70 hover:text-slate-100 [&.active]:bg-emerald-500/10 [&.active]:text-emerald-300" to="/borrower"><ArrowUpRight size={16} /> Borrow</NavLink>}
          {user?.role === "BORROWER" && <NavLink className="flex shrink-0 items-center gap-2 rounded-xl px-3 py-2.5 text-xs font-semibold text-slate-400 transition hover:bg-slate-800/70 hover:text-slate-100 [&.active]:bg-emerald-500/10 [&.active]:text-emerald-300" to="/groups"><Users size={16} /> Groups</NavLink>}
          {user?.role === "BORROWER" && <NavLink className="flex shrink-0 items-center gap-2 rounded-xl px-3 py-2.5 text-xs font-semibold text-slate-400 transition hover:bg-slate-800/70 hover:text-slate-100 [&.active]:bg-emerald-500/10 [&.active]:text-emerald-300" to="/lender-request"><BriefcaseBusiness size={16} /> Become a lender</NavLink>}
          {user?.role === "LENDER" && <NavLink className="flex shrink-0 items-center gap-2 rounded-xl px-3 py-2.5 text-xs font-semibold text-slate-400 transition hover:bg-slate-800/70 hover:text-slate-100 [&.active]:bg-emerald-500/10 [&.active]:text-emerald-300" to="/lender"><BriefcaseBusiness size={16} /> Lending</NavLink>}
          {user?.role === "ADMIN" && <NavLink className="flex shrink-0 items-center gap-2 rounded-xl px-3 py-2.5 text-xs font-semibold text-slate-400 transition hover:bg-slate-800/70 hover:text-slate-100 [&.active]:bg-emerald-500/10 [&.active]:text-emerald-300" to="/admin"><ShieldCheck size={16} /> Admin</NavLink>}
        </nav>
        <div className="flex shrink-0 items-center gap-2">
          <span className="grid h-9 w-9 place-items-center rounded-full border border-emerald-500/20 bg-emerald-500/10 text-emerald-300"><UserRound size={17} /></span>
          <span className="hidden max-w-28 truncate text-xs font-medium text-slate-300 lg:block">{user?.name || "My account"}</span>
          <button className="secondary-button min-h-0 px-3 py-2 text-xs" type="button" onClick={handleLogout} aria-label="Log out">
            <LogOut size={17} /><span>Log out</span>
          </button>
        </div>
      </div>
    </header>
  );
}

export default Navigation;
