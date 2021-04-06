package com.baro.ui.create

import android.content.ContentResolver
import android.net.Uri
import android.opengl.Visibility
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.baro.R
import com.baro.constants.AppCodes
import com.baro.constants.AppTags
import com.baro.constants.FileEnum
import com.baro.constants.IntentEnum
import com.baro.dialogs.ImageDialog
import com.baro.helpers.AsyncHelpers
import com.baro.helpers.FileHelper
import com.baro.helpers.interfaces.OnDeleteFile
import com.baro.helpers.interfaces.OnUpdatedJSONFile
import com.baro.helpers.interfaces.OnVideoUriSaved
import com.baro.models.Course
import com.baro.models.Slide
import java.io.File
import java.lang.ref.WeakReference
import java.nio.file.Paths
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


class CreateSlideActivity : AppCompatActivity(), ImageDialog.OnInputListener, OnVideoUriSaved, OnDeleteFile, OnUpdatedJSONFile {


    // UI
    private lateinit var nextButton: ImageButton
    private lateinit var previousButton: ImageButton
    private lateinit var playButton: ImageButton
    private lateinit var addDeleteVideoButton: ImageButton
    private lateinit var deleteSlide: ImageButton
    private lateinit var videoView: VideoView
    private lateinit var finishSlide: ImageButton


