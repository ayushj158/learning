Exactly right — and that's a VP-level answer. Let me validate and complete:

---

## What stores tweets per user

### Storage — DB

```
Tweets stored in DB:
  tweetId (Snowflake ID — time ordered)
  userId
  content
  timestamp
  mediaUrls

Why Snowflake ID for tweets?
  Time ordered → range queries by time efficient
  "get tweets after tweetId X" → simple comparison
  No separate timestamp index needed
```

**SQL vs NoSQL for tweets:**
```
SQL (PostgreSQL):
  ✓ ACID compliance — tweet either exists or doesn't
  ✓ Range queries — WHERE tweetId > X LIMIT 10
  ✗ Sharding complex at Twitter scale

NoSQL (Cassandra):
  ✓ Write heavy — millions of tweets/sec
  ✓ Natural partitioning by userId
  ✓ Time-series queries fast
  ✗ No joins — need denormalization
  → Twitter actually uses Manhattan (custom)
  → GS scale → Cassandra fine
```

---

## Content cached at CDN ✅ — exactly right

```
Tweet text → small, stored in DB + Redis cache
Media (images, videos) → CDN

Flow:
  user posts tweet with image
  → image uploaded to S3/blob storage
  → CDN pulls from S3, caches at edge
  → tweet stores CDN URL not raw image

Benefits:
  CDN serves media from nearest edge node
  → low latency globally
  → reduces DB/storage load
  → scales automatically
```

---

## Your full architecture — validated

```
Post flow:
  user posts → DB insert → emit PostEvent to Kafka
  
  FeedService consumes PostEvent:
    get followers from cache (userId → List<followerId>)
    for each follower → update feed cache
    feed cache: Map<userId, Deque<Tweet>> (bounded size)
  
  Media → S3 → CDN

Follow flow:
  user follows → DB insert → emit FollowEvent to Kafka
  
  FeedService consumes FollowEvent:
    update followers cache
    optionally backfill recent posts of new followee

Feed pagination:
  page 1 → Redis feed cache (fast, in memory)
  page 2+ → DB range query (tweetId > lastSeen LIMIT 10)
```

---

## One thing to add — fan-out problem

```
Celebrity user (1M followers) posts tweet:
  FeedService updates 1M feed caches
  → takes too long → feed delay for followers

Two strategies:

Fan-out on write (push model — what you described):
  ✓ getFeed() is instant — already precomputed
  ✗ celebrity post → 1M cache updates → slow

Fan-out on read (pull model):
  getFeed() → fetch posts from all followees → merge
  ✓ no write overhead
  ✗ getFeed() slow for users following many people

Hybrid (Twitter's actual approach):
  Regular users → fan-out on write
  Celebrities   → fan-out on read
  getFeed() merges precomputed feed + celebrity posts
```

---

## Your pagination question — how DB stores sorted feed

```
Not a heap in DB — use B-Tree index:

CREATE TABLE tweets (
  tweetId BIGINT PRIMARY KEY,  ← Snowflake ID, naturally time-ordered
  userId  BIGINT,
  content TEXT,
  INDEX (userId, tweetId)      ← composite index for user's tweets
);

Range query:
SELECT * FROM tweets 
WHERE userId IN (followee1, followee2, ...)
AND tweetId > lastSeenTweetId
ORDER BY tweetId DESC
LIMIT 10;

B-Tree index makes this O(log n) ← fast range query
Snowflake ID = time ordered = no separate timestamp needed
```

---

## Complete picture for interview

```
Storage:
  Tweets    → Cassandra/PostgreSQL (Snowflake ID as PK)
  Follows   → PostgreSQL (userId, followeeId)
  Feed cache→ Redis (userId → sorted set of tweetIds)
  Media     → S3 + CDN

Processing:
  Post/Follow events → Kafka
  FeedService consumes → updates Redis feed cache
  
  Fan-out:
    Regular users → push to followers feed cache
    Celebrities   → pull on getFeed()

Pagination:
  Page 1: Redis sorted set → O(1)
  Page N: DB range query on Snowflake ID → O(log n)
```

---

## What to say in GS interview

