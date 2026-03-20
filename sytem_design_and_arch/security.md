# 🔐 Security Architecture — Complete Deep Dive

This cuts across System Design AND Java/MS AND Cloud — so I'll teach it as a unified topic with clear sections, then you'll be able to apply it anywhere.

---

# 🗂️ Security Architecture — 6 Pillars

1. 🔵 **Zero Trust Architecture**
2. 🟡 **Authentication & Authorisation — OAuth2 / OIDC / JWT**
3. 🟢 **Service-to-Service Security — mTLS**
4. 🔴 **Secrets Management**
5. 🟣 **Data Security — Encryption, Tokenisation, PII**
6. ⚫ **API Security**

---

# 🔵 PILLAR 1 — Zero Trust Architecture

## What is Zero Trust?

> *"Never trust, always verify — regardless of whether the request comes from inside or outside the network perimeter."*

```
Old model (Castle and Moat):
→ Outside network: untrusted ❌
→ Inside network: trusted ✅
→ Once inside → access to everything
→ Problem: insider threats, lateral movement after breach

Zero Trust model:
→ Inside network: still NOT trusted
→ Every request must be authenticated + authorised
→ Least privilege — minimum access needed
→ Assume breach — design to limit blast radius
```

---

## Zero Trust Core Principles

### Principle 1 — Verify Explicitly

```
Every request must prove:
WHO: identity (who is calling?)
WHAT: device posture (is the device healthy?)
WHERE: network location (expected location?)
WHEN: time-based context (expected time?)

FS Implementation:
→ Every API call carries JWT — verified every time
→ Service accounts verified via Workload Identity (GCP)
→ mTLS — every service proves its identity via certificate
→ No IP-based trust — VPN access alone ≠ trusted
```

### Principle 2 — Least Privilege Access

```
Every identity gets MINIMUM access needed:

Human users:
→ Developers: read-only production logs, no DB access
→ On-call: read + limited write during incident only (JIT)
→ DBAs: specific DB access, not entire GCP project

Service accounts:
→ Payment Service: can write to payment-events topic only
→ Statement API: can read from Cassandra, no write
→ Fraud Service: can read fraud features, no customer PII

GCP Implementation:
→ Custom IAM roles — exact permissions needed
→ No primitive roles (Owner/Editor) in production
→ Workload Identity — pod-level identity, not node-level
→ PAM (Privileged Access Manager) — time-bound elevation
```

### Principle 3 — Assume Breach

```
Design as if attacker is already inside:

→ Encrypt all internal traffic (mTLS) — even inside VPC
→ VPC Service Controls — prevent data exfiltration
  even from compromised internal pod
→ Audit log everything — detect lateral movement
→ Segment network — blast radius containment
→ Short-lived credentials — tokens expire, rotate often

FS Example:
"At Lloyds, we designed payment services assuming
any pod could be compromised. mTLS meant even if
an attacker got into one pod, they couldn't impersonate
another service. VPC Service Controls meant they
couldn't exfiltrate data to external storage.
Short-lived Workload Identity tokens (1 hour) meant
compromised credentials expired quickly."
```

---

## Zero Trust in Practice — FS Architecture

```
Request flow with Zero Trust:

Customer → Mobile App
    ↓
Cloud Armor (WAF — verify request is not malicious)
    ↓
Global Load Balancer
    ↓
API Gateway:
  ✓ Verify JWT (who is this customer?)
  ✓ Check token not expired
  ✓ Check token not revoked (token introspection)
  ✓ Rate limit (is this normal behaviour?)
  ✓ IP reputation check
    ↓
Payment Service (GKE pod):
  ✓ JWT validated again (defence in depth)
  ✓ Authorisation check (can this customer do this?)
    ↓ mTLS
Fraud Service (GKE pod):
  ✓ Certificate verified (is caller really Payment Service?)
  ✓ Authorisation (Payment Service allowed to call Fraud?)
    ↓ mTLS
Core Banking Adapter:
  ✓ Certificate verified
  ✓ Service account checked
    ↓
Core Banking System
```

---

# 🟡 PILLAR 2 — OAuth2 / OIDC / JWT

## The Three Concepts — What They Are

```
OAuth2:
→ Authorisation framework — "what can you access?"
→ Defines flows for obtaining access tokens
→ Does NOT define authentication (who you are)

OIDC (OpenID Connect):
→ Authentication layer ON TOP of OAuth2
→ "who are you?" — adds identity to OAuth2
→ Adds ID Token (JWT) containing user identity
→ Defines UserInfo endpoint

JWT (JSON Web Token):
→ Token format — compact, self-contained
→ Used by OAuth2 + OIDC as token format
→ Contains: header + payload + signature
```

---

## JWT — Deep Dive

### Structure

```
JWT = Base64(Header) + "." + Base64(Payload) + "." + Signature

Header:
{
  "alg": "RS256",    ← signing algorithm
  "typ": "JWT",
  "kid": "key-id-1" ← which key was used to sign
}

Payload (Claims):
{
  "sub": "C-UK-001",          ← subject (customer ID)
  "iss": "https://auth.bank.com", ← issuer
  "aud": "payment-service",   ← intended audience
  "exp": 1705312800,          ← expiry timestamp
  "iat": 1705309200,          ← issued at timestamp
  "jti": "uuid-unique-token-id", ← JWT ID (for revocation)
  "scope": "payments:write accounts:read",
  "jurisdiction": "UK",       ← custom claim
  "roles": ["customer"]       ← custom claim
}

Signature:
→ RS256: signed with issuer's PRIVATE key
→ Verified with issuer's PUBLIC key
→ Tamper-proof — any modification invalidates signature
```

### RS256 vs HS256

```
HS256 (HMAC SHA-256):
→ Single shared secret — both sign AND verify
→ Problem: every service that verifies needs the secret
→ If one service is compromised → secret exposed
→ FS: AVOID — shared secret is a single point of failure ❌

RS256 (RSA SHA-256):
→ Asymmetric — private key signs, public key verifies
→ Auth server keeps private key (secret)
→ ALL services get public key (safe to distribute)
→ Compromise of a service → public key useless to attacker
→ FS: ALWAYS USE RS256 ✅

Key rotation with RS256:
→ Auth server rotates key pair periodically
→ JWKS endpoint: https://auth.bank.com/.well-known/jwks.json
→ Services fetch new public keys automatically
→ "kid" in JWT header identifies which key to use
→ Zero-downtime key rotation ✅
```

### JWT Validation — Every Service Must Do This

```java
// Every receiving service validates:

public Claims validateJwt(String token) {

  // Step 1: Parse JWT
  Jws<Claims> jws = Jwts.parserBuilder()
    .setSigningKeyResolver(jwksKeyResolver) // fetch public key by kid
    .build()
    .parseClaimsJws(token);

  Claims claims = jws.getBody();

  // Step 2: Verify issuer
  if (!claims.getIssuer().equals("https://auth.bank.com")) {
    throw new SecurityException("Invalid issuer");
  }

  // Step 3: Verify audience
  if (!claims.getAudience().equals("payment-service")) {
    throw new SecurityException("Token not intended for this service");
  }

  // Step 4: Verify expiry (library does this automatically)
  // exp claim checked — expired token rejected

  // Step 5: Verify not-before (nbf) if present

  // Step 6: Check jti not in revocation list (optional)
  if (tokenRevocationService.isRevoked(claims.getId())) {
    throw new SecurityException("Token has been revoked");
  }

  return claims;
}
```

---

## OAuth2 Flows — Which to Use When

### Flow 1 — Authorization Code + PKCE (Customer-Facing)

```
Used for: Customer authenticating on mobile/web app

Flow:
1. Customer clicks "Login" on mobile app
2. App redirects to Auth Server login page
   GET /authorize?
     response_type=code&
     client_id=mobile-app&
     redirect_uri=bankapp://callback&
     scope=payments:write accounts:read&
     code_challenge=xyz&        ← PKCE
     code_challenge_method=S256

3. Customer enters credentials on Auth Server
4. Auth Server verifies identity (MFA if required)
5. Auth Server redirects back with authorization code:
   bankapp://callback?code=AUTH-CODE-123

6. App exchanges code for tokens:
   POST /token
     grant_type=authorization_code&
     code=AUTH-CODE-123&
     code_verifier=abc  ← PKCE verifier

7. Auth Server returns:
   {
     "access_token": "eyJ...",  ← JWT, short-lived (15 min)
     "refresh_token": "xyz",    ← opaque, long-lived (30 days)
     "id_token": "eyJ...",      ← OIDC — customer identity
     "expires_in": 900
   }

8. App uses access_token for API calls
9. When access_token expires → use refresh_token to get new one

PKCE (Proof Key for Code Exchange):
→ Prevents authorization code interception attacks
→ Mobile apps can't keep secrets → PKCE replaces client secret
→ code_verifier: random string generated by app
→ code_challenge: SHA256(code_verifier) sent in step 2
→ Auth server verifies: SHA256(code_verifier) == code_challenge ✅
```

### Flow 2 — Client Credentials (Service-to-Service)

```
Used for: Machine-to-machine — Payment Service calling Fraud Service

Flow:
1. Payment Service authenticates to Auth Server:
   POST /token
     grant_type=client_credentials&
     client_id=payment-service&
     client_secret=xxx&   ← or mTLS client cert
     scope=fraud:read

2. Auth Server verifies client identity
3. Returns access token:
   {
     "access_token": "eyJ...",
     "expires_in": 300,   ← short-lived, 5 minutes
     "token_type": "Bearer"
   }

4. Payment Service calls Fraud Service:
   GET /fraud/score
   Authorization: Bearer eyJ...

5. Fraud Service validates JWT:
   → Verifies signature
   → Checks audience = "fraud-service"
   → Checks scope includes "fraud:read"
   → Checks client_id is payment-service (authorised caller)

Token caching:
→ Payment Service caches token until 30s before expiry
→ Fetches new token before it expires
→ Avoids Auth Server call on every request ✅
```

### Flow 3 — Implicit Flow (AVOID)

```
DEPRECATED — do not use:
→ Access token returned directly in URL fragment
→ Token visible in browser history, logs
→ No refresh token
→ FS: never use ❌
```

### Flow 4 — Resource Owner Password (AVOID in FS)

```
AVOID in FS:
→ App collects username + password directly
→ Sends to Auth Server
→ Problem: app handles raw credentials
→ FS: never use for customer flows ❌
→ Acceptable only for: legacy system integration
  where no redirect is possible
```

---

## Token Introspection vs Local Validation

```
Local Validation (preferred for performance):
→ Service fetches JWKS public keys once (cached)
→ Validates JWT signature locally
→ Checks claims (exp, iss, aud) locally
→ Zero network call per request ✅
→ Problem: cannot detect revoked tokens in real-time

Token Introspection (for high-security):
→ Service calls Auth Server:
  POST /introspect
    token=eyJ...
→ Auth Server responds:
  { "active": true, "sub": "C-UK-001", "scope": "payments:write" }
→ Always fresh — revoked tokens detected ✅
→ Problem: network call per request — latency + availability risk

FS Hybrid Approach:
→ Normal requests: local validation (fast)
→ High-value transactions (>£10K): token introspection
→ Revocation detection: short token lifetime (15 min)
  → Even if token stolen, expires quickly
→ Critical operations: require fresh token (max age 5 min)
```

---

# 🟢 PILLAR 3 — mTLS (Mutual TLS)

## What is TLS vs mTLS?

