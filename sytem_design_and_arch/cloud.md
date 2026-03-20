Perfect — GCP primary, confident across the board, focused on the 5 areas that matter most for FS GCC interviews. Let me build your targeted prep map.

---

# ☁️ GCP Cloud Prep Map — FS GCC EM Level

## Your 5 Focus Areas — Priority Order for FS GCC

| Priority | Area | Why Critical for FS GCC |
|---|---|---|
| 1 | **Multi-Region Architecture** | 99.999% SLA, data residency, DR |
| 2 | **Security & Compliance** | FCA, PCI-DSS, GDPR — non-negotiable |
| 3 | **Networking Deep Dive** | VPC design, private connectivity, Zero Trust |
| 4 | **Kubernetes on GCP (GKE)** | Primary compute for FS microservices |
| 5 | **Cost Optimisation** | EM-level commercial awareness |

---

## Curriculum Structure

Given your confidence level — I won't teach basics. I'll go straight to **FS-specific depth, tradeoffs and interview-level answers** for each area.

Let's start with the highest-weight topic:

---

# 🌍 AREA 1 — Multi-Region Architecture on GCP

## GCP Region & Zone Fundamentals (Quick Recap)

```
GCP Geography:
Region: Independent geographic area (e.g. europe-west2 London)
Zone:   Isolated location within a region (e.g. europe-west2-a/b/c)
        Each zone = independent failure domain

FS Relevant GCP Regions:
UK:  europe-west2 (London) — 3 zones
EU:  europe-west1 (Belgium) — 3 zones
EU:  europe-west3 (Frankfurt) — 3 zones
EU:  europe-west4 (Netherlands) — 3 zones
EU:  europe-west6 (Zurich) — 3 zones ← Swiss banking
APAC: asia-southeast1 (Singapore) — 3 zones
```

---

## GCP Multi-Region Architecture Patterns

### Pattern 1 — Multi-Zonal (99.99% within region)

```
Single Region: europe-west2 (London)
3 Zones: europe-west2-a, europe-west2-b, europe-west2-c

Components spread across all 3 zones:
→ GKE: node pools across all 3 zones (regional cluster)
→ Cloud SQL: primary in zone-a, replica in zone-b
→ Cloud Spanner: automatically multi-zonal
→ Cloud Load Balancing: global by default, routes to healthy zone

Failure tolerance:
→ Single zone failure → traffic shifts to other 2 zones
→ Recovery: automatic, seconds
→ Achieves: ~99.99% availability
```

---

### Pattern 2 — Multi-Regional (99.999% across regions)

```
UK Stack:
  Primary:   europe-west2 (London)
  Secondary: europe-west1 (Belgium) ← as per your jurisdiction design

EU Stack:
  Primary:   europe-west1 (Belgium) OR europe-west3 (Frankfurt)
  Secondary: europe-west4 (Netherlands) OR europe-west6 (Zurich)

Active-Active across regions:
→ Global Load Balancer routes to nearest healthy region
→ Both regions serve live traffic simultaneously
→ If primary region fails → secondary takes 100% traffic
→ Achieves: ~99.999% availability

GCP Advantage over AWS here:
→ Google's global load balancer is TRULY global
→ Single anycast IP — routes to nearest healthy backend
→ No Route 53 DNS TTL propagation delay
→ Failover: seconds not minutes ✅
```

---

## GCP Global Load Balancing — Deep Dive

This is a major GCP differentiator vs AWS — interviewers love this:

```
AWS approach:
→ Route 53 (DNS-based) → regional ALBs
→ Failover = DNS TTL change (30-60 seconds)
→ Client must re-resolve DNS

GCP approach:
→ Single global anycast IP address
→ Traffic enters Google's network at nearest PoP
→ Routed internally on Google's backbone to nearest healthy backend
→ Failover: milliseconds — no DNS change needed ✅
→ Client never changes IP — Google's network handles routing

FS Impact:
"At Lloyds using GCP, our payment API had a single global IP.
When europe-west2 degraded, traffic shifted to europe-west1
in under 10 seconds — no client impact, no DNS TTL wait.
This was critical for our 99.999% SLA."
```

### GCP Load Balancing Services

| Service | Layer | Use Case | FS Application |
|---|---|---|---|
| **Global External HTTPS LB** | L7 | Internet-facing APIs | Customer-facing payment APIs |
| **Regional Internal LB** | L7 | Internal microservices | Service-to-service within VPC |
| **Global External TCP/SSL Proxy** | L4 | TCP traffic | Legacy protocol support |
| **Network LB** | L4 | Ultra-low latency | HFT, FIX protocol |
| **Traffic Director** | Service Mesh | GKE service mesh | Internal microservices routing |

---

## GCP Multi-Region Data Architecture

### Cloud Spanner — The GCP FS Secret Weapon

```
What makes Spanner unique:
→ Globally distributed RDBMS
→ Full ACID transactions across regions
→99.999% SLA built-in (Google guarantees this)
→ TrueTime API: globally synchronised timestamps
  → Enables external consistency across regions
  → No CAP theorem compromise — CP at global scale

FS Use cases:
→ Multi-region account balance management
→ Global payment ledger
→ Cross-region inventory/limit management

Configuration for FS:
→ Multi-region instance: nam-eur-asia1
  → Replicas in US, EU, APAC automatically
→ Regional instance: eur3 (Belgium + Netherlands)
  → EU data residency ✅

Why this matters in interviews:
"Unlike traditional RDBMS that requires complex sharding
for global scale, Cloud Spanner gives us globally
consistent ACID transactions out of the box. For our
commercial savings platform, we used Spanner's eur3
configuration — Belgium + Netherlands replicas — giving
us EU data residency with 99.999% availability and
strong consistency without managing replication ourselves."
```

### Firestore vs Bigtable vs Spanner — When to Use Which

| Database | Type | FS Use Case | Key Strength |
|---|---|---|---|
| **Cloud Spanner** | Relational, global ACID | Core ledger, payments | Globally consistent transactions |
| **Cloud Bigtable** | Wide-column, NoSQL | Audit trail, time-series, fraud features | Extreme write throughput, low latency |
| **Firestore** | Document, NoSQL | Customer profiles, preferences | Real-time sync, flexible schema |
| **Cloud SQL** | Managed PostgreSQL/MySQL | Microservice DBs, simpler workloads | Familiar RDBMS, managed ops |
| **BigQuery** | Analytical warehouse | Compliance reporting, AML analytics | Petabyte-scale SQL analytics |
| **Memorystore** | Redis/Memcached managed | Caching, sessions, rate limiting | Managed Redis — no ops overhead |

---

## GCP Disaster Recovery Tiers

```
Tier 1 — Cold Standby (RTO: hours)
→ Data replicated to DR region
→ Compute not running in DR
→ On failure: provision compute, restore → hours
→ Cost: cheapest
→ FS Use: non-critical internal tools

Tier 2 — Warm Standby (RTO: minutes)
→ Data replicated continuously
→ Compute running at reduced capacity in DR
→ On failure: scale up DR compute → minutes
→ Cost: moderate
→ FS Use: batch processing, reporting systems

Tier 3 — Hot Standby / Active-Active (RTO: seconds)
→ Full stack in both regions
→ Both serving live traffic
→ On failure: traffic shifts automatically → seconds
→ Cost: most expensive
→ FS Use: payment processing, customer-facing APIs, trading

GCP Tools for each tier:
→ Cloud Storage multi-region buckets: automatic geo-redundancy
→ Cloud Spanner multi-region: automatic synchronous replication
→ GKE multi-cluster: separate clusters per region
→ Global Load Balancer: automatic failover routing
→ Cloud DNS: managed DNS with health checks
```

---

# 🔐 AREA 2 — Security & Compliance on GCP

## GCP Security Model — Defence in Depth

```
Layer 1: Google Infrastructure Security
  → Google's physical datacentres — military-grade security
  → Hardware security chips (Titan) on every server
  → Encrypted data in transit on Google's network
  → Nothing you need to configure — built-in ✅

Layer 2: Network Security (your responsibility)
  → VPC design, firewall rules, Private Google Access
  → Cloud Armor (WAF + DDoS protection)
  → VPC Service Controls (data exfiltration prevention)

Layer 3: Identity & Access (your responsibility)
  → Cloud IAM — who can do what
  → Workload Identity — how GKE pods authenticate
  → Service Accounts — machine identities

Layer 4: Data Security (your responsibility)
  → CMEK (Customer-Managed Encryption Keys)
  → Cloud KMS — key management
  → Secret Manager — secrets storage
  → DLP API — PII detection and tokenisation

Layer 5: Detection & Response (your responsibility)
  → Security Command Center — threat detection
  → Cloud Audit Logs — immutable audit trail
  → Chronicle SIEM — security analytics
```

---

## Cloud IAM — Deep Dive for FS

### IAM Hierarchy

```
Organisation (bank-holding-group.com)
    ↓
Folders (business units)
  ├── Commercial Banking
  │     ├── Project: cb-payments-prod
  │     ├── Project: cb-payments-dev
  │     └── Project: cb-accounts-prod
  ├── Retail Banking
  │     ├── Project: rb-mobile-prod
  │     └── Project: rb-statements-prod
  └── Shared Services
        ├── Project: shared-network-prod
        └── Project: shared-security-prod

FS Best Practice:
→ Separate GCP projects per environment AND per team
→ Network hub in shared project (Hub-and-Spoke model)
→ Billing separation per business unit
→ IAM policies inherited top-down — least privilege
```

### IAM Roles — Three Types

```
Primitive Roles (AVOID in FS):
→ Owner, Editor, Viewer
→ Too broad — violates least privilege
→ Never grant Editor or Owner in production ❌

Predefined Roles (USE):
→ roles/storage.objectViewer
→ roles/pubsub.publisher
→ roles/container.developer
→ Granular — specific to service and action ✅

Custom Roles (USE for specific FS requirements):
→ Create exactly the permissions needed
→ Example: payment-processor role with only
  pubsub.subscriptions.consume +
  spanner.sessions.create +
  spanner.transactions.execute
→ Nothing more — minimum viable permissions ✅
```

### Workload Identity — GKE Service Authentication

Critical for FS — how GKE pods authenticate to GCP services:

```
Without Workload Identity (BAD — never do this):
→ Mount service account key file in pod
→ Key file is long-lived, rotatable only manually
→ If pod is compromised → attacker has key forever ❌
→ Key files often accidentally committed to Git ❌

With Workload Identity (CORRECT):
→ Map Kubernetes Service Account to GCP Service Account
→ Pod gets short-lived token automatically — no key file
→ Token rotated every hour automatically
→ If pod compromised → token expires in 1 hour ✅

Configuration:
# GCP Service Account
gcloud iam service-accounts create payment-processor-sa

# Bind to Kubernetes Service Account
gcloud iam service-accounts add-iam-policy-binding \
  payment-processor-sa@project.iam.gserviceaccount.com \
  --role roles/iam.workloadIdentityUser \
  --member "serviceAccount:project.svc.id.goog[namespace/ksa]"

# GKE pod now has payment-processor-sa identity
# No key files, no manual rotation ✅
```

---

## Cloud KMS + CMEK — Encryption for FS

```
Default GCP encryption:
→ All data encrypted at rest automatically
→ Google manages keys
→ Sufficient for most workloads

CMEK (Customer-Managed Encryption Keys):
→ YOU manage the encryption keys in Cloud KMS
→ Google cannot decrypt your data without your key
→ Key rotation on your schedule
→ Key deletion = crypto shredding (GDPR!) ✅
→ Required for: PCI-DSS Level 1, highly sensitive FS data

CMEK + Cloud KMS for statement service:
→ Bigtable encrypted with CMEK key: projects/bank/locations/eur/keyRings/statements/cryptoKeys/statements-key
→ Spanner encrypted with separate CMEK key
→ Cloud Storage buckets: separate key per data classification
→ Key hierarchy:
  Key Ring: production-keys (per region — EU keys stay in EU)
    ├── Key: spanner-payments-key (rotation: 90 days)
    ├── Key: bigtable-audit-key (rotation: 90 days)
    └── Key: storage-archive-key (rotation: 1 year)

GDPR right-to-erasure via CMEK:
→ Delete customer-specific key version from Cloud KMS
→ All data encrypted with that key version → unreadable
→ Crypto shredding — data physically present but inaccessible ✅
```

