## Celerio Code Generator

[![Build Status](https://travis-ci.org/jaxio/celerio.svg)](https://travis-ci.org/jaxio/celerio)

Celerio is a code generator tool for data-driven application. It can reverse a database schema and generate advanced CRUD-based applications.

* [Celerio Documentation](http://www.jaxio.com/documentation/celerio)

## How to use it ?

Have Java 25 and Maven 3.9.9 (or use the Maven Wrapper) installed?

Simply execute:

    ./mvnw com.jaxio.celerio:bootstrap-maven-plugin:bootstrap

On Windows:

    mvnw.cmd com.jaxio.celerio:bootstrap-maven-plugin:bootstrap

## Build notes

The legacy JiBX code generation steps are now disabled by default for modern JDK compatibility.
If you need to run those legacy JiBX steps explicitly, enable:

    -DlegacyJibxCodegen=true

Recommended local validation command:

    ./mvnw -Denforcer.skip=true verify -DskipITs

If you are validating with a temporary JDK lower than 25, override `java.release` explicitly (for example `-Djava.release=23`).

Notes:

* A small set of JiBX-dependent tests are excluded in `celerio-engine` because legacy JiBX bindings are not compatible with modern JDKs.
* The `-Pit` integration profile still requires the legacy JiBX binding pipeline and currently fails on modern JDKs.

## Related projects

### Code generation templates

 * [Web App Angular 2 + PrimeNG + Spring Boot](https://github.com/jaxio/celerio-angular-quickstart)
 * [Java EE 7 Web App frontend and backend: No Spring Inside](https://github.com/jaxio/javaee-lab)
 * [backend: JPA 2 / Hibernate + Lucene + Spring](https://github.com/jaxio/pack-backend-jpa)
 * [frontend: JSF 2 + Spring](https://github.com/jaxio/pack-jsf2-spring-conversation)

### JPA Query By Example API

 * [JPA Query By Example API](https://github.com/jaxio/jpa-query-by-example)

## License

Celerio is released under version 2.0 of the [Apache License][].

[Apache License]: http://www.apache.org/licenses/LICENSE-2.0

## Thanks

EJ Technologies has offered us a free open source project license for JProfiler, a must have [Java Profiler](http://www.ej-technologies.com/products/jprofiler/overview.html).
