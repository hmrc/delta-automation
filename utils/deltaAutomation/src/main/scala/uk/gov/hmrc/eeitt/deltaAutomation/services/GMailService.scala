package uk.gov.hmrc.eeitt.deltaAutomation.services

import java.io.{ByteArrayOutputStream, File, FileOutputStream}

import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64
import com.google.api.services.gmail.model._

import scala.collection.JavaConverters._
import scala.language.implicitConversions

object GMailService extends GMailHelper {

  def onNotification() : Unit = {
    val userId : String = "me"
    val messages : ListMessagesResponse = gMailService.users().messages().list(userId).execute()
    messages.getMessages.asScala.foreach { x =>
      val id : String = getMessageId(x)
      if (isValidEmail(id)) {
        pullDownAttachments(id)
      }
    }
  }

  def pullDownAttachments(id: String) : Unit = {
    val userId : String = "me"
    val message : Message = gMailService.users().messages().get(userId, id).execute()
    val parts : List[MessagePart] = message.getPayload.getParts.asScala.toList
    for (part <- parts) {
      if (part.getFilename != null && part.getFilename.length > 0) {
        val fileName : String = part.getFilename
        val attId : String = part.getBody.getAttachmentId
        val attachPart : MessagePartBody  = gMailService.users().messages().attachments().get(userId, id, attId).execute()
        val base64Url: Base64 = new Base64(true)
        val fileByteArray : Array[Byte] = base64Url.decode(attachPart.getData)
        val fileOutFile : FileOutputStream = new FileOutputStream("~/Downloads/delta/input/" + fileName) //Home/pi/something/input/filename
        fileOutFile.write(fileByteArray)
        fileOutFile.close()
      }
    }
    markMessageAsRead(id)
  }

  def sendResult() : Unit = {
    val logFile = new File("DELTAAUTO_HOME_IS_UNDEFINED/logs/audit.log")
    val masterFile = new File("Master")
    val buffer = new ByteArrayOutputStream()
    val logMessage = createDeltaMessage(logFile)
    val masterMessage = createDeltaMessage(masterFile)
    logMessage.writeTo(buffer)
    masterMessage.writeTo(buffer)
    val bytes = buffer.toByteArray
    val encodedEmail = Base64.encodeBase64URLSafeString(bytes)
    val message = new Message
    message.setRaw(encodedEmail)
    val result = gMailService.users().messages().send("me", message).execute()
  }
}