*"Tweets stored in Cassandra partitioned by userId — write heavy, scales horizontally. Media on S3 behind CDN — low latency global delivery. Feed precomputed in Redis using Kafka fan-out — getFeed() is instant for page 1. Pagination uses Snowflake ID as cursor for DB range queries. Celebrity fan-out on read to avoid 1M cache updates per post."*

---

Twitter Feed LLD ✅ done.

**Next — Splitwise, Snake & Ladder, or HLD?**

Great structure — let me answer each question precisely.

---

## Tables — SQL vs NoSQL decision

### 1. Tweets Table

```
tweet_id    BIGINT (Snowflake)  PK
user_id     BIGINT
content     TEXT
media_urls  TEXT[]
created_at  TIMESTAMP
```

**SQL (PostgreSQL) or NoSQL (Cassandra)?**

```
For GS trading floor (500 desks, 50K tweets/day):
→ PostgreSQL ✓
  Simple, ACID, easy range queries
  Shard by tweet_id range if needed later

For Twitter scale (500M tweets/day):
→ Cassandra ✓
  Partition by user_id → fast "get user's tweets"
  Write heavy → Cassandra optimized for writes
  No joins needed

For interview → say PostgreSQL first, mention Cassandra for scale
```

---

### 2. Relationships Table

Your two-table approach is correct:

```
followers table:
  user_id      BIGINT   ← the person being followed
  follower_id  BIGINT   ← the person doing the following
  created_at   TIMESTAMP
  PK: (user_id, follower_id)
  INDEX: (follower_id, user_id)  ← "who does user X follow?"

This ONE table answers both questions:
  "who follows user X?" → WHERE user_id = X
  "who does user X follow?" → WHERE follower_id = X
```

No need for two separate tables — one table with two indexes.

**SQL or NoSQL?**
```
PostgreSQL ✓
  Moderate scale — follow relationships change rarely
  Need joins occasionally
  ACID for follow/unfollow consistency

At Twitter scale → Cassandra or dedicated graph DB (Neo4j)
```

---

### 3. Users Table

```
user_id     BIGINT (Snowflake) PK
username    VARCHAR(50) UNIQUE
email       VARCHAR(100)
created_at  TIMESTAMP
profile_url TEXT
```

**Always PostgreSQL** — user data is:
```
Low write frequency (profile changes rarely)
Need ACID (email uniqueness)
Complex queries (search by username)
Small dataset compared to tweets
```

---

### 4 & 5. Feed Tables — your approach is good but simplify

Your `feed` + `feed_tweets` is correct. Let me clarify:

```
feed_tweets table:
  feed_owner_id  BIGINT   ← whose feed this is
  tweet_id       BIGINT   ← tweet in their feed
  tweeted_by     BIGINT   ← who wrote the tweet
  score          BIGINT   ← timestamp for ordering
  PK: (feed_owner_id, tweet_id)
  INDEX: (feed_owner_id, score DESC)  ← fast feed pagination
```

No need for separate `feed` table — `feed_tweets` IS the feed.

**SQL or NoSQL?**
```
This table is WRITE HEAVY:
  Every tweet → N writes (one per follower)
  17K tweets/sec × 200 followers = 3.4M writes/sec at Twitter scale

→ Cassandra ✓
  Partition by feed_owner_id
  Clustering key = tweet_id DESC (time ordered)
  Fast reads: SELECT * WHERE feed_owner_id=X LIMIT 20

For GS scale → PostgreSQL fine
```

---

## Full Table Summary

| Table | DB | Why |
|---|---|---|
| tweets | PostgreSQL (GS) / Cassandra (Twitter) | Write heavy at scale |
| relationships | PostgreSQL | Moderate writes, needs indexes |
| users | PostgreSQL | Low writes, ACID needed |
| feed_tweets | Cassandra (Twitter) / PostgreSQL (GS) | Very write heavy at scale |

---

## Cache — answering your questions

### 1. Does cache hold entry per user?

