# Edge Security: Complete Guide

## Executive Summary

**Edge security** protects APIs before they enter the system by enforcing:
- TLS termination with re-encryption
- DDoS protection
- WAF inspection
- IP filtering
- Rate limiting
- Schema validation
- Coarse-grained authentication and authorization

**Role:** Block malicious or abusive traffic early and reduce attack surface.

**Fine-grained business authorization** is enforced inside domain services.

---

## 🔑 Core Principle

```
Edge → block abuse & known attacks
App  → enforce ownership & intent
Infra → isolate & contain damage
```

**Edge security complements Zero Trust** by protecting north–south traffic while Zero Trust secures east–west (internal) traffic.

---

## 1️⃣ What Is Edge Security?

Edge security is **everything that protects your APIs before requests enter your internal system**.

### Characteristics
- Internet-facing
- High-traffic
- Untrusted
- Attack-prone

### Primary Goal
**Block malicious, abusive, or malformed traffic early** and reduce attack surface.

### Typical Components
- API Gateway
- Edge proxy
- Cloud load balancer
- Web Application Firewall (WAF)

---

## 2️⃣ Edge Security vs Application Security

| Aspect | Edge Security | Application Security |
|--------|---------------|----------------------|
| **Purpose** | Admission control | Intent validation |
| **Threats** | Generic threats | Domain-specific threats |
| **State** | Stateless | Context-aware |
| **Authority** | Fast rejection | Final authority |
| **Scope** | Broad filters | Precise business logic |

**Key Takeaway:** Edge rejects obvious threats. App enforces business rules.

---

## 3️⃣ Complete Edge Security Stack

### Architecture (Top to Bottom)

```
Internet
    ↓
CDN / DDoS Protection (L3/L4 attacks)
    ↓
Load Balancer (L4/L7) (Connection control)
    ↓
WAF (Pattern-based attacks)
    ↓
API Gateway (Schema, rate limits, coarse auth)
    ↓
BFF / Microservices (Fine-grained auth & business logic)
```

**Each layer removes a class of attacks.**

---

## 4️⃣ Threat Model at the Edge

### Edge Security IS Designed To Stop ✔️
- Internet noise
- Automated attacks
- Credential stuffing
- DDoS
- Malformed requests
- API scraping
- Rogue clients

### Edge Security IS NOT Designed To Stop ❌
- Business invariants (rules specific to your domain)
- Data ownership (BOLA attacks)
- Domain logic abuse
- Permission escalation

> **Note:** These require application-level enforcement.

---

## 📊 Master Reference: Edge Security Controls

| Control | What It Does | Threats It Stops | Implementation | Enforced At | Key Rules | Common Anti-Patterns |
|---------|-------------|-----------------|-----------------|-------------|-----------|----------------------|
| **TLS (Public)** | Encrypts client → edge | Sniffing, MITM, token theft | HTTPS, strong ciphers | Load balancer | TLS everywhere | ❌ HTTP, ❌ weak ciphers |
| **TLS Termination** | Ends public TLS at edge | Enables WAF/auth inspection | Decrypt → inspect → re-encrypt | Gateway | Terminate to inspect | ❌ Terminate without re-encrypt |
| **TLS Re-encryption** | Encrypts edge → backend | Internal MITM | New TLS/mTLS session | Gateway → Services | Encrypt every hop | ❌ Plain HTTP internally |
| **DDoS Protection** | Blocks volumetric attacks | L3/L4 floods | CDN, cloud edge filtering | Provider edge | Stop before VPC | ❌ Relying on app limits |
| **Load Balancer** | Connection-level control | Floods, protocol abuse | Conn limits, TLS enforcement | L4/L7 LB | LB ≠ auth | ❌ Auth at LB |
| **WAF** | Inspects payloads | SQLi, XSS, traversal, injection | Signature + heuristic rules | After TLS termination | WAF reduces risk, not fixes | ❌ Trusting WAF alone |
| **IP Allowlist/Denylist** | Network-level filtering | Unauthorized access | CIDR allow/deny rules | Gateway | IP ≠ identity | ❌ Using as identity |
| **Rate Limiting** | Limits request volume | Brute force, scraping, DoS | Per IP/token/route quotas | Gateway | Rate limit = security | ❌ No limits on auth APIs |
| **Throttling/Quotas** | Sustained usage control | Resource exhaustion | Burst + sustained limits | Gateway | Protect downstream | ❌ Unlimited APIs |
| **Bot Detection** | Detects non-human traffic | Scraping, abuse | Behavior + fingerprinting | Edge | Bots ≠ users | ❌ CAPTCHA everywhere |
| **Auth Check** | Verifies JWT present/valid | Anonymous access | JWT presence, expiry, issuer | Gateway | Coarse auth only | ❌ Final auth at edge |
| **Coarse Authorization** | Route-level permission checks | Obvious misuse | Scope → route mapping | Gateway | No business logic | ❌ Ownership checks at edge |
| **Schema Validation** | Validates request structure | Deserialization abuse | JSON schema, size limits | Gateway | Reject early | ❌ Trusting clients |
| **Payload Limits** | Caps request size | Memory exhaustion | Max body/header size | Gateway | Fail fast | ❌ Unlimited payloads |
| **Request Normalization** | Canonicalizes requests | Smuggling, bypass | Path/header normalization | Gateway | One representation | ❌ Multiple parsing paths |
| **CORS Policy** | Controls browser access | Browser-based abuse | Strict allow origins | Gateway | APIs ≠ websites | ❌ `*` with credentials |
| **Edge Logging** | Logs rejected traffic | Detection, forensics | WAF + gateway logs | Edge | Log signals, not secrets | ❌ Logging JWTs |

