# Data Security: Complete Guide

## Executive Summary

**Data security in financial systems** spans:
- Encryption in transit and at rest
- Strong key and secrets management
- Controlled access to sensitive fields
- Strict PII handling aligned with GDPR principles

**Core Goal:** Make stolen data useless, limited, or auditable.

**Key Insight:** Encryption protects data storage and transport, while tokenization, masking, and field-level encryption reduce exposure even during breaches. Proper key management using KMS and HSMs is critical—control of keys determines the real security of encrypted data.

---

## 🎯 First: What Data Security Is REALLY About

Data security is **not just encryption**.

It is about **controlling who can see, use, move, and leak data** — even after a breach.

### Design Goal
```
Make stolen data useless, limited, or auditable
```

---

## 🧠 Cross-Cutting Principles (Very Important)

| # | Principle | Meaning in Practice |
|---|-----------|-------------------|
| 1 | **Assume breach** | Design so stolen data is useless |
| 2 | **Least privilege** | Narrow access to data & keys |
| 3 | **Defense in depth** | Encrypt + tokenize + mask |
| 4 | **Key control = data control** | Protect keys more than data |
| 5 | **Lifecycle thinking** | Creation → use → retention → deletion |

---

## 1️⃣ End-to-End Data Security Flow (Mental Model)

```
User Request
    ↓
[In Transit]      → TLS / mTLS (encrypt between systems)
    ↓
[At Rest]         → Disk/DB encryption (encrypt on disk)
    ↓
[Sensitive Fields] → Field-level encryption / tokenization
    ↓
[Keys]            → KMS / HSM (protect encryption keys)
    ↓
[Access Control]  → IAM + ABAC (who can see/use data)
    ↓
[Visibility]      → Masking (what users see)
    ↓
[Lifecycle]       → Retention & deletion (when to erase)
```

---

## 📊 Master Reference: Data Security Controls

| Control | What It Does | Threats Addressed | When to Use | Implementation | Key Rules | Common Anti-Patterns |
|---------|-------------|------------------|-------------|-----------------|-----------|----------------------|
| **Encryption in Transit** | Encrypts data while moving between systems | Sniffing, MITM, token theft | Always (north–south & east–west) | TLS (external), mTLS (internal) | TLS everywhere, strong ciphers, cert rotation | ❌ "Internal is safe", ❌ Plain HTTP |
| **Encryption at Rest** | Encrypts stored data (DBs, disks, backups) | Storage compromise, offline access | Mandatory for regulated data | Disk encryption, DB encryption, cloud defaults | Encryption ≠ access control | ❌ Thinking it protects DB credential compromise |
| **Key Management (KMS)** | Centralized encryption key management | Key exposure, uncontrolled access | Always for production | Cloud KMS, IAM policies, envelope encryption | Separate data keys & master keys | ❌ Hardcoding keys, ❌ One key for everything |
| **Hardware Security Module (HSM)** | Tamper-resistant key storage & crypto | Root key compromise | High-risk domains (payments, signing) | Managed HSM, PCI HSMs | Root keys never leave HSM | ❌ Software-only keys for root trust |
| **Envelope Encryption** | Encrypt data with data-key, encrypt key with master-key | Blast radius, rotation difficulty | Standard best practice | Data Key → encrypt data; KMS key → encrypt data key | Never encrypt large data directly with master key | ❌ Directly encrypting data with master key |
| **Secrets Management** | Secure storage of credentials & secrets | Secret leakage, reuse | Always (no exceptions) | Secrets manager, dynamic secrets, rotation | Secrets ≠ config | ❌ Secrets in Git, ❌ Env vars forever |
| **Data Masking** | Irreversibly hiding part of sensitive data | Human exposure, log leakage | Display & observability | Masking rules (****1234) | Mask everywhere except where truly needed | ❌ Logging raw PII |
| **Tokenization** | Replacing sensitive data with reversible token | Scope of sensitive storage | When real value rarely needed | Token vault, mapping table | Tokens ≠ encryption | ❌ Token without proper vault security |
| **Field-Level Encryption** | Encrypting specific columns/fields | Insider threats, data access | Highly sensitive PII | App-level crypto + KMS keys | Encrypt only what's necessary | ❌ Encrypting fields used in queries |
| **PII Handling (GDPR)** | Controlled lifecycle of personal data | Legal/regulatory violations | Any personal data | Minimize, restrict, expire, delete | Data minimization & purpose limitation | ❌ "Store now, decide later" |
| **Data Minimization** | Collect/store only what's needed | Breach impact | Always | Schema design & contracts | Less data = less risk | ❌ Over-collecting "just in case" |
| **Retention & Deletion** | Automatic data expiry & erasure | Long-term exposure | Mandatory under GDPR | TTLs, archival policies | Design for deletion | ❌ Manual cleanup |
| **Access Control to Data** | Restrict who can read/write data | Unauthorized access | Always | IAM, ABAC, service identities | Least privilege | ❌ Shared DB users |
| **Logs & Backups Security** | Protecting secondary data copies | Log/backup leakage | Always | Encrypt, mask, restrict access | Logs are data too | ❌ Full payload logging |