    // Model
    private lateinit var course: Course
    private var slideCounter = 0
    private var videoUri: Uri? = null
    private var isPaused: Boolean = true
    private var byCamera: Boolean = false

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_slide)

        // Get Course
        course = intent.getParcelableExtra(IntentEnum.COURSE.key)

        // Create First Slide
        val slide = Slide(UUID.randomUUID(), course)
        course.getSlides()?.add(slide)
        videoUri = course.slides?.get(slideCounter)?.getVideoUri()

        // Configure UI
        configureNextButton()
        configurePreviousButton()
        configurePlayButton()
        configureCreateDeleteButton()
        configureVideoView()
        configureDeleteSlide()
        configureFinishButton()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun configureFinishButton() {
        finishSlide = findViewById(R.id.btn_finish)

        finishSlide.setOnClickListener {

            var courseMetaPath = Paths.get(
                    this@CreateSlideActivity.getExternalFilesDir(null).toString(),
                    FileEnum.USER_DIRECTORY.key,
                    FileEnum.COURSE_DIRECTORY.key,
                    course.getCourseUUID().toString(),
                    FileEnum.META_DATA_FILE.key
            )

            var courseMetaFile = courseMetaPath.toFile()

            var slideHashMap = HashMap<String, ArrayList<String>>()

            var slideUUIDArrayList = ArrayList<String>()
            for (slide in course.slides!!) {
                slideUUIDArrayList.add(slide!!.slideUUID.toString())
            }
            slideHashMap[FileEnum.SLIDE_DIRECTORY.key.toString()] = slideUUIDArrayList

            val updateJSONFile = AsyncHelpers.UpdateJSONFile(this)
            var params = AsyncHelpers.UpdateJSONFile.TaskParams(courseMetaFile, slideHashMap)
            updateJSONFile.execute(params)

        }
    }

    override fun onUpdatedJSONFile(result: Boolean?) {
        finish()
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun configureDeleteSlide() {
        deleteSlide = findViewById(R.id.btn_delete_slide)

        deleteSlide.setOnClickListener {
            deleteVideo(deleteSlide=true)

            if (slideCounter > 0) {
                course.slides?.remove(course.slides!!.get(slideCounter))
                if (slideCounter == course.getSlides()?.size) {
                    slideCounter -= 1
                }
            }
            videoUri = course.slides?.get(slideCounter)?.getVideoUri()
            SetVideoURI().execute(AppCodes.NO_CHANGE_SLIDE)
        }

        deleteSlide.visibility = View.INVISIBLE
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun deleteVideo(deleteSlide: Boolean = false) {
        val slideVideoPath = Paths.get(
                this@CreateSlideActivity.getExternalFilesDir(null).toString(),
                FileEnum.USER_DIRECTORY.key,
                FileEnum.COURSE_DIRECTORY.key,
                course.getCourseUUID().toString(),
                FileEnum.SLIDE_DIRECTORY.key,
                course.slides?.get(slideCounter)?.slideUUID.toString() + FileEnum.VIDEO_EXTENSION.key
        )

        var file = File(slideVideoPath.toString())
        val deleteFile = AsyncHelpers.DeleteFile(this)
        val taskParams = AsyncHelpers.DeleteFile.TaskParams(file, deleteSlide)
        deleteFile.execute(taskParams)

    }

    override fun onDeleteFile(deleteSlide: Boolean?) {
        if (!deleteSlide!!) {
            videoUri = null
        }

        isPaused = true
        updateUI()
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun configureCreateDeleteButton() {
        addDeleteVideoButton = findViewById(R.id.btn_add_delete)
        addDeleteVideoButton.setOnClickListener {

            if (videoUri == null) {
                // TODO PERMISSION_REFACTOR
                val imageDialog = ImageDialog(this)
                imageDialog.show(supportFragmentManager, AppTags.THUMBNAIL_SELECTION.toString())
                updateUI()
            } else {

                deleteVideo()
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun configurePreviousButton() {
        previousButton = findViewById(R.id.btn_previous)

        // Initially invisible
        previousButton.visibility = View.INVISIBLE

        previousButton.setOnClickListener {
            updateClickable(allUnclickable = true)
            SetVideoURI().execute(AppCodes.BACKWARDS_SLIDE)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun configureNextButton() {
        nextButton = findViewById(R.id.btn_next)

        nextButton.visibility = View.INVISIBLE

        nextButton.setOnClickListener {
            updateClickable(allUnclickable = true)
            SetVideoURI().execute(AppCodes.FORWARD_SLIDE)

        }
    }

    private fun updateUI() {
        updateClickable()
        updateVideoViewUI()
        updatePlayButtonUI()
        updateSlideCountUI()
        updateDeleteCreateButton()
        updateDeleteSlideButton()
    }

    private fun updateDeleteSlideButton() {
        if (slideCounter == 0) {
            deleteSlide.visibility = View.INVISIBLE
        } else {
            deleteSlide.visibility = View.VISIBLE
        }

    }

    private fun updateClickable(allUnclickable: Boolean = false) {

        if (allUnclickable) {
            nextButton.isClickable = false
            previousButton.isClickable = false
            playButton.isClickable = false
            addDeleteVideoButton.isClickable = false
            videoView.isClickable = false
            deleteSlide.isClickable = false
        } else {
            if (!isPaused) {
                nextButton.isClickable = false
                previousButton.isClickable = false
                playButton.isClickable = false
                addDeleteVideoButton.isClickable = false
                videoView.isClickable = true
                deleteSlide.isClickable = true

            } else {
                nextButton.isClickable = true
                previousButton.isClickable = true
                playButton.isClickable = true
                addDeleteVideoButton.isClickable = true
                videoView.isClickable = false
                deleteSlide.isClickable = true

            }
        }
    }

    private fun updateVideoViewUI() {
        if (videoUri == null) {
            videoView.setVideoURI(null)
            videoView.visibility = View.INVISIBLE
            videoView.visibility = View.VISIBLE
        } else if (isPaused) {
            videoView.setVideoURI(videoUri)

            if (videoUri != null) {
                videoView.seekTo(100)

            }
        }
    }

    private fun updatePlayButtonUI() {
        if (videoUri == null || !isPaused) {
            playButton.visibility = View.INVISIBLE
        } else {
            playButton.visibility = View.VISIBLE
        }
    }

    private fun updateSlideCountUI() {

        when (slideCounter) {
            0 -> {
                if (course.slides?.size!! > 1 || videoUri != null) {
                    nextButton.visibility = View.VISIBLE
                }
                else if (videoUri == null) {
                    nextButton.visibility = View.INVISIBLE
                }
                previousButton.visibility = View.INVISIBLE

            }
            course.getSlides()?.size?.minus(1) -> {
                if (videoUri != null) {
                    nextButton.visibility = View.VISIBLE
                } else {
                    nextButton.visibility = View.INVISIBLE
                }
                previousButton.visibility = View.VISIBLE
            }
            else -> {
                nextButton.visibility = View.VISIBLE
                previousButton.visibility = View.VISIBLE
            }
        }


    }

    private fun updateDeleteCreateButton() {
        if (videoUri != null) {
            addDeleteVideoButton.setImageResource(R.drawable.ic_delete)
        } else {
            addDeleteVideoButton.setImageResource(R.drawable.ic_create)

        }
    }


    private fun configurePlayButton() {
        playButton = findViewById(R.id.btn_play)
        playButton.visibility = View.INVISIBLE

        playButton.setOnClickListener {
            if (videoUri != null) {
                if (isPaused) {
                    isPaused = false
                    videoView.start();
                    updateUI()
                }
            }
        }

    }

    private fun configureVideoView() {
        videoView = findViewById(R.id.video_view_slide)

        videoView.setOnClickListener {
            if (videoUri != null) {
                if (!isPaused) {

                    isPaused = true
                    videoView.pause()
                    updateUI()

                }
            }
        }

        videoView.setOnCompletionListener {
            isPaused = true
            updateUI()
        }

    }

    @RequiresApi(Build.VERSION_CODES.P)
    private var getGalleryContent: ActivityResultLauncher<String?>? = registerForActivityResult(ActivityResultContracts.GetContent()
    ) { uri ->

        val slideVideoPath = Paths.get(
                this@CreateSlideActivity.getExternalFilesDir(null).toString(),
                FileEnum.USER_DIRECTORY.key,
                FileEnum.COURSE_DIRECTORY.key,
                course.getCourseUUID().toString(),
                FileEnum.SLIDE_DIRECTORY.key,
                course.slides?.get(slideCounter)?.slideUUID.toString() + FileEnum.VIDEO_EXTENSION.key
        )

        val slideVideoFile = FileHelper.createFileAtPath(slideVideoPath)
        val contentResolverReference = WeakReference<ContentResolver>(contentResolver)
        val videoUriSave = AsyncHelpers.VideoUriSave(this, contentResolverReference)
        val taskParams = AsyncHelpers.VideoUriSave.TaskParams(slideVideoFile, uri)
        videoUriSave.execute(taskParams)


    }

    override fun onVideoUriSaved(outputFile: File?) {
        if (outputFile != null) {
            videoUri = Uri.fromFile(outputFile)
        } else {
            Toast.makeText(this, getString(R.string.video_not_saved), Toast.LENGTH_LONG).show()
        }

        updateUI()
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private var getCameraContent: ActivityResultLauncher<Uri?>? = registerForActivityResult(
            ActivityResultContracts.TakeVideo()
    ) {
        // TODO Fix bug: Go to recording video but go back before recording one -> UI displayed is wrong
        videoView.setVideoURI(videoUri)
        videoView.seekTo(100)

        // UI
        updateUI()
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    override fun sendInput(choice: Int) {
        if (choice == AppCodes.CAMERA_ROLL_SELECTION.code) {
            byCamera = true
            val slideVideoPath = Paths.get(
                    this@CreateSlideActivity.getExternalFilesDir(null).toString(),
                    FileEnum.USER_DIRECTORY.key,
                    FileEnum.COURSE_DIRECTORY.key,
                    course.getCourseUUID().toString(),
                    FileEnum.SLIDE_DIRECTORY.key,
                    course.slides?.get(slideCounter)?.slideUUID.toString() + FileEnum.VIDEO_EXTENSION.key
            )
            val slideVideoFile = FileHelper.createFileAtPath(slideVideoPath)
            videoUri = FileProvider.getUriForFile(applicationContext!!, applicationContext!!.packageName + ".fileprovider", slideVideoFile!!)
            getCameraContent?.launch(videoUri)
        } else if (choice == AppCodes.GALLERY_SELECTION.code) {
            byCamera = false
            getGalleryContent?.launch("video/*")
        }
    }

    inner class SetVideoURI : AsyncTask<AppCodes, Void?, AppCodes>() {
        override fun doInBackground(vararg directions: AppCodes): AppCodes {
            course.slides?.get(slideCounter)?.setVideoUri(videoUri)
            return directions[0]
        }

        override fun onPostExecute(direction: AppCodes?) {
            if (direction == AppCodes.FORWARD_SLIDE) {
                slideCounter += 1
                if (course.getSlides()?.size == slideCounter) {
                    val slide = Slide(UUID.randomUUID(), course)
                    course.getSlides()?.add(slide)
                }

                videoUri = course.slides?.get(slideCounter)?.getVideoUri()
            } else if (direction == AppCodes.BACKWARDS_SLIDE) {
                if (videoUri == null && slideCounter == course.getSlides()?.size?.minus(1) && slideCounter > 0) {
                    course.getSlides()?.remove(course.getSlides()?.size?.minus(1))
                }

                slideCounter -= 1
                videoUri = course.getSlides()?.get(slideCounter)?.getVideoUri()
            }
            updateUI()
        }

    }




}
