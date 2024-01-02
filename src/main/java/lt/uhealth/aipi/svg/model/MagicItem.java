package lt.uhealth.aipi.svg.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RegisterForReflection
public record MagicItem (String magicString,
                        Integer index,
                        Integer total,
                        Long epoch,
                        String seed,
                        String magic,
                        List<Barrier> barriers,
                        Long iat){

    public boolean isIndependent(){
        return Stream.ofNullable(barriers)
                .flatMap(Collection::stream)
                .allMatch(b -> b.getDependencies().isEmpty());
    }

    public Set<Integer> getDependencies(){
        return Stream.ofNullable(barriers)
                .flatMap(Collection::stream)
                .flatMap(b -> b.getDependencies().stream())
                .collect(Collectors.toSet());
    }

}
