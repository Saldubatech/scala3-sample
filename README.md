# A Project Sample for Scala3 with Sbt

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

1. Add OpenApi services
2. Add gRpc services
3. Add Redis
4. Add Modular/API level Testing
