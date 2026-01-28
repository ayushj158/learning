# JWT Security Guide

## Core Principle
**JWT security = cryptography + expiry + transport + authorization**

---

## 1️⃣ Threat Model (Always Start Here)

When using JWTs, assume:
- ✔ Browser is untrusted
- ✔ Network is hostile
- ✔ Attackers can:
  - See requests
  - Replay requests
  - Modify client-side state

### JWT Security Goals
JWT security is about:
- Preventing forgery
- Limiting misuse
- Bounding damage

---

## 2️⃣ JWT Structure

```
HEADER.PAYLOAD.SIGNATURE
```

| Component | Purpose |
|-----------|---------|
| Header | Algorithm + key ID |
| Payload | Claims (identity, scopes) |
| Signature | Cryptographic integrity |

> ⚠️ **Note:** Header & payload are base64 encoded, not encrypted
> 
> 🔐 **Security comes entirely from the signature**

---

## 3️⃣ How JWT Signature Verification Works

### 3.1 Token Issuance (Auth Server)

```
data = base64(header) + "." + base64(payload)
signature = SIGN(data, PRIVATE_KEY)
JWT = data.signature
```

🔑 **Only the auth server has the private key**

### 3.2 Token Verification (API)

```
data = base64(header) + "." + base64(payload)
VERIFY(data, signature, PUBLIC_KEY)
```

**Results:**
- ✅ Signature matches → token is authentic
- ❌ Signature fails → token rejected

---

## 4️⃣ JWT Can Be Replayed — That's True

JWTs are bearer tokens:
> "If you have it, you can use it"

So replay is a real concern.

---

## 5️⃣ How Replay Attacks Are Mitigated (VERY IMPORTANT)

JWT security is **layered**, not absolute.

### 5.1 Short Token Lifetime (PRIMARY DEFENSE)
- Access tokens: 5–15 minutes
- Replay window is tiny

> 🔑 **Interview line:** "JWTs are safe because they're short-lived, not because they're non-replayable."

### 5.2 TLS (Mandatory)
- JWT never travels in cleartext
- Prevents network sniffing

Without TLS → JWTs are useless.

### 5.3 Secure Cookie Flags

If JWT is in cookie:
```
HttpOnly
Secure
SameSite=Strict/Lax
```

**Prevents:**
- JS access (XSS token theft)
- CSRF (with SameSite)

### 5.4 Audience (`aud`) Restriction

Token includes:
```json
"aud": "accounts-api"
```

**Result:**
- Token stolen from one API
- ❌ Cannot be used on another

### 5.5 Scope Restriction
Even if replayed:
- Limited to specific actions

### 5.6 Optional: `jti` (Token ID)

JWT may include:
```json
"jti": "random-id"
```

**Can be used for:**
- High-risk flows
- Token blacklisting

⚠️ **Rare in stateless systems (adds state)**

---

## 6️⃣ Common JWT Breaches & Defenses

### Breach 1: Token Forgery

| Aspect | Details |
|--------|---------|
| **Attack** | Modify payload, Fake admin role |
| **Defense** | ✔ Asymmetric signature verification, ✔ Enforced algorithm |

### Breach 2: Algorithm Confusion (`alg=none`)

| Aspect | Details |
|--------|---------|
| **Attack** | Trick verifier into skipping signature |
| **Defense** | ✔ Server enforces algorithm, ✔ Ignores token header alg |

### Breach 3: Token Theft via XSS

| Aspect | Details |
|--------|---------|
| **Attack** | Read token from JS storage |
| **Defense** | ✔ HttpOnly cookies, ✔ CSP, ✔ No localStorage |

### Breach 4: Replay Attack

| Aspect | Details |
|--------|---------|
| **Attack** | Reuse stolen token |
| **Defense** | ✔ Short expiry, ✔ Audience + scope, ✔ TLS |

### Breach 5: Privilege Escalation

| Aspect | Details |
|--------|---------|
| **Attack** | Abuse broad scopes |
| **Defense** | ✔ Least privilege scopes, ✔ ABAC inside services |

---

## 7️⃣ Why Public Key CANNOT Create a Signature

This is a fundamental property of asymmetric cryptography:
- Private key → one-way operation (sign)
- Public key → inverse check (verify)
- The inverse does not allow forward generation

### Important Point

**Verification does NOT mean:** "Create signature and compare"

**It means:** "Mathematically prove this signature could only have been produced by the private key"

---

## 8️⃣ Key Rotation

### JWT IS A TOKEN FORMAT, NOT A SECURITY SYSTEM

JWT does **NOT** define:
- How users authenticate
- How authorization is decided
- How revocation works
- How tokens are stored
- How trust boundaries are enforced

JWT **only** defines:
- A container for claims
- A signature mechanism

> 🔥 **Interview line:** "JWT is just a signed data structure; all security comes from how you use it."

---

