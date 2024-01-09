package lt.uhealth.aipi.svg.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lt.uhealth.aipi.svg.util.JsonReader;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RegisterForReflection
public record RestError(String name, String message, List<Issue> issues) {

    public boolean isTooLate(){
        return issues != null && issues.stream().anyMatch(Issue::isTooLate);
    }

    public boolean isTooEarly(){
        return issues != null && issues.stream().anyMatch(Issue::isTooEarly);
    }

    public boolean isMissingProof(){
        return issues != null && issues.stream().anyMatch(i -> i.getProofMessage() != null);
    }

    public String getProofMessage(){
        if (issues == null){
            return null;
        }

        return issues.stream()
                .map(Issue::getProofMessage)
                .filter(Objects::nonNull)
                .findAny()
                .map(m -> {
                    int pos = m.indexOf(": ");
                    return pos >= 0 ? m.substring(pos + 2) : m;
                })
                .orElse(null);
    }

    public Long tooEarlyByMillis(){
        if (issues == null){
            return null;
        }

        return issues.stream()
                .filter(Issue::isTooEarly)
                .map(Issue::tooEarlyByMillis)
                .filter(Objects::nonNull)
                .findAny()
                .orElse(null);
    }

    public static RestError fromErrorString(String errorString){
        return JsonReader.readValue(errorString, RestError.class);
    }

    public boolean isMissingDependencies(){
        return issues != null && issues.stream().anyMatch(Issue::isMissingDependency);
    }

    public Set<Integer> getMissingDependencies(){
        if (issues == null){
            return Set.of();
        }

        return issues.stream()
                .filter(Issue::isMissingDependency)
                .map(Issue::getMissingDependency)
                .collect(Collectors.toSet());
    }
}
