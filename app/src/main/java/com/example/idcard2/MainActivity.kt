package com.example.idcard2

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.View.OnClickListener
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.idcard2.databinding.ActivityMainBinding
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity(), OnClickListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var baseApi: TessBaseAPI
    private var tempPath: String? = null
    private var originImage: Bitmap? = null
    private var resultImage: Bitmap? = null
    private val imageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.also { data ->
                getResult(data)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.apply {
            val ctx = this@MainActivity
            btnAlbum.setOnClickListener(ctx)
            btnSearch.setOnClickListener(ctx)
            btnRecognition.setOnClickListener(ctx)
            btn1.setOnClickListener(ctx)
            btn2.setOnClickListener(ctx)
            btn3.setOnClickListener(ctx)
        }
        initAPI()
    }

    override fun onDestroy() {
        super.onDestroy()
        baseApi.end()
    }

    private fun initAPI() {
        baseApi = TessBaseAPI()
        lifecycleScope.launch(Dispatchers.IO) {
            val dir = getExternalFilesDir("tessdata")
            if (dir?.exists() == false) {
                dir.mkdir()
            }
            val language = "cn"
            val file = File(dir, "$language.traineddata")
            if (!file.exists()) {
                file.createNewFile()
            }

            assets.open("$language.traineddata").use { input ->
                FileOutputStream(file).use { output ->
                    var read: Int
                    while (input.read().also { read = it } != -1) {
                        output.write(read)
                    }
                }
            }
            val result = baseApi.init(dir?.parentFile?.absolutePath, language)
            if (result) {
                lifecycleScope.launch {
                    binding.btnAlbum.isEnabled = true
                    binding.btnSearch.isEnabled = true
                    binding.btnRecognition.isEnabled = true
                }
            }
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btn_album -> {
                // https://stackoverflow.com/questions/30572261/using-data-from-context-providers-or-requesting-google-photos-read-permission
                val intent = Intent(Intent.ACTION_PICK)//ACTION_PICK
                intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                imageLauncher.launch(Intent.createChooser(intent, "选择待识别图片"))
            }

            R.id.btn_search -> {
                originImage?.also {
                    binding.txResult.text = ""
                    resultImage?.recycle()
                    resultImage = ImageProcess.getIdNumber(it, Bitmap.Config.ARGB_8888)
                    binding.imgResult.setImageBitmap(resultImage)
                }
            }

            R.id.btn_recognition -> {
                resultImage?.also {
                    // 识别Bitmap中的图片
                    baseApi.also { api ->
                        api.setImage(it)
                        binding.txResult.text = api.utF8Text
                        api.clear()
                    }
                }
            }

            R.id.btn1 -> {
                originImage?.also {
                    binding.img1.setImageBitmap(
                        ImageProcess.startProcess(it, Bitmap.Config.ARGB_8888, 1, 0)
                    )
                    binding.img2.setImageBitmap(null)
                }
            }

            R.id.btn2 -> {
                originImage?.also {
                    binding.img1.setImageBitmap(
                        ImageProcess.startProcess(it, Bitmap.Config.ARGB_8888, 2, 0)
                    )
                    binding.img2.setImageBitmap(
                        ImageProcess.startProcess(it, Bitmap.Config.ARGB_8888, 2, 1)
                    )
                }
            }

            R.id.btn3 -> {
                originImage?.also {
                    binding.img1.setImageBitmap(
                        ImageProcess.startProcess(it, Bitmap.Config.ARGB_8888, 3, 0)
                    )
                    binding.img2.setImageBitmap(
                        ImageProcess.startProcess(it, Bitmap.Config.ARGB_8888, 3, 1)
                    )
                }
            }
        }
    }

    private fun getResult(uri: Uri?) {
        uri ?: return
        val imagePath = when (uri.scheme) {
            "file" -> uri.path
            "content" -> {
                if (uri.host == "com.miui.gallery.open") {
                    uri.path?.substring(5)
                } else {
                    var path: String? = null
                    val filePathColumns = arrayOf(MediaStore.Images.Media.DATA)
                    val cursor = contentResolver.query(uri, filePathColumns, null, null, null)
                    cursor?.let {
                        if (cursor.moveToFirst()) {
                            val columnIndex = cursor.getColumnIndex(filePathColumns[0])
                            path = cursor.getString(columnIndex)
                        }
                        cursor.close()
                    }
                    path
                }
            }

            else -> null
        }
        Log.e("TagIdCard", "uri: $uri, path: $imagePath")
        if (!imagePath.isNullOrEmpty()) {
            checkAndGetBitmap(imagePath)
        }
    }

    private fun checkAndGetBitmap(imagePath: String) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            originImage?.recycle()
            originImage = toBitmap(imagePath)
            binding.txResult.text = ""
            binding.imgOrigin.setImageBitmap(originImage)
        } else {
            //没有权限则申请权限
            tempPath = imagePath
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ),
                1
            )
        }
    }

    private fun toBitmap(pathName: String?): Bitmap? {
        if (pathName.isNullOrEmpty()) return null
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(pathName, options)
        var width = options.outWidth
        var height = options.outHeight
        var scale = 1
        while (width > 640 || height > 480) {
            width /= 2
            height /= 2
            scale *= 2
        }

        val result = BitmapFactory.Options()
        result.inSampleSize = scale
        result.outHeight = height
        result.outWidth = width
        return BitmapFactory.decodeFile(pathName, result)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val index = permissions.indexOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (index >= 0 && grantResults[index] == PackageManager.PERMISSION_GRANTED) {
            originImage?.recycle()
            originImage = toBitmap(tempPath)
            binding.txResult.text = ""
            binding.imgOrigin.setImageBitmap(originImage)
        }
        tempPath = null
    }
}