```
Standard TLS (one-way):
Client → Server
→ Server presents certificate (proves server identity)
→ Client verifies server cert ✅
→ Server does NOT verify client identity ❌
→ Used for: browser → website, customer → API

mTLS (mutual TLS — two-way):
Client → Server
→ Server presents certificate (proves server identity)
→ Client verifies server cert ✅
→ Client presents certificate (proves client identity)
→ Server verifies client cert ✅
→ Both parties authenticated ✅
→ Used for: service-to-service in Zero Trust architecture
```

---

## How mTLS Works — Step by Step

```
Certificate Authorities (CA):
→ Each service has a certificate signed by internal CA
→ Internal CA trusted by all services in the cluster
→ Certificate contains: service name, expiry, public key

mTLS handshake:
1. Payment Service → Fraud Service: "Hello, I want to connect"
2. Fraud Service → Payment Service: here is my certificate
   (signed by internal CA)
3. Payment Service verifies: CA trusted? cert not expired?
   cert matches expected service name (fraud-service)?
4. Payment Service → Fraud Service: here is MY certificate
5. Fraud Service verifies: CA trusted? cert not expired?
   cert is payment-service? (is this an authorised caller?)
6. Encrypted session established ✅
7. All traffic encrypted end-to-end ✅

Result:
→ Fraud Service KNOWS caller is Payment Service ✅
→ Payment Service KNOWS it's talking to real Fraud Service ✅
→ No impersonation possible ✅
→ All traffic encrypted — even inside VPC ✅
```

---

## Certificate Management — The Hard Part

```
Manual certificate management (avoid):
→ Generate cert for each service
→ Manually distribute to services
→ Track expiry dates
→ Manually rotate before expiry
→ Operational nightmare at scale ❌

Automated with Istio (preferred):
→ Citadel (Istio CA) — internal certificate authority
→ Automatically issues certificate to each pod on startup
→ Certificate lifetime: 24 hours (configurable)
→ Automatic rotation: Istio rotates before expiry
→ SPIFFE identity: spiffe://cluster/ns/payments/sa/payment-svc
→ Zero operational overhead ✅

Certificate rotation zero-downtime:
→ New cert issued while old cert still valid
→ Both certs valid during rotation window
→ Old cert expires → only new cert valid
→ Traffic never interrupted ✅
```

---

## mTLS in GKE with Istio

```
How Istio implements mTLS:

1. Sidecar injection:
   Each pod gets Envoy proxy sidecar automatically
   annotation: sidecar.istio.io/inject: "true"

2. Certificate issuance:
   Envoy sidecar → Istiod (control plane)
   "I am payment-service pod in payments namespace"
   Istiod issues certificate: SPIFFE identity

3. mTLS policy:
   PeerAuthentication policy: STRICT mode
   → All traffic MUST use mTLS
   → Plain text traffic rejected ❌

4. Service-to-service call:
   Payment pod → Payment Envoy sidecar
   (plain HTTP inside pod — app doesn't handle TLS)
   Payment Envoy → Fraud Envoy (mTLS — encrypted)
   Fraud Envoy → Fraud pod
   (plain HTTP inside pod)
   → App code never handles TLS certificates ✅
   → All mTLS handled by sidecar ✅

GCP Configuration:
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
  namespace: payments
spec:
  mtls:
    mode: STRICT  # reject all non-mTLS traffic
```

---

# 🔴 PILLAR 4 — Secrets Management

## What is a Secret?

```
Secrets = sensitive configuration values:
→ Database passwords
→ API keys (third-party services)
→ Encryption keys (if not using KMS)
→ OAuth2 client secrets
→ TLS private keys
→ Service account credentials
```

---

## The Wrong Ways (Common in FS Legacy)

```
❌ Hardcoded in source code:
   String dbPassword = "MyP@ssw0rd123";
   → In Git history forever
   → Anyone with repo access has password

❌ Environment variables (naive):
   DB_PASSWORD=MyP@ssw0rd123
   → Visible in process list: ps aux
   → Logged by monitoring tools
   → In Kubernetes: visible in pod spec YAML

❌ Config files in repo:
   application.properties: spring.datasource.password=xxx
   → Same problem as hardcoded
```

---

## HashiCorp Vault — The FS Standard

### What Vault Does

```
Vault = centralised secrets management platform

Core capabilities:
1. Static secrets storage — store + retrieve secrets
2. Dynamic secrets — generate ephemeral credentials
3. Encryption as a service — encrypt/decrypt without key exposure
4. PKI — certificate authority, issue/revoke certs
5. Tokenisation (Transform engine)
6. Audit log — every secret access logged immutably
```

### Dynamic Secrets — The Key Feature

```
Static secret (traditional):
→ DBA creates DB user: app_user / P@ssw0rd123
→ Stored in Vault
→ Payment Service fetches: app_user / P@ssw0rd123
→ Uses same credentials forever
→ If leaked: attacker has permanent access ❌

Dynamic secret (Vault Database engine):
→ Payment Service requests DB credentials from Vault
→ Vault creates a new DB user:
  v-payment-service-xyz123 / $r4nD0mP@ss
  (TTL: 1 hour)
→ Payment Service uses these credentials
→ After 1 hour: Vault automatically revokes the user
→ If leaked: attacker has 1 hour max ✅
→ Different credentials every time ✅
→ No shared permanent credentials ✅

FS Impact:
"We implemented Vault dynamic secrets for our
Cloud SQL connections. Each payment service pod
got unique 1-hour credentials. When we had a
suspected credential leak, we simply revoked the
lease — no password rotation needed across all pods."
```

### Vault in GKE — Integration

```
Two approaches:

Approach 1 — Vault Agent Sidecar:
→ Vault Agent runs as sidecar in each pod
→ Authenticates to Vault using Kubernetes auth method
  (JWT from Kubernetes service account)
→ Fetches secrets → writes to shared volume
→ App reads from file — no Vault SDK needed
→ Agent handles renewal automatically ✅

Approach 2 — Vault Secrets Operator (newer):
→ Kubernetes operator syncs Vault secrets
  to Kubernetes Secrets automatically
→ Define VaultStaticSecret or VaultDynamicSecret CRD
→ Operator creates/updates K8s Secret
→ Pod mounts K8s Secret normally
→ Operator handles renewal + rotation ✅

Kubernetes auth method:
→ Pod presents its Kubernetes service account JWT
→ Vault validates with Kubernetes API
→ Vault issues Vault token with appropriate policies
→ Pod uses Vault token to fetch secrets
→ No hardcoded Vault credentials needed ✅
```

---

## GCP Secret Manager

```
GCP's native secrets management:

vs HashiCorp Vault:
→ Simpler — fewer features
→ No dynamic secrets (static only)
→ No encryption as a service
→ Fully managed — zero operational overhead
→ Native GCP integration — IAM controls access
→ Cheaper for simple use cases

Best for:
→ API keys, connection strings, static credentials
→ Teams without Vault expertise
→ GCP-native projects wanting minimal ops

Access pattern:
→ GKE pod with Workload Identity →
  Secret Manager API → fetch secret value
→ IAM: roles/secretmanager.secretAccessor
  on specific secret only ✅

Secret rotation:
→ Manual rotation or Cloud Functions trigger
→ Rotation notification via Pub/Sub
→ Application must handle rotation gracefully:
  catch auth error → refresh secret → retry ✅

GCP Secret Manager + External Secrets Operator:
→ Operator syncs GCP secrets to K8s secrets
→ App reads from K8s secret normally
→ Operator handles sync automatically ✅
```

---

# 🟣 PILLAR 5 — Data Security

## Encryption — Three States

```
Data at Rest (stored):
→ Database files on disk
→ GCS bucket objects
→ Kafka log files on broker disk
→ Solution: AES-256 encryption
  GCP: CMEK (Customer-Managed Encryption Keys)
  Java: JPA field-level encryption for extra sensitivity

Data in Transit (moving):
→ Customer → API (HTTPS/TLS 1.3)
→ Service → Service (mTLS)
→ Service → Database (TLS)
→ On-prem → GCP (Dedicated Interconnect — private, no internet)
→ Solution: TLS everywhere, mTLS for internal

Data in Use (processing):
→ Data in memory being processed
→ Hardest to protect
→ Solution: Confidential Computing (GCP Confidential VMs)
  → Memory encrypted even from Google
  → For ultra-sensitive workloads (HSM-level)
```

## Field-Level Encryption

```
When database-level encryption not enough:
→ DBA can see all data in plaintext ❌
→ Backup files contain plaintext ❌

Field-level encryption:
→ Sensitive fields encrypted BEFORE writing to DB
→ DB stores ciphertext — DBA sees gibberish ✅
→ Only application with key can decrypt

FS Implementation:
@Entity
public class CustomerProfile {
  private String customerId;

  @Convert(converter = EncryptedStringConverter.class)
  private String nationalInsuranceNumber;  // encrypted at field level

  @Convert(converter = EncryptedStringConverter.class)
  private String dateOfBirth;              // encrypted at field level

  private String jurisdiction;             // NOT encrypted — not PII
}

// JPA Converter uses Vault Transit engine:
public String convertToDatabaseColumn(String plaintext) {
  return vaultTemplate.transit().encrypt("customer-key", plaintext);
}

public String convertToEntityAttribute(String ciphertext) {
  return vaultTemplate.transit().decrypt("customer-key", ciphertext);
}
```

---

## Tokenisation vs Encryption — Recap in Security Context

```
Encryption:
→ Mathematical transformation — reversible with key
→ Ciphertext looks random — different length from plaintext
→ GDPR erasure: delete key → crypto shredding
→ Performance: fast — local CPU operation
→ FS use: bulk data, database fields, file encryption

Tokenisation:
→ Random token mapped to real value in vault
→ Token format can match original (FPE) or be random
→ GDPR erasure: delete mapping from vault
→ Performance: vault round-trip per token
→ FS use: account numbers, card numbers, sort codes

When to use which:
→ Format must be preserved (card number validation): FPE tokenisation
→ High volume, no format constraint: encryption
→ Third parties must never see real value: tokenisation
→ Data shared across systems: tokenisation (tokens safe to share)
→ Bulk files, backups: encryption
```

---

# ⚫ PILLAR 6 — API Security

## API Gateway Security Functions

```
API Gateway sits in front of all APIs:
→ Single entry point for all external traffic
→ Enforces security policies centrally

Security functions:

1. Authentication:
   → Validate JWT on every request
   → Verify signature, expiry, issuer, audience
   → Reject invalid tokens before reaching microservices

2. Authorisation:
   → Check scopes: does token have payment:write scope?
   → Role-based access control
   → Tenant isolation (multi-tenant FS platforms)

3. Rate Limiting:
   → Per customer: max 100 payment requests/minute
   → Per API key: max 1000 requests/minute
   → Per IP: max 500 requests/minute
   → Burst allowance: short spike allowed, sustained rate limited

4. Input Validation:
   → Reject malformed requests early
   → Schema validation — correct fields, correct types
   → Size limits — prevent large payload attacks

5. TLS Termination:
   → Terminate TLS at gateway
   → Re-encrypt for internal services (mTLS)

6. Threat Protection (+ Cloud Armor):
   → SQL injection patterns
   → XSS patterns
   → OWASP Top 10

7. Logging + Audit:
   → Every request logged with correlation ID
   → Sensitive fields masked in logs
   → Immutable audit trail
```

---

## Rate Limiting — Deep Dive

### Token Bucket Algorithm