---

## 🔐 Control Deep Dives

### 1️⃣ Encryption in Transit

**Purpose:** Prevent sniffing, MITM attacks, and token theft while data moves between systems.

#### External Traffic (Client → Edge)
```
HTTPS / TLS 1.3
├─ Strong ciphers only
├─ Certificate management
└─ HSTS headers
```

**Must-Haves:**
- ✅ TLS 1.3 (or 1.2 minimum)
- ✅ Strong cipher suites (no RC4, DES, MD5)
- ✅ Certificate pinning (for sensitive apps)
- ✅ No downgrade to HTTP

#### Internal Traffic (Service → Service, Service → Database)

```
mTLS / TLS
├─ Mutual certificate authentication
├─ Service identity verification
└─ Encrypted channels
```

**Rule:** "Internal is safe" = False. Encrypt every hop.

**Examples:**
- Service → Database: TLS connection
- Service → Service: mTLS (mutual TLS)
- Service → Cache: Encrypted channel
- Service → Message Queue: Encrypted channel

---

### 2️⃣ Encryption at Rest

**Purpose:** Protect data if storage is stolen, accessed offline, or compromised via credentials.

#### Threat Addressed

```
Attacker steals:
- Database backups
- Disk snapshots
- Storage devices
- Cloud storage buckets

Encrypted at-rest → Data useless without keys
```

#### Implementation

| Layer | How |
|-------|-----|
| **Disk Level** | Full disk encryption (BitLocker, LUKS) |
| **Database Level** | Native DB encryption (TDE, InnoDB encryption) |
| **Object Storage** | Encrypted S3, GCS, Azure blobs |
| **Backups** | Encrypted snapshots, encrypted archives |

#### Important Limitation

⚠️ **Encryption at rest does NOT protect against:**
- Compromised database credentials
- Unauthorized application access
- Insider threats with proper permissions

**Solution:** Combine with access controls and field-level encryption.

---

### 3️⃣ Key Management (KMS)

**Purpose:** Centralize and control encryption keys, preventing key exposure and uncontrolled access.

#### Architecture

```
Application
    ↓ (needs to encrypt data)
KMS (requests key)
    ↓ (if authorized)
Returns encryption key (or encrypted key)
    ↓
Application encrypts/decrypts
```

#### Key Principles

| Principle | Meaning |
|-----------|---------|
| **Separation** | Data keys ≠ Master keys |
| **Rotation** | Regular key rotation (quarterly/annually) |
| **Audit** | All key access logged |
| **Least Privilege** | Only services that need the key get access |

#### Envelope Encryption (Recommended)

```
Data encryption flow:
┌─────────────────────────────────────┐
│ Data (plaintext)                    │
│ ↓                                   │
│ [KMS generates Data Key]            │
│ ↓                                   │
│ Encrypt Data with Data Key          │
│ ↓                                   │
│ [Result: Encrypted Data]            │
│                                     │
│ Data Key (plaintext)                │
│ ↓                                   │
│ [Encrypt with Master Key]           │
│ ↓                                   │
│ [Result: Encrypted Data Key]        │
└─────────────────────────────────────┘

Stored as: [Encrypted Data] + [Encrypted Data Key]
```

