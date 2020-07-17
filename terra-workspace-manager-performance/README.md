This is the source of workspace manager performance tests.

It is structured as a subproject with the ability to reuse core Java artifacts from the root project and 
can be extended to use other types of Java-based load test frameworks.

At the moment, Scala-based Gatling load test framework was being used.

There are two strategies to orchestrate these tests:

1) Skaffold/Helm CI/CD as described in local-perf
2) GitHub Actions CI/CD as described in .github/workflows/performance-test.yml