package com.loera.nomad;

import android.animation.ObjectAnimator;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.NotificationCompat;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Display;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResolvingResultCallbacks;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.adw.library.widgets.discreteseekbar.DiscreteSeekBar;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback,SongChooser.SongListener, ListView.OnItemClickListener{

    static boolean TEST_MODE = false;
    private final String TAG = "Main Activity";
    final int GEOFENCE_RADIUS = 45;


    GoogleApiClient playServices;
    LocationRequest locationRequest;

    Context context;
    PendingIntent mGeofencePendingIntent;
    private static String occupied = "none";

    GoogleMap googleMap;

    LatLng currentLatLng;
    Location currentLocation;

    NotificationCompat.Builder notification;
    NotificationManager notMgr;

    ArrayList<String> names;
    List<CircleOptions> circles;
    HashMap<String,MusicSpot> musicSpots;

    GeofenceReciever reciever;

    MediaPlayer musicPlayer;
    static int touchY;
    static long touchTime;
    ActionBarDrawerToggle toggle;

    static String[] currentSong;

    DiscreteSeekBar seekBar;
    android.os.Handler seekUpdater;
    Runnable secondCheck;

    static boolean expanded = false;
    static boolean slidePlayer = false;
    static boolean addingGeofence = false;
    static boolean UISetup = false;
    static boolean followGps = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        new LogWriter().execute();
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        context = this;


        setupDrawer();
        if(savedInstanceState == null){
        musicSpots = new HashMap<>();
        circles = new LinkedList<>();
            names = new ArrayList<>();
        }else{

            musicSpots = (HashMap) savedInstanceState.getSerializable("musicSpots");
            occupied = savedInstanceState.getString("occupied");
            circles = getCirclesFromList(savedInstanceState.getStringArrayList("circles"));
            names = savedInstanceState.getStringArrayList("names");
            UISetup = savedInstanceState.getBoolean("UISetup");
        }

        reciever = new GeofenceReciever();
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.aol.android.geofence.ACTION_RECEIVE_GEOFENCE");
        filter.addAction("com.nomad.ACTION_NOTIFICATION_PRESS");
        registerReceiver(reciever, filter);

        MapFragment map = (MapFragment) getFragmentManager().findFragmentById(R.id.mapFragment);
        map.getMapAsync(this);
        createLocationRequest();
        buildGoogleApiClient();

        playServices.connect();

    }

    public void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);

        state.putSerializable("musicSpots", musicSpots);
        state.putString("occupied", occupied);
        state.putStringArrayList("circles", getCirclesArrayList());
        state.putStringArrayList("names", names);
        state.putBoolean("UIsetup", UISetup);

    }

    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if(!UISetup){
            UISetup = true;
            setupUI();
        }
    }

    public void monitorPreviousGeofences(ArrayList<String> list){
        LinkedList<Geofence> fences = new LinkedList<>();

        for(int i = 0;i<list.size();i++){

            LatLng latLng = circles.get(i).getCenter();

            Location loc = new Location("Nomad");
            loc.setLongitude(latLng.longitude);
            loc.setLatitude(latLng.latitude);

            Geofence g = getCurrentGeofence(loc,list.get(i));

            fences.add(g);
        }
        addMusicSpots(fences);
    }

    public ArrayList<String> getCirclesArrayList(){
        ArrayList<String> l = new ArrayList();

        for(CircleOptions c:circles){

            String s = c.getCenter().latitude+","+c.getCenter().longitude+","+
                    c.getRadius()+","+c.getFillColor()+","+c.getStrokeColor()+","
                    +c.getStrokeWidth();
            l.add(s);

        }


        return l;

    }

    public LinkedList<CircleOptions> getCirclesFromList(ArrayList<String> l){

        LinkedList<CircleOptions> list = new LinkedList<>();

        for(String s : l){

            Scanner sc = new Scanner(s);
            sc.useDelimiter(",");

            CircleOptions circle = new CircleOptions();
            circle.center(new LatLng(sc.nextDouble(), sc.nextDouble()));
            circle.radius(sc.nextDouble());
            circle.fillColor(sc.nextInt());
            circle.strokeColor(sc.nextInt());
            circle.strokeWidth(sc.nextFloat());

            list.add(circle);
        }
        return list;
    }

    public void setupUI(){

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        final RelativeLayout player = (RelativeLayout) findViewById(R.id.player);

        FrameLayout map = (FrameLayout) findViewById(R.id.mapLayout);
        Display display = getWindowManager().getDefaultDisplay();
        final Point point = new Point();
        display.getSize(point);
        Resources resources = context.getResources();
        int resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android");

        float navigationBarHeight;
        if (resourceId > 0) {
            navigationBarHeight = resources.getDimensionPixelSize(resourceId);
        }else {
            navigationBarHeight =  0;
        }

        boolean hasBackKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK);
        boolean hasHomeKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_HOME);

        final float height = point.y;
        final float width = point.x;
        //bottomBar and animation
        TextView text = (TextView)findViewById(R.id.infoBG);
        if(!(hasBackKey && hasHomeKey))
        setScaledHeight(text,width,height,21,128);
        else
            setScaledHeight(text,width,height,117,640);
        text = (TextView)findViewById(R.id.songText);
        text.setSelected(true);
        setScaledHeight(text,width,height,43,480);
        text = (TextView)findViewById(R.id.artsitAlbumText);
        text.setSelected(true);
        setScaledHeight(text,width,height,37,640);
        RelativeLayout controls = (RelativeLayout)findViewById(R.id.controlsLayout);
        controls.getLayoutParams().height = (int)(height - player.getLayoutParams().height) - controls.getLayoutParams().height;
        float buttonHeight =  (height - controls.getY())/2;
        setupButtons((int)buttonHeight/2);
        text = (TextView) findViewById(R.id.bottomBar);
        if(!(hasBackKey && hasHomeKey))
        setScaledHeight(text,width,height,11,128);
        else
            setScaledHeight(text,width,height,67,640);
        ImageView image = (ImageView)findViewById(R.id.albumArt);
        if(!(hasBackKey && hasHomeKey))
        image.getLayoutParams().height = (int)(height*7/16);
        else
            image.getLayoutParams().height = (int)(height*73/160);

        final float bottomBarSize = text.getLayoutParams().height;

        RelativeLayout contentMain = (RelativeLayout)findViewById(R.id.contentMain);

        final float closedPlayerY = contentMain.getHeight() - bottomBarSize;
        map.getLayoutParams().height = (int)closedPlayerY;
        player.setY(closedPlayerY);
        player.setX(0);

        text.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                float currentY = event.getRawY();

                if (event.getAction() == MotionEvent.ACTION_DOWN && !slidePlayer) {
                    slidePlayer = true;
                    touchY = (int) event.getY();
                    touchTime = System.currentTimeMillis();
                } else if (event.getAction() == MotionEvent.ACTION_MOVE && slidePlayer) {
                    currentY = currentY - bottomBarSize - touchY;
                    if (currentY > 0 && currentY < closedPlayerY)
                        player.setY(currentY);
                } else if (event.getAction() == MotionEvent.ACTION_UP && slidePlayer) {

                    float finalY = expanded ? closedPlayerY : 0;

                    float beginY = finalY == 0 ? closedPlayerY : 0;

                    float speed = getSpeedOfSwipe(touchTime, System.currentTimeMillis(), beginY, currentY, closedPlayerY);

                    if (speed > 200) {
                        speed = 200;
                    }

                    Log.i(TAG, "Speed is " + speed);

                    ObjectAnimator animator = ObjectAnimator.ofFloat(player, "y", finalY);
                    animator.setDuration((long) speed);
                    animator.start();

                    expanded = !expanded;

                    animateButtonsOnExpand();

                    if (!expanded && currentLatLng != null)
                        googleMap.animateCamera(CameraUpdateFactory.newLatLng(currentLatLng));

                    slidePlayer = false;
                }

                return false;
            }
        });

        setupSeekBar();

        player.setVisibility(View.VISIBLE);
    }

    public void setupSeekBar(){

        TextView text = (TextView)findViewById(R.id.infoBG);

        float height = text.getLayoutParams().height;
        float y = text.getY();

        seekBar = (DiscreteSeekBar) findViewById(R.id.musicSeekBar);

        float endOfView = y + height;

        seekBar.setY(endOfView - seekBar.getHeight() / 4);
        seekBar.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                if(event.getAction() == MotionEvent.ACTION_DOWN){
                    stopSeek();
                }else if(event.getAction() == MotionEvent.ACTION_UP){

                    int stoppedTime = seekBar.getProgress();

                    musicPlayer.pause();
                    musicPlayer.seekTo(stoppedTime);
                    musicPlayer.start();
                    setPlayOrPauseIcons("pause");
                    updateNotification();
                    startSeek(stoppedTime);

                }



                return false;
            }
        });

    }

    public void startSeek(final long initialSeekPosition){
        seekBar.setProgress((int) initialSeekPosition);
        seekBar.setMax(musicPlayer.getDuration());
        seekBar.setVisibility(View.VISIBLE);
        seekUpdater = new android.os.Handler();

        final long startTime = System.currentTimeMillis();

        secondCheck = new Runnable() {
            @Override
            public void run() {
                seekBar.setProgress((int) (initialSeekPosition + (System.currentTimeMillis() - startTime)));
                seekUpdater.postDelayed(secondCheck, 1000);
            }
        };

        secondCheck.run();

    }


    void stopSeek() {
        seekUpdater.removeCallbacks(secondCheck);
    }

    public void animateButtonsOnExpand(){

        ImageButton barButton = (ImageButton)findViewById(R.id.bar_play_pause);
        FloatingActionButton fab = (FloatingActionButton)findViewById(R.id.fab);

        float amountMoved = (float) (barButton.getLayoutParams().width * 1.5);
        if(expanded){

            ObjectAnimator moveRight = ObjectAnimator.ofFloat(barButton,"x",barButton.getX() + amountMoved);
            moveRight.setDuration(400);
            moveRight.start();
            fab.hide();

        }else{

            ObjectAnimator moveRight = ObjectAnimator.ofFloat(barButton,"x",barButton.getX() - amountMoved);
            moveRight.setDuration(200);
            moveRight.start();
            fab.show();

        }

    }

    public float getSpeedOfSwipe(long touchTime,long currentTime, float firstTouch,float secondTouch,float sizeOfBox){

        float slope = ((currentTime-touchTime)/(secondTouch-firstTouch));

        float distanceLeft = sizeOfBox - secondTouch;

        return Math.abs(slope * distanceLeft);

    }

    public void setScaledHeight(TextView text,float width,float height, double numerator,double denominator){

        text.getLayoutParams().height = (int)(height*numerator/denominator);

    }

    public void setupDrawer(){

        final DrawerLayout layout = (DrawerLayout)findViewById(R.id.homeDrawerLayout);

        toggle = new ActionBarDrawerToggle(this,layout,R.string.open_drawer,R.string.close_drawer){

            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                invalidateOptionsMenu();
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                invalidateOptionsMenu();
            }
        };

        layout.setDrawerListener(toggle);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);


    }

    public void onPostCreate(Bundle savedInstanceState){

        super.onPostCreate(savedInstanceState);
        toggle.syncState();

    }

    public void onDestroy() {
        unregisterReceiver(reciever);
        stopMusic();
        super.onDestroy();

    }

    public void stopMusic(){
        if(notMgr!=null)
        notMgr.cancelAll();
        if(musicPlayer!=null){
            musicPlayer.stop();
            musicPlayer.release();
            musicPlayer = null;
        }
        setPlayOrPauseIcons("play");
    }

    public void updateLocationUI() {

        TextView locationText = (TextView)findViewById(R.id.locationText);
       //Not Used Yet.
       // TextView claimedByText = (TextView)findViewById(R.id.claimedByText);

        Geocoder geocoder = new Geocoder(context);
        try{
        List<Address> addresses = geocoder.getFromLocation(currentLocation.getLatitude(),currentLocation.getLongitude(),10);
            if (addresses != null && addresses.size() > 0){
                Address address = addresses.get(0);
                locationText.setText(address.getAddressLine(0));
            }else{
                locationText.setText("Unknown Location");
            }
        }catch (IOException e){

            e.printStackTrace();

        }



    }

    public void updatePlayerUI(){

        currentSong = new String[]{"Unknown","Unknown","Unknown"};

        TextView songName = (TextView)findViewById(R.id.songText);
        TextView albumAndArtist = (TextView)findViewById(R.id.artsitAlbumText);
        ImageView albumArt = (ImageView)findViewById(R.id.albumArt);

        MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
        metadataRetriever.setDataSource(musicSpots.get(occupied).getSongFile());
        String songTitle = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);

        if(songTitle!=null) {
            songName.setText(songTitle);
            currentSong[0] = songTitle;
        }

        String artist = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
        String album = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);

        if(artist != null){
            albumAndArtist.setText(artist + " - ");
            currentSong[1] = artist;
        }

        if(album != null){
            albumAndArtist.setText(albumAndArtist.getText()+album);
            currentSong[2] = album;
        }

        byte[] byteArt = metadataRetriever.getEmbeddedPicture();

        if(byteArt!=null){
            Bitmap art = BitmapFactory.decodeByteArray(byteArt, 0, byteArt.length);
            int color = getAverageColor(art);
            albumArt.setBackground(new ColorDrawable(color));
            albumArt.setImageBitmap(art);
        }

    }

    public int getAverageColor(Bitmap b){

        long totalPixels = 0;
        long red = 0;
        long green = 0;
        long blue = 0;

        for(int y = 0;y<b.getHeight();y++){
            for(int x = 0;x<b.getWidth();x++){
                totalPixels++;
                int color = b.getPixel(x,y);
                red+= Color.red(color);
                green+= Color.green(color);
                blue+= Color.blue(color);
            }
        }
        return Color.rgb((int) (red / totalPixels), (int) (green / totalPixels), (int) (blue / totalPixels));
    }

    public Geofence getCurrentGeofence(Location currentLocation,String name) {

        return new Geofence.Builder()
                .setRequestId(name)
                .setCircularRegion(currentLocation.getLatitude(), currentLocation.getLongitude(), GEOFENCE_RADIUS)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT).build();

    }

    public void createLocationRequest() {

        locationRequest = new LocationRequest();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

    }

    public void startLocationUpdates() {

        LocationServices.FusedLocationApi.requestLocationUpdates(playServices, locationRequest, new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {

                if (!addingGeofence) {

                    boolean zoomCamera = currentLocation == null;

                    currentLocation = location;
                    currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());

                    MarkerOptions marker = new MarkerOptions()
                            .position(currentLatLng);

                    googleMap.clear();
                    addCircles();
                    googleMap.addMarker(marker);

                    if (followGps || zoomCamera)
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 18.0f));

                    Log.i(TAG, "Location updated, Marker updated");

                    if (occupied.equals("none"))
                        updateLocationUI();

                }
            }
        });
    }

    public void addCircles(){

        for(CircleOptions c:circles)
            googleMap.addCircle(c);

    }

    public void addMusicSpots(Geofence g) {

        LocationServices.GeofencingApi.addGeofences(
                playServices,
                getGeofencingRequest(g),
                getGeofencePendingIntent()
        ).setResultCallback(new ResolvingResultCallbacks<Status>(this, 1) {
            @Override
            public void onSuccess(Status status) {
                Log.i(TAG, "Monitoring Geofence");
            }

            @Override
            public void onUnresolvableFailure(Status status) {

                Log.i(TAG, "Monitoring Geofence Failed Status: " + status.toString());

            }
        });

    }
    public void addMusicSpots(LinkedList<Geofence> g) {

        LocationServices.GeofencingApi.addGeofences(
                playServices,
                getGeofencingRequest(g),
                getGeofencePendingIntent()
        ).setResultCallback(new ResolvingResultCallbacks<Status>(this, 1) {
            @Override
            public void onSuccess(Status status) {
                Log.i(TAG, "Monitoring Geofence");
            }

            @Override
            public void onUnresolvableFailure(Status status) {

                Log.i(TAG, "Monitoring Geofence Failed Status: " + status.toString());

            }
        });

    }

    public void getSong(){
        addingGeofence = true;
        SongChooser chooser = new SongChooser();
        chooser.show(getFragmentManager(), "Song Chooser");

    }

    public void addCircleAroundCurrentPosition(String name){

        String fillString = "#9D45BA";
        String strokeString = "#5358DB";


        CircleOptions circle = new CircleOptions().center(currentLatLng)
                .radius(GEOFENCE_RADIUS).fillColor(Color.parseColor(fillString))
                .strokeColor(Color.parseColor(strokeString))
                .strokeWidth(8.0f);

        googleMap.addCircle(circle);
        circles.add(circle);


        Log.i(TAG, "Circle added around current position");
    }

    protected synchronized void buildGoogleApiClient() {
        playServices = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle bundle) {
                        Toast.makeText(context, "Connected to Google Play Services", Toast.LENGTH_SHORT).show();
                        removeGeofences();
                        if(!names.isEmpty())
                            monitorPreviousGeofences(names);
                        startLocationUpdates();
                    }

                    @Override
                    public void onConnectionSuspended(int i) {

                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult connectionResult) {
                        Toast.makeText(context, "Failed to Connect", Toast.LENGTH_SHORT).show();
                    }
                })
                .addApi(LocationServices.API)
                .build();
    }

    private GeofencingRequest getGeofencingRequest(Geofence g) {
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
        builder.addGeofence(g);
        return builder.build();
    }
    private GeofencingRequest getGeofencingRequest(LinkedList<Geofence> g) {
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
        builder.addGeofences(g);
        return builder.build();
    }

    private PendingIntent getGeofencePendingIntent() {
        // Reuse the PendingIntent if we already have it.
        if (mGeofencePendingIntent != null) {
            return mGeofencePendingIntent;
        }
       // Intent intent = new Intent(this, GeofenceTransitionsIntentService.class);
        Intent intent = new Intent("com.aol.android.geofence.ACTION_RECEIVE_GEOFENCE");
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when
        // calling addGeofences() and removeGeofences().
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.
                FLAG_UPDATE_CURRENT);
    }

    public void setupButtons(int imageButtonHeight) {

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentLocation != null) {
                    if (occupied.equals("none")) {
                        getSong();
                    } else {

                        Snackbar.make(view, "Current Position Occupied", Snackbar.LENGTH_LONG).show();

                    }
                } else
                    Snackbar.make(view, "Waiting on location...", Snackbar.LENGTH_LONG).show();
            }
        });

        TextView infoBg = (TextView)findViewById(R.id.infoBG);
        float floatingButY = ((infoBg.getLayoutParams().height - fab.getLayoutParams().height)) + infoBg.getY();

        fab.setY(floatingButY);

        ImageButton but = (ImageButton) findViewById(R.id.play_pause);
        but.getLayoutParams().height = imageButtonHeight;
        but.getLayoutParams().width = imageButtonHeight;
        but = (ImageButton) findViewById(R.id.bar_play_pause);
        but.getLayoutParams().height = imageButtonHeight;
        but.getLayoutParams().width = imageButtonHeight;
        but.setY(but.getY() + (imageButtonHeight / 4));
        but.setX(but.getX() - (imageButtonHeight / 4));

    }

    public void removeGeofences(){
        LocationServices.GeofencingApi.removeGeofences(
                playServices,
                // This is the same pending intent that was used in addGeofences().
                getGeofencePendingIntent()
        ).setResultCallback(new ResolvingResultCallbacks<Status>(this, 3) {
            @Override
            public void onSuccess(Status status) {

                Log.i(TAG, "Removed Geofences");

            }

            @Override
            public void onUnresolvableFailure(Status status) {
                Log.i(TAG, "Could not remove Geofences, error: " + status.toString());
            }
        }); // Result processed in onResult().

    }

    @Override
    public void onMapReady(final GoogleMap googleMap) {
        googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        googleMap.setBuildingsEnabled(false);
        googleMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {
                addingGeofence = true;
                currentLatLng = latLng;
                currentLocation = new Location("Nomad");
                currentLocation.setLatitude(latLng.latitude);
                currentLocation.setLongitude(latLng.longitude);
                getSong();


            }
        });
       this.googleMap = googleMap;



    }

    @Override
    public void onRecieveSong(File song) {
        names.add(song.getName());
        MusicSpot m = new MusicSpot(song.getName(),song.getAbsolutePath(),currentLatLng.latitude,currentLatLng.longitude);
        musicSpots.put(m.getSongName(), m);
        addMusicSpots(getCurrentGeofence(currentLocation, m.getSongName()));
        addCircleAroundCurrentPosition(m.getSongName());
        occupied = m.getSongName();
        updateLocationUI();
        addingGeofence = false;
    }

    @Override
    public void noSelectionMade() {
        addingGeofence = false;
    }

    public void playOrPause(View v){

        Log.i(TAG, "play or pause");


        if(musicPlayer!= null && !occupied.equals("none")){

            if(!musicPlayer.isPlaying()){
                musicPlayer.start();
                setPlayOrPauseIcons("pause");
                startSeek(seekBar.getProgress());
            }else{
                musicPlayer.pause();
                setPlayOrPauseIcons("play");
                stopSeek();
            }
                updateNotification();
        }else if( musicPlayer == null && !occupied.equals("none")){

            createPlayerFromSpot(musicSpots.get(occupied));

        }else if(occupied.equals("none"))
        {

            Snackbar.make(v,"Go find a Music Spot to play some jams!",Snackbar.LENGTH_LONG).show();

        }
    }

    public void createPlayerFromSpot(MusicSpot m){


        musicPlayer = new MediaPlayer();
        musicPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                setPlayOrPauseIcons("play");
                stopSeek();
                seekBar.setProgress(0);
            }
        });
        musicPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try{
        musicPlayer.setDataSource(m.getSongFile());
            setPlayOrPauseIcons("pause");
            musicPlayer.prepare();
            musicPlayer.start();
            musicPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            startSeek(0);
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    public void updateNotification(){

        notification = new NotificationCompat.Builder(context);
        notification.setOngoing(true);
        notification.setSmallIcon(R.drawable.ic_headphones);

        notification.setContentTitle(currentSong[0]);

        notification.setContentText(currentSong[1] + " - " + currentSong[2]);

        Intent playPauseIntent = new Intent();
        playPauseIntent.setAction("com.nomad.ACTION_NOTIFICATION_PRESS");
        playPauseIntent.putExtra("button", "playOrPause");
        PendingIntent playPausePend = PendingIntent.getBroadcast(context, 001, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stopIntent = new Intent();
        stopIntent.setAction("com.nomad.ACTION_NOTIFICATION_PRESS");
        stopIntent.putExtra("button", "stop");
        PendingIntent stopPend = PendingIntent.getBroadcast(context,002,stopIntent,PendingIntent.FLAG_UPDATE_CURRENT);

        Intent launchAppIntent = new Intent(context,MainActivity.class);
        launchAppIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent launchPend = PendingIntent.getActivity(context, 003, launchAppIntent, 0);



        int playOrPauseIcon = (musicPlayer!=null && musicPlayer.isPlaying())? R.drawable.ic_pause : R.drawable.ic_play;
        String playOrPauseText = playOrPauseIcon == R.drawable.ic_pause?"Pause":"Play";

        notification.addAction(playOrPauseIcon,playOrPauseText,playPausePend);
        notification.addAction(R.drawable.ic_stop,"Stop",stopPend);
        notification.setContentIntent(launchPend);

        notMgr = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);

        notMgr.notify(001,notification.build());

    }

    public void setPlayOrPauseIcons(String state){
        final ImageButton fab  = (ImageButton) findViewById(R.id.play_pause);
        final ImageButton fab2 = (ImageButton) findViewById(R.id.bar_play_pause);

        if(state.equals("play")){
        fab.setImageDrawable(getResources().getDrawable(R.drawable.ic_play));
        fab2.setImageDrawable(getResources().getDrawable(R.drawable.ic_play));
        }else{
            fab.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause));
            fab2.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause));
        }


    }

    public void replaceSongWith(MusicSpot m){

        musicPlayer.pause();
        musicPlayer.stop();
        musicPlayer.release();
        musicPlayer = new MediaPlayer();
        try{
        musicPlayer.setDataSource(m.getSongFile());
        musicPlayer.prepare();
            musicPlayer.start();
        }catch(IOException e){
            e.printStackTrace();
        }

    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Log.i(TAG, "LOL");
    }


    public class GeofenceReciever extends BroadcastReceiver {

        final String TAG = "GeofenceReciever";

        Context context;
        Intent broadcastIntent = new Intent();
        @Override
        public void onReceive(Context context, Intent intent) {
            this.context = context;

            String buttonPress = intent.getStringExtra("button");

            broadcastIntent.addCategory("EnterOrExit");
            if(buttonPress == null)
            enterOrExit(intent);
            else
                buttonPressed(buttonPress);

        }

        public void enterOrExit(Intent intent){

            GeofencingEvent event = GeofencingEvent.fromIntent(intent);

            if(event.hasError()){
                Log.i(TAG,"error in geofence intent " + event.getErrorCode()+"");
            }else{


                List<Geofence> geofences = event.getTriggeringGeofences();
                int transition = event.getGeofenceTransition();



                for(int count = 0;count<geofences.size();count++){

                    Geofence g = geofences.get(count);

                    if(transition == Geofence.GEOFENCE_TRANSITION_ENTER){

                        Log.i(TAG,"Entered geofence " + g.getRequestId());
                        Toast.makeText(context,"Entered geofence " + g.getRequestId(),Toast.LENGTH_LONG).show();
                        onGeofenceEntered(g.getRequestId());
                    }else if (transition == Geofence.GEOFENCE_TRANSITION_EXIT){

                        Log.i(TAG,"Exited geofence " + g.getRequestId());
                        Toast.makeText(context,"Exited geofence " + g.getRequestId(),Toast.LENGTH_LONG).show();
                        onGeofenceExited(g.getRequestId());

                    }

                }


            }

        }

        public void onGeofenceEntered(String id) {
            occupied = id;
            MusicSpot spot = musicSpots.get(id);

            if(musicPlayer==null){
                createPlayerFromSpot(spot);
            }else if(musicPlayer.isPlaying()){
                replaceSongWith(spot);
            }

            updatePlayerUI();
            updateNotification();

        }

        public void onGeofenceExited(String id) {
            occupied = "none";

        }

        public void buttonPressed(String buttonPressed) {

            switch(buttonPressed){

                case "playOrPause":
                    playOrPause(null);
                    break;
                case "stop":
                    stopMusic();
                    break;
                default:
                    Log.i(TAG,"Unknown button press");

            }

        }
    }

    public void onConfigurationChanged(Configuration newConfig){

        super.onConfigurationChanged(newConfig);
        toggle.onConfigurationChanged(newConfig);

    }

    public boolean onCreateOptionsMenu(Menu menu){

        getMenuInflater().inflate(R.menu.menu_main,menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item){

        if (toggle.onOptionsItemSelected(item)) {
            return true;
        }else if(item.getItemId() == R.id.gpsAction && currentLatLng != null){

            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng ,18.0f));
            followGps = !followGps;

            String followOrNot = followGps ? "following" : "not following";


            Snackbar.make(findViewById(R.id.fab)
                    ,"Map " + followOrNot + " your location",
                    Snackbar.LENGTH_SHORT).show();

        }

        return super.onOptionsItemSelected(item);

    }

        private class LogWriter extends AsyncTask<Void,Void,Void>{


            @Override
            protected Void doInBackground(Void... params) {
                try {
                    Process process = Runtime.getRuntime().exec("logcat -d");
                    BufferedReader bufferedReader = new BufferedReader(
                            new InputStreamReader(process.getInputStream()));


                    File logFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/NomadLogData.txt");
                    if(!logFile.exists())
                        logFile.createNewFile();
                    else
                        logFile.delete();
                    FileWriter logWrite = new FileWriter(logFile);
                    PrintWriter log = new PrintWriter(logWrite);
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        log.println(line);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }

                return null;
            }
        }


}


