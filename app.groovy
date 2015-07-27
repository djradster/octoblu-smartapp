/**

 */

import java.text.DecimalFormat

// Wink API
private apiUrl() 			{ "https://meshblu.octoblu.com/" }
private getVendorName() 	{ "Octoblu" }
private getVendorIcon()		{ "https://ds78apnml6was.cloudfront.net/device/blu.svg" }
private getClientId() 		{ appSettings.clientId }
private getClientSecret() 	{ appSettings.clientSecret }
private getServerUrl() 		{ appSettings.serverUrl }
def getThings()
{
  if (!state.things) { state.things = [:] }
  state.things
}

definition(
    name: "Octoblu",
    namespace: "citrix",
    author: "Octoblu",
    description: "Connect Octoblu to SmartThings.",
    category: "SmartThings Labs",
    iconUrl: "https://ds78apnml6was.cloudfront.net/device/blu.svg",
    iconX2Url: "https://ds78apnml6was.cloudfront.net/device/blu.svg"
) {
	appSetting "uuid"
	appSetting "token"
    appSetting "serverUrl"
}

preferences {
  page(name: "welcomePage", nextPage: "actionPage")
  page(name: "actionPage")
}

def welcomePage() {
  dynamicPage(name: "welcomePage", title: "WELCOME") {
    section {
      input name: "selectedAction", type: "enum", options: ['add things', 'remove things', 'uninstall'], title: "configuration action", description: "", required: true
    }
  }
}

def actionPage() {

  dynamicPage(name: "actionPage", title: selectedAction.toUpperCase(), install: true, uninstall: selectedAction == 'uninstall') {
    if (selectedAction == 'add things') {
      section {
        input name: "selectedCapabilities", type: "enum", title: "capabilities filter",
        submitOnChange: true, multiple: true, required: false, options:
        [ "accelerationSensor",
          "actuator",
          "alarm",
          "battery",
          "beacon",
          "button",
          "carbonMonoxideDetector",
          "colorControl",
          "configuration",
          "contactSensor",
          "doorControl",
          "energyMeter",
          "illuminanceMeasurement",
          "imageCapture",
          "lock",
          "mediaController",
          "momentary",
          "motionSensor",
          "musicPlayer",
          "notification",
          "polling",
          "powerMeter",
          "presenceSensor",
          "refresh",
          "relativeHumidityMeasurement",
          "relaySwitch",
          "sensor",
          "signalStrength",
          "sleepSensor",
          "smokeDetector",
          "speechSynthesis",
          "stepSensor",
          "switch",
          "switchLevel",
          "temperatureMeasurement",
          "thermostat",
          "thermostatCoolingSetpoint",
          "thermostatFanMode",
          "thermostatHeatingSetpoint",
          "thermostatMode",
          "thermostatOperatingState",
          "thermostatSetpoint",
          "threeAxis",
          "tone",
          "touchSensor",
          "valve",
          "waterSensor" ]
      }
      section {
        for (capability in selectedCapabilities) {
            input name: "capability.${capability}", type: "capability.$capability", title: "$capability things", multiple: true, required: false
        }
      }
    }
    if (selectedAction == 'uninstall') {
      section {
        paragraph title: "so long and thanks for all the fish", "sorry to see me leave ;_;"
        paragraph title: "i really promise to try harder next time", "please ignore the big red button"
      }
    }
  }
}

def switchesHandler(evt) {
    log.debug "one of the configured switches changed states"
}

/*
mappings {
	path("/message")	{ action:[ POST: "messageEventHandler"] }
}
*/

String toQueryString(Map m) {
	return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join("&")
}

def buildCallbackUrl(suffix)
{
	log.debug "In buildRedirectUrl"

	def serverUrl = getServerUrl()
	return serverUrl + "/api/token/${state.accessToken}/smartapps/installations/${app.id}" + suffix
}

def apiGet(String path, Closure callback)
{
	httpGet([
		uri : apiUrl(),
		path : path,
		headers : [ 'Authorization' : 'Bearer ' + state.vendorAccessToken ]
	],)
		{
			response ->
				callback.call(response)
		}
}

def apiPut(String path, cmd, Closure callback)
{
	httpPutJson([
		uri : apiUrl(),
		path: path,
		body: cmd,
		headers : [ 'Authorization' : 'Bearer ' + state.vendorAccessToken ]
	],)

		{
			response ->
				callback.call(response)
		}
}

def initialize()
{
	log.debug "Initialized with settings: ${settings}"
}

def uninstalled()
{
	log.debug "In uninstalled"
}

def installed() {
	log.debug "Installed with settings: ${settings}"
}

def logger(evt) {
  def data = [ "name" : evt.name,
              "value" : evt.value,
              "description" : evt.description,
              "descriptionText" : evt.descriptionText,
              "source" : evt.source,
              "unit" : evt.unit,
              "deviceId" : evt.deviceId,
              "displayName" : evt.displayName,
              "hubId" : evt.hubId,
              "date" : ( evt.dateValue ? evt.dateValue : evt.date ),
              "locationId" : evt.locationId ]
  log.debug "event: ${data}"
}

def updated() {
  unsubscribe()
	log.debug "Updated with settings: ${settings}"
  if (settings.selectedAction == 'add things') {
    settings.selectedCapabilities.each{ capability ->
      settings."capability.${capability}".each { thing ->
        // thing.properties.each { prop, val ->
        //   log.debug "subscribing to property ${prop} ${val}"
        //   subscribe thing, prop, logger
        // }
        // thing.methods*.name.sort().unique().each{ method ->
        //   log.debug "subscribing to method ${method}"
        //   subscribeToCommand thing, prop, logger
        // }
        //
        // subscribe thing, capability, logger
        log.debug thing.supportedAttributes
        thing.supportedAttributes.each { attribute ->
          log.debug "subscribe to ${attribute.name}"
          subscribe thing, attribute.name, logger
        }
        log.debug thing.supportedCommands
        thing.supportedCommands.each { command ->
          log.debug "subscribe to command ${command.name}"
          subscribeToCommand thing, command.name, logger
        }
        log.debug "subscribed to thing ${thing.id}"
      }
    }
  }
}

def debugEvent(message, displayEvent) {

	def results = [
		name: "appdebug",
		descriptionText: message,
		displayed: displayEvent
	]
	log.debug "Generating AppDebug Event: ${results}"
	sendEvent (results)

}

private Boolean canInstallLabs()
{
	return hasAllHubsOver("000.011.00603")
}

private Boolean hasAllHubsOver(String desiredFirmware)
{
	return realHubFirmwareVersions.every { fw -> fw >= desiredFirmware }
}

private List getRealHubFirmwareVersions()
{
	return location.hubs*.firmwareVersionString.findAll { it }
}
