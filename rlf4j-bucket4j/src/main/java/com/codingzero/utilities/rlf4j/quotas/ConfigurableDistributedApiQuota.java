package com.codingzero.utilities.rlf4j.quotas;

import com.codingzero.utilities.rlf4j.ApiIdentity;
import com.codingzero.utilities.rlf4j.ApiQuotaConfig;
import com.codingzero.utilities.rlf4j.ConfigurableApiQuota;
import com.codingzero.utilities.rlf4j.ConsumptionReport;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ConfigurableDistributedApiQuota extends ConfigurableApiQuota {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurableDistributedApiQuota.class);

    private DistributedBucketProvider greenBucketProvider;
    private DistributedBucketProvider blueBucketProvider;

    public ConfigurableDistributedApiQuota(ApiQuotaConfig config) {
        this(
                config,
                DistributedBucketProvider.builder().cacheNamePrefix("caches-green").build(),
                DistributedBucketProvider.builder().cacheNamePrefix("caches-blue").build());
    }

    public ConfigurableDistributedApiQuota(ApiQuotaConfig config,
                                           DistributedBucketProvider greenBucketProvider,
                                           DistributedBucketProvider blueBucketProvider) {
        super(config);
        this.greenBucketProvider = greenBucketProvider;
        this.blueBucketProvider = blueBucketProvider;
    }

    private Bucket getBucket(ApiIdentity identity) {
        String key = getBucketKey(identity);
        if (isGreenConfigOn()) {
            return greenBucketProvider.get(key, identity, this::getBandwidth);
        } else {
            return blueBucketProvider.get(key, identity, this::getBandwidth);
        }
    }

    abstract protected Bandwidth getBandwidth(ApiIdentity identity);

    abstract protected String getBucketKey(ApiIdentity identity);

    @Override
    protected boolean tryConsumeInternally(ApiIdentity identity, long token) {
        Bucket bucket = getBucket(identity);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(token);
        return (probe.isConsumed());
    }

    @Override
    protected ConsumptionReport tryConsumeAndRetuningReportInternally(ApiIdentity identity, long token) {
        Bucket bucket = getBucket(identity);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(token);
        if (probe.isConsumed()) {
            return ConsumptionReport.consumed(token).remainingQuota(probe.getRemainingTokens()).build();
        } else {
            return ConsumptionReport.notConsumed().build();
        }
    }

    @Override
    protected void onBlueConfigUpdate(ApiQuotaConfig config) {
        blueBucketProvider.clean();
    }

    @Override
    protected void onBlueConfigUpdateComplete(ApiQuotaConfig config) {

    }

    @Override
    protected void onGreenConfigUpdate(ApiQuotaConfig config) {
        greenBucketProvider.clean();
    }

    @Override
    protected void onGreenConfigUpdateComplete(ApiQuotaConfig config) {

    }

    @Override
    public void supplement(ApiIdentity identity, long token) {
        Bucket bucket = getBucket(identity);
        bucket.addTokens(token);
    }

}
