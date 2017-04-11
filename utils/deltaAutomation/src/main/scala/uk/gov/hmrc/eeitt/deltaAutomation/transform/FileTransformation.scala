package uk.gov.hmrc.eeitt.deltaAutomation.transform

import java.io.{ File, FileWriter, PrintWriter }
import java.nio.file.Files._
import java.nio.file.{ Files, Path, Paths, StandardCopyOption }
import java.text.SimpleDateFormat
import java.util
import java.util.Date

import com.typesafe.config.{ Config, ConfigFactory }
import com.typesafe.scalalogging.Logger
import org.apache.poi.poifs.crypt.{ Decryptor, EncryptionInfo }
import org.apache.poi.poifs.filesystem.NPOIFSFileSystem
import org.apache.poi.ss.usermodel.{ Cell, Row, Workbook, _ }
import org.apache.poi.xssf.usermodel.XSSFWorkbook

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer
import scala.util.{ Failure, Success, Try }

//TODO Rename FileImport to FileTransformation and FileImportCLI as FileImportTransformerCLI
trait FileTransformation {

  System.setProperty("LOG_HOME", getPath("/Logs"))
  var logger = Logger("FileImport")

  val currentDateTime: String = getCurrentTimeStamp
  val conf: Config = ConfigFactory.load()
  val inputFileLocation: String = getFileLocation("location.inputfile.value", "/Files/Input")
  val inputFileArchiveLocation: String = getFileLocation("location.inputfile.archive.value", "/Files/Input/Archive")
  val outputFileLocation: String = getFileLocation("location.outputfile.value", "/Files/Output")
  val badFileLocation: String = getFileLocation("location.badfile.value", "/Files/Bad")

  println("Input : " + inputFileLocation)
  println("Archive : " + inputFileArchiveLocation)
  println("Output : " + outputFileLocation)
  println("Bad : " + badFileLocation)

  val password: String = conf.getString("password.value")

  type CellsArray = Array[CellValue]

  //TODO add unit tests
  def process(
    currentDateTime: String,
    inputFileLocation: String,
    inputFileArchiveLocation: String,
    outputFileLocation: String,
    badFileLocation: String
  ): Unit = {
    val files: List[File] = getListOfFiles(inputFileLocation)
    logger.info(s"The following ${files.size} files will be processed ")
    val filesWithIndex: List[(File, Int)] = files.zipWithIndex
    for (file <- filesWithIndex) logger.info((file._2 + 1) + " - " + file._1.getAbsoluteFile.toString)
    for (file <- files if isValidFile(file.getCanonicalPath)) {
      logger.info(s"Parsing ${file.getAbsoluteFile.toString} ...")
      val workbook: Workbook = getFileAsWorkbook(file.getCanonicalPath)
      val lineList: List[RowString] = readRows(workbook)
      val linesAndRecordsAsListOfList: List[CellsArray] = lineList.map(line => line.content.split("\\|")).map(strArray => strArray.map(str => CellValue(str)))
      val userIdIndicator: CellValue = linesAndRecordsAsListOfList.tail.head.head
      val user: User = getUser(userIdIndicator)
      val (goodRowsList, badRowsList): (List[RowString], List[RowString]) = user.partitionUserAndNonUserRecords(lineList, outputFileLocation, badFileLocation, currentDateTime, file.getAbsoluteFile.getName)
      write(outputFileLocation, badFileLocation, goodRowsList, badRowsList, file.getAbsoluteFile.getName)
      logger.info("Succesful records parsed:" + goodRowsList.length)
      logger.info("Unsuccesful records parsed:" + badRowsList.length)
      Files.move(file.toPath, new File(inputFileArchiveLocation + "//" + file.toPath.getFileName).toPath, StandardCopyOption.REPLACE_EXISTING)
    }
  }

