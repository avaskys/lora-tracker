package com.vaskys.tracker;

import android.app.Activity;
import android.content.Intent;
import android.media.audiofx.BassBoost;
import android.os.Bundle;
import android.os.Environment;
import android.support.constraint.ConstraintLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.Toast;

import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.datastore.MultiMapDataStore;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.InternalRenderTheme;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    // name of the map file in the external storage
    private static final String[] MAP_FILES = { "hood.map", "brc.map" };


    private MapView mapView;

    private void copyStream(InputStream is, OutputStream os) throws IOException {
        final int BUF_SIZE = 1024;
        byte[] bytes = new byte[BUF_SIZE];
        for (;;) {
            int count = is.read(bytes, 0, BUF_SIZE);
            if (count == -1)
                break;
            os.write(bytes, 0, count);
        }
    }

    private void copyAssets() throws IOException {
        for (String name : MAP_FILES) {
            File f = new File(getCacheDir(), name);
            if (!f.exists()) {
                InputStream is = getAssets().open(name);
                OutputStream os = new FileOutputStream(f);
                copyStream(is, os);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            copyAssets();
        } catch (IOException ioe) {
            Toast.makeText(this, "Failed to copy assets: " + ioe.getMessage(), Toast.LENGTH_SHORT).show();
        }

        AndroidGraphicFactory.createInstance(this.getApplication());

        this.mapView = new MapView(this);
        setContentView(R.layout.activity_main);
        ((FrameLayout)findViewById(R.id.map_placeholder)).addView(mapView);

        this.mapView.setClickable(true);
        this.mapView.getMapScaleBar().setVisible(true);
        this.mapView.setBuiltInZoomControls(true);
        this.mapView.setZoomLevelMin((byte) 10);
        this.mapView.setZoomLevelMax((byte) 20);

        // Initialize preferences
        //ListView drawerList = (ListView) findViewById(R.id.left_drawer);
        //drawerList.setAdapter(new ArrayAdapter<>(this,
        //        R.layout.drawer_list_item, mPlanetTitles));


        TileCache tileCache = AndroidUtil.createTileCache(this, "mapcache",
                mapView.getModel().displayModel.getTileSize(), 1f,
                this.mapView.getModel().frameBufferModel.getOverdrawFactor());

        MultiMapDataStore mapDataStore = new MultiMapDataStore(MultiMapDataStore.DataPolicy.RETURN_FIRST);

        for (String file : MAP_FILES) {
            MapDataStore mf = new MapFile(new File(getCacheDir(), file));
            mapDataStore.addMapDataStore(mf, false, false);
        }

        TileRendererLayer tileRendererLayer = new TileRendererLayer(tileCache, mapDataStore,
                this.mapView.getModel().mapViewPosition, AndroidGraphicFactory.INSTANCE);
        tileRendererLayer.setXmlRenderTheme(InternalRenderTheme.DEFAULT);

        this.mapView.getLayerManager().getLayers().add(tileRendererLayer);

        // BRC
        //LatLong loc = new LatLong(40.786315, -119.206562);
        // SF
        LatLong loc = new LatLong(37.765730, -122.418266);
        this.mapView.setCenter(loc);
        this.mapView.setZoomLevel((byte) 14);


        // FIXME: goes elsewhere
        final LayoutInflater inflater = LayoutInflater.from(this);
        final Button button = (Button) inflater.inflate(R.layout.pointer_bubble, null);
        button.setText("BRC BABY");
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Clicked", Toast.LENGTH_SHORT).show();
            }
        });
        this.mapView.addView(button, new MapView.LayoutParams(MapView.LayoutParams.WRAP_CONTENT, MapView.LayoutParams.WRAP_CONTENT,
                loc, MapView.LayoutParams.Alignment.BOTTOM_CENTER));
    }

    @Override
    protected void onResume() {
        super.onResume();

        Intent intent = new Intent(LocationWatcherService.ACTION_START, null, this, LocationWatcherService.class);
        startService(intent);
    }

    @Override
    protected void onDestroy() {
        this.mapView.destroyAll();
        AndroidGraphicFactory.clearResourceMemoryCache();
        super.onDestroy();
    }

    public void openSettings(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }
}