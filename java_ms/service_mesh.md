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


# 🔭 Observability via Istio — Deep Dive

*This is a high-signal topic at FS GCC interviews. The question is never "what is observability" — it's "a payment shows as pending in the UI but settled in CBS, you have 6 services in the chain, how do you find the problem in under 5 minutes?" That's the practical test.*

---

# The Three Pillars — How Istio Covers All Three

```
OBSERVABILITY = Metrics + Traces + Logs
                    │           │        │
                    ▼           ▼        ▼
Istio provides:  Prometheus  Jaeger   Access logs
                 + Grafana   + Zipkin  per sidecar

The key insight:
Without a service mesh, each team instruments their own service.
Different libraries, different formats, different coverage.
One team uses Micrometer, another uses Dropwizard,
a third forgot to add metrics entirely.

With Istio: ALL services get the same metrics, traces, and
access logs automatically — zero application code change.
The Envoy sidecar instruments every request.
```

---

# PILLAR 1: Metrics — What Istio Collects Automatically

Istio's Envoy sidecar collects the **RED metrics** for every service automatically:

```
RED = Rate + Errors + Duration

For EVERY service-to-service call, Istio captures:

RATE (requests per second):
istio_requests_total{
  source_workload="payment-service",
  destination_workload="fraud-service",
  response_code="200"
}

ERROR RATE:
istio_requests_total{
  source_workload="payment-service",
  destination_workload="fraud-service",
  response_code=~"5.."    ← regex: any 5xx
}

DURATION (latency):
istio_request_duration_milliseconds_bucket{
  source_workload="payment-service",
  destination_workload="fraud-service",
  le="50"    ← requests completed in under 50ms
}

TCP metrics (for databases, Kafka):
istio_tcp_sent_bytes_total
istio_tcp_received_bytes_total
istio_tcp_connections_opened_total
```

### The Four Golden Signals in Grafana

Istio ships pre-built Grafana dashboards. The four panels you care about in FS:

```
Dashboard 1: Service Mesh Overview
┌─────────────────────┬─────────────────────┐
│ Global Request Rate │ Global Error Rate   │
│ 47,832 req/s        │ 0.02%               │
│ ▲ +12% vs yesterday │ ✅ within SLO       │
├─────────────────────┼─────────────────────┤
│ P99 Latency         │ Active Services     │
│ 87ms                │ 23 services         │
│ ⚠ SLO = 100ms       │ 0 degraded          │
└─────────────────────┴─────────────────────┘

Dashboard 2: Per-Service Breakdown
Payment Service → Fraud Service
  Request rate:  12,450 req/s
  Error rate:    0.01%
  P50 latency:   8ms
  P95 latency:   22ms
  P99 latency:   47ms     ← this is what matters for SLA
  P99.9 latency: 180ms    ← tail latency — catches outliers

Fraud Service → ML Model Service
  Request rate:  12,450 req/s
  Error rate:    0.03%
  P99 latency:   38ms     ← ML inference dominates
```

### EM-Level Framing on Metrics

> *"Before Istio, our Grafana dashboards were inconsistent across services — Payment Service used Micrometer with one set of labels, Fraud Service used a custom metrics library, Account Service had almost no metrics. When we had a latency spike, we couldn't tell which service in the chain was slow. After Istio, every service has identical RED metrics with identical labels. I can open one dashboard, select 'payment-service → fraud-service', and see exactly: is the problem in the network, in fraud service processing, or in fraud service → CBS? The mean time to diagnose dropped from 45 minutes to under 5 minutes."*

---

# PILLAR 2: Distributed Tracing — The Most Important for FS

This is where Istio's observability becomes genuinely powerful for debugging payment flows.

## How Distributed Tracing Works

