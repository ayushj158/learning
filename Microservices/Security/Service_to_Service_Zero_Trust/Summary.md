## 1️⃣4️⃣ Interview-Perfect Summary (MEMORISE)

> *“Service-to-service security follows a zero-trust model. Mutual TLS provides strong, non-forgeable service identity and encrypted transport, while OAuth provides fine-grained, auditable authorization through scoped tokens. mTLS alone is insufficient for authorization, and OAuth alone is weaker without transport identity. Together, they prevent impersonation, limit lateral movement, and contain blast radius — which is critical in financial systems.”*

---

# 🔐 Service-to-Service Security

## **Zero Trust • Microservices • Financial Services (FAST-READ STRUCTURE)**

---

## 1️⃣ Problem Statement

### Reality of Microservices

* Network is **untrusted**
* Traffic is **east–west**
* Perimeter security is **gone**

### Two Separate Questions (Never Mix)

| Question        | Concern                       |
| --------------- | ----------------------------- |
| Who is calling? | **Authentication / Identity** |
| Is it allowed?  | **Authorization**             |

---

## 2️⃣ Zero Trust Principle (FOUNDATION)

**Rule**

> Never trust the network. Always verify the caller.

**Implications**

* “Internal” ≠ trusted
* IPs / subnets / DNS ≠ identity
* **Every request must authenticate + authorize**

---

## 3️⃣ mTLS — CAPABILITIES vs LIMITS

### What mTLS PROVIDES

| Capability             | Outcome                        |
| ---------------------- | ------------------------------ |
| Cryptographic identity | Non-forgeable service identity |
| Encrypted transport    | Confidentiality                |
| Mutual authentication  | MITM protection                |
| Cert-based identity    | Strong service proof           |

**Answers**

> **“WHO is calling?”**

---

### What mTLS DOES NOT PROVIDE

| Missing                | Why                   |
| ---------------------- | --------------------- |
| Business authorization | No permission model   |
| Fine-grained access    | No scopes / policies  |
| User intent            | Service-only identity |

> **mTLS ≠ authorization**

---

## 4️⃣ Service Identity (How Identity Is Established)

### Certificate-Based Identity

* Each service has **unique certificate**
* Issued by **trusted internal CA**
* Identity embedded in:

  * SAN
  * SPIFFE ID

**Example**

```
spiffe://bank/prod/payments-service
```

### Identity Properties

* Cannot be spoofed
* Short-lived
* Automatically rotated

---

## 5️⃣ OAuth — Service Authorization Layer

### What OAuth Answers

> **“WHAT is this service allowed to do?”**

### Typical Pattern

* **Client Credentials flow**
* Access token represents:

  * Service identity
  * Allowed scopes

### Receiving Service Responsibilities

* Validate token signature
* Validate audience
* Validate scopes

---

## 6️⃣ Why mTLS + OAuth MUST Be Combined (KEY TABLE)

| Concern            | mTLS | OAuth |
| ------------------ | ---- | ----- |
| Service identity   | ✅    | ❌     |
| Authorization      | ❌    | ✅     |
| Transport security | ✅    | ❌     |
| Least privilege    | ❌    | ✅     |
| Defense in depth   | ❌    | ❌     |
| **Combined**       | ✅    | ✅     |

🔥 **Interview line**

> *“mTLS gives identity; OAuth gives authority.”*

---

## 7️⃣ Threats & Mitigations (MAP VIEW)

### Service Impersonation

* mTLS → prevents identity forgery
* OAuth → prevents unauthorized scopes

### Lateral Movement

* mTLS → explicit trust graph
* OAuth → scoped API access

### MITM Attacks

* mTLS → encryption + mutual authentication

### Token Replay / Amplification

* Short-lived tokens
* Audience-restricted tokens
* Bound to verified service identity

### Rogue Internal Clients

* No certificate → TLS handshake fails
* No token → authorization fails

---

## 8️⃣ Critical Maturity Insight (DO NOT SKIP)

**Security does NOT prevent all abuse**

If a service is compromised:

* ✅ Can abuse **existing permissions**
* ❌ Cannot access **permissions it never had**

**Security goal**

> **Blast-radius containment**, not perfection

---

## 9️⃣ Token Propagation vs Token Exchange

| Pattern                 | Characteristics      | Risk                 |
| ----------------------- | -------------------- | -------------------- |
| Token propagation       | Same token forwarded | ❌ Large blast radius |
| **Token exchange (FS)** | New token per hop    | ✅ Least privilege    |

🔥 *“Token exchange limits damage even inside trusted paths.”*

---

## 🔟 Service Mesh vs Application Layer (CLEAR SPLIT)

### Service Mesh Responsibilities

* Automatic mTLS
* Certificate rotation
* Central transport policy

### Application Responsibilities

* OAuth token validation
* Scope checks
* Business authorization (ABAC)

**FS Reality**

> Mesh = transport identity
> App = authorization logic

---

## 1️⃣1️⃣ Enforcement Layers (STACK VIEW)

| Layer          | Enforces                      |
| -------------- | ----------------------------- |
| Network        | TLS                           |
| Mesh / Sidecar | mTLS                          |
| Application    | OAuth validation              |
| Domain         | Business authorization (ABAC) |

> **No single layer is sufficient**

---

## 1️⃣2️⃣ Common Anti-Patterns ❌

* Trusting internal network
* IP allowlists as identity
* mTLS without OAuth
* Long-lived service tokens
* One token usable everywhere

---

## 1️⃣3️⃣ Why Financial Services Demand This

**Regulatory Expectations**

* Explicit service identity
* Least privilege
* Contained blast radius
* Strong auditability

**mTLS + OAuth**

* Enable zero trust
* Support audits
* Reduce breach impact

---

## 1️⃣5️⃣ Final Mental Model (LOCK THIS)

```
mTLS  → Who are you?
OAuth → What can you do?
ABAC  → Are you allowed right now?
```

---

### ✅ You’re SOLID if you can explain

* Why **mTLS ≠ authorization**
* Why **OAuth alone is insufficient**
* Why **compromise ≠ full access**
* How **blast radius is contained**
* Why **token exchange matters**