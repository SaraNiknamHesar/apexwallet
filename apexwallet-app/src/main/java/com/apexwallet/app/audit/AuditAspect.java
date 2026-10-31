package com.apexwallet.app.audit;

import com.apexwallet.app.identity.CurrentUser;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Aspect
@Component
public class AuditAspect {

    private final AuditSink sink;
    private final Clock clock;

    public AuditAspect(AuditSink sink, Clock clock) {
        this.sink = sink;
        this.clock = clock;
    }

    @AfterReturning("@annotation(audited)")
    public void record(Audited audited) {
        String actor = "anonymous";
        try {
            actor = CurrentUser.require().username();
        } catch (RuntimeException ignored) {
            actor = "anonymous";
        }
        sink.append(audited.action(), actor, clock.instant(), MDC.get("correlation_id"));
    }
}
