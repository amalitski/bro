[![test](https://github.com/amalitski/bro/actions/workflows/test.yml/badge.svg)](https://github.com/amalitski/bro/actions/workflows/test.yml)
[![SAST](https://github.com/amalitski/bro/actions/workflows/sast.yml/badge.svg)](https://github.com/amalitski/bro/actions/workflows/sast.yml)
[![build](https://github.com/amalitski/bro/actions/workflows/build.yml/badge.svg)](https://github.com/amalitski/bro/actions/workflows/build.yml)

![Build Status](./docs/head-logo.png)


This project provides an extendable Java application for creating development and testing workflow. Bender (Bender Rodrigues aka BRo) handles the complex plumbing and provides the interfaces necessary for many aspects of SDLC.


# Local Development/Testing

- Copy SSH private key (for git repo) to `.docker/git_key.private` 
- Run `mvn spring-boot:run` or docker-compose `docker-compose up -d`


# Health check

`curl -v http://localhost:8080/bro/health/liveness`

# Contributing

Features and bug fixes are welcome
