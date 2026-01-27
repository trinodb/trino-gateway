
## Performance Tuning

### Cache TTL (`cacheTtlSeconds`)

- **Default (1800s / 30min):** Good for typical workloads
- **Short-lived queries (<5min):** Use 600s (10min)
- **Long-running queries (hours):** Use 3600s (1 hour) or more
- **Interactive development:** Use 300s (5min)

### Health Check Interval (`healthCheckIntervalMs`)

- **Default (30000ms / 30s):** Balanced check frequency
- **Unstable network:** Increase to 60000ms (1 min)
- **Critical systems:** Decrease to 10000ms (10s)

### Connection Timeouts (`timeoutMs`)

- **Default (2000ms):** Good for local/same-datacenter Valkey
- **Cross-region:** Increase to 5000ms
- **High latency network:** Increase to 10000ms

---

## Monitoring

Valkey cache exposes the following metrics (accessible via `ValkeyDistributedCache` instance):

```java
long hits = cache.getCacheHits();
long misses = cache.getCacheMisses();
long writes = cache.getCacheWrites();
long errors = cache.getCacheErrors();
double hitRate = cache.getCacheHitRate();  // Percentage
```

### Expected Metrics (Healthy System)

- **Cache Hit Rate:** 85-95%
- **Cache Errors:** 0 (or very low)
- **Cache Writes:** ~Equal to query submission rate

### Troubleshooting

**Low Hit Rate (<70%)**
- Check TTL settings (may be too short)
- Verify Valkey isn't evicting entries (check memory)
- Check if multiple gateway versions deployed (cache key mismatch)

**High Error Rate**
- Check Valkey connectivity
- Verify password/AUTH configuration
- Review Valkey server logs

**Connection Pool Exhaustion**
- Increase `maxTotal` setting
- Check for connection leaks (should be none with try-with-resources)

---

## Security Considerations

### Production Deployment Checklist

- [ ] **Enable AUTH:** Set `password` in configuration
- [ ] **Use Environment Variables:** Don't hardcode passwords
- [ ] **Network Security:** Deploy Valkey in private VPC/network
- [ ] **Encryption at Rest:** Enable Valkey persistence encryption
- [ ] **TLS/SSL:** (Future enhancement - not yet supported)
- [ ] **Access Control:** Restrict Valkey port (6379) to gateway instances only

### Example Production Setup

```yaml
# config.yaml
valkeyConfiguration:
  enabled: true
  host: ${VALKEY_INTERNAL_HOST}
  port: 6379
  password: ${VALKEY_PASSWORD}
```

```bash
# Environment variables (set in deployment)
export VALKEY_INTERNAL_HOST=valkey.vpc.internal
export VALKEY_PASSWORD=$(vault read -field=password secret/valkey)
```

---

## Architecture

### 3-Tier Caching

```
Request Flow:
1. Check L1 (Local Guava Cache) → 10k entries, 30min TTL
   ├─ Hit: Return immediately (~1ms)
   └─ Miss: Continue to L2

2. Check L2 (Valkey Distributed Cache) → Shared across gateways
   ├─ Hit: Populate L1, return (~5ms)
   └─ Miss: Continue to L3

3. Check L3 (PostgreSQL Database) → Source of truth
   ├─ Found: Populate L2 + L1, return (~50ms)
   └─ Not Found: Search all backends via HTTP (~200ms)
```

### Cache Keys

```
Backend:        trino:query:backend:{queryId}
Routing Group:  trino:query:routinggroup:{queryId}
External URL:   trino:query:externalurl:{queryId}
```

---

## Migration Guide

### From Single Gateway to Multi-Gateway

1. **Deploy Valkey server** (standalone or cluster)
2. **Update config.yaml** on all gateways:
   ```yaml
   valkeyConfiguration:
     enabled: true
     host: valkey.internal
     port: 6379
     password: ${VALKEY_PASSWORD}
   ```
3. **Restart gateways** (rolling restart recommended)
4. **Monitor metrics** to verify cache hit rates

No data migration needed - cache will populate automatically.

---

## FAQ

**Q: Do I need Valkey if I only have one gateway?**
A: No. Local Guava cache is sufficient for single-instance deployments.

**Q: What happens if Valkey goes down?**
A: Graceful degradation - queries continue working, falling back to database. Performance may degrade slightly.

**Q: Can I use Redis instead of Valkey?**
A: Yes! Valkey is a Redis fork with compatible protocol. Just point to your Redis server.

**Q: How much memory does Valkey need?**
A: Rough estimate: `(queries per minute) × (average query lifetime in minutes) × 500 bytes`
   Example: 1000 q/min × 30 min × 500 bytes = ~15 MB

**Q: Can I clear the cache?**
A: Yes, via Valkey CLI: `redis-cli -h <host> -a <password> FLUSHDB`
   Or selectively: `redis-cli DEL trino:query:backend:*`

---

## Support

For issues or questions:
- GitHub Issues: https://github.com/trinodb/trino-gateway/issues
- Trino Community Slack: #trino-gateway channel
