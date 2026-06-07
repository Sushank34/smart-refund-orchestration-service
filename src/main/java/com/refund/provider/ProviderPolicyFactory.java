package com.refund.provider;

import com.refund.domain.Provider;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Resolves the {@link ProviderPolicy} for a given provider. */
@Component
public class ProviderPolicyFactory {

    private final Map<Provider, ProviderPolicy> policies = new EnumMap<>(Provider.class);

    public ProviderPolicyFactory(List<ProviderPolicy> allPolicies) {
        for (ProviderPolicy policy : allPolicies) {
            policies.put(policy.provider(), policy);
        }
    }

    public ProviderPolicy forProvider(Provider provider) {
        ProviderPolicy policy = policies.get(provider);
        if (policy == null) {
            throw new IllegalStateException("No policy registered for provider " + provider);
        }
        return policy;
    }
}
