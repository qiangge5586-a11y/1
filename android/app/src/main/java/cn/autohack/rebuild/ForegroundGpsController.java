package cn.autohack.rebuild;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

/** GPS-provider-only location updates, explicitly started and stopped by the foreground Activity. */
public final class ForegroundGpsController implements LocationListener {
    public interface Listener {
        void onGpsUpdate(boolean hasSpeed, float speedKmh,
                boolean hasBearing, float bearingDegrees, boolean hasAccuracy, float accuracyMeters);
        void onGpsUnavailable(String reason);
    }

    private final Context context;
    private final LocationManager locationManager;
    private final Listener listener;
    private boolean registered;

    public ForegroundGpsController(Context context, Listener listener) {
        this.context = context;
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        this.listener = listener;
    }

    public boolean hasFinePermission() {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public boolean start() {
        if (registered) return true;
        if (!hasFinePermission()) {
            notifyUnavailable("Precise location permission is not granted.");
            return false;
        }
        if (locationManager == null) {
            notifyUnavailable("Location service is unavailable.");
            return false;
        }
        try {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                notifyUnavailable("GPS provider is disabled.");
                return false;
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    1000L, 0.0f, this, Looper.getMainLooper());
            registered = true;
            notifyUnavailable("Waiting for a GPS fix…");
            return true;
        } catch (SecurityException error) {
            notifyUnavailable("GPS permission was not granted.");
            return false;
        } catch (IllegalArgumentException error) {
            notifyUnavailable("GPS provider is unavailable on this device.");
            return false;
        }
    }

    public void stop() {
        if (registered && locationManager != null) {
            try {
                locationManager.removeUpdates(this);
            } catch (SecurityException ignored) {}
        }
        registered = false;
    }

    public boolean isRunning() {
        return registered;
    }

    @Override public void onLocationChanged(Location location) {
        if (location == null || listener == null) return;
        boolean hasSpeed = location.hasSpeed();
        float speedKmh = hasSpeed ? location.getSpeed() * 3.6f : -1f;
        boolean hasBearing = location.hasBearing();
        float bearing = hasBearing ? location.getBearing() : -1f;
        boolean hasAccuracy = location.hasAccuracy();
        float accuracy = hasAccuracy ? location.getAccuracy() : -1f;
        listener.onGpsUpdate(hasSpeed, speedKmh, hasBearing, bearing, hasAccuracy, accuracy);
    }

    @Override public void onProviderDisabled(String provider) {
        registered = false;
        notifyUnavailable("GPS provider was disabled.");
    }

    @Override public void onProviderEnabled(String provider) {
        // The Activity can restart the provider on the next explicit opt-in/resume.
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) {
        // GPS fix availability is reported through onLocationChanged.
    }

    private void notifyUnavailable(String reason) {
        if (listener != null) listener.onGpsUnavailable(reason);
    }
}
