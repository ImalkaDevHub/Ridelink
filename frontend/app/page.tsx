import Link from "next/link";
import NavBar from "@/components/NavBar";

export default function LandingPage() {
  return (
    <div>
      <div style={{ minHeight: "100vh", display: "flex", flexDirection: "column", backgroundImage: 'url("/image/im1.webp")', backgroundSize: "cover", backgroundPosition: "center" }}>
        <NavBar label="/" />

        <header
          style={{
            display: "flex",
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

          <div style={{ position: "relative", maxWidth: 620 }}>
            <h1
              style={{
                fontFamily: "var(--font-heading)",
                fontWeight: 700,
                fontSize: "clamp(30px,4.4vw,52px)",
                lineHeight: 1.02,
                letterSpacing: -1.4,
                margin: "0 0 14px",
              }}
            >
              A dispatch board
              <br />
              for your city.
            </h1>
            <p style={{ fontSize: 15.5, lineHeight: 1.6, color: "var(--muted)", margin: "0 0 28px", maxWidth: "44ch" }}>
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
                color: "#14161a",
                borderRadius: 8,
                padding: "13px 22px",
              }}
            >
              Get started
            </Link>
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
