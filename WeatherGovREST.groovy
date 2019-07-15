/*

Original source: https://github.com/hubitat/HubitatPublic/blob/master/examples/drivers/environmentSensor.groovy

*/

/*
	WeatherGOVRest
  
  API for what I want: https://api.weather.gov/gridpoints/PQR/110,100/forecast
  
  Start with "today's high" and we'll go from there.
  
*/

import groovy.transform.Field

metadata {
    definition (name: "", namespace: "hubitatCustom", author: "hubitatCustom/dave") {
	capability "Configuration"
	capability "Refresh"
	capability "Temperature Measurement"

	}
        
    preferences {
        //standard logging options
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def parse(String description) {
	if (logEnable) log.debug "description is ${description}"
	if (description.startsWith("catchall")) return
	def descMap = zigbee.parseDescriptionAsMap(description)
	if (logEnable) log.debug "descMap:${descMap}"
	
	def cluster = descMap.cluster
	def hexValue = descMap.value
	def attrId = descMap.attrId
	
	switch (cluster){
		case "0400" :	//illuminance
			getLuminanceResult(hexValue)
			break
		case "0402" :	//temp
			getTemperatureResult(hexValue)
			break
		case "0403" :	//pressure
			getPressureResult(hexValue)
			break
		case "0405" :	//humidity
			getHumidityResult(hexValue)
			break
		case "0B05" : //diag
        		if (logEnable) log.warn "attrId:${attrId}, hexValue:${hexValue}"
        		def value = hexStrToUnsignedInt(hexValue)
        		log.warn "diag- ${diagAttributes."${attrId}".name}:${value} "
			break
		default :
			log.warn "skipped cluster: ${cluster}, descMap:${descMap}"
			break
	}
	return
}

//event methods
private getTemperatureResult(hex){
    def valueRaw = hexStrToSignedInt(hex)
    valueRaw = valueRaw / 100
    def value = convertTemperatureIfNeeded(valueRaw.toFloat(),"c",1)
    /*
	//example temp offset
	state.sensorTemp = value
    if (state.tempOffset) {
        value =  (value.toFloat() + state.tempOffset.toFloat()).round(2)
    }
	*/
    def name = "temperature"
    def unit = "°${location.temperatureScale}"
    def descriptionText = "${device.displayName} ${name} is ${value}${unit}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: name,value: value,descriptionText: descriptionText,unit: unit)
}

private getHumidityResult(hex){
    def valueRaw = hexStrToUnsignedInt(hex)
    def value = valueRaw / 100
    def name = "humidity"
    def unit = "%"
    def descriptionText = "${device.displayName} ${name} is ${value}${unit}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: name,value: value,descriptionText: descriptionText,unit: unit)
}

private getLuminanceResult(hex) {
	def rawValue = hexStrToUnsignedInt(hex)
    def value = (Math.pow(10,(rawValue/10000))+ 1).toInteger()
    if (rawValue.toInteger() == 0) value = "0"
    def name = "illuminance"
    def unit = "Lux"	
    def descriptionText = "${device.displayName} ${name} is ${value}${unit}"
    if (txtEnable) log.info "${descriptionText}"
	sendEvent(name: name,value: value,descriptionText: descriptionText,unit: unit)
}

private getPressureResult(hex){
    def valueRaw = hexStrToUnsignedInt(hex)
    def value = valueRaw / 10
    def name = "pressure"
    def unit = "kPa"
    def descriptionText = "${device.displayName} ${name} is ${value}${unit}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: name,value: value,descriptionText: descriptionText,unit: unit)
}


//capability and device methods
def off() {
    zigbee.off()
}

def on() {
    zigbee.on()
}

def refresh() {
    log.debug "Refresh"
    
	//readAttribute(cluster,attribute,mfg code,optional delay ms)
    def cmds = zigbee.readAttribute(0x0402,0x0000,[:],200) +		//temp
        zigbee.readAttribute(0x0405,0x0000,[:],200) + 			//humidity
        zigbee.readAttribute(0x0403,0x0000,[:],200) +			//pressure
        zigbee.readAttribute(0x0400,0x0000,[:],200) 			//illuminance
    	diagAttributes.each{ it ->
            //log.debug "it:${it.value.val}"
			cmds +=  zigbee.readAttribute(0x0B05,it.value.val,[:],200) 
		}  
    return cmds
}

def configure() {
    log.debug "Configuring Reporting and Bindings."
    runIn(1800,logsOff)
    
    //temp offset init
    //state.tempOffset = 0
    
    List cmds = zigbee.temperatureConfig(5,300)												//temp
    cmds = cmds + zigbee.configureReporting(0x0405, 0x0000, DataType.UINT16, 5, 300, 100)	//humidity
    cmds = cmds + zigbee.configureReporting(0x0403, 0x0000, DataType.UINT16, 5, 300, 2)		//pressure
    cmds = cmds + zigbee.configureReporting(0x0400, 0x0000, DataType.UINT16, 1, 300, 500)	//illuminance
    cmds = cmds + refresh()
    log.info "cmds:${cmds}"
    return cmds
}

def updated() {
    log.trace "Updated()"
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    if (logEnable) runIn(1800,logsOff)    

    /* example temp offset
   	def crntTemp = device?.currentValue("temperature")
    if (refTemp && crntTemp && state.sensorTemp) {
        def prevOffset = (state.tempOffset ?: 0).toFloat().round(2)
        def deviceTemp = state.sensorTemp.toFloat().round(2)
        def newOffset =  (refTemp.toFloat() - deviceTemp).round(2)
        def newTemp = (deviceTemp + newOffset).round(2)
        //send new event on offSet change
        if (newOffset.toString() != prevOffset.toString()){
            state.tempOffset = newOffset
            def map = [name: "temperature", value: "${newTemp}", descriptionText: "${device.displayName} temperature offset was set to ${newOffset}°${location.temperatureScale}"]
            if (txtEnable) log.info "${map.descriptionText}"
            sendEvent(map)
        }
        //clear refTemp so it doesn't get changed later...
        device.removeSetting("refTemp")
    }
	*/
