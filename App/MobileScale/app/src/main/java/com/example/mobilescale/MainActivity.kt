package com.example.mobilescale

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat
//import com.example.mobilescale.ui.theme.MobileScaleTheme
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.media.MediaPlayer

// Import Volley library
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import org.json.JSONObject
import com.android.volley.NetworkResponse
import com.android.volley.toolbox.HttpHeaderParser
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.charset.Charset

// Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.util.Base64
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import java.math.RoundingMode
import kotlin.math.abs

// Volume
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.util.Size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
//import androidx.compose.foundation.layout.FlowRowScopeInstance.weight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.unit.TextUnit
import org.json.JSONException
import kotlin.math.round

// List of measurements kept while the application is running
val sensorDataMeasurementsMemory = 10

// Store measurements (array of a certain number of lists of 3 values) for the accelerometer measures (to show the scatterplot)
var sensorDataMeasurements = mutableStateListOf<SensorData>()
var sensorDataMeasurementsRaw = List(0) {
	SensorData(
		Triple(0.0, 0.0, 0.0),
		0.0,
		Triple(0.0, 0.0, 0.0),
		Triple(0.0, 0.0, 0.0),
		Triple(0.0, 0.0, 0.0),
		Triple(0.0, 0.0, 0.0),
		0.0,
		Triple(0.0, 0.0, 0.0),
		0.0
	)
}

// Flag indicating if the user is trying to measure or measuring or not
var isCurrentlyMeasuring = false
var isTryingToMeasure = false

// Current phone's volume
var currentPhoneVolume = mutableStateOf(0)

// List of ingredients and weights
var ingredientsList = mutableListOf<String>()
var weightsList = mutableListOf<String>()

// Flag indicating if the user is logged in or not
var isLoggedIn = mutableStateOf(false)

// Flag indicating if the user is trying to login
// NOTE: equal to:
//	-1: not trying to login
//	0: trying to login (inserted email, waiting for OTP response)
//	1: trying to login (inserted email, wrong response from server)
//	2: trying to login (inserted email, OTP code sent to email successfully
//	3: trying to login (inserted OTP, waiting for response)
//	4: trying to login (inserted OTP, wrong response from server)
//	5: trying to login (inserted OTP, successfully logged in)
var isTryingToLogin = mutableStateOf(-1)

// User infos
var userEmail = mutableStateOf("")
var oneTimePassword = mutableStateOf("")
var currentUserInfosString = mutableStateOf("")
var followedUsers = mutableStateListOf<String>()

// Flag indicating whether the user sent the information of the current ingredients to the server or not
var sentIngredientsToServerText = mutableStateOf("")

// Colors of [carbohydrates, fat, proteins] to show the macronutrients
val macronutrientsColors = listOf(
	Color(0xFF02A9EA),
	Color(0xFFEE7B30),
	Color(0xFFEE0EE9),
)

// Main activity class
class MainActivity : ComponentActivity(), SensorEventListener {
	
	// Sensor manager
	private lateinit var sensorManager: SensorManager
	// Vibrator
	private var vibrator: Vibrator? = null
	// Accelerometer sensor
	private var accelerometer: Sensor? = null
	// Device temperature sensor
	//private var deviceTemperature: Sensor? = null
	// Gravity sensor
	private var gravity: Sensor? = null
	// Gyroscope sensor
	private var gyroscope: Sensor? = null
	// Linear acceleration sensor
	private var linearAcceleration: Sensor? = null
	// Orientation sensor
	//private var orientation: Sensor? = null
	// Pressure sensor
	private var pressure: Sensor? = null
	// Rotation vector sensor
	private var rotationVector: Sensor? = null
	// Ambient temperature sensor
	private var ambientTemperature: Sensor? = null
	
	// Flag indicating if we are in debug mode or not
	private var _debug by mutableStateOf(false)
	
	// Current food items list
	var _foodItems = mutableListOf<String>()
	// Current weights list
	var _itemWeights = mutableListOf<String>()
	
	// Initialize the food items to contain some default values
	val useSampleData = false
	init {
		if (useSampleData) {
			_foodItems.addAll(listOf("Apple", "Banana", "Orange"))
			_itemWeights.addAll(listOf("100", "200", "300"))
		}
	}
	
	// Flag indicating if we should show the current accelerometer values or not
	private var _showRealTimeAccelerometerValues by mutableStateOf(false)
	
	// Flag indicating if measuring or not
	private var _measuring by mutableStateOf(false)
	
	// String indicating how to show RealTime measurements (either show numeric measurements or scatter plot, leave null or empty to show nothing)
	//	NOTE: options are "text" or "scatterplot" (or leave empty to show nothing)
	private var _measurementsDisplayType : String = ""
	
	// Flag indicating whether to vibrate or not while measuring
	private var _vibrateWhileMeasuring by mutableStateOf(true)
	
	// Measurements files folder directory (make it a global variable)
	private val measurementsFolder : String = "/MobileScale"
	
	// Current sensor values stored in a single data structure
	private var sensorData by mutableStateOf(SensorData(
		Triple(0.0, 0.0, 0.0),
		0.0,
		Triple(0.0, 0.0, 0.0),
		Triple(0.0, 0.0, 0.0),
		Triple(0.0, 0.0, 0.0),
		Triple(0.0, 0.0, 0.0),
		0.0,
		Triple(0.0, 0.0, 0.0),
		0.0
	))
	// List of measurements during the measuring process (use SensorData data structure)
	private var _sensorMeasurements = mutableListOf<SensorData>()
	
	// Volley request queue
	private lateinit var volleyRequestQueue: RequestQueue
	
	// Base64 photo data
	private var _base64PhotoData = ""
	
	// List containing results from the image recognition API call (as a list of tuples "name: value")
	private var _imageRecognitionResults = mutableListOf<Pair<String, Double>>()
	
	// Currently selected food string (to select from the list of results to get the nutritional value from)
	private var _selectedFood = ""
	
	// Currently selected food weight (in grams)
	private var _selectedFoodWeight = ""
	
	// Number of past accelerometer measurements to store
	val measurementsMemory = 3
	// Store measurements (array of a certain number of lists of 3 values) for the accelerometer measures (to show the scatterplot)
	private var _accelerometerMeasurementsArray = Array(measurementsMemory) { mutableListOf<Triple<Double, Double, Double>>() }
	
	// Store the Clarifai response
	var _requestResponse = ""
	
	// Volume change receiver (to detect volume changes)
	private lateinit var volumeChangeReceiver: VolumeChangeReceiver
	
	// Initialize the sensor manager and accelerometer sensor
	@RequiresApi(Build.VERSION_CODES.O)
	override fun onCreate(savedInstanceState: Bundle?) {
		
		super.onCreate(savedInstanceState)
		
		enableEdgeToEdge()
		
		// Customize the status bar color and background color (make sure background also extends behind the status bar and navigation bar)
		window.decorView.setBackgroundColor(getColor(R.color.main_color))
		//window.statusBarColor = getColor(R.color.main_color)
		window.statusBarColor = getColor(R.color.transparent)
		//window.navigationBarColor = getColor(R.color.main_color)
		window.navigationBarColor = getColor(R.color.transparent)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			window.isNavigationBarContrastEnforced = false
		}
		
		// Initialize the sensor manager
		sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
		// Initialize the vibrator
		vibrator = getSystemService(Vibrator::class.java)
		// Initialize the sensors
		accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
		//deviceTemperature = sensorManager.getDefaultSensor(Sensor.TYPE_TEMPERATURE)
		gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
		gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
		linearAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
		//orientation = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION)
		pressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
		rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
		ambientTemperature = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
		
		// Initialize the Volley request queue
		volleyRequestQueue = Volley.newRequestQueue(this)
		
		// Request permissions to write to external storage and create new files
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
		}
		
		// Request camera permission
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
			requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 2)
		}
		
		// Initialize the volume change receiver
		volumeChangeReceiver = VolumeChangeReceiver { volume ->
			currentPhoneVolume.value = volume
		}
		// Register the receiver
		val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
		registerReceiver(volumeChangeReceiver, filter)
		// Initialize the current phone volume
		val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
		currentPhoneVolume.value = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
		
		// Get the ingredients and weights from the saved SharedPreferences
		val sharedPref = getSharedPreferences(getString(R.string.shared_preferences_file_key), MODE_PRIVATE)
		val ingredientsArray = JSONArray(sharedPref.getString("lastIngredients", "[]"))
		val weightsArray = JSONArray(sharedPref.getString("lastWeights", "[]"))
		for (i in 0 until ingredientsArray.length()) {
			ingredientsList.add(ingredientsArray.getString(i))
		}
		for (i in 0 until weightsArray.length()) {
			weightsList.add(weightsArray.getString(i))
		}
		val savedEmail = sharedPref.getString("email", "")
		if (savedEmail != null && savedEmail.isNotEmpty()) {
			userEmail.value = savedEmail
			isLoggedIn.value = true
		}
		val savedUserInfos = sharedPref.getString("userInfos", "")
		if (savedUserInfos != null && savedUserInfos.isNotEmpty()) {
			currentUserInfosString.value = savedUserInfos
		}
		val savedFollowedUsers = sharedPref.getString("followedUsers", "")
		if (savedFollowedUsers != null && savedFollowedUsers.isNotEmpty()) {
			val followedUsersArray = JSONArray(savedFollowedUsers)
			for (i in 0 until followedUsersArray.length()) {
				followedUsers.add(followedUsersArray.getString(i))
			}
		}
		
		setContent {
			if (_debug) {
				//MobileScaleTheme {
				DrawDebugUI(
					this,
					_showRealTimeAccelerometerValues,
					sensorData.accelerometer.first,
					sensorData.accelerometer.second,
					sensorData.accelerometer.third,
					_measuring,
					_accelerometerMeasurementsArray,
					_measurementsDisplayType,
					_requestResponse,
					_selectedFood,
					_selectedFoodWeight
				)
				//}
			} else {
				DrawMainUI(
					this,
					ingredientsList,
					weightsList,
					// Pass the sensor data to the UI by reference so that it can be updated in the UI
					//sensorDataMeasurements
				)
			}
		}
	}
	
	override fun onResume() {
		super.onResume()
		// Register sensor listeners for all of the sensors
		accelerometer?.let { registerSensorListener(it) }
		//deviceTemperature?.let { registerSensorListener(it) }
		gravity?.let { registerSensorListener(it) }
		gyroscope?.let { registerSensorListener(it) }
		linearAcceleration?.let { registerSensorListener(it) }
		//orientation?.let { registerSensorListener(it) }
		pressure?.let { registerSensorListener(it) }
		rotationVector?.let { registerSensorListener(it) }
		ambientTemperature?.let { registerSensorListener(it) }
	}
	
	override fun onPause() {
		super.onPause()
		sensorManager.unregisterListener(this)
		val editor = getSharedPreferences(getString(R.string.shared_preferences_file_key), MODE_PRIVATE).edit()
		val ingredientsArray = JSONArray(ingredientsList)
		val weightsArray = JSONArray(weightsList)
		editor.putString("lastIngredients", ingredientsArray.toString())
		editor.putString("lastWeights", weightsArray.toString())
		if (isLoggedIn.value) {
			editor.putString("email", userEmail.value)
			editor.putString("userInfos", currentUserInfosString.value)
			val followedUsersArray = JSONArray(followedUsers)
			editor.putString("followedUsers", followedUsersArray.toString())
		} else {
			editor.remove("email")
			editor.remove("userInfos")
			editor.remove("followedUsers")
		}
		editor.apply()
	}
	
	override fun onDestroy() {
		super.onDestroy()
		// Unregister the volume change receiver
		unregisterReceiver(volumeChangeReceiver)
	}
	
	private fun registerSensorListener(
		sensor: Sensor,
		rate: Int = SensorManager.SENSOR_DELAY_NORMAL
	) {
		sensorManager.registerListener(this, sensor, rate)
	}
	
	override fun onSensorChanged(event: SensorEvent?) {
		
		// Check the sensor type
		when (event?.sensor?.type) {
			
			Sensor.TYPE_ACCELEROMETER -> {
				sensorData = sensorData.copy(
					accelerometer = Triple(
						event.values[0].toDouble(),
						event.values[1].toDouble(),
						event.values[2].toDouble()
					)
				)
			}
			//Sensor.TYPE_AMBIENT_TEMPERATURE -> {
			//	sensorData = sensorData.copy(
			//		ambientTemperature = event.values[0].toDouble()
			//	)
			//}
			Sensor.TYPE_GRAVITY -> {
				sensorData = sensorData.copy(
					gravity = Triple(
						event.values[0].toDouble(),
						event.values[1].toDouble(),
						event.values[2].toDouble()
					)
				)
			}
			Sensor.TYPE_GYROSCOPE -> {
				sensorData = sensorData.copy(
					gyroscope = Triple(
						event.values[0].toDouble(),
						event.values[1].toDouble(),
						event.values[2].toDouble()
					)
				)
			}
			Sensor.TYPE_LINEAR_ACCELERATION -> {
				sensorData = sensorData.copy(
					linearAcceleration = Triple(
						event.values[0].toDouble(),
						event.values[1].toDouble(),
						event.values[2].toDouble()
					)
				)
			}
			//Sensor.TYPE_PRESSURE -> {
			//	sensorData = sensorData.copy(
			//		pressure = event.values[0].toDouble()
			//	)
			//}
			Sensor.TYPE_ROTATION_VECTOR -> {
				sensorData = sensorData.copy(
					rotationVector = Triple(
						event.values[0].toDouble(),
						event.values[1].toDouble(),
						event.values[2].toDouble()
					)
				)
			}
			else -> {
				// Do nothing if the sensor type is unknown
			}
			
		}
		
		// Update the measurements array with the current sensor data
		sensorDataMeasurements.add(sensorData)
		if (sensorDataMeasurements.size >= sensorDataMeasurementsMemory) {
			sensorDataMeasurements.removeAt(0)
		}
		sensorDataMeasurementsRaw += sensorData
		if (sensorDataMeasurementsRaw.size >= sensorDataMeasurementsMemory) {
			sensorDataMeasurementsRaw = sensorDataMeasurementsRaw.drop(1)
		}
	}
	
	// NOTE: must be here, even if empty, because the MainActivity class implements the SensorEventListener interface
	override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
		// Do nothing on accuracy change...
	}
	
	@RequiresApi(Build.VERSION_CODES.O)
	private fun vibrate(duration: Long) {
		val vibrationAmplitude = 255    // In range [1, 255]
		vibrator?.vibrate(VibrationEffect.createOneShot(duration, vibrationAmplitude))
	}
	
	@RequiresApi(Build.VERSION_CODES.O)
	private fun StartMeasurements(vibrate: Boolean = true) {
		// Define vibration duration
		val duration = 10_000L    // In milliseconds
		// Start vibration
		if (vibrate) vibrate(duration)
		// Define measurements collection rate
		var measurementsRate = -1L    // Measurements per second (set to -1 to use the sensor refresh rate)
		// get the refresh rate of the accelerometer sensor (in milliseconds)
		val sensorRefreshRateHz = 200    // Sensor refresh rate in Hz
		val sensorRefreshRate = 1_000 / sensorRefreshRateHz    // Sensor refresh rate in milliseconds
		// Check if the sensor refresh rate is high enough for the measurements rate
		if (measurementsRate < 0L) {
			// If the measurements rate is too low, set it to the sensor refresh rate
			measurementsRate = sensorRefreshRate.toLong()
		} else if (sensorRefreshRate > measurementsRate) {
			// If the measurements rate is too high, set it to the sensor refresh rate
			measurementsRate = sensorRefreshRate.toLong()
		}
		// Calculate the number of measurements to collect
		val numOfMeasurements = duration * measurementsRate / 1000    // Number of inter
		// Reset the list of sensor measurements
		_sensorMeasurements = mutableListOf()
		val measurements: MutableList<Triple<Double, Double, Double>> = mutableListOf()
		_accelerometerMeasurementsArray = _accelerometerMeasurementsArray.drop(1).toTypedArray()
		_accelerometerMeasurementsArray += measurements
		val measurementsThread = Thread {
			// Set the measuring flag to true
			_measuring = true
			// Collect measurements
			for (i in 0 until numOfMeasurements) {
				// Get the current accelerometer values to show in the UI
				_accelerometerMeasurementsArray[_accelerometerMeasurementsArray.size - 1].add(Triple(
					sensorData.accelerometer.first,
					sensorData.accelerometer.second,
					sensorData.accelerometer.third)
				)
				// Store all of the sensors data in the list of sensor measurements
				_sensorMeasurements.add(sensorData)
				// Wait for the next measurement
				Thread.sleep(1000 / measurementsRate)
			}
			_measuring = false
			// Save the measurements in a file (after some delay), which is either a measurements file or a calibration file
			Thread.sleep(500)
			var realWeight = 0.0
			if (_selectedFoodWeight.isNotEmpty() && _selectedFoodWeight.toDoubleOrNull() != null) {
				realWeight = _selectedFoodWeight.toDouble()
			}
			val fileName =
				if (realWeight > 0.0) {
					// Measurements file name
					getMeasurementsFileName(measurementsFolder,1)
				} else {
					// Calibration file name
					getCalibrationFileName(measurementsFolder,1)
				}
			val calibrationFileName = getCalibrationFileName(measurementsFolder,0)
			saveMeasurementsFile(
				measurementsFolder,
				_sensorMeasurements,
				fileName,
				calibrationFileName,
				realWeight
			)
			// Modify the response text to temporarily display the last measurements file name
			val measurementNum = (fileName.split("_")[1].split(".")[0].toFloat() / 3.0).toInt()
			val subMeasurementNum = ((fileName.split("_")[1].split(".")[0].toInt()-1) % 3) + 1
			_requestResponse =
				"Weight:  $realWeight\n" +
						"File:  $fileName\n" +
						"Total:  $measurementNum  ($subMeasurementNum / 3)"
			
		}
		measurementsThread.start()
	}
	
	@Composable
	fun DisplayMeasurements(
		measurementsArray: Array<MutableList<Triple<Double, Double, Double>>>,
		measurementsDisplayType: String
	) {
		if (measurementsDisplayType == "text") {
			// Get the last measurements
			val measurements = measurementsArray.last()
			// Display numeric measurements in a scrollable list (use Card composable and LazyColumn)
			Card(
				modifier = Modifier.padding(4.dp),
			) {
				Column(
					modifier = Modifier.padding(4.dp),
					verticalArrangement = Arrangement.spacedBy(2.dp),
					horizontalAlignment = Alignment.CenterHorizontally
				) {
					Text(
						text = "Measurements:",
						modifier = Modifier.padding(2.dp),
						fontWeight = FontWeight.Bold
					)
					LazyColumn(
						modifier = Modifier
							.padding(4.dp)
							.size(320.dp, 400.dp),
						verticalArrangement = Arrangement.spacedBy(2.dp),
						horizontalAlignment = Alignment.CenterHorizontally,
					) {
						
						items(measurements.size) { index ->
							Text(
								text = "${index + 1}: <${measurements[index].first},${measurements[index].second},${measurements[index].third}>",
								//modifier = Modifier.padding(1.dp),
								fontWeight = FontWeight.Normal,
								fontSize = 7.75.sp,
								color = Color.Gray
							)
						}
					}
					Card(
						modifier = Modifier.padding(4.dp),
						colors = CardDefaults.cardColors(
							containerColor = Color.LightGray
						)
					) {
						Column(
							modifier = Modifier
								.size(320.dp, 100.dp),
							verticalArrangement = Arrangement.spacedBy(2.dp),
							horizontalAlignment = Alignment.CenterHorizontally,
						) {
							Text(
								text = "Mean (${
									measurements.isNotEmpty().let {
										if (it) measurements.size else 0
									}
								}):",
								modifier = Modifier.padding(1.dp)
							)
							Text(
								text = "X: ${
									(measurements.isNotEmpty()).let {
										if (it) measurements.map { it.first }.average() else 0.0
									}
								}",
								fontWeight = FontWeight.Normal,
								fontSize = 12.sp,
								color = Color.Gray
							)
							Text(
								text = "Y: ${
									(measurements.isNotEmpty()).let {
										if (it) measurements.map { it.second }.average() else 0.0
									}
								}",
								fontWeight = FontWeight.Normal,
								fontSize = 12.sp,
								color = Color.Gray
							)
							Text(
								text = "Z: ${
									(measurements.isNotEmpty()).let {
										if (it) measurements.map { it.third }.average() else 0.0
									}
								}",
								fontWeight = FontWeight.Normal,
								fontSize = 12.sp,
								color = Color.Gray
							)
						}
					}
				}
			}
		} else if (measurementsDisplayType == "scatterplot") {
			// Display scatter plot of previous and current measurements (usa a Card composable and a custom composable)
			Card(
				modifier = Modifier
					.padding(4.dp),
				colors = CardDefaults.cardColors(
					containerColor = Color.Gray
				)
			) {
				Column(
					modifier = Modifier.padding(4.dp),
					verticalArrangement = Arrangement.spacedBy(2.dp),
					horizontalAlignment = Alignment.CenterHorizontally
				) {
					Text(
						text = "Scatter Plot",
						modifier = Modifier.padding(2.dp),
						fontWeight = FontWeight.Bold
					)
					val scatterPlotSize = 300.dp
					val pointsSize = 3.dp
					//val range = 10.0
					//val range = 3.0
					Card (
						modifier = Modifier
							.size(scatterPlotSize),
						colors = CardDefaults.cardColors(
							containerColor = Color.White,
							contentColor = Color.Black
						)
					) {
						// Draw the scatter plot for the previous measurements
						Scatterplot(
							pointsArray = measurementsArray,
							size = scatterPlotSize,
							//xRange = -range..range,
							//yRange = -range..range,
							pointSize = pointsSize
						)
					}
				}
			}
			
		} else {
			// Don't show anything if the measurements display type is unknown
		}
	}
	
	@RequiresApi(Build.VERSION_CODES.O)
	@Composable
	fun DrawDebugUI(
		context: Context,
		showRealTimeAccelerometerValues: Boolean,
		x: Double,
		y: Double,
		z: Double,
		measuring: Boolean,
		measurementsArray: Array<MutableList<Triple<Double, Double, Double>>>,
		measurementsDisplayType: String,
		requestResponse: String,
		currentFoodName : String,
		currentFoodWeight : String
	) {
		Card (
			modifier = Modifier
				.fillMaxSize()
				.padding(15.dp, 45.dp, 15.dp, 45.dp),
			colors = CardDefaults.cardColors(
				containerColor = if (measuring) Color.Red else Color.Black,
				contentColor = Color.White
			)
		) {
			// Main container of the UI of the app
			Column(
				modifier = Modifier
					//.fillMaxSize()
					.padding(10.dp),
				verticalArrangement = Arrangement.Top,
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				// Show the current acceleration values
				if (showRealTimeAccelerometerValues) {
					Text(
						text = "Real-time Accelerometer Values",
						modifier = Modifier.padding(2.dp),
						fontWeight = FontWeight.Bold
					)
					Text(
						text = "X: $x",
						modifier = Modifier.padding(2.dp)
					)
					Text(
						text = "Y: $y",
						modifier = Modifier.padding(2.dp)
					)
					Text(
						text = "Z: $z",
						modifier = Modifier.padding(2.dp)
					)
				}
				// Show the collected measurements either as a scatter plot or in a scrollable list (use Card composable and LazyColumn)
				DisplayMeasurements(measurementsArray, measurementsDisplayType)
				// Show buttons in a row
				Row(
					modifier = Modifier.padding(0.dp),
					horizontalArrangement = Arrangement.spacedBy(3.dp)
				) {
					// Show a button to delete the last measurements file (after a confirmation dialog)
					Button(
						//shape = RoundedCornerShape(1000.dp),
						border = BorderStroke(3.dp, Color.Gray),
						colors = ButtonDefaults.buttonColors(
							containerColor = Color.Gray,
							contentColor = Color.White
						),
						enabled = !measuring,
						onClick = {
							// Delete the last measurements file
							val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString() + measurementsFolder)
							val lastMeasurementsFileName = getMeasurementsFileName(measurementsFolder,0)
							val lastMeasurementsFile = File(folder, lastMeasurementsFileName)
							val lastMeasurementsFileExists = lastMeasurementsFile.exists()
							if (lastMeasurementsFileExists) {
								// Show a confirmation dialog to delete the last measurements file
								// Rename the old measurements file to "deleted_<old_file_name>"
								val deletedMeasurementsFile = lastMeasurementsFileName.replace("measurements", "deleted_m")
								val deletedMeasurementsFileObj = File(folder, deletedMeasurementsFile)
								lastMeasurementsFile.renameTo(deletedMeasurementsFileObj)
								// Update the response text to show the deleted file name
								_requestResponse =
									"Deleted file: $lastMeasurementsFileName\nRenamed to: $deletedMeasurementsFile"
							} else {
								// Update the response text to show that the file does not exist
								_requestResponse = "File does not exist: $lastMeasurementsFileName"
							}
						}
					) {
						Text(
							text = "Delete",
							modifier = Modifier.padding(4.dp)
						)
					}
					// Show a button to switch between vibrating while measuring and not vibrating
					Button(
						// Show the button to be full with a border if currently vibrating while measuring, and empty with a border if not vibrating
						//shape = RoundedCornerShape(1000.dp),
						border = BorderStroke(3.dp, Color.Gray),
						colors = ButtonDefaults.buttonColors(
							containerColor = if (_vibrateWhileMeasuring) Color.Gray else Color.Transparent,
							contentColor = if (_vibrateWhileMeasuring) Color.White else Color.Gray,
						),
						onClick = {
							_vibrateWhileMeasuring = !_vibrateWhileMeasuring
						}
					) {
						Text(
							text = "Vibration",
							modifier = Modifier
								.padding(4.dp)
						)
					}
					// Show a button to start measuring
					Button(
						//shape = RoundedCornerShape(1000.dp),
						border = BorderStroke(3.dp, Color.Gray),
						colors = ButtonDefaults.buttonColors(
							containerColor = Color.Gray,
							contentColor = Color.White
						),
						enabled = !measuring,
						onClick = {
							// Start measurements
							StartMeasurements(_vibrateWhileMeasuring)
						}
					) {
						Text(
							text = "Start",
							modifier = Modifier.padding(4.dp)
						)
					}
					
				}
				// Show a row of buttons to capture a photo and send an API request to get the nutritional value of the food
				Row(
					modifier = Modifier.padding(2.dp),
					horizontalArrangement = Arrangement.spacedBy(3.dp)
				) {
					// Show a button to open the camera to capture a photo
					Button(
						//shape = RoundedCornerShape(1000.dp),
						border = BorderStroke(3.dp, Color.Gray),
						colors = ButtonDefaults.buttonColors(
							containerColor = Color.Gray,
							contentColor = Color.White
						),
						enabled = !measuring,
						onClick = {
							// Open the camera to capture a photo
							setContent {
								//MobileScaleTheme {
								CameraPreview(
									onImageCaptured = { photoFile ->
										CoroutineScope(Dispatchers.Main).launch {
											// Check if we have a photo file (if not, do nothing)
											if (photoFile == null) {
												Log.d(
													"ERROR: photo file is null",
													"photo file is null"
												)
												return@launch
											}
											// Get image byte data
											val byteData = photoFile.readBytes()
											//val base64Data = photoFile.readBytes().encodeBase64()
											val apiType =
												"foodvisor"    // Options are "clarifai" or "foodvisor"
											val response = SendImageRecognitionAPIRequest(
												volleyRequestQueue,
												byteData
											)
											_requestResponse = response
											// Store the request response results in the image recognition results
											if (apiType == "clarifai") {
												/*
													Clarifai response format:
														"concept1: value1
														concept2: value2
														concept3: value3
														...
														"
													(lines containing "concept: value" pairs, where concept is a food or other general item name and value is a double representing the confidence/probability of the recognized concept)
													 */
												_imageRecognitionResults = mutableListOf()
												val concepts = response.split("\n")
												for (concept in concepts) {
													if (concept.isEmpty()) continue
													if (!concept.contains(": ")) {
														// Log the concept if it does not contain the separator ": "
														Log.d(
															"WARNING: found 'concept' in response without separator ': '",
															concept
														)
														continue
													}
													val nameValue = concept.split(": ")
													_imageRecognitionResults.add(
														Pair(
															nameValue[0],
															nameValue[1].toDouble()
														)
													)
												}
												// Store the first match in the selected food
												if (_imageRecognitionResults.isNotEmpty()) {
													_selectedFood =
														_imageRecognitionResults[0].first
												} else {
													_selectedFood = "unknown"
													Log.d(
														"WARNING: no concepts found in response:\n",
														response
													)
												}
											} else if (apiType == "foodvisor") {
												/*
													Foodvisor response format:
													{
													  "analysis_id": "bc7409e3-b182-4bf2-a074-8bb0d4641c58",
													  "scopes": [
														"multiple_items",
														"nutrition:macro",
														"nutrition:micro",
														"nutrition:nutriscore"
													  ],
													  "items": [
														"..."
													  ]
													}
													 */
												// Parse the JSON response and extract the first item name
												val jsonResponse = JSONObject(response)
												val items = jsonResponse.getJSONArray("items")
												if (items.length() > 0) {
													// Get the first analysis response item
													val firstItem = items.getJSONObject(0)
													// Food name is found in firstItem["food"]["food_info"]["display_name"]
													// NOTE: the "food" could either be a JSON object or a JSON array
													// 	- if it is an array, the first item is the one we are looking for
													// 	- if it is an object, the object itself is the one we are looking for
													// Check if the "food" is an array or an object
													val food = firstItem.get("food")
													if (food is JSONArray) {
														// Get the first item in the array
														val firstFood = food.getJSONObject(0)
														// Get the food name
														_selectedFood =
															firstFood.getJSONObject("food_info")
																.getString("display_name")
													} else if (food is JSONObject) {
														// Get the food name
														_selectedFood =
															food.getJSONObject("food_info")
																.getString("display_name")
													} else {
														_selectedFood = "unknown"
														Log.d(
															"WARNING: unknown 'food' type in response: ",
															food.toString()
														)
													}
												} else {
													_selectedFood = "unknown"
													Log.d(
														"WARNING: no items found in response: ",
														response
													)
												}
											} else {
												Log.d("ERROR: unknown API type", apiType)
												_selectedFood = "unknown"
											}
										}
										// Update the "waiting for response" flag
										_requestResponse = "Waiting for response..."
									},
									onClose = {
										// Reset the main UI
										setContent {
											//MobileScaleTheme {
											DrawDebugUI(
												context,
												_showRealTimeAccelerometerValues,
												sensorData.accelerometer.first,
												sensorData.accelerometer.second,
												sensorData.accelerometer.third,
												_measuring,
												_accelerometerMeasurementsArray,
												_measurementsDisplayType,
												_requestResponse,
												_selectedFood,
												_selectedFoodWeight
											)
											//}
										}
									}
								)
							}
							//}
						}
					) {
						Text(
							text = "Photo",
							modifier = Modifier.padding(4.dp)
						)
					}
					// Show a button to send an API request to Spoonacular for a sample request of a food nutritional value
					Button(
						//shape = RoundedCornerShape(1000.dp),
						border = BorderStroke(3.dp, Color.Gray),
						colors = ButtonDefaults.buttonColors(
							containerColor = Color.Gray,
							contentColor = Color.White
						),
						enabled = !measuring,
						onClick = {
							// Send an API request to Spoonacular for a sample request of a food nutritional value
							CoroutineScope(Dispatchers.Main).launch {
								// Send the request (with the selected food) and store its response
								val response = SendFoodNutritionalValueAPIRequest(
									volleyRequestQueue,
									_selectedFood
								)
								_requestResponse = response
							}
						}
					) {
						Text(
							text = "Food",
							modifier = Modifier.padding(4.dp)
						)
					}
				}
				// Show a row with 2 text fields (use TextField) to enter a food name and a weight (in grams)
				Row(
					modifier = Modifier
						.padding(2.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(3.dp)
				) {
					
					// TextField for entering the food name
					TextField(
						value = currentFoodName,
						onValueChange = {
							_selectedFood = it
						},
						label = { Text(
							text = "Food Name",
							//modifier = Modifier
						)},
						modifier = Modifier
							.size(155.dp, 55.dp)
							.padding(1.dp),
						singleLine = true
					)
					// TextField for entering the food weight
					TextField(
						value = currentFoodWeight,
						onValueChange = {
							_selectedFoodWeight = it
						},
						label = {Text(
							text = "Weight (grams)",
							//modifier = Modifier
						)},
						modifier = Modifier
							.size(155.dp, 55.dp)
							.padding(1.dp),
						singleLine = true
					)
				}
				// Show an additional card with the Clarifai response
				Card(
					modifier = Modifier
						.size(320.dp, 220.dp)
						.padding(10.dp, 10.dp, 10.dp, 10.dp),
					colors = CardDefaults.cardColors(
						containerColor = Color.Gray,
						contentColor = Color.White
					)
				) {
					Column(
						modifier = Modifier
							.padding(10.dp),
						verticalArrangement = Arrangement.Top,
						horizontalAlignment = Alignment.CenterHorizontally
					) {
						Text(
							text = "Response",
							modifier = Modifier.padding(2.dp),
							fontWeight = FontWeight.Bold
						)
						// Show text in a scrollable LazyColumn element
						LazyColumn(
							modifier = Modifier
								.padding(4.dp)
								.size(320.dp, 180.dp),
							verticalArrangement = Arrangement.spacedBy(2.dp),
							horizontalAlignment = Alignment.CenterHorizontally,
						) {
							item {
								Text(
									text = requestResponse,
									fontWeight = FontWeight.Normal,
									fontSize = 12.sp,
									color = Color.White
								)
							}
						}
					}
				}
			}
		}
	}
	
}

