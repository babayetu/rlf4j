package com.codingzero.utilities.rlf4j.quotas;

import com.codingzero.utilities.rlf4j.ApiIdentity;
import com.codingzero.utilities.rlf4j.ConsumptionReport;
import com.codingzero.utilities.rlf4j.RateLimitQuota;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.ConsumptionProbe;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class LocalRateLimitQuota implements RateLimitQuota {

    private static final int DEFAULT_BUCKETS_SIZE = 25;

    private Map<String, Bucket> buckets;
    private Lock lock = new ReentrantLock();

    public LocalRateLimitQuota() {
        this.buckets = new HashMap<>(DEFAULT_BUCKETS_SIZE);
    }

    @Override
    public boolean tryConsume(ApiIdentity identity, long token) {
        return false;
    }

    @Override
    public boolean isConsumptionReportSupported() {
        return true;
    }

    @Override
    public ConsumptionReport tryConsumeAndRetuningReport(ApiIdentity identity, long token) {
        Bucket bucket = getBucket(identity);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(token);
        if (probe.isConsumed()) {
            return ConsumptionReport.consumed(token).remainingQuota(probe.getRemainingTokens()).build();
        } else {
            return ConsumptionReport.notConsumed().build();
        }
    }

    abstract protected Bandwidth getBandwidth(ApiIdentity identity);

    abstract protected String getBucketKey(ApiIdentity identity);

    private Bucket getBucket(ApiIdentity identity) {
        String key = getBucketKey(identity);
        Bucket bucket = buckets.get(key);
        if (Objects.isNull(bucket)) {
            lock.lock();
            try {
                //try to get one for other thread create a bucket with the same key.
                bucket = buckets.get(key);
                if (!Objects.isNull(bucket)) {
                    return bucket;
                }
                Bandwidth limit = getBandwidth(identity);
                bucket = createBucket(limit);
                buckets.put(key, bucket);
            } finally {
                lock.unlock();
            }
        }
        return bucket;
    }

    private Bucket createBucket(Bandwidth limit) {
        return Bucket4j.builder().addLimit(limit).build();
    }

}