---

## VPC Service Controls — Data Exfiltration Prevention

Critical for FS — often missed by candidates:

```
Problem without VPC Service Controls:
→ Compromised GKE pod can call Google APIs
→ Exfiltrate data to attacker-controlled GCS bucket
→ Even within your VPC — Google API calls go via internet ❌

VPC Service Controls solution:
→ Create a security perimeter around GCP projects
→ Only resources INSIDE the perimeter can access services
→ Access from outside perimeter = blocked even with valid IAM

Example FS configuration:
Security Perimeter: production-banking-perimeter
  Projects inside: cb-payments-prod, rb-statements-prod
  Services protected: storage.googleapis.com,
                      spanner.googleapis.com,
                      bigquery.googleapis.com

Result:
→ GKE pod in perimeter → can access Spanner ✅
→ External request trying to access Spanner → blocked ❌
→ Compromised pod trying to write to external GCS → blocked ❌
→ Data exfiltration prevented at network level ✅

FS Interview Answer:
"For our payment platform we implemented VPC Service Controls
around our production projects — even if a pod was compromised,
it couldn't exfiltrate customer data to an external bucket.
This was a mandatory control for our PCI-DSS compliance."
```

---

## Cloud Armor — WAF & DDoS Protection

```
What it does:
→ L7 WAF (Web Application Firewall)
→ DDoS protection (volumetric attacks)
→ IP allow/deny lists
→ Rate limiting per IP
→ OWASP Top 10 protection

FS Configuration:
Security Policy: banking-waf-policy

Rules:
1. Block known bad IPs (threat intelligence)
2. Rate limit: max 1000 requests/minute per IP
3. Block SQL injection patterns
4. Block XSS patterns
5. Geo-restriction: allow only UK/EU IPs for retail banking
6. Allow: Visa/Mastercard IP ranges for card scheme callbacks

Adaptive Protection:
→ ML-based — learns normal traffic patterns
→ Alerts on anomalous traffic spikes
→ Auto-suggests new rules during attacks
→ Critical for payment APIs during DDoS attempts
```

---

# 🕸️ AREA 3 — Networking Deep Dive on GCP

## VPC Architecture for FS

### Shared VPC — Hub and Spoke Model

```
Standard FS GCP Network Architecture:

Host Project (shared-network-prod):
  → Owns the VPC and subnets
  → Managed by network/security team
  → Other projects ATTACH to this VPC

Service Projects (attach to host VPC):
  → cb-payments-prod (Commercial Banking Payments)
  → rb-statements-prod (Retail Banking Statements)
  → shared-kafka-prod (Kafka cluster)

Benefits:
→ Centralised network management ✅
→ Centralized firewall rules ✅
→ Service projects can't modify network — isolation ✅
→ Cross-project communication within same VPC ✅
→ Single Cloud NAT, Cloud VPN, Interconnect ✅
```

### Subnet Design for FS

```
VPC: banking-prod-vpc (10.0.0.0/8)

Subnets:
  payments-subnet-london    10.1.0.0/20  (europe-west2)
  payments-subnet-belgium   10.2.0.0/20  (europe-west1)
  kafka-subnet-london       10.3.0.0/20  (europe-west2)
  kafka-subnet-belgium      10.4.0.0/20  (europe-west1)
  data-subnet-london        10.5.0.0/20  (europe-west2)
  mgmt-subnet-london        10.6.0.0/20  (europe-west2)

Secondary IP ranges (for GKE pods):
  payments-pods-london      172.16.0.0/14
  payments-services-london  172.20.0.0/20

Firewall rules:
→ payments → kafka: ALLOW TCP 9092
→ payments → spanner: ALLOW via Private Google Access
→ internet → payments: DENY (internal LB only)
→ mgmt → all: ALLOW SSH (IAP tunnel only — no public IP)
```

### Private Google Access — No Public IPs

```
Problem: GKE pods need to call Google APIs (Spanner, GCS, KMS)
Naive solution: Give pods public IPs → security risk ❌

Private Google Access:
→ Enable on subnet: gcloud compute networks subnets update
  payments-subnet-london --enable-private-ip-google-access
→ Pods call Google APIs via internal Google network
→ No public IP needed on pods ✅
→ Traffic never leaves Google's network ✅
→ PCI-DSS: cardholder data never on public internet ✅

Private Service Connect (newer, preferred):
→ More granular — per-service private endpoints
→ Expose specific Google services inside your VPC
→ No shared IP ranges — cleaner network design
```

### Cloud Interconnect — On-Premise to GCP

Critical for banks — connecting to on-premise core banking:

```
Options:

1. Cloud VPN (up to 3Gbps):
   → Encrypted tunnel over internet
   → Lower cost
   → Use for: non-critical connectivity, dev/test
   → FS: connecting to on-prem monitoring, non-sensitive tools

2. Dedicated Interconnect (10Gbps or 100Gbps):
   → Physical connection at colocation facility
   → Not over internet — private connectivity
   → SLA: 99.99%
   → Use for: core banking integration, high-volume data
   → FS: CBS adapter connecting to on-prem COBOL system

3. Partner Interconnect (50Mbps - 10Gbps):
   → Via network service provider
   → More flexible locations than Dedicated
   → SLA: 99.9% or 99.99%
   → Use for: medium-volume, locations without Dedicated PoP

FS Architecture:
On-prem Core Banking (DB2) 
    ↓
Dedicated Interconnect (10Gbps)
    ↓
Cloud Router (BGP routing)
    ↓
Shared VPC — banking-prod-vpc
    ↓
CBS Adapter GKE pods → call on-prem CBS APIs
→ Traffic never on public internet ✅
→ Sub-5ms latency to CBS ✅
```

---

# ☸️ AREA 4 — GKE Deep Dive

## GKE Cluster Architecture for FS

### Cluster Types

```
Standard Cluster (full control):
→ You manage node pools, scaling, upgrades
→ Choose machine types, disk sizes
→ Full kubectl access
→ FS use: production workloads requiring fine-grained control

Autopilot Cluster (managed):
→ Google manages nodes — you manage pods only
→ Pay per pod resources — not per node
→ Automatic security hardening
→ FS use: dev/test, smaller workloads, teams without K8s expertise

Regional Cluster (ALWAYS use in FS):
→ Control plane replicated across 3 zones
→ Node pools span 3 zones
→ Zone failure → other 2 zones serve traffic
→ 99.99% control plane SLA ✅

Zonal Cluster (AVOID in FS):
→ Single zone control plane
→ Zone failure = entire cluster down ❌
→ Only for dev/test
```

### GKE Node Pool Design for FS

```
Production GKE Cluster: payments-cluster-london

Node Pools:

1. system-pool (always-on):
   Machine: e2-standard-4 (4 vCPU, 16GB)
   Nodes: 3 (one per zone)
   Purpose: system pods, monitoring agents
   Taints: none

2. payments-pool (auto-scaling):
   Machine: c2-standard-8 (8 vCPU, 32GB) — compute optimised
   Nodes: 3 min, 30 max (HPA + cluster autoscaler)
   Purpose: payment microservices
   Taints: workload=payments:NoSchedule
   → Only payment pods scheduled here ✅

3. kafka-consumer-pool (auto-scaling):
   Machine: n2-highmem-8 (8 vCPU, 64GB) — memory optimised
   Nodes: 5 min, 50 max
   Purpose: Kafka consumer pods
   Taints: workload=kafka:NoSchedule

4. batch-pool (spot/preemptible — cost saving):
   Machine: n2-standard-4
   Nodes: 0 min, 20 max
   Purpose: batch jobs, archival processes
   Spot instances: 60-90% cost saving ✅
   → Acceptable for non-critical batch workloads
```

### GKE Security Hardening for FS

```
1. Private Cluster:
   → Node IPs are private — not reachable from internet
   → Control plane accessible only via authorised networks
   → All GKE recommended for FS production

2. Workload Identity (covered above):
   → No service account key files in pods ✅

3. Binary Authorization:
   → Only pre-approved container images can run
   → Policy: images must be signed by CI/CD pipeline
   → Prevents unauthorised or malicious images ✅
   → FS compliance: full audit trail of what runs in prod

4. Pod Security:
   → securityContext: runAsNonRoot: true
   → No privileged containers in FS production
   → readOnlyRootFilesystem: true
   → Drop all Linux capabilities: drop: [ALL]

5. Network Policies:
   → Default deny all ingress/egress
   → Explicit allow rules per service
   → payments-pod → kafka: ALLOW
   → payments-pod → spanner: ALLOW
   → payments-pod → internet: DENY ✅

6. GKE Autopilot security:
   → Automatically enforces above hardening
   → Nodes are ephemeral — no SSH access
   → Workload Identity mandatory
```

### GKE Autoscaling — Three Layers

```
Layer 1 — HPA (Horizontal Pod Autoscaler):
Scales PODS based on metrics:
→ CPU utilisation > 70% → add pods
→ Custom metric: Kafka consumer lag > 50K → add consumers
→ Pub/Sub subscription size → add processors

Layer 2 — VPA (Vertical Pod Autoscaler):
Adjusts CPU/memory REQUESTS per pod:
→ Analyses actual usage over time
→ Recommends right-sizing
→ FS use: right-size pod resources to reduce waste
→ Don't use in update mode for stateful workloads

Layer 3 — Cluster Autoscaler:
Scales NODES based on pod scheduling demand:
→ Pods can't schedule (insufficient resources) → add nodes
→ Nodes underutilised for 10+ minutes → remove nodes
→ Respects PodDisruptionBudget — won't remove nodes
  if it would violate PDB

FS autoscaling example:
Normal: 10 consumer pods, 3 nodes
Market open spike:
→ Kafka lag grows → HPA adds pods
→ Pods can't schedule → Cluster Autoscaler adds nodes
→ New nodes ready: ~90 seconds
→ Consumers scaling: ~30 seconds
→ Total scale-out: ~2 minutes
→ Too slow? → Pre-scale with scheduled scaling:
  7:45am every weekday → scale to 30 pods pre-emptively
```

---

# 💰 AREA 5 — Cost Optimisation on GCP

## EM-Level Cost Thinking

At EM level, cost questions test **commercial awareness** — not just technical:

> *"As an EM I'm accountable for my team's cloud spend. At Lloyds we had a £2M annual GCP budget. I implemented four cost reduction initiatives that saved £480K annually without impacting performance or reliability."*

---

## The 5 GCP Cost Optimisation Levers

### Lever 1 — Committed Use Discounts (CUDs)

```
On-demand pricing: pay full price per hour
CUD 1-year: 37% discount on compute
CUD 3-year: 55% discount on compute

FS Strategy:
→ Baseline workload (always running): commit 1 or 3 years
→ Spiky workload (market hours): on-demand or spot

Example:
Payments cluster baseline: 20 nodes always running
→ Commit 20 × n2-standard-8 for 1 year
→ Save 37% = significant annual saving

Rule: Never commit 100% of capacity
→ Commit 70-80% (baseline)
→ Leave 20-30% on-demand for spikes
```

### Lever 2 — Spot VMs (Preemptible)

```
Spot VMs: up to 90% cheaper than on-demand
Catch: can be preempted (terminated) with 30s notice

FS Use cases (acceptable preemption):
→ Kafka archival consumers (replay from offset on restart)
→ Batch reconciliation jobs
→ ML model training (checkpoint regularly)
→ Dev/test environments

FS Use cases (NEVER use spot):
→ Payment processing pods ❌
→ Core API servers ❌
→ Database nodes ❌
→ Any stateful production workload ❌

GKE implementation:
→ Separate spot node pool for batch workloads
→ Taints to ensure only appropriate pods schedule on spot
→ PodDisruptionBudget: ensures minimum pods always running
→ Graceful shutdown: 30s SIGTERM handler saves state to Kafka
```

### Lever 3 — Cloud Storage Classes

