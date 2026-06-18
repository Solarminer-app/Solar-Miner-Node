package de.verdox.pv_miner.frontend.pvsite.details;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Div;
import java.util.function.BiConsumer;

@StyleSheet("https://unpkg.com/leaflet@1.9.4/dist/leaflet.css")
@JavaScript("https://unpkg.com/leaflet@1.9.4/dist/leaflet.js")
public class LeafletMap extends Div {

    private BiConsumer<Double, Double> onMapClickListener;

    private Double initialLat = 51.1657;
    private Double initialLng = 10.4515;
    private int initialZoom = 6;
    private boolean showInitialMarker = false;

    public LeafletMap() {
        getStyle().set("height", "400px").set("width", "100%");
        getStyle().set("z-index", "1");
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        initMap();
    }

    private void initMap() {
        UI.getCurrent().getPage().executeJs(
                "setTimeout(function() {" +
                        "   const el = $0;" +
                        "   if(el._map) return;" +
                        "   const map = L.map(el).setView([$1, $2], $3);" +
                        "   el._map = map;" +
                        "   L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {" +
                        "       attribution: '&copy; OpenStreetMap contributors'," +
                        "       maxZoom: 19" +
                        "   }).addTo(map);" +
                        "   el._currentMarker = null;" +
                        "   if ($4) {" +
                        "       el._currentMarker = L.marker([$1, $2]).addTo(map);" +
                        "   }" +
                        "   map.on('click', function(e) {" +
                        "       if (el._currentMarker) map.removeLayer(el._currentMarker);" +
                        "       el._currentMarker = L.marker(e.latlng).addTo(map);" +
                        "       el.$server.handleMapClick(e.latlng.lat, e.latlng.lng);" +
                        "   });" +
                        "}, 100);",
                getElement(), initialLat, initialLng, initialZoom, showInitialMarker
        );
    }

    public void setInitialView(double lat, double lng, int zoom, boolean withMarker) {
        this.initialLat = lat;
        this.initialLng = lng;
        this.initialZoom = zoom;
        this.showInitialMarker = withMarker;
    }

    public void setView(double lat, double lng, int zoom) {
        UI.getCurrent().getPage().executeJs(
                "if ($0._map) { $0._map.setView([$1, $2], $3); }",
                getElement(), lat, lng, zoom
        );
    }

    public void addMarker(double lat, double lng) {
        UI.getCurrent().getPage().executeJs(
                "if ($0._map) {" +
                        "   if ($0._currentMarker) $0._map.removeLayer($0._currentMarker);" +
                        "   $0._currentMarker = L.marker([$1, $2]).addTo($0._map);" +
                        "}",
                getElement(), lat, lng
        );
    }

    @ClientCallable
    public void handleMapClick(double lat, double lng) {
        if (onMapClickListener != null) {
            onMapClickListener.accept(lat, lng);
        }
    }

    public void setOnMapClickListener(BiConsumer<Double, Double> listener) {
        this.onMapClickListener = listener;
    }
}