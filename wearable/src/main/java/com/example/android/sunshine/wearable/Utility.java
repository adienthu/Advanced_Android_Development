package com.example.android.sunshine.wearable;


public class Utility {

    /**
     * Helper method to provide the icon resource id according to the weather condition id returned
     * by the OpenWeatherMap call.
     * @param weatherId from OpenWeatherMap API response
     * @return resource id for the corresponding icon. -1 if no relation is found.
     */
    public static int getIconResourceForWeatherCondition(int weatherId, boolean inAmbientMode) {
        // Based on weather code data found at:
        // http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
        if (weatherId >= 200 && weatherId <= 232) {
            return inAmbientMode ? R.drawable.ic_storm_ambient : R.drawable.ic_storm;
        } else if (weatherId >= 300 && weatherId <= 321) {
            return inAmbientMode ? R.drawable.ic_light_rain_ambient : R.drawable.ic_light_rain;
        } else if (weatherId >= 500 && weatherId <= 504) {
            return inAmbientMode ? R.drawable.ic_rain_ambient : R.drawable.ic_rain;
        } else if (weatherId == 511) {
            return inAmbientMode ? R.drawable.ic_snow_ambient : R.drawable.ic_snow;
        } else if (weatherId >= 520 && weatherId <= 531) {
            return inAmbientMode ? R.drawable.ic_rain_ambient : R.drawable.ic_rain;
        } else if (weatherId >= 600 && weatherId <= 622) {
            return inAmbientMode ? R.drawable.ic_snow_ambient : R.drawable.ic_snow;
        } else if (weatherId >= 701 && weatherId <= 761) {
            return inAmbientMode ? R.drawable.ic_fog_ambient : R.drawable.ic_fog;
        } else if (weatherId == 761 || weatherId == 781) {
            return inAmbientMode ? R.drawable.ic_storm_ambient : R.drawable.ic_storm;
        } else if (weatherId == 800) {
            return inAmbientMode ? R.drawable.ic_clear_ambient : R.drawable.ic_clear;
        } else if (weatherId == 801) {
            return inAmbientMode ? R.drawable.ic_light_clouds_ambient : R.drawable.ic_light_clouds;
        } else if (weatherId >= 802 && weatherId <= 804) {
            return inAmbientMode ? R.drawable.ic_cloudy_ambient : R.drawable.ic_cloudy;
        }
        return -1;
    }

}