**Benefits:**
- Data key used once (no rotation needed)
- Master key never touches application
- Can rotate master key without re-encrypting all data

---

### 4️⃣ Hardware Security Module (HSM)

**Purpose:** Tamper-resistant key storage for root trust and high-value keys.

#### When to Use

| Scenario | Use HSM? | Why |
|----------|----------|-----|
| **Payment processing** | ✅ Yes | PCI DSS requirement, highest risk |
| **TLS certificate signing** | ✅ Yes | Root trust, cannot compromise |
| **API key encryption** | ⚠️ Maybe | Depends on risk level |
| **Database encryption** | ⚠️ Maybe | Cloud KMS often sufficient |
| **Session tokens** | ❌ No | Managed by app/auth system |

#### Key Requirement

```
HSM Rule: Root keys never leave HSM
└─ Keys stored inside tamper-resistant hardware
└─ Crypto operations happen inside HSM
└─ Private key never exposed to software
```

#### Common Platforms
- AWS CloudHSM
- Azure Dedicated HSM
- On-premise Thales/Gemalto HSMs

---

### 5️⃣ Secrets Management

**Purpose:** Secure storage and rotation of credentials, API keys, and certificates.

#### What's a Secret?

```
Secrets = credentials + API keys + certificates + tokens

❌ NOT secrets: config values, feature flags, deployment info
```

#### Implementation Requirements

| Requirement | Details |
|------------|---------|
| **Storage** | Encrypted at rest, access-controlled |
| **Rotation** | Automatic rotation (quarterly/monthly) |
| **Audit** | Log every access attempt |
| **Revocation** | Ability to instantly revoke secrets |
| **Least Privilege** | Service only gets secrets it needs |

#### Anti-Patterns

```
❌ Secrets in Git (even if encrypted, too risky)
❌ Secrets in environment variables (visible in processes)
❌ Hardcoded secrets (absolute no)
❌ Shared secrets (one secret per service minimum)
❌ Manual rotation (automate or fail)
```

#### Best Practice: Dynamic Secrets

```
Traditional:
  1. Create DB password
  2. Store in Vault
  3. App uses password
  4. Manual rotation

Dynamic:
  1. App requests credentials from Vault
  2. Vault creates temporary DB user
  3. App uses temporary credentials
  4. Credentials auto-expire (5 min – 1 hour)
```

---

### 6️⃣ Data Masking

**Purpose:** Irreversibly hide sensitive data in logs, UIs, and support tools.

#### Masking Patterns

| Data Type | Masking Pattern | Example |
|-----------|-----------------|---------|
| **SSN** | XXX-XX-#### | 123-45-6789 → XXX-XX-6789 |
| **Credit Card** | ****1234 | 4532 1234 5678 9010 → ****9010 |
| **Email** | j***@example.com | john@example.com → j***@example.com |
| **Phone** | (XXX) XXX-1234 | (555) 123-4567 → (XXX) XXX-4567 |
| **Full PII** | [REDACTED] | Full name → [REDACTED] |

#### Where to Mask

| Location | Masking Required? | Why |
|----------|-------------------|-----|
| **Logs** | ✅ Yes | Logs are often insecure, widely shared |
| **UI** | ✅ Yes (mostly) | Users shouldn't see full data unnecessarily |
| **Support Tools** | ✅ Yes | Support staff doesn't need full PII |
| **APIs** | ⚠️ Selective | Only where client doesn't need full value |
| **Internal Services** | ⚠️ If logged | Only for log masking, not actual data |
| **Database** | ❌ No | Services need real data for processing |

#### Implementation

```java
// Logging with masking
logger.info("User login: {}", maskEmail(email));
// Output: User login: j***@example.com

// UI display
userProfile.email = maskEmail(user.email);
// Shows: j***@example.com

// Database
// Stores: john.doe@example.com (unmasked)
```

---

### 7️⃣ Tokenization

**Purpose:** Replace sensitive data with reversible tokens, reducing scope of sensitive data storage.