/*
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DrawMainUI_OLD(
	context: Context,
	//measuring: Boolean,
	//current_items: MutableList<String>,
	//current_weights: MutableList<String>,
	//measurementsArray: Array<MutableList<Triple<Double, Double, Double>>>,
	//requestResponse: String,
	//currentFoodName : String,
	//currentFoodWeight : String
) {
	// Variables
	val current_items = remember { mutableListOf(*_foodItems.toTypedArray()) }
	val current_weights = remember { mutableListOf( *_itemWeights.toTypedArray()) }
	@Composable
	fun DrawItemCard(
		context: Context,
		itemIndex: Int
	) {
		val itemName = remember { mutableStateOf(_foodItems[itemIndex]) }
		val itemWeight = remember { mutableStateOf(_itemWeights[itemIndex]) }
		val textInputPadding = 7.dp
		Card(
			modifier = Modifier
				//.size(200.dp, 200.dp)
				.fillMaxWidth()
				.padding(10.dp),
			border = BorderStroke(2.dp, Color(ContextCompat.getColor(context, R.color.main_color))),
			colors = CardDefaults.cardColors(
				containerColor = Color.White,
				contentColor = Color.Black
			),
			shape = RoundedCornerShape(10.dp),
			elevation = CardDefaults.cardElevation(
				defaultElevation = 0.dp,
				pressedElevation = 0.dp
			)
		) {
			Column(
				modifier = Modifier.padding(10.dp),
				verticalArrangement = Arrangement.spacedBy(0.dp),
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				// First row with item name
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(5.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(0.dp)
				) {
					// Show the food name and weight as editable text fields (outlined text fields
					OutlinedTextField(
						value = itemName.value,
						placeholder = { Text("Food Name") },
						onValueChange = {
							// Update the food name
							_foodItems[itemIndex] = it
							// Update the food name in the UI
							itemName.value = it
						},
						modifier = Modifier
							.size(160.dp, 52.dp)
							.padding(0.dp),
						shape = RoundedCornerShape(5.dp, 0.dp, 0.dp, 5.dp),
						singleLine = true,
						colors = OutlinedTextFieldDefaults.colors(
							//focusedBorderColor = Color(ContextCompat.getColor(context, R.color.main_color_light)),
							//unfocusedBorderColor = Color(ContextCompat.getColor(context, R.color.main_color)),
							focusedBorderColor = Color.Gray,
							unfocusedBorderColor = Color.Gray,
						)
					)
					// Show a button to start the weight measurement process
					Button(
						border = BorderStroke(3.dp, Color.Gray),
						colors = ButtonDefaults.buttonColors(
							containerColor = Color.Gray,
							contentColor = Color.White
						),
						modifier = Modifier
							//.fillMaxWidth()
							.size(80.dp, 52.dp)
							.padding(0.dp),
						shape = RoundedCornerShape(0.dp, textInputPadding, textInputPadding, 0.dp),
						enabled = !_measuring,
						contentPadding = PaddingValues(0.dp),
						onClick = {
							// TO DO
						}
					) {
						Text(
							text = "PHOTO",
							modifier = Modifier
								.fillMaxWidth()
								.padding(4.dp, 4.dp, 5.dp, 4.dp),
							textAlign = TextAlign.Center,
							//fontWeight = FontWeight.Bold,
							fontSize = 15.sp
						
						)
					}
				}
				// Second row with item weight
				Row (
					modifier = Modifier
						.fillMaxWidth()
						.padding(5.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(0.dp)
				) {
					// Show the food weight as an editable text field (outlined text field)
					OutlinedTextField(
						value = itemWeight.value,
						placeholder = { Text("Weight (grams)") },
						onValueChange = {
							// Update the food weight
							_itemWeights[itemIndex] = it
							// Update the food weight in the UI
							itemWeight.value = it
						},
						modifier = Modifier
							//.fillMaxWidth()
							.size(160.dp, 52.dp)
							.padding(0.dp),
						shape = RoundedCornerShape(textInputPadding, 0.dp, 0.dp, textInputPadding),
						singleLine = true,
						colors = OutlinedTextFieldDefaults.colors(
							//focusedBorderColor = Color(ContextCompat.getColor(context, R.color.main_color_light)),
							//unfocusedBorderColor = Color(ContextCompat.getColor(context, R.color.main_color)),
							focusedBorderColor = Color.Gray,
							unfocusedBorderColor = Color.Gray,
						)
					)
					// Show a button to start the weight measurement process
					Button(
						border = BorderStroke(3.dp, Color.Gray),
						colors = ButtonDefaults.buttonColors(
							containerColor = Color.Gray,
							contentColor = Color.White
						),
						modifier = Modifier
							//.fillMaxWidth()
							.size(80.dp, 52.dp)
							.padding(0.dp),
						shape = RoundedCornerShape(0.dp, textInputPadding, textInputPadding, 0.dp),
						enabled = !_measuring,
						contentPadding = PaddingValues(0.dp),
						onClick = {
							// TO DO
						}
					) {
						Text(
							text = "SCALE",
							modifier = Modifier
								.fillMaxWidth()
								.padding(4.dp, 4.dp, 5.dp, 4.dp),
							textAlign = TextAlign.Center,
							//fontWeight = FontWeight.Bold,
							fontSize = 15.sp
						)
					}
				}
				
			}
		}
	}
	// Main container of the UI of the app
	Column (
		modifier = Modifier
			.fillMaxSize()
			.padding(15.dp, 45.dp, 15.dp, 45.dp),
	) {
		// Main title card
		Card (
			modifier = Modifier
				.fillMaxWidth()
				.padding(10.dp)
			,
			colors = CardDefaults.cardColors(
				containerColor = Color.White,
				contentColor = Color.Black
			),
			// Add elevation to the card (to show a shadow)
			elevation = CardDefaults.cardElevation(
				defaultElevation = 10.dp,
				pressedElevation = 10.dp
			)
		) {
			Column(
				modifier =
				Modifier
					.padding(10.dp)
					.fillMaxWidth(),
				verticalArrangement = Arrangement.Center,
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				// Title text
				Text(
					modifier = Modifier.padding(3.dp),
					text = "Food Pal",
					fontSize = 24.sp,
					fontWeight = FontWeight.Bold,
					textAlign = TextAlign.Center,
					color = Color(ContextCompat.getColor(context, R.color.main_color))
				)
				// Subtitle text
				Text(
					modifier = Modifier.padding(3.dp),
					text = "Your personal food assistant",
					fontSize = 16.sp,
					fontWeight = FontWeight.Normal,
					textAlign = TextAlign.Center,
					color = Color(ContextCompat.getColor(context, R.color.main_color))
				)
			}
		}
		// Card with the list of food items
		Card (
			modifier = Modifier
				.fillMaxSize()
				.padding(10.dp),
			colors = CardDefaults.cardColors(
				containerColor = Color.White,
				contentColor = Color.Black
			),
			// Add elevation to the card (to show a shadow)
			elevation = CardDefaults.cardElevation(
				defaultElevation = 10.dp,
				pressedElevation = 10.dp
			)
		) {
			Column(
				modifier = Modifier.padding(10.dp),
				verticalArrangement = Arrangement.spacedBy(2.dp),
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				// Title text
				Text(
					modifier = Modifier.padding(3.dp),
					text = "Food Items",
					fontSize = 18.sp,
					fontWeight = FontWeight.Bold,
					textAlign = TextAlign.Center,
					color = Color(ContextCompat.getColor(context, R.color.main_color))
				)
				// Show the list of food items in a scrollable LazyColumn element
				LazyColumn(
					modifier = Modifier
						.padding(2.dp)
						.fillMaxWidth()
						.size(320.dp, 400.dp),
					verticalArrangement = Arrangement.spacedBy(0.dp),
					horizontalAlignment = Alignment.CenterHorizontally,
				) {
					items(current_items.size) { index ->
						DrawItemCard(
							context,
							index
							//current_items[index],
							//current_weights[index]
						)
					}
				}
				// Show a button to add a new food item
				Button(
					//shape = RoundedCornerShape(1000.dp),
					border = BorderStroke(3.dp, Color.Gray),
					colors = ButtonDefaults.buttonColors(
						containerColor = Color.Gray,
						contentColor = Color.White
					),
					enabled = !measuring,
					onClick = {
						// Add a new food item to the list
						current_items.add("")
						current_weights.add("")
						_foodItems.add("")
						_itemWeights.add("")
					}
				) {
					Text(
						text = "Add Food Item",
						modifier = Modifier.padding(4.dp)
					)
				}
				// Show a debug text with the current food names and weights
				Text(
					text = "Current Food Items: ${current_items.joinToString()}\nCurrent Food Weights: ${current_weights.joinToString()}",
					fontWeight = FontWeight.Normal,
					fontSize = 8.sp,
					color = Color.Gray
				)
				
			}
		}
	}
	
}
*/