```
Storage class selection is pure cost optimisation:

Standard:    £0.020/GB/month — hot data, frequent access
Nearline:    £0.010/GB/month — access < 1x/month
Coldline:    £0.004/GB/month — access < 1x/quarter
Archive:     £0.0012/GB/month — access < 1x/year

FS Strategy for 7-year statement archive:
Year 0-1:    Standard (frequent regulatory queries)
Year 1-3:    Nearline (occasional audit requests)
Year 3-7:    Coldline/Archive (rare — only for legal/regulatory)

Object Lifecycle Management (automates transition):
{
  "rule": [
    {"action": {"type": "SetStorageClass",
                "storageClass": "NEARLINE"},
     "condition": {"age": 365}},
    {"action": {"type": "SetStorageClass",
                "storageClass": "COLDLINE"},
     "condition": {"age": 1095}},
    {"action": {"type": "SetStorageClass",
                "storageClass": "ARCHIVE"},
     "condition": {"age": 1825}}
  ]
}
→ Automatic — zero operational overhead
→ Estimated saving: 94% vs keeping everything in Standard
```

### Lever 4 — Right-Sizing

```
Most common waste in FS GCP:
→ Pods with 4GB memory request, using 800MB actual
→ Nodes provisioned for peak, idle 90% of time
→ Dev environments running 24/7

Tools:
→ GCP Recommender: automated right-sizing suggestions
→ VPA in recommendation mode: suggests CPU/memory adjustments
→ Cloud Monitoring: actual usage vs requested resources

EM Process:
→ Monthly cost review with tech leads
→ VPA recommendations reviewed quarterly
→ Dev environments: auto-shutdown at 7pm, auto-start 8am
   Saving: 65% on dev compute costs
→ Staging: scale to zero on weekends
   Saving: 28% on staging costs
```

### Lever 5 — Network Cost Optimisation

```
GCP Egress pricing:
→ Data leaving GCP to internet: charged
→ Data between regions: charged
→ Data within same region: FREE
→ Data within same zone: FREE

FS Optimisation:
→ Collocate services that communicate frequently
  in same zone → zero inter-zone charges
→ Cache at edge (Cloud CDN) → reduce origin egress
→ Compress API responses → reduce egress volume
→ Batch small API calls → reduce request overhead

Cloud CDN for statement API:
→ Public static responses cached at edge
→ Reduces origin traffic by 60-70%
→ Lower latency for customers + lower egress cost ✅
```

---

## GCP Cost Governance for FS

```
Budgets & Alerts:
→ Budget per project per month
→ Alert at 50%, 90%, 100% of budget
→ Auto-action at 100%: notify EM + send Slack alert

Labels for cost allocation:
→ Every resource labelled:
  env=prod, team=payments, cost-centre=commercial-banking
→ BigQuery billing export → Looker dashboard
→ Weekly cost report per team
→ Anomaly detection: spend > 20% above 7-day average → alert

FinOps process (EM responsibility):
→ Weekly: review cost anomalies
→ Monthly: right-sizing review with tech leads
→ Quarterly: CUD renewal decisions
→ Annually: architecture review for major cost opportunities
```

---

## ✅ Cloud Prep — Quick Revision Checklist

### Multi-Region
- [ ] Can explain GCP global load balancer vs AWS Route 53 difference
- [ ] Can design active-active multi-region on GCP for FS
- [ ] Know FS-relevant GCP regions (London, Belgium, Frankfurt)
- [ ] Can explain Cloud Spanner multi-region for global ACID
- [ ] Know DR tiers: cold/warm/hot standby with RTO

### Security
- [ ] Can explain IAM hierarchy: Org → Folder → Project
- [ ] Know difference: primitive vs predefined vs custom roles
- [ ] Can explain Workload Identity — why no key files
- [ ] Can explain CMEK + Cloud KMS for GDPR crypto shredding
- [ ] Can explain VPC Service Controls — data exfiltration prevention
- [ ] Know Cloud Armor for WAF + DDoS in FS context

### Networking
- [ ] Can explain Shared VPC hub-and-spoke model
- [ ] Can design subnet architecture for FS
- [ ] Know Private Google Access + Private Service Connect
- [ ] Know Dedicated Interconnect for on-prem CBS connectivity
- [ ] Can explain firewall rules default-deny approach

### GKE
- [ ] Know regional vs zonal clusters — always regional in FS
- [ ] Can design node pool architecture (system/workload/batch)
- [ ] Know three autoscaling layers: HPA + VPA + Cluster Autoscaler
- [ ] Know GKE security hardening: private cluster, binary auth, network policies
- [ ] Know Workload Identity for pod authentication

### Cost Optimisation
- [ ] Know 5 cost levers: CUDs, Spot, Storage classes, Right-sizing, Network
- [ ] Know when NOT to use Spot VMs in FS
- [ ] Can explain storage lifecycle management for 7-year archive
- [ ] Know FinOps process for EM-level cost governance

---
Perfect! 3 questions, one at a time, progressively harder — GCP focused with FS context! 💪

---

# 🎤 GCP Cloud Drill — Question 1 of 3

### "You are the Engineering Manager for a **Retail Banking Platform** at a UK bank running entirely on GCP. The platform serves 8 million UK customers via a mobile app and web portal.

**Current architecture:**
- Single GCP region: europe-west2 (London)
- GKE zonal cluster (single zone: europe-west2-a)
- Cloud SQL PostgreSQL (single zone, no replica)
- All services running with default service accounts
- Pods communicating with Cloud Spanner and GCS using service account key files mounted as Kubernetes secrets
- No VPC Service Controls configured
- Cloud Armor not enabled
- All node pools using on-demand pricing

**The CTO raises 5 concerns in an architecture review:**

1. *"We had a zone outage last month — europe-west2-a went down for 45 minutes. Our entire platform was down."*
2. *"Our security team found a service account key file in a developer's laptop — apparently copied from a pod. This is a serious breach risk."*
3. *"A penetration test found that a compromised pod could exfiltrate customer data to an external GCS bucket."*
4. *"We're spending £180K/month on GCP. Finance is asking us to reduce by 20% without impacting production reliability."*
5. *"We have no WAF protection — our payment APIs are directly exposed to internet traffic."*

**For each concern — identify the root cause and provide the specific GCP solution with enough technical detail to satisfy a Principal Engineer in the room."**

---

> 💡 *Tip 1: Concern 1 — think about what "zonal cluster" means and what the right GKE cluster type is for production FS*

> 💡 *Tip 2: Concern 2 — we covered exactly this pattern and why it's dangerous. What's the GCP-native fix?*

> 💡 *Tip 3: Concern 3 — there's a specific GCP security feature designed exactly for this data exfiltration scenario*

> 💡 *Tip 4: Concern 4 — think about the 5 cost levers we covered. Which ones apply here without impacting production reliability?*

> 💡 *Tip 5: Concern 5 — which GCP service sits in front of your load balancer and provides WAF + DDoS protection?*


Outstanding answer! You covered all 5 concerns with strong technical depth and real EM thinking! Here's your detailed feedback 👇

---

## ✅ Feedback — Question 1

### What you got right
- ✅ **Concern 1** — Regional GKE cluster correctly identified
- ✅ **Control plane replication across 3 zones** — technically precise
- ✅ **Pod rescheduling on zone failure** — correct instinct
- ✅ **Concern 2** — Workload Identity correctly identified
- ✅ **KSA → GCP IAM SA mapping** — technically precise
- ✅ **No key files, reduced blast radius** — correct reasoning
- ✅ **Concern 3** — VPC Service Controls correctly identified
- ✅ **Service perimeter concept** — correct
- ✅ **Fine-grained ingress/egress policies** — shows depth
- ✅ **Concern 5** — Cloud Armor correctly identified
- ✅ **OWASP, DDoS, rate limiting, IP lists** — complete
- ✅ **Cost increases before decreases** — excellent EM honesty
- ✅ **CUD 60-70% baseline** — correct strategy
- ✅ **No Spot for critical services** — correct FS judgment
- ✅ **Co-locate resources to reduce egress** — good cost thinking

### Your Score: **8/10** — Strong across all 5! Here's the push to 9/10 💡

---

### What to Strengthen

**Concern 1 — Node rescheduling uncertainty — clarify this:**

You said *"not sure if nodes on down zone would be rescheduled on active zone"* — let me clarify and you should state this confidently:

> *"When a zone goes down, the nodes in that zone become NotReady. Kubernetes controller detects pods on NotReady nodes after pod-eviction-timeout (default 5 minutes — configurable to 30 seconds in GKE). Pods are then rescheduled on healthy nodes in other zones. Key requirement: node pools must span all 3 zones AND have capacity headroom — if other zones are already at max capacity, pods can't reschedule. Solution: set PodDisruptionBudget + configure cluster autoscaler to maintain N+1 node capacity per zone so zone failure never hits a capacity wall."*

**Also missing — Cloud SQL:**
> *"Cloud SQL single-zone is a single point of failure — zone outage takes down the DB even if GKE recovers. Fix: Cloud SQL High Availability configuration — primary in zone-a, standby replica in zone-b, automatic failover in ~60 seconds. Or migrate to Cloud Spanner which is multi-zonal by default with no failover needed."*

---

**Concern 2 — Add one more critical point:**

> *"Beyond Workload Identity, I'd also audit all existing service account keys immediately — revoke any keys not actively needed, rotate all remaining keys, and enable org policy constraint `iam.disableServiceAccountKeyCreation` to prevent future key creation. This prevents the same breach from recurring while Workload Identity migration is in progress."*

---

**Concern 3 — Add the Access Context Manager piece:**

> *"VPC Service Controls alone defines the perimeter — but we also need Access Context Manager to define who can access from outside the perimeter. For example: security team accessing prod for incident response from corporate network should be allowed via an access level — without this, even legitimate access is blocked. Access levels define trusted IP ranges, device policies, identity conditions."*

---

**Concern 4 — Missing 2 cost levers:**

You correctly identified CUDs and egress co-location. Two more significant levers:

> *"Third lever — Storage class optimisation: audit GCS buckets — any data older than 30 days on Standard storage should move to Nearline (50% cheaper). Audit logs, old backups likely sitting on Standard unnecessarily. Implement lifecycle policies immediately — zero risk, pure saving.*
>
> *Fourth lever — Right-sizing: use GCP Recommender to identify over-provisioned instances. FS teams typically over-provision by 40-50% out of caution. VPA in recommendation mode shows actual vs requested CPU/memory. Dev and staging environments — auto-shutdown at 7pm, auto-start 8am → 65% saving on non-prod compute.*
>
> *Combined these four levers realistically achieve 30-35% saving — exceeding the 20% target while regional cluster + Cloud Armor costs are absorbed."*

---

## 🏆 Model Answer Summary (9/10)

> **Concern 1:**
> *"Migrate to regional GKE cluster — control plane across 3 zones, node pools spanning all 3 zones. Zone failure → pods rescheduled on healthy zones in ~30 seconds (configure pod-eviction-timeout). Ensure cluster autoscaler maintains N+1 capacity per zone. Fix Cloud SQL: enable HA configuration with automatic failover. Or migrate to Cloud Spanner — multi-zonal by default."*

> **Concern 2:**
> *"Enable Workload Identity — map KSA to GCP IAM SA. No key files, automatic token rotation hourly, minimum privilege per pod. Immediate action: audit all existing keys, revoke unnecessary ones, enable org policy iam.disableServiceAccountKeyCreation to prevent recurrence."*

> **Concern 3:**
> *"Implement VPC Service Controls — create service perimeter around production projects. Define fine-grained ingress/egress policies — whitelist specific APIs, service accounts, projects. Add Access Context Manager for legitimate exception access (security team, incident response). Data exfiltration to external GCS now blocked at network level — even with valid credentials."*

> **Concern 4:**
> *"Four levers: CUDs on 60-70% baseline compute (37% saving on committed portion). Co-locate resources in same region/zone — eliminate inter-region egress. Storage lifecycle policies — move data > 30 days to Nearline/Coldline. Right-size dev/staging — auto-shutdown evenings and weekends. Combined: 30-35% saving, absorbing regional cluster + Cloud Armor cost increase."*