#### Tokenization vs Encryption

| Aspect | Tokenization | Encryption |
|--------|--------------|-----------|
| **Reversibility** | Yes (with vault) | Yes (with key) |
| **Scope** | Specific values | Any data |
| **Storage** | Small token + vault | Encrypted data anywhere |
| **Performance** | Overhead (vault lookup) | Minimal |
| **Use Case** | PAN, account numbers | Sensitive fields, compliance |

#### How It Works

```
Credit Card: 4532 1234 5678 9010

Step 1: Request token from Vault
  Input: 4532 1234 5678 9010
  
Step 2: Vault creates mapping
  Vault: { token: "tk_9a8b7c6d", value: "4532 1234 5678 9010" }
  
Step 3: Return token
  Application stores: tk_9a8b7c6d
  
Step 4: Later, need real value
  Request: detokenize(tk_9a8b7c6d)
  Response: 4532 1234 5678 9010
```

#### When to Use

- ✅ Credit card numbers (PAN)
- ✅ Account numbers
- ✅ National IDs
- ✅ Passport numbers
- ❌ Frequently searched data (use encryption instead)
- ❌ Data used in database queries

---

### 8️⃣ Field-Level Encryption

**Purpose:** Encrypt specific sensitive columns/fields, protecting against insiders and credential compromise.

#### Implementation

```sql
-- Without field-level encryption
SELECT id, name, ssn, salary FROM employees;
-- Requires: Access to table = access to all fields

-- With field-level encryption
SELECT id, name, encrypt(ssn), encrypt(salary) FROM employees;
-- Requires: Access to table + decryption key
```

#### What to Encrypt

| Data Type | Encrypt? | Why |
|-----------|----------|-----|
| **SSN** | ✅ Yes | Highly sensitive, rarely searched |
| **PAN** | ✅ Yes | Payment data, regulatory |
| **Salary** | ✅ Yes | Personal, sensitive |
| **Email** | ⚠️ Maybe | Used in searches/queries |
| **Name** | ❌ No | Used for display, common |
| **User ID** | ❌ No | Used for joins, querying |

#### Important Limitation

⚠️ **Cannot encrypt fields used in queries:**

```sql
-- This FAILS
SELECT * FROM users WHERE email = 'john@example.com';
-- If email is encrypted, can't match

-- Solution: Encrypt + use hash for lookups
SELECT * FROM users WHERE email_hash = hash('john@example.com');
-- Then decrypt email for display
```

---

### 9️⃣ PII Handling (GDPR Compliance)

**Purpose:** Legal and regulatory compliance for personal data.

#### GDPR Principles

| Principle | Meaning |
|-----------|---------|
| **Lawfulness** | Consent or legal basis required |
| **Fairness** | Transparent data use |
| **Data Minimization** | Collect only what's necessary |
| **Accuracy** | Keep data current |
| **Storage Limitation** | Don't keep longer than needed |
| **Integrity/Confidentiality** | Encrypt and protect |
| **Accountability** | Document decisions |

#### Data Lifecycle (Birth to Death)

```
1. Collection
   └─ Only collect with purpose & consent

2. Storage
   └─ Encrypt, minimize, restrict access

3. Processing
   └─ Use only for stated purpose

4. Retention
   └─ Delete after purpose fulfilled

5. Deletion
   └─ Remove all copies (hard, backups too)
```

#### Common Mistakes

```
❌ "Store now, decide later" (no purpose defined)
❌ "We might need it someday" (not a legal basis)
❌ "Users gave consent, so keep forever" (storage limits apply)
❌ "Delete from DB, but backup has it" (incomplete deletion)
❌ "Process is manual" (no audit trail)
```

---

### 🔟 Data Minimization

**Purpose:** Reduce breach impact by collecting and storing only necessary data.

#### Principle

```
Less data = Less risk = Easier to protect & delete
```

#### By Lifecycle Stage

