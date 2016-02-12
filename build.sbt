name := "Future"

version := "1.0"

scalaVersion := "2.11.7"

resolvers += "Sonatype OSS Snapshots" at
  "https://oss.sonatype.org/content/repositories/releases"

libraryDependencies += ("org.scala-stm" %% "scala-stm" % "0.7")

libraryDependencies += ("org.scalatest" % "scalatest_2.11" % "2.2.4" % "test")

libraryDependencies += ("com.storm-enroute" %% "scalameter" % "0.7")

parallelExecution in Test := false