> **Concern 5:**
> *"Enable Cloud Armor security policy on Global External HTTPS LB. Configure: OWASP Top 10 preconfigured rules, rate limiting (1000 req/min per IP), geo-restriction (UK/EU only for retail banking), IP deny list for known threat actors, Adaptive Protection for ML-based anomaly detection."*

---

# 🎤 GCP Cloud Drill — Question 2 of 3

### "You are the Engineering Manager for a **Commercial Banking Platform** at Lloyds running on GCP. Your platform processes corporate payments for business customers — large enterprises, SMEs and institutional clients.

**Your current network architecture:**
- Single VPC: `banking-prod-vpc`
- All teams (payments, accounts, fraud, notifications) deploy into the same VPC with no subnet isolation
- All microservices communicate freely with each other — no network policies
- On-premise Core Banking System (CBS) connected via Cloud VPN over internet
- GKE pods access Cloud Spanner and GCS via public internet endpoints
- Developers SSH directly into GKE nodes using public IPs for debugging
- All environments (prod, staging, dev) share the same VPC

**Three incidents and one business requirement trigger an urgent architecture review:**

**Incident 1:**
A compromised notification service pod was able to make direct API calls to the payment processing service and attempt fraudulent payment injections. The blast radius could have been catastrophic.

**Incident 2:**
The Cloud VPN to on-premise CBS dropped twice last month during peak hours — each time causing 8-12 minute payment processing outages. The VPN uses a shared internet connection.

**Incident 3:**
A developer accidentally SSH'd into a production GKE node, ran a debug script that caused a production node to crash, taking down 30% of payment processing capacity for 20 minutes.

**Business Requirement:**
The bank is acquiring a fintech company. Their GCP infrastructure needs to be connected to the banking platform's VPC so they can share certain internal APIs — but the fintech must NOT be able to access customer PII data or core banking services.

**Four things to answer:**

1. **Network Segmentation** — how do you redesign the VPC architecture to prevent Incident 1 from ever happening again?
2. **CBS Connectivity** — how do you replace the unreliable Cloud VPN with enterprise-grade connectivity for on-premise CBS integration?
3. **Production Access** — how do you eliminate direct SSH access to production nodes while still allowing developers to debug production issues?
4. **Fintech Acquisition** — how do you connect the fintech GCP infrastructure to share specific APIs without exposing PII or core banking services?"**

---

> 💡 *Tip 1: Incident 1 — two layers needed: subnet isolation AND Kubernetes network policies. Think about default-deny*

> 💡 *Tip 2: Incident 2 — Cloud VPN over internet is unreliable and not enterprise grade. What GCP connectivity option gives you dedicated physical connectivity with an SLA?*

> 💡 *Tip 3: Incident 3 — developers should NEVER SSH directly to production nodes. GCP has a specific service for secure, audited, zero-public-IP access*

> 💡 *Tip 4: Fintech acquisition — connecting two separate GCP organisations/projects. VPC Peering vs Shared VPC vs something more controlled. Think about the constraint: share some APIs but block PII access*

Take your shot! 🎯

> 🔑 **Coaching note:** Your biggest gap was uncertainty on pod rescheduling — state it confidently with the eviction timeout detail. The other gaps were additive details (Cloud SQL HA, Access Context Manager, storage lifecycle) that push a good answer to exceptional. The cost thinking was strong — proactively saying "regional cluster + Armor will increase costs first" shows EM commercial maturity that most candidates miss.

---

Exceptional answer! This is genuinely Principal/Director level network architecture thinking! Here's your detailed feedback 👇

---

## ✅ Feedback — Question 2

### What you got right
- ✅ **Multi-VPC + project/folder isolation** — correct long-term target
- ✅ **Subnet isolation as minimum** — pragmatic short-term fix
- ✅ **Cloud VPN limitations** — bandwidth + public internet correctly identified
- ✅ **Dedicated Interconnect** — correct enterprise-grade solution
- ✅ **10Gbps pipes, additive** — technically precise
- ✅ **Regulatory concern on internet traffic** — excellent FS instinct
- ✅ **Dedicated node pools per workload** — correct blast radius isolation
- ✅ **Namespace isolation** — correct Kubernetes segregation
- ✅ **Fine-grained network policies** — ingress/egress per workload
- ✅ **Private nodes + Private Google Access** — correct, no public IPs
- ✅ **VPC Service Controls** — data exfiltration prevention
- ✅ **Private GKE cluster** — master not publicly accessible
- ✅ **Authorised networks** — corporate network whitelist
- ✅ **JIT access process** — auditability — excellent EM thinking
- ✅ **PSC for fintech connectivity** — correct, elegant solution
- ✅ **Dedicated VPC for shared APIs** — correct isolation approach
- ✅ **Defence in depth** — firewall + Istio egress gateway + no OAuth creds — outstanding

### Your Score: **8.5/10** — Outstanding! Here's the push to 9/10 💡

---

### What to Strengthen

**Incident 3 — SSH access — you missed the specific GCP solution**

You correctly described private cluster + authorised networks — but didn't name the **specific GCP tool** that replaces SSH entirely:

> *"The specific GCP solution is **Identity-Aware Proxy (IAP) Tunnel** — developers never SSH directly to nodes. Instead:*
>
> *1. IAP Tunnel: developer authenticates via corporate SSO → IAP validates identity + device posture → creates encrypted tunnel to private node → no public IP needed, no firewall rule opening needed*
>
> *2. For production debugging specifically: never SSH to nodes — use `kubectl exec` into pods via private cluster endpoint, or use **Cloud Shell** which has access to authorised networks*
>
> *3. JIT access via **Privileged Access Manager (PAM)**: developer requests temporary elevated access → manager approves → access granted for 2 hours → full audit trail in Cloud Audit Logs → access automatically revoked*
>
> *4. Break-glass procedure: for true emergencies — IAP + PAM + dual approval + automatic incident ticket creation. Every keystroke logged via Cloud Audit Logs → immutable, tamper-proof audit trail for FCA."*

---

**CBS Connectivity — add redundancy detail**

You correctly chose Dedicated Interconnect — add the HA configuration:

> *"Single Dedicated Interconnect = single point of failure — same problem as single VPN. For enterprise FS:*
> ```
> HA Interconnect configuration:
> Metro: London (Equinix LD8)
>   → Interconnect 1: 10Gbps (primary)
>   → Interconnect 2: 10Gbps (secondary — same metro, different device)
>
> Metro: London (Telehouse North) — different facility
>   → Interconnect 3: 10Gbps (tertiary — different physical location)
>
> Cloud Router: BGP routing with failover
> → Primary path: Interconnect 1
> → Automatic failover: Interconnect 2 (same facility, <1s)
> → DR failover: Interconnect 3 (different facility, <5s)
> ```
> *This gives 99.99% SLA on the interconnect itself. Combined with Cloud Router BGP, failover is automatic — no manual intervention needed."*

---

**Network Policies — add default-deny explicitly**

> *"The critical missing piece in current architecture is default-allow. Kubernetes default is allow-all — any pod can talk to any pod. First action: implement **default-deny NetworkPolicy** in every namespace:*
> ```yaml
> apiVersion: networking.k8s.io/v1
> kind: NetworkPolicy
> metadata:
>   name: default-deny-all
>   namespace: payments
> spec:
>   podSelector: {}      # applies to ALL pods
>   policyTypes:
>   - Ingress
>   - Egress
> # No rules = deny everything
> ```
> *Then add explicit allow rules per service pair. Payments → Spanner: ALLOW. Notification → Payments: DENY. This is why Incident 1 happened — notification pod could reach payment service freely."*

---

**PSC for fintech — add one important detail**

> *"Private Service Connect is the right choice — but importantly, PSC is **unidirectional**. The fintech can call your exposed API endpoint via PSC — but cannot initiate connections to any other service in your VPC. This is fundamentally more secure than VPC Peering which gives bidirectional network access. Combined with the dedicated proxy VPC — fintech gets exactly what they need, nothing more."*

---

**Missing — VPC architecture diagram clarity**

Your answer correctly described the architecture but an interviewer would want to hear the target state explicitly:

> *"Target architecture — four layers:*
>
> *Layer 1 — Folder structure:*
> ```
> Organisation: lloyds-bank.com
>   Folder: Production
>     Project: shared-network-prod (VPC host)
>     Project: payments-prod
>     Project: fraud-prod
>     Project: notifications-prod
>     Project: fintech-bridge-prod (dedicated for PSC)
>   Folder: Non-Production
>     Project: payments-staging
>     Project: payments-dev
> ```
>
> *Layer 2 — Shared VPC with subnet isolation:*
> ```
> Host VPC: banking-prod-vpc
>   payments-subnet:      10.1.0.0/20 (europe-west2)
>   fraud-subnet:         10.2.0.0/20 (europe-west2)
>   notifications-subnet: 10.3.0.0/20 (europe-west2)
>   fintech-bridge-subnet:10.4.0.0/20 (europe-west2)
> ```
>
> *Layer 3 — GKE: separate node pools + namespaces + NetworkPolicies per domain*
>
> *Layer 4 — VPC Service Controls: service perimeter around all production projects*"*

---

## 🏆 Model Answer Summary (9/10)

> **Network Segmentation:**
> *"Short term: dedicated subnets per domain + default-deny NetworkPolicies in every namespace + explicit allow rules per service pair. Notification → Payment: DENY. Long term: separate GCP projects per domain in Shared VPC host model — folder-level isolation. VPC Service Controls perimeter around all production projects. Private nodes + Private Google Access — no public IPs anywhere."*

> **CBS Connectivity:**
> *"Replace Cloud VPN with Dedicated Interconnect — HA configuration: 2 interconnects at Equinix London + 1 at Telehouse (different facility). Cloud Router BGP for automatic failover. 99.99% SLA. Payment data never touches public internet — travels Google backbone + private Interconnect. Meets FCA and PCI-DSS requirements."*

> **Production Access:**
> *"Eliminate SSH entirely. Private GKE cluster — no public node IPs. IAP Tunnel for any required node access — identity verified, device posture checked, full audit log. Privileged Access Manager (PAM) for JIT elevated access — manager approval, 2-hour window, automatic revocation, immutable audit trail. kubectl exec via private endpoint for pod debugging. Break-glass: dual approval + automatic incident ticket."*

> **Fintech Connectivity:**
> *"Dedicated proxy VPC in fintech-bridge-prod project. Only whitelisted APIs exposed — no core banking, no PII services. PSC publishes proxy VPC endpoint to fintech — unidirectional, fintech cannot traverse to other services. Defence in depth: firewall rules + Istio egress gateway + service entries + no OAuth credentials to core services. VPC Service Controls excludes fintech-bridge project from core banking perimeter."*

---

> 🔑 **Coaching note:** Two things to add to your vocabulary — **IAP Tunnel** (replaces SSH, always name it explicitly) and **PSC unidirectionality** (stronger than VPC Peering, explain why). Your defence-in-depth thinking for the fintech scenario — firewall + Istio egress + no OAuth creds — is genuinely exceptional and most candidates would never think of three layers. That's what makes you stand out.

# 🎤 GCP Cloud Drill — Question 3 of 3 🔥

### The hardest GCP question — combining multi-region, security, cost and FS compliance in one real scenario!

---

### "You are the Engineering Manager at a **tier-1 investment bank** running a **Global Trade Processing Platform** on GCP. The platform processes equity trades, FX trades and bond settlements for institutional clients across three jurisdictions — **UK, EU and Singapore**.

**Current state:**
- Single GCP region: europe-west2 (London)
- Processing £50 billion in trades daily
- 15 microservices on GKE
- Cloud Spanner for trade ledger
- Pub/Sub for trade event streaming
- BigQuery for regulatory reporting
- GCP spend: £420K/month

**The board has approved a 6-month transformation programme with four mandates:**

**Mandate 1 — Global Expansion:**
Singapore institutional clients are experiencing **380ms API latency** — they are threatening to move to a competitor. The board wants Singapore latency under **50ms**. Singapore trade data must never leave Singapore (MAS regulatory requirement — Monetary Authority of Singapore)

