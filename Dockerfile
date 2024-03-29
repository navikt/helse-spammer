FROM debian:12-slim AS locale_image

RUN apt-get update && apt-get -y install locales
RUN sed -i -e 's/# nb_NO.UTF-8 UTF-8/nb_NO.UTF-8 UTF-8/' /etc/locale.gen && locale-gen

FROM gcr.io/distroless/java21-debian12:nonroot

COPY build/libs/*.jar /app/

COPY --from=locale_image /usr/lib/locale /usr/lib/locale

ENV LC_ALL="nb_NO.UTF-8"
ENV TZ="Europe/Oslo"
ENV JAVA_OPTS='-XX:MaxRAMPercentage=90'

WORKDIR /app

CMD ["app.jar"]
