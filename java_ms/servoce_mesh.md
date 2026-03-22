# 📡 Day 3 — Service Mesh + Security

*Why this matters at JPMC/GS: Security is non-negotiable in FS. Every EM interview will probe "how do your services authenticate to each other", "how do you manage secrets", and "what does zero-trust mean in practice in your system". These aren't academic questions — they expect production answers with regulatory context.*

---

# The Mental Model First

Day 3 has two halves that connect:

```
SERVICE MESH                          SECURITY
─────────────                         ────────
How traffic flows between services    How services prove who they are

Istio + Envoy sidecar          →      mTLS between every service
Traffic management             →      Zero-trust network model
Observability via mesh         →      Distributed tracing + audit
Canary deployments             →      Safe rollout of auth changes

The connection:
Service mesh IS the infrastructure that enforces security policy.
mTLS doesn't happen by magic — Istio's control plane
issues certificates, rotates them, and enforces policy.
You can't talk about mTLS in production without talking
about how the mesh manages it.
```

---

# TOPIC 1: Service Mesh

## Core Concept + Internals

> *"A service mesh is a dedicated infrastructure layer for handling service-to-service communication — traffic management, security, and observability — without changing application code."*

The key phrase: **without changing application code.** Your Java service doesn't know the mesh exists. It just makes HTTP calls. The mesh intercepts everything.

### The Sidecar Pattern — How It Works

```
WITHOUT service mesh:
┌─────────────────────┐         ┌─────────────────────┐
│   Payment Service   │ ──────► │   Fraud Service     │
│   (Java app)        │  HTTP   │   (Java app)        │
└─────────────────────┘         └─────────────────────┘
No auth, no encryption, no retry,
no circuit breaking, no tracing
Application code handles everything

WITH Istio service mesh:
┌──────────────────────────────────┐    ┌──────────────────────────────────┐
│  Payment Service Pod             │    │  Fraud Service Pod               │
│  ┌─────────────┐ ┌────────────┐  │    │  ┌────────────┐ ┌─────────────┐  │
│  │  Java App   │►│   Envoy    │  │    │  │   Envoy    │►│  Java App   │  │
│  │  (port 8080)│ │  Sidecar   │──┼────┼─►│  Sidecar   │ │  (port 8080)│  │
│  │             │ │  (port     │  │    │  │            │ │             │  │
│  │             │ │  15001)    │  │    │  │            │ │             │  │
│  └─────────────┘ └────────────┘  │    │  └────────────┘ └─────────────┘  │
└──────────────────────────────────┘    └──────────────────────────────────┘

What Envoy handles transparently:
✅ mTLS — encrypts + authenticates the connection
✅ Retry logic — 3 retries with exponential backoff
✅ Circuit breaking — opens if 50% errors in 10s
✅ Load balancing — round robin, least connections
✅ Distributed tracing — injects trace headers
✅ Metrics — request rate, latency, error rate
✅ Traffic splitting — 90% v1, 10% v2

Your Java code sees none of this.
It makes a plain HTTP call to fraud-service:8080.
Envoy intercepts at the network level (iptables rules).
```

---

### Istio Components — Control Plane vs Data Plane

```
DATA PLANE (what handles traffic — Envoy sidecars)
─────────────────────────────────────────────────
Every pod gets an Envoy sidecar injected automatically
(namespace label: istio-injection=enabled)

Envoy handles: actual packet routing, mTLS, retries,
               load balancing, metrics collection

CONTROL PLANE (what tells Envoy what to do — istiod)
────────────────────────────────────────────────────
istiod (unified control plane — replaced Pilot/Citadel/Galley in Istio 1.5+)

Three functions of istiod:
1. Pilot    → Service discovery + traffic management rules
              Pushes VirtualService/DestinationRule config to Envoy
2. Citadel  → Certificate authority
              Issues X.509 certs to every service (SPIFFE standard)
              Rotates certs automatically (default: 24h)
3. Galley   → Config validation
              Validates Istio config before applying

SPIFFE (Secure Production Identity Framework for Everyone):
Every service gets a cryptographic identity:
spiffe://cluster.local/ns/payments/sa/payment-service
This is the identity used in mTLS — not IP address, not hostname.
IP addresses change (pods restart) — SPIFFE identity is stable.
```

---

### Traffic Management — VirtualService + DestinationRule