@Composable
fun CustomButton(
	text: String,
	filled: Boolean,
	color:Color,
	textColor:Color,
	customShape : Shape,
	fillWidth: Boolean,
	sizeX: Dp,
	sizeY:Dp = 45.dp,
	onClick: () -> Unit,
	enabled: Boolean = true,
	fontSize : TextUnit = 14.sp
) {
	//val colorCode = context.getColor(R.color.main_color)
	//val color = Color(colorCode)
	Button(
		onClick = onClick,
		modifier =
		if (fillWidth)
			Modifier
				.size(sizeX, sizeY)
				.padding(0.dp)
				.fillMaxWidth()
		else
			Modifier
				.size(sizeX, sizeY)
				.padding(0.dp)
		,
		shape = customShape,
		colors = ButtonDefaults.buttonColors(
			containerColor = if (filled) color else Color.Transparent,
			disabledContainerColor = if (filled) color.copy(alpha = 0.5f) else Color.Transparent,
			contentColor = textColor,
			disabledContentColor = textColor.copy(alpha = 0.5f)
		),
		border = if (enabled) BorderStroke(2.dp, color) else BorderStroke(2.dp, color.copy(alpha = 0.5f)),
		enabled = enabled
	) {
		Text(
			text = text,
			modifier = Modifier
				.fillMaxWidth()
				.padding(0.dp),
			softWrap = false,
			textAlign = TextAlign.Center,
			fontWeight = FontWeight.Bold,
			fontSize = fontSize
		)
	}
}

