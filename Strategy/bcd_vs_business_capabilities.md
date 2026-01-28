# 📘 BCD vs Business Capability Model

### (Long-term Revision Notes)

---

## 1️⃣ One-Line Memory Hook (Very Important)

> **Capabilities = WHAT the business must be good at**
> **BCD = HOW systems and actors interact to make it happen**

If you remember just this, you’ll never confuse them again.

---

## 2️⃣ Core Purpose (Why They Exist)

| Aspect                 | Business Capability Model            | Business Context Diagram (BCD)                      |
| ---------------------- | ------------------------------------ | --------------------------------------------------- |
| **Primary Question**   | *What does the business do?*         | *How does the business work with systems & actors?* |
| **Why it exists**      | Strategy, investment, prioritization | Solution design, integration clarity                |
| **Decision supported** | *Where should we invest?*            | *How should we build it?*                           |
| **Audience**           | CXOs, Business Architects, Strategy  | Architects, Tech Leads, Product Teams               |

---

## 3️⃣ What Each Diagram Actually Shows

### 🧩 Business Capability Model — Shows:

* Stable business **abilities**
* Independent of org structure & tech
* Hierarchical (L1 → L2 → L3)
* Often heat-mapped

**Example (Client Accounts):**

* Manage Client Accounts
  → Client Onboarding
  → Account Maintenance
  → Compliance Monitoring
  → Account Closure

> These remain valid even if systems change.

---

### 🔷 Business Context Diagram (BCD) — Shows:

* **Actors** (users, external parties)
* **Systems** (internal & external)
* **Interactions & flows**
* **System boundaries / contexts**

**Example (Client Onboarding):**

* Client → Portal → Onboarding System
* Onboarding → KYC Provider
* Onboarding → Core Banking
* RM → CRM → Approval

> This changes when systems or integrations change.

---

## 4️⃣ Stability vs Change (Critical Distinction)

| Dimension                      | Capability Model | BCD             |
| ------------------------------ | ---------------- | --------------- |
| Stability over time            | ✅ Very stable    | ❌ Changes often |
| Impacted by system replacement | ❌ No             | ✅ Yes           |
| Impacted by org restructure    | ❌ No             | ❌ Usually no    |
| Impacted by regulation         | ✅ Sometimes      | ✅ Often         |

📌 **Rule**:

> If it changes every year → it’s NOT a capability.

---

## 5️⃣ WHAT vs HOW vs WHO

| Dimension | Capability Model      | BCD                    |
| --------- | --------------------- | ---------------------- |
| WHAT      | ✅ Primary focus       | ⚠️ Secondary           |
| HOW       | ❌ Not shown           | ✅ Primary focus        |
| WHO       | ❌ Not shown           | ✅ Actors clearly shown |
| SYSTEMS   | ❌ Technology-agnostic | ✅ System-centric       |

---

## 6️⃣ Common Mistakes (Exam + Real World)

### ❌ Mistakes with Capabilities

* Mixing **process steps** into capabilities
  ❌ “Verify documents manually”
  ✅ “Client Verification”

* Naming capabilities after systems
  ❌ “CRM Management”
  ✅ “Client Relationship Management”

---

### ❌ Mistakes with BCD

* Mixing **capabilities, org charts, data models** in one diagram
* Treating BCD as an enterprise architecture diagram
* Forgetting actors (humans!)

> A BCD is NOT a capability map
> A capability map is NOT a system diagram

---

## 7️⃣ How They Work Together (Very Important)

### Correct Flow (Enterprise → Solution):

1. **Capability Model**

   * Identify weak / strategic capabilities
   * Decide **where to invest**

2. **BCD**

   * Design **how systems interact**
   * Decide **how to implement**

📌 Example:

* Capability says:
  → *Improve Client Onboarding*
* BCD shows:
  → New onboarding system
  → API with KYC provider
  → CRM integration

---

## 8️⃣ Side-by-Side “Revision Table”

| Dimension      | Business Capability Model | Business Context Diagram |
| -------------- | ------------------------- | ------------------------ |
| Nature         | Conceptual                | Structural               |
| Focus          | Business ability          | System interaction       |
| Level          | Enterprise / Strategy     | Solution / Program       |
| Technology     | Tech-agnostic             | Tech-aware               |
| Time horizon   | Long-term                 | Short–mid term           |
| Changes often? | No                        | Yes                      |
| Used by        | Strategy, EA              | Architecture, Delivery   |
| Answers        | “What must we do well?”   | “How does it work?”      |

---

## 9️⃣ Interview / Design One-Liners (Gold)

* “Capabilities are **stable business abilities**, not processes or systems.”
* “A BCD explains **interaction and boundaries**, not strategy.”
* “We use capability models to **decide investment**, and BCDs to **design solutions**.”
* “If you put systems into a capability model, you’ve already lost abstraction.”

---

## 🔟 If You Remember Only 5 Things

1. **Capability = WHAT**, **BCD = HOW**
2. Capabilities are **stable**, BCDs **change**
3. Capabilities ignore systems; BCDs revolve around them
4. Capability → investment decision
5. BCD → solution & integration design

---

If you want, next we can:

* Do **one full worked example** (Client Accounts end-to-end)
* Or create a **1-page printable cheat sheet**
* Or map **Capabilities → BCD → APIs → Microservices** (very interview-strong)

Just tell me where you want to go next.