```
A payment initiation touches 6 services:
Channel API → Payment Service → Fraud Service →
Account Service → TM Vault Adapter → Notification Service

Without tracing:
Each service logs independently.
When payment PMT-001 fails, you search logs in each service.
"grep PMT-001 payment-service.log" — find the log
"grep PMT-001 fraud-service.log" — find the log
6 services × grep × different timestamps = 45 minutes

With distributed tracing:
One trace ID follows the request through ALL services.
One query: "show me trace abc-123-def" → entire journey
```

## How Istio Injects Trace Context

```
Step 1: Envoy at Channel API ingress generates trace context
x-request-id: abc-123-def-456          ← Istio correlation ID
x-b3-traceid: 80f198ee56343ba864fe8b2a9d49a  ← Zipkin/B3 trace ID
x-b3-spanid: 05e3ac9a4f6e3b90
x-b3-parentspanid: e457b5a2e4d86294
x-b3-sampled: 1                         ← Sample this trace

Step 2: Envoy propagates these headers on every outbound call
Payment Service receives them → Fraud Service call includes them
Fraud Service receives them → Account Service call includes them
Chain preserved automatically — ZERO code change for propagation

Step 3: ONE important thing application code must do:
Propagate trace headers from inbound request to outbound calls

// ✅ Your Spring service must forward these headers
// Istio handles injection/collection but NOT propagation
// through your application code

@RestController
public class PaymentController {

    private final FraudServiceClient fraudClient;
    private final WebClient webClient;

    @PostMapping("/payments")
    public PaymentResult initiatePayment(
            @RequestBody PaymentRequest request,
            // Extract trace headers from incoming request
            @RequestHeader HttpHeaders incomingHeaders) {

        // Forward trace headers to outbound calls
        // This is the ONE thing your code must do
        return webClient.post()
            .uri("http://fraud-service/assess")
            .headers(h -> {
                // Copy B3 trace headers
                copyTraceHeader(incomingHeaders, h,
                    "x-request-id",
                    "x-b3-traceid",
                    "x-b3-spanid",
                    "x-b3-parentspanid",
                    "x-b3-sampled",
                    "x-b3-flags");
            })
            .bodyValue(FraudRequest.from(request))
            .retrieve()
            .bodyToMono(FraudAssessment.class)
            .block();
    }
}

// In Spring Boot with Micrometer Tracing (recommended):
// Add dependency: micrometer-tracing-bridge-otel
// Configure: management.tracing.sampling.probability=1.0
// Propagation happens automatically via WebClient/RestTemplate
// instrumentation — no manual header copying needed
```

## Reading a Trace in Jaeger — The Production Debugging Scenario

```
Scenario: Payment PMT-20241115-001 shows PENDING in UI
          but Account Service shows SETTLED in its database.
          Customer calling support. Need to find root cause.

Jaeger query:
Service: payment-service
Tags: payment.id=PMT-20241115-001
Time range: last 1 hour

Result — one trace, expanded:

Trace ID: abc-123-def-456  Total: 287ms
│
├── [0ms]    Channel API → Payment Service          12ms
│   └── POST /v1/payments
│       Status: 202 Accepted ✅
│
├── [12ms]   Payment Service → Fraud Service        48ms
│   └── gRPC FraudScreeningService/AssessPayment
│       Status: OK ✅
│       fraud.decision: ALLOW
│       fraud.confidence: 0.12
│
├── [60ms]   Payment Service → Account Service      18ms
│   └── gRPC AccountService/DebitAccount
│       Status: OK ✅
│       balance.after: GBP 47823.00
│
├── [78ms]   Payment Service → TM Vault Adapter     184ms ⚠️
│   └── POST /posting-orders
│       Status: OK ✅ (Vault accepted)
│       vault.posting_id: VP-20241115-789
│
├── [262ms]  Payment Service → Notification Svc     8ms
│   └── async publish PaymentSubmittedEvent
│       Status: OK ✅
│
└── [270ms]  Payment Service response to Channel    17ms
    └── 202 Accepted — status: PROCESSING

ROOT CAUSE VISIBLE IN THE TRACE:
TM Vault Adapter took 184ms — 64% of total time
Vault accepted the posting order but returned PROCESSING status
Payment Service updated its own status to PROCESSING correctly
Account Service had already debited → SETTLED in their DB
Mismatch: Account BC settled, Payment BC still PROCESSING

Fix: Payment Service needs to subscribe to
     VaultPostingCompletedEvent → update to SETTLED
     The event was not being consumed — Kafka consumer
     group offset was behind by 2 hours
```