```
YES — one cache entry per user:

Redis Sorted Set:
  key:   timeline:{userId}
  value: Set of tweetIds scored by timestamp

timeline:user1 → {tweetId5:1700, tweetId3:1699, tweetId1:1698...}
timeline:user2 → {tweetId4:1700, tweetId2:1699...}

Each user has completely independent cache entry ✓
```

### 2. How many elements in cache?

```
Keep latest 800 tweets per user in cache:
  800 × avg 1KB per tweetId reference = 800KB per user
  300M users × 800KB = 240TB total Redis

Why 800?
  Most users never scroll past page 20 (20 tweets × 20 pages = 400)
  800 gives comfortable buffer
  
  ZREMRANGEBYRANK timeline:{userId} 0 -801
  → keeps only latest 800, removes oldest automatically
```

### 3. Pagination — how cache and DB interact

```
Page 1 (tweets 1-20):
  ZREVRANGE timeline:{userId} 0 19
  → returns 20 latest tweetIds from Redis
  → fetch full tweet content from Tweet Cache
  → return to client
  → cache NOT modified

Page 2 (tweets 21-40):
  ZREVRANGE timeline:{userId} 20 39
  → still from Redis (within 800 limit)
  → cache NOT modified

Page N beyond 800:
  lastSeenTweetId from client (cursor)
  SELECT * FROM feed_tweets
  WHERE feed_owner_id = userId
  AND tweet_id < lastSeenTweetId
  ORDER BY tweet_id DESC
  LIMIT 20
  → DB query, cache NOT updated (no point caching old pages)
```

**Cache never updated on read — only on write (new tweet fan-out)**

---

## Tweet Flow — complete and correct

```
POST /tweet {content, media}

Step 1 — Tweet Service:
  generate tweetId (Snowflake)
  upload media → S3 → get CDN URL
  INSERT into tweets table (PostgreSQL/Cassandra)
  PUBLISH to Kafka topic "new_tweet" {tweetId, userId, timestamp}
  return 200 OK ← immediately, don't wait for fan-out

Step 2 — Fan-out Service (Kafka consumer):
  consume message {tweetId, userId, timestamp}
  
  get followers:
    SELECT follower_id FROM relationships WHERE user_id = tweetAuthorId
    paginate in batches of 2000
  
  for each batch of followers:
    
    Redis update (cache):
      ZADD timeline:{followerId} {timestamp} {tweetId}
      ZREMRANGEBYRANK timeline:{followerId} 0 -801  ← trim to 800
    
    DB update (persistence):
      INSERT INTO feed_tweets (feed_owner_id, tweet_id, tweeted_by, score)
      VALUES (followerId, tweetId, userId, timestamp)
      → batch insert for efficiency

Step 3 — Real-time notification (optional):
  PUBLISH to Redis Pub/Sub "feed:{followerId}"
  WebSocket server picks up → pushes to connected client
  client shows "1 new tweet" banner
```

---

## GET Feed Flow

```
GET /feed?userId=X&cursor=null (page 1)

Step 1 — Feed Service:
  tweetIds = ZREVRANGE timeline:{userId} 0 19  ← Redis O(log n)
  
Step 2 — Hydrate:
  tweets = GET tweet:{tweetId} from Tweet Cache (Redis/Memcached)
  if cache miss → fetch from tweets table → populate cache
  
Step 3 — Return:
  {tweets: [...], nextCursor: lastTweetId}

GET /feed?userId=X&cursor=tweetId20 (page 2+)

  if page within 800:
    ZREVRANGE timeline:{userId} 20 39  ← still Redis
  else:
    SELECT * FROM feed_tweets
    WHERE feed_owner_id = userId
    AND tweet_id < cursor
    ORDER BY tweet_id DESC
    LIMIT 20
```

---

## Full Architecture Picture

```
Client
  ↓
API Gateway
  ↓
┌──────────────┬─────────────┬──────────────┐
│ Tweet Service│ Feed Service│ User Service │
└──────┬───────┴──────┬──────┴──────────────┘
       │              │
       ↓              ↓
     Kafka         Redis Cluster
       │           timeline:{userId} sorted sets
       ↓           tweet:{tweetId} content cache
  Fan-out Service
       │
       ↓
  ┌────┴────────────────────┐
  │ tweets DB  │ feed_tweets DB │ relationships DB │
  │ PostgreSQL │ Cassandra      │ PostgreSQL       │
  └────────────────────────┘
       │
       ↓
     S3 + CDN (media)
```