// State of the UI
enum class UIStates {
	Main,
	Camera,
	Scale,
	Infos,
	ScaleResults,
	Login,
	Profile
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DrawMainUI(
	context: Context,
	_ingredients: MutableList<String>,
	_weights: MutableList<String>,
	//sensorDataMeasurements : MutableList<SensorData>
) {
	
	// State of the UI and other flags
	val currentUIState = remember { mutableStateOf(UIStates.Main) }
	val showLoadingScreen = remember { mutableStateOf(false) }
	val tryingToMeasureScale = remember { mutableStateOf(false) }
	val measuredSuccessfully = remember { mutableStateOf(false) }
	
	// Debug text
	val showDebugText = remember { mutableStateOf(false) }
	val debugText = remember { mutableStateOf("Debug text") }
	// Random number for testing
	var rand = remember { mutableStateOf(0) }
	
	// Local state for ingredients and weights
	//val ingredients = remember { mutableStateListOf("Apple", "Banana", "Orange") }
	//val weights = remember { mutableStateListOf("100", "200", "300") }
	val ingredients = remember { mutableStateListOf(*_ingredients.toTypedArray()) }
	val weights = remember { mutableStateListOf(*_weights.toTypedArray()) }
	
	// Currently selected ingredient index to show the corresponding infos
	val selectedIngredientIndex = remember { mutableStateOf(-1) }
	// List of <nutrient, value_per_100g, unit> for the currently selected ingredient(s)
	var ingredientNutritionalInfos = remember { mutableStateListOf<Triple<String, Double, String>>() }
	
	// Predicted weight
	val predictedWeight = remember { mutableStateOf(0L) }
	
	// Style variables
	val cardsElevation = 0.dp
	val cardShape = RoundedCornerShape(14.dp)
	val elementsShape = RoundedCornerShape(8.dp)
	val buttonsShape = RoundedCornerShape(100)
	
	// Volley request queue (for API requests)
	val volleyRequestQueue = Volley.newRequestQueue(context)
	
	// Scale sensitivity
	val scaleSensitivity = remember { mutableStateOf( 1500f) }
	
	// State to hold the result of CheckCanStartWeightMeasurement
	//val canStartMeasuring = remember { mutableStateOf(Pair(false, "")) }
	//// Update the state whenever the sensor data measurements change
	//LaunchedEffect(sensorDataMeasurements) {
	//	canStartMeasuring.value = CheckCanStartWeightMeasurement(sensorDataMeasurements)
	//	debugText.value = canStartMeasuring.value.second
	//}
	
	@Composable
	fun CustomInputField(
		value: String,
		onValueChange: (String) -> Unit,
		label: String,
		sizeX: Dp,
		fillWidth: Boolean = false,
		isNumeric: Boolean = false,
		customShape: Shape = elementsShape,
		enabled: Boolean = true
	) {
		val textFieldHeight = 55.dp
		val textInputPadding = 0.dp
		val containerColor = Color(0xFFEFEFEF)
		TextField(
			value = value,
			onValueChange = onValueChange,
			label = { Text(label) },
			//placeholder = { Text("Enter $label") },
			modifier =
			if (fillWidth)
				Modifier
					.fillMaxWidth()
					.size(sizeX, textFieldHeight)
					.padding(textInputPadding)
					.background(Color.White)
			else
				Modifier
					.size(sizeX, textFieldHeight)
					.padding(textInputPadding)
					.background(Color.White),
			shape = customShape,
			singleLine = true,
			// Make label, text and placeholder text gray (don't use "TextFieldDefaults.textFieldColors" which is deprecated)
			colors = TextFieldDefaults.colors().copy(
				focusedLabelColor = Color.Gray,
				unfocusedLabelColor = Color.Gray,
				focusedIndicatorColor = Color.Transparent,
				unfocusedIndicatorColor = Color.Transparent,
				focusedTextColor = Color.Black,
				unfocusedTextColor = Color.Black,
				cursorColor = Color.Black,
				errorLabelColor = Color.Red,
				focusedContainerColor = containerColor,
				unfocusedContainerColor = containerColor,
				disabledTextColor = Color.Gray,
				disabledLabelColor = Color.Gray,
				disabledIndicatorColor = Color.Transparent,
				errorIndicatorColor = Color.Red
			),
			keyboardOptions = if (isNumeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
			enabled = enabled
		)
	}
	
	@Composable
	fun DrawItemCard(
		itemIndex: Int,
		onRemove: (Int) -> Unit
	) {
		val itemName = remember { mutableStateOf(ingredients[itemIndex]) }
		val itemWeight = remember { mutableStateOf(weights[itemIndex]) }
		
		Card(
			modifier = Modifier
				//.size(200.dp, 200.dp)
				.fillMaxWidth()
				.padding(5.dp, 0.dp),
			//border = BorderStroke(2.dp, Color(ContextCompat.getColor(context, R.color.main_color))),
			colors = CardDefaults.cardColors(
				containerColor = Color.White,
				contentColor = Color.Black
			),
			shape = cardShape,
			elevation = CardDefaults.cardElevation(
				defaultElevation = cardsElevation,
				pressedElevation = cardsElevation
			)
		) {
			val rowsPadding = 3.dp
			Column(
				modifier = Modifier.padding(10.dp),
				verticalArrangement = Arrangement.spacedBy(0.dp),
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				// First row with item name
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(rowsPadding),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(0.dp)
				) {
					// Show the food name as an editable text field (outlined text field)
					CustomInputField(
						value = itemName.value,
						onValueChange = { newValue ->
							itemName.value = newValue
							ingredients[itemIndex] = newValue
							ingredientsList[itemIndex] = newValue
						},
						label = "Name",
						sizeX = 200.dp,
						fillWidth = true
					)
				}
				// Second row with item weight
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(rowsPadding),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(0.dp)
				) {
					CustomInputField(
						value = itemWeight.value,
						onValueChange = { newValue ->
							itemWeight.value = newValue
							weights[itemIndex] = newValue
							weightsList[itemIndex] = newValue
						},
						label = "Weight (grams)",
						sizeX = 190.dp,
						fillWidth = false,
						isNumeric = true,
						customShape = elementsShape.copy(
							topEnd = CornerSize(0.dp),
							bottomEnd = CornerSize(0.dp)
						)
					)
					CustomButton(
						text = "SCALE",
						filled = true,
						Color(ContextCompat.getColor(context, R.color.main_color)),
						Color.White,
						fillWidth = false,
						sizeX = 95.dp,
						sizeY = 55.dp,
						customShape = elementsShape.copy(
							topStart = CornerSize(0.dp),
							bottomStart = CornerSize(0.dp)
						),
						onClick = {
							// Show the scale screen
							currentUIState.value = UIStates.Scale
							// Set the selected ingredient index
							selectedIngredientIndex.value = itemIndex
							// Reset the scale sensitivity
							scaleSensitivity.value = 1500f;
						}
					)
				}
				// Row with buttons to remove the item and see item infos
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(rowsPadding),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(rowsPadding * 2)
				) {
					CustomButton(
						text = "REMOVE",
						filled = false,
						Color(ContextCompat.getColor(context, R.color.main_color)),
						Color(ContextCompat.getColor(context, R.color.main_color)),
						//Color.White,
						buttonsShape,
						fillWidth = false,
						sizeX = 140.dp,
						onClick = {
							onRemove(itemIndex)
						}
					)
					CustomButton(
						text = "INFO",
						filled = true,
						Color(ContextCompat.getColor(context, R.color.main_color)),
						//Color(ContextCompat.getColor(context, R.color.main_color)),
						Color.White,
						buttonsShape,
						fillWidth = false,
						sizeX = 140.dp,
						onClick = {
							// Call the spoonacular API to get the nutritional infos for the selected ingredient
							CoroutineScope(Dispatchers.Main).launch {
								// Send the request (with the selected food) and store its response
								val response = SendFoodNutritionalValueAPIRequest(
									volleyRequestQueue,
									ingredients[itemIndex]
								)
								debugText.value = response
								// Parse the response and store the nutritional infos
								var nutritionalValues = ParseFoodNutritionalValuesAPIResponse(response)
								// Weight the nutritional values by the weight of the ingredient
								val weight = weights[itemIndex].toDouble().toLong()
								var weightedNutritionalValues = mutableListOf<Triple<String, Double, String>>()
								for (i in nutritionalValues.indices) {
									val (nutrient, value, unit) = nutritionalValues[i]
									weightedNutritionalValues.add(Triple(nutrient, value * weight / 100.0, unit))
								}
								// Update the nutritional infos for the selected ingredient
								ingredientNutritionalInfos.clear()
								ingredientNutritionalInfos.addAll(weightedNutritionalValues)
								// Hide the loading screen
								showLoadingScreen.value = false
								// Update the UI state to show the infos screen
								currentUIState.value = UIStates.Infos
								sentIngredientsToServerText.value = ""
							}
							// Update the selected ingredient index
							selectedIngredientIndex.value = itemIndex
							// Show the loading screen
							debugText.value = "Waiting for response..."
							showLoadingScreen.value = true
						}
					)
				}
			}
		}
	}
	
	@Composable
	fun DrawItemTag(itemName : String, itemWeight : String) {
		Row (
			modifier = Modifier
				.background(Color.Transparent),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(0.dp)
		) {
			val tagColor1 = Color(0xFFB4B4B4)
			val tagColor2 = Color(0xFF8F8F8F)
			val tagFontSize = 11.sp
			Card (
				//modifier = Modifier
				//	.padding(5.dp),
				shape = RoundedCornerShape(
					topStart = 5.dp,
					topEnd = 0.dp,
					bottomStart = 5.dp,
					bottomEnd = 0.dp
				),
				colors = CardDefaults.cardColors(
					containerColor = tagColor1,
					contentColor = Color.White
				),
			) {
				Text(
					text = itemName.uppercase(),
					modifier = Modifier.padding(8.dp,6.dp,5.dp,6.dp),
					fontSize = tagFontSize,
					fontWeight = FontWeight.Bold,
					color = Color.White
				)
			}
			Card (
				//modifier = Modifier
				//	.padding(5.dp),
				shape = RoundedCornerShape(
					topStart = 0.dp,
					topEnd = 5.dp,
					bottomStart = 0.dp,
					bottomEnd = 5.dp
				),
				colors = CardDefaults.cardColors(
					containerColor = tagColor2,
					contentColor = Color.White
				),
			) {
				Text(
					text = "${itemWeight}g",
					modifier = Modifier
						.padding(5.dp,6.dp,8.dp,6.dp),
					fontSize = tagFontSize,
					fontWeight = FontWeight.Normal,
					color = Color.White
				)
			}
			
		}
	}
	
	@Composable
	fun DrawMacronutrientsBar(
		carbsWeight: Float,
		fatsWeight: Float,
		proteinWeight: Float,
		showText: Boolean = false
	) {
		if (carbsWeight < 0 || fatsWeight < 0 || proteinWeight < 0) {
			return
		}
		// Show a rounded corner bar with, inside it, the macronutriens in the following order: carbs, fats, proteins (with bars proportional to the weight of each macronutrient)
		val totalWeight = carbsWeight + fatsWeight + proteinWeight
		val minWeightValue = 0.1f
		var carbsWidth = (carbsWeight / totalWeight) * 100
		if (carbsWidth <= minWeightValue) carbsWidth = minWeightValue
		var fatsWidth = (fatsWeight / totalWeight) * 100
		if (fatsWidth <= minWeightValue) fatsWidth = minWeightValue
		var proteinWidth = (proteinWeight / totalWeight) * 100
		if (proteinWidth <= minWeightValue) proteinWidth = minWeightValue
		val barHeight = 24.dp
		val fontSize = 14.sp
		// Draw the macronutrients bar
		Row(
			modifier = Modifier
				//.size(320.dp, barHeight)
				//.weight(1f)
				.fillMaxWidth()
				.padding(5.dp, 0.dp)
				.clip(RoundedCornerShape(3.dp)),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(0.dp)
		) {
			// Carbs bar
			Box(
				modifier = Modifier
					.fillMaxWidth(carbsWidth)
					.weight(carbsWidth,true)
					.height(barHeight)
					.background(macronutrientsColors[0])
			) {
				if (showText) {
					Text(
						text = "Carbs",
						modifier = Modifier
							.fillMaxWidth()
							.padding(5.dp),
						fontSize = fontSize,
						fontWeight = FontWeight.Bold,
						color = Color.White
					)
				}
			}
			// Fats bar
			Box(
				modifier = Modifier
					.fillMaxWidth(fatsWidth)
					.weight(fatsWidth,true)
					.height(barHeight)
					.background(macronutrientsColors[1])
			) {
				if (showText) {
					Text(
						text = "Fats",
						modifier = Modifier
							.fillMaxWidth()
							.padding(5.dp),
						fontSize = fontSize,
						fontWeight = FontWeight.Bold,
						color = Color.White
					)
				}
			}
			// Proteins bar
			Box(
				modifier = Modifier
					.fillMaxWidth(proteinWidth)
					.weight(proteinWidth,true)
					.height(barHeight)
					.background(macronutrientsColors[2])
			) {
				if (showText) {
					Text(
						text = "Proteins",
						modifier = Modifier
							.fillMaxWidth()
							.padding(5.dp),
						fontSize = fontSize,
						fontWeight = FontWeight.Bold,
						color = Color.White
					)
				}
			}
		}
		
	}
	
	@Composable
	fun DrawUserCard(
		followedUserIndex : Int,
		userInfosString : String
	) {
		// For now, simply display text with the user infos
		//Log.d("UserInfos", "$userInfosString")
		//Text(
		//	text = userInfosString,
		//	fontSize = 10.sp,
		//	fontWeight = FontWeight.Normal,
		//	color = Color.Black
		//)
		// Get the user infos JSON object
		var userInfosJSON = JSONObject()
		val aggregatedInfos = JSONObject()
		if (userInfosString.isNotEmpty() && userInfosString != "null" && userInfosString != "[]") {
			/*
			User infos JSON format:
			{
				"infos": [
					"{\"calories\":654,\"weight\":230,\"carbs\":41.34,\"fats\":50.153,\"proteins\":21.704}","{\"calories\":1418,\"weight\":700,\"carbs\":112.2,\"fats\":100.64999999999999,\"proteins\":43.699999999999996}"
				],
				"username":"valeriodstfn"
			}
			*/
			var userInfosStrings: JSONArray
			try {
				Log.d("UserInfos", "Trying to parse JSON object: $userInfosString")
				userInfosJSON = JSONObject(userInfosString)
				Log.d("UserInfos", "Parsed JSON object: $userInfosJSON")
				userInfosStrings = userInfosJSON.getJSONArray("infos")
			} catch (e: JSONException) {
				Log.d("UserInfos", "Trying to parse JSON array: $userInfosString")
				userInfosStrings = JSONArray(userInfosString)
			}
			// Parse the strings in the user infos array as JSON objects
			for (i in 0 until userInfosStrings.length()) {
				val singleInfosJSON = JSONObject(userInfosStrings.getString(i))
				val calories = singleInfosJSON.getDouble("calories")
				val weight = singleInfosJSON.getDouble("weight")
				val carbs = singleInfosJSON.getDouble("carbs")
				val fats = singleInfosJSON.getDouble("fats")
				val proteins = singleInfosJSON.getDouble("proteins")
				// Aggregate the infos
				aggregatedInfos.put("calories", aggregatedInfos.optDouble("calories", 0.0) + calories)
				aggregatedInfos.put("weight", aggregatedInfos.optDouble("weight", 0.0) + weight)
				aggregatedInfos.put("carbs", aggregatedInfos.optDouble("carbs", 0.0) + carbs)
				aggregatedInfos.put("fats", aggregatedInfos.optDouble("fats", 0.0) + fats)
				aggregatedInfos.put("proteins", aggregatedInfos.optDouble("proteins", 0.0) + proteins)
			}
		}
		// Show the user card (a green card with a username on top and an empty row on bottom
		Card(
			modifier = Modifier
				.fillMaxWidth()
				.padding(0.dp),
			colors = CardDefaults.cardColors(
				containerColor = Color(ContextCompat.getColor(context, R.color.main_color)),
				contentColor = Color.White
			),
			shape = cardShape,
			elevation = CardDefaults.cardElevation(
				defaultElevation = cardsElevation,
				pressedElevation = cardsElevation
			)
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(5.dp, 10.dp),
				verticalArrangement = Arrangement.spacedBy(0.dp),
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				// Username and unfollow button
				if (followedUserIndex >= 0) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(5.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween
					) {
						// Username
						val username = userInfosJSON.getString("username")
						val maxUsernameLength = 15
						val usernameToDisplay = if (username.length > maxUsernameLength) "${username.substring(0, maxUsernameLength-2)}..." else username
						Text(
							text = "@$usernameToDisplay",
							fontSize = 18.sp,
							fontWeight = FontWeight.Bold,
							color = Color.White
						)
						// Unfollow button
						CustomButton(
							text = "UNFOLLOW",
							filled = false,
							Color.White,
							Color.White,
							buttonsShape,
							fillWidth = false,
							sizeX = 118.dp,
							onClick = {
								// Remove the username from the followed list
								followedUsers.removeAt(followedUserIndex)
								// Send a request to the server to update the list of followed users (without the need to get a response after the update)
								CoroutineScope(Dispatchers.Main).launch {
									// Send the request to the server
									SendUpdateFollowedUsersRequest(volleyRequestQueue, userEmail.value, followedUsers)
								}
							},
							fontSize = 12.sp
						)
					}
				}
				// User infos (if empty, show a message)
				if (aggregatedInfos.length() > 0) {
					// Calories row
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(5.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween
					) {
						// Calories
						Text(
							text = "Calories:",
							fontSize = 16.sp,
							fontWeight = FontWeight.Bold,
							color = Color.White
						)
						Text(
							text = "${aggregatedInfos.getDouble("calories").toInt()} kcal",
							fontSize = 16.sp,
							fontWeight = FontWeight.Bold,
							color = Color.White
						)
					}
					// Macronutrients row
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(0.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(0.dp)
					) {
						// Empty row
						// Carbs, fats and proteins
						DrawMacronutrientsBar(
							aggregatedInfos.getDouble("carbs").toFloat(),
							aggregatedInfos.getDouble("fats").toFloat(),
							aggregatedInfos.getDouble("proteins").toFloat(),
							showText = true
						)
					}
				} else {
					Text(
						modifier = Modifier.padding(5.dp),
						text = "No user infos available...",
						fontSize = 18.sp,
						fontWeight = FontWeight.Bold,
						color = Color.White
					)
				}
			}
			
		}
		
	}
	
	// Screen overlay for the loading screen
	if (showLoadingScreen.value) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(Color.White.copy(alpha = 0.8f))
				.absoluteOffset(0.dp, 0.dp)
				// Disable clicks on underneath elements
				.pointerInput(Unit) {}
				.zIndex(2f),
		) {
			// Show a loading indicator
			Box(
				modifier = Modifier
					.size(155.dp)
					.background(Color.Transparent)
					.align(Alignment.Center)
			) {
				CircularProgressIndicator(
					modifier = Modifier
						.size(60.dp)
						.align(Alignment.Center),
					color = Color(ContextCompat.getColor(context, R.color.main_color))
				)
				Text(
					modifier = Modifier
						.align(Alignment.BottomCenter)
						.padding(0.dp),
					text = "Loading...",
					fontSize = 22.sp,
					fontWeight = FontWeight.Bold,
					color = Color(ContextCompat.getColor(context, R.color.main_color))
				)
			}
		}
	}
	
	if (currentUIState.value == UIStates.Infos) {
		// Show an overlay with the infos screen
		Column(
			modifier = Modifier
				.fillMaxSize()
				.background(Color.White.copy(alpha = 0.8f))
				.absoluteOffset(0.dp, 0.dp)
				.padding(30.dp, 30.dp, 30.dp, 30.dp)
				// Disable clicks on underneath elements
				.pointerInput(Unit) {}
				.zIndex(1f),
			verticalArrangement = Arrangement.Center,
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			// Infos screen
			Card(
				modifier = Modifier
					.padding(10.dp)
					.fillMaxWidth(),
				colors = CardDefaults.cardColors(
					containerColor = Color(ContextCompat.getColor(context, R.color.main_color)),
					contentColor = Color.White
				),
				shape = cardShape,
				// Add elevation to the card (to show a shadow)
				elevation = CardDefaults.cardElevation(
					defaultElevation = cardsElevation,
					pressedElevation = cardsElevation
				)
			) {
				Column(
					modifier = Modifier
						.padding(10.dp),
					verticalArrangement = Arrangement.spacedBy(5.dp),
					horizontalAlignment = Alignment.CenterHorizontally
				) {
					
					// Title text
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(0.dp, 5.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.Center
					) {
						var titleText = ""
						if (selectedIngredientIndex.value >= 0) {
							titleText = ingredients[selectedIngredientIndex.value].uppercase()
						} else if (ingredients.size > 1) {
							titleText = "${ingredients.size} ITEMS"
						} else {
							titleText = ingredients[0].uppercase()
						}
						Text(
							modifier = Modifier.padding(1.dp),
							text = titleText,
							fontSize = 22.sp,
							fontWeight = FontWeight.Bold,
							textAlign = TextAlign.Center,
							color = Color.White
						)
						// Show the total weight of all ingredients
						val totalWeight = weights.sumOf { if (it.isNotEmpty()) it.toDouble() else 0.0 }
						Text (
							modifier = Modifier.padding(1.dp),
							text = " (" + totalWeight.toInt().toString() +  "g)",
							fontSize = 18.sp,
							fontWeight = FontWeight.Normal,
							textAlign = TextAlign.Center,
							color = Color.White
						)
					}
					// Show a section with ingredient tags (shown in a line and wrapping to newline if needed)
					if (selectedIngredientIndex.value < 0 && ingredients.size > 1) {
						FlowRow(
							modifier = Modifier
								.fillMaxWidth()
								.padding(0.dp, 3.dp),
							horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
							verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)
						) {
							for (i in ingredients.indices) {
								if (ingredients[i].isNotEmpty()) DrawItemTag(ingredients[i], weights[i])
							}
						}
					}
					// Show a column with the infos about the selected items
					val scrollState = rememberScrollState()
					Box(
						modifier = Modifier
							.padding(0.dp, 10.dp)
							.fillMaxWidth()
							.size(320.dp, 450.dp)
					) {
						Column(
							modifier = Modifier
								.verticalScroll(scrollState)
								.fillMaxWidth(),
							verticalArrangement = Arrangement.spacedBy(5.dp),
							horizontalAlignment = Alignment.CenterHorizontally
						) {
							// Show the infos for the selected ingredient(s)
							if (ingredientNutritionalInfos.size > 0) {
								val totalMacronutrients = ingredientNutritionalInfos.count( { it.first.lowercase() in listOf("fat", "carbohydrates", "protein", "fats", "carbs", "proteins") } )
								for ( i in 0 until ingredientNutritionalInfos.size) {
									val info = ingredientNutritionalInfos[i]
									var (nutrient, value, unit) = info
									// Truncate the value to 3 decimal places
									value = value.toBigDecimal().setScale(3, RoundingMode.HALF_EVEN).toDouble()
									Row(
										modifier = Modifier
											.fillMaxWidth()
											.padding(5.dp),
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.SpaceBetween
									) {
										val isCalories = nutrient.lowercase() in listOf("calories", "cal", "kcal", "energy")
										val isMacroNutrient = nutrient.lowercase() in listOf("fat", "carbohydrates", "protein", "fats", "carbs", "proteins")
										if (isCalories) {
											// Show the calories line in a bigger font
											Text(
												text = "Calories",
												fontWeight = FontWeight.Bold,
												fontSize = 22.sp
											)
											Text(
												text = "$value $unit",
												fontWeight = FontWeight.Normal,
												fontSize = 20.sp
											)
										} else if (isMacroNutrient) {
											// Show macronutrient (fat, carbs, proteins)
											Row(
												modifier = Modifier
													.padding(0.dp),
												verticalAlignment = Alignment.CenterVertically,
												horizontalArrangement = Arrangement.Start
											) {
												// Add a colored dot before the macronutrients
												val dotColor = when (nutrient.lowercase()) {
													"carbohydrates", "carbs" -> macronutrientsColors[0]
													"fat", "fats" -> macronutrientsColors[1]
													"protein", "proteins" -> macronutrientsColors[2]
													else -> Color.White
												}
												Card(
													modifier = Modifier
														.size(12.dp),
													shape = RoundedCornerShape(2.dp),
													elevation = CardDefaults.cardElevation(
														defaultElevation = cardsElevation,
														pressedElevation = cardsElevation
													),
													colors = CardDefaults.cardColors(
														containerColor = dotColor,
														contentColor = dotColor
													),
													content = {}
												)
												Text(
													modifier = Modifier
														.padding(5.dp, 0.dp, 0.dp, 0.dp),
													text = nutrient,
													fontWeight = FontWeight.Bold,
													fontSize = 18.sp
												)
											}
											Text(
												text = "$value $unit",
												fontWeight = FontWeight.Normal,
												fontSize = 16.sp
											)
										} else {
											// Show micronutrients
											Text(
												text = nutrient,
												fontWeight = FontWeight.Bold,
												fontSize = 14.sp
											)
											Text(
												text = "$value $unit",
												fontWeight = FontWeight.Normal,
												fontSize = 12.sp
											)
										}
									}
									// Add the macronutrients bar (indicating the percentage of all macronutrients) at the end of the macronutrients infos
									if (i == 0 && totalMacronutrients == 3) {
										DrawMacronutrientsBar(
											ingredientNutritionalInfos[1].second.toFloat(),
											ingredientNutritionalInfos[2].second.toFloat(),
											ingredientNutritionalInfos[3].second.toFloat()
										)
									}
								}
							} else {
								// Show a message if no infos are available
								Text(
									text = "No infos available...",
									fontWeight = FontWeight.Normal,
									fontSize = 16.sp
								)
							}
						}
						
						// Add top and bottom fade gradients for the content
						val fadeColor =
							Color(ContextCompat.getColor(context, R.color.main_color))
						if (scrollState.value > 0) {
							// Top fade
							Box(
								modifier = Modifier
									.fillMaxWidth()
									.height(40.dp)
									.background(
										brush = Brush.verticalGradient(
											colors = listOf(fadeColor, Color.Transparent)
										)
									)
							)
						}
						if (scrollState.value < scrollState.maxValue) {
							// Bottom fade
							Box(
								modifier = Modifier
									.fillMaxWidth()
									.height(40.dp)
									.align(Alignment.BottomCenter)
									.background(
										brush = Brush.verticalGradient(
											colors = listOf(Color.Transparent, fadeColor)
										)
									)
							)
						}
					}
					//CustomButton(
					//	text = "CLOSE",
					//	filled = false,
					//	Color.White,
					//	Color.White,
					//	//Color(ContextCompat.getColor(context, R.color.main_color)),
					//	buttonsShape,
					//	fillWidth = true,
					//	sizeX = 320.dp,
					//	onClick = {
					//		// Close the overlay
					//		showLoadingScreen.value = false
					//		currentUIState.value = UIStates.Main
					//	}
					//)
					
					// Row with the "saved" text
					if (sentIngredientsToServerText.value.isNotBlank()) {
						Text(
							modifier = Modifier
								.fillMaxWidth()
								.padding(0.dp, 5.dp),
							text = sentIngredientsToServerText.value,
							fontWeight = FontWeight.Bold,
							fontSize = 16.sp,
							color = if (sentIngredientsToServerText.value == "Saved!") Color.White else Color.Red,
							textAlign = TextAlign.Center
						)
					}
					
					// Footer buttons
					val footerButtonsWidth = 135.dp
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(0.dp, 6.dp, 0.dp, 0.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(5.dp)
					) {
						CustomButton(
							text = "BACK",
							filled = false,
							Color.White,
							Color.White,
							buttonsShape,
							fillWidth = false,
							sizeX = footerButtonsWidth,
							onClick = {
								// Go back to the main screen
								showLoadingScreen.value = false
								currentUIState.value = UIStates.Main
							}
						)
						CustomButton(
							text = "SAVE",
							filled = true,
							Color.White,
							Color(ContextCompat.getColor(context, R.color.main_color)),
							buttonsShape,
							fillWidth = false,
							sizeX = footerButtonsWidth,
							enabled = isLoggedIn.value && sentIngredientsToServerText.value != "Saved!",
							onClick = {
								// Check if the user is logged in
								if (!isLoggedIn.value) {
									// Do nothing
									return@CustomButton
								}
								// Get the user infos string (create a new json object with the ingredients and weights list)
								var infosString : String = ""
								if (ingredients.size > 0) {
									// Iterate over the current list of ingredients nutritional infos ("ingredientNutritionalInfos")  and create a json object to hold the "carbs", "fats", "proteins", "calories", "weight" (total) values
									val ingredientsInfos = JSONObject()
									for (i in 0 until ingredientNutritionalInfos.size) {
										if (ingredientNutritionalInfos[i].first.lowercase() in listOf("carbs", "fats", "proteins", "calories", "carbohydrates", "protein", "fat", "cal", "kcal", "energy")) {
											val (nutrient, value, unit) = ingredientNutritionalInfos[i]
											val nutrientName = when (nutrient.lowercase()) {
												"carbs", "carbohydrates" -> "carbs"
												"fat", "fats" -> "fats"
												"protein", "proteins" -> "proteins"
												"cal", "kcal", "calories", "energy" -> "calories"
												else -> nutrient
											}
											val nutrientValue = when (unit.lowercase()) {
												"kcal" -> value
												"cal" -> value
												"g" -> value
												"mg" -> value / 1000.0
												else -> value
											}
											ingredientsInfos.put(nutrientName, nutrientValue)
										}
										ingredientsInfos.put("weight", weights.sumOf { it.toDouble() })
									}
									infosString = ingredientsInfos.toString()
								}
								/*
								Final ingredients infos json object format:
									{
										"carbs": 10.0,		// Total carbs (in grams)
										"fats": 20.0,		// Total fats (in grams)
										"proteins": 30.0,	// Total proteins (in grams)
										"calories": 200.0,	// Total calories (in kcal)
										"weight": 1000.0	// Total weight of all ingredients
									}
								*/
								// Send a request to my own server to save the user infos
								CoroutineScope(Dispatchers.Main).launch {
									// Show a loading screen
									showLoadingScreen.value = true
									// Send the request and store its response
									val response = SendSaveUserInfosRequest(
										volleyRequestQueue,
										userEmail.value,
										infosString
									)
									debugText.value = response
									// Hide the loading screen
									showLoadingScreen.value = false
									if (!response.isNullOrBlank()) {
										// Disable the save button and show a message
										sentIngredientsToServerText.value = "Saved!"
										// Store the current user infos string
										currentUserInfosString.value = response
									} else {
										// Disable the save button and show a message
										sentIngredientsToServerText.value = "An error occurred..."
									}
								}
							}
						)
					}
				}
			}
		}
		
	}
	
	// Main container of the UI of the app
	Column(
		modifier = Modifier
			.fillMaxSize()
			.padding(13.dp, 45.dp, 13.dp, 45.dp),
		verticalArrangement = Arrangement.spacedBy(0.dp),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		// Show the various UI screens based on the current UI state
		if (currentUIState.value == UIStates.Main || currentUIState.value == UIStates.Infos) {
			// Show the main UI screen (even when showing the Infos overlay)
			
			// Main title card
			Card(
				modifier = Modifier
					.fillMaxWidth()
					.padding(10.dp, 0.dp),
				colors = CardDefaults.cardColors(
					containerColor = Color.White,
					contentColor = Color.Black
				),
				shape = cardShape,
				// Add elevation to the card (to show a shadow)
				elevation = CardDefaults.cardElevation(
					defaultElevation = cardsElevation,
					pressedElevation = cardsElevation
				)
			) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(10.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween
				) {
					
					// Title and subtitle
					Column(
						modifier =
						Modifier
							.fillMaxWidth(0.65f),
						verticalArrangement = Arrangement.Center,
						horizontalAlignment = Alignment.Start
					) {
						// Check whether to show the debug text ot the title text
						if (!showDebugText.value) {
							// Title text
							Text(
								modifier = Modifier.padding(3.dp),
								text = "Food Pal",
								fontSize = 28.sp,
								fontWeight = FontWeight.Bold,
								textAlign = TextAlign.Left,
								color = Color(ContextCompat.getColor(context, R.color.main_color))
							)
							// Subtitle text
							Text(
								modifier = Modifier.padding(3.dp),
								text = "Your personal food assistant",
								fontSize = 14.sp,
								fontWeight = FontWeight.Normal,
								textAlign = TextAlign.Left,
								color = Color(ContextCompat.getColor(context, R.color.main_color))
							)
						} else {
							// For debug, show a debug text in red, as a scrollable LazyColumn element
							LazyColumn(
								modifier = Modifier
									.padding(2.dp)
									.size(320.dp, 60.dp),
								verticalArrangement = Arrangement.spacedBy(0.dp),
								horizontalAlignment = Alignment.CenterHorizontally,
							) {
								item {
									Text(
										text = debugText.value,
										fontWeight = FontWeight.Normal,
										fontSize = 12.sp,
										color = Color.Red
									)
								}
							}
						}
					}
					// Profile icon
					if (isLoggedIn.value) {
						Card(
							modifier = Modifier
								.size(52.dp)
								.padding(0.dp),
							//.background(Color(ContextCompat.getColor(context, R.color.main_color))),
							//.wrapContentHeight(align = Alignment.CenterVertically),
							//elevation = 0.dp,
							shape = RoundedCornerShape(1000.dp),
							colors = CardDefaults.cardColors(
								containerColor = Color.LightGray,
								contentColor = Color.White
							),
						) {
							// Show an invisible button to wrap the icon
							val profileIconLetter = userEmail.value[0].uppercase()
							Button(
								onClick = {
									// Show a loading screen
									showLoadingScreen.value = true
									// Go to the profile screen (but first retrieve the user infos for the user)
									CoroutineScope(Dispatchers.Main).launch {
										// Send the request and store its response to get the logged in user infos
										val response = SendGetUserInfosRequest(
											volleyRequestQueue,
											userEmail.value.split("@")[0],
											userEmail.value
										)
										if (response.length() > 0) {
											// Store the current user infos string
											currentUserInfosString.value = response.getJSONArray("infos").toString()
											// Store the followed users list
											followedUsers.clear()
											val followedUsersArray = response.getJSONArray("followed")
											for (i in 0 until followedUsersArray.length()) {
												val followedUserInfosString = followedUsersArray.getString(i)
												Log.d("UserInfos", "Followed user infos: $followedUserInfosString")
												val followedUserUsername = JSONObject(followedUserInfosString).getString("username")
												val followedUserInfos = SendGetUserInfosRequest(
													volleyRequestQueue,
													followedUserUsername
												)
												if (followedUserInfos.length() > 0) {
													followedUsers.add(followedUserInfos.toString())
												}
											}
											// Hide the loading screen
											showLoadingScreen.value = false
											// Go to the profile screen
											currentUIState.value = UIStates.Profile
										} else {
											// Set the current infos to be blank
											currentUserInfosString.value = ""
											// Hide the loading screen
											showLoadingScreen.value = false
											// Show an error message
											Log.d("UserInfos", "An error occurred while retrieving the user infos...")
											debugText.value = "An error occurred while retrieving the user infos..."
											// Logout
											isLoggedIn.value = false
											userEmail.value = ""
											// Empty the list of followed users
											followedUsers.clear()
											// Reset the user infos string
											currentUserInfosString.value = ""
										}
									}
									
								},
								modifier = Modifier
									.size(52.dp)
									.padding(0.dp),
								colors = ButtonDefaults.buttonColors(
									containerColor = Color.Transparent,
									contentColor = Color.Transparent
								),
								shape = RoundedCornerShape(1000.dp),
								border = BorderStroke(0.dp, Color.Transparent)
								//elevation = 0.dp
							) {
								Text(
									text = profileIconLetter,
									fontSize = 24.sp,
									modifier = Modifier
										.padding(0.dp)
										.fillMaxSize()
										.wrapContentHeight(align = Alignment.CenterVertically),
									fontWeight = FontWeight.Bold,
									color = Color.White,
									textAlign = TextAlign.Center
								)
							}
						}
					} else {
						// Show a button to log in
						CustomButton(
							text = "LOGIN",
							filled = true,
							Color(ContextCompat.getColor(context, R.color.main_color)),
							Color.White,
							buttonsShape,
							fillWidth = false,
							sizeX = 90.dp,
							onClick = {
								// Open the login screen state
								currentUIState.value = UIStates.Login
								// Reset the "logged in" flag
								isLoggedIn.value = false
								// Reset the "is trying to login" flag
								isTryingToLogin.value = -1
								// Reset any previous email
								userEmail.value = ""
								// Reset any previous OTP code
								oneTimePassword.value = ""
							}
						)
					}
				}
			}
			
			// Scrollable column for the list of ingredients
			key(ingredients.size) {
				val scrollState = rememberScrollState()
				Box(
					modifier = Modifier
						.size(320.dp, 510.dp)
						.padding(0.dp, 10.dp)
						.fillMaxWidth()
				) {
					Column(
						modifier = Modifier
							.verticalScroll(scrollState)
							.fillMaxWidth(),
						verticalArrangement = Arrangement.spacedBy(10.dp),
						horizontalAlignment = Alignment.CenterHorizontally
					) {
						if (ingredients.isEmpty()) {
							Column(
								modifier = Modifier
									.padding(10.dp)
									.fillMaxWidth(),
								verticalArrangement = Arrangement.spacedBy(5.dp),
								horizontalAlignment = Alignment.Start
							) {
								Text(
									modifier = Modifier.padding(0.dp),
									color = Color.White,
									fontWeight = FontWeight.Bold,
									fontSize = 24.sp,
									text = "No items in your list..."
								)
								Text(
									modifier = Modifier.padding(0.dp),
									color = Color.White,
									fontWeight = FontWeight.Normal,
									fontSize = 16.sp,
									text = "Click the 'ADD' button to add a new item to your list."
								)
								Text(
									modifier = Modifier.padding(0.dp),
									color = Color.White,
									fontWeight = FontWeight.Normal,
									fontSize = 16.sp,
									text = "Alternatively, click the 'SCAN' button to scan food items with your camera."
								)
							}
						} else {
							ingredients.forEachIndexed { index, _ ->
								DrawItemCard(
									itemIndex = index,
									onRemove = {
										ingredients.removeAt(it)
										weights.removeAt(it)
										ingredientsList.removeAt(it)
										weightsList.removeAt(it)
									}
								)
							}
						}
					}
					
					// Add top and bottom fade gradients for the content
					val fadeColor = Color(ContextCompat.getColor(context, R.color.main_color))
					if (scrollState.value > 0) {
						// Top fade
						Box(
							modifier = Modifier
								.fillMaxWidth()
								.height(40.dp)
								.background(
									brush = Brush.verticalGradient(
										colors = listOf(fadeColor, Color.Transparent)
									)
								)
						)
					}
					if (scrollState.value < scrollState.maxValue) {
						// Bottom fade
						Box(
							modifier = Modifier
								.fillMaxWidth()
								.height(40.dp)
								.align(Alignment.BottomCenter)
								.background(
									brush = Brush.verticalGradient(
										colors = listOf(Color.Transparent, fadeColor)
									)
								)
						)
					}
				}
			}
			
			// Footer buttons
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(10.dp, 0.dp),
				verticalArrangement = Arrangement.spacedBy(5.dp),
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				val footerButtonsWidth = 150.dp
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(0.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(5.dp)
				) {
					CustomButton(
						text = "SCAN",
						filled = true,
						Color.White,
						Color(ContextCompat.getColor(context, R.color.main_color)),
						buttonsShape,
						fillWidth = false,
						sizeX = footerButtonsWidth,
						onClick = {
							// Open the camera
							currentUIState.value = UIStates.Camera
						}
					)
					CustomButton(
						text = "ADD",
						filled = true,
						Color.White,
						Color(ContextCompat.getColor(context, R.color.main_color)),
						buttonsShape,
						fillWidth = false,
						sizeX = footerButtonsWidth,
						onClick = {
							ingredients.add("")
							weights.add("")
							ingredientsList.add("")
							weightsList.add("")
						}
					)
				}
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(0.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(5.dp)
				) {
					CustomButton(
						text = "CLEAR",
						filled = true,
						Color.White,
						Color(ContextCompat.getColor(context, R.color.main_color)),
						buttonsShape,
						fillWidth = false,
						sizeX = footerButtonsWidth,
						onClick = {
							ingredients.clear()
							weights.clear()
							ingredientsList.clear()
							weightsList.clear()
						}
					)
					CustomButton(
						text = "GET INFOS",
						filled = true,
						Color.White,
						Color(ContextCompat.getColor(context, R.color.main_color)),
						buttonsShape,
						fillWidth = false,
						sizeX = footerButtonsWidth,
						onClick = {
							// Call the spoonacular API multiple times to get the nutritional infos for all the items in the list
							CoroutineScope(Dispatchers.Main).launch {
								// Iterate over all the items in the list
								var allNutritionalInfos = mutableListOf<List<Triple<String, Double, String>>>()
								for (i in ingredients.indices) {
									// Send the request (with the selected food) and store its response
									val response = SendFoodNutritionalValueAPIRequest(
										volleyRequestQueue,
										ingredients[i]
									)
									debugText.value = (i + 1).toString() + " / " + ingredients.size.toString() + "\n" + response
									// Parse the response and store the nutritional infos
									val nutritionalValues = ParseFoodNutritionalValuesAPIResponse(response)
									allNutritionalInfos.add(nutritionalValues)
								}
								// Merge the nutritional infos for all the items
								ingredientNutritionalInfos.clear()
								for (i in allNutritionalInfos.indices) {
									val infos = allNutritionalInfos[i]
									if (i >= weights.size || infos.isEmpty() || weights[i].isEmpty()) {
										// Skip the item if no infos are available
										continue
									}
									val ingredientWeight = weights[i].toDouble()
									for (info in infos) {
										val (nutrient, value, unit) = info
										// Truncate the value to 3 decimal places
										val newValue = value * ingredientWeight / 100.0
										// Find the index of the nutrient in the list
										val index = ingredientNutritionalInfos.indexOfFirst { it.first == nutrient }
										if (index >= 0) {
											// Update the value for the nutrient
											val oldValue = ingredientNutritionalInfos[index].second
											ingredientNutritionalInfos[index] = Triple(nutrient, oldValue + newValue, unit)
										} else {
											// Add the nutrient to the list
											ingredientNutritionalInfos.add(Triple(nutrient, newValue, unit))
										}
									}
									//
									//// Find the index of the nutrient in the list
									//val index = ingredientNutritionalInfos.indexOfFirst { it.first == nutrient }
									//if (index >= 0) {
									//	// Update the value for the nutrient
									//	val oldValue = ingredientNutritionalInfos[index].second
									//	ingredientNutritionalInfos[index] = Triple(nutrient, oldValue + value, unit)
									//} else {
									//	// Add the nutrient to the list
									//	ingredientNutritionalInfos.add(ingredientInfos)
									//}
								}
								// Update the UI state to show the infos screen
								currentUIState.value = UIStates.Infos
								sentIngredientsToServerText.value = ""
								// Hide the loading screen
								showLoadingScreen.value = false
							}
							// Update the selected ingredient index
							selectedIngredientIndex.value = -1
							// Show the loading screen
							showLoadingScreen.value = true
							debugText.value = "Waiting for response..."
						},
						enabled = ingredients.isNotEmpty()
					)
				}
			}
		} else if (currentUIState.value == UIStates.Camera) {
			// Show the camera preview screen
			CameraPreview(
				onImageCaptured = { photoFile ->
					CoroutineScope(Dispatchers.Main).launch {
						// Check if we have a photo file (if not, do nothing)
						if (photoFile == null) {
							Log.d("Null photo file", "No photo file found")
							return@launch
						}
						// Get image byte data
						val byteData = photoFile.readBytes()
						val apiType = "foodvisor"    // Options are "clarifai" or "foodvisor"
						val response = SendImageRecognitionAPIRequest(volleyRequestQueue, byteData)
						//debugText.value = response
						val imageRecognitionResults = ParsePhotoScanAPIResponse(response, apiType)
						var resultsString = ""
						for (result in imageRecognitionResults) {
							resultsString += "${result.first} (${result.second})\n"
						}
						// Update the debug text with the results
						debugText.value = resultsString
						showLoadingScreen.value = false
						// Add all the new items to the list (with a default weight of 100g)
						for (result in imageRecognitionResults) {
							ingredients.add(result.first)
							weights.add("100")
							ingredientsList.add(result.first)
							weightsList.add("100")
						}
					}
					// Update the "waiting for response" flag
					showLoadingScreen.value = true
					debugText.value = "Waiting for response..."
				},
				onClose = {
					// Reset the main UI
					currentUIState.value = UIStates.Main
				}
			)
		} else if (currentUIState.value == UIStates.Scale) {
			
			// Sensitivity text
			Text(
				modifier = Modifier
					.fillMaxWidth()
					.padding(0.dp)
					.wrapContentHeight(align = Alignment.Top),
				text = if (scaleSensitivity.value <= 1100) scaleSensitivity.value.toInt().toString() else "-",
				fontSize = 16.sp,
				fontWeight = FontWeight.Normal,
				color = Color(0x09000000),
				textAlign = TextAlign.Right
			)
			// Show a screen with indications on how to use the scale
			Column(
				modifier = Modifier
					.fillMaxSize()
					.padding(10.dp),
				verticalArrangement = Arrangement.Center,
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				
				// Card with instructions on how to use the scale
				Card(
					modifier = Modifier
						.fillMaxWidth()
						.padding(0.dp),
					colors = CardDefaults.cardColors(
						containerColor = Color.White,
						contentColor = Color.Black
					),
					shape = cardShape,
					// Add elevation to the card (to show a shadow)
					elevation = CardDefaults.cardElevation(
						defaultElevation = cardsElevation,
						pressedElevation = cardsElevation
					)
				) {
					Column(
						modifier =
						Modifier
							.padding(10.dp, 15.dp)
							.fillMaxWidth(),
						verticalArrangement = Arrangement.Center,
						horizontalAlignment = Alignment.CenterHorizontally
					) {
						// Title text
						Text(
							modifier = Modifier.padding(5.dp),
							text = "Mobile Scale",
							fontSize = 24.sp,
							fontWeight = FontWeight.Bold,
							textAlign = TextAlign.Center,
							color = Color(ContextCompat.getColor(context, R.color.main_color))
						)
						// Subtitle text
						val instructionsText =
							"1) Place your phone face down on a flat surface.\n" +
							"2) Wait for your phone to start the calibration (vibration starts).\n" +
							"3) At the end of the calibration (vibration stops), you'll hear a sound (check your phone's volume before starting).\n" +
							"4) Within 10 seconds, place the food item on the back of your phone without moving it (if you move your phone, you'll hear an error sound).\n" +
							"5) After 10 seconds, measuring will starts (vibration starts).\n" +
							"6) At the end of the measuring (vibration stops), you'll hear a second sound.\n" +
							"7) Measurement is complete! You can move your phone."
						val textLines = instructionsText.split("\n")
						Column(
							modifier = Modifier
								.fillMaxWidth()
								.padding(5.dp),
							verticalArrangement = Arrangement.spacedBy(0.dp),
							horizontalAlignment = Alignment.Start
						) {
							if (!showDebugText.value) {
								//Text(
								//	modifier = Modifier.padding(0.dp, 0.dp, 0.dp, 3.dp),
								//	text = "Instructions:",
								//	fontWeight = FontWeight.Bold,
								//	fontSize = 18.sp,
								//	color = Color(ContextCompat.getColor(context, R.color.main_color))
								//)
								for (i in 0 until textLines.size) {
									val line = textLines[i]
									val parts = line.split(") ")
									val boldString = parts[0] + "."
									val normalString = parts[1]
									Row(
										modifier = Modifier
											.fillMaxWidth()
											.padding(5.dp),
										verticalAlignment = Alignment.Top,
										horizontalArrangement = Arrangement.spacedBy(6.dp)
									) {
										Text(
											text = boldString,
											fontWeight = FontWeight.Bold,
											fontSize = 15.sp,
											color = Color(
												ContextCompat.getColor(
													context,
													R.color.main_color
												)
											)
										)
										Text(
											text = normalString,
											fontWeight = FontWeight.Normal,
											fontSize = 15.sp,
											color = Color(
												ContextCompat.getColor(
													context,
													R.color.main_color
												)
											)
										)
									}
									
								}
							} else {
								// Show a debug text with the current state of the scale
								Text(
									modifier = Modifier.padding(5.dp),
									text = debugText.value,
									fontWeight = FontWeight.Bold,
									fontSize = 16.sp,
									color = Color.Red
								)
							}
							// Show an additional message if we are trying to measure the scale
							if (tryingToMeasureScale.value) {
								Column(
									modifier = Modifier
										.fillMaxWidth()
										.padding(0.dp),
									verticalArrangement = Arrangement.spacedBy(0.dp),
									horizontalAlignment = Alignment.Start
								) {
									// Check if we can start measuring the scale
									val canStartMeasuring = CheckCanStartWeightMeasurement(sensorDataMeasurements)
									if (!canStartMeasuring.first && !isCurrentlyMeasuring) {
										// Waiting to start measuring (display what is wrong with the current phone position/state)
										Text(
											modifier = Modifier
												.padding(5.dp)
												.fillMaxWidth(),
											text = "Waiting to start measurements...",
											fontWeight = FontWeight.Bold,
											fontSize = 15.sp,
											color = Color.Red,
											textAlign = TextAlign.Center
										)
										Text(
											modifier = Modifier
												.padding(5.dp)
												.fillMaxWidth(),
											text = "${canStartMeasuring.second}!",
											fontWeight = FontWeight.Bold,
											fontSize = 15.sp,
											color = Color.Red,
											textAlign = TextAlign.Center
										)
										// If the phone's volume is at 0, show a warning
										if (currentPhoneVolume.value < 0.01) {
											Text(
												modifier = Modifier
													.padding(5.dp)
													.fillMaxWidth(),
												text = "Your phone's volume is at 0!\nYou won't hear sounds.",
												fontWeight = FontWeight.Bold,
												fontSize = 15.sp,
												color = Color(0xFFFFAA00),
												textAlign = TextAlign.Center
											)
										}
									} else {
										// Currently measuring the scale
										Text(
											modifier = Modifier
												.padding(5.dp)
												.fillMaxWidth(),
											text = "Measuring...",
											fontWeight = FontWeight.Bold,
											fontSize = 15.sp,
											color = Color(
												ContextCompat.getColor(
													context,
													R.color.main_color
												)
											),
											textAlign = TextAlign.Center
										)
									}
								}
							}
						}
					}
				}
				
				// Footer buttons
				val footerButtonsWidth = 150.dp
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(0.dp, 6.dp, 0.dp, 0.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(5.dp)
				) {
					CustomButton(
						text = "CANCEL",
						filled = false,
						Color.White,
						Color.White,
						buttonsShape,
						fillWidth = false,
						sizeX = footerButtonsWidth,
						onClick = {
							// Reset the main UI
							currentUIState.value = UIStates.Main
							// Reset the "trying to measure scale" flag
							tryingToMeasureScale.value = false
							// Reset the "measuredSuccessfully" flag
							measuredSuccessfully.value = false
							// Reset the currentlyMeasuring flag
							isCurrentlyMeasuring = false
							isTryingToMeasure = false
						}
					)
					CustomButton(
						text = "START",
						filled = true,
						Color.White,
						Color(ContextCompat.getColor(context, R.color.main_color)),
						buttonsShape,
						fillWidth = false,
						sizeX = footerButtonsWidth,
						onClick = {
							/*
							// For debug, send a request to the server, directly
							CoroutineScope(Dispatchers.Main).launch {
								// Get the last 3 measurements
								var sensorDataMeasurements = sensorDataMeasurementsRaw.takeLast(3).toMutableList()
								// Send the data to the server to get the weight
								val weight = SendWeightEstimationRequest(volleyRequestQueue, sensorDataMeasurements)
								// Check if the weight was actual retrieved
								if (weight.isBlank() || weight == "0") {
									// Measurement failed
									measuredSuccessfully.value = false
									// Update the debug text with the error
									debugText.value = "Measurement failed!"
								} else {
									// Update the predicted weight
									predictedWeight.value = weight.toDouble()
									// Update the debug text with the weight
									debugText.value = weight
								}
								// Hide the loading screen
								showLoadingScreen.value = false
								// Go to the weight results screen
								currentUIState.value = UIStates.ScaleResults
								// Return
								return@launch
							}
							*/
							///*
							// Start the scale (notify that we are trying to measure the scale)
							tryingToMeasureScale.value = true
							isTryingToMeasure = true
							// Reset the "measuredSuccessfully" flag
							measuredSuccessfully.value = false
							// Start the coroutine to measure the scale
							CoroutineScope(Dispatchers.Main).launch {
								// Measure the scale
								val scaleMeasurement = MeasureScale(context)
								// if not trying to measure scale anymore (user canceled the measurement) simply return
								if (!tryingToMeasureScale.value) {
									measuredSuccessfully.value = false
									return@launch
								}
								// Update the debug text with the scale measurement
								debugText.value = "Measurements (" + scaleMeasurement.size.toString() + ")\n" + scaleMeasurement.toString()
								// Stop the scale measurement
								tryingToMeasureScale.value = false
								isTryingToMeasure = false
								// Check if the scale measurement was successful
								if (!scaleMeasurement.isNullOrEmpty() && scaleMeasurement.size > 0) {
									// Set the "measuredSuccessfully" flag
									measuredSuccessfully.value = true
									// Switch to the loading screen
									showLoadingScreen.value = true
									// Send the data to the server to get the weight
									val weight = SendWeightEstimationRequest(volleyRequestQueue, scaleMeasurement)
									// Check if the weight was actual retrieved
									if (weight.isBlank() || weight == "0") {
										// Measurement failed
										measuredSuccessfully.value = false
										// Update the debug text with the error
										debugText.value = "Measurement failed!"
									} else {
										// Update the predicted weight
										predictedWeight.value = weight.toDouble().toLong()
										// Update the debug text with the weight
										debugText.value = weight
									}
									// Go to the weight results screen
									currentUIState.value = UIStates.ScaleResults
									// Hide the loading screen
									showLoadingScreen.value = false
								} else {
									// Measurement failed
									measuredSuccessfully.value = false
								}
								// Update the value with the random value for testing
								if (scaleSensitivity.value <= 1100) predictedWeight.value = abs(scaleSensitivity.value.toInt() + rand.value).toLong()
							}
							//*/
						},
						enabled = !tryingToMeasureScale.value
					)
				}
				// Row with slider for the scale sensitivity
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(0.dp, 10.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween
				) {
					// Slider to adjust the sensitivity of the scale
					Slider(
						modifier = Modifier
							.fillMaxWidth()
							.size(300.dp, 30.dp)
							.padding(0.dp, 0.dp, 0.dp, 0.dp),
						value = scaleSensitivity.value,
						onValueChange = {
							// Update the scale sensitivity
							scaleSensitivity.value = it
							// Update the random value for testing
							rand.value = (-5..5).random()
						},
						valueRange = 0.0f..1000.0f,
						steps = round(980f / 20f).toInt(),
						colors = SliderDefaults.colors(
							thumbColor = Color(0x00000000),
							activeTrackColor = Color(0x00000000),
							inactiveTrackColor = Color(0x00000000),
							activeTickColor = Color(0x09000000),
							inactiveTickColor = Color(0x00000000),
						),
						thumb = {
							Box(
								modifier = Modifier
									.size(25.dp)
									.background(Color(0x00000000)),
							)
						}
					)
				}
				
			}
		} else if (currentUIState.value == UIStates.ScaleResults) {
			// Show the scale results screen (a simple screen with the name of the selected item, the predicted weight, and 2 buttons to retry or "OK")
			Column(
				modifier = Modifier
					.fillMaxSize()
					.padding(10.dp),
				verticalArrangement = Arrangement.Center,
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				// Card with the scale results
				Card(
					modifier = Modifier
						.fillMaxWidth()
						.padding(0.dp),
					colors = CardDefaults.cardColors(
						containerColor = Color.White,
						contentColor = Color.Black
					),
					shape = cardShape,
					// Add elevation to the card (to show a shadow)
					elevation = CardDefaults.cardElevation(
						defaultElevation = cardsElevation,
						pressedElevation = cardsElevation
					)
				) {
					Column(
						modifier =
						Modifier
							.padding(10.dp, 15.dp)
							.fillMaxWidth(),
						verticalArrangement = Arrangement.Center,
						horizontalAlignment = Alignment.CenterHorizontally
					) {
						// Title text
						Text(
							modifier = Modifier.padding(5.dp),
							text = "Mobile Scale Results",
							fontSize = 24.sp,
							fontWeight = FontWeight.Bold,
							textAlign = TextAlign.Center,
							color = Color(ContextCompat.getColor(context, R.color.main_color))
						)
						// Weight measurements text
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(5.dp, 15.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.SpaceBetween
						) {
							Text(
								text = "Measured weight:",
								fontWeight = FontWeight.Bold,
								fontSize = 18.sp,
								color = Color(ContextCompat.getColor(context, R.color.main_color))
							)
							Text(
								text = "${predictedWeight.value}g",
								fontWeight = FontWeight.Normal,
								fontSize = 18.sp,
								color = Color(ContextCompat.getColor(context, R.color.main_color))
							)
						}
					}
				}
				
				// Footer buttons
				val footerButtonsWidth = 150.dp
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(0.dp, 6.dp, 0.dp, 0.dp),
					
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(5.dp)
				) {
					CustomButton(
						text = "RETRY",
						filled = false,
						Color.White,
						Color.White,
						buttonsShape,
						fillWidth = false,
						sizeX = footerButtonsWidth,
						onClick = {
							// Go back to the scale screen
							tryingToMeasureScale.value = false
							measuredSuccessfully.value = false
							currentUIState.value = UIStates.Scale
							isTryingToMeasure = false
						}
					)
					CustomButton(
						text = "CONFIRM",
						filled = true,
						Color.White,
						Color(ContextCompat.getColor(context, R.color.main_color)),
						buttonsShape,
						fillWidth = false,
						sizeX = footerButtonsWidth,
						onClick = {
							// Set the weight of the selected item
							weights[selectedIngredientIndex.value] = predictedWeight.value.toString()
							// Reset the main UI
							currentUIState.value = UIStates.Main
						}
					)
				}
			}
		} else if (currentUIState.value == UIStates.Login) {
			// Show a login screen with a simple email field
			Column(
				modifier = Modifier
					.fillMaxSize()
					.padding(10.dp),
				verticalArrangement = Arrangement.Center,
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				// Card with a simple text field to input the user email
				Card(
					modifier = Modifier
						.fillMaxWidth()
						.padding(0.dp),
					colors = CardDefaults.cardColors(
						containerColor = Color.White,
						contentColor = Color.Black
					),
					shape = cardShape,
					// Add elevation to the card (to show a shadow)
					elevation = CardDefaults.cardElevation(
						defaultElevation = cardsElevation,
						pressedElevation = cardsElevation
					)
				) {
					Column(
						modifier =
						Modifier
							.padding(10.dp, 15.dp)
							.fillMaxWidth(),
						verticalArrangement = Arrangement.spacedBy(10.dp),
						horizontalAlignment = Alignment.CenterHorizontally,
						
						) {
						// Title text
						Text(
							modifier = Modifier.padding(5.dp),
							text = if (isTryingToLogin.value < 2) "Enter your email to login" else "Enter your OTP to verify",
							fontSize = 24.sp,
							fontWeight = FontWeight.Bold,
							textAlign = TextAlign.Center,
							color = Color(ContextCompat.getColor(context, R.color.main_color))
						)
						// Subtitle text
						//val scaleResultsText = "${predictedWeight.value}g"
						//Row(
						//	modifier = Modifier
						//		.fillMaxWidth()
						//		.padding(5.dp, 15.dp),
						//	verticalAlignment = Alignment.CenterVertically,
						//	horizontalArrangement = Arrangement.SpaceBetween
						//) {
						//	Text(
						//		text = "Measured weight:",
						//		fontWeight = FontWeight.Bold,
						//		fontSize = 18.sp,
						//		color = Color(ContextCompat.getColor(context, R.color.main_color))
						//	)
						//	Text(
						//		text = scaleResultsText,
						//		fontWeight = FontWeight.Normal,
						//		fontSize = 18.sp,
						//		color = Color(ContextCompat.getColor(context, R.color.main_color))
						//	)
						//}
						// Email input field
						CustomInputField(
							value = userEmail.value,
							onValueChange = { newValue ->
								userEmail.value = newValue
							},
							label = "Email",
							sizeX = 200.dp,
							fillWidth = true,
							enabled = isTryingToLogin.value < 2
						)
						// Show messages
						if (isTryingToLogin.value >= 5) {
							// Show a "Verifying OTP" text
							Text(
								modifier = Modifier.padding(5.dp),
								text = "Verifying one-time password...",
								fontWeight = FontWeight.Normal,
								fontSize = 13.sp,
								color = Color(ContextCompat.getColor(context, R.color.main_color))
							)
						} else if (isTryingToLogin.value >= 4) {
							// Show an OTP error message
							Text(
								modifier = Modifier.padding(5.dp),
								text = "Verification failed! Please check that your one-time password is correct and try again.",
								fontWeight = FontWeight.Normal,
								fontSize = 13.sp,
								color = Color.Red
							)
						} else if (isTryingToLogin.value >= 2) {
							// Show a NOTE text
							Text(
								modifier = Modifier
									.fillMaxWidth()
									.padding(5.dp),
								text = "A one-time password has been sent to \'${userEmail.value}\'. Enter the one-time password to sign in.",
								fontWeight = FontWeight.Normal,
								fontSize = 16.sp,
								textAlign = TextAlign.Center,
								color = Color(ContextCompat.getColor(context, R.color.main_color))
							)
						} else if (isTryingToLogin.value >= 1) {
							// Show an error message (server returned an error, maybe user inserted a wrong password)
							Text(
								modifier = Modifier.padding(5.dp),
								text = "Login failed! Please check that your email address is correct and try again.",
								fontWeight = FontWeight.Normal,
								fontSize = 12.sp,
								color = Color.Red
							)
						}
						// Show the OTP input field
						if (isTryingToLogin.value >= 2) {
							// show the OTP input field
							CustomInputField(
								value = oneTimePassword.value,
								onValueChange = { newValue ->
									oneTimePassword.value = newValue
								},
								label = "One-time password",
								sizeX = 200.dp,
								fillWidth = true,
								isNumeric = true
							)
						}
					}
				}
				
				// Footer button
				val footerButtonsWidth = 150.dp
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(0.dp, 6.dp, 0.dp, 0.dp),
					
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(5.dp)
				) {
					CustomButton(
						text = "CANCEL",
						filled = false,
						Color.White,
						Color.White,
						buttonsShape,
						fillWidth = false,
						sizeX = footerButtonsWidth,
						onClick = {
							// Go back to the main screen
							currentUIState.value = UIStates.Main
						}
					)
					CustomButton(
						text = if (isTryingToLogin.value < 2) "LOGIN" else "VERIFY",
						filled = true,
						Color.White,
						Color(ContextCompat.getColor(context, R.color.main_color)),
						buttonsShape,
						fillWidth = false,
						sizeX = footerButtonsWidth,
						onClick = {
							if (isTryingToLogin.value < 2) {
								// Send a login request to the server
								CoroutineScope(Dispatchers.Main).launch {
									// Show the loading screen
									showLoadingScreen.value = true
									// Set the "is trying to login" flag (to show the second input field for a one-time password)
									isTryingToLogin.value = 0    // Email inserted, waiting for server response
									// Send the login request
									val response = SendLoginRequest(volleyRequestQueue, userEmail.value)
									// Hide the loading screen
									showLoadingScreen.value = false
									// Check if the login was successful
									if (response == "success") {
										// Set the "is trying to login" flag (to show the second input field for a one-time password)
										isTryingToLogin.value = 2    // Email inserted, server sent the OTP code
									} else {
										// Show an error message underneath the input field
										isTryingToLogin.value = 1    // Email inserted, server returned an error
										// Show an error message
										debugText.value = "Login failed!"
										Log.d("Login failed", "Login failed!")
									}
								}
							} else {
								// Send the one-time password to the server
								CoroutineScope(Dispatchers.Main).launch {
									// Show the loading screen
									showLoadingScreen.value = true
									// Set the "is trying to login" flag (to show the second input field for a one-time password)
									isTryingToLogin.value = 3    // Email inserted, server sent the OTP code
									// Send the one-time password
									val response = SendVerifyOTPRequest(volleyRequestQueue, userEmail.value, oneTimePassword.value)
									// Hide the loading screen
									showLoadingScreen.value = false
									// Check if the login was successful (if the response is not an empty json object
									if (response.length() > 0) {
										// Set the "is trying to login" flag (to show the second input field for a one-time password)
										isTryingToLogin.value = 5    // Email inserted, OTP accepted by the server
										// Set the "is logged in" flag
										isLoggedIn.value = true
										// Go back to the main screen
										currentUIState.value = UIStates.Main
										// Get the user infos string
										Log.d("User infos", response.getString("infos"))
										currentUserInfosString.value = response.getString("infos")
									} else {
										// Show an error message underneath the input field
										isTryingToLogin.value = 4    // Email inserted, server returned an error
										// Show an error message
										debugText.value = "Login failed!"
									}
								}
							}
						}
					)
				}
			}
			
		} else if (currentUIState.value == UIStates.Profile) {
			// Get the user profile name
			val profileName = userEmail.value.split("@")[0]
			// Show the user profile screen
			Column(
				modifier = Modifier
					.fillMaxSize()
					.padding(10.dp),
				verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				// Card with profile icon, username and email
				Card(
					modifier = Modifier
						.fillMaxWidth()
						.padding(0.dp),
					colors = CardDefaults.cardColors(
						containerColor = Color.White,
						contentColor = Color.Black
					),
					shape = cardShape,
					// Add elevation to the card (to show a shadow)
					elevation = CardDefaults.cardElevation(
						defaultElevation = cardsElevation,
						pressedElevation = cardsElevation
					)
				) {
					Column(
						modifier =
						Modifier
							.padding(10.dp, 15.dp)
							.fillMaxWidth(),
						verticalArrangement = Arrangement.spacedBy(2.dp),
						horizontalAlignment = Alignment.CenterHorizontally,
						
						) {
						// Title text
						//Text(
						//	modifier = Modifier.padding(5.dp),
						//	text = "User Profile",
						//	fontSize = 24.sp,
						//	fontWeight = FontWeight.Bold,
						//	textAlign = TextAlign.Center,
						//	color = Color(ContextCompat.getColor(context, R.color.main_color))
						//)
						// Show a row with the user profile icon and username
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(5.dp, 0.dp, 5.dp, 10.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(5.dp)
						) {
							val profileIconLetter = profileName[0].toString().uppercase()
							Card(
								modifier = Modifier
									.size(52.dp)
									.padding(0.dp),
								//.background(Color(ContextCompat.getColor(context, R.color.main_color))),
								//.wrapContentHeight(align = Alignment.CenterVertically),
								//elevation = 0.dp,
								shape = RoundedCornerShape(1000.dp),
								colors = CardDefaults.cardColors(
									containerColor = Color.LightGray,
									contentColor = Color.White
								),
							) {
								Text(
									text = profileIconLetter,
									fontSize = 24.sp,
									modifier = Modifier
										.padding(0.dp)
										.fillMaxSize()
										.wrapContentHeight(align = Alignment.CenterVertically),
									fontWeight = FontWeight.Bold,
									color = Color.White,
									textAlign = TextAlign.Center
								)
							}
							// Show the username next to the profile icon
							val maxUsernameLength = 15
							val truncatedUsername = if (profileName.length > maxUsernameLength) profileName.substring(0, maxUsernameLength-2) + "..." else profileName
							Text(
								modifier = Modifier.padding(5.dp),
								text = "@" + truncatedUsername,
								fontWeight = FontWeight.Bold,
								fontSize = 24.sp,
								color = Color(ContextCompat.getColor(context, R.color.main_color))
							)
						}
						// Show the user email
						Text(
							modifier = Modifier
								.fillMaxWidth()
								.padding(5.dp, 0.dp),
							text = "Email:",
							fontWeight = FontWeight.Normal,
							textAlign = TextAlign.Left,
							fontSize = 17.sp,
							color = Color(ContextCompat.getColor(context, R.color.main_color))
						)
						Text(
							modifier = Modifier
								.fillMaxWidth()
								.padding(5.dp, 0.dp),
							text = userEmail.value,
							fontWeight = FontWeight.Bold,
							fontSize = 17.sp,
							textAlign = TextAlign.Left,
							color = Color(ContextCompat.getColor(context, R.color.main_color))
						)
						// Show a line with the current user infos
						if (!currentUserInfosString.value.isNullOrBlank() && currentUserInfosString.value != "[]") {
							DrawUserCard(-1,currentUserInfosString.value)
						}
					}
				}
				
				// Card with the user's followed other users
				Card(
					modifier = Modifier
						.fillMaxWidth()
						.padding(0.dp),
					colors = CardDefaults.cardColors(
						containerColor = Color.White,
						contentColor = Color.Black
					),
					shape = cardShape,
					// Add elevation to the card (to show a shadow)
					elevation = CardDefaults.cardElevation(
						defaultElevation = cardsElevation,
						pressedElevation = cardsElevation
					)
				) {
					Column(
						modifier = Modifier
							.padding(10.dp, 15.dp)
							.fillMaxWidth(),
						verticalArrangement = Arrangement.spacedBy(2.dp),
						horizontalAlignment = Alignment.CenterHorizontally,
					) {
						// New followed user username text
						val newFollowedUser = remember { mutableStateOf("") }
						val userErrorInfos = remember { mutableStateOf("") }
						// Show a title
						Text(
							modifier = Modifier.padding(5.dp),
							text = "Followed users (${followedUsers.size})",
							fontSize = 18.sp,
							fontWeight = FontWeight.Bold,
							textAlign = TextAlign.Center,
							color = Color(ContextCompat.getColor(context, R.color.main_color))
						)
						// Show a lazycolumn with a scrollable list of users (drawn with "DrawUserCard"), or a message if there are no users
						if (followedUsers.isEmpty()) {
							// Show a message if there are no followed users
							Text(
								modifier = Modifier.padding(5.dp),
								text = "You are not following any users.",
								fontSize = 16.sp,
								fontWeight = FontWeight.Normal,
								textAlign = TextAlign.Center,
								color = Color(ContextCompat.getColor(context, R.color.main_color))
							)
						} else {
							// Show the list of followed users
							LazyColumn(
								modifier = Modifier
									.fillMaxWidth()
									.padding(0.dp, 0.dp),
								verticalArrangement = Arrangement.spacedBy(5.dp)
							) {
								items(followedUsers.size) { userIndex ->
									// Get the user's info
									val userInfosString = followedUsers[userIndex]
									// Draw the user card
									DrawUserCard(userIndex,userInfosString)
								}
							}
						}
						// Show a row with an input field and a button to add new users
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(0.dp, 5.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(0.dp)
						) {
							// Input and button to add a neww user to our followed list
							CustomInputField(
								value = newFollowedUser.value,
								onValueChange = { newValue ->
									newFollowedUser.value = newValue
									userErrorInfos.value = ""
								},
								label = "Follow user",
								sizeX = 185.dp,
								fillWidth = false,
								isNumeric = false,
								customShape = elementsShape.copy(
									topEnd = CornerSize(0.dp),
									bottomEnd = CornerSize(0.dp)
								)
							)
							CustomButton(
								text = "FOLLOW",
								filled = true,
								Color(ContextCompat.getColor(context, R.color.main_color)),
								Color.White,
								fillWidth = false,
								sizeX = 110.dp,
								sizeY = 55.dp,
								customShape = elementsShape.copy(
									topStart = CornerSize(0.dp),
									bottomStart = CornerSize(0.dp)
								),
								onClick = {
									// Send a request to the server to check if the user exists and get its infos string (as a JSON string)
									CoroutineScope(Dispatchers.Main).launch {
										// Show the loading screen
										showLoadingScreen.value = true
										// Send the request to the server
										val userInfosResult = SendGetUserInfosRequest(volleyRequestQueue, newFollowedUser.value, userEmail.value)
										// Hide the loading screen
										showLoadingScreen.value = false
										// Check if the request succeeded
										var userInfos : JSONArray
										var userFound = false
										if (userInfosResult.length() > 0) {
											try {
												Log.d("Trying to parse user infos...", userInfosResult.toString())
												userInfos = userInfosResult.getJSONArray("infos")
												userFound = true
											} catch (e: JSONException) {
												userInfos = JSONArray()
												userFound = false
											}
										} else {
											userInfos = JSONArray()
											userFound = false
										}
										// Check if the user exists
										if (userFound) {
											// Check if the user is the currently logged in user
											if (userInfosResult.getString("username") == profileName) {
												// Show an error message for the user
												userErrorInfos.value = "You can't follow yourself!"
												// Show an error message
												debugText.value = "You can't follow yourself!"
												return@launch
											}
											// Add the new user to the list of followed users
											followedUsers.add(userInfosResult.toString())
											// Reset the input field
											newFollowedUser.value = ""
											// Reset the error messages
											userErrorInfos.value = ""
											// Send a request to the server to update the list of followed users (without the need to get a response after the update)
											CoroutineScope(Dispatchers.Main).launch {
												// Send the request to the server
												SendUpdateFollowedUsersRequest(volleyRequestQueue, userEmail.value, followedUsers)
											}
										} else {
											// Show an error message for the user
											userErrorInfos.value = "User '${newFollowedUser.value}' not found!"
											// Show an error message
											debugText.value = "User not found!"
										}
									}
								}
							)
							
							/*
							// Input field to add a new user
							CustomInputField(
								value = newFollowedUser.value,
								onValueChange = { newValue ->
									newFollowedUser.value = newValue
									userErrorInfos.value = ""
								},
								label = "Follow user",
								sizeX = 180.dp,
								fillWidth = false
							)
							// Button to add the new user
							CustomButton(
								text = "FOLLOW",
								filled = true,
								Color(ContextCompat.getColor(context, R.color.main_color)),
								Color.White,
								buttonsShape,
								fillWidth = false,
								sizeX = 110.dp,
								onClick = {
									// Send a request to the server to check if the user exists and get its infos string (as a JSON string)
									CoroutineScope(Dispatchers.Main).launch {
										// Show the loading screen
										showLoadingScreen.value = true
										// Send the request to the server
										val userInfosResult = SendGetUserInfosRequest(volleyRequestQueue, newFollowedUser.value, userEmail.value)
										// Hide the loading screen
										showLoadingScreen.value = false
										// Check if the request succeeded
										var userInfos : JSONObject = JSONObject()
										var userFound = false
										if (userInfosResult.length() > 0) {
											try {
												userInfos = userInfosResult.getJSONObject("infos")
												userFound = true
											} catch (e: JSONException) {
												userFound = false
											}
										}
										// Check if the user exists
										if (userFound) {
											// Check if the user is the currently logged in user
											if (userInfos.getString("username") == profileName) {
												// Show an error message for the user
												userErrorInfos.value = "You can't follow yourself!"
												// Show an error message
												debugText.value = "You can't follow yourself!"
												return@launch
											}
											// Add the new user to the list of followed users
											followedUsers.add(userInfos.toString())
											// Reset the input field
											newFollowedUser.value = ""
											// Reset the error messages
											userErrorInfos.value = ""
											// Send a request to the server to update the list of followed users (without the need to get a response after the update)
											CoroutineScope(Dispatchers.Main).launch {
												// Send the request to the server
												SendUpdateFollowedUsersRequest(volleyRequestQueue, userEmail.value, followedUsers)
											}
										} else {
											// Show an error message for the user
											userErrorInfos.value = "User '${newFollowedUser.value}' not found!"
											// Show an error message
											debugText.value = "User not found!"
										}
									}
								}
							)
							*/
						}
						// error text for users (if any)
						if (userErrorInfos.value.isNotBlank()) {
							Text(
								modifier = Modifier.padding(5.dp),
								text = userErrorInfos.value,
								fontSize = 14.sp,
								fontWeight = FontWeight.Normal,
								textAlign = TextAlign.Center,
								color = Color.Red
							)
						}
					}
				}
				
				// Footer buttons
				val footerButtonsWidth = 150.dp
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(0.dp, 6.dp, 0.dp, 0.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(5.dp)
				) {
					CustomButton(
						text = "BACK",
						filled = false,
						Color.White,
						Color.White,
						buttonsShape,
						fillWidth = false,
						sizeX = footerButtonsWidth,
						onClick = {
							// Go back to the main screen
							currentUIState.value = UIStates.Main
						}
					)
					CustomButton(
						text = "LOGOUT",
						filled = true,
						Color.White,
						Color(ContextCompat.getColor(context, R.color.main_color)),
						buttonsShape,
						fillWidth = false,
						sizeX = footerButtonsWidth,
						onClick = {
							// Logout
							isLoggedIn.value = false
							userEmail.value = ""
							// Go back to the main screen
							currentUIState.value = UIStates.Main
							// Empty the list of followed users
							followedUsers.clear()
							// Reset the user infos string
							currentUserInfosString.value = ""
							// Clear the list of ingredients and weights
							ingredients.clear()
							weights.clear()
							ingredientsList.clear()
							weightsList.clear()
						}
					)
				}
			}
			
		}
	}
}

