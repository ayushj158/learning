# 🔐 Service-to-Service Security
## Zero Trust • Microservices

---

## 1️⃣ Core Problem 

**In microservices:**
- The network is untrusted
- Traffic is east–west
- Perimeter security is gone

**The core questions are:**
- **Who is calling this service?** (Authentication)
- **Is it allowed to do this operation?** (Authorization)

> These are separate concerns.
---

## 2️⃣ Zero Trust Principle (Foundational)

**Rule:** Never trust the network. Always verify the caller.

**Implications:**
- "Internal" ≠ trusted
- IPs, subnets, DNS ≠ identity
- Every call must authenticate AND authorize
---

## 3️⃣ mTLS — What It Solves (and What It Doesn't)

### ✔ What mTLS Provides

- Cryptographic service identity
- Encrypted transport
- Protection against MITM
- Non-forgeable caller identity

**mTLS answers:** "WHO is calling?"

### ❌ What mTLS Does NOT Provide

- Business authorization
- Fine-grained permissions
- User-level intent

**Key Point:** mTLS ≠ authorization
---

## 4️⃣ Service Identity via Certificates

**Each service has:**
- A unique certificate issued by trusted internal CA
- Identity embedded in cert (SAN / SPIFFE ID)

**Example:**
```
spiffe://bank/prod/payments-service
```

**This identity:**
- Cannot be spoofed
- Is short-lived
- Is rotated automatically
---

## 5️⃣ OAuth for Service-to-Service Authorization

**OAuth answers:** "WHAT is this service allowed to do?"

### Typical Flow

**Client Credentials Flow:**
- Access token represents:
  - Service identity
  - Allowed scopes

**Service B validates:**
- Token validity
- Scopes / audience claims

---

## 6️⃣ Why mTLS + OAuth Must Be Combined (Critical)

| Concern | mTLS | OAuth |
|---------|------|-------|
| Service identity | ✅ | ❌ |
| Authorization | ❌ | ✅ |
| Transport security | ✅ | ❌ |
| Least privilege | ❌ | ✅ |
| Defense in depth | ❌ | ❌ |
| **Together** | ✅ | ✅ |

**Key Line:** "mTLS gives identity; OAuth gives authority."
Together
✅
✅
Key line:
“mTLS gives identity; OAuth gives authority.”
---

## 7️⃣ Threats & How They Are Mitigated

### ✔ Service Impersonation

**What it is:** A rogue or compromised service pretends to be a legitimate service (e.g., payments-service) to access protected resources or APIs it shouldn't.

**How it's mitigated:**
- **mTLS** prevents forging service identity - each cert is unique and cryptographically tied to a service identity (SPIFFE ID)
- **OAuth** prevents unauthorized scopes - even if identity is spoofed, the service cannot claim scopes it wasn't granted
- **Defense:** TLS handshake fails if cert is forged; token scope validation fails if unauthorized

**Who defends:** mTLS at network layer (mesh/sidecar), OAuth at application layer

---

### ✔ Lateral Movement

**What it is:** An attacker compromises one service and uses it as a pivot point to access other services in the network, spreading the blast radius.

**How it's mitigated:**
- **mTLS** enforces explicit trust graph - only explicitly allowed service-to-service paths work
- **OAuth scopes** limit reachable APIs - a compromised service can only call what its token scopes permit
- **Defense:** Unknown service cannot establish mTLS; overprivileged token requests are rejected

**Who defends:** Network policy (service mesh), token scope enforcement (OAuth provider)

---

### ✔ MITM Attacks

**What it is:** An attacker intercepts service-to-service traffic to eavesdrop, modify requests, or inject malicious payloads.

**How it's mitigated:**
- **mTLS encrypts traffic** end-to-end between services
- **Mutual authentication blocks interception** - attacker cannot complete TLS handshake without valid cert
- **Defense:** Traffic is encrypted; MITM cannot read or modify undetected

**Who defends:** TLS layer with mutual certificate validation (mesh/sidecar)