## Trace Sampling — The EM Decision

```
The problem with sampling:
Tracing EVERY request at 50,000 TPS = 50,000 traces/sec
Each trace = ~50KB of data
50,000 × 50KB = 2.5GB/sec of trace data
Storage cost: ~$8,000/day on GCP Cloud Trace
Clearly unsustainable

Sampling strategies:

1. Head-based sampling (decision at trace start):
   Sample 1% of all traces by default
   Risk: you might miss the 1 problematic trace in 10,000
   Use for: normal operations, baseline performance data

2. Tail-based sampling (decision after trace completes):
   OpenTelemetry Collector evaluates completed traces
   Sample 100% of:
     - Traces with errors (any 5xx)
     - Traces with latency > P99 threshold (>100ms)
     - Traces matching specific payment IDs (on-demand)
   Sample 1% of healthy fast traces
   Best of both worlds — never miss errors
   Use for: production FS systems

3. Force-sample specific payments:
   High-value payment (>£100,000): always trace
   PCI-DSS audit investigation: force-sample by payment ID
   Add header: x-b3-sampled: 1

// Spring Boot Actuator config:
management:
  tracing:
    sampling:
      probability: 0.01    # 1% baseline
  # Tail-based: configure in OTel Collector, not app

// Istio mesh config for 1% default:
apiVersion: telemetry.istio.io/v1alpha1
kind: Telemetry
metadata:
  name: mesh-default
  namespace: istio-system
spec:
  tracing:
  - randomSamplingPercentage: 1.0    # 1% default
```

---

# PILLAR 3: Access Logs — Compliance + Security

```
Every Envoy sidecar logs every request/response.
This is different from your application logs.
Envoy logs: network-level data
App logs: business-level data
Both needed in FS.

Istio access log format (JSON — searchable):
{
  "timestamp": "2024-11-15T09:23:45.123Z",
  "source_workload": "payment-service",
  "destination_workload": "fraud-service",
  "request_id": "abc-123-def-456",
  "method": "POST",
  "path": "/fraud.v1.FraudScreeningService/AssessPayment",
  "response_code": 200,
  "duration_ms": 47,
  "request_bytes": 284,
  "response_bytes": 156,
  "source_principal": "spiffe://cluster.local/ns/payments/sa/payment-svc",
  "destination_principal": "spiffe://cluster.local/ns/fraud/sa/fraud-svc",
  "tls_version": "TLSv1.3",
  "tls_cipher_suite": "TLS_AES_256_GCM_SHA384"
}

WHY THIS MATTERS FOR FCA:
FCA SYSC 6 — firms must keep adequate records of all transactions
Istio access logs provide:
  ✅ Complete audit trail of every service-to-service call
  ✅ Cryptographic identity of caller (SPIFFE principal)
  ✅ TLS version and cipher — evidence of encryption in transit
  ✅ Timestamps with millisecond precision
  ✅ Request/response sizes — detect anomalous data volumes

These logs shipped to: GCP Cloud Logging → BigQuery
Retention: 7 years (FCA requirement)
Queryable: "show me all calls from payment-service to
            fraud-service that took >100ms in November 2024"
```

---

# Kiali — The Service Mesh Topology Visualiser