```
Concept:
→ Bucket holds N tokens (capacity)
→ Tokens added at rate R per second (refill rate)
→ Each request consumes 1 token
→ If bucket empty → request rejected (429 Too Many Requests)

Example: Payment API rate limit
→ Bucket capacity: 100 tokens
→ Refill rate: 10 tokens/second
→ Customer sends 100 requests in 1 second:
  → First 100: processed ✅ (bucket empty)
  → Next request: rejected 429 ❌
  → After 1 second: 10 tokens added
  → Next 10 requests: processed ✅

Allows bursting:
→ Customer can burst up to bucket capacity
→ Then limited to refill rate
→ FS: allows payment batch but prevents abuse

Redis implementation:
→ Key: rate_limit:{customerId}:{windowId}
→ Value: token count
→ DECR atomically — thread-safe ✅
→ TTL: reset bucket after inactivity
```

### Sliding Window Algorithm

```
Concept:
→ Track request timestamps in a window
→ Count requests in last N seconds
→ If count > limit → reject

vs Fixed Window:
Fixed Window problem:
  Window: 0-60s, limit: 100
  Customer sends 100 at 59s, 100 at 61s
  → 200 requests in 2 seconds — bypasses limit ❌

Sliding Window:
  Always counts last 60 seconds
  → 100 at 59s + 100 at 61s
  → At 61s: look back 60s → 200 requests → limit exceeded ✅

FS use: sliding window more accurate for FS APIs
→ More complex to implement (Redis sorted set)
→ Better protection against boundary attacks

Redis implementation:
ZADD rate_limit:{customerId} {timestamp} {requestId}
ZREMRANGEBYSCORE rate_limit:{customerId} 0 {now-window}
ZCARD rate_limit:{customerId} → count in window
```

### Leaky Bucket Algorithm

```
Concept:
→ Requests enter a queue (bucket)
→ Processed at constant rate (leak rate)
→ If queue full → request rejected

vs Token Bucket:
→ Token Bucket: allows bursting, constant refill
→ Leaky Bucket: smooths traffic, constant output rate
→ FS: Leaky Bucket for downstream CBS protection
  → CBS can only handle 1000 TPS
  → Leaky Bucket queues payments, releases at 1000/s
  → CBS never overwhelmed regardless of spikes ✅
```

---

## Common API Security Vulnerabilities — FS Context

### OWASP API Security Top 10

```
1. Broken Object Level Authorisation (BOLA/IDOR):
   GET /accounts/ACC-001/balance
   → Attacker changes to: GET /accounts/ACC-002/balance
   → Can they see another customer's balance?
   FS Fix: always verify resource ownership:
   if (!account.getOwnerId().equals(currentUserId)) {
     throw new ForbiddenException();
   }

2. Broken Authentication:
   → Weak JWT validation
   → Not checking token expiry
   → Accepting unsigned tokens (alg:none attack)
   FS Fix: strict JWT validation (shown earlier)
   → Explicitly set allowed algorithms:
   Jwts.parserBuilder().requireAlgorithm("RS256")

3. Excessive Data Exposure:
   → API returns full account object including internal fields
   → Client receives data it shouldn't
   FS Fix: DTOs — never expose domain objects directly
   → Return only fields needed by caller
   → Mask sensitive fields: accountNumber → ****3456

4. Rate Limiting Not Implemented:
   → Attacker can brute-force PIN, enumerate accounts
   FS Fix: rate limiting per customer + IP (covered above)

5. Function Level Authorisation:
   → Customer calls admin endpoint DELETE /accounts/{id}
   FS Fix: role-based checks at every endpoint:
   @PreAuthorize("hasRole('ADMIN')")
   public void deleteAccount(String accountId) { ... }

6. Mass Assignment:
   PATCH /accounts/ACC-001
   { "balance": 1000000, "isAdmin": true }
   → Attacker sets their own balance!
   FS Fix: explicit field mapping — never bind request
   body directly to domain object
```

---

## Sensitive Data in Logs — FS Critical

```
Problem:
log.info("Processing payment for customer {} account {} amount {}",
  customerName, accountNumber, amount);
→ customerName + accountNumber in logs ❌
→ Logs shipped to ELK/Splunk — many people have access
→ Regulatory violation — PII in logs ❌

Solution 1 — Mask at logging layer:
log.info("Processing payment for customer {} account {} amount {}",
  mask(customerName),     // "J*** S****"
  maskAccount(accountNumber), // "****3456"
  amount);                // amount is NOT PII

Solution 2 — Structured logging with MDC:
MDC.put("customerId", customerId);   // tokenised ID, not name
MDC.put("correlationId", UUID.randomUUID().toString());
log.info("Processing payment amount={}", amount);
// Never log name, account number, sort code, NI number

Solution 3 — Log scrubbing (defence in depth):
→ Logstash filter: mask patterns matching account numbers
→ Regex: \d{8} → ****XXXX (8-digit account numbers)
→ Catches accidental PII logging ✅

FS Logging Rules:
→ NEVER log: name, address, account number, sort code,
  card number, NI number, date of birth
→ ALWAYS log: tokenised customerId, correlationId,
  amount (not PII), timestamp, outcome
→ ALWAYS include: correlation ID for distributed tracing
```

---

## Correlation ID — Security + Observability

```
Every request gets a unique correlation ID:
→ Generated at API Gateway (or first service)
→ Propagated through all downstream services
→ Included in every log entry
→ Included in response headers (for debugging)

FS value:
→ Trace a payment journey across 8 services
→ FCA audit: "show me everything that happened
  for payment PAY-001" → search by correlationId
→ Incident investigation: find all logs for
  a suspicious request

Implementation:
// API Gateway generates:
String correlationId = UUID.randomUUID().toString();
request.addHeader("X-Correlation-ID", correlationId);

// Each service propagates:
@Component
public class CorrelationIdFilter implements Filter {
  public void doFilter(request, response, chain) {
    String correlationId = request.getHeader("X-Correlation-ID");
    if (correlationId == null) {
      correlationId = UUID.randomUUID().toString();
    }
    MDC.put("correlationId", correlationId);
    response.addHeader("X-Correlation-ID", correlationId);
    chain.doFilter(request, response);
    MDC.clear();
  }
}

// Every log automatically includes correlationId via MDC
```

---

## ✅ Security Architecture — Revision Checklist

### Zero Trust
- [ ] Can explain never trust, always verify with FS example
- [ ] Know 3 Zero Trust principles — verify explicitly, least privilege, assume breach
- [ ] Can describe full request flow with Zero Trust controls

### OAuth2 / OIDC / JWT
- [ ] Know difference: OAuth2 vs OIDC vs JWT
- [ ] Can explain JWT structure — header, payload, signature
- [ ] Know RS256 vs HS256 — why RS256 in FS
- [ ] Can walk through Authorization Code + PKCE flow
- [ ] Can walk through Client Credentials flow
- [ ] Know local validation vs token introspection — when each
- [ ] Know what to validate in JWT — signature, exp, iss, aud, jti

### mTLS
- [ ] Know TLS vs mTLS difference
- [ ] Can explain mTLS handshake step by step
- [ ] Know SPIFFE identity format
- [ ] Know how Istio automates mTLS — Citadel, sidecar
- [ ] Know certificate rotation — zero downtime approach

### Secrets Management
- [ ] Know static vs dynamic secrets — Vault dynamic secrets
- [ ] Know Vault integration with GKE — Vault Agent sidecar
- [ ] Know GCP Secret Manager — when vs Vault
- [ ] Know why environment variables are not sufficient

### Data Security
- [ ] Know encryption at rest, in transit, in use
- [ ] Know field-level encryption — when and how
- [ ] Know tokenisation vs encryption — when each
- [ ] Know CMEK + crypto shredding for GDPR

### API Security
- [ ] Know API Gateway security functions — all 7
- [ ] Can explain Token Bucket, Sliding Window, Leaky Bucket
- [ ] Know OWASP API Top 10 — BOLA, mass assignment, etc
- [ ] Know PII masking in logs — rules and implementation
- [ ] Know correlation ID — purpose and implementation

---
Great questions — these are exactly the kind of depth questions that come up at JPMC/Goldman senior interviews. Let me go deep on all of them.

---

# 🔐 OAuth2 vs OIDC vs Tokens — Complete Deep Dive

---

## 1️⃣ OAuth2 vs OIDC — The Real Difference

### The Analogy First

```
OAuth2 = A valet parking ticket
→ "This person is allowed to park my car"
→ Proves PERMISSION — not who the person is
→ The valet doesn't know your name
→ Just knows: you're allowed to do this specific thing

OIDC = A passport + valet ticket
→ "This is John Smith, and he's allowed to park my car"
→ Proves IDENTITY + PERMISSION
→ Adds WHO on top of WHAT
```

---

### OAuth2 — What It Actually Is

```
OAuth2 is an AUTHORISATION framework only:
→ Answers: "What are you allowed to do?"
→ Does NOT answer: "Who are you?"

OAuth2 defines:
→ Flows (grant types) for obtaining tokens
→ Token types (access token, refresh token)
→ Scopes — what permissions the token grants
→ Does NOT define: token format, user identity, login

OAuth2 Tokens:
→ Access Token — proves you're authorised to do something
→ Refresh Token — used to get new access token
→ Format: NOT defined by OAuth2 spec
  → Can be opaque string (random bytes)
  → Can be JWT
  → Depends on implementation
```

---

### OIDC — What It Adds

```
OIDC (OpenID Connect) = OAuth2 + Identity Layer:
→ Built ON TOP of OAuth2
→ Answers: "Who are you?" + "What are you allowed to do?"

OIDC adds:
→ ID Token (always JWT) — proves user identity
→ UserInfo endpoint — fetch user profile
→ Standard claims — sub, email, name, phone
→ Discovery endpoint — /.well-known/openid-configuration
→ Standard scopes — openid, profile, email, phone

OIDC flows = OAuth2 flows + ID Token in response

Tokens in OIDC:
→ ID Token (JWT) — WHO you are — for the CLIENT app
→ Access Token — WHAT you can do — for the API
→ Refresh Token — get new tokens when expired
```

---

### Side-by-Side Comparison

```
                    OAuth2              OIDC
                    ──────              ────
Purpose:            Authorisation       Authorisation + Authentication
Answers:            What can you do?    Who are you + what can you do?
Token issued:       Access + Refresh    Access + Refresh + ID Token
ID Token:           ❌ Not defined      ✅ Always JWT
User info:          ❌ Not defined      ✅ /userinfo endpoint
Token format:       Not specified       ID Token = always JWT
Use case:           API access          Login + API access
Example:            "Allow app to       "Log in as John Smith
                    read my calendar"   and access his calendar"

FS Example:
OAuth2 only:
→ Payment Service gets access to CBS API
→ No user identity needed — machine-to-machine

OIDC:
→ Customer logs in to mobile banking
→ Bank needs to know WHO the customer is
→ ID Token: { sub: "C-UK-001", name: "John Smith" }
→ Access Token: grants access to payment APIs
```

---

## 2️⃣ Opaque Token vs JWT — Deep Comparison

### Opaque Token

```
What it looks like:
"aB3xK9mR2pL8nQ5wE7vY4uI6oT1sZ0cF"
→ Random string — no information inside
→ Meaningless without looking it up

How it works:
→ Auth Server generates random string
→ Stores in DB/Redis: token → { userId, scopes, expiry }
→ Issues token to client

Validation by API:
→ API receives opaque token
→ API calls Auth Server: POST /introspect { token: "aB3x..." }
→ Auth Server looks up token in DB
→ Returns: { active: true, sub: "C-UK-001", scope: "payments:write" }
→ API processes request

Properties:
✅ Can be revoked instantly — delete from DB
✅ No sensitive data in token itself
✅ Short token string — small payload
❌ Every validation = network call to Auth Server
❌ Auth Server becomes bottleneck + SPOF
❌ Higher latency per request

FS Use cases:
→ Refresh tokens — always opaque, never JWT
→ High-security operations requiring instant revocation
→ Internal tokens where network call acceptable
```