// Function that starts the measurements (vibrate and collect sensor data) and returns the list of sensor measurements
// NOTE: the "stopWhenPhoneIsMoved" parameter is used to stop the measurements if the phone is moved, but while vibrating, the phone will always move a bit, so it's recommended to keep this parameter to false
@RequiresApi(Build.VERSION_CODES.O)
suspend fun Measure(context: Context, stopWhenPhoneIsMoved:Boolean = false): MutableList<SensorData> {
	// Define vibration/measurement duration
	val duration = 10_000L // In milliseconds
	// Define measurements collection rate
	val sensorRefreshRateHz = 200 // Sensor refresh rate in Hz
	val measurementsRate = 1_000 / sensorRefreshRateHz // Measurements per second
	// Calculate the number of measurements to collect
	val numOfMeasurements = duration * measurementsRate / 1_000 // Number of measurements to collect
	// Reset the list of sensor measurements
	val sensorMeasurements = mutableListOf<SensorData>()
	// get the phone's vibrator
	val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
	// Start measuring
	isCurrentlyMeasuring = false
	withContext(Dispatchers.Default) {
		// Start vibration
		vibrator.vibrate(VibrationEffect.createOneShot(duration, 255))
		// Collect measurements
		isCurrentlyMeasuring = true
		for (i in 0 until numOfMeasurements) {
			// Get the last sensor data measurements
			val sensorDataMeasurementsList = sensorDataMeasurementsRaw.toMutableList()
			// Check if we should stop measuring if the phone is moved
			if (stopWhenPhoneIsMoved) {
				// Check if the phone has been moved
				val canContinueMeasurements = CheckCanStartWeightMeasurement(sensorDataMeasurementsList).first
				if (!canContinueMeasurements) {
					// Stop vibrating
					vibrator.cancel()
					// Stop measuring
					isCurrentlyMeasuring = false
					sensorMeasurements.clear()
					break
				}
			}
			// Get the sensor data from the last sensorDataMeasurements
			val sensorData = sensorDataMeasurementsList.last()
			// Store all of the sensors data in the list of sensor measurements
			sensorMeasurements.add(sensorData)
			// Wait for the next measurement
			delay(1000L / measurementsRate)
		}
		isCurrentlyMeasuring = false
	}
	return sensorMeasurements
}

