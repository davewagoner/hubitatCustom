/*
master: https://api.weather.gov/points/45.449,-122.714
daily forecast: https://api.weather.gov/gridpoints/PQR/110,100/forecast

todo: 
- add logic check for today's high temp based on date, isDaytime
- add temp units


*/
metadata {
    definition(name: "Weather.gov Forecast Device", namespace: "davewagoner", author: "davewagoner") {
        capability "Temperature Measurement"
        capability "Refresh"
        capability "Sensor"
	capability "Actuator"
        
		//Current Conditions
        attribute "weather", "string"
        attribute "weatherIcon", "string"

    }
	preferences {
        section("Preferences") {
            input "showLogs", "bool", required: false, title: "Show Debug Logs?", defaultValue: false
        }
    }
}

import groovy.json.JsonSlurper

def fetchNewWeather() {
    def apiUrl = "https://api.weather.gov/gridpoints/PQR/110,100/forecast";
    def card = new JsonSlurper().parse(apiUrl.toURL());
    def weatherMap = [:];
    def tempInt = card.properties.periods[0].temperature;
    weatherMap.temperature = Integer.toString(tempInt);
    weatherMap.icon = card.properties.periods[0].icon;
    //log.debug("reqdataslurper: " + Integer.toString(tempInt));
    setWeather(weatherMap);
}

def refresh() {
	fetchNewWeather(); 
}

def setWeather(weather){
	//logger("debug", "Weather: "+weather);
    log.debug("setweather: " + weather.temperature);
	
	//Set temperature
	sendEvent(name: "temperature", value: weather.temperature, unit: '°F', isStateChange: true, displayed: true);
    sendEvent(name: "icon", value: weather.icon, isStateChange: true, displayed: true);
}
	