---

### JWT (JSON Web Token)

```
What it looks like:
eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.
eyJzdWIiOiJDLVVLLTAwMSIsImlzcyI6Imh0dHBzOi8vYXV0aC5iYW5rLmNvbSJ9.
SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c

Three parts separated by dots:
Part 1: eyJhbGci... = Base64(Header)
Part 2: eyJzdWIi... = Base64(Payload/Claims)
Part 3: SflKxwRJ... = Signature

Decoded Header:
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "key-2024-01"  ← key ID for rotation
}

Decoded Payload:
{
  "sub": "C-UK-001",                    ← subject (user ID)
  "iss": "https://auth.bank.com",       ← issuer
  "aud": "payment-service",             ← intended audience
  "exp": 1705312800,                    ← expiry (unix timestamp)
  "iat": 1705309200,                    ← issued at
  "nbf": 1705309200,                    ← not valid before
  "jti": "550e8400-e29b-41d4-a716",    ← unique token ID
  "scope": "payments:write accounts:read",
  "jurisdiction": "UK",
  "roles": ["customer"],
  "session_id": "sess-abc123"
}

Signature:
→ RS256: RSA(SHA256(Base64(header) + "." + Base64(payload)),
            privateKey)
→ Self-contained — no DB lookup needed

Validation:
→ API receives JWT
→ Decodes header → finds kid → fetches public key
→ Verifies signature locally — NO network call ✅
→ Checks claims: exp, iss, aud
→ Done — all local ✅

Properties:
✅ Stateless — no DB lookup per request
✅ Self-contained — all info inside token
✅ Fast — local validation only
✅ Scalable — no Auth Server bottleneck
❌ Cannot revoke instantly — must wait for expiry
❌ Sensitive data inside token (though Base64 not encrypted)
❌ Larger payload than opaque token
```

---

### Which to Use When — FS Decision

```
Access Token:
→ Short-lived (15 minutes): JWT ✅
  → Even if stolen, expires in 15 min
  → Fast validation — no Auth Server call
  → Scale — millions of validations per second

→ Long-lived (hours): consider opaque
  → Long JWT = long window for misuse
  → Revocation not possible with long JWT

Refresh Token:
→ ALWAYS opaque — never JWT ✅
  → Long-lived (30 days)
  → Must be revocable instantly
  → Stored securely — one-time or rotating use
  → JWT refresh token = cannot revoke = dangerous ❌

ID Token:
→ ALWAYS JWT (OIDC specification)
  → Contains user identity claims
  → Client reads claims — JWT format required
  → Short-lived — single use at login

FS Standard:
Access Token: JWT, 15-minute lifetime ✅
Refresh Token: Opaque, 30-day lifetime, rotating ✅
ID Token: JWT, 5-minute lifetime ✅
API Keys: Opaque, long-lived, for M2M ✅
```

---

## 3️⃣ How Public Key Verifies Signature — Deep Explanation

This confuses many people. Let me explain clearly.

### Asymmetric Cryptography — The Foundation

```
Key pair generated:
Private Key: kept SECRET by Auth Server — never shared
Public Key:  shared OPENLY — anyone can have it

Mathematical relationship:
→ Anything signed with Private Key
  can be verified with Public Key ✅
→ Cannot derive Private Key from Public Key
  (computationally infeasible) ✅
→ Cannot sign with Public Key ✅
→ Cannot forge a signature without Private Key ✅
```

### How RS256 Signing Works Step by Step

```
Auth Server (has Private Key):

Step 1: Build token content
  header = Base64Url({"alg":"RS256","kid":"key-1"})
  payload = Base64Url({"sub":"C-UK-001","exp":...})
  message = header + "." + payload

Step 2: Hash the message
  hash = SHA256(message)
  → "3a7f9b2c4e8d1f6a..."  (32 bytes)
  → Fixed length regardless of message size
  → Unique to this exact message

Step 3: Encrypt the hash with Private Key
  signature = RSA_encrypt(hash, privateKey)
  → "SflKxwRJSMeKKF2QT4..."

Step 4: Append signature to token
  JWT = header + "." + payload + "." + signature
```

### How Public Key Verifies — The Magic

```
Payment Service (has Public Key only):

Receives JWT: header.payload.signature

Step 1: Separate components
  received_header = "eyJhbGci..."
  received_payload = "eyJzdWIi..."
  received_signature = "SflKxwRJ..."

Step 2: Rebuild the message
  message = received_header + "." + received_payload

Step 3: Hash the message (same algorithm)
  computed_hash = SHA256(message)
  → "3a7f9b2c4e8d1f6a..."

Step 4: Decrypt the signature with PUBLIC Key
  decrypted_hash = RSA_decrypt(received_signature, publicKey)
  → "3a7f9b2c4e8d1f6a..."

Step 5: Compare hashes
  if (computed_hash == decrypted_hash):
    VALID — signature is authentic ✅
  else:
    INVALID — token tampered or forged ❌

Why this works:
→ Only Private Key can produce a signature that
  decrypts to the correct hash with Public Key
→ Attacker without Private Key CANNOT produce
  a valid signature for any modified payload
→ If attacker changes payload:
  computed_hash changes ≠ decrypted_hash → INVALID ✅
```

### Visual Explanation

```
AUTH SERVER                      PAYMENT SERVICE
───────────────                  ───────────────
Has: Private Key                 Has: Public Key only
                                 (fetched from JWKS endpoint)

Signs JWT:                       Verifies JWT:
1. Build message                 1. Receive JWT
2. SHA256(message) = hash_A      2. Separate parts
3. RSA(hash_A, privateKey)       3. SHA256(header.payload) = hash_B
   = signature                   4. RSA_decrypt(signature, publicKey)
4. Append signature to JWT          = hash_C
                                 5. hash_B == hash_C? ✅ Valid
                                    hash_B != hash_C? ❌ Tampered
```

### JWKS Endpoint — Key Distribution

```
Auth Server publishes public keys at:
https://auth.bank.com/.well-known/jwks.json

{
  "keys": [
    {
      "kty": "RSA",
      "kid": "key-2024-01",        ← key ID
      "use": "sig",                ← usage: signing
      "alg": "RS256",
      "n": "0vx7agoebGcQ...",      ← RSA modulus (public key)
      "e": "AQAB"                  ← RSA exponent (public key)
    },
    {
      "kty": "RSA",
      "kid": "key-2024-02",        ← second key (rotation)
      ...
    }
  ]
}

Payment Service:
→ Fetches JWKS on startup → caches keys
→ JWT arrives with kid: "key-2024-01"
→ Finds key in cache → verifies signature
→ Refreshes cache periodically (every hour)
→ Unknown kid → refresh cache immediately
  (handles key rotation) ✅
```

---

## 4️⃣ How JWT Prevents Common Attacks

### Attack 1 — Token Tampering

```
Attacker wants to change their balance:
Original payload: { "sub": "C-UK-001", "role": "customer" }
Attacker changes: { "sub": "C-UK-001", "role": "admin" }

Attack:
→ Decode JWT (Base64 is NOT encryption — anyone can decode)
→ Modify payload
→ Re-encode payload
→ Send modified JWT

Why it FAILS:
→ Signature was computed on ORIGINAL payload
→ Modified payload produces DIFFERENT hash
→ computed_hash ≠ decrypted_hash → INVALID ❌
→ Attacker cannot forge new signature without private key
→ Token rejected ✅

Defence:
→ RS256 signature — mathematically impossible to forge ✅
→ ALWAYS verify signature before trusting any claim ✅
```

### Attack 2 — Algorithm Confusion (alg:none)

```
Attack:
→ Attacker decodes JWT header
→ Changes: { "alg": "RS256" } → { "alg": "none" }
→ Removes signature
→ Sends: header.payload. (empty signature)
→ Some naive implementations accept unsigned tokens!

Why it WORKS on vulnerable implementations:
→ Library checks: alg == "none" → skip signature check
→ Payload accepted as valid — no verification! ❌

Defence:
→ EXPLICITLY specify allowed algorithms:
  Jwts.parserBuilder()
    .requireAlgorithm("RS256")  // only RS256 accepted
    .build()
→ NEVER accept "none" algorithm ✅
→ NEVER use algorithm from token header to determine
  which algorithm to use — always use configured algorithm ✅
```

### Attack 3 — Token Stealing

```
Attack:
→ Attacker intercepts JWT (network sniffing, XSS, log leakage)
→ Uses stolen token to impersonate victim

Why it partially WORKS:
→ JWT is self-contained — no binding to original client
→ Attacker can use token from anywhere until expiry

Defences:
1. Short expiry (15 minutes):
   → Stolen token usable for max 15 minutes
   → Limited attack window ✅

2. HTTPS everywhere:
   → Token cannot be intercepted in transit ✅

3. Secure storage on client:
   → Web: HttpOnly cookie (not accessible to JavaScript)
   → Mobile: secure enclave storage
   → Never: localStorage (XSS risk) ❌

4. Token binding (advanced):
   → Bind token to client's TLS session
   → Token useless if used from different TLS session ✅
   → Not widely implemented yet

5. Refresh token rotation:
   → Each refresh token use → invalidate old, issue new
   → If stolen refresh token used → legitimate user
     gets invalid token error → detects theft ✅

6. Audience claim (aud):
   → Token issued for: aud = "payment-service"
   → If attacker sends to: "account-service"
   → aud check fails → rejected ✅
   → Token cannot be replayed to different services

7. jti (JWT ID) + replay detection:
   → Each token has unique jti
   → Short-lived tokens: store used jtis in Redis with TTL
   → If same jti seen twice → replay attack detected ✅
   → Impractical for long token lifetimes
```

### Attack 4 — JWT Confusion (RS256 → HS256)

```
Attack:
→ Server normally uses RS256
→ Attacker changes header: "alg": "RS256" → "alg": "HS256"
→ Signs token with PUBLIC KEY (which is known to attacker)
→ Sends to server

Vulnerable server behaviour:
→ Sees alg=HS256 in header
→ Uses configured "public key" as HS256 shared secret
→ Verifies HS256 signature using public key
→ Attacker signed with public key → verification passes! ❌

Defence:
→ Same as alg:none: NEVER use algorithm from token
→ Always use explicitly configured algorithm:
  .requireAlgorithm("RS256") ✅
→ Use well-maintained JWT library — most modern
  libraries prevent this by default ✅
```

### Attack 5 — Token Expiry Not Checked

```
Attack:
→ Attacker obtains expired token
→ Sends to API
→ Naive API trusts token without checking exp claim

Defence:
→ ALWAYS check exp claim:
  if (claims.getExpiration().before(new Date())) {
    throw new ExpiredJwtException("Token expired");
  }
→ Modern JWT libraries check automatically ✅
→ Clock skew tolerance: max 30 seconds
  (allow small time difference between servers) ✅
```

### Attack 6 — CSRF (Cross-Site Request Forgery)

```
Attack:
→ Victim logged into bank.com (has valid cookie with JWT)
→ Victim visits attacker.com
→ Attacker page sends: POST bank.com/payment (with victim's cookie)
→ Browser automatically sends cookie → payment executed! ❌

Why relevant for JWT in cookies:
→ JWT in HttpOnly cookie → protected from XSS ✅
→ BUT HttpOnly cookie still sent on cross-site requests ❌

Defences:
1. SameSite cookie attribute:
   Set-Cookie: token=eyJ...; SameSite=Strict
   → Browser never sends cookie in cross-site requests ✅

2. CSRF token (double submit):
   → Server sets CSRF token in non-HttpOnly cookie
   → Client reads CSRF token, sends in header
   → Server validates header == cookie value
   → Attacker can't read cookie → can't set header ✅

3. JWT in Authorization header (not cookie):
   Authorization: Bearer eyJ...
   → Browser never auto-sends headers cross-site ✅
   → But: requires JavaScript to send → XSS risk
   → For APIs consumed by mobile apps: Bearer header ✅
   → For browser web apps: HttpOnly cookie + SameSite ✅
```

