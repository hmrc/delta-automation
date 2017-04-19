package uk.gov.hmrc.eeitt.deltaAutomation.transform

import java.io._
import java.nio.file.Files._
import java.nio.file.{ Files, Path, Paths, StandardCopyOption }
import java.text.SimpleDateFormat
import java.util.Date

import com.typesafe.scalalogging.Logger
import org.apache.poi.poifs.crypt.{ Decryptor, EncryptionInfo }
import org.apache.poi.poifs.filesystem.NPOIFSFileSystem
import org.apache.poi.ss.usermodel.{ Row, Workbook, _ }
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import uk.gov.hmrc.eeitt.deltaAutomation.extract.GMailService._

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.language.implicitConversions
import scala.util.{ Failure, Success, Try }

trait FileTransformation extends Locations with Writer with DataValidation {

  System.setProperty("LOG_HOME", getPath("/Logs"))

  var logger = Logger("FileImport")

  val currentDateTime: String = {
    val dateFormat = new SimpleDateFormat("EEEdMMMyyyy.HH.mm.ss.SSS")
    dateFormat.format(new Date)
  }

  val password: String = conf.getString("password.value")

  type CellsArray = Array[CellValue]

  def process(
    currentDateTime: String,
    inputFileLocation: String,
    inputFileArchiveLocation: String,
    outputFileLocation: String,
    badFileLocation: String,
    implementation: List[File] => Unit
  ): Unit = {
    val files: List[File] = getListOfFiles(inputFileLocation)
    logger.info(s"The following ${files.size} files will be processed ")
    implementation(files)
  }

  def manualImplementation(files: List[File]): Unit = {
    val filesWithIndex: List[(File, Int)] = files.zipWithIndex
    filesWithIndex.foreach(x => logger.info((x._2 + 1) + " - " + x._1.getAbsoluteFile.toString))
    filterValid(files, inputFileArchiveLocation, isValidFile).map { file =>
      logger.info(s"Parsing ${file.getAbsoluteFile.toString} ...")
      val workbook: Workbook = getFileAsWorkbook(file.getCanonicalPath)
      val lineList: List[RowString] = readRows(workbook)
      val linesAndRecordsAsListOfList: List[CellsArray] = lineList.map(line => line.content.split("\\|")).map(strArray => strArray.map(str => CellValue(str)))
      val userIdIndicator: CellValue = linesAndRecordsAsListOfList.tail.head.head
      val user: User = getUser(userIdIndicator)
      val (goodRowsList, badRowsList, ignoredRowsList): (List[RowString], List[RowString], List[RowString]) = user.partitionUserNonUserAndIgnoredRecords(lineList)
      badRowsList match {
        case Nil =>
          write(outputFileLocation, badFileLocation, goodRowsList, badRowsList, ignoredRowsList, file.getAbsoluteFile.getName)
          logger.debug(s"The file ${file.getAbsoluteFile.toString} is successfully parsed and written to the file")
        case _ =>
          write(outputFileLocation, badFileLocation, List.empty[RowString], badRowsList, ignoredRowsList, file.getAbsoluteFile.getName)
          logger.info(s"The file ${file.getAbsoluteFile.toString} has incorrect rows. The file is rejected.")
      }
      logger.info("Total number of records :" + (lineList.length - 1))
      logger.info("Successful records :" + goodRowsList.length)
      logger.info("Unsuccessful records :" + badRowsList.length)
      logger.info("Ignored records :" + ignoredRowsList.length)
      Files.move(file.toPath, new File(inputFileArchiveLocation + "//" + file.toPath.getFileName).toPath, StandardCopyOption.REPLACE_EXISTING)
    }
  }