```yaml
# VirtualService — how to route requests
# "When someone calls fraud-service, apply these rules"
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: fraud-service
spec:
  hosts:
  - fraud-service
  http:
  # Canary: route 10% of payments traffic to v2 fraud model
  - match:
    - headers:
        x-payment-type:
          exact: "HIGH_VALUE"    # Route high-value payments to v2
    route:
    - destination:
        host: fraud-service
        subset: v2
      weight: 100
  # Default: 90% v1, 10% v2 for everything else
  - route:
    - destination:
        host: fraud-service
        subset: v1
      weight: 90
    - destination:
        host: fraud-service
        subset: v2
      weight: 10
  # Retry policy — 3 retries, 25ms timeout per attempt
  retries:
    attempts: 3
    perTryTimeout: 25ms
    retryOn: gateway-error,connect-failure,retriable-4xx

---
# DestinationRule — what the subsets are + load balancing policy
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: fraud-service
spec:
  host: fraud-service
  trafficPolicy:
    loadBalancer:
      simple: LEAST_CONN          # Route to least busy pod
    connectionPool:
      http:
        http2MaxRequests: 1000    # Max concurrent HTTP/2 requests
        http1MaxPendingRequests: 100
    # Circuit breaker — outlier detection
    outlierDetection:
      consecutiveErrors: 5        # 5 errors → eject pod
      interval: 30s               # Check every 30s
      baseEjectionTime: 30s       # Eject for 30s minimum
      maxEjectionPercent: 50      # Never eject more than 50% of pods
  subsets:
  - name: v1
    labels:
      version: v1
  - name: v2
    labels:
      version: v2
```

**EM framing on circuit breaking in Istio:**

> *"We configure outlier detection in Istio rather than Resilience4j in the application for one reason — consistency. With Resilience4j, every service team configures their own circuit breaker differently — different thresholds, different timeouts. With Istio outlier detection, the platform team sets policy centrally. No service team can forget to add a circuit breaker. When TM Vault was having latency issues during a release, Istio automatically ejected the slow pods and the Account Lifecycle service continued serving from the healthy pods — zero application code change needed."*

---

# TOPIC 2: mTLS + Zero-Trust

## Core Concept

> *"mTLS (mutual TLS) means BOTH sides of a connection present a certificate and verify each other. Not just the server proving it's who it says it is — the client also proves its identity."*

### Regular TLS vs mTLS

```
Regular TLS (HTTPS):
Client → "Give me your certificate"
Server → presents certificate
Client → verifies server identity
Client → sends data encrypted
Server → never verified who the client is

Anyone who can reach the server can call it.
In a bank: anyone inside the network can call your payment service.
If an attacker gets inside the network (compromised pod) →
they can call any internal service freely.

mTLS:
Client → presents its certificate
Server → verifies client identity (is this really payment-service?)
Server → presents its certificate
Client → verifies server identity (is this really fraud-service?)
Both sides verified → encrypted channel established

With Istio: certificates issued by istiod (SPIFFE identity)
payment-service cert: spiffe://cluster.local/ns/payments/sa/payment-svc
fraud-service cert:   spiffe://cluster.local/ns/fraud/sa/fraud-svc

AuthorizationPolicy says:
"fraud-service ONLY accepts connections from payment-service"
Even if attacker compromises a pod in the cluster,
they can't call fraud-service without payment-service's certificate.
```

### Zero-Trust Network Model

```
OLD MODEL (perimeter security):
"Trust everything inside the network, block everything outside"
Firewall at edge → anything that gets past it is trusted
Problem: once attacker is inside → lateral movement is trivial

ZERO-TRUST MODEL:
"Never trust, always verify — regardless of network location"
Every service call authenticated + authorised
Even service-to-service calls within the cluster

FOUR PILLARS in practice:

1. Identity (not IP)
   Services identified by SPIFFE certificate — not IP address
   IP changes when pod restarts — identity is stable

2. Least privilege
   AuthorizationPolicy: payment-service can ONLY call fraud-service
   fraud-service CANNOT call payment-service (wrong direction)
   Reporting-service can ONLY read — never write

3. Encrypt everything
   mTLS on every connection — even internal cluster traffic
   PCI-DSS requirement: cardholder data must be encrypted in transit
   Even on internal networks

4. Continuous verification
   Certificate rotation every 24 hours (Istio default)
   Compromised certificate is valid for max 24 hours
   Can reduce to 1 hour for high-security namespaces

PCI-DSS connection:
PCI-DSS Requirement 4: Protect cardholder data in transit
PCI-DSS Requirement 7: Restrict access to need-to-know
Zero-trust architecture satisfies both requirements by design.
```

