import sbt._
import Keys._

object Build extends Build {

  import BuildSettings._
  import Dependencies._

  lazy val root = Project( "root", file( "." ) )
    .aggregate( commons, slick, spray )
    .settings( basicSettings: _* )
    .settings( noPublishing: _* )

  lazy val commons = Project( "commons", file( "commons" ) )
    .settings( moduleSettings: _* )
    .settings( libraryDependencies ++=
      compile( config ) ++
      compile( eeioUUID ) ++
      compile( logbackclassic ) ++
      compile( sprayJson ) ++
      compile( grizzledSlf4j ) ++
      compile( joda ) ++
      compile( jodaConvert ) ++
      // compile( jscience ) ++
      // compile( sprayJson ) ++
      test( specs2 )
    )

  lazy val slick = Project( "slick", file( "slick" ) )
    .dependsOn( commons )
    .settings( moduleSettings: _* )
    .settings( libraryDependencies ++=
      compile( config ) ++
      // compile( logbackclassic ) ++
      // compile( grizzledSlf4j ) ++
      compile( slickPersistence ) ++
      compile( mysqlConnector ) ++
      // compile( joda ) ++
      // compile( jodaConvert ) ++
      // compile( jscience ) ++
      test( specs2 ) 
    )

  lazy val spray = Project( "spray", file( "spray" ) )
    .dependsOn( commons )
    .settings( moduleSettings: _* )
    .settings( libraryDependencies ++=
      compile( config ) ++
      // compile( logbackclassic ) ++
      // compile( grizzledSlf4j ) ++
      compile( sprayCan ) ++
      compile( sprayRouting ) ++
      compile( sprayJson ) ++
      // compile( joda ) ++
      // compile( jodaConvert ) ++
      // compile( jscience ) ++
      test( specs2 ) 
    )

  lazy val play = Project( "play", file( "play" ) )
    .dependsOn( commons )
    .settings( moduleSettings: _* )
    .settings( libraryDependencies ++=
      compile( config ) ++
      // compile( logbackclassic ) ++
      // compile( grizzledSlf4j ) ++
      // compile( joda ) ++
      // compile( jodaConvert ) ++
      // compile( jscience ) ++
      test( specs2 ) 
    )
}