  //TODO Check if this method can be moved to FileImportCLI
  def getListOfFiles(dirName: String): List[File] = {
    val directory = new File(dirName)
    if (directory.exists && directory.isDirectory) {
      directory.listFiles.filter(_.isFile).toList
    } else {
      List[File]()
    }
  }

  def isValidFile(file: String): Boolean = {
    val path: Path = Paths.get(file)
    if (!exists(path) || !isRegularFile(path)) {
      logger.error(s"Invalid filelocation in $file - This file is not processed")
      false
    } else if (!isReadable(path)) {
      logger.error(s"Unable to read from $file - This file is not processed")
      false
    } /*else if (!Files.probeContentType(path).equals("application/vnd.ms-excel")) { //TODO this fragment can throw a null and is dangerous
      logger.error(s"Incorrent File Content in $file - The program exits")
      false
    }*/ else {
      Try(getFileAsWorkbook(file)) match {
        case Success(_) => true
        case Failure(e) => {
          logger.error(s"Incorrent File Content in $file ${e.getMessage} - This file is not processed")
          false
        }
      }
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
    val rows: util.Iterator[Row] = sheet.rowIterator()
    val rowBuffer: ListBuffer[RowString] = ListBuffer.empty[RowString]
    for (row <- rows) {
      val cells: util.Iterator[Cell] = row.cellIterator()
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

  def getCurrentTimeStamp: String = {
    val dateFormat = new SimpleDateFormat("EEEdMMMyyyy.HH.mm.ss.SSS")
    dateFormat.format(new Date)
  }

  protected def getPath(location: String): String = {
    val path = getClass.getResource(location).getPath
    if (path.contains("file:")) {
      path.drop(5)
    } else {
      path
    }
  }

  protected def write(
    outputFileLocation: String,
    badFileLocation: String,
    goodRowsList: List[RowString],
    badRowsList: List[RowString],
    fileName: String
  ): Unit = {
    writeRows(s"$badFileLocation/${fileName.replaceFirst("\\.[^.]+$", ".txt")}", badRowsList, "Incorrect Rows ")
    writeRows(s"$outputFileLocation/${fileName.replaceFirst("\\.[^.]+$", ".txt")}", goodRowsList, "Correct Rows ")
    writeMaster(s"$outputFileLocation/Master", goodRowsList, fileName.replaceFirst("\\.[^.]+$", ".txt"))
  }

  private def writeMaster(filePath: String, rowStrings: List[RowString], fileName: String): Unit = {
    val isAppend = true
    val regex = " (\\d{2})[.](\\d{2})[.]20(\\d{2})[.]".r.findFirstMatchIn(fileName) match {
      case Some(x) => x.subgroups.mkString
      case None => logger.error("")
    }
    val divider = regex
    val file = new FileWriter(filePath, isAppend)
    file.write(divider + "\n")
    rowStrings.foreach(x => file.write(x.content + "\n"))
    file.close()
  }
  private def writeRows(file: String, rowStrings: List[RowString], label: String) = {
    if (rowStrings.nonEmpty) writeToFile(new File(file), label)({ printWriter => rowStrings.foreach(rowString => printWriter.println(rowString.content)) })
  }

  private def writeToFile(f: File, label: String)(op: (PrintWriter) => Unit): Unit = {
    val writer: PrintWriter = new PrintWriter(f)
    try {
      op(writer)
      logger.info(s"The file with $label is " + f.getAbsoluteFile)
    } catch {
      case e: Throwable => logger.error(e.getMessage)
    } finally {
      writer.close()
    }
  }

  private def initialiseFiles(path: String): Unit = {
    new File(path).mkdirs()
  }

  private def getFileLocation(configValue: String, pathValue: String): String = {
    if (conf.getString(configValue).equals("DEFAULT")) {
      val path = getPath(pathValue)
      initialiseFiles(path)
      path
    } else {
      conf.getString(configValue)
    }
  }
}

class FailedInitiation extends RuntimeException
