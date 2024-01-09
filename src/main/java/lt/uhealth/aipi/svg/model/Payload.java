package lt.uhealth.aipi.svg.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.Map;
import java.util.stream.Collectors;

@RegisterForReflection
public record Payload(String payload, Map<String, String> responses, String proof) {

    public static Payload fromMagicItemWithNotes(MagicItemWithNotes magicItemWithNotes, String proof){

        Map<String, String> prevAnswers = magicItemWithNotes.dependsOn().values().stream()
                .filter(m -> m.answer().get() != null)
                .collect(Collectors.toMap(m -> String.valueOf(m.magicItem().index()), m -> m.answer().get().payload()));

        return new Payload(magicItemWithNotes.magicItemString(), prevAnswers, proof);
    }
}