## 9️⃣ JWT ≠ OAuth ≠ OIDC (BOUNDARIES MATTER)

You must be crystal clear on this separation:

| Item | Purpose |
|------|---------|
| **JWT** | Token format |
| **OAuth2** | Authorization framework |
| **OIDC** | Authentication protocol |

### Common Traps ❌
- ❌ "JWT is OAuth"
- ❌ "OIDC is JWT"

### Correct Framing ✔️
"OAuth and OIDC **may** use JWTs, but JWTs are protocol-agnostic."

---

## 🔟 JWT Claims — Which Ones Matter (AND WHY)

### Core Claims You Must Validate

| Claim | Meaning |
|-------|---------|
| `iss` | Who issued it |
| `aud` | Who it is for |
| `exp` | When it expires |
| `nbf` | Not before |
| `sub` | Subject (user / service) |
| `iat` | Issued at |

**Skipping any of these = security hole.**

> 🔥 **Interview line:** "Signature validation without issuer and audience validation is insufficient."

---

## 1️⃣1️⃣ Audience (`aud`) Is Your First Blast-Radius Control

Many teams ignore `aud`. That's dangerous.

### Why `aud` Matters

It prevents:
- Token reuse across APIs
- Lateral movement

### Example

```json
"aud": "payments-service"
```

A stolen token:
- ❌ Cannot call accounts-service
- ❌ Cannot call admin APIs

> 🔥 **This is mandatory in microservices.**

---

## 1️⃣2️⃣ JWT Expiry Is a Security Control, NOT a UX Feature

JWTs are bearer tokens → replayable.

**Therefore:**
- Short expiry is not optional
- Refresh tokens exist for UX, not access tokens

### Typical Production Values

| Token Type | Lifetime |
|-----------|----------|
| Access token | 5–15 minutes |
| ID token | 5–15 minutes |
| Refresh token | hours/days |

> 🔥 **Interview line:** "JWTs are safe because they expire quickly, not because they're non-replayable."

---

## 1️⃣3️⃣ JWT Revocation — Know the Limits

JWTs are stateless, so:
> **JWT cannot be revoked instantly unless you add state**

### Options (Trade-offs)

1. **Short expiry** (most common)
2. **Token blacklists** (`jti`) — adds state
3. **Key rotation** — coarse revocation
4. **Session-based BFF** — strongest

> **Key Takeaway:** "JWT revocation is probabilistic unless you introduce state."

---

## 1️⃣4️⃣ Key Rotation & JWKS (VERY IMPORTANT)

### How Real Systems Rotate Keys

- Auth server exposes JWKS endpoint
- Multiple public keys active at once
- JWT includes `kid` (key ID)
- Services select correct key

**This allows:**
- ✔ Zero-downtime rotation
- ✔ Gradual token expiry

> 🔥 **Interview line:** "JWTs support seamless key rotation using JWKS and kid."

---

## 1️⃣5️⃣ JWT Size & Performance Concerns

### Characteristics

JWTs are:
- Base64 encoded
- Sent on every request

### Problems
- Large headers
- Network overhead
- Proxy limits

### Guidelines
- Keep payload minimal
- No PII
- No business data
- No permissions explosion

> 🔥 **JWTs are identity carriers, not user profiles.**

---

## 1️⃣6️⃣ JWT ≠ Authorization (REPEAT THIS)

JWT carries claims, not decisions.

### JWT Can Tell You
- Who
- What scope
- What attributes

### JWT Cannot Tell You
- If user owns this resource
- If business rules allow action
- If transaction is legal

### Authorization Must Be
- Inside services
- Close to data
- ABAC-based

> 🔥 **Interview line:** "JWT provides input to authorization; it is not authorization."

---

## 1️⃣7️⃣ JWT Attacks You Must Know (AND Defenses)

| Attack | Defense |
|--------|---------|
| Algorithm confusion (`alg=none`) | Enforce algorithm server-side |
| Symmetric key misuse | Use asymmetric keys only |
| Token replay | Short expiry, TLS, audience |
| XSS token theft | HttpOnly cookies / BFF |
| Scope escalation | Least privilege + ABAC |

---

## 1️⃣8️⃣ JWT in Microservices — Practical Patterns

### Pattern A — Stateless JWT
- Token in header or secure cookie
- APIs validate JWT directly
- Simple, scalable

### Pattern B — BFF + Session
- JWT stored server-side
- Browser holds session cookie
- Stronger security, controlled state

### Pattern C — Token Exchange
- JWT transformed per hop
- Least privilege
- Zero trust friendly

---

## 1️⃣9️⃣ JWT Is Not Always the Best Option

Good engineers know when NOT to use JWT.

### Avoid JWT When
- Immediate revocation is mandatory
- Payload would be large
- Centralized control required
- Ultra-high-risk actions

### Alternatives
- Opaque tokens
- Session-based auth
- mTLS-only service identity
