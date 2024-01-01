package lt.uhealth.aipi.svg.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lt.uhealth.aipi.svg.util.JsonReader;

@RegisterForReflection
public record Answer(boolean success, String payload) {

    public static Answer fromAnswerString(String answerString){
        return JsonReader.readValue(answerString, Answer.class);
    }
}