---

Clear on all five questions? Want to go deeper on any specific part — fan-out service, pagination, or real-time updates?


Great questions — let me answer each one precisely.

---

## 1. Is 800 tweets cached for 1B users feasible?

Short answer — **NO for all users, YES for active users.**

```
All 1B users × 800 tweets × ~50 bytes per tweetId reference:
= 1B × 800 × 50 = 40TB Redis
= ~$800K/month on AWS ElastiCache ← expensive but possible

BUT:
1B registered ≠ 1B active
Twitter DAU = ~250M (25% of registered)
MAU = ~400M

So cache only ACTIVE users:
250M DAU × 800 × 50 bytes = 10TB Redis ← manageable
```

**Three strategies to manage cache size:**

```
Strategy 1 — Cache only active users:
  If user hasn't logged in 30 days → evict from Redis
  On next login → rebuild feed from feed_tweets DB
  Redis TTL: EXPIRE timeline:{userId} 2592000 (30 days)
  Reset TTL on every login

Strategy 2 — Reduce cached tweets per user:
  Instead of 800 → cache only 200 (10 pages × 20)
  Most users never scroll past page 10
  250M × 200 × 50 bytes = 2.5TB ← very manageable

Strategy 3 — Tiered cache:
  Hot users (login daily) → Redis (fast)
  Warm users (login weekly) → Redis with smaller cache
  Cold users (login monthly) → no cache, rebuild on demand
```

**For GS trading floor:**
```
500 desks → trivially small
Even 800 tweets × 500 users = 400K entries
→ single Redis instance, no concern
```

---

## 2. Does Redis Sorted Set store descending order?

**Sorted Set always stores ascending by score internally.** But you QUERY in descending order:

```
ZADD timeline:user1 1000 tweetA  ← score=timestamp
ZADD timeline:user1 2000 tweetB
ZADD timeline:user1 3000 tweetC

Internal storage (ascending by score):
  tweetA: 1000
  tweetB: 2000
  tweetC: 3000  ← highest score = most recent

ZRANGE  timeline:user1 0 -1  → oldest first [tweetA, tweetB, tweetC]
ZREVRANGE timeline:user1 0 -1 → newest first [tweetC, tweetB, tweetA] ✓
```

**ZREVRANGE** = reverse range = descending order = newest tweets first ✓

---

## 3. feed_tweets — composite key and one-to-many

You're right — `tweet_id` alone can't be PK because one user's feed has MANY tweets.

**Composite Primary Key:**

```sql
-- In PostgreSQL:
CREATE TABLE feed_tweets (
    feed_owner_id  BIGINT,    ← whose feed
    tweet_id       BIGINT,    ← which tweet
    tweeted_by     BIGINT,    ← who wrote it
    created_at     TIMESTAMP,
    PRIMARY KEY (feed_owner_id, tweet_id)  ← COMPOSITE PK
);

-- In Cassandra:
CREATE TABLE feed_tweets (
    feed_owner_id  BIGINT,
    tweet_id       BIGINT,
    tweeted_by     BIGINT,
    created_at     TIMESTAMP,
    PRIMARY KEY (feed_owner_id, tweet_id)
) WITH CLUSTERING ORDER BY (tweet_id DESC);
```

**Why composite PK works:**
```
(feed_owner_id=1, tweet_id=100) ← unique row ✓
(feed_owner_id=1, tweet_id=200) ← different row ✓
(feed_owner_id=2, tweet_id=100) ← different row ✓

One feed_owner_id can have MANY tweet_ids ✓
Same tweet_id can appear in MANY feed_owner_ids ✓
Together they are always unique ✓
```

---

## 4. Won't querying huge DB affect response time?

Yes — this is exactly why Redis cache exists. Let me explain the full picture:

```
Page 1-10 (tweets 1-200):
→ Redis ZREVRANGE ← ~1ms, doesn't touch DB ✓

Page 11+ (tweets 201+):
→ DB query needed

Without optimization: full table scan → O(n) → slow ✗
With optimization: indexes + cursor → O(log n) → fast ✓
```

**Three optimizations for DB query speed:**

### Optimization 1 — Composite index
```sql
-- Cassandra automatically indexes by partition + clustering key
-- PostgreSQL:
CREATE INDEX idx_feed_owner_tweet 
ON feed_tweets (feed_owner_id, tweet_id DESC);

-- Query hits index directly → O(log n) not O(n)
```

### Optimization 2 — Cursor pagination (not offset)
```sql
-- WRONG (slow) — offset scans and discards rows:
SELECT * FROM feed_tweets
WHERE feed_owner_id = 1
ORDER BY tweet_id DESC
LIMIT 20 OFFSET 200;  ← scans 220 rows, returns last 20 ✗

-- RIGHT (fast) — cursor jumps directly to position:
SELECT * FROM feed_tweets
WHERE feed_owner_id = 1
AND tweet_id < 1699920000  ← cursor = last seen tweet_id
ORDER BY tweet_id DESC
LIMIT 20;  ← only scans 20 rows ✓
```

### Optimization 3 — Partitioning (answer to your Q5)

---

## 5. Partitioning by feed_owner_id + Clustering key tweet_id

This is Cassandra-specific concept. Let me explain from scratch.

**What is partitioning?**
```
Cassandra splits data across multiple nodes (servers)
Partition key determines WHICH node stores the data

feed_owner_id as partition key:
  user1's feed → Node A
  user2's feed → Node B
  user3's feed → Node A
  user4's feed → Node C

All tweets for user1 are on Node A
→ query for user1's feed hits ONE node only
→ no cross-node joins
→ fast O(1) node lookup
```

**What is clustering key?**
```
Within one partition (one user's feed):
tweet_id is the clustering key
→ tweets sorted by tweet_id DESC within each partition

Node A stores user1's data as:
  tweet_id=3000 (newest)
  tweet_id=2000
  tweet_id=1000 (oldest)

Cursor query:
  WHERE feed_owner_id=1 AND tweet_id < 2000 LIMIT 20
  → goes to Node A directly (partition key)
  → scans sorted tweets from tweet_id < 2000
  → returns next 20 instantly ✓
```

**Visual:**
```
Node A:                    Node B:
user1's feed:              user2's feed:
  tweetId=3000               tweetId=5000
  tweetId=2000               tweetId=4000
  tweetId=1000               tweetId=3500

Query for user1 → Node A only, sorted scan ✓
Query for user2 → Node B only, sorted scan ✓
Never cross-node ✓
```

---

## 6. How cursor works in REST API

**Why cursor not page number?**
```
Page number approach:
GET /feed?page=2
→ backend does OFFSET 20
→ slow (scans and discards rows)
→ if new tweet added, page 2 shifts → duplicate tweets

Cursor approach:
GET /feed?cursor=tweetId20
→ backend does WHERE tweet_id < tweetId20
→ fast (index jump)
→ new tweets don't affect existing pages
```

**Standard REST cursor pagination:**

```
Request page 1:
GET /feed
→ no cursor

Response:
{
  "tweets": [tweet1, tweet2, ... tweet20],
  "nextCursor": "tweet_id_of_last_item",  ← tweet20's id
  "hasMore": true
}

Request page 2:
GET /feed?cursor=<nextCursor from page 1>
→ backend: WHERE tweet_id < cursor LIMIT 20

Response:
{
  "tweets": [tweet21, tweet22, ... tweet40],
  "nextCursor": "tweet_id_of_last_item",
  "hasMore": true
}

Last page:
{
  "tweets": [tweet181, tweet182, ... tweet195],
  "nextCursor": null,
  "hasMore": false  ← client stops paginating
}
```

**Why tweet_id works perfectly as cursor:**
```
Snowflake ID = time ordered
tweet_id=5000 is always newer than tweet_id=4000

WHERE tweet_id < cursor
= "give me tweets older than this one"
= natural chronological pagination ✓

No ambiguity, no duplicates, no missing tweets ✓
```

