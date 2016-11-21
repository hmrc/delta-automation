package uk.gov.hmrc.eeitt

import uk.gov.hmrc.eeitt.model.{ Arn, EtmpAgent, EtmpBusinessUser, Postcode, RegistrationNumber }

import scala.util.Random

trait EtmpFixtures {
  def testEtmpAgent() = {
    def randomize(s: String) = s + "-" + Random.alphanumeric.take(10).mkString
    EtmpAgent(
      Arn(randomize("arn")),
      randomize("identificationType"),
      randomize("identificationTypeDescription"),
      randomize("organisationType"),
      randomize("organisationTypeDescription"),
      Some(randomize("organisationName")),
      Some(randomize("title")),
      Some(randomize("name1")),
      Some(randomize("name2")),
      Some(Postcode(randomize("postcode"))),
      randomize("countryCode"),
      customers = Seq()
    )
  }

  def testEtmpBusinessUser() = {
    def randomize(s: String) = s + "-" + Random.alphanumeric.take(10).mkString
    EtmpBusinessUser(
      RegistrationNumber(randomize("registrationNumber")),
      randomize("taxRegime"),
      randomize("taxRegimeDescription"),
      randomize("organisationType"),
      randomize("organisationTypeDescription"),
      Some(randomize("organisationName")),
      Some(randomize("customerTitle")),
      Some(randomize("customerName1")),
      Some(randomize("customerName2")),
      Some(Postcode(randomize("postcode"))),
      randomize("countryCode")
    )
  }

}