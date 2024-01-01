package lt.uhealth.aipi.svg.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record Expected(Long after, Long before) {
}