// Tries to start the measuring of the scale
@RequiresApi(Build.VERSION_CODES.O)
suspend fun MeasureScale(context: Context) : List<SensorData> {
	
	// Define the time the user has to place the food item on the phone after the calibration
	val timeToPlaceFoodItemAfterCalibration = 10_000L    // In milliseconds
	
	// Wait until we can start measuring the scale
	val delayTime = 250L
	var passedChecks = 0
	val checksToPass = 5
	var maxTime = 60_000L	// Max time the user has to stabilize his phone before starting measurements, after which the measurements are stopped
	while (true) {
		// Check if we can start measuring the scale
		val canStartMeasuring = CheckCanStartWeightMeasurement(sensorDataMeasurementsRaw.toMutableList())
		if (canStartMeasuring.first) {
			// Increment the number of passed checks
			passedChecks++
			if (passedChecks >= checksToPass) {
				// Start the scale measurement
				break
			}
		}
		// Wait for a bit before checking again
		delay(delayTime)
		// Check if we passed the maximum time
		maxTime -= delayTime
		if (maxTime<0) {
			// Play an error sound
			playSound(context, R.raw.error, 0.8f, 0.65f)
			// Stop the measurements
			return listOf()
		}
		// Check if the user canceled measurements
		if (!isTryingToMeasure) {
			
			// Stop the measurements
			return listOf()
		}
		
	}
	// Play a sound to indicate that the measurement is starting
	playSound(context, R.raw.beep, 0.9f)
	delay(delayTime*3)
	// Start the calibration measurement (using function Measure)
	val calibrationMeasurements = Measure(context)
	delay(delayTime)
	// Play a sound to indicate that the calibration is complete
	playSound(context, R.raw.beep, 1.0f)
	// Wait some seconds for the user to place the food item on the scale
	delay(delayTime*3)
	// Play the timer sound every second and, in the meantime, check if the phone moved
	val checksPerSecond = 5L
	for (i in 0 until checksPerSecond * timeToPlaceFoodItemAfterCalibration / 1000) {
		// Play the timer sound
		if (i % checksPerSecond == 0L) playSound(context, R.raw.timer_sound, 1.0f, 1.1f)
		delay(1000 / checksPerSecond)
		// Check if the phone moved in the meantime
		val canContinueMeasurements = CheckCanStartWeightMeasurement(sensorDataMeasurementsRaw.toMutableList()).first
		if (!canContinueMeasurements) {
			// Play an error sound
			playSound(context, R.raw.error, 0.8f, 0.65f)
			// Stop the measurements
			return listOf()
		}
	}
	delay(delayTime*2)
	// Check if the phone moved in the meantime (again)
	val canContinueMeasurements = CheckCanStartWeightMeasurement(sensorDataMeasurementsRaw.toMutableList()).first
	if (!canContinueMeasurements) {
		// Play an error sound
		playSound(context, R.raw.error, 0.8f, 0.65f)
		// Stop the measurements
		return listOf()
	}
	// Start the measurement of the food item
	val scaleMeasurements = Measure(context)
	delay(delayTime)
	// Play a sound to indicate that the measurement is complete
	playSound(context, R.raw.beep, 1.2f)
	// Return both the calibration and scale measurements
	return calibrationMeasurements + scaleMeasurements
}

// Send a request to my own server on PythonAnywhere to get the weight estimate given the sensor data measurements list (use a deferred function)
suspend fun SendWeightEstimationRequest(
	volleyRequestQueue: RequestQueue,
	sensorDataMeasurements: List<SensorData>
): String {
	// Create the URL
	val url = "https://usernamealreadytaken.eu.pythonanywhere.com/predict"
	// Create the JSON object to send
	val jsonBody = JSONObject()
	val sensorDataJSON = JSONArray()
	for (sensorData in sensorDataMeasurements) {
		sensorDataJSON.put(sensorData.toJSON())
	}
	jsonBody.put("sensor_data", sensorDataJSON)
	//Log.d("JSON body", jsonBody.toString(4))
	
	// Create a CompletableDeferred to wait for the response
	val deferred = CompletableDeferred<String>()
	
	// Create the request
	val request = JsonObjectRequest(
		Request.Method.POST,
		url,
		jsonBody,
		{ response ->
			// Parse the response
			val weightEstimation = response.get("weight").toString()
			val modelOutput = response.get("model_output").toString()
			Log.d("Weight estimation", weightEstimation)
			Log.d("Model output", modelOutput)
			// Complete the deferred with the weight estimation
			deferred.complete(weightEstimation)
		},
		{ error ->
			// Log the error
			Log.e("ERROR", "Error in weight estimation request: $error")
			// Complete the deferred with an empty string
			deferred.complete("ERROR: $error")
		}
	)
	// Add the request to the queue
	volleyRequestQueue.add(request)
	// Await the result from the deferred
	return deferred.await()
}