**Mandate 2 — Regulatory Compliance:**
The FCA has issued a finding — the bank's **BigQuery regulatory reporting datasets contain unencrypted PII** (trader names, client identifiers). Must be remediated within **60 days**. Additionally all encryption keys must be **customer-managed** — Google must not be able to decrypt any banking data.

**Mandate 3 — Resilience:**
Last quarter there were **3 incidents** totalling **4.2 hours of downtime** — all caused by single-region dependency. The board mandates **99.99% availability** — maximum 52 minutes downtime per year — across all three jurisdictions independently.

**Mandate 4 — Cost Reduction:**
The CFO demands **25% reduction** in GCP spend (£105K/month saving) within 6 months — without reducing production reliability or expanding headcount.

**You have 6 months and a team of 30 engineers.**

**Five things to answer:**

1. **Global Architecture** — design the multi-region GCP architecture across UK, EU and Singapore. How does routing work? How does a UK trader working late from Singapore still access UK trade data?
2. **Singapore Latency** — how do you get Singapore clients from 380ms to under 50ms? Be specific about which GCP services and configuration achieves this.
3. **BigQuery PII Remediation** — how do you fix the unencrypted PII finding within 60 days? What is your technical approach and your 60-day delivery plan?
4. **99.99% Resilience** — how do you achieve 99.99% within each jurisdiction independently? Walk through your Cloud Spanner, GKE and Pub/Sub resilience design.
5. **Cost Reduction** — identify specific levers to achieve £105K/month saving. Be specific about which services, what changes, and realistic saving estimates."**

---

> 💡 *Tip 1: Singapore latency — 380ms is London → Singapore round trip. You need compute IN Singapore. But MAS says data stays in Singapore. Think about what you run in Singapore vs what stays in London*

> 💡 *Tip 2: UK trader in Singapore — same problem as London customer in Frankfurt from our Kafka session. Route by identity jurisdiction, not IP*

> 💡 *Tip 3: BigQuery PII — two problems: existing unencrypted data AND future prevention. Cloud DLP for discovery and de-identification. CMEK for encryption. What's the 60-day sequencing?*

> 💡 *Tip 4: 99.99% per jurisdiction independently — Cloud Spanner has a built-in answer here. GKE regional cluster. Pub/Sub is already global — but think about message ordering and regional topics*

> 💡 *Tip 5: Cost — £420K/month is high. Think CUDs, BigQuery slot commitments, storage lifecycle, right-sizing. What's the biggest single lever at this spend level?*

---

This is a **genuine MD/Director level interview question** — 25 minutes in a real interview. Take your time, think through all 5 parts! 💪🎯


Ha! Fair enough — you've been at this for hours, hands deserve a rest! 😄

Here's the complete model answer for learning:

---

# 🏆 Global Trade Processing Platform — Complete Model Answer

---

## 1️⃣ Global Architecture

### Multi-Region Design

```
Three independent jurisdiction stacks:

UK Stack:
  Primary:   europe-west2 (London) — 3 AZs
  Secondary: europe-west1 (Belgium) — active-active
  Data residency: UK trade data never leaves UK/Belgium

EU Stack:
  Primary:   europe-west3 (Frankfurt) — 3 AZs
  Secondary: europe-west4 (Netherlands) — active-active
  Data residency: EU trade data stays in EU only

Singapore Stack:
  Primary:   asia-southeast1 (Singapore) — 3 AZs
  Secondary: asia-southeast2 (Jakarta) — active-active
  Data residency: MAS requirement — Singapore data never leaves
                  Singapore jurisdiction ✅
```

### Global Routing Architecture

```
Global Load Balancer (GCP Anycast IP):
→ Single global IP — traders worldwide connect here
→ Traffic enters Google network at nearest PoP
→ Routed internally on Google backbone
→ No DNS TTL delays — failover in seconds

Routing logic:
Authenticated requests:
  → Read JWT: { traderId: "T-UK-001", jurisdiction: "UK" }
  → Route to UK stack regardless of physical location
  → UK trader in Singapore → UK stack via Google backbone
  → +15ms latency vs Singapore stack — acceptable for UK trader

Unauthenticated (login):
  → Route by IP geolocation to nearest stack
  → Singapore IP → Singapore login service
  → After auth: JWT jurisdiction claim takes over
```

### UK Trader Working Late from Singapore

```
Trader authenticates from Singapore office:
  → Hits Singapore login endpoint (nearest)
  → Authenticates → receives JWT { jurisdiction: "UK" }

Subsequent trade requests:
  → JWT jurisdiction = UK
  → Global LB routes to UK stack (London/Belgium)
  → Traffic: Singapore → Google PoP Singapore →
    Google backbone → London
  → Latency: ~150ms (acceptable for authenticated trader)
  → UK trade data never leaves UK ✅
  → MAS satisfied: Singapore data not involved ✅
  → FCA satisfied: UK data stays in UK ✅
```

---

## 2️⃣ Singapore Latency — 380ms → Under 50ms

### Root Cause of 380ms

```
Current: Singapore client → public internet → London
  → Public internet routing: unpredictable, slow
  → Round trip London ↔ Singapore: ~200ms base latency
  → Application processing in London: ~180ms
  → Total: ~380ms ❌
```

### Solution — Compute in Singapore + Google Backbone

```
Step 1: Deploy Singapore GKE cluster (asia-southeast1)
  → Singapore clients hit Singapore GKE directly
  → No London round trip for Singapore trades ✅

Step 2: Singapore-specific services deployed locally
  → Trade Order API: Singapore GKE
  → Trade Validation: Singapore GKE
  → Real-time price feeds: Singapore (local market data)
  → Singapore Pub/Sub topics: asia-southeast1

Step 3: Cloud Spanner Singapore instance
  → MAS requirement: Singapore trade data in Singapore
  → Spanner regional instance: asia-southeast1
  → Singapore trades written to Singapore Spanner ✅
  → Never replicated to London (MAS compliance) ✅

Step 4: Cloud CDN + Global LB
  → Static API responses cached at Singapore PoP
  → Dynamic requests served from Singapore GKE
  → TLS terminated at nearest Google PoP

Latency breakdown (post-fix):
  Client → Singapore Google PoP:    5ms
  PoP → Singapore GKE:              2ms
  GKE processing:                   20ms
  Singapore Spanner write:          5ms
  Response back:                    10ms
  Total:                           ~42ms ✅ under 50ms
```

### What Stays in London

```
London handles UK/EU trades only:
→ UK equity trades → UK Spanner
→ EU bond settlements → EU Spanner
→ BigQuery regulatory reporting: separate per jurisdiction
→ No Singapore data ever in London ✅

Cross-jurisdiction reporting (e.g. global P&L):
→ BigQuery Omni or federated queries
→ Query runs in each jurisdiction
→ Aggregated results returned — raw data never moves
```

---

## 3️⃣ BigQuery PII Remediation — 60 Days

### The Two Problems

```
Problem 1: Existing unencrypted PII in BigQuery datasets
  → Trader names, client identifiers in plaintext
  → FCA finding — must remediate

Problem 2: Future prevention
  → No controls preventing PII landing in BigQuery again
  → Governance gap
```

### 60-Day Delivery Plan

```
Days 1-10: Discovery & Assessment
  → Cloud DLP scan all BigQuery datasets
    dlp.googleapis.com → inspectContent API
  → Identify: which tables, which columns, what PII types
  → Classify: trader names, client IDs, account numbers
  → Output: complete PII inventory with risk ranking
  → Stakeholder report: FCA remediation plan submitted

Days 11-25: CMEK Implementation
  → Create Cloud KMS key rings per jurisdiction:
    keyRing: bigquery-uk-prod-keyring (europe-west2)
    keyRing: bigquery-eu-prod-keyring (europe-west3)
    keyRing: bigquery-sg-prod-keyring (asia-southeast1)
  → Enable CMEK on all BigQuery datasets:
    bq update --default_kms_key=projects/bank/locations/
    europe-west2/keyRings/bigquery-uk/cryptoKeys/bq-key
    banking-project:trade_reporting
  → Key rotation: 90 days automatic
  → Google cannot decrypt — CMEK enforced ✅

Days 26-45: PII De-identification
  → Cloud DLP de-identification transforms on existing data:
    Option A: Tokenisation — replace with format-preserving token
    Option B: Pseudonymisation — replace with consistent pseudonym
    Option C: Masking — replace with ***** for display only
  → For trader names: pseudonymisation
    "John Smith" → "TRADER-T-UK-4821" (consistent, reversible for authorised)
  → For client IDs: tokenisation via Cloud DLP surrogate tokens
  → Run DLP de-identification job on all affected tables:
    dlp.projects.deidentifyContent API
  → New de-identified tables created — old tables archived

Days 46-55: Prevention Controls
  → DLP inspection on BigQuery ingestion pipeline:
    Dataflow pipeline: inspect → alert → block if PII detected
  → BigQuery column-level security:
    CREATE ROW ACCESS POLICY on sensitive columns
    Only compliance team can see raw values
  → Data Catalog policy tags:
    Tag: PII_RESTRICTED on sensitive columns
    Tag enforcement: non-compliant queries blocked
  → Org policy: require CMEK on all new BigQuery datasets

Days 56-60: Validation & FCA Response
  → Re-run DLP scan — verify zero unencrypted PII
  → Penetration test on BigQuery access
  → Document: remediation evidence pack for FCA
  → Submit: FCA remediation closure report
  → Internal: update data governance runbook
```

---

## 4️⃣ 99.99% Resilience Per Jurisdiction

### 99.99% Maths

```
99.99% = 52 minutes downtime per year
= 4.3 minutes per month
= ~8.6 seconds per day

This means:
→ Single AZ failure: recover in < 30 seconds
→ Single region failure: recover in < 2 minutes
→ Everything automated — zero manual intervention
```

### Cloud Spanner Resilience

```
UK Stack:
  Spanner regional instance: nam4 → NO
  Correct: Spanner regional instance: eur3
    → belgium + netherlands replicas
    → 99.999% SLA built-in ✅
    → Automatic multi-zone replication
    → No failover configuration needed
    → Synchronous replication — zero data loss

Singapore Stack:
  Spanner regional instance: asia-southeast1
    → 3 zones in Singapore
    → MAS compliant — data never leaves Singapore ✅
    → 99.999% SLA ✅

Why Spanner over Cloud SQL:
→ Cloud SQL HA: ~60 second failover (brief outage)
→ Cloud Spanner: zero downtime, always available
→ For £50B/day trade processing: Spanner is mandatory
```

### GKE Resilience

```
Regional GKE Cluster (mandatory for 99.99%):
  → Control plane: 3 zones, replicated automatically
  → Node pools: spread across all 3 zones
  → PodDisruptionBudget: min 2 replicas always running

Zone failure scenario:
  → Pods on failed zone: evicted after 30 seconds
    (configure: --default-unreachable-toleration-seconds=30)
  → Rescheduled on healthy zones: 30-60 seconds
  → Cluster autoscaler: adds nodes in healthy zones if needed
  → Total impact: < 90 seconds ✅

Node pool configuration:
  payments-pool:
    minNodes: 3 (1 per zone minimum)
    maxNodes: 30
    locations: europe-west2-a, europe-west2-b, europe-west2-c

PodDisruptionBudget:
  spec:
    minAvailable: 2  # always 2 pods running minimum
    selector:
      matchLabels:
        app: trade-processor
```

### Pub/Sub Resilience

```
Pub/Sub: globally distributed by default
→ Messages stored across multiple zones automatically
→ 99.9%+ availability built-in
→ No configuration needed for zone resilience ✅

Regional topics (for data residency):
→ Singapore trades: topic with message-storage-policy
  allowedPersistenceRegions: [asia-southeast1]
→ UK trades: allowedPersistenceRegions: [europe-west2, europe-west1]
→ EU trades: allowedPersistenceRegions: [europe-west3, europe-west4]

Message ordering:
→ Enable ordering keys: trade_id as ordering key
→ All messages for same trade delivered in order ✅
→ Exactly-once delivery: enable exactly-once subscriptions
```

