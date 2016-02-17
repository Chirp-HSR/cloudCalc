FROM java:8

WORKDIR /

ADD target/VSSNetworkLab-1.0-SNAPSHOT.jar /app/cloudsum.jar

ADD target/lib /app/lib/

CMD [ "java", "-jar", "/app/cloudsum.jar", "-s", "tcp://*:8080" ]

EXPOSE 8080