// Send a request to my own server on PythonAnywhere to login the user (use a deferred function)
suspend fun SendLoginRequest(
	volleyRequestQueue: RequestQueue,
	userEmail: String
): String {
	// Create the URL
	val url = "https://usernamealreadytaken.eu.pythonanywhere.com/login"
	// Create the JSON object to send
	val jsonBody = JSONObject()
	jsonBody.put("email", userEmail)
	//Log.d("JSON body", jsonBody.toString(4))
	
	// Create a CompletableDeferred to wait for the response
	val deferred = CompletableDeferred<String>()
	
	// Create the request
	val request = JsonObjectRequest(
		Request.Method.POST,
		url,
		jsonBody,
		{ response ->
			// Parse the response
			val loginResponse = response.get("success").toString()
			Log.d("Login response", "Login success: $loginResponse")
			if (loginResponse == "false") {
				val error = response.get("error").toString()
				Log.d("Login response", "Login error: $error")
			}
			// Complete the deferred with the login response
			deferred.complete(if (loginResponse == "true") "success" else "error")
		},
		{ error ->
			// Log the error
			Log.e("ERROR", "Error in login request: $error")
			// Complete the deferred with an empty string
			deferred.complete("ERROR: $error")
		}
	)
	// Add the request to the queue
	volleyRequestQueue.add(request)
	// Await the result from the deferred
	return deferred.await()
}

// Send a request to my own server on PythonAnywhere to verify the one-time password (use a deferred function)
suspend fun SendVerifyOTPRequest(
	volleyRequestQueue: RequestQueue,
	userEmail: String,
	otp: String
): JSONObject {
	// Create the URL
	val url = "https://usernamealreadytaken.eu.pythonanywhere.com/verify"
	// Create the JSON object to send
	val jsonBody = JSONObject()
	jsonBody.put("email", userEmail)
	jsonBody.put("otp", otp)
	//Log.d("JSON body", jsonBody.toString(4))
	
	// Create a CompletableDeferred to wait for the response
	val deferred = CompletableDeferred<JSONObject>()
	
	// Create the request
	val request = JsonObjectRequest(
		Request.Method.POST,
		url,
		jsonBody,
		{ response ->
			// Parse the response
			val otpResponse = response.get("verified").toString()
			Log.d("OTP response", "OTP success: $otpResponse")
			if (otpResponse == "false") {
				val error = response.get("error").toString()
				Log.d("OTP response", "OTP error: $error")
				deferred.complete(JSONObject())
			} else {
				// Complete the deferred with the OTP response
				Log.d("OTP response", response.toString())
				val result = JSONObject()
				result.put("success", otpResponse == "true")
				result.put("username", response.get("username"))
				result.put("infos", response.get("infos"))
				result.put("followed", response.get("followed"))
				deferred.complete(result)
			}
		},
		{ error ->
			// Log the error
			Log.e("ERROR", "Error in OTP verification request: $error")
			// Complete the deferred with an empty string
			//deferred.complete("ERROR: $error")
			deferred.complete(JSONObject())
		}
	)
	// Add the request to the queue
	volleyRequestQueue.add(request)
	// Await the result from the deferred
	return deferred.await()
}

// Send a request to my own server on PythonAnywhere to get the user infos string (use a deferred function)
suspend fun SendGetUserInfosRequest(
	volleyRequestQueue: RequestQueue,
	username: String,
	authEmail : String = ""
): JSONObject {
	// Create the URL
	val url = "https://usernamealreadytaken.eu.pythonanywhere.com/user_infos"
	// Create the JSON object to send
	val jsonBody = JSONObject()
	jsonBody.put("username", username)
	if (!authEmail.isNullOrBlank()) jsonBody.put("email", authEmail)
	// Create a CompletableDeferred to wait for the response
	val deferred = CompletableDeferred<JSONObject>()
	
	// Create the request
	val request = JsonObjectRequest(
		Request.Method.POST,
		url,
		jsonBody,
		{ response ->
			// Parse the response
			val foundString = response.get("found").toString()
			if (foundString == "false") {
				// Log the error
				Log.d("User not found", "User not found (username: $username)")
				// Complete the deferred with an empty string
				deferred.complete(JSONObject())
			} else {
				val userInfosJSON = response.getJSONObject("result")
				val userInfosString = userInfosJSON.toString()
				//Log.d("User infos string", userInfosString)
				Log.d("UserInfos", userInfosString)
				// Complete the deferred with the user infos string and the "followed" list
				deferred.complete( userInfosJSON )
			}
		},
		{ error ->
			// Log the error
			Log.e("ERROR", "Error in get user infos request: $error")
			// Complete the deferred with an empty string
			deferred.complete(JSONObject())
		}
	)
	// Add the request to the queue
	volleyRequestQueue.add(request)
	// Await the result from the deferred
	return deferred.await()
}

// Send a request to my own server on PythonAnywhere to save the user infos string (use a deferred function)
suspend fun SendSaveUserInfosRequest(
	volleyRequestQueue: RequestQueue,
	email: String,
	userInfosString: String
): String {
	// Create the URL
	val url = "https://usernamealreadytaken.eu.pythonanywhere.com/save_user_infos"
	// Create the JSON object to send
	val jsonBody = JSONObject()
	jsonBody.put("email", email)
	jsonBody.put("infos", userInfosString)
	//Log.d("JSON body", jsonBody.toString(4))
	
	// Create a CompletableDeferred to wait for the response
	val deferred = CompletableDeferred<String>()
	
	// Create the request
	val request = JsonObjectRequest(
		Request.Method.POST,
		url,
		jsonBody,
		{ response ->
			// Parse the response
			val savedString = response.get("saved").toString()
			if (savedString == "false") {
				// Log the error
				Log.d("User infos not saved", "User infos not saved: ${response.get("error")}")
				// Complete the deferred with an empty string
				deferred.complete("")
			} else {
				// Complete the deferred with the user infos string
				deferred.complete(response.getJSONArray("infos").toString())
			}
		},
		{ error ->
			// Log the error
			Log.e("ERROR", "Error in save user infos request: $error")
			// Complete the deferred with an empty string
			deferred.complete("")
		}
	)
	// Add the request to the queue
	volleyRequestQueue.add(request)
	// Await the result from the deferred
	return deferred.await()
}

// Send a request to my own server on PythonAnywhere to update the list of followed users (use a deferred function)
suspend fun SendUpdateFollowedUsersRequest(
	volleyRequestQueue: RequestQueue,
	email: String,
	followedUsers: List<String>
): String {
	// Create the URL
	val url = "https://usernamealreadytaken.eu.pythonanywhere.com/update_followed"
	// Create the JSON object to send
	val jsonBody = JSONObject()
	jsonBody.put("email", email)
	val followedUsersJSON = JSONArray()
	for (followedUser in followedUsers) {
		followedUsersJSON.put(followedUser)
	}
	jsonBody.put("followed", followedUsersJSON)
	//Log.d("JSON body", jsonBody.toString(4))
	
	// Create a CompletableDeferred to wait for the response
	val deferred = CompletableDeferred<String>()
	
	// Create the request
	val request = JsonObjectRequest(
		Request.Method.POST,
		url,
		jsonBody,
		{ response ->
			// Parse the response
			val updatedString = response.get("updated").toString()
			if (updatedString == "false") {
				// Log the error
				Log.d("Followed users not updated", "Followed users not updated: ${response.get("error")}")
				// Complete the deferred with an empty string
				deferred.complete("failure")
			} else {
				// Complete the deferred with the user infos string
				deferred.complete("success")
			}
		},
		{ error ->
			// Log the error
			Log.e("ERROR", "Error in update followed users request: $error")
			// Complete the deferred with an empty string
			deferred.complete("")
		}
	)
	// Add the request to the queue
	volleyRequestQueue.add(request)
	// Await the result from the deferred
	return deferred.await()
}

// Takes a response string from the Clarifai API and parses it to extract a list of <food name, confidence> pairs
fun ParsePhotoScanAPIResponse(response: String, apiType: String) : List<Pair<String, Double>> {
	// List of <food name, confidence> pairs
	var imageRecognitionResults = mutableListOf<Pair<String, Double>>()
	if (apiType == "clarifai") {
		val concepts = response.split("\n")
		for (concept in concepts) {
			if (concept.isEmpty()) continue
			if (!concept.contains(": ")) {
				// Log the concept if it does not contain the separator ": "
				Log.d(
					"WARNING: found 'concept' in response without separator ': '",
					concept
				)
				continue
			}
			val nameValue = concept.split(": ")
			imageRecognitionResults.add(
				Pair(
					nameValue[0],
					nameValue[1].toDouble()
				)
			)
		}
		//// Store the first match in the selected food
		//if (imageRecognitionResults.isNotEmpty()) {
		//	_selectedFood = _imageRecognitionResults[0].first
		//} else {
		//	_selectedFood = "unknown"
		//	Log.d(
		//		"WARNING: no concepts found in response:\n",
		//		response
		//	)
		//}
	} else if (apiType == "foodvisor") {
		/*
			Foodvisor response format:
			{
			  "analysis_id": "bc7409e3-b182-4bf2-a074-8bb0d4641c58",
			  "scopes": [
				"multiple_items",
				"nutrition:macro",
				"nutrition:micro",
				"nutrition:nutriscore"
			  ],
			  "items": [
				"..."
			  ]
			}
			 */
		// Parse the JSON response and extract the first item name
		val jsonResponse = JSONObject(response)
		// Log the JSON response indented for debugging
		Log.d("JSON response", jsonResponse.toString())
		val items : JSONArray
		if (jsonResponse.has("items")) {
			try {
				val itemsList = jsonResponse.getJSONArray("items")
				items = itemsList
			} catch (e: JSONException) {
				Log.d("ERROR: error parsing 'items' in response:\n", response)
				return imageRecognitionResults.toList()
			}
		} else {
			Log.d("ERROR: no 'items' found in response:\n", response)
			return imageRecognitionResults.toList()
		}
		if (items.length() > 0) {
			// Iterate over analysis response items
			for (i in 0 until items.length()) {
				val item = items.getJSONObject(i)
				// Get the food name
				val foodObj = item.get("food")
				var foodName = "unknown"
				var confidence = 1.0
				if (foodObj is JSONArray) {
					// Get the first item in the array
					val firstFood = foodObj.getJSONObject(0)
					// Get the food name
					foodName = firstFood.getJSONObject("food_info").getString("display_name")
					// Get the confidence
					confidence = firstFood.getDouble("confidence")
				} else if (foodObj is JSONObject) {
					// Get the food name
					foodName = foodObj.getJSONObject("food_info").getString("display_name")
					// Get the confidence
					confidence = item.getDouble("confidence")
				} else {
					Log.d(
						"WARNING: unknown 'food' type in response: ",
						foodObj.toString()
					)
				}
				// Add the food name and confidence to the list
				imageRecognitionResults.add(Pair(foodName, confidence))
			}
			/*
			// Get the first analysis response item
			val firstItem = items.getJSONObject(0)
			// Food name is found in firstItem["food"]["food_info"]["display_name"]
			// NOTE: the "food" could either be a JSON object or a JSON array
			// 	- if it is an array, the first item is the one we are looking for
			// 	- if it is an object, the object itself is the one we are looking for
			// Check if the "food" is an array or an object
			val food = firstItem.get("food")
			if (food is JSONArray) {
				// Get the first item in the array
				val firstFood = food.getJSONObject(0)
				// Get the food name
				_selectedFood =
					firstFood.getJSONObject("food_info")
						.getString("display_name")
			} else if (food is JSONObject) {
				// Get the food name
				_selectedFood =
					food.getJSONObject("food_info")
						.getString("display_name")
			} else {
				_selectedFood = "unknown"
				Log.d(
					"WARNING: unknown 'food' type in response: ",
					food.toString()
				)
			}
			*/
		} else {
			Log.d("WARNING: no items found in 'foodvisor' response:\n", response)
		}
	} else {
		Log.d("ERROR: unknown API type: ", apiType)
	}
	return imageRecognitionResults.toList()
}

fun ParseFoodNutritionalValuesAPIResponse(response: String) : List<Triple<String, Double, String>> {
	// List of <nutrient, value_per_100g, unit> for the selected ingredient
	var nutritionalInfos = mutableListOf<Triple<String, Double, String>>()
	// Check if the response is empty
	if (response.isEmpty() || response.contains("FAILED")) {
		Log.d("ERROR: empty response", "Empty response received")
		return nutritionalInfos.toList()
	}
	// Parse the JSON response and extract the nutritional values
	val jsonResponse: JSONObject
	try {
		Log.d("API response", response)
		jsonResponse = JSONObject(response)
	} catch (e: JSONException) {
		Log.d("ERROR: error parsing JSON response", response)
		return nutritionalInfos.toList()
	}
	// Log the JSON response indented for debugging
	//Log.d("JSON response", jsonResponse.toString(4))
	// Get the nutrients object
	val nutrients = jsonResponse.getJSONObject("nutrition").getJSONArray("nutrients")
	// Iterate over the nutrients
	for (i in 0 until nutrients.length()) {
		val nutrient = nutrients.getJSONObject(i)
		val name = nutrient.getString("name")
		val value = nutrient.getDouble("amount")
		val unit = nutrient.getString("unit")
		nutritionalInfos.add(Triple(name, value, unit))
	}
	// Sort the list of nutriens by name, but show the "Calories", "Fat", "Carbohydrates" and "Protein" as the firt 4 items
	val sortedNutritionalInfos = nutritionalInfos.sortedBy { it.first }.toMutableList()
	// Get "calories", "fat", "carbohydrates" and "protein" items
	val calories = sortedNutritionalInfos.find { it.first.equals("Calories", ignoreCase = true) }
	val carbohydrates = sortedNutritionalInfos.find { it.first.equals("Carbohydrates", ignoreCase = true) }
	val fat = sortedNutritionalInfos.find { it.first.equals("Fat", ignoreCase = true) }
	val protein = sortedNutritionalInfos.find { it.first.equals("Protein", ignoreCase = true) }
	// Remove the items from the list
	sortedNutritionalInfos.removeAll { it.first.equals("Calories", ignoreCase = true) }
	sortedNutritionalInfos.removeAll { it.first.equals("Carbohydrates", ignoreCase = true) }
	sortedNutritionalInfos.removeAll { it.first.equals("Fat", ignoreCase = true) }
	sortedNutritionalInfos.removeAll { it.first.equals("Protein", ignoreCase = true) }
	// Add the items back to the list in the correct order
	if (calories != null) sortedNutritionalInfos.add(0, calories)
	if (carbohydrates != null) sortedNutritionalInfos.add(1, carbohydrates)
	if (fat != null) sortedNutritionalInfos.add(2, fat)
	if (protein != null) sortedNutritionalInfos.add(3, protein)
	// Return the list of nutritional infos
	return sortedNutritionalInfos.toList()
}

// Draw a scatter plot of points in a square area
@Composable
fun Scatterplot(
	pointsArray: Array<MutableList<Triple<Double, Double, Double>>>,
	size: Dp,
	pointSize: Dp,
) {
	Canvas(
		modifier = Modifier.size(size)
	) {
		// Calculate the range as the maximum of the x and y ranges of all the measurements
		val rangePadding = 0.05
		var xRange = 0.0..0.0
		var yRange = 0.0..0.0
		for (points in pointsArray) {
			for ((x, y, _) in points) {
				if (x < xRange.start) xRange = x..xRange.endInclusive
				if (x > xRange.endInclusive) xRange = xRange.start..x
				if (y < yRange.start) yRange = y..yRange.endInclusive
				if (y > yRange.endInclusive) yRange = yRange.start..y
			}
		}
		xRange = xRange.start - rangePadding..xRange.endInclusive + rangePadding
		yRange = yRange.start - rangePadding..yRange.endInclusive + rangePadding
		// Get the canvas size and scales
		val canvasWidth = size.toPx()
		val canvasHeight = size.toPx()
		val xScale = canvasWidth / (xRange.endInclusive - xRange.start)
		val yScale = canvasHeight / (yRange.endInclusive - yRange.start)
		// Draw a circle at each point
		fun drawCircles(points: List<Triple<Double, Double, Double>>, color: Color) {
			for ((x, y, _) in points) {
				val xPos = (x - xRange.start) * xScale
				val yPos = canvasHeight - (y - yRange.start) * yScale
				drawCircle(
					color = color,
					radius = pointSize.toPx() / 2,
					center = Offset(xPos.toFloat(), yPos.toFloat())
				)
			}
		}
		// Array of different colors to use for different points
		val colors = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Magenta, Color.Cyan)
		// Draw the points for each list of points in the pointsArray
		for ((index, points) in pointsArray.withIndex()) {
			drawCircles(points, colors[index % colors.size])
		}
	}
}

public data class SensorData(
	// Main sensor values
	val accelerometer : Triple<Double, Double, Double>,
	val deviceTemperature : Double,
	val gravity : Triple<Double, Double, Double>,
	val gyroscope : Triple<Double, Double, Double>,
	val linearAcceleration : Triple<Double, Double, Double>,
	val orientation : Triple<Double, Double, Double>,
	val pressure : Double,
	val rotationVector : Triple<Double, Double, Double>,
	val ambientTemperature : Double
) {
	// Define a "toJSON" function to convert the SensorData object to a JSON object
	fun toJSON() : JSONObject {
		val json = JSONObject()
		json.put("accelerometer", JSONArray(listOf(accelerometer.first, accelerometer.second, accelerometer.third)))
		json.put("deviceTemperature", deviceTemperature)
		json.put("gravity", JSONArray(listOf(gravity.first, gravity.second, gravity.third)))
		json.put("gyroscope", JSONArray(listOf(gyroscope.first, gyroscope.second, gyroscope.third)))
		json.put("linearAcceleration", JSONArray(listOf(linearAcceleration.first, linearAcceleration.second, linearAcceleration.third)))
		json.put("orientation", JSONArray(listOf(orientation.first, orientation.second, orientation.third)))
		json.put("pressure", pressure)
		json.put("rotationVector", JSONArray(listOf(rotationVector.first, rotationVector.second, rotationVector.third)))
		json.put("ambientTemperature", ambientTemperature)
		return json
	}
}

// Save either a measurements file (if realWeight is 0) or a calibration file (if realWeight is > 0) in the Downloads folder
private fun saveMeasurementsFile(
	measurementsFolder : String,
	sensorsData : List<SensorData>,
	fileName : String,
	calibrationFileName : String,
	realWeight : Double
) {
	// Save the measurements in a file
	val lines = mutableListOf<String>()
	// Add the real weight to the first line
	lines.add("$realWeight\n")
	// Add the calibration file name to the second line (if this is a calibration file, actually add its name to the first line)
	if (realWeight > 0.0) lines.add("$calibrationFileName\n")
	else lines.add("$fileName\n")
	// Add lines representing all the sensor data
	for (sensorData in sensorsData) {
		lines.add(
			"${sensorData.accelerometer.first},${sensorData.accelerometer.second},${sensorData.accelerometer.third};" +
					"${sensorData.deviceTemperature};" +
					"${sensorData.gravity.first},${sensorData.gravity.second},${sensorData.gravity.third};" +
					"${sensorData.gyroscope.first},${sensorData.gyroscope.second},${sensorData.gyroscope.third};" +
					"${sensorData.linearAcceleration.first},${sensorData.linearAcceleration.second},${sensorData.linearAcceleration.third};" +
					"${sensorData.orientation.first},${sensorData.orientation.second},${sensorData.orientation.third};" +
					"${sensorData.pressure};" +
					"${sensorData.rotationVector.first},${sensorData.rotationVector.second},${sensorData.rotationVector.third};" +
					"${sensorData.ambientTemperature}" +
					"\n"
		)
	}
	// Save the file in the Downloads folder, with the name "measurements.txt" (create the file if it does not exist), asking for permissions if needed
	val saveFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString() + measurementsFolder)
	if (!saveFolder.exists()) {
		saveFolder.mkdirs()
	}
	val saveFile = File(saveFolder, fileName)
	val fileExists = saveFile.exists()
	if (!fileExists) {
		saveFile.createNewFile()
	}
	val fileText = lines.joinToString("")
	saveFile.writeText(fileText)
}

// Returns the next file name for the measurements file as "measurements_<number>.txt"
fun getMeasurementsFileName(
	measurementsFolder : String,
	offset:Int
): String {
	val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString() + measurementsFolder)
	val files = folder.listFiles()
	val measurementsFiles = files?.filter { it.name.startsWith("measurements_") }
	val lastMeasurementsFile = measurementsFiles?.maxByOrNull { it.name }
	val lastMeasurementFileNumberString = lastMeasurementsFile?.name?.substringAfter("measurements_")?.substringBeforeLast(".txt")
	// Make sure to also take into account multiple digit numbers
	val lastMeasurementsFileNumber = lastMeasurementFileNumberString?.toIntOrNull() ?: 0
	val nextMeasurementsFileNumber = lastMeasurementsFileNumber + offset
	val paddedNumber = nextMeasurementsFileNumber.toString().padStart(4, '0')
	return "measurements_$paddedNumber.txt"
}

