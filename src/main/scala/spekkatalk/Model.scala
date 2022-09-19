package spekkatalk

object Model {
  type Ctx = Long

  sealed trait SuperheroUniverse
  case object Marvel extends SuperheroUniverse
  case object DC extends SuperheroUniverse

  case class Movie(
      title: String,
      actors: List[String],
      universe: SuperheroUniverse,
      durationMin: Int
  )
}

object Data {
  import Model._
  // Actors
  val henryCavill = "Henry Cavill"
  val benAffleck = "Ben Affleck"
  val galGadot = "Gal Gadot"

  val robertDowneyJr = "Robert Downey Jr"
  val rayanReynolds = "Rayan Reynolds"
  val chrisHemsworth = "Chris Hamsworth"

  // Movies
  val manOfSteel = Movie("Man of Steel", List(henryCavill), DC, 143)
  val batmanVsSuperman = Movie("Batman vs Superman", List(henryCavill, benAffleck), DC, 151)
  val greenLantern = Movie("Green Lantern", List(rayanReynolds), DC, 123)
  val justiceLeague = Movie("Justice League", List(henryCavill, benAffleck, galGadot), DC, 120)

  val ironMan = Movie("Ironman", List(robertDowneyJr), Marvel, 126)
  val thor = Movie("Thor", List(chrisHemsworth), Marvel, 115)
  val theAvengers = Movie("The Avengers", List(robertDowneyJr, chrisHemsworth), Marvel, 143)
  val deadpool = Movie("Deadpool", List(rayanReynolds), Marvel, 108)

  val allMovies = List(
    manOfSteel,
    batmanVsSuperman,
    greenLantern,
    justiceLeague,
    ironMan,
    thor,
    theAvengers,
    deadpool
  )

  val dcMovies = allMovies.filter(_.universe == DC)
  val marvelMovies = allMovies.filter(_.universe == Marvel)
}
