# 🔐 Microservices Security Checklist
---

## 1️⃣ Identity, Authentication & Authorization (CORE)
**Layer:** Application

- ✔ OAuth 2.0 (why, flows)
- ✔ OpenID Connect (OIDC) vs OAuth
- ✔ JWT vs opaque tokens
- ✔ Access token vs refresh token
- ✔ Scopes vs roles vs claims
- ✔ Token expiry & rotation
- ✔ Authorization models (RBAC vs ABAC)
- ✔ Fine-grained authorization (service & data level)

---

## 2️⃣ Service-to-Service Security (CORE)
**Layer:** Platform

- ✔ mTLS (what it gives, what it doesn't)
- ✔ Service identity (SPIFFE / cert-based)
- ✔ Token propagation vs token exchange
- ✔ Zero-trust principles
- ✔ East-west traffic protection
- ✔ Why mTLS ≠ authorization

---

## 3️⃣ API Security (CORE)
**Layer:** Edge Security

- ✔ API Gateway security responsibilities
- ✔ Authentication at edge
- ✔ Authorization at edge vs service
- ✔ Rate limiting & throttling
- ✔ Schema validation
- ✔ API versioning & deprecation security
- ✔ Protection against API abuse

---

## 4️⃣ Data Security (CORE)
**Layer:** Application + Platform

- ✔ Encryption at rest
- ✔ Encryption in transit
- ✔ Key management (KMS, HSM)
- ✔ Secrets management
- ✔ Data masking / tokenization
- ✔ PII handling & GDPR principles
- ✔ Field-level encryption (when & why)

---

## 5️⃣ Event & Messaging Security (CORE)

- ✔ Event authentication & authorization
- ✔ Topic-level ACLs
- ✔ Producer vs consumer permissions
- ✔ Sensitive data in events
- ✔ Event encryption (when needed)
- ✔ Replay security considerations

---

## 6️⃣ Authorization INSIDE the Domain (IMPORTANT)

- ✔ Policy enforcement points
- ✔ Where authorization lives (edge vs domain)
- ✔ Domain-driven authorization
- ✔ Preventing "authorization leakage"
- ✔ Multi-tenant isolation

---

## 7️⃣ Secrets, Keys & Certificates (CORE)
**Cross Cutting Concern**

- ✔ Secrets lifecycle
- ✔ Rotation strategy
- ✔ Avoiding secrets in code/config
- ✔ Cert rotation (mTLS)
- ✔ Vault / KMS patterns

---

## 8️⃣ Auditing, Compliance & Non-Repudiation (FS CRITICAL)

- ✔ Audit logs vs application logs
- ✔ What must be audited (who, what, when)
- ✔ Tamper-proof logs
- ✔ Regulatory traceability
- ✔ Non-repudiation principles

---

## 9️⃣ Threat Modeling & Security Posture (ADVANCED)

- ✔ Threat modeling (STRIDE-like thinking)
- ✔ Attack surfaces in microservices
- ✔ Trust boundaries
- ✔ Defense in depth
- ✔ Secure defaults

---

## 🔟 Operational Security (ADVANCED)

- ✔ Security monitoring & alerts
- ✔ Detecting credential misuse
- ✔ Incident response
- ✔ Security in CI/CD
- ✔ Dependency & supply-chain risks

---

## 1️⃣1️⃣ Common Security Anti-Patterns (MUST KNOW)

- ❌ Trusting internal traffic
- ❌ mTLS used as authorization
- ❌ Token leakage via logs
- ❌ Over-privileged service accounts
- ❌ Business authorization only at gateway
- ❌ Sensitive data in events

---

## 1️⃣2️⃣ Infrastructure Security

### 6.1 Network Segmentation & Isolation

- ✔ VPC / VNet isolation
- ✔ Subnet separation (public vs private)
- ✔ Micro-segmentation (namespace / pod-level)
- ✔ Environment isolation (dev / test / prod)

**🔑 Why:** Limits blast radius if a service is compromised

---

### 6.2 Ingress / Egress Control

- ✔ Ingress controllers
- ✔ Firewall rules
- ✔ Egress restrictions (very important in banks)
- ✔ Allow-listing outbound traffic

**🔑 Why:** Prevents data exfiltration and command-and-control attacks

---

### 6.3 Firewalls & WAF

- ✔ Network firewalls
- ✔ Web Application Firewall (WAF)
- ✔ DDoS protection
- ✔ L7 attack protection

**🔑 Why:** Stops attacks before they hit applications

---

### 6.4 Kubernetes / Container Security (If Applicable)

- ✔ Pod security policies / admission controls
- ✔ Non-root containers
- ✔ Image scanning
- ✔ Runtime protection
- ✔ Network policies between pods

**🔑 Why:** Most breaches exploit misconfigured containers, not code

---

### 6.5 Infrastructure Identity & Access (IAM)

- ✔ Least-privilege IAM roles
- ✔ No long-lived credentials
- ✔ Short-lived service identities
- ✔ Separation of duties

**🔑 Why:** Compromised credentials are the #1 cloud attack vector

---

## 🎯 Security Confidence Test

### You're security-ready if you can explain:

- ✔ Why OAuth ≠ authentication
- ✔ Why mTLS ≠ authorization
- ✔ Why zero-trust applies internally
- ✔ Why domain authorization still matters
- ✔ Why audit ≠ logging
