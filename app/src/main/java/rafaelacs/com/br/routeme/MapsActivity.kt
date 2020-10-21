package rafaelacs.com.br.routeme

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.TypeFilter
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import java.util.*
import kotlin.collections.ArrayList

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var lastLocation: Location
    private lateinit var placesClient: PlacesClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val apiKey = getString(R.string.google_maps_key)

        Places.initialize(applicationContext, apiKey)
        val placesClient = Places.createClient(this)

        initAutocompleteSupportFragment()
    }

    /**
     * Manipula o mapa quando disponível.
     * Este callback é chamado quando o mapa está pronto para ser usado.
     * Adiciona um marcador em Anápolis - GO e move a câmera.
     * Se Google Play services não estiver instalado, será solicitado ao usuário sua instalação.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        val latitude = -16.3282951
        val longitude = -48.9458506
        val zoomLevel = 15f

        val anapolis = LatLng(latitude, longitude)
        map.addMarker(MarkerOptions().position(anapolis).title("Marcador em Anápolis - GO"))
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(anapolis, zoomLevel))
        map.uiSettings.isZoomControlsEnabled = true

        setMapLongClick(map)
        setPoiClick(map)
        enableMyLocation()
    }

    /*
     * Infla o menu de opções.
     */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.map_options, menu)
        return true
    }

    /*
     * Muda o tipo do mapa de acordo com a seleção do usuário.
     */
    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.normal_map -> {
            map.mapType = GoogleMap.MAP_TYPE_NORMAL
            true
        }
        R.id.hybrid_map -> {
            map.mapType = GoogleMap.MAP_TYPE_HYBRID
            true
        }
        R.id.satellite_map -> {
            map.mapType = GoogleMap.MAP_TYPE_SATELLITE
            true
        }
        R.id.terrain_map -> {
            map.mapType = GoogleMap.MAP_TYPE_TERRAIN
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    /*
     * Verifica se o requestCode é igual a REQUEST_LOCATION_PERMISSION.
     * Se sim, significa que a permissão foi garantida.
     * Se a permissão for garantida, também verifica se o array grantResults
     * tem PackageManager.PERMISSION_GRANTED no seu primeiro slot.
     * Se for verdadeiro, chama enableMyLocation().
     */
    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray) {
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.contains(PackageManager.PERMISSION_GRANTED)) {
                enableMyLocation()
            }
        }
    }

   /*
    * Ao iniciar a Activity, o método é chamado com o código de Autocomplete.
    * Se o resultCode for Ok, localiza o local pesquisado.
    * Se o resultCode for Erro, mostra o status no Logcat.
    */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == AUTOCOMPLETE_REQUEST_CODE) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    data?.let {
                        val place = Autocomplete.getPlaceFromIntent(data)
                        val namePlace = place.name
                        val latLngPlace = place.latLng
                        map.addMarker(latLngPlace?.let { it1 -> MarkerOptions().position(it1).title(namePlace) })
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLngPlace, 15f))
                        val updateCamera = CameraUpdateFactory.newLatLngZoom(latLngPlace, 15f)
                        map.animateCamera(updateCamera)
                        Log.i(TAG, "Place: ${place.name}, ${place.id}")
                    }
                }
                AutocompleteActivity.RESULT_ERROR -> {
                    // TODO: Handle the error.
                    data?.let {
                        val status = Autocomplete.getStatusFromIntent(data)
                        status.statusMessage?.let { it1 -> Log.i(TAG, it1) }
                    }
                }
                Activity.RESULT_CANCELED -> {
                    // The user canceled the operation.
                }
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    /* Métodos privados */

    /*
     * Inicializa o AutocompleteSupportFragment, especifica o tipo de dados de
     * Place para retorno e cria um PlaceSelecionListener para este retorno.
     */
    private fun initAutocompleteSupportFragment() {
        val autocompleteFragment =
                supportFragmentManager.findFragmentById(R.id.autocomplete_fragment)
                        as AutocompleteSupportFragment
        autocompleteFragment.setPlaceFields(listOf(Place.Field.ID, Place.Field.NAME,
                Place.Field.LAT_LNG))
        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(@NonNull place: Place) {
                Log.i(TAG, "Place found: " + place.name)
                val newName = place.name
                val newLatLng = place.latLng

                val markerOpt = MarkerOptions()
                if (newLatLng != null) {
                    map.addMarker(MarkerOptions().position(newLatLng).title(newName))
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(newLatLng, 15f))
                }
                markerOpt.title(newName)
                markerOpt.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))

                Log.i(TAG, "Place: ${place.name}, ${place.id}")
            }

            override fun onError(@NonNull status: Status) {
                // TODO: Handle the error.
                Log.i(TAG, "An error occurred: $status")
            }
        })

        autocompleteFragment.setTypeFilter(TypeFilter.ADDRESS)

        val fields: List<Place.Field> = ArrayList()
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
                .setTypeFilter(TypeFilter.ADDRESS)
                .build(this)
        startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE)

        autocompleteFragment.setCountry("BR")
    }

    /*
     * Adiciona um marcador quando o usuário clicar e segurar no mapa
     * com latitude e longitude do local.
     */
    private fun setMapLongClick(map: GoogleMap) {
        map.setOnMapLongClickListener { latLng ->
            val snippet = String.format(
                    Locale.getDefault(),
                    "Lat: %1$.5f, Long: %2$.5f",
                    latLng.latitude,
                    latLng.longitude
            )

            map.addMarker(
                    MarkerOptions()
                            .position(latLng)
                            .title(getString(R.string.dropped_pin))
                            .snippet(snippet)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
            )
        }
    }

    /*
     * Coloca um marcador no mapa depois que o usuário clicar em um Ponto de
     * Interesse (POI).
     * O click listener também mostra uma janela com informações como o
     * nome do POI.
     */
    private fun setPoiClick(map: GoogleMap) {
        map.setOnPoiClickListener { poi ->
            val poiMarker = map.addMarker(
                    MarkerOptions()
                            .position(poi.latLng)
                            .title(poi.name)
            )
            poiMarker.showInfoWindow()
        }
    }

    /*
     * Vrrifica se as permissões foram concedidas pelo usuário.
     */
    private fun isPermissionGranted() : Boolean {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    /*
     * Habilita a localização do usuário no mapa.
     * Se a permissão foi garantida, habilita a camada de localização e obtem
     * a última localização
     * Senão, pede a permissão ao usuário.
     */
    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {
        if (isPermissionGranted()) {
            map.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener(this) { location ->
                if(location != null) {
                    lastLocation = location
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 10f))
                }
            }
        }
        else {
            ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_LOCATION_PERMISSION
            )
        }
    }


    companion object {
        private val TAG = MapsActivity::class.simpleName
        private const val REQUEST_LOCATION_PERMISSION = 1
        private const val AUTOCOMPLETE_REQUEST_CODE = 2
    }

}