### Certificate Rotation — Zero Downtime

```
THE PROBLEM:
Rotating certificates naively:
Step 1: Issue new cert to fraud-service
Step 2: payment-service still has old trust bundle
Step 3: payment-service rejects fraud-service's new cert
Step 4: All calls to fraud-service fail → production incident

THE ISTIO SOLUTION — overlapping validity:
Istiod issues new cert BEFORE old one expires
Old cert: valid 09:00 → 09:00 tomorrow (24h)
New cert issued at: 08:45 (15 min before expiry)
Overlap window: 15 minutes where BOTH certs are valid
During overlap: payment-service accepts EITHER cert
After overlap: old cert expired, only new cert valid
Zero downtime — seamless rotation

In practice:
Istio rotates certs automatically every 24h
You never touch this as an operator
Audit log shows every rotation — FCA operational resilience
evidence that your security controls are continuously maintained
```

---

# TOPIC 3: OAuth2 + JWT End-to-End

## The Full Flow — Two Scenarios

### Scenario 1: Customer (External) → Channel API

```
Mobile App → Internet Banking API → Account Lifecycle BC

Step 1: Customer logs in
Mobile App redirects to: Identity Provider (Keycloak / Azure AD B2C)
Customer authenticates (username + password + MFA)
IDP issues: Authorization Code (short-lived, one-time use)

Step 2: Token exchange
Mobile App sends Authorization Code to IDP token endpoint
IDP returns:
  - Access Token (JWT, short-lived: 15 minutes)
  - Refresh Token (opaque, long-lived: 8 hours)
  - ID Token (OIDC — who the user is)

Step 3: API call with Access Token
Mobile App → POST /v1/savings/accounts/lsa/LSA-789/deposits
Header: Authorization: Bearer eyJhbGciOiJSUzI1NiJ9...

Step 4: API Gateway validates JWT
  - Verify signature (RS256 — public key from IDP JWKS endpoint)
  - Check exp claim (not expired)
  - Check iss claim (correct issuer)
  - Check aud claim (correct audience — this API)
  - Check scope claim (savings:write required for deposits)
  - Check sub claim (customer identity)

Step 5: Downstream service receives enriched request
API Gateway strips raw JWT, adds:
  X-Customer-Id: CUST-123
  X-Customer-Segment: COMMERCIAL
  X-Scopes: savings:read savings:write
Account Lifecycle BC trusts these headers (from gateway only)
Does NOT re-validate JWT — gateway already did
```

### Scenario 2: Service-to-Service (Internal)

```
Payment Service → Fraud Service (internal, no customer involved)

OAuth2 Client Credentials Flow:
No user involved — machine-to-machine auth

Step 1: Payment Service requests token
POST /token
grant_type=client_credentials
client_id=payment-service
client_secret=<from Vault>    ← NEVER hardcoded
scope=fraud:assess

Step 2: IDP returns Access Token
{
  "access_token": "eyJ...",
  "token_type": "Bearer",
  "expires_in": 300,          // 5 minutes — short for service tokens
  "scope": "fraud:assess"
}

Step 3: Payment Service calls Fraud Service
Authorization: Bearer eyJ...

Step 4: Fraud Service validates
Options:
  A) Local validation — verify JWT signature with IDP public key
     Fast (~1ms), no network call
     Risk: revocation not immediate (token valid until exp)

  B) Token introspection — call IDP /introspect endpoint
     Always current — revoked token detected immediately
     Cost: ~5ms network call per request
     At 50k TPS: 50k introspection calls/sec → IDP bottleneck

DECISION for FS:
Local validation for standard service calls (fast, scalable)
Token introspection for high-risk operations:
  - Account closure
  - Large withdrawals (>£10,000)
  - Maturity instruction changes
  - Any irreversible operation

In practice with Istio:
mTLS handles service identity authentication
JWT/OAuth2 handles authorisation (what this service is allowed to do)
Both run in parallel — defence in depth
```

---

### JWT Structure — EM Must Know This

