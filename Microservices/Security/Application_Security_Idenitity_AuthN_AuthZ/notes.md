# 🔐 Application Security
## Identity • Authentication • Authorization

---

## Core Concepts

**Authentication** = Who are you?  
**Authorization** = What are you allowed to do?

> OAuth2, OIDC, JWTs, scopes — everything fits into this model.

---

## 🎯 Key Takeaways

1. We use **Authorization Code flow** for user-facing apps and **Client Credentials** for service-to-service calls.
2. Access tokens are **short-lived and scoped**, refresh tokens are **protected**, and token choice depends on revocation needs.
3. For authorization, we prefer **ABAC over RBAC** to support fine-grained, context-aware decisions.
4. We always enforce **data-level authorization** inside services.

**Security is layered:**
```
Identity → Tokens → API Authorization → Domain Authorization
```

---

## 1️⃣ OAuth 2.0 — What & WHY (Very Important)

### What OAuth 2.0 Actually Is

**OAuth 2.0 is an authorization framework, NOT authentication.**

It answers: **"Can this client access this resource on behalf of someone?"**

### Why OAuth Exists (Problem It Solves)

**Before OAuth:**
- ❌ Apps shared usernames/passwords
- ❌ No fine-grained access
- ❌ No revocation

**OAuth provides:**
- ✔ Delegated access
- ✔ Scoped permissions
- ✔ Revocable tokens

**What OAuth defines:**
- A framework for delegated authorization
- How clients obtain access tokens
- Does NOT define authentication

**🔥 Banking relevance:** Open Banking, partner access, mobile apps.

### What OAuth Issues

- Access Token (JWT or opaque)
- Refresh Token (usually opaque)

---

## 2️⃣ OAuth 2.0 Core Actors (You MUST name these)

| Actor | Meaning |
|-------|---------|
| Resource Owner | User |
| Client | App (mobile, web, service) |
| Authorization Server | Issues tokens |
| Resource Server | API |

---

## 3️⃣ OAuth 2.0 Flows (When to Use What)

### 3.1 Authorization Code Flow (MOST IMPORTANT)

**Use when:**
- User is involved
- Browser / mobile app
- Banking login flows

**Flow:** User → Auth Server → Code → Token

**Advantages:**
- ✔ Secure
- ✔ Supports MFA
- ✔ Used with OIDC

**🔥 Interview line:** "Authorization Code flow is the default for user-facing apps."

---

### 3.2 Client Credentials Flow

**Use when:**
- Service-to-service
- No user involved
- Example: Reporting service calling Account service

**Advantages:**
- ✔ Simple
- ✔ Machine identity

**🔥** "Client credentials is used for backend service authorization."

---

### ❌ Deprecated / Dangerous Flows

- Implicit flow ❌
- Password grant ❌

**Say explicitly:** "We avoid implicit and password flows due to security risks."

---

## 4️⃣ OpenID Connect (OIDC) vs OAuth (CRITICAL)

**Important:** OAuth alone cannot tell you WHO the user is.

**OIDC adds:**
- ID Token
- User identity
- Authentication semantics

| Aspect | OAuth | OIDC |
|--------|-------|------|
| Purpose | Authorization | Authentication |
| Tokens | Access token | ID token + access token |
| Question | "Can I access?" | "Who is the user?" |

**🔥:** "OIDC sits on top of OAuth to provide authentication."

### Tokens in OIDC

| Token | Purpose | Audience |
|-------|---------|----------|
| ID Token (JWT) | Identity | Client (FE/BFF) |
| Access Token | API access | APIs |
| Refresh Token | Token renewal | Auth server |

**Golden rule:** ID tokens are for clients, access tokens are for APIs.

---

## 5️⃣ Token Types — Deep Understanding

### 5.1 ID Token

- Always JWT
- Proves who the user is
- Used for:
  - Session creation
  - UI personalization
- **Never** used for API authorization

**Important:** Frontend is untrusted, so ID tokens are safe even if exposed — because they grant no backend access.

### 5.2 Access Token

- Used for authorization
- Short-lived
- Can be:
  - JWT (self-contained)
  - Opaque (reference token)

### 5.3 Refresh Token

- Long-lived
- Used only with auth server
- Never sent to APIs
- Stored server-side in secure architectures

**🔥 Banking rule:** Refresh tokens are treated like credentials.

---

## 6️⃣ JWT vs Opaque Tokens (Trade-offs Matter)

### JWT (Self-contained)

**Pros:**
- ✔ No auth-server lookup
- ✔ Scales well
- ✔ Stateless

**Cons:**
- ❌ Hard to revoke
- ❌ Bigger tokens
- ❌ Sensitive data risk

**Use when:**
- Internal APIs
- High throughput
- Short-lived tokens

### Opaque Tokens (Reference tokens)

**Pros:**
- ✔ Easy revocation
- ✔ Central control

**Cons:**
- ❌ Extra lookup (introspection)
- ❌ Latency

**Use when:**
- External APIs
- High security / revocation needs

**🔥 Interview line:** "JWTs favor scalability; opaque tokens favor control."

---

## 7️⃣ Scopes vs Roles vs Claims (Big Confusion Area)