```
Kiali = purpose-built UI for Istio mesh visualisation
Ships with Istio — free

What it shows:
- Real-time service dependency graph
- Traffic flow between services with volume + error rate
- Health status per service
- mTLS status — green lock = mTLS enforced, red = plaintext

FS use case — PCI-DSS audit:
Auditor asks: "Show me that all communication involving
               cardholder data is encrypted"
You open Kiali → filter to payment namespace
Every edge shows: 🔒 mTLS enforced
Export as PDF → audit evidence

FS use case — incident investigation:
Production alert: "fraud-service error rate spiked to 5%"
Open Kiali → fraud-service is highlighted red
Click on it: "receiving 12,450 req/s, returning 5.1% 503s"
Trace upstream: payment-service is flooding it
Check DestinationRule: circuit breaker not configured
Root cause: traffic spike + no circuit breaker = cascade failure
```

---

# Putting It All Together — The Production Debugging Workflow

This is the answer to the interview question: *"Payment shows pending in UI but settled in CBS — how do you debug?"*

```
STEP 1: Grafana — 2 minutes
Open Service Mesh dashboard
Filter: last 30 minutes
Look for: error rate spike or latency anomaly
Identify: which service-to-service edge is red/yellow?

STEP 2: Jaeger — 2 minutes
Search by: service=payment-service + tag payment.id=PMT-001
Find the trace
Expand the span tree
Look for: longest span, error status, warning annotations
Identify: TM Vault Adapter took 184ms → root cause

STEP 3: Application logs — 1 minute
Take the trace ID from Jaeger: abc-123-def-456
Query GCP Cloud Logging:
  labels.trace_id="abc-123-def-456"
See application-level log: "Vault returned PROCESSING status,
Kafka consumer lagging by 127,000 messages"

STEP 4: Fix + verify — with tracing
Deploy fix
Open Jaeger → same payment type
Confirm: TM Vault span now 8ms
Confirm: Payment status correctly transitions to SETTLED

Total diagnosis time: ~5 minutes vs 45 minutes without mesh
```

---

## EM-Level Framing — The Full Answer

> *"Istio gives us observability as a platform capability, not a per-team responsibility. Every service gets RED metrics in Prometheus, full distributed traces in Jaeger, and access logs with SPIFFE identity automatically. In our Lloyds engagement, we had a payment reconciliation issue where 0.3% of payments were stuck in PROCESSING despite CBS confirming settlement. Without distributed tracing, that would have been a multi-hour investigation across 6 teams. With Jaeger, I could search by payment ID, see the full trace, and identify that the TM Vault Adapter's PROCESSING response was not triggering the expected Kafka event — a Kafka consumer group had fallen 127,000 messages behind. Fixed and verified in 40 minutes including the code change."*

> *"The FCA operational resilience piece is equally important. Istio access logs give us a tamper-evident audit trail of every service-to-service call — who called what, when, with what identity, over what TLS version. That's direct evidence for PCI-DSS audits and FCA SYSC 6 record-keeping requirements. We retain these logs in BigQuery for 7 years. During a PCI-DSS assessment last year, the QSA asked for evidence that all internal communication involving cardholder data was encrypted. I opened Kiali, filtered to the payments namespace, and showed every edge was mTLS enforced — green locks across the board. The auditor accepted it on the spot."*

---MD file ready. The three things to lock in from this topic:

The **one thing your code must do** — propagate B3 trace headers from inbound to outbound calls. Istio handles everything else. Micrometer Tracing with OTel bridge does this automatically if you're on Spring Boot 3+. Without this, traces break at your service boundary — you get two disconnected traces instead of one end-to-end trace.

The **tail-based sampling decision** — always sample errors and anything above P99 latency. Head-based 1% means you might miss the exact problematic trace. In a FS production system you cannot afford to miss error traces.

The **Kiali + PCI-DSS story** — being able to show a QSA a visual, PDF-exportable proof that every edge in your payments namespace has mTLS enforced is genuinely how audits work. That story lands every time.

Ready to continue with the **Day 3 drills**, or move on to **Day 4: Data management, distributed tracing SLOs, and API composition**?