### Regional Failover Architecture

```
UK Active-Active (London + Belgium):
  Global LB → health check every 10 seconds
  London unhealthy (2 consecutive failures = 20 seconds):
    → Traffic shifts to Belgium automatically
    → Belgium GKE handles 100% UK traffic
    → Spanner eur3: Belgium replicas serve reads/writes
    → Total failover: ~30 seconds ✅

Singapore Active (Single region — MAS constraint):
  3-zone regional cluster in asia-southeast1
  Zone failure → automatic pod rescheduling
  Full region failure: Singapore service unavailable
  → Accept: MAS prevents cross-region failover
  → Mitigate: 3-zone HA achieves 99.99% within Singapore
  → Document: force majeure clause for full region failure
```

---

## 5️⃣ Cost Reduction — £105K/Month Saving

### Current Spend Analysis: £420K/Month

```
Estimated breakdown:
GKE compute (15 services, on-demand):  ~£180K/month (43%)
Cloud Spanner:                          ~£80K/month  (19%)
BigQuery:                               ~£60K/month  (14%)
Network egress:                         ~£40K/month  (10%)
Cloud Storage:                          ~£30K/month   (7%)
Other (Pub/Sub, KMS, etc):             ~£30K/month   (7%)
Total:                                  £420K/month
```

### Lever 1 — Committed Use Discounts (Biggest Lever)

```
Current: all compute on-demand
Fix: CUDs on baseline GKE compute

Baseline (always running): 60% of 15 services
→ Commit 60% of GKE compute for 1 year
→ 37% discount on committed portion

Saving:
£180K × 60% = £108K committed
£108K × 37% discount = £40K/month saving ✅

Action: analyse 90-day CPU/memory usage
→ Commit to p50 usage (median) — conservative
→ On-demand covers p50-p100 spikes
```

### Lever 2 — BigQuery Slot Commitments

```
Current: BigQuery on-demand pricing
→ Pay per TB scanned — expensive for large regulatory reports

Fix: BigQuery capacity commitments (slot reservations)
→ 500 slots committed: flat rate pricing
→ Break-even: if > X TB scanned per month

Analysis: review BigQuery usage logs
→ If regulatory jobs scan > 50TB/month → commitments cheaper
→ Estimated saving: £15K/month ✅

Additionally:
→ Partition all BigQuery tables by date
→ Queries scan only relevant partitions — not full table
→ Estimated query cost reduction: 60-70%
→ Additional saving: £8K/month ✅
```

### Lever 3 — Cloud Storage Lifecycle

```
Current: all GCS data on Standard storage
Fix: lifecycle policies per bucket

Trade archive (> 90 days): Nearline  (50% cheaper)
Trade archive (> 1 year):  Coldline  (80% cheaper)
Trade archive (> 3 years): Archive   (94% cheaper)

Implementation: lifecycle policy JSON on each bucket
→ Zero risk, zero performance impact on hot data
→ Estimated saving: £10K/month ✅
```

### Lever 4 — Right-Sizing Non-Production

```
Current: dev + staging running 24/7 at full capacity
Fix:
→ Dev environments: auto-shutdown 7pm, auto-start 8am
  → 65% compute saving on dev
→ Staging: scale to zero weekends (Friday 6pm → Monday 7am)
  → 28% compute saving on staging
→ GKE node auto-provisioning: scale to zero overnight

Estimated saving: £12K/month ✅
```

### Lever 5 — Network Egress Optimisation

```
Current: services in different regions communicating
Fix:
→ Co-locate services that communicate frequently
  in same region/zone — intra-zone traffic free
→ Cloud CDN for static trade reference data
  → Cache FX rates, instrument data at edge
  → Reduce origin egress 60%
→ Compress API responses (gzip/brotli)
  → Reduce egress volume 30-40%

Estimated saving: £8K/month ✅
```

### Lever 6 — Spot VMs for Batch Workloads

```
Non-critical batch workloads suitable for Spot:
→ BigQuery data preparation jobs
→ Trade reconciliation batch (runs midnight)
→ Regulatory report generation
→ ML model training (fraud detection)

Spot discount: 60-90% cheaper than on-demand
These workloads: restart gracefully on preemption
→ Checkpoint to GCS, resume from checkpoint

Estimated saving: £12K/month ✅
```

### Total Savings Summary

| Lever | Monthly Saving | Risk Level |
|---|---|---|
| CUDs on GKE baseline | £40K | Low |
| BigQuery commitments + partitioning | £23K | Low |
| Storage lifecycle policies | £10K | Zero |
| Right-size non-prod | £12K | Low |
| Network egress optimisation | £8K | Low |
| Spot VMs for batch | £12K | Low-Medium |
| **Total** | **£105K** | **Low overall** |

```
Target: £105K/month saving ✅
Timeline: achievable within 6 months
→ Quick wins (weeks 1-4): Storage lifecycle + right-size non-prod
  → £22K/month immediately
→ Medium term (weeks 5-12): CUDs + BigQuery partitioning
  → Additional £63K/month
→ Longer term (weeks 13-24): Spot batch + egress optimisation
  → Additional £20K/month
→ Total by month 6: £105K/month ✅
```

---

## 6️⃣ 6-Month Delivery Sequencing

```
Month 1:
→ Singapore GKE cluster provisioned
→ BigQuery DLP scan complete
→ Storage lifecycle policies live (immediate saving)
→ Non-prod auto-shutdown live (immediate saving)

Month 2:
→ Singapore Spanner instance live
→ Singapore routing via Global LB
→ BigQuery CMEK implemented
→ CUDs purchased for GKE baseline

Month 3:
→ Singapore latency < 50ms achieved ✅
→ BigQuery PII de-identified (FCA 60-day deadline met) ✅
→ BigQuery partitioning live

Month 4:
→ UK active-active (London + Belgium) live
→ Regional GKE clusters all jurisdictions
→ £85K/month savings running ✅

Month 5:
→ EU stack (Frankfurt + Netherlands) live
→ Spot VMs for batch workloads
→ Egress optimisation complete

Month 6:
→ Full 99.99% per jurisdiction achieved ✅
→ £105K/month savings achieved ✅
→ Full CMEK across all services ✅
→ MAS, FCA, GDPR compliance validated ✅
```

---

## 🎯 Board Summary — One Slide

| Mandate | Solution | Timeline | Outcome |
|---|---|---|---|
| **Singapore <50ms** | Singapore GKE + Spanner + Global LB | Month 3 | 42ms ✅ |
| **MAS data residency** | Spanner asia-southeast1, regional Pub/Sub topics | Month 2 | Compliant ✅ |
| **FCA PII remediation** | Cloud DLP + CMEK + column security | Day 60 | Compliant ✅ |
| **CMEK everywhere** | Cloud KMS per jurisdiction + org policy | Month 4 | Compliant ✅ |
| **99.99% UK** | London + Belgium active-active, Spanner eur3 | Month 4 | 99.99% ✅ |
| **99.99% EU** | Frankfurt + Netherlands active-active | Month 5 | 99.99% ✅ |
| **99.99% Singapore** | 3-zone regional cluster | Month 2 | 99.99% ✅ |
| **£105K saving** | CUDs + BigQuery + lifecycle + right-size + Spot | Month 6 | £105K ✅ |

---

> 🏆 **This is a Director/MD level answer.** The key differentiators: delivery sequencing by business priority (Singapore latency + FCA deadline first), specific GCP service names with configuration details, realistic cost estimates per lever, and acknowledging constraints (MAS prevents cross-region failover for Singapore) rather than pretending they don't exist.

---

## 📊 GCP Drill Complete — Final Scorecard

| Question | Topic | Score |
|---|---|---|
| Q20 — GCP | Architecture Review (5 concerns) | 8/10 |
| Q21 — GCP | Network Architecture (4 incidents) | 8.5/10 |
| Q22 — GCP | Global Trade Platform | Study mode 📖 |

---

> 📝 **Reminder: Observability (Day 7) still to cover before we finish!**

---

##https://www.cncf.io/blog/2020/10/19/how-to-set-up-multi-cluster-load-balancing-with-gke/



# 🗄️ GCP Databases — Complete Deep Dive

All 8 GCP database services — internals, use cases, scalability, read/write characteristics, FS fit and when to choose each.

---

## 🗺️ The GCP Database Decision Map

```
Is it transactional financial data? (money moves, ledger)
    ↓ YES                              ↓ NO
    ↓                                  ↓
Need global scale?              Is it analytical? (reporting, ML)
    ↓ YES      ↓ NO                 ↓ YES        ↓ NO
    ↓          ↓                    ↓             ↓
Cloud      Cloud SQL          BigQuery      Is it time-series?
Spanner    AlloyDB                           ↓ YES    ↓ NO
                                          Bigtable  Is it document?
                                                     ↓ YES   ↓ NO
                                                  Firestore  Is it cache?
                                                              ↓ YES  ↓ NO
                                                          Memorystore
                                                              Bigtable
```

---

# 1️⃣ Cloud Spanner

## What Is It?

> *"Cloud Spanner is the world's first globally distributed relational database with external consistency — it gives you the scale of NoSQL with the ACID guarantees of a traditional RDBMS. No other database does this at global scale."*

---

## Internals — How It Works

### TrueTime API — The Secret Sauce

```
Problem with distributed transactions:
→ Two servers in London and Singapore
→ Both receive writes simultaneously
→ Which write happened first?
→ Network clocks drift — can't trust timestamps

Traditional solution: pessimistic locking
→ Lock rows across regions before writing
→ Cross-region lock = 200ms+ latency ❌

Spanner solution: TrueTime
→ Google's atomic clocks + GPS receivers
  in every datacenter worldwide
→ TrueTime gives: time = [earliest, latest]
  → Uncertainty window: ~7 milliseconds
→ Spanner waits out the uncertainty window
  before committing
→ Guarantees: if T1 commits before T2 starts,
  T1.commitTime < T2.commitTime — always ✅
→ External consistency: globally ordered commits
  without cross-region locks ✅

Impact:
→ Read-write transactions: globally consistent
→ No stale reads — ever
→ No lost updates — ever
→ FS implication: balance correct everywhere
  simultaneously — no split-brain ✅
```

### Paxos Replication

```
Each Spanner "split" (data shard) replicated
across N Paxos replicas:

Spanner eur3 instance (Belgium + Netherlands):
  Belgium zone-a: Paxos leader replica
  Belgium zone-b: Paxos follower replica
  Netherlands zone-a: Paxos follower replica

Write flow:
  Client writes → Belgium leader
  Leader replicates to followers via Paxos
  Majority acknowledgement (2 of 3) → commit
  → Synchronous replication ✅
  → Leader failure → new leader elected in seconds ✅

Read flow:
  Strong read → goes to leader → always fresh
  Stale read (bounded) → any replica → faster
  FS rule: always strong reads for balances,
           stale reads acceptable for analytics
```

### Splits — Automatic Sharding

```
Spanner automatically splits data into ranges:
→ No manual sharding needed
→ Hot ranges automatically split and redistributed
→ Cold ranges merged to save resources

Example — account_balance table:
  Split 1: accountId A000000 - A500000 → Belgium zone-a
  Split 2: accountId A500001 - B000000 → Belgium zone-b
  Split 3: accountId B000001 - B500000 → Netherlands zone-a

If accountId A000100 gets very hot (high traffic):
→ Spanner automatically splits:
  Split 1a: A000000 - A000099
  Split 1b: A000100 - A000199
  Split 1c: A000200 - A500000
→ Each split on different server → load distributed ✅
→ Zero manual intervention ✅
```

---

## Scalability

```
Read throughput:
→ Add read replicas in any region
→ Stale reads scale horizontally — unlimited
→ Strong reads: scale with number of splits

Write throughput:
→ Scale by adding nodes (processing units)
→ 1 node = 1000 processing units = ~2000 QPS writes
→ Scale to thousands of nodes → millions of writes/s

Storage:
→ Unlimited — automatically managed by Google
→ No storage capacity planning needed

Latency:
→ Same region: ~5ms writes, ~2ms reads
→ Multi-region: ~20ms writes (TrueTime wait)
→ Cross-continent: ~100ms (physics — unavoidable)
```