  def automatedImplementation(files: List[File]): Unit = {
    val filesWithIndex: List[(File, Int)] = files.zipWithIndex
    filesWithIndex.foreach(x => logger.info((x._2 + 1) + " - " + x._1.getAbsoluteFile.toString))
    for { file <- files if isValidFile(file.getCanonicalPath) } yield {
      logger.info(s"Parsing ${file.getAbsoluteFile.toString} ...")
      val workbook: Workbook = getFileAsWorkbook(file.getCanonicalPath)
      val lineList: List[RowString] = readRows(workbook)
      val linesAndRecordsAsListOfList: List[CellsArray] = lineList.map(line => line.content.split("\\|")).map(strArray => strArray.map(str => CellValue(str)))
      val userIdIndicator: CellValue = linesAndRecordsAsListOfList.tail.head.head
      val user: User = getUser(userIdIndicator)
      val (goodRowsList, badRowsList, ignoredRowsList): (List[RowString], List[RowString], List[RowString]) = user.partitionUserNonUserAndIgnoredRecords(lineList)
      badRowsList match {
        case Nil =>
          write(outputFileLocation, badFileLocation, goodRowsList, badRowsList, ignoredRowsList, file.getAbsoluteFile.getName)
          logger.debug(s"The file ${file.getAbsoluteFile.toString} is successfully parsed and written to the file")
        case _ =>
          write(outputFileLocation, badFileLocation, List.empty[RowString], badRowsList, ignoredRowsList, file.getAbsoluteFile.getName)
          logger.info(s"The file ${file.getAbsoluteFile.toString} has incorrect rows. The file is rejected.")
      }
      if (isGoodData(goodRowsList, file.getCanonicalPath)) {
        logger.info("Total number of records :" + (lineList.length - 1))
        logger.info("Successful records :" + goodRowsList.length)
        logger.info("Unsuccessful records :" + badRowsList.length)
        logger.info("Ignored records :" + ignoredRowsList.length)
        Files.move(file.toPath, new File(inputFileArchiveLocation + "//" + file.toPath.getFileName).toPath, StandardCopyOption.REPLACE_EXISTING)
        if (isSuccessfulTransformation(file.getName.replaceFirst("\\.[^.]+$", ".txt"))) {
          sendSuccessfulResult(user)
        } else {
          sendError()
        }
      } else {
        sendError()
      }
    }
  }

  def isSuccessfulTransformation(fileName: String): Boolean = {
    val file = new File(getPath("/Files/Output"))
    if (file.exists && file.isDirectory) {
      val fileList = file.listFiles.filter(thing => thing.isFile).toList
      fileList.exists(f => f.getName == fileName)
    } else {
      false
    }
  }

  def getListOfFiles(dirName: String): List[File] = {
    val directory = new File(dirName)
    if (directory.exists && directory.isDirectory) {
      directory.listFiles.filter(_.isFile).toList
    } else {
      List[File]()
    }
  }

  def getFileAsWorkbook(fileLocation: String): XSSFWorkbook = {
    val fs = new NPOIFSFileSystem(new File(s"$fileLocation"), true)
    val info = new EncryptionInfo(fs)
    val d: Decryptor = Decryptor.getInstance(info)

    if (!d.verifyPassword(password)) {
      println("unable to process document incorrect password")
    }
    val wb: XSSFWorkbook = new XSSFWorkbook(d.getDataStream(fs))
    wb
  }

  def readRows(workBook: Workbook): List[RowString] = {
    val sheet: Sheet = workBook.getSheetAt(0)
    val maxNumOfCells: Short = sheet.getRow(0).getLastCellNum
    val rows: Iterator[Row] = sheet.rowIterator().asScala
    val rowBuffer: ListBuffer[RowString] = ListBuffer.empty[RowString]
    rows.foreach { row =>
      val listOfCells: IndexedSeq[String] = for { cell <- 0 to maxNumOfCells } yield {
        Option(row.getCell(cell)).map(_.toString).getOrElse("")
      }
      rowBuffer += RowString(listOfCells.mkString("|"))
    }
    rowBuffer.toList
  }

  def getUser(userIdIndicator: CellValue): User = {
    userIdIndicator.content match {
      case BusinessUser.name => BusinessUser
      case AgentUser.name => AgentUser
      case _ => UnsupportedUser
    }
  }

  private def isValidFile(file: String): Boolean = {
    val path: Path = Paths.get(file)
    if (!exists(path) || !isRegularFile(path)) {
      logger.error(s"Invalid filelocation in $file - This file is not processed")
      false
    } else if (!isReadable(path)) {
      logger.error(s"Unable to read from $file - This file is not processed")
      false
    } /*else if (!Files.probeContentType(path).equals("application/vnd.ms-excel")) { //TODO this fragment can throw a null
      logger.error(s"Incorrent File Content in $file - The program exits")
      false
    }*/ else {
      Try(getFileAsWorkbook(file)) match {
        case Success(_) => true
        case Failure(e) =>
          logger.error(s"Incorrent File Content in $file ${e.getMessage} - This file is not processed")
          false
      }
    }
  }
}
