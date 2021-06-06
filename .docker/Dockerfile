FROM maven:3.8-openjdk-16
WORKDIR /www

COPY pom.xml ./
RUN mvn -B -s /usr/share/maven/ref/settings-docker.xml dependency:go-offline

COPY ./src ./src
RUN mvn -B -am -s /usr/share/maven/ref/settings-docker.xml package

ENTRYPOINT ["java", "-jar", "/www/target/bro.jar"]
