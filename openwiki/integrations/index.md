# Files

- [Runtime Dependencies: Vendored Jars, JDK Compatibility, and the Missing Build](runtime-dependencies.md) - How Tmall_SSH declares and integrates its dependencies without a build tool: the 65 vendored jars in web/WEB-INF/lib grouped by purpose, the exact framework versions the source requires, the JDK 8/11/17 workarounds (javax.annotation-api, jaxb-api, ASM 9.7.1, programmatic add-opens), the classpath duplication that forces StartJetty's addSystemClass list, how to add a dependency, and how to detect or repair a damaged jar.
