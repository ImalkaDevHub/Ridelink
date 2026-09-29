"use client";

import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { 
  Users, 
  CheckCircle2, 
  XCircle, 
  Search, 
  Bell, 
  LayoutDashboard, 
  Car, 
  FileText, 
  Settings, 
  LogOut,
  ArrowUpRight,
  RefreshCw
} from 'lucide-react';
import { useAuth } from '@/lib/auth';

interface AdminDriver {
  id: string; // The ID from driver-service
  userId: string; // The ID from account-service
  name: string;
  email: string;
  vehicleType: string;
  status: "ACTIVE" | "SUSPENDED";
  available: boolean;
  currentRideId?: string | null;
}

const MOCK_DRIVERS: AdminDriver[] = [
  { id: "d1", userId: "u1", name: "John Doe", email: "john@example.com", vehicleType: "Car", status: "ACTIVE", available: true },
  { id: "d2", userId: "u2", name: "Jane Smith", email: "jane@example.com", vehicleType: "Van", status: "SUSPENDED", available: false },
  { id: "d3", userId: "u3", name: "Bob Johnson", email: "bob@example.com", vehicleType: "Bike", status: "ACTIVE", available: false },
  { id: "d4", userId: "u4", name: "Alice Brown", email: "alice@example.com", vehicleType: "Car", status: "ACTIVE", available: false, currentRideId: "ride123" },
];