---

## 🎯 Control Deep Dives

### 1️⃣ TLS & Encryption

#### Public TLS (Client → Edge)
```
HTTPS → TLS 1.3 → Strong ciphers
```
- **Must:** Enforce TLS everywhere
- **Must:** Disable HTTP fallback
- **Must:** Use strong ciphers (no RC4, DES, MD5)

#### TLS Termination
- Decrypt at edge to enable inspection (WAF, auth checks)
- Edge needs visibility into payloads
- Gateway re-terminates for inspection

#### Internal TLS Re-encryption (Edge → Services)
```
Edge (decrypts) → Inspects → Re-encrypts → Services
```
- Never send plaintext internally
- Prevents internal MITM
- Use mTLS for service-to-service

---

### 2️⃣ DDoS Protection

**Volumetric attacks stop at provider edge, not in your VPC.**

| Layer | What It Stops | Technology |
|-------|---------------|-----------|
| **L3/L4** | IP floods, UDP floods | CDN, DDoS provider |
| **L7 (App)** | Request floods | Rate limiting, bot detection |

**Key Rule:** Application-level rate limiting ≠ DDoS protection.

---

### 3️⃣ WAF (Web Application Firewall)

**Purpose:** Detect and block known attack patterns.

| Attack | How WAF Helps |
|--------|----------------|
| SQLi | Pattern matching for `' OR 1=1`, comment chars |
| XSS | Pattern matching for `<script>`, event handlers |
| Path traversal | Block `../`, `..\\` sequences |
| Command injection | Block pipe, semicolon, backtick patterns |

**Limitations:**
- ❌ Signature-based (misses novel attacks)
- ❌ Bypassed by encoding/obfuscation
- ❌ High false positive rate
- ❌ Cannot understand business logic

**Best Use:** Secondary defense layer. Primary defense = secure code.

---

### 4️⃣ Rate Limiting

**Purpose:** Prevent abuse by limiting request volume.

#### Strategies

| Strategy | Use Case | Example |
|----------|----------|---------|
| **Per IP** | Public APIs | 100 req/min per IP |
| **Per Token** | Authenticated APIs | 1000 req/min per user |
| **Per Route** | Specific endpoints | 10 login attempts/min |
| **Global** | Protect infra | 1M req/sec total |

#### Critical: Rate Limit Authentication Endpoints
```
POST /login      → 10 attempts/min (brute force)
POST /otp/verify → 5 attempts/min (OTP brute force)
POST /register   → 5 registrations/min per IP (spam)
GET  /users/{id} → 100 req/min per token (scraping)
```

**Anti-Pattern:** Unlimited rate limits on auth endpoints = credential stuffing paradise.

---

### 5️⃣ Bot Detection

**Purpose:** Identify and block automated traffic (scrapers, abusers).

#### Detection Signals
- Header anomalies (missing User-Agent, etc.)
- Request patterns (too fast, sequential IDs)
- Behavioral signals (no cookies, no JS execution)
- IP reputation (known bot IPs)

#### Better Than CAPTCHA
- Legitimate users aren't blocked
- Harder for bots to bypass
- Can challenge suspicious requests

**Anti-Pattern:** CAPTCHA on every request = bad UX, doesn't stop sophisticated bots.

---

### 6️⃣ Coarse Authorization at the Edge

**Purpose:** Block obvious role misuse at gateway level.

#### Examples
```
# Allow
GET /accounts/{id}     → Any authenticated user
POST /transfer         → Authenticated user with "payment" scope

# Block at Gateway
POST /admin/users      → Only "admin" role
DELETE /database       → Only "super-admin" role
```

#### Limitations
- ❌ Cannot check if user owns `{id}`
- ❌ Cannot apply business rules
- ❌ Cannot verify intent

**Rule:** Coarse (route-level) at edge, fine (ownership-level) in app.

---

### 7️⃣ Schema Validation

**Purpose:** Reject malformed requests early.

#### Examples
```json
{
  "email": { "type": "string", "format": "email" },
  "age": { "type": "integer", "minimum": 0, "maximum": 150 },
  "items": { "type": "array", "maxItems": 100 },
  "body": { "type": "string", "maxLength": 10000 }
}
```

**Benefits:**
- Rejects payload-based attacks early
- Prevents oversized payloads (memory bombs)
- Protects downstream services
- Clear contract enforcement

---

### 8️⃣ IP Filtering

**Purpose:** Network-level access control.

#### Valid Use Cases
- Partner API access (partner CIDR)
- Admin endpoint access (office CIDR)
- Geofencing (country-level blocks)

