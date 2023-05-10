package ke.co.osl.utcollectorapp

import android.Manifest
import android.Manifest.permission.CAMERA
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.net.http.SslError
import android.os.*
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.auth0.android.jwt.JWT
import com.google.android.gms.location.*
import ke.co.osl.utcollectorapp.api.ApiInterface
import ke.co.osl.utcollectorapp.models.Message
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit


class Reporting: AppCompatActivity() {
    lateinit var preferences: SharedPreferences
    lateinit var editor: SharedPreferences.Editor
    lateinit var mFusedLocationClient: FusedLocationProviderClient
    lateinit var webview: WebView
    private lateinit var locationRequest: LocationRequest
    lateinit var accuracy: TextView
    lateinit var coords: TextView
    var vFilename: String = ""
    var lat: Double = 0.0
    var lng: Double = 0.0
    var acc: Float = 1000f
    var requestingLocationUpdates : Boolean = false
    lateinit var imageView: ImageView
    val REQUEST_IMAGE_CAPTURE = 1
    val REQUEST_CODE = 100
    private val PERMISSION_REQUEST_CODE: Int = 101
    private var mCurrentPhotoPath: String? = null
    private lateinit var locationCallback: LocationCallback

    val ip_URL = "http://102.222.147.190/api/homepage"

    object AndroidJSInterface {
        @JavascriptInterface
        fun onClicked() {
            Log.d("HelpButton", "Help button clicked")
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reporting)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest().apply {
            interval = TimeUnit.SECONDS.toMillis(10)
            fastestInterval = TimeUnit.SECONDS.toMillis(5)
            maxWaitTime = TimeUnit.MINUTES.toMillis(2)
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return
                System.out.println("location updates")
                for (location in locationResult.locations){
                    System.out.println(location)
                    val txt = "Accuracy: " + location?.accuracy.toString() + " m"
                    accuracy.text = txt
                    val txt1 =
                        "Lat: " + location?.latitude?.toString() + " Lng: " + location?.longitude?.toString()
                    coords.text = txt1
                    System.out.println(location)
                    adjustMarker(location?.longitude!!, location?.latitude!!)
                    lat = location!!.latitude
                    lng = location!!.longitude
                    acc = location!!.accuracy
                }
            }
        }

        mFusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())

        mFusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                requestingLocationUpdates = true
                val txt = "Accuracy: " + location?.accuracy.toString() + " m"
                accuracy.text = txt
                val txt1 =
                    "Lat: " + location?.latitude?.toString() + " Lng: " + location?.longitude?.toString()
                coords.text = txt1
                System.out.println(location)
                adjustMarker(location?.longitude!!, location?.latitude!!)
                lat = location!!.latitude
                lng = location!!.longitude
                acc = location!!.accuracy
                System.out.println("ohoo " + acc)
            }
        }

        preferences = this.getSharedPreferences("ut_manager", MODE_PRIVATE)
        editor = preferences.edit()

        val jwt = JWT(preferences.getString("publictoken","")!!)
        val userid = jwt.getClaim("UserID").asString()

        val back = findViewById<ImageView>(R.id.back)
        val refresh = findViewById<ImageView>(R.id.refresh)
        val myLocation = findViewById<ImageView>(R.id.gps)
        accuracy = findViewById(R.id.accuracy)
        coords = findViewById(R.id.coords)
        imageView = findViewById(R.id.photo)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestLocationPermission()
        }

        //Acquiring User Location
        myLocation.setOnClickListener {
            if (lat !== 0.0 && lng !== 0.0) {
                getLocationUpdates();
                val txt = "Accuracy: " + acc.toString() + " m"
                accuracy.text = txt
                val txt1 = "Lat: " + lat.toString() + " Lng: " + lng.toString()
                coords.text = txt1
                adjustMarker(lng, lat)

            } else {
                getLocationUpdates()
                Toast.makeText(this, "Location not acquired! Please wait", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        val progDialog: ProgressDialog? = ProgressDialog.show(this, "Loading","Please wait...", true);
        progDialog?.setCancelable(false);

        webview = findViewById(R.id.webview)
        webview.webViewClient = WebViewClient()

        val webSettings = webview.settings
        webSettings.javaScriptEnabled = true

        webview.addJavascriptInterface(AndroidJSInterface, "Android")
        webview.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                if (handler != null){
                    handler.proceed();
                } else {
                    super.onReceivedSslError(view, null, error);
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                progDialog?.show()
                view.loadUrl(url)
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                progDialog?.dismiss()
                getLocationUpdates()
                    adjustMarker(lng, lat)
            }
        }
        webview.loadUrl(ip_URL)

        //Refresh Map
        refresh.setOnClickListener {
            refreshMap()
            getLocationUpdates()
        }

        back.setOnClickListener {
            startActivity(Intent(this, Incidences::class.java))
        }

        reportIncident ()
    }

    fun getLocationUpdates() {

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return
               System.out.println("update called")
                if (locationResult.locations.isNotEmpty()) {
                    System.out.println(locationResult.lastLocation)
                    val lc = locationResult.lastLocation
                    val txt = "Accuracy: " + lc?.accuracy.toString() + " m"
                    accuracy.text = txt
                    val accuracy2 = accuracy.toString()
                    System.out.println("NOW HERE " + accuracy2)
                    val txt1 =
                        "Lat: " + lc?.latitude.toString() + " Lng: " + lc?.longitude!!.toString()
                    coords.text = txt1
                    adjustMarker(lc?.longitude!!, lc?.latitude!!)
                    lat = lc!!.latitude
                    lng = lc!!.longitude
                    acc = lc!!.accuracy
                }
            }
        }
    }

    private fun refreshMap() {
        webview.loadUrl(ip_URL)
    }

    private fun adjustMarker(longitude: Double, latitude: Double) {
        webview.loadUrl(
            "javascript:(adjustMarker($longitude,$latitude))"
        )
    }

    //request location permission
    fun requestLocationPermission() {
        val locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                when {
                    permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                    }
                    permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    }
                    else -> {
                    }
                }
            }
        }
        // 7o7gi9h9
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun reportIncident() {
        val reportedIncident=intent.getStringExtra("ReportIncident")
        val takePhoto = findViewById<ImageView>(R.id.takePhoto)
        val error = findViewById<TextView>(R.id.error)
        val gallery = findViewById<ImageView>(R.id.gallery)
        val submit = findViewById<Button>(R.id.submit)
        val comment = findViewById<TextView>(R.id.comment)
        val progress = findViewById<ProgressBar>(R.id.progress)

        takePhoto.isEnabled = false
        gallery.isEnabled = false

        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 111)
        } else {
            takePhoto.isEnabled = true
        }

        takePhoto.setOnClickListener {
            if (checkPersmission())  takeAPhoto() else requestPermission()
        }

        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 111)
        } else {
            gallery.isEnabled = true
        }

        gallery.setOnClickListener {
            if (checkPersmission())  uploadPhoto() else requestPermission()
        }

        submit.setOnClickListener {
            val jwt = JWT(preferences.getString("publictoken","")!!)
            val userid = jwt.getClaim("UserID").asString()

            error.text = ""

            val file = File(mCurrentPhotoPath)
            val requestFile = RequestBody.create(MediaType.parse("multipart/form-data"), file)

            if(acc > 50.00){
                error.text = "Accuracy must be below 50m. Please wait!!"
                return@setOnClickListener
            }

            if (TextUtils.isEmpty(comment.text.toString())) {
                error.text = "Please describe the incident!"
                return@setOnClickListener
            }

            progress.visibility = View.VISIBLE

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("Latitude", lat.toString())
                .addFormDataPart("Type", reportedIncident.toString())
                .addFormDataPart("Status", "Received")
                .addFormDataPart("NRWUserID", "")
                .addFormDataPart("DueDate", "")
                .addFormDataPart("Action", "")
                .addFormDataPart("Longitude", lng.toString())
                .addFormDataPart("Description", comment.text.toString())
                .addFormDataPart("Image", file.name, requestFile)
                .addFormDataPart("UserID", userid)

                .build()

            val apiInterface = ApiInterface.create().reportIncident(multipartBody)
            apiInterface.enqueue( object : Callback<Message> {
                override fun onResponse(call: Call<Message>?, response: Response<Message>?) {
                    progress.visibility = View.GONE
                    System.out.println("success")
                    System.out.println("Now the incidence is "+response)
                    if(response?.body()?.success !== null){
                        error.text = response?.body()?.success

                        val countdonwtimer = object: CountDownTimer(2000,1){
                            override fun onTick(p0: Long) {
                            }
                            override fun onFinish() {
                                startActivity(Intent(this@Reporting, Incidences::class.java))
                                finish()
                            }
                        }
                        countdonwtimer.start()
                    }
                    else {
                        error.text = response?.body()?.error
                    }
                }
                override fun onFailure(call: Call<Message>?, t: Throwable?) {
                    progress.visibility = View.GONE
                    System.out.println(t)
                    error.text = "Connection to server failed"
                }
            })
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            //To get the File for further usage
            val auxFile = File(mCurrentPhotoPath)
            var bitmap :Bitmap= BitmapFactory.decodeFile(mCurrentPhotoPath)
            imageView.setImageBitmap(Bitmap.createScaledBitmap(bitmap, 512, 512, true))
        }

        if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_CODE){
            mCurrentPhotoPath = getRealPathFromURI(this,data?.data)
            val inputStream: InputStream? = contentResolver.openInputStream(data?.data!!)
            val bitmap = BitmapFactory.decodeStream(inputStream)
//            imageView.setImageURI(data?.data) // handle chosen image
            imageView.setImageBitmap(Bitmap.createScaledBitmap(bitmap, 512, 512, true))
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            val takePhoto = findViewById<ImageView>(R.id.takePhoto)
            takePhoto.isEnabled = true
        } else if (requestCode == 100 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            val gallery = findViewById<ImageView>(R.id.gallery)
            gallery.isEnabled = true
        }
    }

    private fun uploadPhoto() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        val file: File = createFile()
        val uri: Uri =  FileProvider.getUriForFile(Objects.requireNonNull(getApplicationContext()),
            BuildConfig.APPLICATION_ID + ".provider", file)
        intent.putExtra(MediaStore.EXTRA_OUTPUT,uri)
        startActivityForResult(intent,100)
    }

    private fun takeAPhoto() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val file: File = createFile()
        val uri: Uri =  FileProvider.getUriForFile(Objects.requireNonNull(getApplicationContext()),
            BuildConfig.APPLICATION_ID + ".provider", file)
        intent.putExtra(MediaStore.EXTRA_OUTPUT,uri)
        startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
    }

    private fun checkPersmission(): Boolean {
        return (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this,
            android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(READ_EXTERNAL_STORAGE, CAMERA),
            PERMISSION_REQUEST_CODE)
    }

    @Throws(IOException::class)
    private fun createFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File = getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            mCurrentPhotoPath = absolutePath
        }
    }

    fun getRealPathFromURI(context: Context, contentUri: Uri?): String? {
        var cursor: Cursor? = null
        return try {
            val proj = arrayOf(MediaStore.Images.Media.DATA)
            cursor = context.getContentResolver().query(contentUri!!, proj, null, null, null)
            val column_index: Int = cursor!!.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            cursor.moveToFirst()
            cursor.getString(column_index)
        } finally {
            if (cursor != null) {
                cursor.close()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (requestingLocationUpdates) startLocationUpdates()
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermission();
            return
        }
        mFusedLocationClient.requestLocationUpdates(locationRequest,
            locationCallback,
            Looper.getMainLooper())
    }
    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    private fun stopLocationUpdates() {
        mFusedLocationClient.removeLocationUpdates(locationCallback)
    }

}