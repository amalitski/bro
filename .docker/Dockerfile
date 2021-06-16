FROM maven:3.8-openjdk-16
WORKDIR /www

RUN ln -sf /usr/share/zoneinfo/Europe/Moscow /etc/localtime

COPY pom.xml ./
RUN mvn -B -s /usr/share/maven/ref/settings-docker.xml dependency:go-offline

COPY ./src ./src
RUN mvn -B -am -s /usr/share/maven/ref/settings-docker.xml package

COPY ./.docker/entrypoint.sh ./
ENTRYPOINT ["/www/entrypoint.sh"]