const AdminDashboard = () => {
  const router = useRouter();
  const { session, ready, logout } = useAuth();

  const [drivers, setDrivers] = useState<AdminDriver[]>([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState<"ALL" | "ACTIVE" | "SUSPENDED">("ALL");

  // Authentication & Role Protection
  useEffect(() => {
    if (!ready) return;
    if (!session) {
      router.push("/login");
    } else if (session.role !== "ADMIN") {
      router.push(session.role === "DRIVER" ? "/driver" : "/passenger");
    }
  }, [session, ready, router]);

  const fetchDrivers = async () => {
    if (!session || session.role !== "ADMIN") return;
    try {
      const res = await fetch('http://localhost:8082/api/drivers', {
        headers: { Authorization: `Bearer ${session.token}` }
      });
      if (res.ok) {
        const rawData = await res.json();
        const mappedData = rawData.map((d: any) => ({
          id: d.id,
          userId: d.accountId || d.userId || d.id, 
          name: d.name,
          email: d.email, // Using real email from DB 
          vehicleType: d.vehicleType || "N/A",
          status: d.status || "ACTIVE",
          available: !!d.available,
          currentRideId: d.currentRideId || null
        }));
        setDrivers(mappedData);
      } else {
        throw new Error(`Failed to fetch drivers: ${res.status}`);
      }
    } catch (err) {
      console.warn('Error fetching drivers, using mock data:', err);
      // Keep mock data only for absolute failure cases where backend is down
      if (drivers.length === 0) setDrivers(MOCK_DRIVERS);
    }
  };

  // Fetch Drivers initially and poll every 10 seconds
  useEffect(() => {
    if (!session || session.role !== "ADMIN") return;

    fetchDrivers(); // Initial fetch
    
    const interval = setInterval(() => {
      fetchDrivers();
    }, 10000); // 10 seconds polling

    return () => clearInterval(interval);
  }, [session]);

  const handleToggleStatus = async (driver: AdminDriver) => {
    if (!session) return;
    const newStatus = driver.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';

    // Optimistic Update
    setDrivers(prev => prev.map(d => (d.userId === driver.userId ? { ...d, status: newStatus } : d)));

    try {
      // Pass status as a query parameter because AccountController expects @RequestParam
      const res = await fetch(`http://localhost:8081/api/accounts/${driver.userId}/status?status=${newStatus}`, {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${session.token}`
        },
        body: JSON.stringify({ status: newStatus })
      });

      if (!res.ok) {
        throw new Error("Failed to update status");
      }
    } catch (err) {
      console.error('Status update failed:', err);
      // Revert optimistic update
      setDrivers(prev => prev.map(d => (d.userId === driver.userId ? { ...d, status: driver.status } : d)));
      alert('Action failed! Make sure you are authenticated as ADMIN.');
    }
  };

  const handleLogout = () => {
    logout();
    router.push('/login');
  };

  if (!ready || !session || session.role !== "ADMIN") {
    return <div className="min-h-screen bg-[#F8FAFC] flex items-center justify-center text-slate-500 font-medium">Loading Dashboard...</div>;
  }

  const filteredDrivers = drivers.filter(driver => {
    const matchesSearch = 
      driver.name?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      driver.email?.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesStatus = statusFilter === 'ALL' || driver.status === statusFilter;
    return matchesSearch && matchesStatus;
  });

  const totalRegistered = drivers.length;
  const activeCount = drivers.filter(d => d.status === 'ACTIVE').length;
  const suspendedCount = drivers.filter(d => d.status === 'SUSPENDED').length;
  const onlineCount = drivers.filter(d => d.available).length;

  return (
    <div className="flex min-h-screen bg-[#F8FAFC] font-sans antialiased text-slate-800">
      
      {/* 1. SIDEBAR */}
      <aside className="w-64 bg-slate-900 flex flex-col justify-between p-5 text-slate-300 shadow-xl hidden md:flex h-screen sticky top-0">
        <div>
          {/* Brand Logo */}
          <div className="flex items-center gap-3 px-2 py-4 mb-6">
            <div className="w-9 h-9 rounded-xl bg-amber-400 flex items-center justify-center font-black text-slate-950 text-xl shadow-lg shadow-amber-400/20">
              R
            </div>
            <div>
              <h1 className="text-xl font-extrabold tracking-tight text-white flex items-center">
                Ride<span className="text-amber-400">Link</span>
              </h1>
              <span className="text-[10px] uppercase tracking-wider text-slate-400 font-semibold">Admin Workspace</span>
            </div>
          </div>

          {/* Navigation Links */}
          <nav className="space-y-1.5">
            <a href="#" className="flex items-center gap-3 px-4 py-3 text-sm font-semibold rounded-xl bg-amber-400 text-slate-950 shadow-md shadow-amber-400/20 transition-all">
              <LayoutDashboard size={19} />
              <span>Dashboard</span>
            </a>
            <a href="#" className="flex items-center gap-3 px-4 py-3 text-sm font-medium rounded-xl hover:bg-slate-800/80 hover:text-white transition-all">
              <Users size={19} />
              <span>Drivers</span>
            </a>
            <a href="#" className="flex items-center gap-3 px-4 py-3 text-sm font-medium rounded-xl hover:bg-slate-800/80 hover:text-white transition-all">
              <Car size={19} />
              <span>Live Rides</span>
            </a>
            <a href="#" className="flex items-center gap-3 px-4 py-3 text-sm font-medium rounded-xl hover:bg-slate-800/80 hover:text-white transition-all">
              <FileText size={19} />
              <span>Reports</span>
            </a>
            <a href="#" className="flex items-center gap-3 px-4 py-3 text-sm font-medium rounded-xl hover:bg-slate-800/80 hover:text-white transition-all">
              <Settings size={19} />
              <span>Settings</span>
            </a>
          </nav>
        </div>

        {/* User profile footer */}
        <div className="pt-4 border-t border-slate-800/80 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-full bg-amber-400/20 border border-amber-400/40 flex items-center justify-center text-amber-400 font-bold text-sm">
              {session.name ? session.name.substring(0, 2).toUpperCase() : "SA"}
            </div>
            <div className="text-xs">
              <p className="font-semibold text-white max-w-[100px] truncate">{session.name || "System Admin"}</p>
              <p className="text-slate-400">ADMIN</p>
            </div>
          </div>
          <button 
            onClick={handleLogout}
            className="p-2 hover:bg-slate-800 rounded-lg text-slate-400 hover:text-red-400 transition"
            title="Sign out"
          >
            <LogOut size={17} />
          </button>
        </div>
      </aside>

      {/* 2. MAIN CONTENT */}
      <main className="flex-1 flex flex-col p-4 md:p-8 overflow-y-auto">
        
        {/* Header / Search */}
        <header className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-6 border-b border-slate-200/80">
          <div>
            <h2 className="text-2xl font-black tracking-tight text-slate-900">Admin Dashboard</h2>
            <p className="text-sm text-slate-500 font-medium">Manage driver verifications, status, and network activities.</p>
          </div>

          <div className="flex items-center gap-4">
            <div className="relative">
              <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" size={17} />
              <input
                type="text"
                placeholder="Search driver by name or email..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-10 pr-4 py-2.5 w-full md:w-72 text-sm bg-white border border-slate-200 rounded-xl shadow-sm focus:outline-none focus:ring-2 focus:ring-amber-400 focus:border-amber-400 transition"
              />
            </div>
            <button 
              onClick={fetchDrivers}
              className="p-2.5 bg-white border border-slate-200 rounded-xl text-slate-600 hover:text-amber-500 shadow-sm transition"
              title="Refresh Drivers"
            >
              <RefreshCw size={18} />
            </button>
            <button className="p-2.5 bg-white border border-slate-200 rounded-xl text-slate-600 hover:text-slate-900 shadow-sm transition">
              <Bell size={18} />
            </button>
          </div>
        </header>

        {/* 3. OVERVIEW METRICS CARDS */}
        <section className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 my-8">
          
          {/* Total Drivers Card */}
          <div className="relative overflow-hidden rounded-2xl bg-gradient-to-br from-amber-500 via-amber-400 to-amber-300 p-6 text-slate-950 shadow-lg shadow-amber-500/15">
            <div className="flex justify-between items-start relative z-10">
              <div>
                <p className="text-xs uppercase font-bold tracking-wider text-slate-900/70">Total Registered</p>
                <h3 className="text-4xl font-black mt-2 tracking-tight">{totalRegistered}</h3>
              </div>
              <div className="p-3 bg-white/30 backdrop-blur-md rounded-2xl">
                <Users size={24} className="text-slate-950" />
              </div>
            </div>
            <div className="mt-4 flex items-center gap-1 text-xs font-bold text-slate-900 relative z-10">
              <ArrowUpRight size={15} />
              <span>Full fleet</span>
            </div>
            <div className="absolute -bottom-8 -right-8 w-28 h-28 bg-white/20 rounded-full blur-2xl pointer-events-none" />
          </div>

          {/* Active Accounts Card */}
          <div className="relative overflow-hidden rounded-2xl bg-gradient-to-br from-slate-900 to-slate-800 p-6 text-white shadow-lg shadow-slate-900/15">
            <div className="flex justify-between items-start relative z-10">
              <div>
                <p className="text-xs uppercase font-bold tracking-wider text-slate-400">Active Accounts</p>
                <h3 className="text-4xl font-black mt-2 tracking-tight text-white">{activeCount}</h3>
              </div>
              <div className="p-3 bg-emerald-500/20 border border-emerald-500/30 rounded-2xl">
                <CheckCircle2 size={24} className="text-emerald-400" />
              </div>
            </div>
            <div className="mt-4 flex items-center gap-1.5 text-xs font-semibold text-emerald-400 relative z-10">
              <span>Non-suspended accounts</span>
            </div>
          </div>

          {/* Suspended Accounts Card */}
          <div className="relative overflow-hidden rounded-2xl bg-white border border-slate-200/90 p-6 text-slate-900 shadow-sm">
            <div className="flex justify-between items-start">
              <div>
                <p className="text-xs uppercase font-bold tracking-wider text-slate-400">Suspended Accounts</p>
                <h3 className="text-4xl font-black mt-2 tracking-tight text-red-600">{suspendedCount}</h3>
              </div>
              <div className="p-3 bg-red-50 rounded-2xl">
                <XCircle size={24} className="text-red-500" />
              </div>
            </div>
            <div className="mt-4 text-xs font-semibold text-slate-400">
              Accounts restricted
            </div>
          </div>

          {/* Online Now Card */}
          <div className="relative overflow-hidden rounded-2xl bg-white border border-slate-200/90 p-6 text-slate-900 shadow-sm">
            <div className="flex justify-between items-start">
              <div>
                <p className="text-xs uppercase font-bold tracking-wider text-slate-400">Online Now</p>
                <h3 className="text-4xl font-black mt-2 tracking-tight text-emerald-600">{onlineCount}</h3>
              </div>
              <div className="p-3 bg-emerald-50 rounded-2xl">
                <Car size={24} className="text-emerald-500" />
              </div>
            </div>
            <div className="mt-4 flex items-center gap-1.5 text-xs font-semibold text-emerald-600">
              <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
              Ready for dispatch
            </div>
          </div>

        </section>

        {/* 4. DRIVER MANAGEMENT TABLE CARD */}
        <section className="bg-white rounded-2xl border border-slate-200/80 shadow-sm overflow-hidden flex-1">
          
          <div className="p-5 border-b border-slate-100 flex flex-wrap items-center justify-between gap-4 bg-white/50 backdrop-blur-sm">
            <h4 className="font-extrabold text-slate-900 text-lg">Registered Fleet Members</h4>
            <div className="flex items-center gap-3">
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value as any)}
                className="text-xs font-semibold px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-amber-400"
              >
                <option value="ALL">All Statuses</option>
                <option value="ACTIVE">Active Only</option>
                <option value="SUSPENDED">Suspended Only</option>
              </select>
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse whitespace-nowrap">
              <thead>
                <tr className="bg-slate-50/75 border-b border-slate-100 text-[11px] font-bold uppercase tracking-wider text-slate-400">
                  <th className="py-4 px-6">Driver Name</th>
                  <th className="py-4 px-6">Email Address</th>
                  <th className="py-4 px-6">Vehicle Type</th>
                  <th className="py-4 px-6">Account Status</th>
                  <th className="py-4 px-6">Live Status</th>
                  <th className="py-4 px-6 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-sm">
                {filteredDrivers.length > 0 ? (
                  filteredDrivers.map((driver) => (
                    <tr key={driver.id || driver.userId} className="hover:bg-slate-50/60 transition group">
                      <td className="py-4 px-6 font-bold text-slate-900">
                        {driver.name}
                      </td>
                      <td className="py-4 px-6 text-slate-500 font-medium">
                        {driver.email}
                      </td>
                      <td className="py-4 px-6">
                        <span className="px-2.5 py-1 text-xs font-bold rounded-lg bg-slate-100 text-slate-700">
                          {driver.vehicleType || 'Car'}
                        </span>
                      </td>
                      <td className="py-4 px-6">
                        {driver.status === 'ACTIVE' ? (
                          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-emerald-50 text-emerald-600 border border-emerald-200/60">
                            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
                            ACTIVE
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-red-50 text-red-600 border border-red-200/60">
                            <span className="w-1.5 h-1.5 rounded-full bg-red-500"></span>
                            SUSPENDED
                          </span>
                        )}
                      </td>
                      <td className="py-4 px-6">
                        {driver.currentRideId ? (
                          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-bold bg-blue-50 text-blue-600 border border-blue-200">
                            <span className="w-1.5 h-1.5 rounded-full bg-blue-500 animate-pulse"></span>
                            ON TRIP
                          </span>
                        ) : driver.available ? (
                          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-bold bg-emerald-50 text-emerald-600 border border-emerald-200">
                            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
                            ONLINE
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-bold bg-slate-100 text-slate-500 border border-slate-200">
                            <span className="w-1.5 h-1.5 rounded-full bg-slate-400"></span>
                            OFFLINE
                          </span>
                        )}
                      </td>
                      <td className="py-4 px-6 text-right">
                        <button
                          onClick={() => handleToggleStatus(driver)}
                          className={`px-4 py-1.5 text-xs font-bold rounded-xl transition duration-200 shadow-sm ${
                            driver.status === 'ACTIVE'
                              ? 'border border-red-200 text-red-600 hover:bg-red-50'
                              : 'bg-emerald-500 text-white hover:bg-emerald-600 shadow-emerald-500/20'
                          }`}
                        >
                          {driver.status === 'ACTIVE' ? 'Suspend Driver' : 'Activate Driver'}
                        </button>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={6} className="py-12 text-center text-sm text-slate-400">
                      <div className="flex flex-col items-center justify-center">
                        <Users className="w-12 h-12 text-slate-200 mb-3" />
                        <p className="text-slate-500 font-medium">No drivers found matching your search.</p>
                      </div>
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>
      </main>
    </div>
  );
};

export default AdminDashboard;
