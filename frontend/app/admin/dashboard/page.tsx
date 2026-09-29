"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import NavBar from "@/components/NavBar";
import { useAuth } from "@/lib/auth";

interface AdminDriver {
  id: string; // The ID from driver-service
  userId: string; // The ID from account-service
  name: string;
  email: string;
  vehicleType: string;
  status: "ACTIVE" | "SUSPENDED";
}

// Fallback mock data in case backend is offline
const MOCK_DRIVERS: AdminDriver[] = [
  { id: "d1", userId: "u1", name: "John Doe", email: "john@example.com", vehicleType: "Car", status: "ACTIVE" },
  { id: "d2", userId: "u2", name: "Jane Smith", email: "jane@example.com", vehicleType: "Van", status: "SUSPENDED" },
  { id: "d3", userId: "u3", name: "Bob Johnson", email: "bob@example.com", vehicleType: "Bike", status: "ACTIVE" },
];

export default function AdminDashboard() {
  const router = useRouter();
  const { session, ready } = useAuth();
  
  const [drivers, setDrivers] = useState<AdminDriver[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [filterStatus, setFilterStatus] = useState<"ALL" | "ACTIVE" | "SUSPENDED">("ALL");
  const [toast, setToast] = useState<{ message: string, type: "success" | "error" } | null>(null);

  // Authentication & Role Protection
  useEffect(() => {
    if (!ready) return;
    if (!session) {
      router.push("/login");
    } else if (session.role !== "ADMIN") {
      // Non-admins shouldn't access this route
      router.push(session.role === "DRIVER" ? "/driver" : "/passenger");
    }
  }, [session, ready, router]);

  // Fetch Drivers from API
  useEffect(() => {
    if (!session || session.role !== "ADMIN") return;

    const fetchDrivers = async () => {
      try {
        const res = await fetch("http://localhost:8082/api/drivers", {
          headers: { Authorization: `Bearer ${session.token}` }
        });
        if (res.ok) {
          const data = await res.json();
          setDrivers(data);
        } else {
          throw new Error(`Failed to fetch drivers: ${res.status}`);
        }
      } catch (err) {
        console.warn("Backend offline or failed to fetch, using mock data.", err);
        setDrivers(MOCK_DRIVERS);
      } finally {
        setLoading(false);
      }
    };
    
    fetchDrivers();
  }, [session]);

  const handleStatusToggle = async (userId: string, currentStatus: "ACTIVE" | "SUSPENDED") => {
    if (!session) return;
    const newStatus = currentStatus === "ACTIVE" ? "SUSPENDED" : "ACTIVE";
    
    // Optimistic update
    setDrivers(prev => prev.map(d => d.userId === userId ? { ...d, status: newStatus } : d));

    try {
      const res = await fetch(`http://localhost:8081/api/accounts/${userId}/status`, {
        method: "PATCH",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${session.token}`
        },
        body: JSON.stringify({ status: newStatus })
      });

      if (!res.ok) {
        throw new Error("Failed to update status");
      }
      showToast(`Driver status updated to ${newStatus}`, "success");
    } catch (err) {
      console.error(err);
      // Revert optimistic update on error
      setDrivers(prev => prev.map(d => d.userId === userId ? { ...d, status: currentStatus } : d));
      showToast("Failed to update status. Please try again.", "error");
    }
  };

  const showToast = (message: string, type: "success" | "error") => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 3000);
  };

  if (!ready || !session || session.role !== "ADMIN") {
    // Show a blank screen or a loading state while redirecting
    return <div className="min-h-screen bg-gray-50 flex items-center justify-center">Loading...</div>;
  }

  // Filter & Search Logic
  const filteredDrivers = drivers.filter(d => {
    const matchesSearch = (d.name || "").toLowerCase().includes(search.toLowerCase()) || 
                          (d.email || "").toLowerCase().includes(search.toLowerCase());
    const matchesFilter = filterStatus === "ALL" || d.status === filterStatus;
    return matchesSearch && matchesFilter;
  });

  const totalDrivers = drivers.length;
  const activeDrivers = drivers.filter(d => d.status === "ACTIVE").length;
  const suspendedDrivers = drivers.filter(d => d.status === "SUSPENDED").length;

  return (
    <div className="min-h-screen bg-gray-50 text-gray-900 font-sans">
      <NavBar label="Admin Dashboard" />
      
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-gray-900 tracking-tight">Admin Dashboard</h1>
          <p className="text-gray-500 mt-1">Manage driver verifications and account statuses</p>
        </div>
        
        {/* Toast Notification */}
        {toast && (
          <div className={`fixed top-20 right-4 px-6 py-3 rounded-lg shadow-lg z-50 text-white font-medium transition-all duration-300 transform translate-y-0 opacity-100 ${
            toast.type === "success" ? "bg-green-600" : "bg-red-600"
          }`}>
            {toast.message}
          </div>
        )}

        {/* Top Metrics Cards */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
          <MetricCard title="Total Registered" value={totalDrivers} color="bg-blue-100 text-blue-800" />
          <MetricCard title="Active Drivers" value={activeDrivers} color="bg-green-100 text-green-800" />
          <MetricCard title="Suspended Drivers" value={suspendedDrivers} color="bg-red-100 text-red-800" />
        </div>

        {/* Search & Filter Bar */}
        <div className="flex flex-col md:flex-row gap-4 mb-6 items-center justify-between bg-white p-4 rounded-xl shadow-sm border border-gray-100">
          <div className="w-full md:w-1/3 relative">
            <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
              <svg className="h-5 w-5 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </div>
            <input 
              type="text" 
              placeholder="Search by name or email..." 
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full pl-10 pr-4 py-2 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-yellow-400 focus:border-transparent transition-all"
            />
          </div>
          <div className="w-full md:w-auto">
            <select 
              value={filterStatus}
              onChange={(e) => setFilterStatus(e.target.value as any)}
              className="w-full px-4 py-2 border border-gray-200 rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-yellow-400 focus:border-transparent transition-all cursor-pointer"
            >
              <option value="ALL">All Statuses</option>
              <option value="ACTIVE">Active</option>
              <option value="SUSPENDED">Suspended</option>
            </select>
          </div>
        </div>

        {/* Driver Management Table */}
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
          {loading ? (
            <div className="p-12 text-center">
              <div className="inline-block animate-spin w-8 h-8 border-4 border-gray-200 border-t-yellow-400 rounded-full mb-4"></div>
              <div className="text-gray-500 font-medium">Loading drivers...</div>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse whitespace-nowrap">
                <thead className="bg-gray-50/80 border-b border-gray-200">
                  <tr>
                    <th className="py-4 px-6 text-xs font-semibold text-gray-500 uppercase tracking-wider">Driver Name</th>
                    <th className="py-4 px-6 text-xs font-semibold text-gray-500 uppercase tracking-wider">Email</th>
                    <th className="py-4 px-6 text-xs font-semibold text-gray-500 uppercase tracking-wider">Vehicle Type</th>
                    <th className="py-4 px-6 text-xs font-semibold text-gray-500 uppercase tracking-wider">Status</th>
                    <th className="py-4 px-6 text-xs font-semibold text-gray-500 uppercase tracking-wider text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {filteredDrivers.length > 0 ? (
                    filteredDrivers.map(driver => (
                      <tr key={driver.id || driver.userId} className="hover:bg-gray-50/50 transition-colors">
                        <td className="py-4 px-6 font-medium text-gray-900">{driver.name}</td>
                        <td className="py-4 px-6 text-gray-500">{driver.email}</td>
                        <td className="py-4 px-6 text-gray-700">
                          <span className="inline-flex items-center px-2.5 py-0.5 rounded-md text-sm font-medium bg-gray-100 text-gray-800">
                            {driver.vehicleType || "N/A"}
                          </span>
                        </td>
                        <td className="py-4 px-6">
                          <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold border ${
                            driver.status === "ACTIVE" 
                              ? "bg-green-50 text-green-700 border-green-200" 
                              : "bg-red-50 text-red-700 border-red-200"
                          }`}>
                            {driver.status === "ACTIVE" ? (
                              <svg className="w-3 h-3 mr-1" fill="currentColor" viewBox="0 0 8 8"><circle cx="4" cy="4" r="3" /></svg>
                            ) : (
                              <svg className="w-3 h-3 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" /></svg>
                            )}
                            {driver.status}
                          </span>
                        </td>
                        <td className="py-4 px-6 text-right">
                          <button 
                            onClick={() => handleStatusToggle(driver.userId, driver.status)}
                            className={`inline-flex items-center justify-center px-4 py-2 rounded-lg text-sm font-medium transition-all shadow-sm focus:outline-none focus:ring-2 focus:ring-offset-2 ${
                              driver.status === "ACTIVE" 
                                ? "bg-white text-red-600 border border-red-200 hover:bg-red-50 focus:ring-red-500" 
                                : "bg-white text-green-600 border border-green-200 hover:bg-green-50 focus:ring-green-500"
                            }`}
                          >
                            {driver.status === "ACTIVE" ? "Suspend Driver" : "Activate Driver"}
                          </button>
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan={5} className="py-12 text-center text-gray-500">
                        <svg className="mx-auto h-12 w-12 text-gray-300 mb-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4" />
                        </svg>
                        <p className="text-lg font-medium text-gray-900">No drivers found</p>
                        <p className="text-gray-500 mt-1">Try adjusting your search or filter criteria.</p>
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}

function MetricCard({ title, value, color }: { title: string, value: number, color: string }) {
  return (
    <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-100 flex items-center justify-between transition-transform hover:-translate-y-1 duration-300">
      <div>
        <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">{title}</p>
        <p className="text-3xl font-bold mt-2 text-gray-900">{value}</p>
      </div>
      <div className={`p-4 rounded-full ${color} bg-opacity-20`}>
        <svg className={`w-8 h-8 ${color.split(" ")[1]}`} fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
          {title.includes("Total") && <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />}
          {title.includes("Active") && <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />}
          {title.includes("Suspended") && <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />}
        </svg>
      </div>
    </div>
  );
}