```
JWT = three base64url-encoded parts separated by dots
eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9    ← Header
.eyJzdWIiOiJDVVNULTEyMyIsImlzcyI6...    ← Payload (claims)
.SflKxwRJSMeKKF2QT4fwpMeJf36POk6y...    ← Signature

HEADER:
{
  "alg": "RS256",    // RSA with SHA-256 — asymmetric
  "typ": "JWT",
  "kid": "key-2024-11"  // Key ID — which public key to verify with
}

PAYLOAD (claims):
{
  "sub": "CUST-123",              // Subject — who this token is for
  "iss": "https://auth.bank.com", // Issuer — who issued it
  "aud": "savings-api",           // Audience — who should accept it
  "exp": 1731234567,              // Expiry — Unix timestamp
  "iat": 1731233667,              // Issued at
  "scope": "savings:read savings:write",
  "customer_segment": "COMMERCIAL",
  "account_ids": ["LSA-789"],     // Restrict to specific accounts
  "jti": "uuid-unique-token-id"   // JWT ID — for revocation tracking
}

RS256 vs HS256 — THE CRITICAL DISTINCTION:

HS256 (symmetric):
One secret key — used to BOTH sign and verify
Anyone with the key can sign ANY token
If Fraud Service has the key, it can forge a token saying
"I am customer CUST-123 with admin scope"
NEVER use HS256 for external tokens in FS

RS256 (asymmetric):
Private key: IDP signs the token — only IDP has this
Public key: any service verifies the token
Even if Fraud Service is compromised, attacker can only
VERIFY tokens — cannot create new ones
Correct choice for FS: RS256 always

KID (Key ID) rotation:
IDP rotates signing keys periodically
KID field tells verifier which public key to use
Verifier fetches public keys from IDP JWKS endpoint:
GET https://auth.bank.com/.well-known/jwks.json
Cache these keys (TTL: 1 hour) — don't fetch per request
```

---

# TOPIC 4: Secrets Management

## The Problem

```
// ❌ HOW TEAMS GET CAUGHT — and it's a PCI-DSS violation

# application.yml in git repository
spring:
  datasource:
    url: jdbc:postgresql://savings-db:5432/savings
    username: savings_app
    password: Lloyds@Savings#2024!    ← HARDCODED SECRET IN GIT

# Environment variable in Kubernetes manifest (also wrong)
env:
  - name: DB_PASSWORD
    value: "Lloyds@Savings#2024!"     ← SECRET IN K8S MANIFEST IN GIT

PCI-DSS Requirement 8.2.1:
All credentials must be managed as secrets
No credentials in code or configuration files
Violation: reportable to QSA, potential fine

Real incident: Developer commits db password to git
Attacker finds it via git history (even after deletion)
Gets access to savings database
GDPR Article 33: 72-hour breach notification to ICO
FCA notification: material operational incident
```

## HashiCorp Vault — The Solution

```
VAULT ARCHITECTURE:

                    ┌─────────────────────┐
                    │   HashiCorp Vault    │
                    │                     │
                    │  ┌───────────────┐  │
                    │  │ Secret Engine │  │
                    │  │ KV v2         │  │  ← Static secrets
                    │  │ Database      │  │  ← Dynamic secrets
                    │  │ PKI           │  │  ← Certificate mgmt
                    │  └───────────────┘  │
                    │                     │
                    │  ┌───────────────┐  │
                    │  │ Auth Methods  │  │
                    │  │ Kubernetes    │  │  ← Pod identity
                    │  │ AppRole       │  │  ← Service identity
                    │  └───────────────┘  │
                    └──────────┬──────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
    ┌─────────▼──────┐ ┌───────▼──────┐ ┌──────▼───────┐
    │ Payment Service│ │Fraud Service │ │Account Svc   │
    │ Pod            │ │ Pod          │ │ Pod          │
    └────────────────┘ └──────────────┘ └──────────────┘
```

### Dynamic Secrets — The Key Capability

```java
// STATIC secrets (basic Vault usage):
// Store password in Vault, fetch at startup
// Better than git — but password is long-lived

// DYNAMIC secrets (advanced — use this in FS):
// Vault generates a UNIQUE database credential on demand
// Each pod gets its own credential
// Credential expires when pod dies

// How it works:
// 1. Payment Service pod starts
// 2. Pod authenticates to Vault using Kubernetes service account
//    (Vault verifies pod identity via K8s API)
// 3. Payment Service requests: "I need DB credentials"
// 4. Vault calls PostgreSQL:
//    CREATE USER vault_payment_20241115_abc123
//    WITH PASSWORD 'random-32-char-string'
//    VALID UNTIL '2024-11-15 10:30:00';  // TTL: 1 hour
// 5. Vault returns credentials to pod
// 6. Pod uses these credentials for DB access
// 7. Pod dies → credentials expire → credential revoked

// Result:
// - No shared password between services
// - Breach of one pod = one set of credentials exposed
// - Credentials auto-expire — attacker has 1 hour window max
// - Full audit trail: who requested what credential when
// - PCI-DSS Requirement 8: unique credentials per service ✅

@Configuration
public class VaultConfig {

    // Spring Cloud Vault — auto-fetches and rotates secrets
    // No code changes needed — just config
}

# bootstrap.yml
spring:
  cloud:
    vault:
      host: vault.bank.internal
      port: 8200
      scheme: https
      authentication: KUBERNETES
      kubernetes:
        role: payment-service
        service-account-token-file:
          /var/run/secrets/kubernetes.io/serviceaccount/token
      database:
        enabled: true
        role: payment-service-db-role
        backend: database
        # Vault auto-rotates these credentials
        # Spring refreshes them before expiry
```

