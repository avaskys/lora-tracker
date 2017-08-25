package com.vaskys.tracker;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
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
        setContentView(this.mapView);

        this.mapView.setClickable(true);
        this.mapView.getMapScaleBar().setVisible(true);
        this.mapView.setBuiltInZoomControls(true);
        this.mapView.setZoomLevelMin((byte) 10);
        this.mapView.setZoomLevelMax((byte) 20);

        // create a tile cache of suitable size
        TileCache tileCache = AndroidUtil.createTileCache(this, "mapcache",
                mapView.getModel().displayModel.getTileSize(), 1f,
                this.mapView.getModel().frameBufferModel.getOverdrawFactor());

        // tile renderer layer using internal render theme
        MultiMapDataStore mapDataStore = new MultiMapDataStore(MultiMapDataStore.DataPolicy.RETURN_FIRST);

        for (String file : MAP_FILES) {
            MapDataStore mf = new MapFile(new File(getCacheDir(), file));
            mapDataStore.addMapDataStore(mf, false, false);
        }

        TileRendererLayer tileRendererLayer = new TileRendererLayer(tileCache, mapDataStore,
                this.mapView.getModel().mapViewPosition, AndroidGraphicFactory.INSTANCE);
        tileRendererLayer.setXmlRenderTheme(InternalRenderTheme.DEFAULT);

        // only once a layer is associated with a mapView the rendering starts
        this.mapView.getLayerManager().getLayers().add(tileRendererLayer);

        // BRC
        this.mapView.setCenter(new LatLong(40.786315, -119.206562));
        // SF
        //this.mapView.setCenter(new LatLong(37.765730, -122.418266));
        this.mapView.setZoomLevel((byte) 14);
    }

    @Override
    protected void onDestroy() {
        this.mapView.destroyAll();
        AndroidGraphicFactory.clearResourceMemoryCache();
        super.onDestroy();
    }
}