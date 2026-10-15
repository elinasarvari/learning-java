package com.example.app.service.security;

import com.example.app.service.security.domain.EntityRestrictions;
import com.example.app.service.security.domain.RestrictedEntity;

/** Resolves row-level access policy. Lives in the business layer; never injected below it. */
public interface SecurityPolicyService {

    /**
     * Resolves what this user may see of this entity on this account.
     *
     * @return the resolved policy; {@link EntityRestrictions#none} when the user has no
     *     policy rows configured, which this application treats as unrestricted access
     * @throws AccessPolicyUnavailableException if the policy store cannot be read - never
     *     an empty restriction set (STD-SEC-005)
     */
    EntityRestrictions resolve(Long userId, Long accountId, RestrictedEntity entity);
}