### GCP Secret Manager — Your Context

```
Since your primary cloud is GCP:

GCP Secret Manager is the managed alternative to Vault
No infrastructure to manage — fully serverless

Use GCP Secret Manager for:
  Static secrets (API keys, third-party credentials)
  Certificates and signing keys
  Configuration values that change rarely

Use HashiCorp Vault for:
  Dynamic database credentials
  PKI / certificate management
  Multi-cloud or hybrid (Vault works everywhere)
  Complex access policies across many services

In your Lloyds/GCP context:
GCP Secret Manager for: TM Vault API keys, KYC provider keys,
                         Kafka credentials, OAuth2 client secrets
Spring integration:
  spring-cloud-gcp-starter-secretmanager
  @Value("${sm://projects/PROJECT/secrets/vault-api-key}")

Audit trail:
Every secret access logged in GCP Cloud Audit Logs
FCA operational resilience — evidence of who accessed what secret when
```

---

# Full Day 3 Mental Map

```
SERVICE MESH (Istio)
│
├── Sidecar pattern (Envoy) — intercepts all traffic transparently
├── Control plane (istiod) — issues certs, pushes config
├── VirtualService — traffic routing, canary, retry
├── DestinationRule — load balancing, circuit breaking, subsets
│
└── Enables → mTLS automatically between all services

mTLS + ZERO-TRUST
│
├── Both sides present certificates (SPIFFE identity)
├── AuthorizationPolicy — who can call whom
├── Certificate rotation — 24h default, zero downtime
│
└── Satisfies PCI-DSS Req 4 (encrypt in transit) + Req 7 (least privilege)

OAUTH2 + JWT
│
├── External: Authorization Code + PKCE (mobile) → API Gateway validates
├── Internal: Client Credentials → local JWT validation
├── RS256 not HS256 — asymmetric, cannot forge tokens
├── Local validation (fast) vs introspection (always current)
│
└── High-risk ops: ALWAYS introspect (closure, large withdrawal)

SECRETS MANAGEMENT
│
├── Never in code or git (PCI-DSS Req 8)
├── HashiCorp Vault: dynamic credentials, PKI, multi-cloud
├── GCP Secret Manager: managed, GCP-native, audit trail
│
└── Dynamic DB credentials: unique per pod, TTL 1h, auto-expire
```

---

# 🎯 Drill Time

Pick one:

**Drill A — Service Mesh:**
> *"Your Payments service and Fraud service currently communicate over plain HTTP inside the cluster. A PCI-DSS audit flags this as a violation of Requirement 4. Walk me through implementing mTLS between them using Istio. What changes in your Kubernetes manifests, how does Istio issue and rotate certificates, and how do you verify mTLS is actually enforced and not just enabled?"*

**Drill B — OAuth2/JWT:**
> *"A commercial customer's mobile app calls your Savings API to initiate a withdrawal. Walk me through the complete security flow from the customer tapping 'Withdraw' to your Account Lifecycle BC executing the withdrawal. Name every token, every validation step, and explain why you chose RS256 over HS256."*

**Drill C — Zero-Trust + Secrets:**
> *"A developer on your team has committed a database password to git. Walk me through: the immediate incident response, how you prevent this happening again using Vault dynamic secrets, and what your regulatory notification obligations are under GDPR and PCI-DSS."*

**Drill D — Architecture:**
> *"Your CTO asks: do we need a service mesh? We only have 8 microservices right now and the team finds Istio complex. Make the case for or against Istio adoption, with specific reference to your FS regulatory requirements."*

Or say **move on** → Day 4: Data management, API composition, distributed tracing, SLOs.
