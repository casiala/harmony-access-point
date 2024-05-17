package eu.domibus.core.security;

import eu.domibus.api.util.AOPUtil;
import eu.domibus.common.ErrorCode;
import eu.domibus.core.crypto.spi.model.AuthorizationException;
import eu.domibus.core.ebms3.EbMS3ExceptionBuilder;
import eu.domibus.core.exception.CoreServiceExceptionInterceptor;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import static eu.domibus.core.security.DomibusAuthorizationError.*;

/**
 * @author Thomas Dussart
 * @since 4.1
 * <p>
 * Interceptor in charge of converting authentication spi exceptions into crypto exceptions.
 */
@Aspect
@Component
public class AuthorizationServiceInterceptor extends CoreServiceExceptionInterceptor {

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(AuthorizationServiceInterceptor.class);

    public AuthorizationServiceInterceptor(AOPUtil aopUtil) {
        super(aopUtil);
    }

    @Around(value = "execution(public * eu.domibus.core.security.AuthorizationServiceImpl.*(..))")
    @Override
    public Object intercept(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        if (LOG.isTraceEnabled()) {
            for (Object arg : args) {
                LOG.trace("Method argument:[{}]", arg);
            }
        }
        return super.intercept(joinPoint);
    }

    @Override
    public Exception convertCoreException(Exception e) {
        if (e instanceof AuthorizationException) {
            AuthorizationException a = (AuthorizationException) e;
            LOG.trace("Converting Authorization exception: [{}] into EBMSException", a.getClass(), e);
            if (a.getAuthorizationError() != null) {
                switch (a.getAuthorizationError()) {
                    case INVALID_FORMAT:
                        LOG.error("Invalid incoming message format during authorization: [{}]", a.getMessage(), e);
                        return EbMS3ExceptionBuilder.getInstance()
                                .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0001)
                                .message(a.getMessage())
                                .refToMessageId(a.getMessageId())
                                .build();
                    case AUTHORIZATION_TOKEN_ERROR:
                    case AUTHORIZATION_REJECTED:
                        LOG.error("Authorization for incoming message was not granted: [{}]", a.getMessage(), e);
                        return EbMS3ExceptionBuilder.getInstance()
                                .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0004)
                                .message(A0001.toString())
                                .refToMessageId(a.getMessageId())
                                .build();
                    case AUTHORIZATION_MODULE_CONFIGURATION_ISSUE:
                    case AUTHORIZATION_SYSTEM_DOWN:
                        LOG.error("System down in the authorization module: [{}]", a.getMessage(), e);
                        return EbMS3ExceptionBuilder.getInstance()
                                .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0004)
                                .message(A0004.toString())
                                .refToMessageId(a.getMessageId())
                                .build();
                    case AUTHORIZATION_CONNECTION_REJECTED:
                        LOG.error("Connection credential to Authorization was rejected: [{}]", a.getMessage(), e);
                        return EbMS3ExceptionBuilder.getInstance()
                                .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0004)
                                .message(A0002.toString())
                                .refToMessageId(a.getMessageId())
                                .build();
                    case TECHNICAL_ISSUE:
                        LOG.error("The authorization module has technical problems: [{}]", a.getMessage(), e);
                        return EbMS3ExceptionBuilder.getInstance()
                                .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0004)
                                .message(A0003.toString())
                                .refToMessageId(a.getMessageId())
                                .build();
                    default:
                        LOG.error("Unknown authorization error:[{}]", a.getAuthorizationError());
                }
            }
        }
        LOG.error("Authorization module unforeseen error: [{}]", e.getMessage(), e);
        return EbMS3ExceptionBuilder.getInstance()
                .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0004)
                .message(A0003.toString())
                .build();
    }

    @Override
    public DomibusLogger getLogger() {
        return LOG;
    }
}
