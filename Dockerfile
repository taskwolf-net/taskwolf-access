FROM alpine

COPY /build/libs/access-1.0.0-SNAPSHOT.jar access.jar
COPY /locale/ /locale/