package org.apereo.cas.audit.spi;

import org.apereo.cas.authentication.CurrentCredentialsAndAuthentication;
import org.apereo.inspektr.common.spi.PrincipalResolver;
import org.aspectj.lang.JoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Inspektr PrincipalResolver that gets the value for principal id from Authentication object bound to a current thread of execution.
 *
 * @author Dmitriy Kopylenko
 * @since 5.0.0
 */
public class ThreadLocalPrincipalResolver implements PrincipalResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadLocalPrincipalResolver.class);

    private PrincipalIdProvider principalIdProvider;

    public ThreadLocalPrincipalResolver(final PrincipalIdProvider principalIdProvider) {
        this.principalIdProvider = principalIdProvider;
    }

    @Override
    public String resolveFrom(final JoinPoint auditTarget, final Object returnValue) {
        LOGGER.debug("Resolving principal at audit point [{}]", auditTarget);
        return getCurrentPrincipal();
    }

    @Override
    public String resolveFrom(final JoinPoint auditTarget, final Exception exception) {
        LOGGER.debug("Resolving principal at audit point [{}] with thrown exception [{}]", auditTarget, exception);
        return getCurrentPrincipal();
    }

    @Override
    public String resolve() {
        return UNKNOWN_USER;
    }

    private String getCurrentPrincipal() {
        String principal = this.principalIdProvider.getPrincipalIdFrom(CurrentCredentialsAndAuthentication.getCurrentAuthentication());
        if (principal == null) {
            principal = CurrentCredentialsAndAuthentication.getCurrentCredentialIdsAsString();
        }
        return (principal != null) ? principal : UNKNOWN_USER;
    }
}