---

## Summary — answers to all 5 questions

```
1. Cache 800 tweets × 1B users = 40TB → too much
   Cache only 250M DAU × 200 tweets = 2.5TB → feasible
   Evict inactive users (30 day TTL)

2. Sorted Set stores ascending internally
   ZREVRANGE queries descending → newest first ✓

3. Composite PK (feed_owner_id, tweet_id)
   One user has many tweets → feed_owner_id alone not unique
   Together always unique ✓

4. Index + cursor = O(log n) not O(n)
   Redis handles pages 1-10, DB only for deep pagination

5. Partition key = feed_owner_id → which node
   Clustering key = tweet_id DESC → order within node
   Query hits one node, scans sorted data → fast ✓

6. Cursor = last seen tweet_id passed as query param
   WHERE tweet_id < cursor → index jump → fast ✓
   nextCursor in response → client passes to next request
```

---

Clear on all 5? Want to continue with celebrity problem or move to follow/unfollow flow?

Perfect — exactly right on both. Let me validate and add depth.

---

## Celebrity Flow ✅

Your approach is correct. Let me add the details:

### Celebrity Detection
```
How do we know who is a celebrity?
  threshold = 10,000 followers (configurable)
  
  Fan-out service checks on every tweet:
    followerCount = relationships.count(WHERE user_id = tweetAuthorId)
    if followerCount > threshold → celebrity path
    else → regular fan-out path
  
  Better: pre-compute celebrity status:
    celebrities SET in Redis:
      SADD celebrities userId123
    
    Fan-out checks:
      SISMEMBER celebrities userId123 → O(1) ✓
    
    Updated when:
      user crosses 10K followers → add to SET
      user drops below 10K → remove from SET
```

### Celebrity Cache Structure
```
Key:   celebrity_tweets:{userId}
Type:  Redis Sorted Set (same as timeline)
Score: timestamp
Value: tweetId

ZADD celebrity_tweets:elonmusk 1700000000 tweetId123
ZREMRANGEBYRANK celebrity_tweets:elonmusk 0 -101  ← keep latest 100
```

### getFeed with Celebrity Merge
```
getFeed(userId):

Step 1: get precomputed feed
  tweetIds = ZREVRANGE timeline:{userId} 0 19  ← regular feed

Step 2: get followed celebrities
  celebrities = SMEMBERS following_celebrities:{userId}
  → separate set per user of celebrity followees

Step 3: get celebrity tweets
  for each celebrity:
    celebTweets = ZREVRANGE celebrity_tweets:{celebId} 0 1
    ← last 2 tweets as you said

Step 4: merge + rank
  allTweets = regularTweets + celebTweets
  sort by timestamp DESC (or ranking score)
  take top 20

Step 5: hydrate and return
```

### Ranking Logic
```
Simple chronological:
  sort by timestamp DESC ✓

Advanced ranking (Twitter-like):
  score = timestamp × recencyWeight
        + engagementCount × engagementWeight
        + relationshipStrength × relationshipWeight
  
  For GS trading floor:
    simple chronological is fine
    or severity-based: RISK_BREACH > TRADE_ALERT > PRICE_UPDATE
```

### Single-flight for celebrity cache
```
Problem:
  Celebrity tweets 50M followers
  All 50M open app → 50M requests for celebrity tweets
  → cache stampede if celebrity cache expires

Solution:
  Only ONE request hits DB when cache expires
  All other 49,999,999 wait for that one result
  → single-flight pattern (like Java CompletableFuture + ConcurrentHashMap)

  Map<String, CompletableFuture<List<Tweet>>> inFlight = new ConcurrentHashMap<>();
  
  getCelebrityTweets(celebId):
    if cache hit → return immediately
    
    future = inFlight.computeIfAbsent(celebId, 
      k -> CompletableFuture.supplyAsync(() -> fetchFromDB(celebId)))
    
    result = future.get()  ← all threads wait on same future
    inFlight.remove(celebId)
    return result
```

---

## Follow/Unfollow Flow ✅

Your defensive vs aggressive framing is exactly right.

