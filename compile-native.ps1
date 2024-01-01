docker run --rm -it -v ${pwd}:/workspace -v ${home}\.m2:/home/quarkus/.m2 -w /workspace `
        --entrypoint ./mvnw `
        quay.io/quarkus/ubi-quarkus-mandrel-builder-image:23.1-java21 `
        clean package -Pnative
