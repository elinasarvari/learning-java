---
applyTo: '**/pom.xml'
description: 'Rules for changing dependency versions in Maven poms'
---

# Changing dependency versions

- Before editing a version, find what sets it: an explicit `<version>`, a `<properties>` entry, the
  parent (`spring-boot-starter-parent`) or an imported BOM. Change that **lever**. Don't add a
  `<version>` to a managed artifact; it silently forks that artifact from the rest of the platform.
- A Spring Boot major/minor bump moves Spring Framework, Jackson, Hibernate, Tomcat, JUnit and others
  with it. Treat it as a platform upgrade and use the `dep-upgrade` workflow
  (`/dep-upgrade`, procedures in `.github/skills/dep-upgrade/`).
- Libraries the Boot parent doesn't manage (Camel BOM, springdoc, Testcontainers...) must be
  bumped to a version built for the new Boot line. Keep the compatibility comment next to
  `camel.version` accurate.
- Verify with `./mvnw -B clean verify` (wrapper, never system `mvn`). Never skip or disable tests to get green.
