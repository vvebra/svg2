package lt.uhealth.aipi.svg.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record Params (Expected expected, Long actual) {
}
