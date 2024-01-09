package lt.uhealth.aipi.svg.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lt.uhealth.aipi.svg.util.JsonReader;

public record MagicItemWithNotes(int index,
                                 String magic,
                                 MagicItem magicItem,
                                 String magicItemString,
                                 Map<Integer, MagicItemWithNotes> dependsOn,
                                 Map<Integer, MagicItemWithNotes> dependents,
                                 AtomicReference<Long> until,
                                 AtomicBoolean pickedForRequest,
                                 AtomicReference<Answer> answer,

                                 AtomicReference<Map<Integer, MagicItemWithNotes>> allMagicItems) {

    public MagicItemWithNotes withAnswer(String answerString) {
        this.answer.set(Answer.fromAnswerString(answerString));
        return this;
    }

    public boolean isReady(){
        return dependsOn.values().stream().allMatch(m -> m.answer().get() != null);
    }

    public List<MagicItemWithNotes> findReadyDependents(){
        if (answer.get() == null){
            return List.of();
        }
        return dependents.values().stream().filter(MagicItemWithNotes::isReady).toList();
    }

    public static MagicItemWithNotes create(int index, String magic, String magicItemString) {
        int posOfFirstDot = magicItemString.indexOf('.');
        int posOfLastDot = magicItemString.lastIndexOf('.');

        String magicItemStringPart = posOfFirstDot >= 0 && posOfLastDot >= 0
                ? magicItemString.substring(posOfFirstDot + 1, posOfLastDot)
                : magicItemString;

        byte[] json = Base64.getUrlDecoder().decode(magicItemStringPart);

        MagicItem magicItem = JsonReader.readValue(json, MagicItem.class);
        return new MagicItemWithNotes(index, magic, magicItem, magicItemString,
                new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new AtomicReference<>(),
                new AtomicBoolean(false), new AtomicReference<>(), new AtomicReference<>());
    }

    public Long updateUntilRecursively(){
        return updateUntilRecursively(new HashSet<>());
    }

    Long updateUntilRecursively(Set<Integer> currentPath){
        Long currentValue = until.get();
        if (currentValue != null){
            return currentValue;
        } else if (currentPath.contains(index)){
            return null;
        }

        currentPath.add(index);

        long minUntilOfDependents = dependents.values().stream()
                .map(m -> m.updateUntilRecursively(currentPath))
                .filter(Objects::nonNull)
                .mapToLong(l -> l)
                .min()
                .orElse(Long.MAX_VALUE);

        long untilOfMyself = this.magicItem.getUntil();

        long finalUntil = Math.min(minUntilOfDependents - 50, untilOfMyself);
        until.set(finalUntil);

        currentPath.remove(index);

        return finalUntil;
    }
}