#### Invalid Use Cases
- ❌ User identity (IPs change, VPNs exist)
- ❌ Fine-grained authorization
- ❌ Token replacement

**Rule:** IP filtering = coarse network control, not identity.

---

### 9️⃣ CORS Policy

**Purpose:** Control which browser applications can call your API.

#### Secure Configuration
```
Allow-Origin: https://trusted-domain.com
Allow-Credentials: false  (or true + specific origin, never with *)
Allow-Methods: GET, POST, PUT
Allow-Headers: Content-Type, Authorization
```

#### Common Mistakes
```
❌ Allow-Origin: *
❌ Allow-Origin: * + Allow-Credentials: true
❌ Allow-Origin: https://*.example.com (wildcard subdomains)
```

**Rule:** Never use `*` with `Allow-Credentials: true`.

---

## 🏗️ Key Architectural Principles

### Principle 1: Edge ≠ Authority

| Entity | Responsibility |
|--------|----------------|
| **Edge** | Admit or reject (filters abuse) |
| **App** | Authorize or forbid (enforces intent) |

Edge can reject an obvious SQLi. Edge **cannot** check if you own account 123.

### Principle 2: Separation of Concerns

```
Edge Layer         → What reaches the system?
Application Layer  → What is allowed to do what?
Data Layer         → How is data stored and protected?
```

Mixing these creates security gaps.

### Principle 3: Defense in Depth

```
No single layer is sufficient.
Each layer stops different attacks.
Failure in one layer doesn't compromise others.
```

| Attack | Layer 1 | Layer 2 | Layer 3 |
|--------|---------|---------|---------|
| DDoS | ✅ CDN | ⚠️ Rate limit | ❌ App |
| SQLi | ✅ WAF | ✅ App code | ❌ Edge alone |
| BOLA | ❌ Edge | ✅ App | ✅ |
| XSS | ✅ WAF | ✅ App CSP | ✅ |

---

## 🚨 Common Anti-Patterns

| Anti-Pattern | Why It's Wrong | Correct Approach |
|--------------|----------------|------------------|
| ❌ Business logic in gateway | Gateway doesn't understand domain | Keep logic in services |
| ❌ Ownership checks at edge | Edge has no context | Check in application |
| ❌ Auth is final at edge | Only entry point, not authority | Re-validate at service |
| ❌ Trusting WAF completely | WAF has false negatives | Use as defense-in-depth |
| ❌ Plain HTTP internally | Internal MITM possible | Use TLS everywhere |
| ❌ `*` CORS with credentials | Vulnerable to CSRF/XSS | Specific origins only |
| ❌ Unlimited API quotas | Enables scraping/DoS | Rate limit all endpoints |
| ❌ Verbose error messages | Enables enumeration | Uniform error responses |
| ❌ "Internal APIs are safe" | Internal ≠ trusted | Apply same security rigor |
| ❌ Skipping edge for internal traffic | Still exposed via containers | Encrypt all hops |

---

## 🧠 Mental Model: The Three Pillars

```
Edge Security     = Admission Control (What gets in?)
   ↓
App Security      = Authorization & Intent (What is allowed?)
   ↓
Infra Security    = Isolation & Containment (Limit damage if breached)
```

### In Action

**Scenario:** Attacker tries to access another user's account.

1. **Edge:** JWT is valid, route is `/accounts/{id}` → ✅ Admit
2. **App:** Check if current user owns account 123 → ❌ Deny
3. **Infra:** Even if somehow bypassed, data encrypted, audit logs created

**No single layer is sufficient. All three must work.**

---

## ✅ Best Practices Checklist

### TLS & Encryption
- ✅ TLS 1.3+ everywhere
- ✅ Strong ciphers only
- ✅ Internal TLS/mTLS
- ✅ Certificate management automated

### DDoS & Availability
- ✅ CDN for DDoS protection
- ✅ Rate limiting on sensitive endpoints
- ✅ Bot detection active
- ✅ Payload size limits enforced

### WAF & Inspection
- ✅ WAF active with rulesets updated
- ✅ Request normalization enabled
- ✅ SQL/XSS patterns blocked
- ✅ Schema validation enforced

### Authentication & Authorization
- ✅ Coarse route-level auth at edge
- ✅ Fine-grained ABAC in services
- ✅ Token validation (iss, aud, exp)
- ✅ No business logic in gateway

### Logging & Monitoring
- ✅ Log rejected requests (WAF, rate limit)
- ✅ Alert on attack patterns
- ✅ Do NOT log tokens or credentials
- ✅ Forensics trail available

---

## 📝 Summary

| Aspect | Edge | App | Infra |
|--------|------|-----|-------|
| **What?** | Admission | Authorization | Isolation |
| **How?** | TLS, WAF, rate limit | ABAC, ownership checks | Firewall, encryption |
| **Protects** | Entry point | Business logic | Data & systems |
| **Scope** | Generic threats | Domain threats | Breach containment |

**Final Rule:** Edge is your first defense. Application is your second defense. Infrastructure is your last defense. All three are mandatory.