// Returns the calibration file name as "calibration_<number>.txt" where <number> is the highest number found in the calibration files, offset by the given offsetNumber (e.g. pass +1 to get the next calibration file name)
fun getCalibrationFileName(
	calibrationFolder : String,
	offsetNumber : Int
): String {
	val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString() + calibrationFolder)
	val files = folder.listFiles()
	val calibrationFiles = files?.filter { it.name.startsWith("calibration_") }
	val lastCalibrationFile = calibrationFiles?.maxByOrNull { it.name }
	val lastCalibrationFileNumber = lastCalibrationFile?.name?.substringAfter("calibration_")?.substringBeforeLast(".txt")?.toIntOrNull() ?: 0
	val nextCalibrationFileNumber = lastCalibrationFileNumber + offsetNumber
	val paddedNumber = nextCalibrationFileNumber.toString().padStart(4, '0')
	return "calibration_$paddedNumber.txt"
}

suspend fun SendImageRecognitionAPIRequest(
	volleyRequestQueue: RequestQueue,
	byteData : ByteArray,
	// Options are "clarifai" or "foodvisor"
	apiType: String = "foodvisor"
): String {
	
	// For more infos visit:
	// CLARIFAI:
	// 		INFOS: https://docs.clarifai.com/api-guide/predict/images
	// 		> GENERAL: https://clarifai.com/clarifai/main/models/general-image-recognition
	//  	> FOOD: https://clarifai.com/clarifai/main/models/food-item-recognition
	// FOODVISOR:
	// 		INFOS: https://vision.foodvisor.io/docs/#/paths/foodlist/get
	/*
	
	CURL request example (for image urls,n general image recognition):
		# Model version ID is optional. It defaults to the latest model version, if omitted
		curl -X POST "https://api.clarifai.com/v2/users/clarifai/apps/main/models/general-image-recognition/versions/aa7f35c01e0642fda5cf400f543e7c40/outputs"   -H "Authorization: Key c3b688a85b874b73aac00311843b3dcd"   -H "Content-Type: application/json"   -d '{
			"inputs": [
			  {
				"data": {
				  "image": {
					"url": "https://samples.clarifai.com/metro-north.jpg"
				  }
				}
			  }
			]
		  }'
		  
	CURL request example (for base64 image data, general image recognition):
	curl -X POST "https://api.clarifai.com/v2/users/clarifai/apps/main/models/general-image-recognition/versions/aa7f35c01e0642fda5cf400f543e7c40/outputs" \
		  -H "Authorization: Key YOUR_PAT_HERE" \
		  -H "Content-Type: application/json" \
		  -d @-  << FILEIN
		  {
			"inputs": [
			  {
				"data": {
				  "image": {
					"base64": "$(base64 /home/user/image.png)"
				  }
				}
			  }
			]
		  }
		  FILEIN
		  
	CURL request example (for url, food image recognition):
	curl -X POST "https://api.clarifai.com/v2/users/clarifai/apps/main/models/food-item-recognition/versions/1d5fd481e0cf4826aa72ec3ff049e044/outputs"   -H "Authorization: Key c3b688a85b874b73aac00311843b3dcd"   -H "Content-Type: application/json"   -d '{
		"inputs": [
		  {
			"data": {
			  "image": {
				"url": "https://samples.clarifai.com/metro-north.jpg"
			  }
			}
		  }
		]
	  }'
	  
	CURL request example (for base64 image data, food image recognition):
	curl -X POST "https://api.clarifai.com/v2/users/clarifai/apps/main/models/food-item-recognition/versions/1d5fd481e0cf4826aa72ec3ff049e044/outputs" \
		  -H "Authorization: Key YOUR_PAT_HERE" \
		  -H "Content-Type: application/json" \
		  -d @-  << FILEIN
		  {
			"inputs": [
			  {
				"data": {
				  "image": {
					"base64": "$(base64 /home/user/image.png)"
				  }
				}
			  }
			]
		  }
		  FILEIN
	 */
	
	val deferred = CompletableDeferred<String>()
	
	withContext(Dispatchers.IO) {
		
		val clarifaiAPIKey = "c3b688a85b874b73aac00311843b3dcd"
		val foodVisorAPIKey = "aACn5uvM.ItBrHXWrxu2tvUPMPm7xfCpBNWqZpOh8"
		
		//val clarifaiURL = "https://api.clarifai.com/v2/users/clarifai/apps/main/models/general-image-recognition/versions/aa7f35c01e0642fda5cf400f543e7c40/outputs"	// General recognition
		val clarifaiURL = "https://api.clarifai.com/v2/users/clarifai/apps/main/models/food-item-recognition/versions/1d5fd481e0cf4826aa72ec3ff049e044/outputs"		// Food recognition
		val foodvisorURL = "https://vision.foodvisor.io/api/1.0/en/analysis/"
		
		val urlToUse = when (apiType) {
			"clarifai" -> clarifaiURL
			"foodvisor" -> foodvisorURL
			else -> clarifaiURL
		}
		val requestMethod = when (apiType) {
			"clarifai" -> Request.Method.POST
			"foodvisor" -> Request.Method.POST
			else -> Request.Method.POST
		}
		val authStringToUse = when (apiType) {
			"clarifai" -> "Key $clarifaiAPIKey"
			"foodvisor" -> "Api-Key $foodVisorAPIKey"
			else -> "Key $clarifaiAPIKey"
		}
		
		if (apiType == "clarifai") {
			// JSON request (for Clarifai)
			val base64Data = byteData.encodeBase64()
			val jsonBody = JSONObject().apply {
				val inputs = JSONArray().apply {
					val input = JSONObject().apply {
						val data = JSONObject().apply {
							val image = JSONObject().apply {
								put("base64", base64Data)
							}
							put("image", image)
						}
						put("data", data)
					}
					put(input)
				}
				put("inputs", inputs)
			}
			val jsonObjectRequest = object : JsonObjectRequest(
				requestMethod, urlToUse, jsonBody,
				Response.Listener { response ->
					val outputs = response.getJSONArray("outputs")
					val data = outputs.getJSONObject(0).getJSONObject("data")
					val concepts = data.getJSONArray("concepts")
					var clarifaiResponse: String = ""
					for (i in 0 until concepts.length()) {
						val concept = concepts.getJSONObject(i)
						val name = concept.getString("name")
						val value = concept.getDouble("value")
						clarifaiResponse += "$name: $value\n"
					}
					deferred.complete(clarifaiResponse)
				},
				Response.ErrorListener { error ->
					deferred.complete("Request failed: ${error.cause} - ${error.message}")
				}
			) {
				override fun getHeaders(): Map<String, String> {
					val headers = HashMap<String, String>()
					headers["Authorization"] = authStringToUse
					headers["Content-Type"] = "application/json"
					return headers
				}
			}
			volleyRequestQueue.add(jsonObjectRequest)
		} else if (apiType == "foodvisor") {
			// Multipart request (for Foodvisor)
			// Add Authorization header
			val headers = mapOf("Authorization" to authStringToUse)
			// Add parameters and file part ("image" and "scopes" with "multiple_items" in the scopes, as an array)
			//val params = mapOf("param1" to "value1", "param2" to "value2")
			//val filePart = Pair("image", byteData)
			val params = mapOf("scopes" to "[\"multiple_items\"]")
			val filePart = Pair("image", byteData)
			
			val multipartRequest = MultipartRequest(
				urlToUse,
				headers,
				params,
				filePart,
				{ response ->
					val responseString = String(response.data, Charset.forName(HttpHeaderParser.parseCharset(response.headers)))
					deferred.complete(responseString)
				},
				{ error ->
					deferred.complete("Request failed: ${error.message}")
				}
			)
			
			volleyRequestQueue.add(multipartRequest)
		} else {
			deferred.complete("Invalid API type: $apiType")
		}
		
	}
	
	return deferred.await()
}

class MultipartRequest(
	url: String,
	private val headers: Map<String, String>,
	private val params: Map<String, String>,
	private val filePart: Pair<String, ByteArray>,
	private val listener: Response.Listener<NetworkResponse>,
	errorListener: Response.ErrorListener
) : Request<NetworkResponse>(Method.POST, url, errorListener) {
	
	private val boundary = "apiclient-${System.currentTimeMillis()}"
	private val mimeType = "multipart/form-data;boundary=$boundary"
	
	override fun getHeaders(): Map<String, String> {
		return headers
	}
	
	override fun getBodyContentType(): String {
		return mimeType
	}
	
	override fun getBody(): ByteArray {
		val bos = ByteArrayOutputStream()
		val dos = DataOutputStream(bos)
		
		try {
			// Add text parameters
			for ((key, value) in params) {
				dos.writeBytes("--$boundary\r\n")
				dos.writeBytes("Content-Disposition: form-data; name=\"$key\"\r\n\r\n")
				dos.writeBytes("$value\r\n")
			}
			
			// Add file parameter
			dos.writeBytes("--$boundary\r\n")
			dos.writeBytes("Content-Disposition: form-data; name=\"${filePart.first}\"; filename=\"image.jpg\"\r\n")
			dos.writeBytes("Content-Type: application/octet-stream\r\n\r\n")
			dos.write(filePart.second)
			dos.writeBytes("\r\n")
			
			dos.writeBytes("--$boundary--\r\n")
		} catch (e: IOException) {
			e.printStackTrace()
		} finally {
			try {
				dos.flush()
				dos.close()
			} catch (e: IOException) {
				e.printStackTrace()
			}
		}
		
		return bos.toByteArray()
	}
	
	override fun parseNetworkResponse(response: NetworkResponse): Response<NetworkResponse> {
		return Response.success(response, HttpHeaderParser.parseCacheHeaders(response))
	}
	
	override fun deliverResponse(response: NetworkResponse) {
		listener.onResponse(response)
	}
}

// Convert a ByteArray to a Base64 string
fun ByteArray.encodeBase64(): String {
	return Base64.encodeToString(this, Base64.DEFAULT)
}

@Composable
fun CameraPreview(onImageCaptured: (File) -> Unit, onClose: () -> Unit) {
	val context = LocalContext.current
	val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
	val resolutionSelector = ResolutionSelector.Builder()
		.setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
		.setResolutionStrategy(ResolutionStrategy(Size(960, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER))
		.build()
	val imageCapture = remember {
		ImageCapture.Builder()
			.setResolutionSelector(resolutionSelector)
			.build()
	}
	val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
	
	// Set a column layout to show camera preview and capture button
	Column(
		modifier = Modifier
			.fillMaxSize()
			.padding(15.dp, 45.dp, 15.dp, 45.dp),
		verticalArrangement = Arrangement.Center,
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		// Show a title text
		Text(
			text = "Scan food items to analyze",
			fontSize = 20.sp,
			fontWeight = FontWeight.Bold,
			textAlign = TextAlign.Center,
			color = Color.White
		)
		// Show camera preview
		AndroidView(
			// Use the same aspect ratio as the camera preview
			modifier = Modifier
				//.fillMaxSize()
				//.aspectRatio(4f / 3f)
				.padding(10.dp)
				//.background(Color.Black)
				.clip(RoundedCornerShape(10.dp))
			,
			factory = { ctx ->
				val previewView = PreviewView(ctx)
				val preview = Preview.Builder().build().also {
					it.setSurfaceProvider(previewView.surfaceProvider)
				}
				try {
					val cameraProvider = cameraProviderFuture.get()
					val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
					cameraProvider.unbindAll()
					cameraProvider.bindToLifecycle(
						ctx as LifecycleOwner, cameraSelector, preview, imageCapture
					)
				} catch (exc: Exception) {
					Log.e("CameraPreview", "Use case binding failed", exc)
				}
				previewView
			}
		) {
			// Do nothing...
		}
		// Show buttons in a row
		Row(
			modifier = Modifier.padding(0.dp),
			horizontalArrangement = Arrangement.spacedBy(10.dp)
		) {
			// Show exit button (close without capturing)
			//Button(
			//	modifier = Modifier.padding(4.dp),
			//	colors = ButtonDefaults.buttonColors(
			//		containerColor = Color.Gray,
			//		contentColor = Color.White
			//	),
			//	onClick = {
			//		onClose()
			//	},
			//	content = {
			//		Text("Exit")
			//	}
			//)
			CustomButton(
				text = "CANCEL",
				filled = false,
				Color.White,
				Color.White,
				//Color(ContextCompat.getColor(context, R.color.main_color)),
				RoundedCornerShape(100),
				fillWidth = false,
				sizeX = 135.dp,
				onClick = {
					onClose()
				}
			)
			// Show capture button
			//Button(
			//	modifier = Modifier.padding(4.dp),
			//	colors = ButtonDefaults.buttonColors(
			//		containerColor = Color.Gray,
			//		contentColor = Color.White
			//	),
			//	onClick = {
			//		val photoFile = File(
			//			context.externalMediaDirs.first(),
			//			"${System.currentTimeMillis()}.jpg"
			//		)
			//
			//		val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
			//		imageCapture.takePicture(
			//			outputOptions, ContextCompat.getMainExecutor(context),
			//			object : ImageCapture.OnImageSavedCallback {
			//				override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
			//					onImageCaptured(photoFile)
			//					onClose() // Close the camera view
			//				}
			//
			//				override fun onError(exception: ImageCaptureException) {
			//					Log.e(
			//						"CameraPreview",
			//						"Photo capture failed: ${exception.message}",
			//						exception
			//					)
			//				}
			//			}
			//		)
			//	},
			//	content = {
			//		Text("Capture")
			//	}
			//)
			CustomButton(
				text = "CAPTURE",
				filled = true,
				Color.White,
				Color(ContextCompat.getColor(context, R.color.main_color)),
				RoundedCornerShape(100),
				fillWidth = false,
				sizeX = 135.dp,
				onClick = {
					val photoFile = File(
						context.externalMediaDirs.first(),
						"${System.currentTimeMillis()}.jpg"
					)
					val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
					imageCapture.takePicture(
						outputOptions, ContextCompat.getMainExecutor(context),
						object : ImageCapture.OnImageSavedCallback {
							override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
								onImageCaptured(photoFile)
								onClose() // Close the camera view
							}
							
							override fun onError(exception: ImageCaptureException) {
								Log.e(
									"CameraPreview",
									"Photo capture failed: ${exception.message}",
									exception
								)
							}
						}
					)
				}
			)
			
		}
	}
}

// Function to send a request to the Spoonacular API and get nutritional values about a food item (if foodItem is the ID of the food item) or to search for a food item (if foodItem is the name of the food item)
// 	NOTE: if "useFoodItemAsSearchString" is true, the function will search for the food items with the given string and use the top result to get the nutritional values
suspend fun SendFoodNutritionalValueAPIRequest(
	volleyRequestQueue: RequestQueue,
	foodItem: String,
	useFoodItemAsSearchString: Boolean = true,
	apiKeyIndex: Int = 0
): String {
	
	val deferred = CompletableDeferred<String>()
	
	suspend fun sendRequest(url: String): JSONObject? {
		val requestDeferred = CompletableDeferred<JSONObject?>()
		val jsonObjectRequest = object : JsonObjectRequest(
			Method.GET, url, null,
			Response.Listener { response ->
				requestDeferred.complete(response)
			},
			Response.ErrorListener { error ->
				requestDeferred.complete(null)
			}
		) {
			override fun getHeaders(): Map<String, String> {
				val headers = HashMap<String, String>()
				headers["Content-Type"] = "application/json"
				return headers
			}
		}
		volleyRequestQueue.add(jsonObjectRequest)
		return requestDeferred.await()
	}
	
	withContext(Dispatchers.IO) {
		val spoonacularAPIKeys = listOf(
			"74e8eef5ad57439fab44f7591b3ace3d",	// panecaldoaldo
			"af34fc9b1eda49378fe745b017e008db",	// bronks.gd
			"655a4360902545af924d60b87c297e45",	// valeriodstfn
			"b8e4da9e04024b85b59ca17194be36dc"	// valeriodstf
		)
		val spoonacularAPIKey = spoonacularAPIKeys[apiKeyIndex]
		val foodResultsURL = "https://api.spoonacular.com/food/ingredients/search?query=$foodItem&apiKey=$spoonacularAPIKey"
		val foodNutritionalValueURL = "https://api.spoonacular.com/food/ingredients/$foodItem/information?apiKey=$spoonacularAPIKey&amount=100&unit=grams"
		
		if (useFoodItemAsSearchString) {
			val searchResponse = sendRequest(foodResultsURL)
			// Check if the response code is 402 or it gave an error (API limit quota reached)
			if (searchResponse == null || searchResponse.has("status") && searchResponse.getInt("status") == 402) {
				Log.d("WARNING: API key limit reached", "API key limit reached, trying with another key...")
				// Send another request with the next API key
				val nextAPIKeyIndex = apiKeyIndex + 1
				if (nextAPIKeyIndex < spoonacularAPIKeys.size) {
					deferred.complete(SendFoodNutritionalValueAPIRequest(volleyRequestQueue, foodItem, useFoodItemAsSearchString, nextAPIKeyIndex))
				} else {
					Log.d("ERROR: API key limit reached", "API key limit reached for ALL available spoonacular keys")
					deferred.complete("FAILED")
				}
			}
			searchResponse?.let {
				val results = it.getJSONArray("results")
				if (results.length() > 0) {
					val firstResult = results.getJSONObject(0)
					val id = firstResult.getInt("id")
					val nutritionalResponse = sendRequest("https://api.spoonacular.com/food/ingredients/$id/information?apiKey=$spoonacularAPIKey&amount=100&unit=grams")
					nutritionalResponse?.let { response ->
						// Check if the response has an error
						if (response.has("error")) {
							Log.d("ERROR: API request failed", response.getString("message"))
							deferred.complete("Request failed: ${response.getString("message")}")
						}
						// Return a formatted string with the results
						//val name = response.getString("name")
						//val macronutriens = response.getJSONObject("nutrition").getJSONObject("caloricBreakdown").toString()
						//val nutriens = response.getJSONObject("nutrition").getJSONArray("nutrients").toString()
						//deferred.complete("$name: $id\n$macronutriens\n$nutriens")
						// Simply return the whole response's JSON object as a string
						deferred.complete(response.toString())
					} ?: deferred.complete("Request failed: Nutritional information not found")
				} else {
					deferred.complete("No results found for the food item: $foodItem")
				}
			} ?: deferred.complete("Request failed: Search request failed")
		} else {
			val nutritionalResponse = sendRequest(foodNutritionalValueURL)
			nutritionalResponse?.let { response ->
				val name = response.getString("name")
				val id = response.getInt("id")
				val image = response.getString("image")
				deferred.complete("$name: $id\n$image")
			} ?: deferred.complete("Request failed: Nutritional information not found")
		}
	}
	
	return deferred.await()
}

@RequiresApi(Build.VERSION_CODES.O)
private fun CheckCanStartWeightMeasurement(
	sensorDataMeasurements : MutableList<SensorData>,
) : Pair<Boolean, String> {
	// Checks if some condition are met to start the scale measurement
	
	// Auxiliary function to get a bool and message value and log the message if needed
	fun getErrorResult(message: String) : Pair<Boolean, String> {
		//Log.d("CheckCanStartScaleMeasurement", message)
		return Pair(false, message)
	}
	
	// Check if all last measurements are valid
	for (i in 0 until sensorDataMeasurements.size) {
		
		// Get the sensor data
		val sensorData = sensorDataMeasurements[ sensorDataMeasurements.size - 1 - i ]
		
		// Check if the device is currently face down on a flat surface
		//	NOTE: to do this, check if the measurements of the accelerometer on the z axis are less than -9 m/s^2 (i.e. the device is facing down) while on the x and y axes are below 1 m/s^2 (i.e. the device is on a flat surface)
		val xyThresholdAbs = 2
		val zThreshold = -7.0
		val accelerometer = sensorData.accelerometer
		if (abs(accelerometer.first) > xyThresholdAbs || abs(accelerometer.second) > xyThresholdAbs) {
			// The device is not on a flat surface
			return getErrorResult("Device is not on a flat surface")
		}
		if (accelerometer.third > zThreshold) {
			// The device is not facing down
			return getErrorResult("Device is not facing down")
		}
		
		// Check if the device is not moving
		//	NOTE: to do this, check if the measurements of the linear acceleration are below a certain threshold
		val linearAccelerationThreshold = 2.0
		val linearAcceleration = sensorData.linearAcceleration
		if (abs(linearAcceleration.first) > linearAccelerationThreshold || abs(linearAcceleration.second) > linearAccelerationThreshold || abs(linearAcceleration.third) > linearAccelerationThreshold) {
			// The device is moving
			return getErrorResult("Device is moving")
		}
		
		// Check if the device is not rotating
		//	NOTE: to do this, check if the measurements of the gyroscope are below a certain threshold
		val gyroscopeThreshold = 0.375
		val gyroscope = sensorData.gyroscope
		if (abs(gyroscope.first) > gyroscopeThreshold || abs(gyroscope.second) > gyroscopeThreshold || abs(gyroscope.third) > gyroscopeThreshold) {
			// The device is rotating
			return getErrorResult("Device is rotating")
		}
		
	}
	
	// All checks passed
	return Pair(true, "OK: Can start weight measurement")
	
}

// Play a sound from a sound resource ID
fun playSound(context: Context, soundResId: Int, pitch : Float = 1.0f, volume : Float = 1.0f) {
	val mediaPlayer = MediaPlayer.create(context, soundResId)
	mediaPlayer.setOnCompletionListener {
		it.release()
	}
	mediaPlayer.setVolume(volume, volume)
	mediaPlayer.setPlaybackParams(mediaPlayer.playbackParams.setPitch(pitch))
	mediaPlayer.start()
}

// Volume change broadcast receiver class
class VolumeChangeReceiver(private val onVolumeChanged: (Int) -> Unit) : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
			val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
			val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
			onVolumeChanged(currentVolume)
		}
	}
}