---

## 5️⃣ Service-to-Service — API Key vs Client ID/Secret vs Client Credentials

You raised a great distinction — let me clarify all three.

### API Key

```
What it is:
→ Simple static string: "sk_live_aB3xK9mR2pL8nQ5w"
→ Issued by API provider to calling service
→ Sent in every request header:
  X-API-Key: sk_live_aB3xK9mR2pL8nQ5w
  OR
  Authorization: ApiKey sk_live_aB3xK9mR2pL8nQ5w

How it works:
→ API Gateway receives request
→ Validates API key against stored keys
→ Identifies caller: "this key belongs to Trade Service"
→ Applies rate limits, access controls for that key
→ No token exchange needed — key IS the credential

Properties:
✅ Simple — no token exchange flow
✅ Easy to implement and use
✅ Good for external third-party access
✅ Easy to revoke — delete the key
❌ Long-lived — same key used until rotated
❌ If leaked — attacker has permanent access until rotated
❌ No built-in expiry mechanism
❌ Not suitable for fine-grained scopes
❌ No identity beyond "this key = this service"

FS Use cases:
→ Third-party fintech accessing bank APIs (Open Banking)
→ External payment processors
→ Simple B2B integrations where OAuth2 is overkill
→ Internal non-critical service integrations
```

### Client ID + Client Secret

```
What it is:
→ client_id: public identifier "payment-service-prod"
→ client_secret: private password "xK9mR2pL8nQ5w..."
→ Together: OAuth2 client credentials

How it works (two ways):

Way 1 — HTTP Basic Authentication:
→ Base64(client_id:client_secret) sent in header:
  Authorization: Basic cGF5bWVudDp4SzltUjJwTDhuUTU=
→ Used in token endpoint request

Way 2 — Request Body:
→ Sent in POST body to token endpoint:
  POST /token
    grant_type=client_credentials&
    client_id=payment-service&
    client_secret=xK9mR2pL8nQ5w

What you get back:
→ Access Token (JWT or opaque)
→ client_id/secret NOT sent on every API call
→ Only used ONCE to get access token
→ Access token sent on actual API calls

Properties:
✅ More secure than API key — not sent on every request
✅ Supports scopes — fine-grained permissions
✅ Short-lived tokens — even if token stolen, expires soon
✅ Audit trail — token issuance logged
❌ client_secret is long-lived — same rotation problem as API key
❌ Secret must be securely stored (Vault, Secret Manager)
❌ More complex than API key

FS Use cases:
→ Service-to-service within same organisation
→ When scopes + short-lived tokens needed
→ When OAuth2 infrastructure already in place
```

### OAuth2 Client Credentials Flow — Full Picture

```
Payment Service wants to call Fraud Service:

Step 1: Payment Service fetches token (once, cached)
  POST https://auth.bank.com/token
  Authorization: Basic Base64("payment-service:xK9m...")
  Body: grant_type=client_credentials&
        scope=fraud:score

Step 2: Auth Server validates client credentials
  → Is client_id valid?
  → Is client_secret correct?
  → Is this client allowed to request fraud:score scope?

Step 3: Auth Server issues access token
  {
    "access_token": "eyJhbGci...",  ← JWT
    "token_type": "Bearer",
    "expires_in": 300,              ← 5 minutes
    "scope": "fraud:score"
  }

Step 4: Payment Service calls Fraud Service
  GET /fraud/score
  Authorization: Bearer eyJhbGci...

Step 5: Fraud Service validates JWT locally
  → Verify RS256 signature
  → Check iss = auth.bank.com
  → Check aud = fraud-service
  → Check scope includes fraud:score
  → Check exp not expired
  → Check client_id in claims = payment-service
    (only authorised callers allowed) ✅

Step 6: Payment Service caches token until 30s before expiry
  → Fetches new token proactively
  → API calls never delayed waiting for token ✅
```

### The Three Options — Comparison

| Factor | API Key | Client ID/Secret | Client Credentials (OAuth2) |
|---|---|---|---|
| **Sent on every request?** | ✅ Yes | ❌ No — only to get token | ❌ No — only to get token |
| **Token lifetime** | Long-lived | Long-lived secret, short-lived token | Short-lived token (minutes) |
| **Revocation** | Delete key | Revoke client or token | Revoke token or client |
| **Scopes** | ❌ Limited | ✅ Full OAuth2 scopes | ✅ Full OAuth2 scopes |
| **Complexity** | Low | Medium | Medium-High |
| **Audit trail** | Basic | Good | Full OAuth2 audit |
| **FS Internal M2M** | ❌ Not ideal | ✅ Acceptable | ✅ Best practice |
| **Third-party APIs** | ✅ Common | ✅ Common | ✅ Best |
| **If leaked** | Long exposure | Short exposure (token expires) | Short exposure (token expires) |

---

### API Gateway — How It Uses These

```
External customer request:
Client → API Gateway → validates JWT (Bearer token)
→ JWT contains: sub, scope, jurisdiction
→ Gateway enforces: rate limits per customer
→ Gateway routes to: correct microservice
→ Microservice validates JWT again (defence in depth)

Internal service-to-service (two patterns):

Pattern A: API Gateway in path (common in FS)
Service A → API Gateway → Service B
→ Service A includes JWT (from client credentials flow)
→ API Gateway validates JWT
→ Forwards to Service B
→ Service B validates JWT again

Pattern B: Direct service-to-service (bypasses gateway)
Service A → Service B directly (via ILB)
→ Uses mTLS for transport security
→ Uses JWT (client credentials) for authorisation
→ API Gateway NOT in path — internal traffic only

FS Best Practice:
→ External traffic: ALWAYS through API Gateway
→ Internal traffic: direct via ILB + mTLS + JWT
→ Never expose internal services externally
  without API Gateway ✅

API Key vs JWT at API Gateway:
→ API Key: API Gateway validates directly (no Auth Server call)
  → Good for: third-party partners, simple M2M
→ JWT: API Gateway validates via JWKS (local, fast)
  → Good for: customer requests, internal services
  → Auth Server not in critical path ✅
```

---

## 🔑 Key Takeaways — Complete Summary

| Question | Answer |
|---|---|
| **OAuth2 vs OIDC** | OAuth2 = authorisation only. OIDC = OAuth2 + identity (adds ID Token + UserInfo) |
| **Opaque vs JWT** | Opaque = random string, DB lookup needed, instantly revocable. JWT = self-contained, local validation, cannot revoke instantly |
| **Refresh token format** | Always opaque — never JWT. Must be revocable |
| **How public key verifies** | RSA decrypt signature with public key → get hash. SHA256 payload → get hash. If equal → valid. Only private key can produce matching signature |
| **RS256 vs HS256** | RS256: asymmetric — private signs, public verifies. HS256: symmetric — shared secret. Always RS256 in FS |
| **Algorithm confusion attack** | Never use alg from token header. Always explicitly configure RS256 |
| **Token stealing defence** | Short expiry (15 min) + HTTPS + secure storage + SameSite cookie + aud claim |
| **API Key vs Client Credentials** | API Key: sent every request, simple, long-lived. Client Credentials: exchange for short-lived JWT, not sent every request, scoped |
| **CSRF with JWT** | JWT in HttpOnly cookie + SameSite=Strict. Or JWT in Auth header (mobile) |

---

Perfect! 3 questions, one at a time, progressively harder! 💪

---

# 🎤 Security Architecture Drill — Question 1 of 3

### "You are the Engineering Manager for a **Mobile Banking Platform** at a UK bank. The platform serves 5 million customers via iOS and Android apps.

**Current authentication flow:**
- Customer logs in with username + password
- Server validates credentials
- Server returns a JWT access token (HS256 signed, 24-hour expiry)
- JWT stored in mobile app's local storage
- JWT sent on every API request: `Authorization: Bearer eyJ...`
- Same JWT used for all operations — viewing balance AND initiating payments
- No refresh token mechanism
- No MFA

**Your security team raises 5 critical findings:**

1. *"HS256 signing with a shared secret — the same secret is used across 12 microservices"*
2. *"24-hour JWT expiry — a stolen token gives attacker full access for up to 24 hours"*
3. *"JWT stored in local storage — vulnerable to XSS attacks"*
4. *"Same token used for viewing balance AND initiating payments — no step-up authentication"*
5. *"No MFA — account takeover via credential stuffing is trivial"*

**For each finding:**
- Explain the specific attack vector — what exactly can an attacker do?
- Provide the specific technical fix
- Explain any tradeoffs your fix introduces"**

---

> 💡 *Tip 1: Finding 1 — think about what happens if ONE of the 12 microservices is compromised. What can the attacker do with the shared HS256 secret?*

> 💡 *Tip 2: Finding 2 — short expiry + refresh token. But what format should refresh token be? And what is refresh token rotation?*

> 💡 *Tip 3: Finding 3 — local storage vs HttpOnly cookie vs secure enclave. What's the right storage for mobile?*

> 💡 *Tip 4: Finding 4 — "step-up authentication" — what is it and when is it used in banking?*

> 💡 *Tip 5: Finding 5 — MFA types. Which MFA is strongest? Which is weakest? What's the FS standard?*

Really solid instincts! You covered the core concepts well. Here's your detailed feedback 👇

---

## ✅ Feedback — Question 1

### What you got right
- ✅ **Finding 2** — Token stealing + MITM + 24hr blast radius — correct
- ✅ **Short-lived JWT + refresh** — correct fix
- ✅ **Finding 4** — Scope too broad — correct identification
- ✅ **Step-up auth for payments** — correct fix
- ✅ **Finding 3** — XSS + local storage vulnerability — correct
- ✅ **HttpOnly cookie** — correct fix
- ✅ **SameSite for CSRF** — correct

### Your Score: **5/10** — You covered 3 of 5 findings partially. Here's what's missing 💡

---

### Finding 1 — HS256 Shared Secret — YOU MISSED ENTIRELY

This is the most critical finding and you didn't address it:

> *"HS256 uses a single shared secret for both signing AND verifying. That secret is distributed to all 12 microservices. Attack vector: if ANY one of the 12 microservices is compromised — attacker gets the shared secret. With the shared secret, attacker can:*
> *1. Forge ANY JWT for ANY user — create admin tokens, impersonate any customer*
> *2. Sign malicious tokens that all 12 services will accept*
> *3. The entire platform is compromised from a single service breach*
>
> *Fix: Switch to RS256 asymmetric signing:*
> *→ Auth Server holds private key — never distributed*
> *→ All 12 services get public key only — safe to distribute*
> *→ Even if all 12 services compromised → attacker has public key only*
> *→ Cannot forge tokens without private key ✅*
> *→ JWKS endpoint: services fetch public keys automatically*
>
> *Tradeoff: slightly more complex key management — JWKS rotation needed. Mitigated by automatic JWKS refresh."*

---

### Finding 2 — Good but incomplete

You identified the problem — add the specific fix:

> *"Fix:*
> *→ Access token: JWT, RS256, 15-minute expiry*
> *→ Refresh token: OPAQUE string (never JWT), 30-day expiry, stored server-side*
> *→ Refresh token rotation: every time refresh token used → old one invalidated, new one issued*
> *→ If stolen refresh token used by attacker → legitimate user gets invalid token error → bank detects theft, forces re-login*
>
> *Tradeoff: slightly more complex client logic — must handle token refresh. Mitigated by SDK/interceptor handling refresh transparently."*