---

## Read/Write Characteristics

| Operation | Latency | Notes |
|---|---|---|
| **Strong read** | 2-5ms (regional) | Always fresh — hits leader |
| **Stale read** | 1-2ms | Any replica — may be 15s stale |
| **Read-write txn** | 5-20ms | Full ACID, locks rows |
| **Read-only txn** | 2-5ms | No locks — snapshot isolation |
| **Blind write** | 3-10ms | INSERT/UPDATE without read |
| **Batch write** | High throughput | Mutations API — no locking |

---

## FS Use Cases

```
✅ Core banking ledger
   → Account balances, debits, credits
   → ACID across multiple accounts in one txn
   → Zero data loss, globally consistent

✅ Payment processing
   → Idempotent payment writes
   → Global payment state

✅ Trade book
   → Trade positions across desks
   → Real-time P&L

✅ Limit management
   → Credit limits, trading limits
   → Globally consistent reads — no stale limits

✅ Multi-region account state
   → Customer with accounts in UK + Singapore
   → Consistent view regardless of where accessed
```

---

## Spanner Multi-Region Configurations

```
Regional (single region):
  → All replicas in one region
  → Lowest latency writes
  → SLA: 99.999%
  → FS: UK-only data (FCA compliance)
  Example: regional/europe-west2

Multi-region (2 regions):
  → eur3: Belgium + Netherlands
  → nam4: Northern Virginia + South Carolina
  → asia1: Tokyo + Osaka
  → SLA: 99.999%
  → FS: EU data residency (both regions in EU) ✅

Multi-region (global):
  → nam-eur-asia1: US + EU + APAC
  → SLA: 99.999%
  → FS: Global trading platforms
  → Latency: higher (TrueTime wait across continents)
```

---

## Spanner vs Cloud SQL — When to Choose

| Factor | Cloud Spanner | Cloud SQL |
|---|---|---|
| **Scale** | Unlimited, automatic | Limited — max 96 vCPU, 624GB RAM |
| **Global** | Yes — multi-region ACID | No — single region |
| **Cost** | High — ~£0.90/node/hour | Low — ~£0.10/vCPU/hour |
| **ACID** | Global | Regional only |
| **Familiarity** | Spanner SQL (mostly standard) | Standard PostgreSQL/MySQL |
| **Migration** | Requires schema changes | Lift-and-shift from on-prem |
| **FS Fit** | Core financial systems | Microservice-specific DBs |

---

# 2️⃣ Cloud SQL

## What Is It?

> *"Cloud SQL is Google's fully managed relational database service — PostgreSQL, MySQL or SQL Server. It's lift-and-shift from on-prem RDBMS — same SQL, same drivers, managed infrastructure."*

---

## Internals

```
Under the hood:
→ Standard PostgreSQL/MySQL running on GCE VMs
→ Google manages: patching, backups, replication
→ You manage: schema, queries, indexes, connections

High Availability:
→ Primary instance in zone-a
→ Standby instance in zone-b (same region)
→ Synchronous replication via Google internal network
→ Automatic failover: ~60 seconds
→ SLA: 99.95%

Read Replicas:
→ Async replication from primary
→ Can be in different regions (cross-region replica)
→ Read-only — offload reporting queries
→ Manual promote to primary if needed (disaster recovery)

Connection:
→ Cloud SQL Auth Proxy: secure connection without
  public IPs or SSL certificates
→ Private Service Connect: private IP connection
→ Connection pooling: PgBouncer (must manage yourself)
```

---

## Scalability

```
Vertical scaling only:
→ Scale up vCPUs: 1 → 96 vCPUs
→ Scale up RAM: 0.6GB → 624GB
→ Scale up storage: 10GB → 64TB (auto-grow)
→ Requires brief downtime for vCPU/RAM changes

Horizontal scaling:
→ Read replicas only — not write scaling
→ For write scaling → must shard manually
  OR migrate to Spanner

Limitations:
→ Max ~30K connections (with PgBouncer)
→ Max ~64TB storage
→ Single region primary
→ If these limits are hit → time to evaluate Spanner
```

---

## FS Use Cases

```
✅ Microservice-owned databases
   → Each microservice has its own Cloud SQL instance
   → Database-per-service pattern
   → Small-medium data volumes

✅ Migration target from on-prem Oracle/PostgreSQL
   → Same SQL dialect → minimal code changes
   → Managed → no DBA overhead

✅ Non-critical internal tools
   → Reporting tools, admin interfaces
   → Lower cost than Spanner

✅ Development and staging environments
   → Cheap, easy to spin up/down
   → Same dialect as production

❌ NOT suitable for:
   → Global multi-region transactions
   → Unlimited horizontal write scale
   → > 64TB datasets
   → Sub-second failover requirements
```

---

# 3️⃣ AlloyDB

## What Is It?

> *"AlloyDB is Google's newest database — a PostgreSQL-compatible database built for demanding enterprise workloads. It's 4x faster than standard PostgreSQL for transactional workloads and 100x faster for analytical queries. Think: Cloud SQL's successor for high-performance FS workloads."*

---

## Internals — What Makes It Different

```
Disaggregated storage:
→ Compute (PostgreSQL engine) separated from storage
→ Storage: distributed, replicated automatically
→ Compute: scale independently of storage

Columnar engine (built-in):
→ AlloyDB scans columnar-format data for analytics
→ Same database serves OLTP + analytics queries
→ No need to ETL to BigQuery for some analytics ✅

Adaptive autovacuum:
→ Standard PostgreSQL vacuum is manual/scheduled
→ AlloyDB: ML-driven autovacuum — always optimal
→ No performance degradation from table bloat

Read pool:
→ Multiple read pool instances — horizontal read scaling
→ All read from same storage — always consistent
→ Add/remove read nodes in seconds
```

---

## AlloyDB vs Cloud SQL vs Spanner

| Factor | Cloud SQL | AlloyDB | Cloud Spanner |
|---|---|---|---|
| **Compatibility** | Full PostgreSQL | Full PostgreSQL | Spanner SQL |
| **Write performance** | Standard | 4x faster | Horizontal |
| **Analytical queries** | Standard | 100x faster | Standard |
| **Multi-region** | Read replicas only | Yes (Preview) | Yes — native |
| **Cost** | Low | Medium | High |
| **FS Fit** | Small-medium workloads | High-performance single region | Global ACID |

```
When to choose AlloyDB over Cloud SQL:
→ PostgreSQL workload hitting Cloud SQL limits
→ Need faster analytics without BigQuery ETL
→ Oracle migration — needs high performance
→ Real-time reporting on same DB as transactions

When to choose AlloyDB over Spanner:
→ Must stay PostgreSQL-compatible
→ Single region is acceptable
→ Cost is a constraint
→ Team is PostgreSQL-native
```

---

# 4️⃣ Bigtable

## What Is It?

> *"Bigtable is Google's original NoSQL wide-column store — it powers Google Search, Maps and Gmail internally. It's designed for massive scale, low latency, high throughput time-series and event data."*

---

## Internals

```
Data model:
→ Rows identified by row key (string)
→ Columns grouped into column families
→ Each cell: row key + column family + column qualifier + timestamp

Example — fraud feature store:
Row key: "ACC-001#2024-01-15T09:00:00"
  cf:features:
    tx_velocity_1h:    "15"
    avg_tx_amount_7d:  "234.50"
    location_score:    "0.85"
    device_trust:      "0.92"

Row key design is CRITICAL:
→ Bigtable sorts rows lexicographically by row key
→ Range scans efficient if row key designed correctly
→ Hot spotting if sequential row keys used

FS Row key patterns:
→ "accountId#reverseTimestamp" — recent data first
→ "hash(accountId)#timestamp" — distribute + time order
→ "eventType#timestamp#accountId" — query by type + time
```

### Tablet Architecture

```
Data split into tablets (like Spanner splits):
→ Each tablet: contiguous row key range
→ Tablet servers: serve read/write for their tablets
→ Tablet size: ~1GB default

Scale:
→ Add nodes → more tablet servers
→ Tablets automatically redistributed
→ Linear scaling: 2x nodes = ~2x throughput

Replication:
→ Multi-cluster replication across regions
→ Eventual consistency between clusters
→ FS use: replicate fraud features EU → Singapore
  for local scoring (eventual consistency acceptable)
```

---

## Read/Write Characteristics

```
Writes:
→ Always go to tablet server in memory (Memtable)
→ Flushed to persistent storage (SSTable) periodically
→ Write latency: <1ms for single row
→ Write throughput: millions of rows/second per cluster

Reads:
→ Row key lookup: <1ms (single row)
→ Range scan: efficient if row key ordered correctly
→ Full table scan: avoid — very slow on large tables
→ No JOINs — single table queries only

Bigtable is optimised for:
✅ High write throughput
✅ Low latency key lookups
✅ Time-series data (sequential row keys + timestamps)
✅ Append-only patterns (never update, always append)

Not optimised for:
❌ Complex queries / aggregations
❌ JOINs across tables
❌ Ad-hoc queries (need to know access pattern upfront)
❌ Strong consistency across regions
```

---

## FS Use Cases

```
✅ Fraud detection feature store
   → Write: real-time transaction events → update features
   → Read: score API reads latest features per account
   → Row key: accountId → instant lookup
   → Scale: millions of accounts × hundreds of features

✅ Transaction audit trail
   → Append-only event log
   → Row key: accountId#reverseTimestamp
   → Range query: all events for account in date range
   → Scale: billions of rows

✅ Market data / tick data
   → Price ticks every millisecond
   → Row key: instrument#timestamp
   → Range query: all ticks for instrument in time range
   → Scale: petabytes of historical data

✅ Real-time analytics input
   → Bigtable → Dataflow → BigQuery
   → Streaming pipeline for regulatory reporting

❌ NOT suitable for:
   → ACID transactions
   → Multi-table relationships
   → Ad-hoc SQL queries
   → Small datasets (< 1TB — Cloud SQL cheaper)
```

---

## Bigtable vs Cassandra — Definitive Comparison

This is a common interview question:

| Factor | Bigtable | Cassandra |
|---|---|---|
| **Management** | Fully managed by Google | Self-managed (or Astra cloud) |
| **Consistency** | Strong (single cluster) | Tunable (ONE to ALL) |
| **Multi-region** | Multi-cluster async replication | Native multi-DC, tunable consistency |
| **Ops overhead** | Zero — Google manages | High — compaction, repair, tuning |
| **Cost model** | Pay per node + storage | Pay for VMs + storage |
| **Query language** | Bigtable API (no CQL) | CQL (SQL-like) |
| **Ecosystem** | GCP native | Open source, any cloud |
| **FS Fit on GCP** | Preferred — managed, scalable | If multi-cloud or specific Cassandra expertise |

```
FS Decision:
Running on GCP → Bigtable (managed, no ops overhead)
Multi-cloud or already have Cassandra → Cassandra
Need tunable consistency per operation → Cassandra
Want zero ops → Bigtable ✅
```

---

# 5️⃣ Firestore

## What Is It?

> *"Firestore is Google's managed document database — stores JSON-like documents with flexible schema. Unique feature: real-time listeners — clients receive updates the moment data changes, without polling."*

---

## Internals

```
Data model:
Collections → Documents → Fields + Subcollections

Example — customer profile:
Collection: customers
  Document: C-UK-001
    name:       "TKN-9c1a"        ← tokenised
    jurisdiction: "UK"
    preferences:
      channel:  "email"
      language: "en"
    kyc_status: "VERIFIED"
    Subcollection: accounts
      Document: ACC-001
        type:    "current"
        opened:  "2020-01-15"

ACID transactions:
→ Multi-document transactions ✅
→ Within same database
→ Optimistic concurrency — transaction retries on conflict

Indexing:
→ Automatic single-field indexes on every field
→ Composite indexes: manually created for complex queries
→ FS: index kyc_status + jurisdiction for compliance queries
```

---

