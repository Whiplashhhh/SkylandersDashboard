# syntax=docker/dockerfile:1
#
# Image du serveur du dashboard (Spring Boot + frontend Vue dans le meme JAR,
# FORMAT.md 7.3). Contexte de build : la racine du depot SkylandersDashboard,
# car le POM embarque catalog.json, villains.json et exclusions.txt qui vivent
# a cote de server/ (CLAUDE.md, « Organisation »).
#
# Les visuels sous copyright Activision ne sont JAMAIS copies dans l'image
# (SPEC.md 10.3) : le dossier images/ est monte en volume au demarrage.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Couche de dependances : tant que ces fichiers ne changent pas, le cache Maven
# et npm restent valides.
COPY server/pom.xml server/pom.xml
COPY server/frontend/package.json server/frontend/package-lock.json server/frontend/

COPY catalog.json villains.json exclusions.txt ./
COPY server ./server

# Les tests unitaires restent a lancer hors image (`mvn test`) : une image qui
# se construit doit pouvoir l'etre sans base de donnees.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -f server/pom.xml -DskipTests clean package


FROM eclipse-temurin:21-jre AS runtime

RUN useradd --system --create-home --uid 10001 skylanders
WORKDIR /app
COPY --from=build /build/server/target/skylanders-server-*.jar /app/skylanders-server.jar

# Dossier de montage des visuels. Absent ou vide, l'application reste pleinement
# utilisable : le serveur genere un badge de repli (SPEC.md 10.3).
ENV IMAGES_DIR=/images
RUN install -d -o skylanders -g skylanders /images

USER skylanders
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/skylanders-server.jar"]