### Scopes — What the client can do

**Example:**
- `accounts.read`
- `payments.write`

**Used for:** API access control

### Roles — Who the user is

**Example:**
- `CUSTOMER`
- `ADMIN`

High-level grouping.

### Claims — Attributes about user or context

**Example:**
```json
{
  "customerTier": "GOLD",
  "region": "UK"
}
```

**Key Rule (MEMORISE):**
- **Scopes** = API permissions
- **Roles** = coarse user grouping
- **Claims** = fine-grained attributes

---

## 8️⃣ Token Expiry & Rotation (Why Short-Lived Matters)

### Why tokens MUST expire

- Prevent misuse
- Limit blast radius
- Reduce replay risk

### Typical values:

- Access token: 5–15 mins
- Refresh token: hours / days

### Rotation

- Rotate signing keys
- Rotate refresh tokens
- Support key rollover

**🔥 Interview line:** "Short-lived tokens reduce the impact of compromise."

---

## 9️⃣ Authorization Models — RBAC vs ABAC (FS IMPORTANT)

### RBAC (Role-Based)

**Pros:**
- ✔ Simple
- ✔ Easy to understand

**Cons:**
- ❌ Coarse
- ❌ Explodes with complexity

**Example:** ADMIN can do X

### ABAC (Attribute-Based) — Preferred in FS

**Pros:**
- ✔ Fine-grained
- ✔ Context-aware
- ✔ Policy-driven

**Example:**
```
User can withdraw IF:
  role = CUSTOMER
  AND account.owner = user
  AND amount < dailyLimit
```

**🔥 Interview gold:** "Banks prefer ABAC because authorization depends on context, not just role."

---

## 🔟 Fine-Grained Authorization (Service & Data Level)

### Where authorization must happen

- ✔ At API Gateway (coarse)
- ✔ Inside service (fine-grained)

**Never rely only on gateway.**

### Example: Data-Level Authorization

Even if:
- Token is valid
- Scope allows `accounts.read`

**You must still check:** Does this customer own THIS account?

**🔥 Key line:** "Authentication answers who; authorization must answer which data."

---

## 1️⃣1️⃣ Cryptography & Keys (JWT Safety)

### Why JWTs Are Safe Despite Base64

- Base64 ≠ security
- Security comes from **digital signatures**

### Asymmetric Signing (Standard in FS)

- **Private key** → signs token (Auth Server only)
- **Public key** → verifies token (services)

**Services:**
- Can verify
- Cannot mint tokens

**This prevents token forgery.**

### Why Symmetric Signing Is Avoided

- Shared secret
- Any service could forge tokens
- Hard rotation

**Rule:** Banks use asymmetric signing only.

---

## 1️⃣2️⃣ How Tokens Flow in Microservices (End-to-End)

### Architecture

```
Frontend → BFF → Microservices → Downstream Services
```

### After Login (OIDC)

**Frontend receives:**
- ID Token
- Access Token

**In secure setups:**
- Tokens are handled by BFF
- Frontend gets only a session cookie

---

## 1️⃣3️⃣ BFF Pattern — Who Uses What Token

### Frontend (FE)

**Uses ID Token for:**
- Session context
- UI decisions
- Does not call microservices directly

### BFF (Backend-For-Frontend)

**Uses Access Token**
- Stores tokens server-side
- Issues HttpOnly session cookie to browser

**Why:**
- Browser is untrusted
- Prevents XSS token theft

---

## 1️⃣4️⃣ Service-to-Service Calls — Two Patterns

### Pattern A: Token Propagation

- Same user access token forwarded
- Simple
- Larger blast radius

### Pattern B: Token Exchange (Preferred in FS)

- Service exchanges incoming token
- Gets new access token:
  - Audience-specific
  - Least privilege

**Interview line:** "We prefer token exchange to enforce least privilege and zero trust."

---

## 1️⃣5️⃣ Authorization — Where Decisions Are Made

### Levels of Authorization

**1️⃣ API-Level (Coarse)**
- Scopes
- Example: `accounts.read`

**2️⃣ Domain-Level (Fine-grained)**
- Ownership checks
- Limits
- Status
- Context

**Example:**
```
User owns account
AND amount < dailyLimit
AND account is ACTIVE
```

> This is ABAC, not OAuth.

---

## 1️⃣6️⃣ Token Storage & Safety (Critical)

### Secure Pattern

**Access & refresh tokens stored server-side**

**Browser holds:**
- HttpOnly
- Secure
- SameSite cookie

**Why This Works:**
- Tokens never exposed to JS
- XSS impact minimized
- Central control

---

## 1️⃣7️⃣ Common Anti-Patterns (Must Call Out)

- ❌ Using ID token to call APIs
- ❌ Long-lived access tokens
- ❌ Business authorization only at gateway
- ❌ Trusting frontend checks
- ❌ Over-propagating user tokens
- ❌ Symmetric JWT signing

---

## 1️⃣8️⃣ High-Level Responsibility Split

```
Client → API Gateway → BFF → Domain Microservices
```

**Authorization is layered.**

No single component should make all authorization decisions.

