import Link from "next/link";
import NavBar from "@/components/NavBar";

export default function LandingPage() {
  return (
    <div>
      <div style={{ minHeight: "100vh", display: "flex", flexDirection: "column", backgroundImage: 'url("/image/im1.webp")', backgroundSize: "cover", backgroundPosition: "center bottom" }}>
        <NavBar label="/" />

        <header
          className="desktop-only"
          style={{
            alignItems: "center",
            gap: 28,
            padding: "22px 40px",
            flexWrap: "wrap",
          }}
        >
          <div style={{ marginLeft: "auto", display: "flex", alignItems: "center", gap: 12 }}>
            <Link href="/login" style={{ fontFamily: "var(--font-heading)", fontSize: 13.5, fontWeight: 500, color: "var(--text)" }}>
              Sign in
            </Link>
            <Link
              href="/login"
              style={{
                fontFamily: "var(--font-heading)",
                fontSize: 13.5,
                fontWeight: 600,
                background: "var(--accent)",
                color: "#14161a",
                borderRadius: 7,
                padding: "11px 18px",
              }}
            >
              Request a ride
            </Link>
          </div>
        </header>

        <section
          style={{
            position: "relative",
            overflow: "hidden",
            borderBottom: "1px solid var(--line)",
            padding: "72px 40px 88px",
            flex: 1,
            display: "flex",
            flexDirection: "column",
            justifyContent: "center",
          }}
        >
          <svg
            className="desktop-only"
            viewBox="0 0 1200 400"
            preserveAspectRatio="xMidYMid slice"
            style={{ position: "absolute", inset: 0, width: "100%", height: "100%", pointerEvents: "none", zIndex: 0 }}
            aria-hidden="true"
          >
            <defs>
              <mask id="dash-mask">
                <path
                  d="M120 100 Q 250 20 400 60 T 650 40 T 900 80 T 1100 140"
                  fill="none"
                  stroke="white"
                  strokeWidth="10"
                  strokeLinecap="round"
                  strokeDasharray="1400"
                  style={{ animation: "rl-draw 4s cubic-bezier(.65,.05,.36,1) forwards" }}
                />
              </mask>
            </defs>
            <path
              d="M120 100 Q 250 20 400 60 T 650 40 T 900 80 T 1100 140"
              fill="none"
              stroke="var(--accent)"
              strokeWidth="3.5"
              strokeLinecap="round"
              strokeDasharray="12 12"
              mask="url(#dash-mask)"
            />
            <g>
              <g transform="translate(-12, -12)">
                <path d="M 4,10 L 7,6 L 15,6 L 18,10 L 22,10 L 22,14 L 19,14 A 2.5,2.5 0 0,1 14,14 L 10,14 A 2.5,2.5 0 0,1 5,14 L 2,14 L 2,10 Z" fill="var(--accent)" />
                <circle cx="7.5" cy="14" r="2" fill="var(--bg)" />
                <circle cx="16.5" cy="14" r="2" fill="var(--bg)" />
              </g>
              <animateMotion
                dur="4s"
                fill="freeze"
                calcMode="spline"
                keySplines=".65 .05 .36 1"
                keyTimes="0;1"
                path="M120 100 Q 250 20 400 60 T 650 40 T 900 80 T 1100 140"
                rotate="auto"
              />
            </g>
          </svg>

          <div style={{ position: "relative", maxWidth: 620, zIndex: 1 }}>
            <h1
              style={{
                fontFamily: "var(--font-heading)",
                fontWeight: 700,
                fontSize: "clamp(26px,6vw,52px)",
                lineHeight: 1.02,
                letterSpacing: -1.4,
                margin: "0 0 14px",
              }}
            >
              A dispatch board
              <br />
              for your city.
            </h1>
            <div className="hero-text-card" style={{ maxWidth: "44ch" }}>
              <p
                style={{
                  fontSize: 15.5,
                  lineHeight: 1.625,
                  color: "#111827",
                  margin: "0 0 28px",
                  fontWeight: 500,
                }}
              >
                RideLink matches passengers with drivers who are online right now. You see the fare
                estimate before you confirm, and the ride&apos;s status while it happens.
              </p>
              <Link
                href="/login"
                style={{
                  display: "inline-block",
                  fontFamily: "var(--font-heading)",
                  fontSize: 14,
                  fontWeight: 600,
                  background: "var(--accent)",
                  color: "#111111",
                  borderRadius: 8,
                  padding: "13px 22px",
                  textAlign: "center",
                  width: "100%",
                }}
              >
                Get started
              </Link>
            </div>
          </div>
        </section>
      </div>

      <section style={{ padding: "64px 40px", borderBottom: "1px solid var(--line)" }}>
        <h2 style={{ fontFamily: "var(--font-heading)", fontSize: 24, fontWeight: 600, letterSpacing: -0.5, margin: "0 0 32px" }}>
          How a RideLink trip runs
        </h2>
        <ol
          style={{
            listStyle: "none",
            margin: 0,
            padding: 0,
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit,minmax(210px,1fr))",
            gap: 1,
            background: "var(--line)",
            border: "1px solid var(--line)",
            borderRadius: 10,
            overflow: "hidden",
          }}
        >
          {[
            { n: "01", title: "Request", body: "Enter pickup, destination, and trip distance/time; see the fare estimate before you confirm." },
            { n: "02", title: "Assign", body: "Ride Service asks Driver Service for anyone online nearby and reserves them for you." },
            { n: "03", title: "Ride", body: "The ride's status moves from assigned to completed as the driver marks it done." },
            { n: "04", title: "Settle", body: "Completion records a real payment in Fare & Payment Service and files a receipt." },
          ].map((step) => (
            <li key={step.n} style={{ background: "var(--bg)", padding: 22 }}>
              <div style={{ fontFamily: "var(--font-heading)", fontSize: 12, fontWeight: 700, color: "var(--accent)", marginBottom: 10 }}>
                {step.n}
              </div>
              <div style={{ fontFamily: "var(--font-heading)", fontSize: 16, fontWeight: 600, marginBottom: 6 }}>{step.title}</div>
              <p style={{ margin: 0, fontSize: 13.5, lineHeight: 1.55, color: "var(--muted)" }}>{step.body}</p>
            </li>
          ))}
        </ol>
      </section>

      <section style={{ padding: "64px 40px", borderBottom: "1px solid var(--line)" }}>
        {/* Top Header */}
        <div style={{ display: "flex", flexWrap: "wrap", justifyContent: "space-between", alignItems: "center", gap: "20px", marginBottom: "32px" }}>
          <h2 style={{ fontFamily: "var(--font-heading)", fontSize: "clamp(24px, 3vw, 36px)", fontWeight: 700, textTransform: "uppercase", margin: 0, color: "var(--text)" }}>
            DRIVE YOUR JOURNEY, YOUR WAY.
          </h2>
          <p style={{ fontSize: "14px", color: "var(--muted)", maxWidth: "400px", margin: 0, textAlign: "right" }}>
            Explore our carefully selected collection of cars, bikes, and scooters, available for self-drive or chauffeur service, designed to suit every journey and budget.
          </p>
        </div>

        {/* Card Grid */}
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))", gap: "16px" }}>
          {[
            { title: "Car", count: "2354 available", bg: "https://images.unsplash.com/photo-1549317661-bd32c8ce0db2?auto=format&fit=crop&w=500&q=60" },
            { title: "Bike", count: "2354 available", bg: "https://images.unsplash.com/photo-1558981403-c5f9899a28bc?auto=format&fit=crop&w=500&q=60" },
            { title: "Van", count: "842 available", bg: "/image/van.jpg" },
            { title: "Bus", count: "128 available", bg: "https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?auto=format&fit=crop&w=500&q=60" },
          ].map((cat) => (
            <div key={cat.title} style={{ position: "relative", borderRadius: "12px", overflow: "hidden", height: "256px", backgroundImage: `url(${cat.bg})`, backgroundSize: "cover", backgroundPosition: "center" }}>
              {/* Gradient Overlay */}
              <div style={{ position: "absolute", inset: 0, background: "linear-gradient(to top, rgba(0,0,0,0.9) 0%, rgba(0,0,0,0.5) 40%, transparent 100%)" }} />
              
              {/* Content Box */}
              <div style={{ position: "absolute", bottom: "16px", left: "16px", right: "16px", display: "flex", justifyContent: "space-between", alignItems: "flex-end" }}>
                <div>
                  <div style={{ color: "#fff", fontWeight: 700, fontFamily: "var(--font-heading)", fontSize: "18px", marginBottom: "4px" }}>{cat.title}</div>
                  <div style={{ color: "#ccc", fontSize: "12px" }}>{cat.count}</div>
                </div>
                
                {/* Arrow Button */}
                <div style={{ width: "32px", height: "32px", background: "#fff", borderRadius: "4px", display: "flex", justifyContent: "center", alignItems: "center", cursor: "pointer" }}>
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#000" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <polyline points="9 18 15 12 9 6"></polyline>
                  </svg>
                </div>
              </div>
            </div>
          ))}
        </div>
      </section>

      <section
        style={{
          padding: "56px 40px",
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit,minmax(280px,1fr))",
          gap: 40,
        }}
      >
        <div>
          <h2 style={{ fontFamily: "var(--font-heading)", fontSize: 22, fontWeight: 600, letterSpacing: -0.4, margin: "0 0 10px" }}>
            Driving with RideLink
          </h2>
          <p style={{ margin: "0 0 18px", fontSize: 14.5, lineHeight: 1.6, color: "var(--muted)", maxWidth: "46ch" }}>
            Go online when you want work. When a passenger requests a ride, Ride Service reserves
            the first driver Driver Service reports as available.
          </p>
          <Link
            href="/login"
            style={{
              display: "inline-block",
              fontFamily: "var(--font-heading)",
              fontSize: 13.5,
              fontWeight: 600,
              background: "transparent",
              color: "var(--accent)",
              border: "1px solid var(--accent)",
              borderRadius: 7,
              padding: "11px 16px",
            }}
          >
            Open driver panel
          </Link>
        </div>
        <div style={{ border: "1px solid var(--line)", borderRadius: 10, padding: 20 }}>
          <div style={{ fontFamily: "var(--font-heading)", fontSize: 12, fontWeight: 600, color: "var(--muted)", marginBottom: 14 }}>
            System status
          </div>
          <div style={{ display: "grid", gap: 10, fontSize: 14 }}>
            {[
              ["Account Service", "8081"],
              ["Driver Service", "8082"],
              ["Ride Service", "8083"],
              ["Fare & Payment Service", "8084"],
            ].map(([name, port]) => (
              <div
                key={name}
                style={{ display: "flex", justifyContent: "space-between", paddingBottom: 10, borderBottom: "1px solid var(--line)" }}
              >
                <span>{name}</span>
                <span style={{ color: "var(--muted)", fontFamily: "var(--font-heading)" }}>:{port}</span>
              </div>
            ))}
          </div>
        </div>
      </section>

      <footer style={{ padding: "28px 40px", display: "flex", gap: 18, flexWrap: "wrap", alignItems: "center", fontSize: 12.5, color: "var(--muted)" }}>
        <span style={{ fontFamily: "var(--font-heading)", fontWeight: 600, color: "var(--text)" }}>RideLink</span>
        <span>Dispatch operations &middot; IT3130 microservices project</span>
      </footer>
    </div>
  );
}
