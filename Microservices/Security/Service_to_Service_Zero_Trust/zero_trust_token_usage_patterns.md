# 🔐 Zero Trust Security Architecture
## Token Patterns

---

## Summary

> "Zero Trust assumes breach and removes implicit trust from the network. In microservices, this means every request must carry verifiable identity and explicit authorization. 

**Token propagation breaks Zero Trust by reusing broad credentials across services**, expanding blast radius. **Token exchange aligns with Zero Trust** by issuing audience-specific, least-privileged tokens per hop, enabling strong containment, accountability, and auditability — which is why it's preferred in financial systems"

## 1️⃣ What Zero Trust REALLY Means

**Zero Trust is not "no trust".**

It is: **"Trust is never implicit and never permanent."**

### Traditional Model:
- Outside = untrusted
- Inside = trusted

### Zero Trust Model:
- Everything = untrusted
- Every request = verified

---

## 2️⃣ Core Principles of Zero Trust (Internalize These)

### 🔑 Principle 1: Strong Identity Everywhere

**Users have identities**,
**Services have identities**,
**Workloads have identities**

Identity is:
- Cryptographic
- Verifiable
- Short-lived

➡️ **This is why mTLS exists.**

---

### 🔑 Principle 2: Explicit Verification per Request

Every request must prove:
- Who is calling
- What it wants to do
- Whether it is allowed now

**No:**
- ❌ Session trust
- ❌ Network trust
- ❌ "It's internal"

---

### 🔑 Principle 3: Least Privilege

Access is:
- Narrow
- Time-bound
- Context-aware

**No broad, reusable credentials.**

---

### 🔑 Principle 4: Assume Breach

**This is the most important one.**

**"Design as if something is already compromised."**

So security focuses on:
- Containment
- Blast-radius reduction
- Detection

---

## 3️⃣ Zero Trust in Microservices (Practical View)

In microservices, Zero Trust translates to:

| Concern | Zero Trust Control |
|---------|-------------------|
| Service identity | mTLS |
| Authorization | OAuth |
| Scope restriction | Token scoping |
| Context awareness | ABAC |
| Lateral movement | Explicit trust graph |
| Detection | Logs, metrics, traces |

---

## 4️⃣ Token Propagation (Simple but Dangerous)

### What it is

- Service A receives a token
- Service A forwards the **same token** to Service B, C, D…

```
Client → A → B → C        (same token everywhere)
```

### Why teams choose it

- ✔ Simple
- ✔ Fewer OAuth calls
- ✔ Easy to implement

### Why it violates Zero Trust

#### ❌ Breaks least privilege
- Token often has union of permissions
- Downstream services get more access than needed

#### ❌ Expands blast radius
- Compromised Service A can reuse token everywhere

#### ❌ Weak service accountability
- Services see "user" but not who delegated the call

#### ❌ Hard to audit
- Who actually called Ledger? User or Account service?

### When token propagation is acceptable

- Small systems
- Low-risk domains
- Short call chains

⚠️ **Rare in FS core flows.**

---

## 5️⃣ Token Exchange (Zero-Trust-Aligned)

### What it is

- Service A receives token
- **Exchanges it** for a new token for Service B

```
Client → A
         ↓ exchange
         Token(B)
         ↓
         B
```

### What changes in the new token

- **Audience** = Service B
- **Scopes** = minimal for B
- **Actor chain** preserved (delegation)

### Why token exchange is powerful

#### ✅ Enforces least privilege
- Each hop gets only what it needs

#### ✅ Contains blast radius
- Compromised A cannot reuse B's token elsewhere

#### ✅ Clear accountability
- Token can say:
  - `sub = user`
  - `act = account-service`

#### ✅ Strong auditing
- "Who called whom, on behalf of whom"

---

## 6️⃣ Token Exchange Flow (Concrete)

**Step-by-step:**

1. User → BFF (access token)
2. BFF → Account Service (token A)
3. Account Service → Auth Server:
   - "Exchange token A for Ledger access"
4. Auth Server issues token B:
   - `audience = ledger-service`
   - `scope = ledger.post`
5. Account Service → Ledger Service (token B)

**Ledger sees:**
- Caller = account-service
- Acting for = user123
- Allowed = ledger.post

---

## 7️⃣ Zero Trust vs Token Propagation (Critical Link)

### Token Propagation assumes:

- ❌ Caller is trusted
- ❌ Downstream needs same permissions
- ❌ One token fits all

### Token Exchange assumes:

- ✔ Caller may be compromised
- ✔ Permissions must be minimized
- ✔ Each hop re-verifies trust

**➡️ Token Exchange is the natural consequence of Zero Trust**

---

## 8️⃣ Threat Comparison (Very Interview-Relevant)

| Threat | Propagation | Exchange |
|--------|-------------|----------|
| Service compromise | ❌ Large blast | ✅ Contained |
| Lateral movement | ❌ Easy | ✅ Hard |
| Token replay | ❌ Wide | ✅ Narrow |
| Audit clarity | ❌ Weak | ✅ Strong |
| Least privilege | ❌ Broken | ✅ Enforced |

---

## 9️⃣ Why FS Strongly Prefers Token Exchange

Financial systems need:
- Clear accountability
- Regulatory audits
- Fine-grained entitlements
- Contained breaches

Token exchange supports:
- Delegation semantics
- Non-repudiation
- Policy-driven access

---

## 🔟 Common Anti-Patterns (Call These Out)

- ❌ "Internal token is safe"
- ❌ "We trust our services"
- ❌ "Scopes are enough"
- ❌ "We'll fix later if compromised"

**These all violate Zero Trust.**

