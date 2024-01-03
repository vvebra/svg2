package lt.uhealth.aipi.svg.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record Barrier (String type,
                       Long from,
                       Long until,
                       List<Integer> on){

    List<Integer> getDependencies(){
        return "dependency".equals(type) && on != null ? on : List.of();
    }

    Long getUntil(){
        return "time".equals(type) ? until : null;
    }
}
