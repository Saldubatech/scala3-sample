# A Project Sample for Scala3 with Sbt

## Metrics, etc...

### Scala Code Build

<!-- ![Scala Build](https://github.com/Saldubatech/scala3-sample/.github/workflows/build-test.yml/badge.svg) -->

### Code Coverage

[![codecov](https://codecov.io/gh/Saldubatech/scala3-sample/graph/badge.svg?token=LXGTEWXA1T)](https://codecov.io/gh/Saldubatech/scala3-sample)


## Additional Technologies:

* [Zio](https://zio.dev/)
  * [Quill](https://zio.dev/zio-quill/getting-started/)
  * [Http](https://zio.dev/zio-http/)
* [Postgres](https://www.postgresql.org/)
* [Docker](https://www.docker.com/)
  * [Compose](https://docs.docker.com/compose/)
* [Github]()
  * [Actions](https://github.com/features/actions)
  * [Packages](https://github.com/features/packages)

## Snippets copied from 3rd parties

* [Spark Histogram Functions](https://github.com/LucaCanali/Miscellaneous/blob/master/Spark_Notes/Spark_DataFrame_Histograms.md)
* [Slick/ZIO Interop from ZIO](https://github.com/ScalaConsultants/zio-slick-interop)

## Structure

Project
: Dependencies and build capabilities following standard SBT structure

Lib
: A library that publishes a jar file to [Github Packages](https://github.com/features/packages)

App
: A [Zio](https://zio.dev/) web app that can be deployed and tested locally and packages into a "fat jar".

Image
: A simple project that creates a Docker image and publishes it to GH packages.

Root
: A Docker compose that defines, deploys and runs the system to a docker installation.

## TODO/Steps

### Documentation

1. Migrate to RestructuredText with Sphinx and [sbt-site](https://www.scala-sbt.org/sbt-site/index.html)

### Infrastructure/DevOps

1. Complete the structure as described above
   1. Populate and publish a library to GH Packages.
   2. Publish App fat jar to GH Packages (To be decided)
   3. Publish image to GH Packages
   4. Automate deployment with GH Actions
   5. Enable Local and repo based dependencies for libraries, apps and images.
2. Add a Web Layer using Nginx or similar.
3. Create deployment sub-projects that deploy the system to K8s in a structured manner using [cdk8s](https://cdk8s.io/) or [Helm](https://helm.sh/)
   1. Bootstrap: Configure Cluster, Security, Roles, etc...
   2. Infrastructure: Network, DB, Ingress/Egress, ...
   3. Application Layer.
4. Create OAM space.
5. Create Staging space and promotion logic.

### App Framework Technologies

1. Add Codecov
2. Add [Local-Action](https://github.com/github/local-action) for testing GHA
3. Add OpenApi services
4. Add gRpc services
5. Add Redis
6. Add Modular/API level Testing
