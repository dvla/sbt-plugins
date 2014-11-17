lazy val microservicesSanbox = project in file("microservices-sandbox")

lazy val root = project.in(file(".")).aggregate(microservicesSanbox)

