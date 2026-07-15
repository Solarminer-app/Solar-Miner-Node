'use client';

import {useEffect, useMemo, useState} from 'react';
import {CircleMarker, MapContainer, TileLayer, useMap, useMapEvents} from 'react-leaflet';
import type {LatLngLiteral} from 'leaflet';
import 'leaflet/dist/leaflet.css';

export type PanelLocation = {
    latitude: number;
    longitude: number;
};

type Props = {
    value: PanelLocation;
    onChange: (value: PanelLocation) => void;
    labels: {
        select: string;
        move: string;
        selected: string;
        useLocation: string;
        locationError: string;
    };
};

const FALLBACK_CENTER: LatLngLiteral = {lat: 51.1657, lng: 10.4515};

function CenterMap({center, hasLocation}: {center: LatLngLiteral; hasLocation: boolean}) {
    const map = useMap();

    useEffect(() => {
        map.setView(center, hasLocation ? 19 : 6);
    }, [center, hasLocation, map]);

    return null;
}

function LocationEvents({onChange}: {onChange: (value: PanelLocation) => void}) {
    useMapEvents({
        click(event) {
            onChange({latitude: event.latlng.lat, longitude: event.latlng.lng});
        },
    });
    return null;
}

export default function PanelLocationMap({value, onChange, labels}: Props) {
    const [locationError, setLocationError] = useState(false);
    const hasLocation = value.latitude !== 0 || value.longitude !== 0;
    const center = useMemo<LatLngLiteral>(
        () => hasLocation ? {lat: value.latitude, lng: value.longitude} : FALLBACK_CENTER,
        [hasLocation, value.latitude, value.longitude],
    );

    const useCurrentLocation = () => {
        setLocationError(false);
        if (!navigator.geolocation) {
            setLocationError(true);
            return;
        }
        navigator.geolocation.getCurrentPosition(
            ({coords}) => onChange({latitude: coords.latitude, longitude: coords.longitude}),
            () => setLocationError(true),
            {enableHighAccuracy: true, timeout: 10_000},
        );
    };

    return (
        <div className="overflow-hidden rounded-xl border border-white/10 bg-[#0e0e11]">
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-white/10 px-3 py-2 text-xs">
                <span className="font-medium text-violet-200">{hasLocation ? labels.move : labels.select}</span>
                <div className="flex flex-wrap items-center gap-3">
                    {hasLocation ? <span className="text-[#9c9ca5]">{labels.selected}: {value.latitude.toFixed(6)}, {value.longitude.toFixed(6)}</span> : null}
                    <button className="rounded-md bg-white/[0.06] px-2.5 py-1.5 font-medium text-white transition hover:bg-white/[0.12]" onClick={useCurrentLocation} type="button">{labels.useLocation}</button>
                </div>
            </div>
            {locationError ? <p className="border-b border-red-500/20 bg-red-500/[0.07] px-3 py-2 text-xs text-red-300">{labels.locationError}</p> : null}
            <MapContainer center={center} zoom={hasLocation ? 19 : 6} style={{height: 360, width: '100%'}} zoomControl>
                <TileLayer attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>' maxNativeZoom={19} maxZoom={21} url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"/>
                <CenterMap center={center} hasLocation={hasLocation}/>
                <LocationEvents onChange={onChange}/>
                {hasLocation ? <CircleMarker center={center} pathOptions={{color: '#fff', fillColor: '#facc15', fillOpacity: 1, weight: 2}} radius={7}/> : null}
            </MapContainer>
        </div>
    );
}