| Stage | Minimization Strategy |
|-------|----------------------|
| **Collect** | Ask only for required fields, not "nice-to-have" |
| **Store** | Don't persist unnecessary data (cache instead) |
| **Process** | Tokenize/mask before passing to non-essential services |
| **Retain** | Set TTLs on temporary data |
| **Delete** | Scheduled deletion for expired data |

#### Examples

```
❌ Over-collection
Store: full address, phone, email, DOB, income, employment history
Needed for: Payment processing

✅ Data minimization
Store for payment: zip code, email (for receipts)
Store separately: full address (encrypted, deleted after 1 year)
```

---

### 1️⃣1️⃣ Retention & Deletion

**Purpose:** Automatic data expiry to limit long-term exposure (required under GDPR).

#### Strategy

```
Retention Policy:
├─ Logs: 30 days
├─ PII (fulfilled purpose): 1 year
├─ Financial records: 7 years
├─ Audit logs: 3 years
└─ Backups: encrypted, auto-expire
```

#### Implementation

| Approach | Details |
|----------|---------|
| **TTL (Time to Live)** | Database auto-deletes after timeout |
| **Scheduled Jobs** | Nightly cleanup of expired records |
| **Retention Policies** | S3 object lifecycle, Cloud Storage retention |
| **Archive + Delete** | Compliance copy, then delete original |

#### Challenges

```
Hard deletion = Remove from:
  ✅ Production database
  ✅ All backups
  ✅ Archive storage
  ✅ Replicas
  ✅ Caches
  ✅ Search indices
  ✅ Log files
  ✅ Message queues
```

---

### 1️⃣2️⃣ Access Control to Data

**Purpose:** Restrict who can read/write data, preventing unauthorized access and insider threats.

#### Implementation

```
Identity Layer:
├─ Service identities (mTLS certs)
└─ User identities (tokens)

Authorization Layer:
├─ Role-based (coarse)
└─ ABAC (fine-grained)

Enforcement:
├─ IAM policies (cloud-level)
├─ Database permissions
└─ Application authorization
```

#### Principle: Least Privilege

```
❌ Shared DB user (all apps have full access)
✅ Per-service DB user (each app only what it needs)

❌ Role "read_database"
✅ Permission "read_users_table", "read_transactions_table"

❌ Service has access to all data
✅ Service only sees customer data for its tenant
```

#### Database Access Control

```sql
-- Insecure
CREATE USER app_user GRANT ALL ON *.* TO 'app_user'@'%';
-- Any service with this user = full DB access

-- Secure (per-service)
CREATE USER service_auth GRANT SELECT, INSERT ON auth.* TO 'service_auth'@'%';
CREATE USER service_payment GRANT SELECT ON payments.* TO 'service_payment'@'%';
-- Each service only accesses its tables
```

---

### 1️⃣3️⃣ Logs & Backups Security

**Purpose:** Protect logs and backups (secondary data copies that often leak).

#### The Problem

```
Logs often contain:
❌ Full request/response payloads (with tokens, PII)
❌ SQL queries with sensitive data
❌ Exception details with stack traces
❌ Unencrypted, widely accessible
```

#### Solutions

| Solution | Implementation |
|----------|-----------------|
| **Masking** | Mask PII before logging |
| **Encryption** | Encrypt logs at rest |
| **Access Control** | Only certain roles read logs |
| **Redaction** | Remove sensitive fields from logs |
| **Separate Storage** | Logs in secure vault, not app servers |

#### Backup Security

```
Backups must be:
✅ Encrypted at rest
✅ Encrypted in transit
✅ Access-controlled (not all admins can restore)
✅ Retention-based (auto-delete old backups)
✅ Tested for recovery (but not exposed)
```

#### What NOT to Log

```
❌ Passwords / credentials
❌ Tokens / JWTs
❌ Credit card numbers
❌ SSNs
❌ Full request/response bodies
❌ Query parameters with sensitive data
```

#### What TO Log

```
✅ User action (who, what, when)
✅ Authorization decisions (denied, allowed)
✅ Errors (sanitized, no PII)
✅ Security events (login attempts, failures)
✅ API access patterns (for audits)
```

---

## 🎯 Defense in Depth Example

**Scenario:** Attacker compromises database server.