---

### ✔ Token Replay Amplification

**What it is:** An attacker captures a valid token and replays it to gain unauthorized access, or uses it in ways not intended (e.g., wrong audience, downstream abuse).

**How it's mitigated:**
- **mTLS binds token use** to a verified service identity - token is only valid when presented by the correct mTLS cert
- **OAuth tokens are short-lived** (e.g., 5 min) - stolen token has limited window
- **Audience-restricted tokens** - token for Service B cannot be used to call Service C
- **Defense:** Replayed token fails mTLS binding; expired token is rejected; wrong audience claim rejected

**Who defends:** mTLS at network layer (cert binding), OAuth provider (TTL + audience), app layer (token validation)

---

### ✔ Rogue Internal Clients

**What it is:** An unauthorized or rogue process/service inside the network tries to call protected APIs without proper identity or permissions.

**How it's mitigated:**
- **No cert** → TLS handshake fails (cannot establish mTLS connection)
- **No token** → authorization fails (missing or invalid OAuth token)
- **Defense:** Dual check: must pass mTLS AND have valid OAuth token

**Who defends:** Infrastructure (mTLS enforcement), application (token validation)
---

## 8️⃣ Very Important Nuance (Maturity Signal)

Service-to-service security does **NOT** prevent abuse of legitimate permissions.

**If a service is compromised:**
- ✅ It can abuse X, Y, Z (what it already had)
- ✅ It cannot access A, B, C (what it never had)

**Security goal:** Blast-radius containment, not perfection.
---

## 9️⃣ Token Propagation vs Token Exchange

### Token Propagation
- Same token forwarded downstream
- Simple
- Larger blast radius

### Token Exchange (Preferred in FS)
- New token per hop
- Audience-specific
- Least privilege

**Key Point:** "Token exchange limits damage even within allowed dependencies."
---

## 🔟 Service Mesh vs App-Level Enforcement

| Approach | Capabilities |
|----------|--------------|
| **Service Mesh** | ✔ Automatic mTLS<br>✔ Central policy<br>✔ Cert rotation |
| **Application Layer** | ✔ Explicit OAuth validation<br>✔ Business authorization |

**FS Reality:** Use mesh for transport identity, app for authorization.
---

## 1️⃣1️⃣ Where Enforcement Happens (Layered)

| Layer | Responsibility |
|-------|-----------------|
| Network | TLS |
| Mesh / Sidecar | mTLS |
| Application | OAuth validation |
| Domain | Business authorization (ABAC) |

**Important:** No single layer is sufficient.
---

## 1️⃣2️⃣ Common Anti-Patterns (Call These Out)

- ❌ Trusting internal network
- ❌ IP allowlists as identity
- ❌ mTLS without OAuth
- ❌ Long-lived service tokens
- ❌ One token usable everywhere

---

## 1️⃣3️⃣ Financial Services Why

**Regulators expect:**
- Explicit service identity
- Least privilege
- Contained blast radius
- Strong auditability

**mTLS + OAuth:**
- Satisfy zero trust
- Enable audits
- Reduce breach impact
---

## 1️⃣4️⃣ Interview-Perfect Summary (Memorise)

> "Service-to-service security in microservices follows a zero-trust model. Mutual TLS provides strong, non-forgeable service identity and encrypted transport, while OAuth provides fine-grained, auditable authorization through scoped tokens. mTLS alone is insufficient for authorization, and OAuth alone is weaker without transport identity. Combining both prevents impersonation, limits lateral movement, and contains blast radius, which is critical in financial systems."
---

## 1️⃣5️⃣ Final Mental Model (Lock This In)

- **mTLS** → Who are you?
- **OAuth** → What can you do?
- **ABAC** → Are you allowed right now?

### ✅ You're Solid If You Can Explain:

- ✔ Why mTLS ≠ authorization
- ✔ Why OAuth alone is insufficient
- ✔ Why compromise ≠ full access
- ✔ How blast radius is contained
- ✔ Why token exchange matters