## Scalability

```
Automatic scaling:
→ No capacity planning needed
→ Scales to millions of concurrent connections
→ Scales to billions of documents

Limitations:
→ 1 write/second per document (hot document limit)
→ 1MB per document max
→ Transactions: max 500 documents per transaction

FS Implication — hot document problem:
→ If you update same document > 1/second:
  → contention → retries → latency spikes
→ Example: shared counter (total payments today)
  → Don't store in single Firestore document
  → Use distributed counter pattern:
    → Shard counter across 100 documents
    → Sum shards for total → scales to 100 writes/s
```

---

## FS Use Cases

```
✅ Customer profiles
   → Flexible schema — different fields per customer type
   → Real-time sync — mobile app updates instantly
   → KYC status, preferences, communication settings

✅ Product catalogue
   → Loan products, savings rates, card offerings
   → Real-time updates — rate change → all apps see instantly
   → Flexible schema — different fields per product type

✅ Notification preferences
   → Per-customer settings
   → Real-time listener — preference change → immediate effect

✅ Feature flags / configuration
   → Runtime configuration changes
   → Real-time propagation to all services

❌ NOT suitable for:
   → High-write financial state (>1 write/s per account)
   → Complex joins or aggregations
   → Time-series data
   → Core transaction processing
```

---

# 6️⃣ BigQuery

## What Is It?

> *"BigQuery is Google's serverless, petabyte-scale analytical data warehouse. No infrastructure to manage — run SQL queries on petabytes of data in seconds. The standard for regulatory reporting and analytics in FS on GCP."*

---

## Internals

```
Columnar storage (Capacitor format):
→ Data stored column-by-column (not row-by-row)
→ Query only reads columns needed
→ SELECT amount, timestamp FROM trades
  → reads ONLY amount + timestamp columns
  → skips all other columns → massive I/O saving

Dremel execution engine:
→ Massively parallel query execution
→ Query split into thousands of parallel tasks
→ Each task processes a partition of data
→ Results merged → returned to client
→ Petabyte queries: seconds to minutes

Separation of compute and storage:
→ Storage: Colossus (Google's distributed FS)
→ Compute: dynamically allocated slots
→ No running servers → serverless ✅
→ Pay only for queries run (on-demand)
   OR reserve slots (capacity commitment)

Partitioning:
→ Partition by date/timestamp: most common in FS
→ Query includes partition filter → reads only
  relevant partitions → massive cost + speed saving

Clustering:
→ Physically sort data within partitions
→ By high-cardinality columns (accountId, tradeId)
→ Further reduces data scanned per query
```

---

## Read/Write Characteristics

```
Writes (ingestion):
→ Streaming inserts: ~1-3 seconds latency
→ Batch loads: minutes (but free)
→ Storage Write API: exactly-once, high throughput
→ NOT designed for OLTP writes (1 row at a time)

Reads (queries):
→ Full table scan: avoid — expensive + slow
→ Partitioned + clustered: fast + cheap
→ Concurrent queries: unlimited (serverless)
→ Query result cache: repeated queries → instant

FS Rule:
→ BigQuery = analytics only
→ Never use for transactional reads/writes
→ Feed from: Spanner (export), Pub/Sub (streaming),
  GCS (batch), Datastream (CDC)
```

---

## FS Use Cases

```
✅ FCA regulatory reporting
   → Daily/monthly/quarterly reports
   → Complex aggregations across billions of rows
   → SQL familiar to analysts and compliance team

✅ AML (Anti Money Laundering) analytics
   → Pattern detection across all transactions
   → Network graph analysis (BigQuery Graph queries)
   → ML model training (BigQuery ML)

✅ Trade analytics
   → VWAP, execution quality analysis
   → Historical P&L attribution
   → Risk analytics

✅ Customer analytics
   → Segmentation, propensity modelling
   → Product usage analytics
   → Churn prediction

✅ Audit log analytics
   → Query immutable audit records
   → FCA data requests ("show all trades > £1M in Q3")

❌ NOT suitable for:
   → OLTP workloads
   → Sub-second query latency
   → Frequent small updates
   → Serving customer-facing APIs
```

---

## BigQuery FS Best Practices

```
Partitioning strategy (mandatory for FS):
→ PARTITION BY DATE(event_timestamp)
→ Every query MUST include date filter
→ Org policy: require partition filter on large tables
→ Cost impact: 10TB table, query 1 day = 27GB scanned
  vs full scan = 10TB → 370x cost saving ✅

Clustering (recommended for FS):
→ CLUSTER BY account_id, event_type
→ Queries filtering by account_id → much less data scanned

Column-level security for PII:
→ Policy tags on sensitive columns
→ roles/datacatalog.categoryFineGrainedReader
  → only compliance team can query raw PII columns
→ Everyone else: sees masked values automatically

Row-level security:
→ Row access policies per team
→ UK team sees only UK trades
→ EU team sees only EU trades
→ Compliance: sees all
```

---

# 7️⃣ Memorystore (Redis)

## What Is It?

> *"Memorystore is Google's fully managed Redis and Memcached service. Same Redis API you know — zero operational overhead, automatic HA, managed patching."*

---

## Internals & HA

```
Memorystore for Redis (Standard tier):
→ Primary instance + replica in different zones
→ Automatic failover: ~30 seconds
→ Data persistence: RDB snapshots or AOF logs
→ SLA: 99.9%

Memorystore for Redis Cluster (new):
→ Multiple shards across nodes
→ Horizontal scaling — add shards as needed
→ Scale to 300GB+ in-memory data
→ SLA: 99.9%

vs self-managed Redis on GKE:
→ Memorystore: zero ops, automatic patching, HA
→ Self-managed: full control, more config options
→ FS recommendation: Memorystore — eliminate ops overhead

Connection:
→ Private IP only — no public internet exposure ✅
→ Access via VPC — same as your GKE pods
→ VPC Service Controls: protect from exfiltration
```

---

## FS Use Cases

```
✅ Session management
   → JWT token cache
   → Session state for authenticated users
   → TTL-based expiry

✅ API rate limiting
   → INCR per client per window
   → Atomic operations — no race conditions

✅ Payment limit counters
   → INCR for daily spend tracking
   → TTL for midnight reset

✅ Statement API cache
   → Cache last 30 days statements per account
   → Cache-Aside pattern
   → Event-driven invalidation

✅ Token cache
   → Cache detokenised values
   → Reduce Token Vault round trips
   → TTL: 1 hour

✅ Distributed locking
   → SETNX for mutex locks
   → Prevent concurrent saga execution
```

---

# 8️⃣ Pub/Sub

## What Is It?

> *"Cloud Pub/Sub is GCP's fully managed messaging service — similar to Kafka but serverless and globally distributed by default. No brokers to manage, no partitions to configure, scales automatically."*

---

## Pub/Sub vs Kafka — The Key Comparison

This is critical for FS interviews on GCP:

| Factor | Pub/Sub | Kafka |
|---|---|---|
| **Management** | Fully managed — zero ops | Self-managed (or Confluent) |
| **Partitions** | No concept — automatic | Manual partition planning |
| **Ordering** | Per ordering key | Per partition |
| **Retention** | 7 days max | Configurable — unlimited |
| **Replay** | Limited — seek to timestamp | Full replay from offset 0 |
| **Exactly-once** | Exactly-once subscriptions | Kafka transactions |
| **Throughput** | Unlimited — auto-scales | Limited by partition count |
| **Cost** | Pay per message | Pay per broker node |
| **Ecosystem** | GCP native | Rich — Kafka Streams, Connect |
| **Schema registry** | No native | Confluent Schema Registry |

---

## When Pub/Sub vs Kafka on GCP

```
Choose Pub/Sub when:
→ GCP-native workload — no multi-cloud requirement
→ Don't want to manage brokers/partitions
→ Variable traffic — scales to zero, no idle cost
→ Simple fan-out: one topic → many subscribers
→ Short retention sufficient (< 7 days)
→ Team doesn't have Kafka expertise

Choose Kafka (on GKE or Confluent Cloud) when:
→ Need unlimited retention (event sourcing, audit)
→ Need full replay capability (rebuild read models)
→ Need exactly-once with Kafka transactions
→ Need Kafka Streams / KSQL processing
→ Multi-cloud (Kafka works anywhere)
→ Existing Kafka investment and expertise
→ Need Schema Registry (Avro/Protobuf enforcement)

FS Guidance:
→ New GCP-native project → Pub/Sub first
→ Event sourcing requiring 7+ year replay → Kafka
→ Audit trail requiring immutable log → Kafka
→ Simple notification pipeline → Pub/Sub ✅
→ Complex exactly-once payment pipeline → Kafka ✅
```

---

## Pub/Sub FS Configuration

```
Message ordering:
→ Enable ordering keys: account_id
→ All messages for same account → same ordering key
→ Delivered in order per ordering key ✅
→ (like Kafka partition key)

Exactly-once delivery:
→ Enable on subscription:
  gcloud pubsub subscriptions create trade-processor \
    --enable-exactly-once-delivery
→ Subscriber must ack within ack deadline
→ If not acked → redelivered
→ With exactly-once: no duplicates ✅

Dead letter topic:
→ After N failed delivery attempts → dead letter topic
→ Separate subscriber processes DLQ
→ Alert on DLQ message count growth

Message retention:
→ Default: 7 days
→ FS compliance: not suitable for 7-year FCA audit
→ Solution: Pub/Sub → Dataflow → BigQuery/GCS for archival
```

---

# 📊 Complete GCP Database Comparison — FS Summary

| Database | Type | Consistency | Scale | Latency | FS Primary Use |
|---|---|---|---|---|---|
| **Cloud Spanner** | Relational | Global ACID | Unlimited horizontal | 5-20ms | Core ledger, payments |
| **Cloud SQL** | Relational | Regional ACID | Vertical only | 1-5ms | Microservice DBs |
| **AlloyDB** | Relational (PG) | Regional ACID | Vertical + read pool | <1ms | High-perf PostgreSQL |
| **Bigtable** | Wide-column | Strong (single) | Unlimited horizontal | <1ms | Audit, time-series, features |
| **Firestore** | Document | Strong | Auto | 5-10ms | Customer profiles, config |
| **BigQuery** | Analytical | Eventual | Petabyte | Seconds | Regulatory reporting, AML |
| **Memorystore** | In-memory | Strong | Vertical + cluster | <1ms | Cache, sessions, counters |
| **Pub/Sub** | Messaging | At-least-once / exactly-once | Unlimited | <100ms | Event streaming, notifications |

---

## 🏦 FS GCP Database Architecture — Full Stack

```
Customer-facing APIs:
  Memorystore Redis ← cache layer (sub-ms)
  ↓ cache miss
  Cloud Spanner ← account state, balances (5ms, ACID)
  Firestore ← customer profiles, preferences (5ms)

Event Processing:
  Pub/Sub OR Kafka ← event streaming
  Bigtable ← fraud features, audit trail (<1ms)
  Dataflow ← stream processing ETL

Analytics & Compliance:
  BigQuery ← regulatory reporting (seconds, petabyte)
  AlloyDB ← operational reporting (ms, PostgreSQL)

Microservice-specific:
  Cloud SQL ← per-microservice relational DB

Archive:
  GCS (Standard → Nearline → Coldline → Archive)
  ← 7-year FCA WORM archive
```

---

## ✅ GCP Database — Quick Revision Checklist

- [ ] Explain TrueTime and why it enables global ACID in Spanner
- [ ] Know Spanner multi-region configs: eur3, nam4, global
- [ ] Know when Cloud SQL → AlloyDB → Spanner escalation path
- [ ] Explain Bigtable row key design and hotspot avoidance
- [ ] Know Bigtable vs Cassandra — when each on GCP
- [ ] Know Firestore 1-write/second limit and distributed counter fix
- [ ] Know BigQuery partitioning + clustering for cost optimisation
- [ ] Know BigQuery column-level security for PII
- [ ] Know Pub/Sub vs Kafka — when to choose each in FS
- [ ] Know Memorystore HA configuration
- [ ] Can map any FS use case to the right GCP database

---
