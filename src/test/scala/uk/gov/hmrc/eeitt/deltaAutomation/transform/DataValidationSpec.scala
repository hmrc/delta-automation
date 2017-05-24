package uk.gov.hmrc.eeitt.deltaAutomation.transform

import java.io.File

import com.typesafe.scalalogging.Logger
import org.scalatest.{FlatSpec, Matchers}

class DataValidationSpec extends FlatSpec with Matchers {

  val dataVal = new DataValidation {

    override def reader = new Reader {
      val logger: Logger = Logger("validation Spec Reader ")
    }

    override def logger = Logger("Data Valid Spec")

    override def locations = Locations

    override val password: String = Locations.conf.getString("password.value")

  }


  "Business.txt File" should "Match expected result" in {
    val file = getClass.getResource("/Business.txt").getPath
    val user = BusinessUser
    val result = dataVal.getActualUniqueUserCount(dataVal.getData(file, user), user)
    result shouldBe 15
  }

  "Business.txt File" should "Not match expected result" in {
    val file = getClass.getResource("/Business.txt").getPath
    val user = BusinessUser
    val result = dataVal.getActualUniqueUserCount(dataVal.getData(file, user), user)
    result should not be 12
  }
}
