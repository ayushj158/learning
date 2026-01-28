## 🧠 ONE-LINE FINISHER

> **“OAuth authorizes, OIDC authenticates, tokens prove access, scopes gate APIs, ABAC protects data, and authorization is always layered.”**

---

# 🔐 AUTHN / AUTHZ — ONE-PAGER (BANKING / FS READY)

---

## 1️⃣ Core Mental Model (FOUNDATION)

| Concept            | Answers          | Notes       |
| ------------------ | ---------------- | ----------- |
| **Authentication** | Who are you?     | Identity    |
| **Authorization**  | What can you do? | Permissions |

> OAuth, OIDC, JWTs, scopes — **all fit here**

---

## 2️⃣ OAuth 2.0 — WHAT & WHY (CRITICAL)

| Item      | Reality                                    |
| --------- | ------------------------------------------ |
| OAuth 2.0 | **Authorization framework**                |
| Purpose   | Delegated access                           |
| NOT       | Authentication                             |
| Solves    | Password sharing, no revocation, no scopes |

**Issues**

* Access Token (JWT / opaque)
* Refresh Token (usually opaque)

🔥 *Banking use*: Open Banking, partners, mobile apps

---

## 3️⃣ OIDC vs OAuth (INTERVIEW GOLD)

| OAuth           | OIDC                    |
| --------------- | ----------------------- |
| Authorization   | Authentication          |
| “Can I access?” | “Who is the user?”      |
| Access token    | ID token + Access token |

> **OIDC sits on top of OAuth**

---

## 4️⃣ OAuth Core Actors (MUST NAME)

| Actor                | Meaning       |
| -------------------- | ------------- |
| Resource Owner       | User          |
| Client               | App / Service |
| Authorization Server | Issues tokens |
| Resource Server      | API           |

---

## 5️⃣ OAuth Flows — WHEN TO USE

| Flow                   | Use When              | Verdict   |
| ---------------------- | --------------------- | --------- |
| **Authorization Code** | User + browser/mobile | ✅ Default |
| **Client Credentials** | Service-to-service    | ✅         |
| Implicit               | Browser tokens        | ❌ Avoid   |
| Password Grant         | Credentials shared    | ❌ Avoid   |

🔥 *“Auth Code for users, Client Credentials for services.”*

---

## 6️⃣ Tokens — GOLDEN RULE

| Token              | Purpose      | Audience        |
| ------------------ | ------------ | --------------- |
| **ID Token (JWT)** | Identity     | Client (FE/BFF) |
| **Access Token**   | API access   | APIs            |
| **Refresh Token**  | Renew tokens | Auth Server     |

> ❗ **ID tokens never call APIs**

---

## 7️⃣ Token Types — QUICK FACTS

**ID Token**

* Always JWT
* UI/session only
* Safe even if exposed (no API access)

**Access Token**

* Short-lived (5–15 min)
* JWT or opaque

**Refresh Token**

* Long-lived
* Stored server-side
* Treated like credentials

---

## 8️⃣ JWT vs Opaque — TRADE-OFFS

| JWT         | Opaque          |
| ----------- | --------------- |
| Stateless   | Introspection   |
| Scales well | Easy revocation |
| Hard revoke | Central control |

**Rule**

* JWT → internal, short-lived
* Opaque → external, high security

---

## 9️⃣ Scopes vs Roles vs Claims (CONFUSION ZONE)

| Item       | Meaning            | Example       |
| ---------- | ------------------ | ------------- |
| **Scopes** | API permissions    | accounts.read |
| **Roles**  | User grouping      | CUSTOMER      |
| **Claims** | Attributes/context | region=UK     |

> **Scopes = what**, **Roles = who**, **Claims = context**

---

## 🔟 Authorization Models (FS IMPORTANT)

| Model    | Verdict                |
| -------- | ---------------------- |
| RBAC     | Simple, coarse         |
| **ABAC** | ✅ Preferred in banking |

**ABAC Example**

```
Allow if:
user owns account
AND amount < dailyLimit
AND account is ACTIVE
```

---

## 1️⃣1️⃣ Where Authorization MUST Happen

| Layer              | Purpose                  |
| ------------------ | ------------------------ |
| API Gateway        | Coarse (scopes)          |
| **Inside Service** | Fine-grained, data-level |

> Token valid ≠ data allowed

---

## 1️⃣2️⃣ Token Lifetime & Rotation

| Token   | Lifetime     |
| ------- | ------------ |
| Access  | 5–15 min     |
| Refresh | Hours / days |

**Why short-lived**

* Limit blast radius
* Reduce replay risk

---

## 1️⃣3️⃣ JWT Cryptography (WHY SAFE)

| Rule            | Meaning             |
| --------------- | ------------------- |
| Base64          | ❌ Not security      |
| Asymmetric keys | ✅ Standard          |
| Private key     | Signs (Auth Server) |
| Public key      | Verifies (Services) |

❌ Symmetric signing avoided (forgery risk)

---

## 1️⃣4️⃣ BFF Pattern (MANDATORY IN FS)

| Component | Uses            |
| --------- | --------------- |
| Frontend  | ID token        |
| BFF       | Access token    |
| Browser   | HttpOnly cookie |

**Why**

* Browser untrusted
* Prevent XSS token theft

---

## 1️⃣5️⃣ Service-to-Service Auth

| Pattern            | Verdict           |
| ------------------ | ----------------- |
| Token propagation  | Simple, risky     |
| **Token exchange** | ✅ Least privilege |

---

## 1️⃣6️⃣ Common Anti-Patterns ❌

* ID token used for APIs
* Long-lived access tokens
* Auth only at gateway
* Trusting frontend checks
* Over-propagating user tokens
* Symmetric JWT signing

---

## 1️⃣7️⃣ Final Architecture View

```
Client
 → API Gateway (scopes)
   → BFF (token handling)
     → Domain Services (ABAC + data checks)
```

---


