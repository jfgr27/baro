package com.baro.ui.splash

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.baro.R
import com.baro.constants.AppCodes
import com.baro.constants.AppTags
import com.baro.constants.FileEnum
import com.baro.dialogs.ImageDialog
import com.baro.dialogs.ImageDialog.OnInputListener
import com.baro.helpers.AsyncHelpers
import com.baro.helpers.FileHelper
import com.baro.helpers.interfaces.OnUserCredentialsSaveComplete
import com.baro.ui.main.MainActivity
import java.nio.file.Paths

@RequiresApi(api = Build.VERSION_CODES.N)
class SplashLoggingFragment : Fragment(), OnInputListener, OnUserCredentialsSaveComplete {

    private lateinit var photoThumbnailButton: ImageButton
    private lateinit var usernameEditText: EditText
    private lateinit var nextButton: ImageButton

    private var photoUri: Uri? = null

    @RequiresApi(api = Build.VERSION_CODES.O)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_logging, container, false)

        // Configure UI
        configurePhotoThumbnailButton(view)
        configureUsernameEditText(view)
        configureNextButton(view)
        return view
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun configurePhotoThumbnailButton(view: View?) {
        photoThumbnailButton = view?.findViewById(R.id.im_account)!!

        photoThumbnailButton.setOnClickListener {
            val imageDialog = ImageDialog(this)
            imageDialog.show(parentFragmentManager, AppTags.THUMBNAIL_SELECTION.toString())

        }
    }

    private var getGalleryContent: ActivityResultLauncher<String?>? = registerForActivityResult(GetContent()
    ) { uri ->
        photoUri = uri
        photoThumbnailButton.setImageURI(uri)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private var getCameraContent: ActivityResultLauncher<Uri?>? = registerForActivityResult(
            TakePicture()
    ) { result: Boolean? ->
        if (result == true) {
            photoThumbnailButton.setImageURI(photoUri)
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    override fun sendInput(choice: Int) {
        if (choice == AppCodes.CAMERA_ROLL_SELECTION.code) {
            val userMetaDataPath = Paths.get(activity?.getExternalFilesDir(null).toString(),
                        FileEnum.USER_DIRECTORY.key,
                        FileEnum.PHOTO_THUMBNAIL_FILE.key)
            val userThumbnailFile = FileHelper.createFileAtPath(userMetaDataPath)
            photoUri = FileProvider.getUriForFile(activity?.applicationContext!!, activity?.applicationContext!!.packageName + ".fileprovider", userThumbnailFile!!)
            getCameraContent?.launch(photoUri)

        } else if (choice == AppCodes.GALLERY_SELECTION.code) {

                getGalleryContent?.launch("image/*")

        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun configureNextButton(view: View?) {
        nextButton = view?.findViewById(R.id.btn_next)!!

        nextButton.setOnClickListener {
            if (usernameEditText.text.length > 5) {
                val userCredentialsSave = AsyncHelpers.UserCredentialsSave(this)
                userCredentialsSave.execute(context)
            } else {
                // TODO discuss if we need a username..
                Toast.makeText(
                        context,
                        R.string.please_enter_valid_username_toast,
                        Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun configureUsernameEditText(view: View?) {
        usernameEditText = view?.findViewById(R.id.edit_text_username)!!
    }


    override fun onUserCredentialsSaveDone(result: Boolean?) {
        if (result == true) {
            val startMainActivity = Intent(
                    activity,
                    MainActivity::class.java)
            // Perhaps use UserCredentialsTask From SplashActivity to get the credentials once created
            startActivity(startMainActivity)
            requireActivity().finish()
        } else {
            Toast.makeText(context, R.string.error_saving_credentials, Toast.LENGTH_LONG).show()
        }
    }

    override fun getUsername(): String {
        return usernameEditText.text.toString()
    }

    override fun getPath(): String {
        return requireContext().getExternalFilesDir(null).toString()
    }

    override fun getPhotoUri(): Uri? {
        return photoUri
    }

}