```
Layer 1: Encryption at Rest
  └─ Data encrypted on disk
  └─ Attacker can read encrypted bytes (useless)

Layer 2: Key Management (KMS)
  └─ Keys stored separately in KMS
  └─ Attacker cannot decrypt without KMS access

Layer 3: Field-Level Encryption
  └─ Even if DB accessible, sensitive fields encrypted
  └─ Requires separate decryption key

Layer 4: Masking & Tokenization
  └─ PII masked in logs
  └─ Tokens instead of real values in some fields
  └─ Less sensitive data to extract

Result: Breach = minimal usable data
```

---

## ✅ Best Practices Checklist

### Encryption & Keys
- ✅ TLS 1.3+ for all traffic (transit)
- ✅ Encryption at rest for all databases
- ✅ mTLS for service-to-service
- ✅ KMS for key management
- ✅ HSM for root keys (payments/signing)
- ✅ Envelope encryption (separate master & data keys)
- ✅ Regular key rotation policy

### Secrets & Credentials
- ✅ Secrets manager for all credentials
- ✅ Never commit secrets to Git
- ✅ Automatic rotation (at least quarterly)
- ✅ Dynamic secrets where possible
- ✅ Audit all access to secrets
- ✅ Revoke immediately if leaked

### Data Protection
- ✅ Minimize data collection (only what's needed)
- ✅ Field-level encryption for sensitive PII
- ✅ Tokenization for PAN, IDs
- ✅ Masking in logs and UIs
- ✅ Access control at DB level (least privilege)
- ✅ Retention policies with auto-deletion
- ✅ GDPR compliance mapping

### Logs & Backups
- ✅ Encrypt logs at rest
- ✅ Mask PII in logs (no full tokens, cards, SSNs)
- ✅ Restrict log access
- ✅ Encrypt backups
- ✅ Test restore procedures (without exposing data)
- ✅ Auto-expire old backups
- ✅ Separate backup encryption keys

### Monitoring & Audit
- ✅ Log all encryption key access
- ✅ Alert on unusual data access patterns
- ✅ Audit trails for data deletions
- ✅ Regular security assessments
- ✅ Data discovery (know what PII you have)
- ✅ Regular penetration testing

---

## 🚨 Common Anti-Patterns

| Anti-Pattern | Why It's Wrong | Correct Approach |
|--------------|----------------|------------------|
| ❌ One key for everything | Rotation requires re-encrypting all data | Use envelope encryption (data + master keys) |
| ❌ Hardcoding secrets | Exposed in code, logs, history | Use secrets manager, dynamic secrets |
| ❌ Secrets in env variables | Visible in process listings | Use secrets manager API calls |
| ❌ Encrypt at rest only | Insecure in transit & in memory | Encrypt in transit + at rest + in memory |
| ❌ Logging raw PII | Logs often leak (insecure access) | Mask before logging |
| ❌ Field encryption + queries | Can't search encrypted fields | Use tokenization or hash + encrypt |
| ❌ Internal DB, no encryption | "Internal = safe" is false | Encrypt all databases |
| ❌ Backups not encrypted | Backups leak just like data | Encrypt backups separately |
| ❌ Manual key rotation | Inevitable miss/delay | Automate rotation |
| ❌ No access control | All admins = all data | ABAC, least privilege, service identities |
| ❌ "Delete from DB, keep backup" | Incomplete deletion (GDPR violation) | Delete from all copies |
| ❌ Store data "just in case" | No legal basis (GDPR), increased risk | Minimize collection |

---

## 📝 Summary: The Five Layers

| Layer | What | How | Protects Against |
|-------|------|-----|------------------|
| **Transport** | Data in motion | TLS, mTLS | Sniffing, MITM |
| **Storage** | Data at rest | Disk/DB encryption | Storage theft |
| **Keys** | Encryption keys | KMS, HSM | Key exposure |
| **Access** | Who sees data | IAM, ABAC, secrets management | Unauthorized access |
| **Visibility** | What's exposed | Masking, tokenization, field encryption | Insider threats, logs |

**Final Rule:** No single layer provides complete security. All five must work together.