---

### Finding 3 — Good but mobile context missed

You correctly said HttpOnly cookie — but this is a **mobile app**, not a web browser:

> *"For mobile apps: HttpOnly cookie doesn't apply — mobile apps don't use browser cookies.*
> *Correct fix for mobile:*
> *→ iOS: Keychain — hardware-backed secure enclave storage*
> *→ Android: EncryptedSharedPreferences or Android Keystore*
> *→ Both: OS-level protection — other apps cannot access*
> *→ Never: SharedPreferences (Android) or UserDefaults (iOS) in plaintext*
>
> *For web app (if applicable): HttpOnly + Secure + SameSite=Strict cookie ✅*
>
> *Tradeoff: requires platform-specific implementation. Worth it — protects against malware on compromised devices."*

---

### Finding 4 — Good concept, needs technical detail

You mentioned step-up auth — now make it specific:

> *"Step-up authentication flow:*
> *→ Initial login: issues scoped token: scope=accounts:read*
> *→ Customer initiates payment: API returns 401 with:*
>   *{ "error": "insufficient_scope", "required_scope": "payments:write" }*
> *→ App prompts: re-authenticate for this payment (biometric/PIN/SMS OTP)*
> *→ After re-auth: issues elevated token: scope=payments:write, max_age=300 (5 min)*
> *→ Payment completes → elevated token discarded*
>
> *FS Standard: PSD2 Strong Customer Authentication (SCA) mandates step-up for:*
> *→ Any payment > £30*
> *→ New payee addition*
> *→ Sensitive account changes*
>
> *Tradeoff: additional friction for customers on payments. Mitigated by biometric — fast and seamless."*

---

### Finding 5 — YOU MISSED ENTIRELY

You didn't address MFA at all:

> *"No MFA = credential stuffing vulnerability:*
> *Attack: attacker buys leaked username/password list (dark web)*
> *Tries each credential against banking app*
> *Password reused from another breach → immediate account takeover ❌*
>
> *Fix — MFA types ranked by strength:*
>
> *Weakest → Strongest:*
> *❌ SMS OTP — vulnerable to SIM swapping (attacker ports victim's number)*
> *⚠️ Email OTP — better than SMS, still phishable*
> *✅ TOTP (Google Authenticator) — time-based, not phishable via SIM swap*
> *✅✅ Push notification (bank app approval) — best UX, strong security*
> *✅✅✅ FIDO2/WebAuthn (biometric/hardware key) — phishing-resistant, FS gold standard*
>
> *FS Standard: PSD2 SCA requires at least 2 of 3:*
> *→ Something you know (PIN/password)*
> *→ Something you have (phone/hardware key)*
> *→ Something you are (biometric)*
>
> *Implementation: FIDO2 biometric for mobile — Touch ID/Face ID*
> *→ Private key in device secure enclave — never leaves device*
> *→ Even bank cannot extract it*
> *→ Phishing-proof — credential tied to specific domain*
>
> *Tradeoff: device dependency — if phone lost, recovery process needed.*
> *Mitigate: backup codes + identity verification for account recovery."*

---

## 🏆 Complete Model Answer (9/10)

> **Finding 1 — HS256 Shared Secret:**
> *"Compromising any one of 12 services gives attacker the shared secret — they can forge tokens for ANY user. Fix: RS256 asymmetric signing. Private key stays on Auth Server only. All services get public key via JWKS endpoint. Compromise of any service → useless public key only."*

> **Finding 2 — 24hr Expiry:**
> *"Stolen token = 24hr full access window. Fix: 15-minute JWT access token + 30-day opaque refresh token with rotation. Refresh token rotation detects theft — stolen refresh token use invalidates legitimate user's session, forcing re-login and detection."*

> **Finding 3 — Local Storage:**
> *"XSS attack can steal token from localStorage — malicious script reads token, sends to attacker server. Fix: mobile → iOS Keychain / Android Keystore (hardware-backed secure storage). Web → HttpOnly + Secure + SameSite=Strict cookie. Neither accessible to JavaScript or other apps."*

> **Finding 4 — Broad Scope:**
> *"Token allows payments + balance — attacker with stolen token can initiate payments. Fix: initial token scope=accounts:read only. Payment requires step-up: PSD2 SCA — biometric re-authentication → issues time-limited scope=payments:write token (5 minute expiry). Complies with PSD2 SCA mandate."*

> **Finding 5 — No MFA:**
> *"Credential stuffing: leaked passwords from other breaches used to take over accounts. Fix: FIDO2/WebAuthn biometric — Touch ID/Face ID. Private key in device secure enclave — phishing-proof, SIM-swap-proof. Satisfies PSD2 SCA: something you have (device) + something you are (biometric). SMS OTP weakest — avoid for FS."*



---

> 🔑 **Coaching note:** Your instincts on the findings you covered were correct. The two you missed — HS256 shared secret (most critical!) and MFA — are both high-value in FS interviews. HS256 → RS256 is a fundamental security principle you must nail confidently. MFA types + PSD2 SCA is pure FS domain knowledge that interviewers expect from an EM in banking. Always scan every part of the question — 5 findings = 5 answers needed.

---
# 🎤 Security Architecture Drill — Question 2 of 3

### "You are the Engineering Manager for a **Payment Microservices Platform** at a UK bank running on GCP with GKE. The platform has 15 microservices communicating with each other and with external systems.

**A security audit reveals the following:**

**Finding 1:**
Every microservice has a GCP Service Account key file (JSON) mounted as a Kubernetes secret. These key files are:
- Rotated manually every 6 months
- Stored in the Git repository in an encrypted secrets file
- The same service account has Editor role on the GCP project

**Finding 2:**
Payment Service calls Fraud Service directly using an internal API key hardcoded in application.yml:
```
fraud.service.api-key: xK9mR2pL8nQ5wE7v
```
This same API key has been in use for 3 years unchanged.

**Finding 3:**
Payment Service calls an external Credit Bureau API (Experian). The Experian API credentials (client_id + client_secret) are stored in a Kubernetes ConfigMap in plaintext.

**Finding 4:**
All 15 microservices can freely communicate with each other — no network policies enforced. A security researcher demonstrated that a compromised Notification Service could directly call the Core Banking Adapter and inject fraudulent transactions.

**Finding 5:**
Developers can SSH into production GKE nodes directly using their personal SSH keys. Last month a developer accidentally deleted a production secret while debugging.

**For each finding — explain the attack vector, the specific GCP/GKE fix, and how you would implement it."**

---

> 💡 *Tip 1: Finding 1 — three separate problems here: key files, manual rotation, and over-privileged service account. Address all three*

> 💡 *Tip 2: Finding 2 — hardcoded credentials + never rotated + API key format. What replaces API keys for internal service-to-service? What manages the secret?*

> 💡 *Tip 3: Finding 3 — external credentials in ConfigMap plaintext. Two problems: storage location AND format. What's the right GCP service for this?*

> 💡 *Tip 4: Finding 4 — default-allow network policy. What Kubernetes feature enforces service-to-service communication rules? What's the default-deny pattern?*

> 💡 *Tip 5: Finding 5 — direct SSH to production. Which GCP service eliminates SSH entirely? What process ensures auditability?*

Really strong answer! You covered all 5 findings with good technical depth. Here's your detailed feedback 👇

---

## ✅ Feedback — Question 2

### What you got right
- ✅ **Finding 1** — Workload Identity — correct, no key files
- ✅ **Least privilege** — dedicated SA per service — correct
- ✅ **Finding 2** — Secrets Manager/Vault — correct
- ✅ **Finding 3** — Secret Manager/Vault — correct
- ✅ **Multiple fetch mechanisms** — sidecar, init container, operator — correct
- ✅ **Finding 4** — Network policies + namespace isolation — correct
- ✅ **Finding 5** — Private GKE + IAP + JIT + audit trail — correct

### Your Score: **7/10** — Good across all 5! Here's the push to 9/10 💡

---

### Finding 1 — Missing 3 specific gaps

**Gap 1 — Git storage of secrets:**
> *"Storing encrypted secrets in Git is dangerous — encryption key management becomes the new problem, and git history retains secrets forever even after deletion. Fix: remove ALL secrets from Git entirely. Use Sealed Secrets or External Secrets Operator to manage K8s secrets from Secret Manager — nothing sensitive ever touches Git."*

**Gap 2 — Manual rotation not sufficient:**
> *"Manual 6-month rotation is too infrequent and operationally risky — humans forget, rotation windows get missed. Fix: Workload Identity tokens rotate automatically every hour — zero manual intervention. For any remaining static credentials — Secret Manager automatic rotation via Cloud Functions trigger, not calendar-based manual process."*

**Gap 3 — Editor role is catastrophic:**
> *"Editor role on entire GCP project means any compromised pod can modify ANY resource — delete databases, modify firewall rules, access all secrets. This is not just over-privileged — it's a critical misconfiguration. Fix: custom IAM role per service with ONLY the specific permissions needed:*
> ```
> Payment Service SA permissions:
> → pubsub.topics.publish (payment-events topic only)
> → spanner.sessions.create (payments database only)
> → secretmanager.versions.access (payment-service secrets only)
> Nothing else — not even list permissions on other resources ✅
> ```"*

---

### Finding 2 — Missing the service-to-service pattern

You correctly said move to Secret Manager — but the deeper fix is eliminating API keys entirely for internal services:

> *"Hardcoded API key for internal service-to-service is the wrong pattern entirely — not just the storage. Fix has two layers:*
>
> *Layer 1 — Replace API key with mTLS + OAuth2 Client Credentials:*
> *→ Internal services authenticate via Workload Identity + mTLS (Istio)*
> *→ No API key needed — certificate IS the identity*
> *→ Payment Service certificate proves: 'I am payment-service' to Fraud Service*
> *→ Fraud Service verifies certificate — no shared secret needed*
>
> *Layer 2 — If API key must remain (legacy constraint):*
> *→ Store in Secret Manager — NOT application.yml*
> *→ Fetch via External Secrets Operator or Vault Agent sidecar*
> *→ Rotate every 90 days via automated rotation*
> *→ Never in code, never in Git ✅"*

---

### Finding 3 — ConfigMap vs Secret distinction

> *"ConfigMap stores non-sensitive configuration — using it for credentials is fundamentally wrong, not just a storage location problem. Two distinct fixes:*
>
> *Fix 1 — Immediate: move to Kubernetes Secret (base64 encoded)*
> *→ Better than ConfigMap but still not ideal — base64 is not encryption*
>
> *Fix 2 — Correct: External Secrets Operator + GCP Secret Manager*
> *→ Secret Manager stores Experian credentials encrypted at rest with CMEK*
> *→ External Secrets Operator syncs to K8s Secret automatically*
> *→ Pod mounts K8s Secret — never touches Secret Manager directly*
> *→ Rotation: Secret Manager rotation → operator syncs → pod reloads*
>
> *Additional: Experian credentials should use OAuth2 Client Credentials:*
> *→ Short-lived access token (5 minutes)*
> *→ client_secret stored in Secret Manager*
> *→ Token fetched at runtime — not stored anywhere ✅"*

---

### Finding 4 — East-West via API Gateway is wrong for internal

You said all east-west via API Gateway — this needs correction:

> *"All east-west traffic via API Gateway is not practical or correct for internal microservices:*
> *→ API Gateway adds latency on every internal call*
> *→ API Gateway becomes single point of failure*
> *→ Internal service mesh (Istio) is the right solution*
>
> *Correct layered approach:*
>
> *Layer 1 — Kubernetes NetworkPolicy (L4):*
> ```yaml
> # Default deny ALL in payments namespace
> apiVersion: networking.k8s.io/v1
> kind: NetworkPolicy
> metadata:
>   name: default-deny-all
>   namespace: payments
> spec:
>   podSelector: {}
>   policyTypes: [Ingress, Egress]
> ---
> # Explicit allow: payment → fraud only
> kind: NetworkPolicy
> spec:
>   podSelector:
>     matchLabels: {app: payment-service}
>   egress:
>   - to:
>     - namespaceSelector:
>         matchLabels: {name: fraud}
>       podSelector:
>         matchLabels: {app: fraud-service}
>     ports:
>     - port: 8080
> ```
>
> *Layer 2 — Istio AuthorizationPolicy (L7):*
> ```yaml
> # Only payment-service SA can call fraud-service
> apiVersion: security.istio.io/v1beta1
> kind: AuthorizationPolicy
> metadata:
>   name: fraud-service-policy
>   namespace: fraud
> spec:
>   selector:
>     matchLabels: {app: fraud-service}
>   rules:
>   - from:
>     - source:
>         principals:
>         - cluster.local/ns/payments/sa/payment-service-sa
>     to:
>     - operation:
>         methods: [POST]
>         paths: [/fraud/score]
> ```
>
> *Result: notification-service → core-banking-adapter → BLOCKED at both L4 and L7 ✅*
> *No API Gateway in internal path — Istio handles it ✅"*

---

### Finding 5 — Good but add specific GCP services

> *"Three-layer access control:*
>
> *Layer 1 — Private GKE cluster + no public node IPs:*
> *→ Nodes have no public IPs — SSH from internet impossible*
> *→ Master authorised networks: only corporate IP ranges*
>
> *Layer 2 — IAP Tunnel replaces SSH entirely:*
> *→ Developer authenticates via corporate SSO (Google Identity)*
> *→ IAP verifies: correct identity + device posture + group membership*
> *→ Creates encrypted tunnel to private node*
> *→ No firewall rule needed — IAP handles connectivity*
> *→ No SSH key management — identity-based access*
>
> *Layer 3 — Privileged Access Manager (PAM) for JIT:*
> *→ Developer requests: 'need production access for incident'*
> *→ Manager approves in PAM (or auto-approved for P1 incidents)*
> *→ Access granted: 2-hour window, specific namespace only*
> *→ Access automatically revoked after window*
> *→ Every keystroke logged in Cloud Audit Logs — immutable ✅*
>
> *Layer 4 — Never SSH to nodes directly:*
> *→ For pod debugging: kubectl exec via private endpoint*
> *→ For log access: Cloud Logging — no node access needed*
> *→ For metrics: Cloud Monitoring — no node access needed*
> *→ Node SSH should be: last resort break-glass only*
>
> *Prevents the incident:*
> *→ Developer cannot delete secrets without PAM approval*
> *→ Every action audited — immediate detection*
> *→ Time-bounded access — minimum exposure window ✅"*

---

## 🏆 Model Answer Summary (9/10)

> **Finding 1:** *Workload Identity — no key files, hourly automatic token rotation. Custom IAM role per service — not Editor (catastrophic). Remove all secrets from Git — External Secrets Operator syncs from Secret Manager only.*

> **Finding 2:** *Eliminate API key entirely for internal services — mTLS via Istio + Workload Identity certificates replace API keys. Identity proven via certificate. If API key must remain: Secret Manager + 90-day automated rotation.*

> **Finding 3:** *ConfigMap is wrong for secrets (base64 not encryption). External Secrets Operator + Secret Manager + CMEK. Experian uses OAuth2 Client Credentials — short-lived token, client_secret in Secret Manager only.*

> **Finding 4:** *Default-deny NetworkPolicy in every namespace. Istio AuthorizationPolicy — only authorised service accounts can call specific endpoints. NOT via API Gateway — Istio sidecar handles internal L7 authorisation. Notification → Core Banking: blocked at both L4 and L7.*

> **Finding 5:** *Private GKE cluster + IAP Tunnel (no SSH keys) + PAM JIT (2hr time-bounded, manager approved) + full Cloud Audit Log. kubectl exec for debugging — never node SSH. Every action immutably logged.*

---

## 📊 Running Scorecard

| Question | Topic | Score | Trend |
|---|---|---|---|
| Q23 — Security | Mobile Banking Auth | 5/10 | 📉 Missed 2 findings |
| Q24 — Security | GKE Security Audit | 7/10 | 📈 Improving |

---

> 🔑 **Coaching note:** Two key corrections to remember: (1) All east-west via API Gateway is wrong — Istio AuthorizationPolicy handles internal L7 security without API Gateway in the path. (2) Finding 2 — the deeper fix is eliminating API keys for internal services entirely via mTLS + Workload Identity, not just moving the key to Secret Manager. These two points separate a good security answer from a great one at JPMC/Goldman level.

---

# 🎤 Security Architecture Drill — Question 3 of 3 🔥

### The hardest security question — combining Zero Trust + OAuth2 + mTLS + secrets + API security + GDPR in one real FS scenario!

---

### "You are the Engineering Manager at a **tier-1 UK investment bank**. You are designing the security architecture for a new **Open Banking Platform** from scratch.

**What the platform does:**
- Exposes bank APIs to authorised third-party providers (TPPs) — fintechs, payment initiators, account aggregators
- TPPs access customer account data and initiate payments ON BEHALF of customers
- Regulated under **PSD2** — Strong Customer Authentication (SCA) mandatory
- Platform processes **£2 billion in third-party initiated payments daily**
- Serves **500 regulated TPPs** across UK and EU
- Handles **8 million customer consents** — customers authorise specific TPPs to access specific data

**Regulatory requirements:**
- PSD2 SCA — every payment must use 2 of 3 authentication factors
- FCA — full audit trail of every TPP access to customer data
- GDPR — customers can revoke TPP consent at any time — access must stop within seconds
- eIDAS — TPPs must present qualified certificates (QWAC/QSeal) to prove their regulatory status
- Data minimisation — TPP gets ONLY the data the customer consented to

**You are asked to design 5 components:**

1. **TPP Onboarding & Identity** — how do you verify a TPP is genuinely FCA-regulated before giving them API access? What credentials do you issue them?

2. **Customer Consent Flow** — customer authorises TPP to access their account. Walk through the complete technical flow — which OAuth2 grant type, what scopes, how consent is stored and enforced

3. **Consent Revocation** — customer revokes TPP access at 2pm. The TPP has a valid access token expiring at 2:15pm. How do you stop access within seconds — not 15 minutes?

3. **Runtime API Security** — TPP calls your API with a customer's access token. Walk through every security check your API Gateway and microservices perform before returning data

4. **Audit Trail** — FCA requires complete immutable audit of every TPP API call — who accessed what customer data, when, using which consent. How do you design this?"**

---

> 💡 *Tip 1: TPP identity — eIDAS certificates (QWAC) are the regulatory standard. TPPs present these certificates during mTLS. How does your platform verify QWAC certificates? What do you issue after verification?*

> 💡 *Tip 2: Consent flow — this is Authorization Code + PKCE but with important additions. Think about: how are consents scoped? Where are they stored? What happens to the token if consent is revoked?*

> 💡 *Tip 3: Consent revocation — JWT cannot be revoked. How do you solve this? Token introspection? Short expiry? Token binding? Think about the tradeoff between security and performance*

> 💡 *Tip 4: Runtime checks — think in layers. API Gateway first, then microservice, then data layer. Each layer has different checks. Data minimisation — how do you enforce TPP only gets consented fields?*

> 💡 *Tip 5: Audit trail — immutable, tamper-proof, queryable. Think about Kafka + append-only storage + WORM. FCA will ask: show me every time TPP X accessed customer Y's data in the last 7 years*

---

This is a **genuine Principal/Director level PSD2 security design question** — take your time, think through all 5 components! 💪🎯

Fair enough — this is a complex one! Here's the complete model answer:

---

# 🏆 Open Banking Platform — Complete Security Architecture

---

## Component 1 — TPP Onboarding & Identity

### The Problem
```
Anyone can claim to be a regulated TPP
→ Must verify: is this TPP genuinely FCA/NCA regulated?
→ Must issue: credentials that prove TPP identity on every API call
→ Standard: eIDAS regulation defines exactly how
```

### eIDAS Certificates — The Regulatory Standard

```
eIDAS defines two certificate types for Open Banking:

QWAC (Qualified Website Authentication Certificate):
→ Used for: transport layer authentication (mTLS)
→ Proves: TPP identity during HTTPS connection
→ Contains: TPP name, authorisation number, roles
  (AISP=account info, PISP=payment initiation)
→ Issued by: Qualified Trust Service Provider (QTSP)
  e.g. DigiCert, Entrust — regulated by EU/UK
→ Bank verifies: QTSP signature chain → confirms
  TPP is genuinely registered with their NCA (FCA for UK)

QSeal (Qualified Electronic Seal):
→ Used for: signing API request payloads
→ Proves: message came from this specific TPP unmodified
→ Used in: JWS-signed API requests
→ Critical for: payment initiation — proves TPP didn't
  tamper with payment details ✅
```

### TPP Onboarding Flow

```
Step 1: TPP presents QWAC certificate
→ Bank validates QWAC:
  ✓ Certificate signed by trusted QTSP
  ✓ Certificate not expired or revoked (OCSP check)
  ✓ TPP authorisation number in certificate
  ✓ Roles match requested access (AISP/PISP)

Step 2: Bank verifies regulatory status
→ Query FCA register API:
  GET https://register.fca.org.uk/
    services/V0.1/Firms/{authorisation_number}
→ Confirms: TPP is active, not suspended, correct roles
→ Automated daily re-check — suspended TPP = immediate revoke

Step 3: Bank registers TPP in directory
→ Store: TPP profile, QWAC thumbprint, allowed scopes
→ Issue: OAuth2 client credentials (client_id + client_secret)
→ client_id: public identifier for TPP
→ client_secret: stored in Secret Manager — TPP stores securely
→ Also issue: software statement assertion (SSA)
  → JWT signed by bank proving TPP is registered ✅

Step 4: TPP onboarded
→ Dynamic Client Registration (DCR — PSD2 standard):
  POST /register
    {
      "software_statement": "eyJ...",  ← SSA from bank
      "redirect_uris": ["https://tpp.com/callback"],
      "grant_types": ["authorization_code"],
      "token_endpoint_auth_method": "tls_client_auth"
                                    ← mTLS authentication
    }
→ Every API call: TPP presents QWAC in mTLS handshake
→ Bank verifies certificate on EVERY call — not just onboarding ✅
```

---

## Component 2 — Customer Consent Flow

### OAuth2 Authorization Code + PKCE — With Consent Layer

```
PSD2 mandates: customer explicitly authorises each TPP
Grant type: Authorization Code + PKCE
(customer present, redirects needed, strongest security)

Complete flow:

Step 1: TPP initiates consent request
  GET https://auth.bank.com/authorize?
    response_type=code&
    client_id=tpp-fintech-123&
    redirect_uri=https://tpp.com/callback&
    scope=openid accounts:read:balance
          accounts:read:transactions
          accounts:read:details&
    state=random-csrf-token&
    code_challenge=SHA256(verifier)&
    code_challenge_method=S256&
    consent_id=CONSENT-UUID-001&  ← pre-registered consent
    request=eyJ...  ← signed JWT request object (FAPI)

Step 2: Bank shows customer consent screen
  → Clearly shows:
    "Fintech App wants to access:
     ✓ Your account balance
     ✓ Transactions from last 90 days
     ✓ Account details (sort code, account number)
     For: 90 days
     Purpose: account aggregation"
  → Customer authenticates: username + password
  → SCA: biometric/TOTP (PSD2 mandatory) ✅

Step 3: Customer approves
  → Bank creates consent record:
    {
      consentId: "CONSENT-UUID-001",
      customerId: "C-UK-001",
      tppId: "tpp-fintech-123",
      scopes: ["accounts:read:balance",
               "accounts:read:transactions:90days",
               "accounts:read:details"],
      status: "AUTHORISED",
      expiresAt: "2024-04-15T00:00:00Z",  ← 90 days
      createdAt: "2024-01-15T10:00:00Z",
      authorisedAt: "2024-01-15T10:02:00Z"
    }
  → Stored in: Cloud Spanner (ACID, consistent) ✅
  → Consent ID embedded in authorization code

Step 4: Bank issues authorization code
  → Redirects to TPP:
    https://tpp.com/callback?
      code=AUTH-CODE-XYZ&
      state=random-csrf-token  ← TPP verifies matches original

Step 5: TPP exchanges code for tokens
  POST /token
    grant_type=authorization_code&
    code=AUTH-CODE-XYZ&
    code_verifier=original-verifier&
    client_assertion=eyJ...  ← signed with QSeal (proves TPP identity)

Step 6: Bank issues tokens
  {
    "access_token": "eyJ...",   ← JWT, 15-minute expiry
    "refresh_token": "opaque",  ← 90-day expiry, matches consent
    "token_type": "Bearer",
    "expires_in": 900,
    "scope": "accounts:read:balance accounts:read:transactions"
  }

Access token claims:
  {
    "sub": "C-UK-001",         ← customer
    "tpp_id": "tpp-fintech-123",
    "consent_id": "CONSENT-UUID-001",  ← KEY — links to consent
    "scope": "accounts:read:balance accounts:read:transactions",
    "exp": 1705310100,         ← 15 minutes
    "iss": "https://auth.bank.com",
    "aud": "open-banking-api"
  }
```

### Consent Storage & Enforcement

```
Consent stored in Cloud Spanner:
→ ACID — consent state always consistent
→ No eventual consistency risk — revocation immediate ✅
→ Indexed by: consentId, customerId, tppId

Consent enforcement at data layer:
→ Every API call → extract consent_id from JWT
→ Query Spanner: is this consent ACTIVE?
→ Does consent scope include requested data?
→ Is consent not expired?
→ Only return data within consented scope ✅

Data minimisation enforcement:
→ Consent: accounts:read:transactions:90days
→ Customer has 3 years of transactions
→ API returns: ONLY last 90 days ✅
→ Consent: accounts:read:balance
→ API returns: balance ONLY — not account number ✅
→ Field-level filtering based on consented scopes
```

---

## Component 3 — Consent Revocation in Seconds

### The Problem

```
Customer revokes consent at 2:00pm
TPP has valid JWT access token expiring at 2:15pm

JWT is self-contained — cannot be revoked
If TPP calls API at 2:14pm:
→ JWT signature valid ✅
→ JWT not expired ✅
→ Naive validation: allow access ❌ WRONG

Customer explicitly revoked — access must stop NOW
```

### Solution — Token Introspection + Consent Check

```
Two-layer solution:

Layer 1 — Consent status check on every request:
→ Every API call extracts consent_id from JWT
→ API checks consent status in Spanner:
  SELECT status FROM consents WHERE consent_id = ?
  → ACTIVE: proceed ✅
  → REVOKED: reject immediately → 403 Forbidden ✅
→ Revocation at 2:00pm → Spanner updated instantly
→2:14pm API call → Spanner check → REVOKED → blocked ✅
→ Latency: ~5ms Spanner read — acceptable

Layer 2 — Token introspection for high-value operations:
→ Payment initiation (>£30): ALWAYS token introspection
→ POST /introspect { token: "eyJ..." }
→ Auth Server checks:
  ✓ Token not revoked (revoked token list in Redis)
  ✓ Consent still active (Spanner check)
→ Returns: { active: true/false }
→ Additional network call: ~10ms — acceptable for payments ✅

Why not just short expiry alone:
→ 15-minute JWT: customer revokes at 2:00pm
→ TPP has 15 minutes of continued access ❌
→ For Open Banking: unacceptable — customer revoked explicitly
→ Consent check in Spanner: revocation effective in <1 second ✅
```

### Revocation Flow

```
Customer revokes consent in banking app:

Step 1: Customer hits "Revoke Fintech App Access"
Step 2: Bank updates Spanner:
  UPDATE consents SET status='REVOKED',
  revokedAt=NOW() WHERE consentId='CONSENT-UUID-001'
Step 3: Bank publishes ConsentRevoked event to Pub/Sub
Step 4: Token Service consumer:
  → Adds access token jti to Redis revocation list
  → TTL = remaining token lifetime (e.g. 14 minutes)
Step 5: Any in-flight TPP request:
  → Consent check: REVOKED → 403 ✅
  → Token introspection: jti in revocation list → inactive ✅

TPP refresh token:
→ Stored in Auth Server linked to consent_id
→ Consent REVOKED → refresh token immediately invalidated
→ TPP cannot get new access token ✅
```

---

## Component 4 — Runtime API Security

### Every Layer Has Security Checks

```
TPP request arrives:
GET /accounts/ACC-001/balance
Authorization: Bearer eyJ...
tpp-request-id: REQ-UUID-001

Layer 1 — Cloud Armor (before API Gateway):
✓ IP reputation check — known malicious IPs blocked
✓ Rate limit: 500 TPPs × max 1000 req/min each
✓ DDoS protection — volumetric attacks absorbed
✓ OWASP Top 10 — injection, XSS patterns blocked
✓ Geographic restriction — UK/EU TPPs only

Layer 2 — API Gateway:
✓ mTLS: verify TPP QWAC certificate
  → Certificate signed by trusted QTSP? ✓
  → Certificate not revoked (OCSP)? ✓
  → TPP authorisation number matches registered TPP? ✓
✓ JWT signature verification (RS256, local JWKS)
✓ JWT claims:
  → exp: not expired?
  → iss: correct issuer?
  → aud: this API?
  → tpp_id: matches mTLS certificate CN? ✓ (prevents token theft between TPPs)
✓ Rate limiting per TPP per customer
  → Max 4 requests/minute per customer per TPP (PSD2 limit)
✓ Request signing verification (QSeal):
  → Payment requests must include JWS signature
  → Verified against TPP's registered QSeal certificate ✓
✓ Publish audit event to Pub/Sub (async — non-blocking)

Layer 3 — Account Service (microservice):
✓ JWT re-validated (defence in depth)
✓ Consent check in Spanner:
  → consent_id from JWT → is consent ACTIVE?
  → Does scope include accounts:read:balance?
  → Is consent not expired?
✓ Authorisation: does customer C-UK-001 own ACC-001?
  (prevent BOLA — accessing another customer's account)
✓ Data minimisation:
  → Return ONLY balance field
  → Do NOT return account number, sort code, overdraft limit
  → Unless explicitly in consent scope

Layer 4 — Response:
✓ Response signing with bank's QSeal (payment responses)
✓ Sensitive fields masked in logs
✓ Correlation ID in response header for audit
✓ Audit event completed (success/failure + data returned)
```

---

## Component 5 — Immutable Audit Trail

### FCA Requirement

```
FCA requires: complete audit of every TPP API access
Must answer: "Show me every time TPP X accessed
             customer Y's data between Jan-Mar 2024"
Retention: 7 years minimum
Tamper-proof: immutable — cannot be modified or deleted
```

### Audit Architecture

```
Every API request generates audit event:
{
  auditId:      "AUDIT-UUID-001",
  timestamp:    "2024-01-15T14:00:00.123Z",
  tppId:        "tpp-fintech-123",
  tppName:      "Fintech App Ltd",
  customerId:   "C-UK-001",          ← tokenised
  accountId:    "TKN-8f3a",          ← tokenised
  consentId:    "CONSENT-UUID-001",
  endpoint:     "GET /accounts/{id}/balance",
  httpMethod:   "GET",
  responseCode: 200,
  dataReturned: ["balance"],          ← what was returned
  ipAddress:    "195.x.x.x",         ← TPP IP (hashed)
  requestId:    "REQ-UUID-001",
  correlationId:"CORR-UUID-001",
  outcome:      "SUCCESS",
  durationMs:   45
}
```

### Audit Pipeline

```
API Gateway/Microservice
    ↓ (async — non-blocking)
Pub/Sub topic: audit-events
    ↓
Two parallel consumers:

Consumer 1 — Operational Store (Bigtable):
→ Row key: tppId#reverseTimestamp
→ Fast range queries: "all access by TPP X last 30 days"
→ Row key: customerId#reverseTimestamp
→ Fast queries: "all TPP access to customer Y"
→ Retention: 2 years (hot operational queries)

Consumer 2 — Compliance Archive (GCS WORM):
→ Parquet format (columnar — efficient for FCA queries)
→ Path: gs://fca-audit/{year}/{month}/{day}/audit.parquet
→ Object Lock COMPLIANCE mode: 7-year retention
→ Cannot be deleted — even by admins ✅
→ Athena/BigQuery queryable:
  SELECT * FROM audit
  WHERE tpp_id = 'tpp-fintech-123'
  AND customer_id = 'C-UK-001'
  AND timestamp BETWEEN '2024-01-01' AND '2024-03-31'

Audit integrity:
→ Each audit record has hash of previous record (chain)
→ Any tampering breaks the chain → detectable ✅
→ Audit records signed with bank's QSeal ✅
→ Cloud Audit Logs: who accessed the audit system itself ✅
```

---

## 🗺️ Complete Security Architecture — Summary

```
TPP                     Bank Platform
───                     ─────────────
1. Presents QWAC        Cloud Armor (DDoS, WAF)
   certificate    →          ↓
                        API Gateway
                          ✓ mTLS QWAC verification
                          ✓ JWT validation (RS256)
                          ✓ Rate limiting
                          ✓ QSeal request verification
                          ✓ Audit event published (async)
                               ↓
                        Account Service
                          ✓ Consent status (Spanner)
                          ✓ Scope enforcement
                          ✓ BOLA prevention
                          ✓ Data minimisation
                               ↓
                        Response returned
                          ✓ Only consented fields
                          ✓ Audit completed

Customer revokes:
  Spanner: REVOKED instantly
  Redis: jti blacklisted
  Next TPP call: blocked in <1 second ✅
```

---

## 📊 Final Security Drill Scorecard

| Question | Topic | Score |
|---|---|---|
| Q23 — Security | Mobile Banking Auth | 5/10 |
| Q24 — Security | GKE Security Audit | 7/10 |
| Q25 — Security | Open Banking Platform | Study mode 📖 |

---

## 🔑 Key Concepts from This Question

| Concept | Key Point |
|---|---|
| **QWAC certificates** | eIDAS — TPP identity via mTLS. Verified against FCA register |
| **QSeal certificates** | eIDAS — TPP message signing. Proves payload not tampered |
| **Authorization Code + PKCE** | Customer-present OAuth2 flow for Open Banking consent |
| **Consent in Spanner** | ACID — revocation effective instantly, no eventual consistency |
| **Revocation in seconds** | Consent check in Spanner on every request — not just JWT expiry |
| **BOLA prevention** | Verify customer owns the account being accessed |
| **Data minimisation** | Return ONLY fields within consented scope |
| **Audit chain** | Hash of previous record — tampering detectable |
| **WORM audit archive** | GCS Object Lock COMPLIANCE — 7-year immutable FCA archive |

---
