lazy val microservicesSanbox = project in file("microservices-sandbox")

lazy val buildDetailsGenerator = project in file("build-details-generator")

lazy val root = project.in(file("."))
  .aggregate(microservicesSanbox)
  .aggregate(buildDetailsGenerator)

