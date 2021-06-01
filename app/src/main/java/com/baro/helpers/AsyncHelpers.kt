package com.baro.helpers

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.baro.constants.CategoryEnum
import com.baro.constants.FileEnum
import com.baro.constants.JSONEnum
import com.baro.helpers.interfaces.*
import com.baro.helpers.interfaceweaks.OnCreatorCourseCredentialsLoad
import com.baro.models.Country
import com.baro.models.Course
import com.baro.models.User
import com.baro.ui.share.p2p.WifiDirectEndpoint
import org.json.JSONArray
import java.io.*
import java.lang.ref.WeakReference
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


class AsyncHelpers {
    /**
     * With this class you can call the following methods:
     * VerifyUserCredentials(callback)
     */

    ///////////////////////////////////////////////////////////////////////////////////////////////

    class VerifyUserCredentials(private var callback: OnUserLoginCheckComplete) :
        AsyncTask<File?, Void?, User?>() {
        @RequiresApi(api = Build.VERSION_CODES.O)


        override fun doInBackground(vararg externalFilesDir: File?): User? {

            val userMetaDataPath = Paths.get(
                externalFilesDir[0].toString(),
                FileEnum.USER_DIRECTORY.key,
                FileEnum.META_DATA_FILE.key
            )


            val userThumbnailPath = Paths.get(
                externalFilesDir[0].toString(),
                FileEnum.USER_DIRECTORY.key,
                FileEnum.PHOTO_THUMBNAIL_FILE.key
            )

            val userMetaDataFile = FileHelper.getFileAtPath(userMetaDataPath)
            val userThumbnailFile = FileHelper.getFileAtPath(userThumbnailPath)

            var user: User? = null

            if (userMetaDataFile != null) {
                val contentMetaData = FileHelper.readFile(userMetaDataFile)
                val jsonMetaData = contentMetaData?.let { JSONHelper.createJSONFromString(it) }

                user = if (userThumbnailFile != null && userThumbnailFile.length() > 0) {
                    User(
                        UUID.fromString(jsonMetaData?.get(JSONEnum.USER_UUID_KEY.key) as String),
                        jsonMetaData[JSONEnum.USER_NAME_KEY.key] as String,
                        userThumbnailFile
                    )
                } else {
                    User(
                        UUID.fromString(jsonMetaData?.get(JSONEnum.USER_UUID_KEY.key) as String),
                        jsonMetaData[JSONEnum.USER_NAME_KEY.key] as String
                    )
                }
            }
            return user
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onPostExecute(result: User?) {
            callback.onUserLoginCheckDone(result)
        }

    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    class UserCredentialsSave(private var callback: OnUserCredentialsSaveComplete) :
        AsyncTask<Context, Void?, Boolean?>() {
        @RequiresApi(api = Build.VERSION_CODES.P)
        override fun doInBackground(vararg context: Context): Boolean {
            // Save the Meta information
            saveCredentials(callback.getUsername(), callback.getPath())
            // Save Photo URI
            savePhotoUri(callback.getPhotoUri(), context[0])
            return true
        }


        override fun onPostExecute(result: Boolean?) {
            callback.onUserCredentialsSaveDone(result)
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        private fun saveCredentials(username: String, path: String) {
            val credentialDetails = HashMap<String?, String?>()
            val userUUID = UUID.randomUUID()
            credentialDetails[JSONEnum.USER_NAME_KEY.key] = username
            credentialDetails[JSONEnum.USER_UUID_KEY.key] = userUUID.toString()
            val jsonCredentials = JSONHelper.createJSONFromHashMap(credentialDetails)
            val userMetaDataPath = Paths.get(
                path,
                FileEnum.USER_DIRECTORY.key,
                FileEnum.META_DATA_FILE.key
            )
            val userMetaDataFile = FileHelper.createFileAtPath(userMetaDataPath)
            FileHelper.writeToFile(userMetaDataFile, jsonCredentials.toString())
        }

        @RequiresApi(api = Build.VERSION_CODES.P)
        private fun savePhotoUri(photoUri: Uri?, context: Context) {
            if (photoUri != null) {

                val bitmap = ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(
                        context.contentResolver,
                        photoUri
                    )
                )
                val userThumbnailPicturePath = Paths.get(
                    context.getExternalFilesDir(null).toString(),
                    FileEnum.USER_DIRECTORY.key,
                    FileEnum.PHOTO_THUMBNAIL_FILE.key
                )
                val file = File(userThumbnailPicturePath.toString())

                FileHelper.writeBitmapToFile(file, bitmap)
            }
        }


    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    class LoadUserData(private var callback: OnUserDataFound) :
        AsyncTask<LoadUserData.TaskParams, Void?, LoadUserData.LoadUserDataResponse?>() {
        @RequiresApi(Build.VERSION_CODES.P)
        override fun doInBackground(vararg params: TaskParams?): LoadUserDataResponse? {
            val user = params[0]?.user
            val contentResolver = params[0]?.contentResolver
            if (user?.getThumbnailFile() != null) {
                val source = ImageDecoder.createSource(
                    contentResolver!!,
                    Uri.fromFile(user.getThumbnailFile())
                )
                val username = user.getUsername()
                val imageBmp = ImageDecoder.decodeBitmap(source)
                return LoadUserDataResponse(username, imageBmp)
            } else if (user?.getUsername() != null) {
                return LoadUserDataResponse(user.getUsername(), null)
            } else {
                return null
            }
        }

        @RequiresApi(Build.VERSION_CODES.P)
        override fun onPostExecute(result: LoadUserDataResponse?) {
            callback.onUserDataReturned(result)
        }

        class TaskParams(var user: User?, var contentResolver: ContentResolver)
        class LoadUserDataResponse(var username: String?, var imageBmp: Bitmap?)
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////

    class CourseCredentialsSave(
        private var callback: OnCourseCredentialsSaveComplete,
        private var context: WeakReference<Context>
    ) : AsyncTask<CourseCredentialsSave.TaskParams, Void?, Boolean?>() {
        @RequiresApi(Build.VERSION_CODES.P)
        override fun doInBackground(vararg params: TaskParams?): Boolean? {
            // Save the Meta information
            val course = params[0]?.course
            val imageUri = params[0]?.imageUri

            if (course != null) {
                saveCredentials(course)
            }

            // Save Photo URI
            savePhotoUri(imageUri, course)

            return true
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        private fun saveCredentials(course: Course) {

            val course = course

            val courseMetadata = HashMap<String?, String?>()

            courseMetadata[JSONEnum.USER_NAME_KEY.key] =
                course?.getCreator()?.getUserUUID().toString()
            courseMetadata[JSONEnum.COURSE_NAME_KEY.key] = course?.getCourseName()
            courseMetadata[JSONEnum.COURSE_UUID_KEY.key] = course?.getCourseUUID().toString()
            courseMetadata[JSONEnum.COURSE_CREATION_DATETIME.key] =
                course.getCreationDate()?.toString()
            val isoCode = course.getCourseCountry()?.getIsoCode()
            if (isoCode == null) {
                courseMetadata[JSONEnum.COURSE_LANGUAGE.key] = "null"
            } else {
                courseMetadata[JSONEnum.COURSE_LANGUAGE.key] =
                    course.getCourseCountry()?.getIsoCode()
            }
            courseMetadata[JSONEnum.COURSE_CATEGORY.key] =
                JSONArray(course.getCourseCategory().toString()).toString()
            courseMetadata[JSONEnum.COURSE_SLIDES.key] =
                JSONArray(course.getSlides().toString()).toString()

            val courseMetaDataPath = Paths.get(
                context.get()?.getExternalFilesDir(null).toString(),
                FileEnum.USER_DIRECTORY.key,
                FileEnum.COURSE_DIRECTORY.key,
                course?.getCourseUUID().toString(),
                FileEnum.META_DATA_FILE.key
            )

            val courseMetaDataFile = FileHelper.createFileAtPath(courseMetaDataPath)
            val courseJSONMeta = JSONHelper.createJSONFromHashMap(courseMetadata)


            FileHelper.writeToFile(courseMetaDataFile, courseJSONMeta.toString())
        }

        @RequiresApi(api = Build.VERSION_CODES.P)
        private fun savePhotoUri(photoUri: Uri?, course: Course?) {
            if (photoUri != null) {

                val bitmap = ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(
                        context.get()?.contentResolver!!,
                        photoUri
                    )
                )
                val userThumbnailPicturePath = Paths.get(
                    context.get()?.getExternalFilesDir(null).toString(),
                    FileEnum.USER_DIRECTORY.key,
                    FileEnum.COURSE_DIRECTORY.key,
                    course?.getCourseUUID().toString(),
                    FileEnum.PHOTO_THUMBNAIL_FILE.key
                )
                val file = File(userThumbnailPicturePath.toString())

                FileHelper.writeBitmapToFile(file, bitmap)
            }
        }


        @RequiresApi(Build.VERSION_CODES.P)
        override fun onPostExecute(result: Boolean?) {
            callback.onCourseDataReturned(result)
        }

        class TaskParams(var course: Course?, var imageUri: Uri?)

    }


    class CreatorCourseCredentialsLoad(private var callback: OnCreatorCourseCredentialsLoad) :
        AsyncTask<CreatorCourseCredentialsLoad.TaskParams, Void?, ArrayList<Pair<Course, Uri?>>>() {
        @RequiresApi(Build.VERSION_CODES.P)
        override fun doInBackground(vararg params: TaskParams?): ArrayList<Pair<Course, Uri?>>? {
            var courses = ArrayList<Pair<Course, Uri?>>()

            val path = params[0]?.path
            val coursesFile = FileHelper.createDirAtPath(path)
            val user = params[0]?.user
            if (coursesFile.listFiles() != null) {
                for (courseFolder in coursesFile.listFiles()) {

                    if (courseFolder.isDirectory) {
                        val jsonFilePath =
                            Paths.get(courseFolder.toString(), FileEnum.META_DATA_FILE.key)

                        val jsonFile = jsonFilePath.toFile()

                        // Retrieve content file
                        val contents = FileHelper.readFile(jsonFile)

                        if (contents != null) {
                            val jsonContents = JSONHelper.createJSONFromString(contents!!)

                            // Course UUID
                            val courseUUID = jsonContents?.get(JSONEnum.COURSE_UUID_KEY.key)
                            // Course Name
                            val courseName = jsonContents?.get(JSONEnum.COURSE_NAME_KEY.key)
                            // Category
                            val categoryJSON = JSONArray(
                                jsonContents?.get(JSONEnum.COURSE_CATEGORY.key).toString()
                            )
                            // Language
                            val language = jsonContents?.get(JSONEnum.COURSE_LANGUAGE.key)
                            // Timestamp
                            val courseCreationTimestamp =
                                jsonContents?.get(JSONEnum.COURSE_CREATION_DATETIME.key).toString()
                                    .toLong()
                            // Slides
                            val slidesJSON =
                                JSONArray(jsonContents?.get(JSONEnum.COURSE_SLIDES.key).toString())

                            // Creation course
                            val course = Course(UUID.fromString(courseUUID as String?), user)

                            //  Adding course name
                            course.setCourseName(courseName as String)
                            // Adding Course Category
                            course.setCourseCategory(
                                CategoryEnum.getCategoriesFromJSONArray(
                                    categoryJSON as JSONArray
                                )
                            )
                            // Adding Course language
                            if (language.toString() == "null") {
                                course.setCourseCountry(Country(null))
                            } else {
                                course.setCourseCountry(Country(language.toString()))

                            }
                            // Adding timestamp
                            course.setCreationDate(courseCreationTimestamp)

                            // Adding Slides
                            course.setSlidesFromJSONArray(slidesJSON)

                            // Thumbnail
                            val imagePath = Paths.get(
                                courseFolder.toString(),
                                FileEnum.PHOTO_THUMBNAIL_FILE.key
                            )
                            val imageFile = imagePath.toFile()
                            val imageUri = Uri.fromFile(imageFile)

                            val pair = Pair<Course, Uri>(course, imageUri)
                            courses.add(pair)
                        }
                    }
                }
            }

            var arrayListResults = ArrayList<Pair<Course, Uri?>>()

            if (courses.size > 1) {
                courses.sortedBy { it.first }
            }
            for (course in courses) {
                arrayListResults.add(course)
            }
            return arrayListResults

        }

        @RequiresApi(Build.VERSION_CODES.P)
        override fun onPostExecute(result: ArrayList<Pair<Course, Uri?>>) {
            callback.onCreatorCourseCredentialsLoad(result)
        }

        class TaskParams(var path: Path?, var user: User?)
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////
    class VideoUriSave(
        private var callback: OnVideoUriSaved,
        private var weakReferenceContentResolver: WeakReference<ContentResolver>
    ) : AsyncTask<VideoUriSave.TaskParams, Void?, File?>() {
        override fun doInBackground(vararg params: TaskParams?): File? {
            val outputFile = params[0]?.outputFile
            val videoUri = params[0]?.videoUri

            if (outputFile != null && outputFile.exists()) {
                if (videoUri != null) {
                    return FileHelper.copyVideoToFile(
                        outputFile,
                        videoUri,
                        weakReferenceContentResolver.get()
                    )
                }
            }
            return null

        }

        @RequiresApi(Build.VERSION_CODES.P)
        override fun onPostExecute(result: File?) {
            callback.onVideoUriSaved(result)
        }

        class TaskParams(var outputFile: File?, var videoUri: Uri?)

    }


    class DeleteFile(private var callback: OnDeleteFile) :
        AsyncTask<DeleteFile.TaskParams, Boolean?, Boolean?>() {
        override fun doInBackground(vararg params: TaskParams?): Boolean? {
            val deleteFile = params[0]?.deleteFile
            val deleteSlide = params[0]?.deleteSlide
            FileHelper.deleteFile(deleteFile)
            return deleteSlide
        }

        @RequiresApi(Build.VERSION_CODES.P)
        override fun onPostExecute(result: Boolean?) {
            callback.onDeleteFile(result)
        }

        class TaskParams(var deleteFile: File?, var deleteSlide: Boolean?)

    }


    class UpdateJSONFile(private var callback: OnUpdatedJSONFile) :
        AsyncTask<UpdateJSONFile.TaskParams, Boolean?, Boolean?>() {
        override fun doInBackground(vararg params: UpdateJSONFile.TaskParams?): Boolean? {
            val fileToUpdate = params[0]?.fileToUpdate
            val hashMapData = params[0]?.hashMapData

            var contents = FileHelper.readFile(fileToUpdate)

            var jsonContents = JSONHelper.createJSONFromString(contents!!)

            for ((key, value) in hashMapData!!) {
                jsonContents!!.put(key, value)
            }

            return FileHelper.writeToFile(fileToUpdate, jsonContents.toString())
        }

        @RequiresApi(Build.VERSION_CODES.P)
        override fun onPostExecute(result: Boolean?) {
            callback.onUpdatedJSONFile(result)
        }

        class TaskParams(
            var fileToUpdate: File?,
            var hashMapData: HashMap<String, ArrayList<String>>?
        )

    }

    class DeleteCourse(
        private var callback: OnCourseDeleted,
        private var context: WeakReference<Context>
    ) : AsyncTask<DeleteCourse.TaskParams, Course?, Course?>() {

        class TaskParams(var course: Course)

        @RequiresApi(Build.VERSION_CODES.O)
        override fun doInBackground(vararg params: TaskParams?): Course? {
            val course = params[0]?.course
            val coursePath = Paths.get(
                context.get()?.getExternalFilesDir(null).toString(),
                FileEnum.USER_DIRECTORY.key,
                FileEnum.COURSE_DIRECTORY.key,
                course?.getCourseUUID().toString()
            )
            val courseFile = coursePath.toFile()
            FileHelper.deleteFile(courseFile)
            return course
        }

        @RequiresApi(Build.VERSION_CODES.P)
        override fun onPostExecute(course: Course?) {
            callback.onCourseDeleted(course)
        }

    }

    class GroupOwnerReceiveClientInetAddressAsyncTask(
        private var callback: OnClientInetAddressReceived

    ) :
        AsyncTask<String?, Void, InetAddress?>() {

        companion object {
            val PORT_GET_CLIENT_INET = 8988
        }

        @Override
        override fun onPostExecute(result: InetAddress?) {
            callback.onClientInetAddressReceived(result)
        }

        override fun doInBackground(vararg p0: String?): InetAddress? {
            return try {
                val serverSocket = ServerSocket(PORT_GET_CLIENT_INET)
                val client = serverSocket.accept();
                val clientInetAddress = client.inetAddress
                serverSocket.close();
                return clientInetAddress
            } catch (e: IOException) {
                null;
            }
        }
    }

    class ClientSendInetAddressAsyncTask(
        private var serverEndPoint: WifiDirectEndpoint,
        private var callback: OnClientInetAddressSent
    ) :
        AsyncTask<Void?, InetAddress?, InetAddress?>() {
        private val SOCKET_TIMEOUT = 5000
        override fun doInBackground(vararg p0: Void?): InetAddress? {
            val socket = Socket()
            try {
                socket.bind(null)
                socket.connect(
                    InetSocketAddress(serverEndPoint.ip, serverEndPoint.port),
                    SOCKET_TIMEOUT
                )
            } catch (e: IOException) {

            } finally {
                if (socket != null) {
                    if (socket.isConnected) {
                        try {
                            socket.close()
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            return socket.inetAddress
        }
    }


    class ReceiveCourseAsyncTask(
        private var callback: OnCourseReceived
    ) :
        AsyncTask<Void?, String?, String?>() {
        private val SOCKET_TIMEOUT = 5000


        companion object {
            val PORT_GET_COURSE = 9989
        }

        @Override
        override fun onPostExecute(result: String?) {
            callback.onCourseReceived(result)
        }

        override fun doInBackground(vararg p0: Void?): String? {
            return try {
                val serverSocket = ServerSocket(PORT_GET_COURSE)
                val client = serverSocket.accept();


                val inputStream = client.getInputStream();
                val string = getStringValue(inputStream);
                serverSocket.close();
                string
            } catch (e: IOException) {
                null;
            }
        }

        // TODO remove this
        private fun getStringValue(inputStream: InputStream?): String? {
            var result = String()
            val buf = ByteArray(1024)
            var len: Int
            try {
                while (inputStream!!.read(buf).also { len = it } != -1) {
                    result += String(buf)
                }
                inputStream.close()
            } catch (e: IOException) {
                return result
            }
            return result
        }

    }


    class SendCourseAsyncTask(
        private var receiverEndPoint: WifiDirectEndpoint,
        private var callback: OnCourseSent
    ) :
        AsyncTask<Void?, Boolean?, Boolean?>() {
        private val SOCKET_TIMEOUT = 5000
        override fun doInBackground(vararg p0: Void?): Boolean? {
            val socket = Socket()
            val string = "Test 123"  // TODO change this to the course that we pass as a File.. will need to compess into bytes - send first the number of bytes for progressbar...
            try {
                socket.bind(null)
                socket.connect(
                    InetSocketAddress(receiverEndPoint.ip, receiverEndPoint.port),
                    SOCKET_TIMEOUT
                )
                val stream: OutputStream = socket.getOutputStream()
                var `is`: InputStream? = null
                try {
                    `is` = ByteArrayInputStream(string?.toByteArray())
                } catch (e: FileNotFoundException) {
                }
                copyFile(`is`, stream)
            } catch (e: IOException) {
                Log.i("FAILED SEND COURSE", e.toString())
                return false
            } finally {
                if (socket != null) {
                    if (socket.isConnected) {
                        try {
                            socket.close()
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            return true
        }

        @Override
        override fun onPostExecute(result: Boolean?) {
            callback.onCourseSent(result)
        }


        private fun copyFile(inputStream: InputStream?, out: OutputStream): Boolean {
            val buf = ByteArray(1024)
            var len: Int
            try {
                while (inputStream?.read(buf).also { len = it!! } != -1) {
                    out.write(buf, 0, len)
                }
                out.close()
                inputStream?.close()
            } catch (e: IOException) {
                return false
            }
            return true
        }


    }

}