### Follow Flow
```
POST /follow {followerId, followeeId}

Step 1: DB update
  INSERT INTO relationships (user_id, follower_id) 
  VALUES (followeeId, followerId)

Step 2: Update following_celebrities cache if celebrity
  if SISMEMBER celebrities followeeId:
    SADD following_celebrities:{followerId} followeeId

Step 3: Backfill recent tweets (your point — show new tweets)
  Option A: Immediate backfill
    get last 20 tweets of followeeId from their tweet cache
    ZADD timeline:{followerId} for each tweet
    → user sees followee's recent tweets immediately ✓
    
  Option B: Lazy backfill
    do nothing now
    next tweet from followeeId → fan-out includes followerId
    → user sees tweets from next post onwards
    → simpler but worse UX

  For GS → Option A (immediate backfill) ✓
  For Twitter → Option B (too expensive for celebrities)

Step 4: Publish FollowEvent to Kafka (for analytics)
```

### Unfollow Flow — your defensive vs aggressive
```
DELETE /follow {followerId, followeeId}

Step 1: DB update (always immediate)
  DELETE FROM relationships 
  WHERE user_id = followeeId AND follower_id = followerId

Step 2: Update celebrity cache if celebrity
  SREM following_celebrities:{followerId} followeeId

Step 3: Feed cache — defensive OR aggressive
```

**Defensive (simpler, your approach):**
```
Do nothing to feed cache
Fan-out service won't add new tweets from unfollowed user ✓
Old tweets remain until cache expires or evicted

Result:
  User sees old tweets from unfollowed person ← stale but temporary
  No new tweets from them after unfollow ✓
  Cache expires → old tweets gone naturally

When to use:
  Acceptable stale window (hours)
  High unfollow rate → aggressive too expensive
  Simpler implementation
```

**Aggressive (better UX, your approach):**
```
Step 1: Get all tweetIds from unfollowed user in feed:
  unfollowedTweets = tweets written by unfolloweeId
  
  How? Two approaches:
  
  Approach A: Scan feed cache
    ZRANGE timeline:{followerId} 0 -1  ← get all tweetIds
    for each tweetId → check if tweeted_by == unfolloweeId
    ZREM timeline:{followerId} tweetId  ← remove
    
    Problem: O(n) scan of feed cache → slow for large feeds
  
  Approach B: Separate index
    user_tweets:{unfolloweeId} → SET of their tweetIds
    
    On unfollow:
      tweetIdsToRemove = SMEMBERS user_tweets:{unfolloweeId}
      for each tweetId:
        ZREM timeline:{followerId} tweetId
    
    O(k) where k = unfollowed user's recent tweets ✓
    Much faster than scanning entire feed

When to use:
  User expects immediate clean feed
  Low unfollow rate
  GS trading floor → aggressive preferred (small scale)
```

---

## Complete Flow Summary

```
Tweet posted:
  → DB insert
  → Kafka "new_tweet"
  → Fan-out: regular users push to timeline cache + feed_tweets DB
             celebrity: update celebrity_tweets cache only

Get feed:
  → Redis timeline (precomputed, page 1-10)
  → merge celebrity tweets (ZREVRANGE celebrity_tweets)
  → rank/sort
  → hydrate from tweet cache
  → DB cursor query for deep pagination

Follow:
  → DB insert
  → backfill recent tweets to feed cache
  → add to following_celebrities if celebrity

Unfollow:
  → DB delete
  → defensive: let cache expire naturally
  → aggressive: remove unfollowed user's tweets from feed cache
```

---

## What to say in GS interview

*"Celebrity tweets stored in dedicated Redis sorted set, not fanned out to followers. getFeed merges precomputed timeline with celebrity tweets pulled at read time, ranked by timestamp or custom score. Single-flight pattern prevents cache stampede on celebrity cache expiry. Follow immediately backfills recent tweets for good UX. Unfollow either defensive (let cache expire) or aggressive (remove tweets via user_tweets index). For GS trading floor, aggressive unfollow preferred — small scale, user expects immediate clean feed."*

---

Twitter Feed System Design ✅ fully complete.

**Next — Splitwise, Snake & Ladder, or HLD?**