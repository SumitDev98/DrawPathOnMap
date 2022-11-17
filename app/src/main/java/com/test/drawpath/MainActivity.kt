package com.test.drawpath

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private var googleMap: GoogleMap? = null
    companion object {
        val store = LatLng(22.7412089, 75.9002928)
        val origin = LatLng(22.7439445, 75.8896707)
        val destination = LatLng(22.7264165, 75.8793988)
        const val WAY_POINT_TAG = "way_point_tag"
    }
    private val allLocationList: ArrayList<LatLng> = ArrayList()
    private var polyLineDataBean = PolyLineDataBean()
    private var arrayOfPoints: ArrayList<ArrayList<LatLng>> = ArrayList()
    private val allPathPoints:ArrayList<LatLng> = ArrayList()
    private var polyLineDetailsArray:ArrayList<PolyLineDataBean> = ArrayList()
    private var polylineMap: HashMap<String, ArrayList<PolylineBean>> = HashMap()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap!!.uiSettings.isMapToolbarEnabled = false
        googleMap!!.animateCamera(CameraUpdateFactory.newLatLngZoom(origin, 14f))
        createMarkerAndStraightPolyline()
        initPath()
    }

    private fun getBitmapFromVectorDrawable(context: Context?, drawableId: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context!!, drawableId)
        val bitmap = Bitmap.createBitmap(
            drawable!!.intrinsicWidth,
            drawable.intrinsicHeight, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun createMarkerAndStraightPolyline() {
        allLocationList.add(store)
        allLocationList.add(origin)
        allLocationList.add(destination)
        googleMap?.let { mMap ->
            mMap.clear()
            allLocationList.forEach { location ->
                val latLng = LatLng(location.latitude, location.longitude)
                val markerOptions = MarkerOptions()
                markerOptions.position(latLng)
                markerOptions.position(location).icon(
                    getBitmapFromVectorDrawable(
                        this,
                        R.drawable.ic_blue_location_pin
                    )?.let {
                        BitmapDescriptorFactory.fromBitmap(it)
                    }
                )
                mMap.addMarker(markerOptions)
            }
        }

        CoroutineScope(Dispatchers.Main).launch {
            showCurvedPolyline(
                origin,
                destination,
                0.06,
                Color.parseColor("#CDCED1")
            )
        }

        CoroutineScope(Dispatchers.Main).launch {
            showCurvedPolyline(origin, destination, 0.15, Color.parseColor("#292D40"))
        }
        CoroutineScope(Dispatchers.Main).launch {
            val heading = SphericalUtil.computeHeading(origin, destination)
            Log.v("MapHeading", "MapHeading : $heading")
        }
    }

    private fun latLngCorrect(startLatLng: LatLng, endLatLng: LatLng): Boolean {
        var start = false
        var end = false
        if (startLatLng.latitude > 0 || startLatLng.longitude > 0) {
            start = true
        }
        if (endLatLng.latitude > 0 || endLatLng.longitude > 0) {
            end = true
        }
        return start && end
    }

    /**
     * This function will draw a curved polyline between two location p1 & p2 having radius of
     * curvedRadius (0-1 of float value) and of given color.
     * Color should be { Color.parseColor("#HEXCLR") or Color.CLRVALUE}
     * */
    private fun showCurvedPolyline(p1: LatLng, p2: LatLng, curvedRadius: Double, color: Int) {
        if (latLngCorrect(p1, p2)) {
            //Calculate distance and heading between two points
            val distance = SphericalUtil.computeDistanceBetween(p1, p2)
            val heading = SphericalUtil.computeHeading(p1, p2)
            //Midpoint position
            val p = SphericalUtil.computeOffset(p1, distance * 0.5, heading)
            //Apply some mathematics to calculate position of the circle center
            val x = (1 - curvedRadius * curvedRadius) * distance * 0.5 / (2 * curvedRadius)
            val r = (1 + curvedRadius * curvedRadius) * distance * 0.5 / (2 * curvedRadius)
            val c = SphericalUtil.computeOffset(p, x, heading - 90.0)
            //Polyline options
            val options = PolylineOptions()
            //val pattern: ArrayList<PatternItem> = arrayListOf(Dash(20f), Gap(8f))
            val pattern: ArrayList<PatternItem> = arrayListOf(Dash(12f), Gap(10f))
            //Calculate heading between circle center and two points
            val h1 = SphericalUtil.computeHeading(c, p1)
            val h2 = SphericalUtil.computeHeading(c, p2)
            //Calculate positions of points on circle border and add them to polyline options
            val numpoints = 100
            val step = (h2 - h1) / numpoints
            for (i in 0 until numpoints) {
                val pi = SphericalUtil.computeOffset(c, r, h1 + i * step)
                options.add(pi)
            }
            //Draw polyline
            googleMap?.addPolyline(options.width(3f).color(color).geodesic(false).pattern(pattern))
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Throws(Exception::class)
    fun initPath() {
        if (allPathPoints.isNotEmpty()){
            allPathPoints.clear()
            polyLineDetailsArray.clear()
        }
        origin.let { _ ->
            destination.let { _ ->
                GlobalScope.launch(Dispatchers.Main + mainException) {
                    val data = downloadDataFromUrlAsync(origin, destination)
                    drawData(parseDownloadedDataAsync(data.await()).await())
                }
            } ?: kotlin.run {
                throw Exception("Make sure destination not null")
            }
        } ?: kotlin.run {
            throw Exception("Make sure Origin not null")
        }
    }

    private fun downloadDataFromUrlAsync(o:LatLng, d:LatLng): Deferred<String> = runBlocking {
        val url = getUrl(o, d)
        async(Dispatchers.IO + downloadDataFromUrlException) { downloadUrl(url) }
    }

    private fun parseDownloadedDataAsync(data:String) = runBlocking {
        async(Dispatchers.IO + parseDataFromUrlException) { doParsingWork(data) }
    }

    private fun doParsingWork(jsonData: String): List<List<HashMap<String, String>>> {
        val jObject = JSONObject(jsonData)
        Log.e("EstimationTimeData", "Data : $jObject")
        val parser = DataParser()
        return parser.parse(jObject)
    }

    private fun drawData(result: List<List<HashMap<String, String>>>) {
        arrayOfPoints.clear()
        var points: ArrayList<LatLng>
        // Traversing through all the routes
        for (i in result.indices) {
            points = ArrayList()
            //lineOptions = PolylineOptions()
            // Fetching i-th route
            val path = result[i]
            // Fetching all the points in i-th route
            for (j in path.indices) {
                val point = path[j]
                val lat = java.lang.Double.parseDouble(point["lat"]!!)
                val lng = java.lang.Double.parseDouble(point["lng"]!!)
                val position = LatLng(lat, lng)
                points.add(position)
                allPathPoints.add(position)
                arrayOfPoints.add(allPathPoints)
            }
        }
        polyLineDetailsArray.add(polyLineDataBean)
        drawPath(WAY_POINT_TAG)
    }

    fun drawPath(mTag:String){
        val polylineArray = java.util.ArrayList<PolylineBean>()
        for(array in arrayOfPoints){
            val lineOptions = PolylineOptions()
            // Adding all the points in the route to LineOptions
            lineOptions.addAll(array)
            lineOptions.jointType(JointType.ROUND)
            lineOptions.width(12f)
            lineOptions.color(Color.parseColor("#4594E1"))
            lineOptions.clickable(true)
            polylineArray.add(PolylineBean(googleMap?.addPolyline(lineOptions),null))
        }
        polylineMap.put(mTag,polylineArray)
    }

    private val downloadDataFromUrlException = CoroutineExceptionHandler { _, exception ->
        exception.message?.let {
        }
    }

    private val parseDataFromUrlException = CoroutineExceptionHandler { _, exception ->
        exception.message?.let {
        }
    }

    private val mainException = CoroutineExceptionHandler { _, exception ->
        exception.message?.let {
        }
    }

    private fun downloadUrl(strUrl: String): String {
        val data: String
        var iStream: InputStream? = null
        val urlConnection: HttpURLConnection?
        val url = URL(strUrl)
        // Creating an http connection to communicate with url
        urlConnection = url.openConnection() as HttpURLConnection
        // Connecting to url
        urlConnection.connect()
        // Reading data from url
        iStream = urlConnection.inputStream
        val br = BufferedReader(InputStreamReader(iStream))
        val sb = StringBuffer()
        val strings = br.readLines()
        for( i in strings){
            sb.append(i)
        }
        data = sb.toString()
        br.close()
        iStream?.close()
        urlConnection.disconnect()
        return data
    }

    private fun getUrl(origin: LatLng, dest: LatLng): String {
        // Origin of route
        val str_origin = "origin=" + origin.latitude + "," + origin.longitude
        // Destination of route
        val str_dest = "destination=" + dest.latitude + "," + dest.longitude
        // Sensor enabled
        val sensor = "sensor=false"
        //directionKey
        val key = "key=" + getString(R.string.google_maps_key)
        val departureTime = "departure_time=now"
        // Building the parameters to the web service
        val parameters = "$str_origin&$str_dest&$sensor&$key"
        // Output format
        val output = "json"
        // Building the url to the web service
        return "https://maps.googleapis.com/maps/api/directions/$output?$parameters"
    }

}